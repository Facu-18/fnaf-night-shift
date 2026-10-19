#version 120
#include "/lib/horror_light.glsl"
#define PBR_MATERIALS
uniform sampler2D texture;
uniform sampler2D normals;
uniform sampler2D specular;
varying vec4 color;
varying vec2 texcoord;
varying vec2 lmcoord;
varying vec3 viewPosition;
varying vec3 geometricNormal;

// Derivative cotangent frame avoids depending on model-specific tangent attributes.
mat3 surfaceFrame(vec3 n, vec3 position, vec2 uv) {
    vec3 dp1 = dFdx(position);
    vec3 dp2 = dFdy(position);
    vec2 duv1 = dFdx(uv);
    vec2 duv2 = dFdy(uv);
    vec3 p2 = cross(dp2, n);
    vec3 p1 = cross(n, dp1);
    vec3 t = p2 * duv1.x + p1 * duv2.x;
    vec3 b = p2 * duv1.y + p1 * duv2.y;
    float inv = inversesqrt(max(max(dot(t,t), dot(b,b)), 0.00000001));
    return mat3(t * inv, b * inv, n);
}

void main() {
    vec4 base = texture2D(texture, texcoord) * color;
    if (base.a < 0.1) discard;
    vec3 illumination = horrorLight(lmcoord);
    vec3 result = base.rgb * illumination;
#ifdef PBR_MATERIALS
    vec4 normalData = texture2D(normals, texcoord);
    vec4 surface = texture2D(specular, texcoord);
    vec2 xy = normalData.rg * 2.0 - 1.0;
    vec3 tangentNormal = vec3(xy, sqrt(max(1.0-dot(xy,xy), 0.001)));
    vec3 geometric = normalize(geometricNormal);
    vec3 n = normalize(surfaceFrame(geometric, viewPosition, texcoord) * tangentNormal);
    vec3 view = normalize(-viewPosition);
    float relief = clamp(1.0 + (dot(n,view)-dot(geometric,view)) * 0.65, 0.7, 1.25);
    float roughness = max(0.12, 1.0-surface.r);
    float isMetal = step(229.5/255.0, surface.g);
    vec3 f0 = mix(vec3(min(surface.g, 0.9)), base.rgb * 0.56, isMetal);
    float exponent = mix(100.0, 4.0, roughness);
    float local = pow(clamp((lmcoord.x*256.0-8.0)/240.0,0.0,1.0), LIGHT_FALLOFF);
    // Restrained lightmap-driven highlight. No fake room-wide reflection/SSR,
    // no extra light when the real lamp/flashlight is off.
    vec3 highlight = f0 * pow(max(dot(n,view),0.0),exponent)
                   * (1.0-roughness) * local * 0.16;
    result = result * relief * mix(0.82,1.0,normalData.b) + highlight;
    float emission = surface.a < 0.999 ? surface.a : 0.0;
    result = max(result, base.rgb * emission);
#endif
/* DRAWBUFFERS:0 */
    gl_FragData[0] = vec4(result,base.a);
}
