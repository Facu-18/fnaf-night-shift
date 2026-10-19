#version 120
#include "/lib/horror_light.glsl"
varying vec4 color;
varying vec2 lmcoord;
void main() {
/* DRAWBUFFERS:0 */
    gl_FragData[0] = vec4(color.rgb * horrorLight(lmcoord), color.a);
}
