#!/usr/bin/env python3
"""Validate live resource references, UVs, material encoding, and repaint coverage.
--previews creates offline geometry/lighting review sheets (not game captures).
--prepare-game creates an isolated copy of the existing development test world.
"""
import argparse
import hashlib
import json
import re
from pathlib import Path
import shutil
import os
import subprocess
from zipfile import ZipFile

import numpy as np
from PIL import Image, ImageDraw

import rework_materials as R
import preview_models as preview


def verify():
    root=R.ASSETS
    R.OUT.mkdir(parents=True,exist_ok=True)
    baseline=json.loads((R.ROOT/'tools/material_baseline.json').read_text())
    assert R.fixed_hashes()==baseline['geometry_animation_hashes']
    paths=sorted((root/'textures').rglob('*.png'))
    count=0
    for path in paths:
        with Image.open(path) as im:
            im.load()
            assert im.mode=='RGBA',path
            assert im.width==im.height and im.width in (64,128,512),path
            if path.stem.endswith(('_n','_s')):
                with Image.open(path.with_stem(path.stem[:-2])) as base:
                    assert im.size==base.size,path
                continue
            key=path.relative_to(root/'textures').as_posix()
            assert key in baseline['original_pixel_hashes'],f'Unexpected new albedo: {path}'
            assert hashlib.sha256(im.tobytes()).hexdigest()!=baseline['original_pixel_hashes'][key],f'Unchanged texture: {path}'
            if path.parent.name=='item':
                a=im.getchannel('A')
                assert a.getextrema()==(0,255),path
                x,y,r,b=a.getbbox()
                assert x>0 and y>0 and r<im.width and b<im.height,path
            if path.stem in ('metal_grate','vent_grate'):
                assert im.getchannel('A').getextrema()==(0,255),path
            count+=1
    assert count==93,count
    references=set()
    external=set()
    for path in (root/'models').rglob('*.json'):
        data=json.loads(path.read_text())
        for value in data.get('textures',{}).values():
            if value.startswith('fnaf_night_shift:'):
                target=root/'textures'/(value.split(':')[1]+'.png')
                assert target.is_file(),(path,value)
                references.add(value)
            elif not value.startswith('#'):
                external.add(value)
    for ident,builder in R.builders():
        m,_=builder()
        m.pack()
        live=json.loads((root/'geo'/f'{ident}.geo.json').read_text())
        assert live==m.to_geo(),ident
        bones={b['name'] for b in live['minecraft:geometry'][0]['bones']}
        animations=json.loads((root/'animations'/f'{ident}.animation.json').read_text())
        for animation in animations['animations'].values():
            assert set(animation.get('bones',{}))<=bones,ident
        tex=Image.open(root/'textures/entity'/f'{ident}.png')
        glow=Image.open(root/'textures/entity'/f'{ident}_glowmask.png')
        opaque=np.asarray(tex)[:,:,3]>0
        luminous=np.asarray(glow)[:,:,3]>0
        assert np.all(~luminous|opaque),f'Glow outside albedo: {ident}'
        assert 0<luminous.sum()<50,f'Overlarge emissive eyes: {ident}'
        # All packed cubes fit existing 128-unit UV space at 4 pixels/unit.
        for c in m.cubes:
            assert c.uv[0]+c.uv_w()<=128 and c.uv[1]+c.uv_h()<=128,ident
    for path in (root/'textures/block').glob('*_n.png'):
        n=np.asarray(Image.open(path)).astype(float)
        xy=n[:,:,:2]/255*2-1
        assert (np.sum(xy*xy,axis=2)<=1.02).all(),path
    for path in (root/'textures/block').glob('*_s.png'):
        s=np.asarray(Image.open(path))
        assert set(np.unique(s[:,:,1]))<={10,230},path
    with Image.open(root/'icon.png') as icon:
        assert icon.size==(128,128)
    mib=sum(Image.open(p).width*Image.open(p).height*4 for p in paths)/1024**2
    report={'repainted_texture_pngs':count,'icon':1,'material_maps':len(paths)-count,
            'model_texture_references':len(references),'external_texture_references':sorted(external),
            'raw_rgba_mib':round(mib,2),'estimated_with_mips_mib':round(mib*4/3,2),
            'missing_textures':[], 'geometry_and_animation_files_unchanged':True}
    (R.OUT/'validation.json').write_text(json.dumps(report,indent=2))
    print(json.dumps(report,indent=2))


