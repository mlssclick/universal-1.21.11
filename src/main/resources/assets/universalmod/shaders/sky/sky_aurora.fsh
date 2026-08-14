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
float tri(float x) { return abs(fract(x) - 0.5); }

float triNoise2d(vec2 p, float spd) {
    float z = 1.8, z2 = 2.5, rz = 0.0;
    float c = cos(p.x * 0.06), s = sin(p.x * 0.06);
    p = vec2(p.x * c - p.y * s, p.x * s + p.y * c);
    vec2 bp = p;
    float t = iTime * spd;
    for (int i = 0; i < 3; i++) {
        vec2 dg = vec2(tri(bp.x * 1.85) + tri(bp.y * 1.85), tri(bp.y * 1.85 + tri(bp.x * 1.85))) * 0.75;
        float ct = cos(t), st = sin(t);
        dg = vec2(dg.x * ct - dg.y * st, dg.x * st + dg.y * ct);
        p -= dg / z2;
        bp *= 1.3;
        z2 *= 0.45;
        z *= 0.42;
        p *= 1.21 + (rz - 1.0) * 0.02;
        rz += tri(p.x + tri(p.y)) * z;
        p = vec2(-p.y * 0.95534 + p.x * 0.29552, p.x * 0.95534 + p.y * 0.29552);
    }
    return clamp(1.0 / pow(rz * 29.0, 1.3), 0.0, 0.55);
}

vec4 aurora(vec3 ro, vec3 rd) {
    vec4 col = vec4(0.0);
    vec4 avgCol = vec4(0.0);
    float rdY = rd.y * 2.0 + 0.4;
    if (rdY <= 0.01) return col;
    for (int i = 0; i < 10; i++) {
        float fi = float(i);
        float pt = (0.8 + pow(fi, 1.4) * 0.004 - ro.y) / rdY;
        pt -= 0.006 * hash21(gl_FragCoord.xy) * smoothstep(0.0, 8.0, fi);
        vec3 bpos = ro + pt * rd;
        float rzt = triNoise2d(bpos.zx, 0.15);
        vec4 col2 = vec4(uColor * rzt, rzt);
        avgCol = mix(avgCol, col2, 0.5);
        col += avgCol * exp2(-fi * 0.1 - 1.5) * smoothstep(0.0, 3.0, fi);
    }
    col *= clamp(rd.y * 15.0 + 0.4, 0.0, 1.0);
    return col * 3.0;
}

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

vec3 bg(vec3 rd) {
    float sd = dot(normalize(vec3(-0.5, -0.6, 0.9)), rd) * 0.5 + 0.5;
    sd = pow(sd, 5.0);
    return mix(vec3(0.05, 0.1, 0.2), vec3(0.1, 0.05, 0.2), sd) * 0.63;
}

void main() {
    vec3 rd = normalize(vPos);
    vec3 ro = vec3(0.0, 0.0, -6.7);

    vec3 rdSky = vec3(rd.x, abs(rd.y), rd.z);
    vec3 col = bg(rdSky);
    vec4 aur = aurora(ro, rdSky);
    col += stars(rdSky);
    col = col * (1.0 - aur.a) + aur.rgb;
    OutColor = vec4(col, 1.0);
}
