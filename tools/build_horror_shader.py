#!/usr/bin/env python3
"""Validate/package the horror shader. Optional --gpu compiles with PyOpenGL + glfw.

python tools/build_horror_shader.py --gpu --deploy
--deploy refreshes existing project runtime copies, not external Minecraft installs.
"""
import argparse
import math
from pathlib import Path
import re
import shutil
from zipfile import ZipFile, ZIP_DEFLATED

ROOT = Path(__file__).resolve().parents[1]
PACK = ROOT / 'shaderpack/FNAF_NightShift_Horror'
SHADERS = PACK / 'shaders'


def expanded(path):
    text = path.read_text(encoding='utf-8')
    return re.sub(r'^#include "(/[^\"]+)"$',
                  lambda m: expanded(SHADERS / m[1].lstrip('/')), text, flags=re.M)


def profiles():
    result = {'DEFAULT': {}}
    properties = (SHADERS / 'shaders.properties').read_text(encoding='utf-8')
    for name, values in re.findall(r'^profile\.(\w+)=(.*)$', properties, re.M):
        result[name] = dict(token.split(':', 1) if ':' in token else
                            (token.lstrip('!'), not token.startswith('!'))
                            for token in values.split())
    return result


def configured(text, settings):
    for key, value in settings.items():
        if isinstance(value, bool):
            if not value:
                text = re.sub(r'^#define ' + key + r'\s*$', '// disabled ' + key, text, flags=re.M)
        else:
            text = re.sub(r'^(#define ' + key + r')\s+\S+', lambda m: m[1]+' '+value, text, flags=re.M)
    return text


def check_lighting():
    light = expanded(SHADERS / 'lib/horror_light.glsl')
    defaults = dict(re.findall(r'^#define (\w+) ([\d.]+)', light, re.M))
    for name, settings in profiles().items():
        values = defaults | settings
        ambient, sky, falloff, local = (float(values[k]) for k in
                                       ('AMBIENT_LIGHT', 'SKY_LIGHT', 'LIGHT_FALLOFF', 'LOCAL_LIGHT'))
        curve = [ambient + local * (level/15)**falloff for level in range(16)]
        assert all(a < b for a, b in zip(curve, curve[1:])), name
        assert curve[0] < 0.02 and curve[7] < 0.20, name
        assert curve[14] > 0.85 and curve[15] > 1.0, name
        assert sky < 0.2, name
        density = float(settings.get('FOG_DENSITY', '1.25'))
        fog14 = 1 - math.exp(-((14-3)*0.043*density)**1.65)
        # Lit material at 14m retains at least 75% of its light through fog.
        assert 1 - fog14*(1-0.65) > 0.75, name
        print(f'{name}: unlit={curve[0]:.3f}, light 7={curve[7]:.3f}, light 14={curve[14]:.3f}; lit 14m fog transmission={1-fog14*0.35:.3f}')
    # Every referenced include must resolve before packaging.
    for path in SHADERS.glob('*.*sh'):
        expanded(path)


def compile_gpu():
    import glfw
    from OpenGL import GL
    if not glfw.init():
        raise RuntimeError('Could not initialize an OpenGL context')
    window = None
    try:
        glfw.window_hint(glfw.VISIBLE, glfw.FALSE)
        glfw.window_hint(glfw.CONTEXT_VERSION_MAJOR, 2)
        glfw.window_hint(glfw.CONTEXT_VERSION_MINOR, 1)
        window = glfw.create_window(32, 32, 'Shader validation', None, None)
        if not window:
            raise RuntimeError('Could not create a compatibility OpenGL context')
        glfw.make_context_current(window)
        print('GPU:', GL.glGetString(GL.GL_RENDERER).decode())
        count = 0
        for name, settings in profiles().items():
            for vertex in sorted(SHADERS.glob('*.vsh')):
                shaders = []
                program = GL.glCreateProgram()
                try:
                    for path, kind in [(vertex, GL.GL_VERTEX_SHADER), (vertex.with_suffix('.fsh'), GL.GL_FRAGMENT_SHADER)]:
                        shader = GL.glCreateShader(kind)
                        shaders.append(shader)
                        GL.glShaderSource(shader, configured(expanded(path), settings))
                        GL.glCompileShader(shader)
                        if not GL.glGetShaderiv(shader, GL.GL_COMPILE_STATUS):
                            raise RuntimeError(f'{name}/{path.name}: {GL.glGetShaderInfoLog(shader)}')
                        GL.glAttachShader(program, shader)
                    GL.glLinkProgram(program)
                    if not GL.glGetProgramiv(program, GL.GL_LINK_STATUS):
                        raise RuntimeError(f'{name}/{vertex.stem}: {GL.glGetProgramInfoLog(program)}')
                    count += 1
                finally:
                    GL.glDeleteProgram(program)
                    for shader in shaders:
                        GL.glDeleteShader(shader)
        print(f'PASS: {count} shader program/profile combinations compiled and linked.')
    finally:
        if window:
            glfw.destroy_window(window)
        glfw.terminate()


def package(deploy):
    target = PACK.with_suffix('.zip')
    files = sorted(p for p in PACK.rglob('*') if p.is_file())
    with ZipFile(target, 'w', ZIP_DEFLATED) as archive:
        for path in files:
            archive.write(path, path.relative_to(PACK).as_posix())
    with ZipFile(target) as archive:
        assert archive.testzip() is None
        for path in files:
            assert archive.read(path.relative_to(PACK).as_posix()) == path.read_bytes()
    print('Packaged:', target.relative_to(ROOT))
    if deploy:
        for runtime in ('run', 'run-shaders', 'run-essential'):
            dest = ROOT / runtime / 'shaderpacks' / target.name
            if dest.is_file():
                shutil.copyfile(target, dest)
                assert dest.read_bytes() == target.read_bytes()
                print('Updated:', dest.relative_to(ROOT))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--gpu', action='store_true')
    parser.add_argument('--deploy', action='store_true')
    args = parser.parse_args()
    check_lighting()
    if args.gpu:
        compile_gpu()
    package(args.deploy)
