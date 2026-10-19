#version 120
varying vec4 color;
varying vec2 texcoord;
varying vec2 lmcoord;
varying vec3 viewPosition;
varying vec3 geometricNormal;
void main() {
    vec4 position = gl_ModelViewMatrix * gl_Vertex;
    gl_Position = gl_ProjectionMatrix * position;
    viewPosition = position.xyz;
    geometricNormal = normalize(gl_NormalMatrix * gl_Normal);
    color = gl_Color;
    texcoord = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
    lmcoord = (gl_TextureMatrix[1] * gl_MultiTexCoord1).xy;
}
