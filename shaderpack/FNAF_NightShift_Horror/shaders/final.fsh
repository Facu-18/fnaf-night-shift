#version 120
// Viñeta, grano, aberración cromática, líneas de barrido y parpadeo eléctrico.
#define GRAIN 0.7 // [0.0 0.35 0.7 1.0 1.5]
#define VIGNETTE 1.25 // [0.0 0.5 1.0 1.25 1.5]
#define CHROMATIC 0.5 // [0.0 0.5 1.0 2.0]
#define FLICKER

uniform sampler2D colortex0;
uniform float frameTimeCounter;
uniform float viewWidth;
uniform float viewHeight;
varying vec2 texcoord;

float rand(vec2 co) {
    return fract(sin(dot(co, vec2(12.9898, 78.233))) * 43758.5453);
}

void main() {
    vec2 uv = texcoord;
    vec2 c = uv - 0.5;
    float dist = length(c);
    float ca = 0.004 * CHROMATIC * smoothstep(0.15, 0.7, dist);
    vec2 inset = 0.5 / vec2(viewWidth, viewHeight);
    vec3 col = vec3(texture2D(colortex0, clamp(uv + c * ca, inset, 1.0 - inset)).r,
                    texture2D(colortex0, uv).g,
                    texture2D(colortex0, clamp(uv - c * ca, inset, 1.0 - inset)).b);
    col *= 1.0 - clamp(smoothstep(0.20, 0.72, dist) * 0.72 * VIGNETTE, 0.0, 0.9);
    float g = rand(floor(uv * vec2(viewWidth, viewHeight)) + floor(frameTimeCounter * 24.0)) - 0.5;
    // Multiplicative film grain: black stays black instead of glowing with noise.
    col *= 1.0 + g * 0.14 * GRAIN;
    col *= 0.99 + 0.01 * sin(uv.y * viewHeight * 1.4);
#ifdef FLICKER
    float tick = frameTimeCounter * 0.75;
    float event = step(0.86, rand(vec2(floor(tick), 7.3)));
    float pulse = sin(fract(tick) * 3.14159265);
    col *= 1.0 - event * pulse * pulse * 0.07;
#endif
    gl_FragColor = vec4(clamp(col, 0.0, 1.0), 1.0);
}
