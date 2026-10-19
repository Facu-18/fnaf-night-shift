#version 120
varying vec4 color;
varying vec2 lmcoord;
void main() {
    gl_Position = ftransform();
    color = gl_Color;
    lmcoord = (gl_TextureMatrix[1] * gl_MultiTexCoord1).xy;
}
