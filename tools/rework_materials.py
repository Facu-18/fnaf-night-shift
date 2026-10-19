#!/usr/bin/env python3
"""Original, material-aware repaint. No source PNG is used to generate new art.

Pillow + numpy. 128px architectural surfaces, 64px props/icons, 512px entity
atlases (four pixels per existing UV unit). Block LabPBR maps are consumed by
the bundled Horror terrain program; GeckoLib consumes entity glow masks.
"""
import hashlib
import json
import math
from pathlib import Path
import random
import shutil
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageColor

import redesign_textures as layout
import gen_animatronics as anim

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'src/main/resources/assets/fnaf_night_shift'
OUT = ROOT / 'build/material-review'
NAMES = ('bonnie', 'chica', 'foxy', 'freddy', 'golden_freddy')
ORIGINAL_ART = layout.Art
PALETTE = {
    'bonnie': {'suit':'#666079','suit_light':'#99939f','belly':'#99939f','pink':'#927a83','pupil':'#b18b94'},
    'chica': {'suit':'#a39455','suit_light':'#c1b583','belly':'#b9ae7a','suit_alt':'#b9b5a2','beak':'#ad7b48','pink':'#a17479','pupil':'#aaa0bb'},
    'foxy': {'suit':'#89554a','suit_light':'#b19a7d','belly':'#a58f74','cloth':'#625d4e','pupil':'#c5b68a'},
    'freddy': {'suit':'#826b51','suit_light':'#b7a58b','belly':'#ac9b7d','pupil':'#91b1bb'},
    'golden_freddy': {'suit':'#9b8950','suit_light':'#baac79','belly':'#ac9a64','pupil':'#b5b6a4'},
}


def rng_for(key):
    return np.random.default_rng(int.from_bytes(hashlib.sha256(str(key).encode()).digest()[:8], 'little'))


def field(n, cells, rng):
    """Periodic smooth variation: mottling, not independent per-pixel speckle."""
    grid = rng.random((cells,cells))
    pos = np.arange(n)*cells/n
    lo = pos.astype(int)
    f = pos-lo
    f = f*f*(3-2*f)
    hi = (lo+1)%cells
    return ((grid[lo[:,None],lo[None,:]]*(1-f)[None,:] + grid[lo[:,None],hi[None,:]]*f[None,:])*(1-f)[:,None]
            + (grid[hi[:,None],lo[None,:]]*(1-f)[None,:] + grid[hi[:,None],hi[None,:]]*f[None,:])*f[:,None])


def rgb(color):
    return np.array(ImageColor.getrgb(color) if isinstance(color,str) else color[:3],dtype=float)


def muted(color):
    c=rgb(color)
    l=c@np.array([.2126,.7152,.0722])
    # Palette selection before painting, not a post-process on existing resources.
    return tuple(np.clip(c*.60+l*.40,0,255).astype(int))


