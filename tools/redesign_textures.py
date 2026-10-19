#!/usr/bin/env python3
"""Semantic layout library and compatibility entrypoint for Night Shift art.

Run: python tools/redesign_textures.py
The CLI delegates to rework_materials.py (Pillow + numpy), including entities.
Original layouts remain available as masks for material-aware surface painting.
Coordinates in the layout helpers are 64px design coordinates.
"""
from pathlib import Path
import hashlib
import json
import math
import random
import sys

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'src/main/resources/assets/fnaf_night_shift'
INK = '#151a20'
STEEL = '#879498'
LIGHT = '#d9d5bb'
GOLD = '#dfac44'
RED = '#b84245'
GREEN = '#71d6a0'
BLUE = '#72b8cc'
PURPLE = '#8161a4'
FONT = ImageFont.load_default(size=8)

# A tiny, deliberately pixel-aligned face for labels on the actual textures.
GLYPHS = {
    'A':'010101111101101','B':'110101110101110','C':'011100100100011',
    'D':'110101101101110','E':'111100110100111','F':'111100110100100',
    'G':'011100101101011','H':'101101111101101','I':'111010010010111',
    'J':'001001001101010','K':'101101110101101','L':'100100100100111',
    'M':'101111111101101','N':'101111111111101','O':'010101101101010',
    'P':'110101110100100','Q':'010101101111011','R':'110101110101101',
    'S':'011100010001110','T':'111010010010010','U':'101101101101111',
    'V':'101101101101010','W':'101101111111101','X':'101101010101101',
    'Y':'101101010010010','Z':'111001010100111','0':'111101101101111',
    '1':'010110010010111','2':'110001010100111','3':'110001010001110',
    '4':'101101111001001','5':'111100110001110','6':'011100111101111',
    '7':'111001010010010','8':'111101111101111','9':'111101111001110',
    '-':'000000111000000','!':'010010010000010','+':'000010111010000',
    ':':'000010000010000','/':'001001010100100',' ':'000000000000000',
}


