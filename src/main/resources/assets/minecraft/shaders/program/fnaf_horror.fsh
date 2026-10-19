#version 150
// FNAF: NIGHT SHIFT - filtro de terror (original). Niebla por profundidad, desaturación fría,
// sombras aplastadas, viñeta, grano, aberración cromática, parpadeo y pulso rojo en persecuciones.

uniform sampler2D DiffuseSampler;
uniform sampler2D DepthSampler;
uniform vec2 InSize;
uniform float Time;
uniform float Intensity; // 0 calma, 0.5 algo cerca, 1 persecución
uniform float Blur;      // fallo de ventilación

in vec2 texCoord;
in vec2 oneTexel;

out vec4 fragColor;

float rand(vec2 co) {
    return fract(sin(dot(co, vec2(12.9898, 78.233))) * 43758.5453);
}

vec3 sampleCA(vec2 uv, vec2 c, float ca) {
    return vec3(texture(DiffuseSampler, uv + c * ca).r,
                texture(DiffuseSampler, uv).g,
                texture(DiffuseSampler, uv - c * ca).b);
}

void main() {
    vec2 uv = texCoord;
    // distorsión ondulante cuando falla la ventilación
    uv.x += Blur * 0.006 * sin(uv.y * 18.0 + Time * 6.2832 * 3.0);
    uv.y += Blur * 0.004 * cos(uv.x * 14.0 + Time * 6.2832 * 2.0);
    vec2 c = uv - 0.5;
    float dist = length(c);

    float ca = (0.004 + 0.012 * Intensity + 0.01 * Blur) * dist;
    vec3 col = sampleCA(uv, c, ca);
    if (Blur > 0.0) {
        vec3 acc = col;
        for (int i = 1; i <= 4; i++) {
            float o = float(i) * 1.6 * Blur;
            acc += texture(DiffuseSampler, uv + vec2(o, 0.0) * oneTexel).rgb;
            acc += texture(DiffuseSampler, uv - vec2(o, 0.0) * oneTexel).rgb;
            acc += texture(DiffuseSampler, uv + vec2(0.0, o) * oneTexel).rgb;
            acc += texture(DiffuseSampler, uv - vec2(0.0, o) * oneTexel).rgb;
        }
        col = mix(col, acc / 17.0, clamp(Blur, 0.0, 1.0));
    }

    // niebla oscura según la distancia real (búfer de profundidad)
    float depth = texture(DepthSampler, uv).r;
    float near = 0.05;
    float far = 256.0;
    float z = depth * 2.0 - 1.0;
    float lin = (2.0 * near * far) / (far + near - z * (far - near));
    float fogStart = 5.0;
    float fogEnd = 30.0 - 10.0 * Intensity;
    float fog = smoothstep(fogStart, fogEnd, lin) * 0.88;
    // las fuentes muy brillantes (ojos, lámparas) atraviesan algo la niebla
    float bright = max(col.r, max(col.g, col.b));
    fog *= 1.0 - smoothstep(0.75, 1.0, bright) * 0.6;

    // desaturación fría y sombras aplastadas
    float l = dot(col, vec3(0.299, 0.587, 0.114));
    col = mix(col, vec3(l), 0.5 + 0.2 * Intensity);
    col *= vec3(0.9, 1.0, 0.96);
    col = pow(max(col, 0.0), vec3(1.25)) * 0.95;
    col = mix(col, vec3(0.010, 0.012, 0.016), fog);

    // viñeta fuerte
    col *= 1.0 - smoothstep(0.30, 0.82, dist) * (0.8 + 0.15 * Intensity);

    // grano de película y líneas de barrido
    float g = rand(uv * InSize + vec2(Time * 911.0, Time * 377.0)) - 0.5;
    col += g * (0.07 + 0.07 * Intensity);
    col *= 0.97 + 0.03 * sin(uv.y * InSize.y * 1.4);

    // parpadeo eléctrico ocasional, más frecuente con peligro
    float flick = step(0.94 - 0.08 * Intensity, rand(vec2(floor(Time * 60.0), 3.7)));
    col *= 1.0 - flick * (0.10 + 0.25 * Intensity);

    // pulso rojo durante la persecución
    float pulse = 0.5 + 0.5 * sin(Time * 6.2832 * 2.0);
    col.r += Intensity * Intensity * 0.07 * pulse * smoothstep(0.2, 0.8, dist);

    fragColor = vec4(clamp(col, 0.0, 1.0), 1.0);
}
