// Lightmap coordinates encode block light and sky light independently.
// Do not use vanilla's gamma-boosted lightmap: it lifts unlit interiors.
#define AMBIENT_LIGHT 0.008 // [0.003 0.008 0.015 0.03]
#define SKY_LIGHT 0.08 // [0.03 0.05 0.08 0.15 0.25]
#define LIGHT_FALLOFF 3.0 // [2.0 2.5 3.0 3.5 4.0]
#define LOCAL_LIGHT 1.15 // [0.85 1.0 1.15 1.3]

vec3 horrorLight(vec2 lightCoords) {
    // Texel centers in the 16x16 Minecraft lightmap: 8/256 .. 248/256.
    vec2 levels = clamp((lightCoords * 256.0 - 8.0) / 240.0, 0.0, 1.0);
    float local = pow(levels.x, LIGHT_FALLOFF) * LOCAL_LIGHT;
    float sky = pow(levels.y, 3.0) * SKY_LIGHT;
    // A warm flashlight against cold, barely visible surroundings.
    // Both light sources come from the world: holding an OFF flashlight
    // never creates a shader-generated light or a screen-space cone.
    return vec3(0.65, 0.80, 1.0) * (AMBIENT_LIGHT + sky)
         + vec3(1.0, 0.91, 0.76) * local;
}