class Art:
    def __init__(self, name, color=None):
        self.name = name
        self.rng = random.Random(name)
        self.im = Image.new('RGBA', (64, 64), color or (0, 0, 0, 0))
        self.d = ImageDraw.Draw(self.im)

    def rect(self, box, color, outline=None, width=1):
        self.d.rectangle(box, fill=color, outline=outline, width=width)

    def line(self, pts, color, width=1):
        self.d.line(pts, fill=color, width=width)

    def poly(self, pts, color, outline=INK):
        self.d.polygon(pts, fill=color, outline=outline)

    def ellipse(self, box, color, outline=None, width=1):
        self.d.ellipse(box, fill=color, outline=outline, width=width)

    def text(self, text, x, y, color=LIGHT, scale=1):
        for c in text:
            bits = GLYPHS[c.upper()]
            for i, bit in enumerate(bits):
                if bit == '1':
                    px, py = x + i % 3 * scale, y + i // 3 * scale
                    self.rect((px, py, px + scale - 1, py + scale - 1), color)
            x += 4 * scale

    def label(self, text, y, color=LIGHT, scale=1):
        self.text(text, (64 - (len(text) * 4 - 1) * scale) // 2, y, color, scale)

    def noise(self, strength=5):
        p = self.im.load()
        for y in range(64):
            for x in range(64):
                r, g, b, a = p[x, y]
                if a:
                    v = self.rng.randint(-strength, strength)
                    p[x, y] = (max(0, min(255, r+v)), max(0, min(255, g+v)), max(0, min(255, b+v)), a)

    def bolt(self, x, y):
        self.rect((x-2, y-2, x+2, y+2), INK)
        self.rect((x-1, y-1, x+1, y+1), STEEL)
        self.line([(x-1, y+1), (x+1, y-1)], '#424e54')
        self.rect((x-1, y-1, x-1, y-1), LIGHT)

    def bevel(self, box, color='#59666c'):
        x, y, r, b = box
        self.rect(box, color, INK, 2)
        self.line([(x+2, b-2), (x+2, y+2), (r-2, y+2)], '#a0aaa4')
        self.line([(r-2, y+3), (r-2, b-2), (x+3, b-2)], '#303a40')

    def checker(self, box, size=4, a=INK, b=LIGHT):
        x0, y0, x1, y1 = box
        for y in range(y0, y1+1):
            for x in range(x0, x1+1):
                self.d.point((x,y), fill=a if ((x-x0)//size+(y-y0)//size)%2 else b)

    def hazard(self, box):
        x0,y0,x1,y1 = box
        for y in range(y0,y1+1):
            for x in range(x0,x1+1):
                self.d.point((x,y), fill=GOLD if (x+y)%16 < 8 else INK)

    def wear(self, amount=38):
        for _ in range(amount):
            x, y = self.rng.randrange(3,61), self.rng.randrange(3,61)
            if self.im.getpixel((x,y))[3]:
                self.line([(x,y), (min(61,x+self.rng.randint(1,3)),y)], self.rng.choice(['#62675c','#353d3b','#9d7854']))


def plate(name, color='#526267'):
    t = Art(name, color)
    t.noise(5)
    t.bevel((0,0,63,63),color)
    for x,y in [(4,4),(59,4),(4,59),(59,59)]:
        t.bolt(x,y)
    return t


def led(t,x,y,on=True):
    t.ellipse((x-3,y-3,x+3,y+3),INK)
    t.ellipse((x-2,y-2,x+2,y+2),GREEN if on else RED)
    t.rect((x-1,y-1,x,y), '#d9ffe1' if on else '#ffab72')


def bolt_symbol(t,x,y,color=GOLD):
    t.poly([(x+6,y),(x,y+10),(x+5,y+10),(x+2,y+19),(x+12,y+6),(x+6,y+6)],color)


def bear(t,x,y,s=1):
    # Small Fazbear-style maintenance emblem, not an entity skin.
    for dx in (0,12):
        t.ellipse((x+dx*s,y+3*s,x+(dx+6)*s,y+9*s),GOLD,INK)
    t.ellipse((x+3*s,y+4*s,x+15*s,y+17*s),GOLD,INK)
    t.rect((x+6*s,y,x+12*s,y+5*s),INK)
    t.rect((x+4*s,y+4*s,x+14*s,y+5*s),INK)
    for dx in (6,11):
        t.rect((x+dx*s,y+9*s,x+(dx+1)*s,y+10*s),INK)
    t.ellipse((x+6*s,y+12*s,x+12*s,y+15*s),LIGHT)
    t.rect((x+8*s,y+12*s,x+10*s,y+13*s),INK)


def fan(t,cx=32,cy=32,r=17,broken=False):
    t.ellipse((cx-r-2,cy-r-2,cx+r+2,cy+r+2),STEEL,INK,2)
    t.ellipse((cx-r,cy-r,cx+r,cy+r),INK)
    for a in range(4):
        pts=[]
        for dx,dy in [(2,0),(r-2,-6),(r-1,3),(6,8)]:
            ang=a*math.pi/2
            pts.append((round(cx+dx*math.cos(ang)-dy*math.sin(ang)),round(cy+dx*math.sin(ang)+dy*math.cos(ang))))
        t.poly(pts,'#6e7774' if not broken else '#62514a')
    t.ellipse((cx-3,cy-3,cx+3,cy+3),GOLD,INK)
    for dy in range(-r+4,r-2,6):
        span=int(math.sqrt(r*r-dy*dy))
        t.line([(cx-span,cy+dy),(cx+span,cy+dy)],'#363e43')
    if broken:
        t.line([(cx-r,cy-6),(cx-4,cy+3),(cx+7,cy-5),(cx+r,cy+7)],'#d9b68c',2)


def screen(t,box,broken=False):
    t.bevel(box,'#30393c')
    x,y,r,b=box
    t.rect((x+3,y+3,r-3,b-3),'#102c29' if not broken else '#271c27')
    for yy in range(y+4,b-3,3):
        t.line([(x+4,yy),(r-4,yy)],'#183d34' if not broken else '#46303e')
    t.line([(x+4,b-5),(x+4,y+4),(r-5,y+4)],'#45685b')


def cracks(t,box=(10,16,53,49)):
    x,y,r,b=box
    t.line([(x,y),(x+8,y+9),(x+5,y+16),(x+18,y+20),(r,b)],INK,3)
    t.line([(x,y),(x+8,y+9),(x+5,y+16),(x+18,y+20),(r,b)],'#b9b0a0')
    t.line([(x+8,y+9),(x+17,y+7),(x+20,y+1)],INK,2)


def item(name):
    t=Art(name)
    if name in ('wrench','screwdriver','pliers','flashlight','flashlight_on','fuse','battery'):
        # Draw upright, then rotate pixel-perfect into Minecraft's handheld diagonal.
        if name == 'wrench':
            t.poly([(26,27),(23,22),(21,14),(24,7),(28,4),(28,16),(35,18),(39,13),(37,4),(42,8),(45,15),(43,24),(36,29),(36,54),(32,59),(26,56)],STEEL)
            t.line([(27,29),(27,53),(31,56)],'#d8e0d8',2)
            t.rect((29,32,34,48),'#56656b')
            t.ellipse((28,51,33,56),INK)
            t.line([(25,11),(24,18),(27,23)],LIGHT,2)
        elif name == 'screwdriver':
            t.poly([(30,5),(34,5),(35,12),(34,34),(29,34),(29,12)],STEEL)
            t.line([(30,8),(30,33)],LIGHT)
            t.bevel((25,32,38,55),RED)
            t.rect((25,31,38,35),GOLD,INK)
            for x in (28,32,36):
                t.line([(x,39),(x,51)],'#6e2839')
            t.rect((28,54,35,57),INK)
        elif name == 'pliers':
            t.poly([(22,6),(28,10),(30,21),(34,21),(36,10),(42,6),(40,23),(35,31),(42,52),(38,57),(33,54),(29,35),(25,54),(20,56),(17,52),(25,29),(21,20)],STEEL)
            t.line([(24,33),(20,51)],'#407db1',7)
            t.line([(34,33),(39,51)],'#407db1',7)
            t.line([(23,35),(20,49)],'#85bdd3',2)
            t.line([(33,35),(37,49)],'#85bdd3',2)
            t.ellipse((26,24,33,31),GOLD,INK)
            for y in (10,14,18):
                t.line([(25,y),(28,y+1)],INK)
                t.line([(35,y+1),(38,y)],INK)
        elif name.startswith('flashlight'):
            t.bevel((24,26,39,55),'#333e4c')
            for y in range(37,53,4):
                t.rect((25,y,38,y+1),'#121c28')
                t.line([(26,y+2),(28,y+2)],STEEL)
            t.poly([(24,29),(18,20),(18,10),(45,10),(45,20),(39,29)],'#5e6974')
            t.rect((19,10,44,16),GOLD,INK)
            t.rect((22,11,41,13), '#fff4b9' if name.endswith('_on') else '#a7c2c5')
            t.rect((28,29,35,35),INK)
            t.rect((30,29,33,31),GREEN if name.endswith('_on') else RED)
            t.rect((24,54,39,57),GOLD,INK)
            if name.endswith('_on'):
                for x in (22,31,40):
                    t.line([(x,3),(x,7)],'#fff0a3',2)
        elif name == 'fuse':
            t.rect((23,10,40,53),INK)
            t.rect((25,18,38,45),'#517b82')
            t.rect((27,19,29,44),'#d6ece0')
            t.line([(32,19),(35,25),(31,31),(35,37),(32,45)],GOLD,2)
            for y in (10,46):
                t.bevel((23,y,40,y+8),STEEL)
                t.line([(26,y+2),(37,y+2)],LIGHT)
        else:
            t.rect((26,7,37,11),STEEL,INK)
            t.bevel((20,11,43,56),'#303b45')
            t.rect((22,14,41,29),GOLD)
            t.text('+',28,17,INK,2)
            bolt_symbol(t,26,32)
            t.line([(23,32),(23,51)],STEEL)
            t.rect((23,53,40,54),'#99a39d')
        t.im=t.im.rotate(-38,Image.Resampling.NEAREST)
        t.d=ImageDraw.Draw(t.im)
    elif name == 'cable':
        for box in [(9,12,52,48),(13,17,48,44),(17,22,44,40)]:
            t.d.arc(box,20,345,fill=INK,width=7)
            t.d.arc(tuple(v-1 if i%2 else v for i,v in enumerate(box)),20,345,fill=RED,width=4)
        t.line([(50,32),(54,21),(50,10)],INK,6)
        t.line([(50,32),(54,21),(50,10)],RED,3)
        t.line([(11,30),(7,43),(13,53)],INK,6)
        t.line([(11,30),(7,43),(13,53)],BLUE,3)
        t.rect((46,6,52,12),GOLD,INK)
        t.rect((10,51,16,57),GOLD,INK)
        t.rect((26,12,33,23),'#bdbba5',INK)
    elif name == 'electronic_parts':
        t.bevel((8,12,55,52),'#346b52')
        for y in (19,26,33,40,46):
            t.line([(12,y),(19,y),(22,y-3),(49,y-3)],'#a7b76b')
        t.bevel((24,23,40,40),'#222936')
        for x in range(25,41,4):
            t.rect((x,20,x+1,22),LIGHT)
            t.rect((x,41,x+1,44),LIGHT)
        for y in range(25,40,4):
            t.rect((21,y,23,y+1),STEEL)
            t.rect((41,y,43,y+1),STEEL)
        for x in (13,47):
            t.ellipse((x,16,x+5,22),BLUE,INK)
        for x in range(15,48,5):
            t.rect((x,50,x+2,55),GOLD,INK)
        t.text('FS',28,28,LIGHT)
        led(t,48,42)
    elif name == 'security_door':
        top=door(True).im.resize((28,27),Image.Resampling.NEAREST)
        bottom=door(False).im.resize((28,27),Image.Resampling.NEAREST)
        t.im.alpha_composite(top,(18,5))
        t.im.alpha_composite(bottom,(18,32))
    else:
        raise ValueError(name)
    return t


STATIONS={'generator':('POWER',GOLD),'fuse_box':('FUSES','#cd9563'),
          'wiring':('CABLES',BLUE),'electrical_panel':('SEQUENCE',PURPLE),
          'lighting':('LIGHTS','#d7c78a'),'auto_door':('DOOR',RED),
          'ventilation':('AIR FLOW','#65b6aa'),'terminal':('CODE','#82a1c3'),
          'security':('CCTV','#84ae83')}


def station(kind,broken):
    # Both states share the exact same housing, scratches, and fasteners.
    label,color=STATIONS[kind]
    t=plate('station_'+kind)
    t.wear(30)
    t.rect((8,5,55,12),INK)
    t.label(label,7,color)
    t.bevel((7,15,56,49),'#333e42')
    if kind=='generator':
        t.ellipse((11,18,32,37),LIGHT,INK,2)
        for x,y in [(15,29),(17,23),(23,21),(28,25)]:
            t.rect((x,y,x+1,y+2),INK)
        t.line([(22,32),(15,29) if broken else (26,23)],RED,2)
        bolt_symbol(t,38,19)
        for x in range(12,52,5):
            t.rect((x,41,x+2,46),INK)
    elif kind=='fuse_box':
        for i,x in enumerate((13,27,41)):
            t.rect((x-1,20,x+9,43),INK)
            t.rect((x+1,24,x+7,39),'#86b8b4' if not broken or i==0 else '#463b37')
            t.line([(x+4,25),(x+2,30),(x+5,34),(x+3,38)],GOLD if not broken or i==0 else '#201c22')
            for y in (20,40):
                t.rect((x,y,x+8,y+3),STEEL)
            if broken and i>0:
                t.line([(x+1,30),(x+7,35)],INK,3)
    elif kind=='wiring':
        for i,c in enumerate((RED,BLUE,GOLD,GREEN)):
            y=22+i*7
            t.rect((10,y-2,15,y+2),STEEL,INK)
            t.rect((48,y-2,53,y+2),STEEL,INK)
            pts=[(15,y),(22,y),(38,y+(3 if i%2 else -3)),(48,y)]
            t.line(pts,INK,5)
            t.line(pts,c,3)
            if broken and i in (0,2):
                t.rect((28,y-3,34,y+3),'#333e42')
                t.line([(26,y),(28,y+3)],GOLD)
                t.line([(35,y),(37,y-3)],GOLD)
    elif kind=='electrical_panel':
        for i,x in enumerate((12,23,34,45)):
            t.rect((x,21,x+6,41),INK)
            t.rect((x+1,24+(i%2)*8,x+5,29+(i%2)*8),STEEL, '#d4d9c8')
            led(t,x+3,45,not broken)
            t.text(str(i+1),x+2,16,GOLD)
    elif kind=='lighting':
        for x in (19,43):
            t.ellipse((x-7,19,x+7,33),'#f6e5a3' if not broken else '#68665d',INK)
            t.rect((x-4,32,x+4,38),STEEL,INK)
            t.line([(x-3,26),(x,30),(x+3,25)],GOLD)
            t.line([(x-3,40),(x+3,40)],LIGHT)
        if broken:
            cracks(t,(14,21,24,32))
        else:
            for x in (19,43):
                t.line([(x-10,26),(x-12,26)],GOLD)
                t.line([(x+10,26),(x+12,26)],GOLD)
    elif kind=='auto_door':
        t.bevel((15,18,43,46),STEEL)
        t.rect((18,21,40,43),INK)
        t.rect((19,22,29,42),'#748085')
        t.rect((32,22,39,42),'#748085')
        t.hazard((19,35,39,40))
        t.line([(30,22),(30,42)],RED if broken else GREEN,2)
        t.poly([(10,28),(14,31),(10,34)],GOLD)
        t.poly([(49,28),(45,31),(49,34)],GOLD)
        if broken:
            t.line([(21,24),(36,40)],'#3a2829',3)
    elif kind=='ventilation':
        fan(t,32,32,14,broken)
    elif kind=='terminal':
        screen(t,(10,17,53,35),broken)
        t.text('ERR' if broken else '1987',18,23,RED if broken else GREEN)
        for y in (39,44):
            for x in range(13,49,6):
                t.rect((x,y,x+3,y+2),STEEL)
        if broken:
            cracks(t,(30,19,46,31))
    else:
        for x,y in [(10,17),(33,17),(10,32),(33,32)]:
            screen(t,(x,y,x+20,y+13),broken)
            if broken:
                t.line([(x+4,y+4),(x+16,y+9)],'#98728b')
                t.line([(x+4,y+9),(x+16,y+4)],'#98728b')
            else:
                t.line([(x+4,y+9),(x+9,y+5),(x+16,y+9)],GREEN)
                t.rect((x+9,y+6,x+10,y+9),'#b6cf9a')
    t.checker((8,52,24,59),4)
    t.text('FAIL' if broken else 'RUN',28,54,RED if broken else GREEN)
    led(t,53,56,not broken)
    if broken:
        t.line([(8,18),(12,29),(9,35)],'#211b20',2)
        t.line([(47,47),(52,43),(55,46)],'#b58a65')
    return t


def door(top):
    t=Art('door_top' if top else 'door_bottom','#697477')
    t.noise(6)
    for x in (0,5,58,63):
        t.line([(x,0),(x,63)],INK if x in (0,63) else '#a2aaa2',2)
    if top:
        t.rect((8,0,55,7),INK)
        t.label('SECURITY',2,GOLD)
        t.bevel((12,12,51,43),'#3d484e')
        t.rect((16,16,47,39),'#152c35')
        t.poly([(17,17),(28,17),(45,38),(36,38)],'#315260',None)
        for x in (22,32,42):
            t.line([(x,16),(x,39)],'#84928f',2)
        t.line([(16,27),(47,27)],'#84928f',2)
        t.label('KEEP CLOSED',50,LIGHT)
        t.line([(9,62),(54,62)],'#3c484b')
    else:
        t.bevel((11,7,52,39),'#505c61')
        for y in range(13,34,5):
            t.rect((16,y,47,y+1),INK)
            t.line([(16,y+2),(47,y+2)],STEEL)
        t.hazard((7,45,56,58))
        t.line([(6,63),(57,63)],INK,2)
        t.rect((48,2,53,14),INK)
        t.rect((49,4,51,12),STEEL)
    for x in (9,54):
        for y in (10,60):
            t.bolt(x,y)
    t.wear(24)
    return t


def surface(name):
    t=Art(name,'#70746d')
    if name in ('industrial_concrete','cracked_concrete'):
        t.noise(9)
        for _ in range(70):
            x,y=t.rng.randrange(64),t.rng.randrange(64)
            t.rect((x,y,x+1,y+1),t.rng.choice(['#575e58','#8a8c7e','#62675e']))
        if name=='cracked_concrete':
            t.line([(0,17),(12,20),(20,29),(32,32),(42,44),(53,48),(63,55)],'#393d39',2)
            t.line([(20,29),(22,17),(30,9),(29,0)],'#393d39')
            t.line([(42,44),(40,56),(44,63)],'#393d39')
    elif name in ('checkered_floor','dirty_tiles'):
        size=32 if name=='checkered_floor' else 16
        t.checker((0,0,63,63),size,'#343b40' if size==32 else '#88958a','#c7c3a9')
        t.noise(5)
        for y in range(0,64,size):
            for x in range(0,64,size):
                t.rect((x,y,x+size-1,y+size-1),None,'#535a53')
                t.line([(x+2,y+size-3),(x+2,y+2),(x+size-3,y+2)],'#8e9587')
        t.wear(80)
        t.line([(44,33),(42,40),(47,44)],'#5b6259')
    elif name in ('industrial_wall','industrial_wall_stripe','pizzeria_wall'):
        t=Art(name,'#a2a28d' if name=='pizzeria_wall' else '#7d8982')
        t.noise(5)
        for y in range(0,64,16):
            t.line([(0,y),(63,y)],'#4d5855',2)
            t.line([(0,y+2),(63,y+2)],'#adb0a0')
            for x in range((-16 if y%32 else 0),64,32):
                t.line([(x,y),(x,y+15)],'#4d5855')
        if name=='industrial_wall_stripe':
            t.rect((0,29,63,43),INK)
            t.hazard((0,31,63,40))
        if name=='pizzeria_wall':
            t.rect((0,29,63,35),RED)
            t.line([(0,30),(63,30)],'#e18170')
            t.checker((0,36,63,51),8)
            t.rect((0,52,63,63),'#455657')
            t.line([(0,53),(63,53)],'#829083')
        t.wear(45)
    elif name=='stage_floor':
        t=Art(name,'#79523d')
        for y in range(0,64,16):
            t.rect((0,y,63,y+1),'#342b2b')
            t.line([(0,y+2),(63,y+2)],'#bc8b58')
            x=16 if y%32 else 48
            t.line([(x,y),(x,y+15)],'#342b2b')
            for yy in range(y+5,y+15,3):
                t.line([(0,yy),(63,yy)],t.rng.choice(['#624333','#926347','#a2714d']))
            for xx in (x-3,x+3):
                t.rect((xx,y+4,xx+1,y+5),INK)
        t.noise(4)
        t.ellipse((29,20,41,26),None,'#4e382e')
        t.wear(24)
    elif name=='stage_curtain':
        t=Art(name,'#682a40')
        for x in range(64):
            v=int(22*math.cos(x*math.pi/8))
            t.line([(x,0),(x,63)],(100+v,32+v//3,55+v//2))
        for x in range(-8,64,16):
            t.d.arc((x,1,x+32,22),0,180,fill=GOLD,width=2)
        t.line([(0,57),(63,57)],'#d4a24e',2)
        t.line([(0,61),(63,61)],'#412738',2)
        for x in (10,42):
            t.poly([(x,30),(x+2,34),(x+6,35),(x+2,37),(x,41),(x-2,37),(x-5,35),(x-2,34)],GOLD,None)
        t.noise(3)
    elif name in ('metal_grate','vent_grate'):
        t=Art(name)
        for x in range(0,64,8):
            t.rect((x,0,x+2,63),'#566970')
            t.line([(x,0),(x,63)],'#a3b2ad')
        for y in range(0,64,8):
            t.rect((0,y,63,y+2),'#687b7d')
            t.line([(0,y),(63,y)],'#a3b2ad')
        t.rect((0,0,63,63),None,INK,2)
        if name=='vent_grate':
            t.rect((2,2,61,61),None,STEEL,4)
            t.bevel((23,27,40,36),'#48565b')
            t.rect((28,30,35,33),INK)
            for x in (7,56):
                for y in (7,56):
                    t.bolt(x,y)
    elif name=='cable_bundle':
        t=plate(name,'#313d43')
        for x,c in [(10,RED),(21,GOLD),(32,BLUE),(43,'#77935d'),(54,PURPLE)]:
            t.line([(x,0),(x-3,18),(x+2,42),(x,63)],INK,8)
            t.line([(x,0),(x-3,18),(x+2,42),(x,63)],c,5)
            t.line([(x-1,0),(x-4,18),(x+1,42),(x-1,63)],'#b1aaa0')
        for y in (12,46):
            t.bevel((2,y,61,y+6),'#66716c')
            t.bolt(5,y+3)
            t.bolt(58,y+3)
    else:
        colors={'generator_casing':'#4f6d59','rusty_metal':'#695246','ceiling_panel':'#94988a','machine_top':'#46565a','machine_side':'#526267','tool_locker_side':'#53716d','security_camera_body':'#b2b4a0'}
        t=plate(name,colors.get(name,'#5a6971'))
        if name=='hazard_block':
            t.hazard((7,7,56,56))
            t.rect((7,7,56,56),None,INK,2)
        elif name in ('machine_side','generator_casing','tool_locker_side','security_camera_body'):
            t.bevel((9,11,54,45),colors.get(name,'#526267'))
            for y in range(17,42,5):
                t.rect((14,y,49,y+1),INK)
                t.line([(14,y+2),(49,y+2)],STEEL)
            t.checker((10,51,33,58),4)
            t.text('FS',41,52,GOLD)
        elif name=='machine_top':
            fan(t,32,32,22)
        elif name=='ceiling_panel':
            t.bevel((7,7,56,56),'#999d8f')
            for y in range(12,54,4):
                for x in range(12,54,4):
                    t.rect((x,y,x,y),'#5e6a64')
            t.line([(8,48),(14,47),(18,50),(25,49)],'#77775e')
        else:
            for y in range(10,60,12):
                for x in range(8,60,12):
                    t.line([(x,y+3),(x+4,y)],'#a0a99f',2)
                    t.line([(x+1,y+5),(x+5,y+2)],'#303b40')
        if name=='rusty_metal':
            for _ in range(40):
                x,y=t.rng.randrange(4,59),t.rng.randrange(4,59)
                t.ellipse((x,y,x+t.rng.randrange(2,9),y+t.rng.randrange(2,8)),t.rng.choice(['#8a5033','#a4683c','#533b30']))
        t.wear(30)
    return t


def sign(kind):
    colors={'exit':'#28634d','danger':GOLD,'pizzeria':'#913b46','staff':'#365f83'}
    t=Art('sign_'+kind)
    # Models crop to UV y=3..13, i.e. native pixels 12..52.
    t.rect((0,12,63,51),colors[kind],INK,2)
    t.rect((3,15,60,48),None,LIGHT)
    if kind=='exit':
        t.text('EXIT',20,22,LIGHT,2)
        t.poly([(7,32),(14,25),(14,29),(18,29),(18,35),(14,35),(14,39)],LIGHT,None)
        t.line([(23,39),(52,39)],LIGHT,2)
    elif kind=='danger':
        t.poly([(16,18),(5,42),(27,42)],INK,None)
        t.text('!',13,27,GOLD,2)
        t.text('DANGER',32,24,INK)
        t.text('HIGH',35,34,INK)
        t.text('VOLT',35,41,INK)
    elif kind=='pizzeria':
        t.poly([(8,21),(27,25),(16,43)],'#edbc62')
        t.line([(8,21),(27,25)],'#c88740',4)
        for x,y in [(13,27),(20,28),(16,34)]:
            t.ellipse((x,y,x+3,y+3),RED)
        t.text('FREDDY',33,22,GOLD)
        t.text('PIZZA',34,32,LIGHT)
        t.checker((31,41,57,44),3)
    else:
        t.ellipse((10,21,19,30),LIGHT)
        t.poly([(7,43),(8,34),(14,31),(21,34),(23,43)],LIGHT,None)
        t.text('STAFF',32,24)
        t.text('ONLY',34,35)
    for x in (5,58):
        t.rect((x,17,x+1,18),STEEL)
        t.rect((x,45,x+1,46),STEEL)
    return t


def utility(name):
    t=plate(name)
    if name.startswith('security_door_'):
        return door(name.endswith('_top'))
    if name.startswith('sign_'):
        return sign(name[5:])
    if name.startswith('security_camera_lens'):
        broken=name.endswith('_broken')
        t=plate('camera_lens','#38454b')
        for r,c in [(25,INK),(23,STEEL),(21,'#475b63'),(18,INK),(16,'#234957'),(12,'#367c8c'),(8,'#19323e'),(5,'#0d1925')]:
            t.ellipse((32-r,32-r,32+r,32+r),c)
        t.ellipse((22,21,28,25),'#aecfd0')
        t.rect((37,39,39,41),BLUE)
        led(t,9,9,False)
        if broken:
            t.ellipse((7,7,11,11),'#503238')
            cracks(t,(15,16,49,49))
            t.line([(28,34),(18,42),(14,40)],LIGHT)
        return t
    if name=='camera_monitor_screen':
        screen(t,(5,5,58,48))
        t.line([(12,37),(21,23),(43,23),(52,37)],'#51836c',2)
        t.line([(21,23),(21,37),(43,37),(43,23)],'#385b4b')
        for x in (26,38):
            t.rect((x,26,x+3,33),'#0a181a')
        t.text('CAM 01',10,10,GREEN)
        t.text('12 AM',32,40,GREEN)
        t.rect((48,10,50,12),RED)
        t.text('REC',8,52,LIGHT)
        led(t,53,55)
        for x in (30,38,46):
            t.rect((x,53,x+3,56),STEEL,INK)
    elif name=='vent_control_panel':
        # Entire functional face lives inside UV [3,3,13,13].
        t.bevel((12,12,51,51),'#657873')
        fan(t,32,29,11)
        t.rect((16,43,47,49),INK)
        t.text('RESET',18,44,GREEN)
        led(t,44,46)
    elif name.startswith('flickering_light_'):
        on=name.endswith('_on')
        t.bevel((12,12,51,51),'#424d50')
        for x in (18,29,40):
            t.rect((x,16,x+6,47),INK)
            t.rect((x+1,18,x+5,45),'#e2d7a7' if on else '#65726f')
            t.line([(x+2,19),(x+2,44)],'#fff8d5' if on else '#85918a',2)
            for y in (17,45):
                t.rect((x,y,x+6,y+2),STEEL)
        for y in (24,39):
            t.line([(15,y),(48,y)],'#4b5656')
    elif name=='emergency_light':
        t.bevel((6,10,57,53),'#3a4147')
        t.ellipse((11,14,52,49),'#6b2735',INK,2)
        t.ellipse((15,17,48,46),'#bd3945')
        t.ellipse((19,20,44,42),'#e75f4f')
        t.ellipse((23,23,39,38),'#ffb06d')
        t.line([(21,20),(37,20)],'#ffdec0',2)
        for x in (18,32,46):
            t.line([(x,14),(x,49)],'#412b32',2)
        t.text('ALARM',22,55,GOLD)
    elif name.startswith('shelter_core_'):
        if name.endswith('_top'):
            t.hazard((8,8,55,55))
            t.bevel((13,13,50,50),'#243b40')
            bear(t,23,17)
            t.label('NIGHT',38,GOLD)
            t.label('SHIFT',44,LIGHT)
        else:
            screen(t,(8,7,55,33))
            t.text('12:00',13,13,GREEN,2)
            t.label('OFFICE',26,GREEN)
            t.bevel((8,38,33,54),'#303b45')
            t.rect((11,41,28,51),GREEN,INK)
            t.rect((29,44,31,48),STEEL)
            t.text('+',17,44,INK)
            bolt_symbol(t,41,37)
            t.checker((8,58,55,61),4)
    elif name.startswith('tool_locker_front_'):
        tool=name.removeprefix('tool_locker_front_')
        t=plate('locker','#54736d')
        t.bevel((7,6,56,57),'#719084')
        t.rect((12,17,46,49),'#253b3c',INK,2)
        if tool=='empty':
            t.rect((10,11,49,53),'#1b292e')
            t.line([(11,38),(47,38)],STEEL,2)
            t.line([(28,17),(28,21),(31,21)],STEEL)
            t.poly([(50,9),(57,5),(57,58),(50,53)],'#80978a')
            t.label('EMPTY',44,'#6e827e')
        else:
            icon=item(tool).im.resize((36,36),Image.Resampling.NEAREST)
            t.im.alpha_composite(icon,(11,16))
            t.text({'wrench':'MECH','screwdriver':'ELEC','pliers':'WIRE'}[tool],20,9,GOLD)
            t.rect((49,28,52,40),INK)
            t.rect((50,29,51,36),STEEL)
            led(t,49,50)
        t.checker((11,54,43,57),4)
    elif name.startswith('supply_crate_'):
        t=Art(name,'#6b513b')
        for y in range(0,64,16):
            t.rect((0,y,63,y+1),'#302d28')
            t.line([(0,y+2),(63,y+2)],'#ad8553')
            for yy in (y+6,y+11):
                t.line([(0,yy),(63,yy)],'#7d6142')
        for x in (3,54):
            t.bevel((x,0,x+6,63),'#66716b')
            for y in (7,56):
                t.bolt(x+3,y)
        if name.endswith('_open'):
            t.bevel((11,7,52,56),'#392f29')
            t.rect((15,11,48,51),'#171e21')
            t.line([(16,42),(31,35),(47,42)],'#453d30',2)
            t.text('EMPTY',22,24,'#776c54')
        elif name.endswith('_top'):
            t.bevel((18,14,45,48),'#c4b58a')
            bolt_symbol(t,25,20,RED)
            t.text('PARTS',22,41,INK)
        else:
            t.rect((16,15,47,48),'#c4b58a',INK,2)
            t.text('SUPPLY',20,19,INK)
            t.text('+',25,28,RED,4)
            t.checker((16,48,47,53),4)
        t.noise(3)
    elif name.startswith('party_table_'):
        t=Art(name,'#c4bca6')
        t.checker((0,0,63,63),8,'#a5414c','#d4ccb3')
        t.noise(4)
        if name.endswith('_top'):
            t.ellipse((8,9,33,34),'#f0dfb4','#837b6c',2)
            t.ellipse((12,13,29,30),None,'#b8ad92')
            t.ellipse((33,27,57,43),'#694456',INK)
            t.poly([(45,9),(33,37),(56,37)],PURPLE)
            t.poly([(41,19),(49,18),(52,25),(38,25)],GOLD,None)
            t.ellipse((43,7,47,11),LIGHT)
            for x,y,c in [(8,44,BLUE),(23,53,GOLD),(48,53,GREEN),(54,15,RED),(27,7,PURPLE)]:
                t.line([(x,y),(x+3,y+2)],c,2)
        else:
            for x in range(0,64,8):
                t.line([(x,4),(x,46)],'#733744')
            t.rect((0,48,63,63),'#4b3931')
            t.line([(0,48),(63,48)],GOLD,2)
            t.checker((0,52,63,59),4)
    else:
        raise ValueError('Missing design: '+name)
    return t


ZONES={'general':('#598aca','HALL'),'pizzeria':(RED,'PIZZA'),'service':(GOLD,'SERV'),
       'technical':('#579e79','TECH'),'tunnel':(PURPLE,'VENT')}
KINDS={'any':('#98a2a5','ANY'),'bonnie':(PURPLE,'BONNIE'),'chica':(GOLD,'CHICA'),
       'foxy':(RED,'FOXY'),'freddy':('#b78050','FREDDY')}


def marker(name):
    patrol=name.startswith('patrol_')
    kind=name.split('_')[-1]
    color,label=(ZONES if patrol else KINDS)[kind]
    t=plate(name,'#303e47')
    t.rect((7,7,56,59),None,color,2)
    if patrol:
        t.ellipse((17,13,46,42),None,color,2)
        for x,y in [(23,23),(36,31)]:
            t.ellipse((x-2,y-5,x+3,y+2),color,INK)
            t.rect((x-2,y+4,x+3,y+6),color)
        t.poly([(46,18),(52,23),(44,24)],color,None)
    else:
        t.line([(13,27),(50,27)],'#596771')
        t.line([(32,12),(32,42)],'#596771')
        if kind=='freddy':
            bear(t,23,16)
        elif kind=='bonnie':
            for x in (23,35):
                t.rect((x,12,x+5,26),color,INK)
            t.ellipse((21,23,43,40),color,INK)
            for x in (26,36):
                t.rect((x,29,x+2,31),LIGHT)
            t.poly([(26,42),(32,39),(38,42),(32,45)],RED)
        elif kind=='chica':
            t.ellipse((20,17,44,38),color,INK)
            t.poly([(28,31),(36,31),(32,37)],'#d17e3b')
            for x in (26,36):
                t.rect((x,25,x+2,27),INK)
            t.poly([(24,38),(40,38),(37,44),(27,44)],LIGHT)
        elif kind=='foxy':
            t.poly([(21,28),(18,15),(28,21),(36,21),(46,15),(43,30),(37,40),(26,40)],color)
            t.rect((24,26,30,31),INK)
            t.line([(21,22),(41,34)],INK)
            t.rect((36,26,38,28),GOLD)
            t.poly([(27,34),(39,34),(32,40)],'#d5aa82')
        else:
            t.ellipse((20,15,44,39),color,INK)
            t.text('?',28,22) if '?' in GLYPHS else t.text('+',26,21,INK,3)
        t.text('SPAWN',22,43,color)
    t.label(label,50,color)
    return t


SURFACES=set('industrial_concrete cracked_concrete metal_plating rusty_metal industrial_wall industrial_wall_stripe checkered_floor dirty_tiles pizzeria_wall hazard_block ceiling_panel stage_floor stage_curtain cable_bundle metal_grate vent_grate generator_casing machine_top machine_side tool_locker_side security_camera_body'.split())


def make_block(name):
    if name.startswith('station_'):
        kind=name.removeprefix('station_').split('_front')[0]
        return station(kind,name.endswith('_broken'))
    if name.startswith(('patrol_node_','spawn_marker_')):
        return marker(name)
    if name in SURFACES:
        return surface(name)
    return utility(name)


def mod_icon():
    t=Art('mod_icon','#111d27')
    t.rect((1,1,62,62),None,GOLD)
    for y in range(4,49,3):
        t.line([(3,y),(60,y)],'#1c2e38')
    t.text('REC',5,5,RED)
    t.rect((19,5,21,7),RED)
    t.text('12 AM',40,5,GREEN)
    t.poly([(4,42),(17,16),(47,16),(59,42)],'#293a3d',None)
    t.rect((17,18,47,44),'#111a22')
    bear(t,14,12,2)
    for x in (26,36):
        t.rect((x,30,x+2,32),'#f3ecbb')
        t.rect((x+1,31,x+1,32),'#78cbd0')
    t.checker((3,47,60,51),4)
    t.label('NIGHT SHIFT',55,GOLD)
    return t.im.resize((128,128),Image.Resampling.NEAREST)


def entity_hashes():
    return {str(p.relative_to(ASSETS)):hashlib.sha256(p.read_bytes()).hexdigest()
            for p in (ASSETS/'textures').rglob('*.png')
            if p.parent.name not in ('block','item')}


def contact_sheet(images,path,cols=6):
    cellw,cellh=192,160
    sheet=Image.new('RGB',(cols*cellw,math.ceil(len(images)/cols)*cellh),'#17212c')
    d=ImageDraw.Draw(sheet)
    for i,(name,im) in enumerate(images):
        x,y=(i%cols)*cellw,(i//cols)*cellh
        for yy in range(y+4,y+132,8):
            for xx in range(x+32,x+160,8):
                d.rectangle((xx,yy,xx+7,yy+7),fill='#34414b' if ((xx-x)//8+(yy-y)//8)%2 else '#28343e')
        preview=im.resize((128,128),Image.Resampling.NEAREST)
        sheet.paste(preview,(x+32,y+4),preview)
        # Keep long state names readable without abbreviating the actual filenames.
        lines=[name] if len(name)<=32 else [name[:name.rfind('_',0,32)],name[name.rfind('_',0,32)+1:]]
        for j,line in enumerate(lines):
            d.text((x+5,y+135+j*10),line,font=FONT,fill='#e1dbc5')
    sheet.save(path)


def legacy_main():
    before=entity_hashes()
    rendered={}
    for folder,renderer in [('block',make_block),('item',item)]:
        paths=sorted((ASSETS/'textures'/folder).glob('*.png'))
        if not paths:
            raise RuntimeError('No source textures in '+folder)
        rendered[folder]=[(p,renderer(p.stem).im) for p in paths]
    # Render all designs before writing so missing coverage cannot partially replace assets.
    for entries in rendered.values():
        for p,im in entries:
            assert im.size==(64,64) and im.mode=='RGBA',p
            im.save(p)
    mod_icon().save(ASSETS/'icon.png')
    preview=ROOT/'build/texture-preview'
    preview.mkdir(parents=True,exist_ok=True)
    for folder,entries in rendered.items():
        contact_sheet([(p.stem,im) for p,im in entries],preview/(folder+'.png'))
    assert before==entity_hashes(),'Entity assets changed!'
    print(f'Redesigned {len(rendered["block"])} block + {len(rendered["item"])} item textures at 64px; icon at 128px.')
    print('Entity textures unchanged. Contact sheets: build/texture-preview/')


def legacy_verify():
    count=0
    for folder,renderer in [('block',make_block),('item',item)]:
        for path in sorted((ASSETS/'textures'/folder).glob('*.png')):
            with Image.open(path) as im:
                im.load()
                assert im.size==(64,64) and im.mode=='RGBA',path
                assert im.tobytes()==renderer(path.stem).im.tobytes(),f'Non-reproducible texture: {path}'
                alpha=im.getchannel('A')
                transparent=(folder=='item' or path.stem in ('metal_grate','vent_grate') or path.stem.startswith('sign_'))
                assert alpha.getextrema()==((0,255) if transparent else (255,255)),f'Unexpected alpha: {path}'
                assert set(alpha.tobytes())<={0,255},f'Partial alpha: {path}'
                if folder=='item':
                    x0,y0,x1,y1=alpha.getbbox()
                    assert x0>0 and y0>0 and x1<64 and y1<64,f'Clipped item: {path}'
                if path.stem.endswith('_broken'):
                    normal=path.with_name(path.stem.removesuffix('_broken')+'.png')
                    with Image.open(normal) as good:
                        assert good.tobytes()!=im.tobytes(),path
            count+=1
    refs=set()
    for path in (ASSETS/'models').rglob('*.json'):
        data=json.loads(path.read_text(encoding='utf-8'))
        for ref in data.get('textures',{}).values():
            if ref.startswith(('fnaf_night_shift:block/','fnaf_night_shift:item/')):
                target=ASSETS/'textures'/(ref.split(':')[1]+'.png')
                assert target.is_file(),f'Missing model texture {path}: {ref}'
                refs.add(ref)
    with Image.open(ASSETS/'icon.png') as im:
        assert im.size==(128,128) and im.tobytes()==mod_icon().tobytes()
    print(f'PASS: {count} textures + icon; dimensions, RGBA, binary transparency, item margins, distinct broken states, reproducibility.')
    print(f'PASS: {len(refs)} local block/item texture references resolve from the models.')


def main():
    # Keep old entrypoints compatible while preserving the final material repaint.
    import rework_materials
    rework_materials.main()


def verify():
    import verify_materials
    verify_materials.verify()


if __name__=='__main__':
    if '--check' in sys.argv:
        verify()
    else:
        main()
        verify()