def previews():
    sheets=[]
    for ident in R.NAMES:
        geo=str(R.ASSETS/'geo'/f'{ident}.geo.json')
        tex=str(R.ASSETS/'textures/entity'/f'{ident}.png')
        images=[preview.render(geo,tex,yaw,size=520,scale=9.6) for yaw in (0,35)]
        sheet=Image.new('RGB',(1040,546),'#252b29')
        for i,im in enumerate(images):
            sheet.paste(im,(i*520,26))
        ImageDraw.Draw(sheet).text((12,8),ident+' - offline UV preview',fill='#c6c2ac')
        sheet.save(R.OUT/(ident+'-model.png'))
        sheets.append(sheet.resize((520,273)))
    combined=Image.new('RGB',(1040,273*3),'#252b29')
    for i,im in enumerate(sheets):
        combined.paste(im,((i%2)*520,(i//2)*273))
    combined.save(R.OUT/'animatronics-models.png')
    samples=['pizzeria_wall','dirty_tiles','stage_floor','metal_plating','station_wiring_front','party_table_top']
    relit=Image.new('RGB',(len(samples)*160,3*180),'#1f2524')
    for row,light in enumerate(((1.,1.,1.),(.24,.28,.32),(.60,.16,.12))):
        for col,name in enumerate(samples):
            im=Image.open(R.ASSETS/'textures/block'/f'{name}.png').convert('RGB').resize((144,144))
            pixels=np.asarray(im)*np.array(light)[None,None,:]
            relit.paste(Image.fromarray(np.clip(pixels,0,255).astype(np.uint8)),(col*160+8,row*180+22))
        ImageDraw.Draw(relit).text((8,row*180+4),('Neutral / offline albedo','Dim / offline color check','Red / offline color check')[row],fill='#c6c2ac')
    relit.save(R.OUT/'lighting-study.png')
    print('Offline previews:',R.OUT)


def prepare_game(base=False):
    dest=R.ROOT/'build/material-game'
    world=dest/'saves/FNAF_Test'
    if not world.exists():
        shutil.copytree(R.ROOT/'run-shaders/saves/FNAF_Test',world,ignore=shutil.ignore_patterns('session.lock'))
    (dest/'shaderpacks').mkdir(parents=True,exist_ok=True)
    (dest/'mods').mkdir(exist_ok=True)
    (dest/'config').mkdir(exist_ok=True)
    for p in (R.ROOT/'run-shaders/mods').glob('*.jar'):
        shutil.copyfile(p,dest/'mods'/p.name)
    shutil.copyfile(R.ROOT/'shaderpack/FNAF_NightShift_Horror.zip',dest/'shaderpacks/FNAF_NightShift_Horror.zip')
    (dest/'config/iris.properties').write_text('shaderPack=FNAF_NightShift_Horror.zip\nenableShaders='+('false' if base else 'true')+'\n')
    config=dest/'config/fnaf_night_shift.json'
    if config.exists():
        data=json.loads(config.read_text())
        data['horrorFilter']=False  # Judge native albedo without the optional post filter.
        config.write_text(json.dumps(data,indent=2))
    print('Prepared isolated test world:',dest)


def collect_game():
    game=R.ROOT/'build/material-game'
    mode='shader' if 'enableShaders=true' in (game/'config/iris.properties').read_text() else 'base'
    dest=R.OUT/('in-game-'+mode)
    dest.mkdir(exist_ok=True)
    shots=sorted((game/'screenshots').glob('materials_*.png'))
    assert len(shots)==8,len(shots)
    for path in shots:
        shutil.copyfile(path,dest/path.name)
    log=(game/'logs/latest.log').read_text(encoding='utf-8',errors='replace')
    errors=[line for line in log.splitlines() if re.search(r'Unable to load.*(?:texture|model)|Missing.*texture|Failed to (?:load|compile).*shader|Shader compilation failed|\[.*ERROR\]',line,re.I)
            and 'Failed to verify authentication' not in line]  # Offline Gradle dev identity.
    assert not errors,'\n'.join(errors)
    assert len(re.findall('material-review captured',log))==8
    off=np.asarray(Image.open(dest/'materials_05_flashlight_off.png').convert('RGB'),dtype=float)
    on=np.asarray(Image.open(dest/'materials_06_flashlight_on.png').convert('RGB'),dtype=float)
    assert on.mean()>off.mean()+.5,(on.mean(),off.mean())
    (dest/'latest.log').write_text(log,encoding='utf-8')
    (dest/'result.json').write_text(json.dumps({'mode':mode,'captures':8,'resource_or_shader_errors':errors,'off_mean':float(off.mean()),'on_mean':float(on.mean())},indent=2))
    print('PASS game review:',mode,'off/on mean luminance:',round(off.mean(),2),round(on.mean(),2))


if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--previews',action='store_true')
    p.add_argument('--prepare-game',action='store_true')
    p.add_argument('--base',action='store_true')
    p.add_argument('--collect-game',action='store_true')
    p.add_argument('--reproducible',action='store_true')
    p.add_argument('--run-game',action='store_true',help='Run the isolated visual review and collect its results')
    p.add_argument('--jar',type=Path,help='Verify packaged PNG bytes against live resources')
    args=p.parse_args()
    if args.reproducible:
        def hashes():
            return {str(p):hashlib.sha256(p.read_bytes()).hexdigest() for p in R.ASSETS.rglob('*.png')}
        before=hashes()
        R.main()
        assert hashes()==before,'Non-deterministic material generation'
        print('PASS: reproducible PNG content across full regeneration.')
    verify()
    if args.jar:
        with ZipFile(args.jar) as archive:
            files=list(R.ASSETS.rglob('*.png'))
            for path in files:
                name='assets/fnaf_night_shift/'+path.relative_to(R.ASSETS).as_posix()
                assert archive.read(name)==path.read_bytes(),name
        print(f'PASS: {len(files)} PNGs in JAR match the final resources.')
    if args.previews:
        previews()
    if args.prepare_game:
        prepare_game(args.base)
    if args.run_game:
        command=['cmd.exe','/d','/c','gradlew.bat','runMaterialReview'] if os.name=='nt' else ['./gradlew','runMaterialReview']
        result=subprocess.run(command,cwd=R.ROOT,capture_output=True,text=True,encoding='utf-8',errors='replace',timeout=240)
        (R.OUT/'gradle-material-review.log').write_text(result.stdout+'\n'+result.stderr,encoding='utf-8')
        if result.returncode:
            print(result.stdout,result.stderr)
            raise SystemExit(result.returncode)
        print('PASS: isolated Minecraft visual review completed.')
        collect_game()
    if args.collect_game:
        collect_game()
