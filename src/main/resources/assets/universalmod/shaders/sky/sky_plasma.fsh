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

float hash31(vec3 n) { return fract(sin(dot(n, vec3(12.9898, 4.1414, 5.28934))) * 43758.5453); }

vec3 stars(vec3 p) {
    if (uShowStars == 0) return vec3(0.0);
    vec3 c = vec3(0.0);
    float res = 400.0;
    for (int i = 0; i < 2; i++) {
        vec3 q = fract(p * res) - 0.5;
        vec3 id = floor(p * res);
        float rn = hash31(id);
        float c2 = 1.0 - smoothstep(0.0, 0.6, length(q));
        c2 *= step(rn, 0.003 + float(i) * 0.001);
        c += c2 * (mix(vec3(1.0, 0.49, 0.1), vec3(0.75, 0.9, 1.0), hash31(id + 100.0)) * 0.1 + 0.9);
        res *= 1.5;
    }
    return c * c * 0.8;
}

void main() {
    vec3 rd = normalize(vPos);
    vec3 rdSky = vec3(rd.x, abs(rd.y), rd.z);

    vec2 uv = vec2(atan(rdSky.x, rdSky.z), acos(rdSky.y));

    float plasma = 0.0;
    plasma += sin(uv.x * 10.0 + iTime);
    plasma += sin(uv.y * 10.0 + iTime * 1.3);
    plasma += sin((uv.x + uv.y) * 5.0 + iTime * 0.7);
    plasma += sin(length(uv * 5.0) + iTime * 1.5);
    plasma *= 0.25;

    vec3 col1 = uColor;
    vec3 col2 = vec3(uColor.y, uColor.z, uColor.x);
    vec3 col = mix(col1, col2, plasma * 0.5 + 0.5);

    col += stars(rdSky);
    col *= 1.2;

    OutColor = vec4(col, 1.0);
}
