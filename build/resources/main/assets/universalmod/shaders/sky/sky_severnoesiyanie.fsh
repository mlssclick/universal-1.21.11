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

#define uTime (SkyData0.x * SkyData1.z)
#define uColor UserColorData.xyz
#define uColor2 NebColor2Data.xyz
#define uScale SkyData1.y
#define uIntensity SkyData1.x
#define uShowStars int(SkyData1.w)

in vec3 vPos;
in vec4 FragColor;

out vec4 OutColor;

float hash11(float p) {
    p = fract(p * 0.1031);
    p *= p + 33.33;
    p *= p + p;
    return fract(p);
}

float hash21(vec2 p) {
    vec3 q = fract(vec3(p.xyx) * vec3(0.1031, 0.1030, 0.0973));
    q += dot(q, q.yzx + 33.33);
    return fract((q.x + q.y) * q.z);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float a = hash21(i);
    float b = hash21(i + vec2(1.0, 0.0));
    float c = hash21(i + vec2(0.0, 1.0));
    float d = hash21(i + vec2(1.0, 1.0));
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

float fbm(vec2 p) {
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < 5; i++) {
        v += noise(p) * a;
        p = p * 2.03 + vec2(5.2, 1.7);
        a *= 0.52;
    }
    return v;
}

float stars(vec3 rd) {
    if (uShowStars == 0) {
        return 0.0;
    }
    vec2 p = normalize(rd.xz) * (1.0 / max(0.2, 1.25 - rd.y)) * 42.0;
    vec2 id = floor(p);
    vec2 gv = fract(p) - 0.5;
    float h = hash21(id);
    float d = length(gv);
    float s = smoothstep(0.11, 0.0, d) * step(0.985, h);
    float tw = 0.55 + 0.45 * sin(uTime * 1.3 + h * 18.0);
    return s * tw * smoothstep(-0.18, 0.38, rd.y);
}

float comet(vec3 rd, float id, out vec3 cc) {
    float ang = hash11(id * 13.1) * 6.2831853;
    float loop = 14.0 + hash11(id * 4.7) * 7.0;
    float ph = fract(uTime / loop + hash11(id * 1.9));
    float live = smoothstep(0.0, 0.02, ph) * (1.0 - smoothstep(0.12, 0.18, ph));
    float stepv = clamp(ph / 0.12, 0.0, 1.0);
    vec3 sd = normalize(vec3(cos(ang) * 0.88, 0.82 + hash11(id * 5.2) * 0.12, sin(ang) * 0.88));
    vec3 ed = normalize(vec3(cos(ang + 0.09) * 0.94, -0.16, sin(ang + 0.09) * 0.94));
    vec3 cp = normalize(mix(sd, ed, stepv));
    vec3 mv = normalize(sd - ed);
    float head = pow(max(dot(rd, cp), 0.0), 3200.0);
    float glow = pow(max(dot(rd, cp), 0.0), 260.0) * 0.18;
    float tail = 0.0;
    for (int i = 1; i <= 8; i++) {
        float k = float(i) / 8.0;
        vec3 tp = normalize(cp + mv * k * 0.075);
        tail += pow(max(dot(rd, tp), 0.0), mix(540.0, 160.0, k)) * (1.0 - k);
    }
    tail *= live * 0.34;
    float trail = pow(max(dot(rd, normalize(cp + mv * 0.12)), 0.0), 120.0) * 0.12 * live;
    cc = mix(vec3(1.0), mix(uColor, uColor2, 0.45), 0.8);
    return (head + tail + trail + glow) * live;
}

void main() {
    vec3 rd = normalize(vPos);
    float h = rd.y;
    float t = uTime;
    vec3 top = vec3(0.01, 0.045, 0.11);
    vec3 mid = vec3(0.03, 0.085, 0.17);
    vec3 hor = mix(vec3(0.02, 0.05, 0.08), vec3(0.04, 0.1, 0.08), 0.35);
    float sky = smoothstep(-0.22, 0.7, h);
    vec3 col = mix(hor, mix(mid, top, sky), sky);

    float s = stars(rd);
    col += vec3(0.85, 0.94, 1.0) * s * 0.95;

    vec2 drift = rd.xz * uScale;
    float c1 = fbm(drift * 0.65 + vec2(t * 0.035, -t * 0.018));
    float c2 = fbm(drift * 1.15 + vec2(-t * 0.048, t * 0.022) + vec2(2.7, 7.4));
    float c3 = fbm((rd.xz + rd.y) * 1.8 + vec2(t * 0.06, -t * 0.04) + vec2(4.4, 1.2));
    float covera = smoothstep(0.34, 0.78, fbm(drift * 0.42 + vec2(t * 0.03, -t * 0.015) + vec2(1.8, 6.3)));
    float coverb = smoothstep(0.36, 0.82, fbm(drift * 0.76 + vec2(-t * 0.028, t * 0.022) + vec2(8.4, 2.1)));
    float alive = mix(covera, coverb, 0.45);
    float band = smoothstep(0.42, 0.88, c1 * 0.55 + c2 * 0.35 + c3 * 0.28);
    float curtain = smoothstep(0.02, 0.5, h) * (1.0 - smoothstep(0.56, 0.92, h));
    float ripple = sin(rd.x * 14.0 + t * 0.9 + c2 * 3.2) * 0.5 + 0.5;
    float beam = smoothstep(0.3, 0.85, ripple) * curtain;
    float pulse = 0.76 + 0.24 * sin(t * 1.45 + c1 * 5.0 + coverb * 2.4);
    float fade = smoothstep(0.18, 0.88, alive) * (0.42 + 0.58 * pulse);
    float gaps = 1.0 - smoothstep(0.62, 0.92, fbm(drift * 1.6 + vec2(t * 0.052, -t * 0.033) + vec2(3.6, 9.7)));
    vec3 au1 = mix(uColor, uColor2, 0.24);
    vec3 au2 = mix(uColor2, uColor, 0.58);
    vec3 au3 = mix(uColor, uColor2, c3);
    col += mix(au1, au2, c2) * band * beam * fade * gaps * (0.92 + uIntensity * 28.0);
    col += au3 * band * curtain * (0.12 + alive * 0.26) * (0.55 + 0.45 * covera);
    col += mix(uColor, uColor2, 0.5) * curtain * smoothstep(0.38, 0.86, c2) * 0.12;

    for (int i = 0; i < 2; i++) {
        vec3 cc;
        float cm = comet(rd, float(i) + 1.0, cc);
        col += cc * cm;
    }

    float haze = smoothstep(-0.3, 0.18, h);
    col += mix(uColor, uColor2, 0.4) * haze * 0.09;
    OutColor = vec4(clamp(col, 0.0, 1.0), 1.0);
}
