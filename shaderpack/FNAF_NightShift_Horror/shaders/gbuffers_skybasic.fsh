#version 120
varying vec4 color;
void main() {
/* DRAWBUFFERS:0 */
    gl_FragData[0] = vec4(color.rgb * vec3(0.035, 0.045, 0.065), color.a);
}
