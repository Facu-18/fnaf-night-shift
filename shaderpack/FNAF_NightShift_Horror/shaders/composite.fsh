#version 120
// Deep cold shadows, warm practical lights, and an obscured far corridor.
#define FOG_DENSITY 1.25 // [0.5 0.75 1.0 1.25 1.5 2.0]
#define SHADOW_CONTRAST 1.12 // [1.0 1.06 1.12 1.2 1.3]

uniform sampler2D colortex0;
uniform sampler2D depthtex0;
uniform mat4 gbufferProjectionInverse;
varying vec2 texcoord;

float viewDistance(float depth) {
    vec4 position = gbufferProjectionInverse * vec4(texcoord * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    return length(position.xyz / position.w);
}

void main() {
    vec3 col = texture2D(colortex0, texcoord).rgb;
    float depth = texture2D(depthtex0, texcoord).r;
    float distance = viewDistance(depth);
    // Smooth extinction, without a hard wall at the flashlight's 14-block reach.
    float travel = max(distance - 3.0, 0.0);
    float fog = 1.0 - exp(-pow(travel * 0.043 * FOG_DENSITY, 1.65));
    float bright = max(col.r, max(col.g, col.b));
    float illuminated = smoothstep(0.07, 0.55, bright);
    // Protect nearby illuminated surfaces, but no infinite-distance light halos.
    float nearLight = 1.0 - smoothstep(14.0, 30.0, distance);
    fog *= 1.0 - illuminated * nearLight * 0.65;
    if (depth >= 0.999999) fog = 0.985;
    float l = dot(col, vec3(0.2126, 0.7152, 0.0722));
    col = mix(col, vec3(l), mix(0.55, 0.16, illuminated));
    col *= mix(vec3(0.72, 0.88, 1.0), vec3(1.0, 0.98, 0.94), illuminated);
    col = pow(max(col, vec3(0.0)), vec3(SHADOW_CONTRAST));
    // Fog absorbs light rather than painting a bright grey veil over black areas.
    vec3 fogColor = min(col, vec3(0.0015, 0.0022, 0.0035));
    col = mix(col, fogColor, clamp(fog, 0.0, 1.0));
/* DRAWBUFFERS:0 */
    gl_FragData[0] = vec4(col, 1.0);
}