def material(n, kind, color, key):
    """Return independently painted color, relief, smoothness, and metal mask."""
    rng=rng_for(key)
    y,x=np.mgrid[:n,:n]
    broad=field(n,4,rng)
    mid=field(n,13,rng)
    fine=field(n,max(16,n//2),rng)
    dirt=np.clip((.47-broad)*2.3,0,.65)
    height=.5 + (mid-.5)*.035
    val=(broad-.5)*15+(mid-.5)*9
    smooth=np.full((n,n),25.)
    metal=np.zeros((n,n))
    base=rgb(color)
    if kind in ('steel','paint','rust'):
        brushed=rng.normal(0,1.5,(n,1)) + np.sin(x*.6+y*.03)*.7
        val+=brushed
        pits=np.clip((.3-fine)*3,0,1)
        height-=pits*.05
        if kind=='steel':
            smooth[:]=130
            metal[:]=1
            corrosion=np.clip((.31-broad)*3,0,.5)*np.clip((.62-fine)*3,0,1)
        else:
            smooth[:]=52
            corrosion=np.clip((.52-broad)*3,0,.75)*np.clip((.65-fine)*3,0,1) if kind=='rust' else np.clip((.23-broad)*3,0,.4)*np.clip((.6-fine)*3,0,1)
        raw=base[None,None,:]+val[:,:,None]
        raw=raw*(1-corrosion[:,:,None])+np.array([87,58,39])*corrosion[:,:,None]
        smooth-=corrosion*95
        metal*=1-corrosion
        height+=corrosion*.1
    elif kind in ('cloth','fur','rubber'):
        if kind=='fur':
            strand=np.sin(x*2.2+np.sin(y*.18)*1.5+mid*2)
            strand*=.5+.5*np.sin(y*.7+x*.04)**2
            val+=strand*6
            height+=strand*.025
        elif kind=='cloth':
            weave=(np.sin(x*math.pi/2)*np.cos(y*math.pi/2))
            val+=weave*4
            height+=weave*.025
        else:
            val+=(fine-.5)*8
            height+=(fine-.5)*.045
        raw=base[None,None,:]+val[:,:,None]
        smooth[:]=12 if kind!='rubber' else 28
    elif kind=='wood':
        grain=np.sin(y*.63 + mid*4 + np.sin(x*.045)*2)
        grain+=.4*np.sin(y*1.9+mid*4)
        val+=grain*9
        raw=base[None,None,:]+val[:,:,None]*np.array([1,.83,.63])
        height+=grain*.045
        smooth[:]=42
    elif kind in ('ceramic','glass'):
        val=(broad-.5)*4+(mid-.5)*2
        raw=base[None,None,:]+val[:,:,None]
        smooth[:]=125 if kind=='ceramic' else 210
        height+=(fine-.5)*.006
        if kind=='ceramic':
            wet=np.clip((broad-.7)*4,0,.6)
            smooth+=wet*100
            raw*=1-wet[:,:,None]*.10
    else:  # concrete, paper, plaster
        pores=np.clip((.33-fine)*4,0,.8)
        # Low-frequency stains must not turn repeated wall blocks into cloud tiles.
        val=(broad-.5)*3+(mid-.5)*6+(fine-.5)*3-pores*10
        raw=base[None,None,:]+val[:,:,None]
        height+=(mid-.5)*.07-pores*.06
        smooth[:]=8 if kind=='concrete' else 20
    grime_strength=.04 if kind in ('concrete','plaster') else .10 if kind in ('ceramic','glass') else .18
    raw*=1-dirt[:,:,None]*grime_strength
    smooth*=1-dirt*.6
    return np.clip(raw,0,255).astype(np.uint8),np.clip(height,0,1),np.clip(smooth,0,254),metal


class Canvas(ORIGINAL_ART):
    """Paint every primitive with its own physical surface, not a flat color.

    Semantic layouts can be reused, but masks are rasterized anew and surfaces
    are generated directly at their delivery resolution. No old image filtering.
    """
    def __init__(self,name,color=None,n=64,kind='paint'):
        self.name=name
        self.rng=random.Random(name)
        self.n=n
        self.kind=kind
        self.im=Image.new('RGBA',(n,n))
        self.d=ImageDraw.Draw(self.im)
        self.height=np.full((n,n),.5)
        self.smooth=np.zeros((n,n))
        self.metal=np.zeros((n,n))
        self.emission=np.zeros((n,n))
        self.cache={}
        if color is not None:
            self.rect((0,0,n-1,n-1),color,kind=kind)

    def paint_mask(self,mask,color,kind=None,relief=0,emission=0):
        if color is None:
            return
        kind=kind or self.kind
        c=muted(color)
        # Very dark recesses are matte. Pale metal hardware is brushed steel.
        if kind=='paint':
            if self.name.startswith('supply_crate') and c[0]>c[1]*1.1 and c[1]>c[2]*1.1:
                kind='wood'
            elif self.name=='fuse' and c[1]>c[0]*1.1:
                kind='glass'
            elif self.name in ('pliers','screwdriver') and max(c)-min(c)>25:
                kind='rubber'
            elif max(c)<55:
                kind='rubber'
            elif max(c)-min(c)<30 and 110<sum(c)/3<190:
                kind='steel'
        key=(kind,c)
        if key not in self.cache:
            self.cache[key]=material(self.n,kind,c,(self.name,key))
        image,h,s,m=self.cache[key]
        self.im.paste(Image.fromarray(image).convert('RGBA'),(0,0),mask)
        take=np.asarray(mask)>0
        self.height[take]=np.clip(h[take]+relief,0,1)
        self.smooth[take]=s[take]
        self.metal[take]=m[take]
        self.emission[take]=emission

    def shape(self,method,coords,color,kind=None,relief=0,width=1):
        mask=Image.new('L',(self.n,self.n))
        d=ImageDraw.Draw(mask)
        if method=='line':
            d.line(coords,fill=255,width=width)
        else:
            getattr(d,method)(coords,fill=255)
        self.paint_mask(mask,color,kind,relief)

    def rect(self,box,color,outline=None,width=1,kind=None,relief=0):
        if color is not None:
            self.shape('rectangle',box,color,kind,relief)
        if outline:
            mask=Image.new('L',(self.n,self.n))
            ImageDraw.Draw(mask).rectangle(box,outline=255,width=width)
            self.paint_mask(mask,outline,kind,-.035)

    def line(self,pts,color,width=1,kind=None,relief=-.015):
        self.shape('line',pts,color,kind,relief,width)

    def poly(self,pts,color,outline=None,kind=None,relief=0):
        self.shape('polygon',pts,color,kind,relief)
        if outline:
            self.line(pts+[pts[0]],outline,kind=kind)

    def ellipse(self,box,color,outline=None,width=1,kind=None,relief=0):
        if color is not None:
            self.shape('ellipse',box,color,kind,relief)
        if outline:
            mask=Image.new('L',(self.n,self.n))
            ImageDraw.Draw(mask).ellipse(box,outline=255,width=width)
            self.paint_mask(mask,outline,kind,-.02)

    def noise(self,strength=5):
        pass  # Each material already contains correlated surface structure.

    def bevel(self,box,color='#626968'):
        x,y,r,b=box
        self.rect(box,color,relief=.035)
        self.line([(x,b),(x,y),(r,y)],'#92948b',relief=.02)
        self.line([(r,y+1),(r,b),(x+1,b)],'#343b3a',relief=-.02)

    def bolt(self,x,y):
        self.ellipse((x-2,y-2,x+2,y+2),'#343b3a',kind='steel')
        self.ellipse((x-1,y-1,x+1,y+1),'#b0b1a3',kind='steel',relief=.08)
        self.line([(x-1,y),(x+1,y)],'#484c49',relief=-.03)

    def checker(self,box,size=4,a='#303333',b='#bcb8a4'):
        x,y,r,bottom=box
        for yy in range(y,bottom+1,size):
            for xx in range(x,r+1,size):
                self.rect((xx,yy,min(r,xx+size-1),min(bottom,yy+size-1)),a if ((xx-x)//size+(yy-y)//size)%2 else b,kind='cloth' if self.kind=='cloth' else 'paint')

    def hazard(self,box):
        x,y,r,b=box
        mask=Image.new('L',(self.n,self.n))
        p=mask.load()
        for yy in range(y,b+1):
            for xx in range(x,r+1):
                p[xx,yy]=255 if (xx+yy)%16<8 else 0
        self.rect(box,'#343635')
        self.paint_mask(mask,'#afa06b','paint')

    def wear(self,amount=38):
        # Chips grow from panel edges; grime runs below fasteners, not random confetti.
        for _ in range(max(2,amount//4)):
            x=self.rng.choice([self.rng.randrange(2,8),self.rng.randrange(self.n-8,self.n-2)])
            y=self.rng.randrange(5,self.n-4)
            self.line([(x,y),(min(self.n-2,x+self.rng.randrange(1,4)),y+1)],'#8a8b7c',kind='steel')
        for x in (4,self.n-5):
            self.line([(x,7),(x+1,13),(x,17)],'#565044',kind='rust')

    def pbr(self):
        # LabPBR 1.3: normal RG, AO B, height A; spec R smoothness, G F0/metal,
        # B porosity/SSS (unused=0), A emission (255 means none, 0..254 active).
        dy,dx=np.gradient(self.height)
        normal=np.dstack((-dx*2.4,-dy*2.4,np.ones_like(dx)))
        normal/=np.linalg.norm(normal,axis=2)[:,:,None]
        alpha=np.asarray(self.im.getchannel('A'))>0
        n=np.zeros((self.n,self.n,4),np.uint8)
        n[:,:,:2]=np.clip((normal[:,:,:2]*.5+.5)*255,0,255).astype(np.uint8)
        n[:,:,2]=np.clip(255-np.maximum(.5-self.height,0)*110,180,255)
        n[:,:,3]=np.clip(self.height*255,0,255)
        n[~alpha]=[128,128,255,128]
        s=np.zeros_like(n)
        s[:,:,0]=self.smooth
        s[:,:,1]=np.where(self.metal>.65,230,10)  # iron or dielectric F0 ~0.04
        s[:,:,3]=np.where(self.emission>0,np.minimum(254,self.emission*254),255)
        s[~alpha]=[0,10,0,255]
        return Image.fromarray(n),Image.fromarray(s)


def architecture(name):
    n=128
    t=Canvas(name,'#87867a',n,'concrete')
    if name in ('industrial_concrete','cracked_concrete'):
        if name=='cracked_concrete':
            t.line([(0,36),(20,43),(32,42),(49,59),(60,62),(76,81),(93,85),(109,102),(127,108)],'#44453e',2,relief=-.16)
            t.line([(49,59),(43,40),(52,27),(48,12),(48,0)],'#55564c',relief=-.12)
            t.line([(76,81),(72,97),(78,111),(75,127)],'#55564c',relief=-.1)
    elif name in ('checkered_floor','dirty_tiles'):
        size=64 if name=='checkered_floor' else 32
        for y in range(0,n,size):
            for x in range(0,n,size):
                c=('#b6b3a5' if ((x+y)//size)%2==0 else '#414642') if size==64 else ('#989b8c' if ((x+y)//size)%2 else '#b1b0a1')
                t.rect((x,y,x+size-1,y+size-1),'#45473e',kind='concrete',relief=-.12)
                t.rect((x+2,y+2,x+size-2,y+size-2),c,kind='ceramic',relief=.02)
                t.line([(x+3,y+size-3),(x+3,y+3),(x+size-3,y+3)],'#c0bdad',kind='ceramic',relief=.025)
        t.line([(71,3),(76,14),(73,22),(80,30)],'#66695d',kind='concrete',relief=-.07)
        t.line([(74,21),(86,18),(93,21)],'#777b6e',kind='concrete',relief=-.04)
        for y in (29,30,31):
            t.line([(9,y),(24,y+2)],'#929384',kind='ceramic')
    elif name.startswith('industrial_wall') or name=='pizzeria_wall':
        for y in range(0,n,32):
            for x in range(-32 if y%64 else 0,n,64):
                t.rect((x,y,x+63,y+31),'#545b53',relief=-.08)
                t.rect((x+1,y+2,x+62,y+30),'#a5a598' if name=='pizzeria_wall' else '#8d978e',kind='plaster')
        if name=='industrial_wall_stripe':
            t.rect((0,62,127,84),'#393d38')
            for x in range(-24,128,32):
                t.poly([(x,64),(x+15,64),(x+35,81),(x+20,81)],'#afa06b',kind='paint')
        if name=='pizzeria_wall':
            t.rect((0,60,127,71),'#774d48',kind='paint')
            t.checker((0,72,127,103),16)
            t.rect((0,104,127,127),'#58625a',kind='paint')
            t.line([(0,106),(127,106)],'#7b8172')
        # Flaked paint reveals porous plaster, concentrated near the base.
        for x,y in [(12,113),(45,119),(96,111),(120,123),(30,26)]:
            t.poly([(x,y),(x+5,y-3),(x+8,y+1),(x+4,y+5),(x-2,y+4)],'#696b5f',kind='concrete',relief=-.03)
    elif name=='stage_floor':
        for y in range(0,128,32):
            t.rect((0,y,127,y+31),'#413c32',kind='wood',relief=-.05)
            t.rect((0,y+2,127,y+30),'#86715a',kind='wood',relief=.025)
            x=38 if y%64 else 97
            t.line([(x,y+2),(x,y+30)],'#494336',kind='wood',relief=-.08)
            for xx in (x-4,x+4):
                for yy in (y+6,y+25):
                    t.ellipse((xx,yy,xx+1,yy+1),'#41443e',kind='steel',relief=-.06)
        t.ellipse((52,44,74,49),None,'#66543e',kind='wood')
        t.line([(48,46),(40,45),(28,47),(4,46)],'#78624b',kind='wood')
    elif name=='stage_curtain':
        t=Canvas(name,'#68444a',128,'cloth')
        # Broad folds in fabric, without plastic highlights.
        a=np.asarray(t.im).copy()
        fold=.82+.18*np.cos(np.arange(128)*math.pi/16)
        a[:,:,:3]=(a[:,:,:3]*fold[None,:,None]).astype(np.uint8)
        t.im=Image.fromarray(a)
        t.d=ImageDraw.Draw(t.im)
        t.height+=np.cos(np.arange(128)*math.pi/16)[None,:]*.08
        t.line([(0,116),(127,116)],'#968665',2,kind='cloth')
        t.line([(0,121),(127,121)],'#42383a',2,kind='cloth')
        for x in (19,83):
            t.poly([(x,45),(x+3,53),(x+12,55),(x+4,60),(x,69),(x-4,60),(x-12,55),(x-3,53)],'#a1926d',kind='cloth')
    elif name=='metal_plating' or name=='rusty_metal':
        t=Canvas(name,'#777b76' if name=='metal_plating' else '#776b55',128,'steel' if name=='metal_plating' else 'rust')
        for y in range(12,128,24):
            for x in range(8+(12 if y%48 else 0),128,24):
                t.line([(x,y+5),(x+7,y)],'#a2a496',2,kind='steel',relief=.10)
                t.line([(x+1,y+7),(x+8,y+2)],'#4e5550',kind='steel')
        for x in (5,122):
            for y in (5,122):
                t.bolt(x,y)
    else:
        raise ValueError(name)
    return t


ARCH=set('industrial_concrete cracked_concrete checkered_floor dirty_tiles industrial_wall industrial_wall_stripe pizzeria_wall stage_floor stage_curtain metal_plating rusty_metal'.split())


def plate(name,color='#626c67'):
    t=Canvas(name,color)
    t.rect((1,1,62,62),color,'#3a413e')
    t.line([(2,61),(2,2),(61,2)],'#83887d')
    for x,y in ((4,4),(59,4),(4,59),(59,59)):
        t.bolt(x,y)
    return t


def make_block(name):
    if name in ARCH:
        return architecture(name)
    t=layout.make_block(name)
    # Repaint broad material areas with appropriate media and usage wear.
    if name.startswith('party_table'):
        # Waxed, worn tablecloth instead of a glossy checkerboard toy.
        cloth=Canvas(name,'#a6a18c',64,'cloth')
        cloth.checker((0,0,63,63),16,'#805e59','#b2ae98')
        cloth.kind='cloth'
        for y in range(3,64,9):
            cloth.line([(0,y),(63,y)],'#9b9380',kind='cloth')
        if name.endswith('_top'):
            cloth.ellipse((7,9,32,34),'#aba99b','#73766a',kind='ceramic',relief=.06)
            cloth.ellipse((11,13,28,30),None,'#969988',kind='ceramic')
            cloth.ellipse((35,31,56,43),'#514953',kind='paper')
            cloth.poly([(45,12),(35,38),(56,38)],'#746779',kind='paper')
            cloth.line([(42,23),(49,23)],'#a19475',3,kind='paper')
            cloth.line([(9,45),(17,43),(25,46)],'#7c7767',kind='cloth')
        else:
            cloth.rect((0,48,63,63),'#665640',kind='wood')
            cloth.line([(0,49),(63,49)],'#918575',kind='wood')
        t=cloth
    if name.startswith('supply_crate'):
        # Existing layout is hardware-correct; plank regions get visible grain.
        for y in (3,20,36,58):
            for x0,x1 in ((11,15),(48,52)):
                t.line([(x0,y),(x1,y+1)],'#97836a',kind='wood')
    # Preserve only actual display/lamp material as emissive. No global glow.
    if name=='flickering_light_on':
        for x in (19,30,41):
            t.emission[20:44,x:x+4]=.72
    elif name=='emergency_light':
        t.emission[23:38,24:39]=.38
    elif name=='camera_monitor_screen':
        t.emission[9:44,9:54]=.14
        t.smooth[9:44,9:54]=145
    elif name.startswith('security_camera_lens'):
        t.smooth[17:48,17:48]=180
    # Shared shape functions sometimes use ImageDraw directly; relief remains neutral
    # there, which is intentional for printed labels and markings.
    return t


def setup_layout():
    layout.Art=Canvas
    layout.plate=plate
    layout.INK='#292f2d'
    layout.STEEL='#979e95'
    layout.LIGHT='#bbb9a7'
    layout.GOLD='#ad9a62'
    layout.RED='#89514e'
    layout.GREEN='#819b83'
    layout.BLUE='#758f98'
    layout.PURPLE='#7b7186'


def builders():
    return [('bonnie',anim.build_bonnie),('chica',anim.build_chica),('foxy',anim.build_foxy),
            ('freddy',anim.build_freddy),('golden_freddy',lambda:anim.build_freddy(True))]


def paint_entity(ident,builder):
    m,palette=builder()
    m.pack()
    geo=json.loads((ASSETS/'geo'/f'{ident}.geo.json').read_text())
    assert m.to_geo()==geo,f'Generator UVs differ from live model: {ident}'
    palette.update(PALETTE[ident])
    palette.update(metal='#90968e',joint='#5f655d',black='#343630',red='#744b49',nose='#34352e',teeth='#bfb99d',eye='#a9aa9c',led='#ccb881')
    tex=Image.new('RGBA',(512,512))
    glow=Image.new('RGBA',(512,512))
    dglow=ImageDraw.Draw(glow)
    old_scale=anim.SCALE
    anim.SCALE=4
    faces=anim.Painter(ident,palette,1)
    try:
        for ci,c in enumerate(m.cubes):
            for face,(x,y,w,h) in faces.faces(c).items():
                kind=('fur' if c.mat in ('suit','suit_light','belly','gold') else
                      'cloth' if c.mat in ('cloth','suit_alt','pink','red','black') else
                      'steel' if c.mat in ('metal','joint','hook') else
                      'rubber' if c.mat=='nose' else 'ceramic' if c.mat in ('teeth','eye','pupil') else 'paint')
                color=palette.get(c.mat,'#8c816b')
                # Render independent surface rectangles directly at native atlas density.
                patch=Canvas(f'{ident}-{ci}-{face}',color,max(16,w,h),kind)
                if kind=='fur':
                    # Upholstery piping and stitches only where panels actually join.
                    if w>12 and h>12:
                        patch.line([(2,0),(2,h-1)],'#58564b',kind='cloth',relief=-.08)
                        for yy in range(3,h-2,5):
                            patch.line([(1,yy),(4,yy+1)],'#9b927d',kind='cloth')
                    if c.detail=='seam' and face in ('front','back'):
                        patch.line([(w//2,0),(w//2,h-1)],'#49483e',kind='cloth')
                        for yy in range(3,h,6):
                            patch.line([(w//2-2,yy),(w//2+2,yy+1)],'#aca08a',kind='cloth')
                if kind=='steel':
                    if w>=12 and h>=12:
                        for xx,yy in ((3,3),(w-4,h-4)):
                            patch.bolt(xx,yy)
                        patch.line([(3,6),(4,min(h-2,12))],'#6b5b43',kind='rust')
                    if c.mat=='joint':
                        for yy in range(3,h,5):
                            patch.line([(0,yy),(w-1,yy)],'#333c35',kind='rubber')
                    if c.bone=='microphone':
                        for yy in range(1,h,3):
                            for xx in range(1,w,3):
                                patch.rect((xx,yy,xx+1,yy+1),'#353d37',kind='steel')
                if c.mat=='teeth':
                    for xx in range(0,w,4):
                        patch.line([(xx,0),(xx,h-1)],'#696b58',kind='ceramic')
                        patch.line([(xx+1,0),(min(w-1,xx+3),0)],'#8c896e',kind='ceramic')
                if c.mat=='eye':
                    # Recessed optical housing: smaller, less expressive glass lens.
                    patch.rect((0,0,w-1,h-1),'#373d36',kind='rubber')
                    if face=='front':
                        patch.ellipse((1,1,w-2,h-2),'#92968b','#525950',kind='glass')
                        patch.line([(1,1),(w-2,1)],'#4e564e',kind='steel')
                if c.mat=='pupil':
                    patch.rect((0,0,w-1,h-1),'#292f2c',kind='glass')
                    if face=='front':
                        patch.ellipse((1,1,w-2,h-2),palette['pupil'],kind='glass')
                        patch.rect((w//2,h//2,w//2,h//2),'#d4d0b9',kind='glass')
                        dglow.rectangle((x+w//2,y+h//2,x+w//2,y+h//2),fill=tuple(rgb(palette['pupil']).astype(int))+(255,))
                if c.mat=='led':
                    dglow.rectangle((x+1,y+1,x+w-2,y+h-2),fill=(173,145,91,255))
                if c.detail=='bib' and face=='front':
                    patch.text('LETS',max(2,(w-15)//2),7,'#756d58')
                    patch.text('EAT',max(2,(w-11)//2),15,'#756d58')
                    patch.line([(3,h-4),(w-4,h-3)],'#767362',kind='cloth')
                if c.detail=='tear' and face=='front':
                    cx,cy=w//2,h//2
                    patch.poly([(cx-8,cy-6),(cx-2,cy-9),(cx+7,cy-5),(cx+9,cy+4),(cx+2,cy+8),(cx-9,cy+3)],'#303630',kind='rubber')
                    for yy in range(cy-4,cy+6,4):
                        patch.line([(cx-5,yy),(cx+5,yy+1)],'#7e8479',2,kind='steel')
                    patch.line([(cx-6,cy-6),(cx-4,cy+4)],'#835f50',kind='rubber')
                if c.detail=='torn' and face not in ('top','bottom'):
                    for xx in range(w):
                        depth=(xx*7+ci)%4
                        ImageDraw.Draw(patch.im).line([(xx,h-depth),(xx,h-1)],fill=(0,0,0,0)) if depth else None
                tex.paste(patch.im.crop((0,0,w,h)),(x,y))
    finally:
        anim.SCALE=old_scale
    return tex,glow


def icon():
    t=Canvas('icon','#202a29',128,'steel')
    t.rect((5,5,122,122),None,'#807961',2)
    t.rect((12,13,115,90),'#252e2b',kind='glass')
    t.text('REC',17,18,'#8b5a50',2)
    t.text('12 AM',77,18,'#a5ab92')
    # Worn security emblem, understated rather than a bright mascot.
    t.ellipse((32,39,47,55),'#74654d',kind='fur')
    t.ellipse((81,39,96,55),'#74654d',kind='fur')
    t.rect((42,44,85,81),'#887659',kind='fur')
    t.rect((49,32,78,46),'#30362f',kind='cloth')
    t.rect((39,43,88,48),'#30362f',kind='cloth')
    for x in (50,73):
        t.rect((x,56,x+5,61),'#282d29',kind='rubber')
        t.rect((x+2,58,x+2,59),'#c9c7af',kind='glass')
    t.rect((53,69,75,78),'#a39271',kind='fur')
    t.rect((61,68,68,72),'#32352e',kind='rubber')
    t.text('NIGHT SHIFT',21,103,'#b6ac8b',2)
    return t.im


def fixed_hashes():
    return {str(p.relative_to(ASSETS)):hashlib.sha256(p.read_bytes()).hexdigest()
            for folder in ('geo','animations','models','blockstates') for p in (ASSETS/folder).rglob('*.json')}


def main():
    OUT.mkdir(parents=True,exist_ok=True)
    if not (OUT/'before').exists():
        shutil.copytree(ASSETS/'textures',OUT/'before/textures')
        shutil.copyfile(ASSETS/'icon.png',OUT/'before/icon.png')
    before=fixed_hashes()
    setup_layout()
    generated={'block':[],'item':[],'entity':[]}
    for folder in ('block','item'):
        paths=sorted(p for p in (ASSETS/'textures'/folder).glob('*.png') if not p.stem.endswith(('_n','_s')))
        for path in paths:
            t=make_block(path.stem) if folder=='block' else layout.item(path.stem)
            t.im.save(path)
            generated[folder].append((path.stem,t.im))
            if folder=='block':
                normal,spec=t.pbr()
                normal.save(path.with_stem(path.stem+'_n'))
                spec.save(path.with_stem(path.stem+'_s'))
    for ident,builder in builders():
        tex,glow=paint_entity(ident,builder)
        tex.save(ASSETS/'textures/entity'/f'{ident}.png')
        glow.save(ASSETS/'textures/entity'/f'{ident}_glowmask.png')
        generated['entity'].append((ident,tex))
    icon().save(ASSETS/'icon.png')
    for folder,images in generated.items():
        layout.contact_sheet(images,OUT/(folder+'.png'))
    assert fixed_hashes()==before,'Models/animations changed'
    payload={'albedo':{k:len(v) for k,v in generated.items()},'block_pbr_maps':146,'glow_masks':5,
             'geometry_animation_hashes':before,'raw_texture_bytes':sum(Image.open(p).width*Image.open(p).height*4 for p in (ASSETS/'textures').rglob('*.png'))}
    (OUT/'manifest.json').write_text(json.dumps(payload,indent=2),encoding='utf-8')
    # Keep a portable pixel-level provenance baseline, independent of build/ caches.
    baseline=ROOT/'tools/material_baseline.json'
    if not baseline.exists():
        original={str(p.relative_to(OUT/'before/textures')).replace('\\','/'):
                  hashlib.sha256(Image.open(p).convert('RGBA').tobytes()).hexdigest()
                  for p in (OUT/'before/textures').rglob('*.png')}
        baseline.write_text(json.dumps({'original_pixel_hashes':original,'geometry_animation_hashes':before},indent=2),encoding='utf-8')
    print('Repainted 83 block/item albedos, 5 entity atlases, 5 glow masks and icon; 146 consumed LabPBR maps.')
    print('Geometry, bones, UV assignments, animations, models and blockstates preserved byte-for-byte.')


if __name__=='__main__':
    main()
