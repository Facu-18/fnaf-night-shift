#version 120
uniform sampler2D texture;
varying vec4 color;
varying vec2 texcoord;
void main() {
    vec4 c = texture2D(texture, texcoord) * color;
    if (c.a < 0.1) discard;
/* DRAWBUFFERS:0 */
    gl_FragData[0] = c;
}
