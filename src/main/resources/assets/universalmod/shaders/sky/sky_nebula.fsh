#version 150

layout(std140) uniform SkyShaderData {
    vec4 SkyData0;
    vec4 SkyData1;
    vec4 SkyZenithData;
    vec4 SkyHorizonData;
    vec4 NebColor1Data;
    vec4 NebColor2Data;
    vec4 StarColorData;
    vec4 UserColorData;
};

#define iTime SkyData0.x
#define uShowStars int(SkyData1.w)
#define uColor UserColorData.xyz

in vec3 vPos;
in vec4 FragColor;

out vec4 OutColor;

float hash21(vec2 n) { return fract(sin(dot(n, vec2(12.9898, 4.1414))) * 43758.5453); }
float hash31(vec3 n) { return fract(sin(dot(n, vec3(12.9898, 4.1414, 5.28934))) * 43758.5453); }

float noise3d(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float n = i.x + i.y * 57.0 + 113.0 * i.z;
    return mix(mix(mix(hash21(vec2(n, 0.0)), hash21(vec2(n + 1.0, 0.0)), f.x),
                   mix(hash21(vec2(n + 57.0, 0.0)), hash21(vec2(n + 58.0, 0.0)), f.x), f.y),
               mix(mix(hash21(vec2(n + 113.0, 0.0)), hash21(vec2(n + 114.0, 0.0)), f.x),
                   mix(hash21(vec2(n + 170.0, 0.0)), hash21(vec2(n + 171.0, 0.0)), f.x), f.y), f.z);
}

float fbm(vec3 p) {
    float f = 0.0, amp = 0.5;
    for (int i = 0; i < 5; i++) {
        f += amp * noise3d(p);
        p *= 2.3;
        amp *= 0.4;
    }
    return f;
}

vec3 brightStars(vec3 p) {
    vec3 c = vec3(0.0);
    float res = 200.0;
    for (int i = 0; i < 4; i++) {
        vec3 q = fract(p * res) - 0.5;
        vec3 id = floor(p * res);
        float rn = hash31(id);
        float size = 0.3 + hash31(id + 200.0) * 0.4;
        float c2 = 1.0 - smoothstep(0.0, size, length(q));
        c2 *= step(rn, 0.008 + float(i) * 0.003);
        float twinkle = sin(iTime * 3.0 + hash31(id + 300.0) * 6.28) * 0.3 + 0.7;
        float brightness = (hash31(id + 50.0) * 0.5 + 0.5) * twinkle;
        vec3 starColor = mix(vec3(0.8, 0.9, 1.0), vec3(1.0, 0.8, 0.6), hash31(id + 100.0));
        c += c2 * brightness * starColor * 2.0;
        res *= 1.3;
    }
    return c;
}

void main() {
    vec3 rd = normalize(vPos);
    vec3 rdSky = vec3(rd.x, abs(rd.y), rd.z);

    vec3 p1 = rdSky * 1.2 + vec3(iTime * 0.015, iTime * 0.02, iTime * 0.01);
    vec3 p2 = rdSky * 1.8 + vec3(-iTime * 0.02, iTime * 0.015, -iTime * 0.012);
    vec3 p3 = rdSky * 2.5 + vec3(iTime * 0.01, -iTime * 0.018, iTime * 0.015);

    float n1 = fbm(p1);
    float n2 = fbm(p2);
    float n3 = fbm(p3);

    vec3 nebula1 = uColor * pow(n1, 1.2) * 1.5;
    vec3 nebula2 = vec3(uColor.z, uColor.x, uColor.y) * pow(n2, 1.3) * 1.2;
    vec3 nebula3 = vec3(uColor.y, uColor.z, uColor.x) * pow(n3, 1.4) * 0.8;

    vec3 col = nebula1 * 0.5 + nebula2 * 0.3 + nebula3 * 0.2;

    float bright = pow(max(max(n1, n2), n3), 2.5);
    col += uColor * bright * 1.5;

    if (uShowStars != 0) {
        col += brightStars(rdSky);
    }

    col *= 1.4;

    OutColor = vec4(col, 1.0);
}
