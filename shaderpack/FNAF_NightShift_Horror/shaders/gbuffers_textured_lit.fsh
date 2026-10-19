#version 120
uniform sampler2D texture;
#include "/lib/horror_light.glsl"
uniform vec4 entityColor;
varying vec4 color;
varying vec2 texcoord;
varying vec2 lmcoord;
void main() {
    vec4 c = texture2D(texture, texcoord) * color;
    if (c.a < 0.1) discard;
    c.rgb = mix(c.rgb, entityColor.rgb, entityColor.a);
    c.rgb *= horrorLight(lmcoord);
/* DRAWBUFFERS:0 */
    gl_FragData[0] = c;
}
