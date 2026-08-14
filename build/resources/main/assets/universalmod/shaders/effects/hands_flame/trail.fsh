#version 150

in vec2 texCoord;
out vec4 fragColor;

uniform sampler2D BeforeSampler;
uniform sampler2D AfterSampler;
uniform sampler2D PrevTrailSampler;
uniform sampler2D DepthSampler;

layout(std140) uniform HandsFlameData {
    vec4 flameColor;
    vec4 params0;
    vec4 params1;
    vec4 screen;
    vec4 smokeMotion;
};

const vec2 dirs[8] = vec2[](
    vec2( 1.000,  0.000), vec2( 0.707,  0.707),
    vec2( 0.000,  1.000), vec2(-0.707,  0.707),
    vec2(-1.000,  0.000), vec2(-0.707, -0.707),
    vec2( 0.000, -1.000), vec2( 0.707, -0.707)
);

float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(
        mix(hash12(i), hash12(i + vec2(1.0, 0.0)), u.x),
        mix(hash12(i + vec2(0.0, 1.0)), hash12(i + vec2(1.0, 1.0)), u.x),
        u.y
    );
}

float fbm(vec2 p) {
    float value = 0.0;
    float amplitude = 0.5;
    for (int i = 0; i < 3; i++) {
        value += noise(p) * amplitude;
        p = p * 2.03 + vec2(17.13, 9.27);
        amplitude *= 0.5;
    }
    return value;
}

float rawHandMaskColor(vec2 uv, out vec3 outColor) {
    vec4 beforeColor = texture(BeforeSampler, uv);
    vec4 afterColor = texture(AfterSampler, uv);
    outColor = afterColor.rgb;

    float depth = texture(DepthSampler, uv).r;
    float depthMask = depth < 0.9999 ? 1.0 : 0.0;

    vec3 delta = abs(afterColor.rgb - beforeColor.rgb);
    float peak = max(max(delta.r, delta.g), delta.b);
    float luma = dot(delta, vec3(0.299, 0.587, 0.114));
    float value = peak * 0.78 + luma * 0.88 + abs(afterColor.a - beforeColor.a);
    float colorMask = smoothstep(0.004, 0.060, value);
    return max(depthMask, colorMask);
}

float depthMaskAt(vec2 uv) {
    return texture(DepthSampler, uv).r < 0.9999 ? 1.0 : 0.0;
}

float nearbyDepthMask(vec2 uv, vec2 px, float radiusPx) {
    float mask = depthMaskAt(uv);
    for (int i = 0; i < 8; i++) {
        vec2 probeUv = clamp(uv + dirs[i] * radiusPx * 0.72 * px, vec2(0.0), vec2(1.0));
        mask = max(mask, depthMaskAt(probeUv));
    }
    return mask;
}

void sampleItemBlur(vec2 uv, vec2 px, float blurScale,
                    out float coreMask, out float softMask, out float haloMask,
                    out vec3 itemColor) {
    vec3 centerColor;
    float centerMask = rawHandMaskColor(uv, centerColor);

    float nearSum = centerMask * 3.50;
    float nearWeight = 3.50;
    float softSum = centerMask * 2.20;
    float softWeight = 2.20;
    float haloSum = centerMask * 1.15;
    float haloWeight = 1.15;

    vec3 colorSum = centerColor * centerMask * 3.50;
    float colorWeight = centerMask * 3.50;

    for (int i = 0; i < 8; i++) {
        vec2 d = dirs[i];

        vec2 uv1 = clamp(uv + d * px * (1.45 * blurScale), vec2(0.0), vec2(1.0));
        vec3 c1;
        float m1 = rawHandMaskColor(uv1, c1);
        nearSum += m1 * 0.58;
        nearWeight += 0.58;
        softSum += m1 * 0.46;
        softWeight += 0.46;
        haloSum += m1 * 0.14;
        haloWeight += 0.14;
        colorSum += c1 * m1 * 0.46;
        colorWeight += m1 * 0.46;

        vec2 uv2 = clamp(uv + d * px * (4.25 * blurScale), vec2(0.0), vec2(1.0));
        vec3 c2;
        float m2 = rawHandMaskColor(uv2, c2);
        softSum += m2 * 0.28;
        softWeight += 0.28;
        haloSum += m2 * 0.20;
        haloWeight += 0.20;
        colorSum += c2 * m2 * 0.14;
        colorWeight += m2 * 0.14;

        vec2 uv3 = clamp(uv + d * px * (8.75 * blurScale), vec2(0.0), vec2(1.0));
        vec3 c3;
        float m3 = rawHandMaskColor(uv3, c3);
        haloSum += m3 * 0.12;
        haloWeight += 0.12;
        colorSum += c3 * m3 * 0.045;
        colorWeight += m3 * 0.045;
    }

    coreMask = smoothstep(0.22, 0.80, nearSum / max(nearWeight, 0.001));
    softMask = smoothstep(0.016, 0.34, softSum / max(softWeight, 0.001));
    haloMask = smoothstep(0.005, 0.17, haloSum / max(haloWeight, 0.001));
    itemColor = colorWeight > 0.001 ? colorSum / colorWeight : flameColor.rgb;
}

void main() {
    float strength = params0.x;
    float riseSpeed = params0.y;
    float wobble = params0.z;
    float flameLength = params0.w;
    float brightness = params1.x;
    float time = params1.y;
    float colorMode = params1.z;
    float colorAlpha = params1.w;
    vec2 px = screen.zw;

    float lengthCurve = clamp(flameLength / 2.5, 0.0, 1.0);
    float recoveryWave = 0.5 + 0.5 * sin(time * 1.18 + texCoord.x * 3.2 - texCoord.y * 2.4);
    float disperse = smoothstep(0.28, 0.92, recoveryWave);
    float recover = 1.0 - smoothstep(0.42, 1.0, recoveryWave);
    float edgeBreath = 0.94
        + sin(time * 1.28 + texCoord.x * 5.0 + texCoord.y * 4.0) * 0.10
        + (fbm(texCoord * vec2(6.0, 5.0) + vec2(time * 0.26, -time * 0.20)) - 0.5) * 0.18
        + (disperse - 0.5) * 0.08;
    float blurScale = mix(0.72, 1.15, lengthCurve) * edgeBreath;
    float radiusPx = mix(15.0, 36.0, lengthCurve) * edgeBreath;

    vec3 quickColor;
    float quickMask = rawHandMaskColor(texCoord, quickColor);
    if (quickMask < 0.01 && nearbyDepthMask(texCoord, px, radiusPx) < 0.5) {
        fragColor = vec4(0.0);
        return;
    }

    float coreMask;
    float softMask;
    float haloMask;
    vec3 itemColor;
    sampleItemBlur(texCoord, px, blurScale, coreMask, softMask, haloMask, itemColor);

    vec2 motion = smokeMotion.xy;
    float motionPower = clamp(length(motion), 0.0, 1.0);
    float loopPhase = fract(time * 0.18);
    float pushOut = sin(loopPhase * 6.283185);
    float pushUp = sin(fract(loopPhase + 0.22) * 6.283185);
    float secondLift = smoothstep(0.38, 0.62, loopPhase) * (1.0 - smoothstep(0.78, 0.98, loopPhase));
    vec2 loopDrift = vec2(
        pushOut * 0.58 + sin(time * 0.42) * 0.18,
        -abs(pushUp) * 0.37 - secondLift * 0.35 + cos(time * 0.31) * 0.11
    );
    vec2 selfFlow = vec2(
        sin(time * 0.64 + texCoord.y * 4.2) * 0.72 + sin(time * 0.31 + texCoord.x * 7.0) * 0.28 + loopDrift.x,
        cos(time * 0.52 + texCoord.x * 3.8) * 0.58 + sin(time * 0.27 - texCoord.y * 6.0) * 0.34 + loopDrift.y
    );
    vec2 selfDir = normalize(selfFlow + vec2(0.001, -0.22));
    vec2 riseDir = normalize(vec2(
        sin(time * 0.72 + texCoord.y * 6.0) * 0.42,
        -1.0 + cos(time * 0.54 + texCoord.x * 5.0) * 0.18
    ));
    vec2 smokeDir = motion * 0.82 + riseDir * (0.18 + (1.0 - motionPower) * 0.30) + selfDir * 0.34 + loopDrift * 0.18;
    if (length(smokeDir) < 0.015) {
        smokeDir = riseDir;
    }
    smokeDir = normalize(smokeDir + riseDir * (0.18 + (1.0 - motionPower) * 0.24));
    vec2 smokeNormal = vec2(-smokeDir.y, smokeDir.x);

    float hillWave = smoothstep(0.18, 0.86, 0.5 + 0.5 * sin(time * 0.44 + texCoord.x * 5.4 + texCoord.y * 2.6));
    float maxDist = mix(16.0, 44.0, lengthCurve) * (0.37 + motionPower * 1.36 + riseSpeed * 0.25 + disperse * 0.26 + hillWave * 0.32 + secondLift * 0.18) * edgeBreath;
    float spread = mix(4.2, 12.2, lengthCurve) * (0.78 + motionPower * 2.90 + wobble * 1.03 + disperse * 0.68 + hillWave * 0.66 + abs(pushOut) * 0.19) * edgeBreath;

    float smokeMask = 0.0;
    float veilMask = 0.0;
    float smokeWeight = 0.0;
    float veilWeight = 0.0;
    vec3 smokeColorSum = vec3(0.0);
    float smokeColorWeight = 0.0;

    for (int i = 1; i <= 12; i++) {
        float fi = float(i);
        float t = fi / 12.0;
        vec2 orbitFlow = vec2(
            sin(time * (0.42 + t * 0.22) + fi * 0.61),
            cos(time * (0.38 + t * 0.18) + fi * 0.47)
        );
        vec2 loopStep = vec2(
            sin(time * 0.72 + t * 4.2 + fi * 0.33) * (0.44 + t * 0.88),
            -abs(sin(time * 0.54 + t * 3.1 + fi * 0.27)) * (0.30 + t * 0.74) - secondLift * t * 0.58
        );
        vec2 flow = motion * motionPower * (1.6 + t * 3.2) + selfDir * (0.65 + t * 1.35) + orbitFlow * (0.35 + t * 0.50) + loopDrift * (0.60 + t * 0.88) + loopStep;
        float localCycle = 0.5 + 0.5 * sin(time * (1.05 + t * 0.42) + fi * 0.83 + texCoord.x * 3.0);
        float localDisperse = smoothstep(0.26, 0.88, localCycle);
        float localRecover = 1.0 - smoothstep(0.44, 1.0, localCycle);
        float puff = 0.72
            + fbm(texCoord * vec2(10.0, 7.0) + flow * 0.8 + vec2(time * 0.38 + fi * 0.31, -time * 0.30 + fi * 0.17)) * 0.48
            + fbm(texCoord * vec2(5.0, 3.8) - flow * 0.55 + vec2(-time * 0.22 + fi * 0.12, time * 0.18)) * 0.18
            + sin(time * 1.05 + fi * 0.95 + texCoord.y * 12.0) * 0.08;
        puff *= 0.94 + localRecover * 0.12 - localDisperse * 0.06;
        float coreW = exp(-t * 3.10) * (1.0 - t * 0.38) * puff;
        float veilW = exp(-t * 1.32) * (1.0 - t * 0.20) * puff;
        float curlA = fbm(texCoord * vec2(8.5, 6.7) + flow + vec2(time * 0.68 + fi * 0.22, -time * 0.50 + fi * 0.30)) - 0.5;
        float curlB = sin(time * (1.42 + t * 0.90) + fi * 1.42 + texCoord.x * 12.5 + texCoord.y * 8.0) * 0.5;
        float curlC = sin(time * 0.98 + t * 7.2 + texCoord.x * 5.0 - texCoord.y * 4.6) * 0.5;
        float curl = curlA * 0.68 + curlB * 0.22 + curlC * 0.10;
        float wobbleAlong = (0.70 + t * 2.85) * (0.64 + wobble * 1.08 + motionPower * 1.76 + localDisperse * 0.38);
        vec2 sampleUv = texCoord + smokeDir * px * maxDist * t;
        sampleUv += loopDrift * px * spread * (0.88 + t * 2.12);
        sampleUv += loopStep * px * spread * (0.38 + t * 1.38);
        sampleUv += smokeNormal * px * curl * spread * wobbleAlong * (0.82 + motionPower * 1.24 + localDisperse * 0.42);
        sampleUv += smokeDir * px * (fbm(texCoord * 5.0 + flow * 0.7 + vec2(-time * 0.40, time * 0.32 + fi)) - 0.5) * spread * t * (0.46 + motionPower * 1.06);
        sampleUv += vec2(
            sin(time * (0.74 + t * 0.22) + fi * 0.55 + texCoord.y * 5.0),
            cos(time * (0.58 + t * 0.18) + fi * 0.49 + texCoord.x * 4.0)
        ) * px * spread * (0.42 + t * 0.92 + hillWave * 0.38);
        sampleUv.y += px.y * sin(time * (1.08 + t * 0.70) + fi * 0.72 + texCoord.x * 8.0) * spread * (0.48 + t * 1.42 + motionPower * 1.12 + hillWave * 0.50);

        vec3 sc;
        float sm = rawHandMaskColor(clamp(sampleUv, vec2(0.0), vec2(1.0)), sc);
        float coreFade = 1.0 - smoothstep(0.10, 0.72, t);
        float veilFade = 1.0 - smoothstep(0.48, 1.0, t);
        smokeMask += sm * coreW * coreFade;
        veilMask += sm * veilW * veilFade;
        smokeWeight += coreW;
        veilWeight += veilW;
        smokeColorSum += sc * sm * (coreW + veilW * 0.42);
        smokeColorWeight += sm * (coreW + veilW * 0.42);
    }

    smokeMask = smoothstep(0.020, 0.46, smokeMask / max(smokeWeight, 0.001));
    veilMask = smoothstep(0.010, 0.34, veilMask / max(veilWeight, 0.001));
    if (smokeColorWeight > 0.001) {
        itemColor = mix(itemColor, smokeColorSum / smokeColorWeight, 0.36);
    }

    vec3 baseColor = colorMode < 0.5 ? itemColor : flameColor.rgb;
    float baseLuma = dot(baseColor, vec3(0.299, 0.587, 0.114));
    baseColor = clamp(mix(vec3(baseLuma), baseColor, colorMode < 0.5 ? 0.72 : 0.96), vec3(0.0), vec3(1.0));
    if (colorMode >= 0.5 && baseLuma > 0.88) {
        baseColor *= 0.88 / max(baseLuma, 0.001);
    }
    vec3 vaporColor = mix(vec3(0.93, 0.97, 1.0), baseColor, colorMode < 0.5 ? 0.18 : 0.62);

    float edgeBlur = max(softMask - coreMask * 0.48, 0.0);
    float wideGlow = max(haloMask - softMask * 0.34, 0.0);
    vec2 motionFlow = motion * motionPower * 2.2 + selfDir * 1.4;
    float smokeLobes = fbm(texCoord * vec2(7.2, 5.4) + motionFlow + vec2(time * 0.28, -time * 0.23));
    float smokeGrain = fbm(texCoord * vec2(18.0, 13.0) + motionFlow * 1.6 + vec2(time * 0.52, -time * 0.40));
    float smokeRecover = fbm(texCoord * vec2(9.0, 6.8) - motionFlow * 0.8 + vec2(-time * 0.24, time * 0.18));
    float recoverMask = smoothstep(0.20, 0.78, smokeRecover) * recover;
    float disperseMask = smoothstep(0.46, 0.88, smokeGrain) * disperse;
    float lobeMask = smoothstep(0.22, 0.88, smokeLobes);
    float smokeBreakup = mix(0.58, 1.18, lobeMask) * mix(0.78, 1.08, smokeGrain) * (1.0 - disperseMask * 0.28 + recoverMask * 0.16);
    float softField = smoothstep(0.00, 0.68, (veilMask * 0.88 + smokeMask * 0.38 + wideGlow * 0.22) * smokeBreakup);
    float glowField = smoothstep(0.07, 0.82, (smokeMask * 0.62 + edgeBlur * 0.48 + coreMask * 0.18) * mix(0.84, 1.12, smokeGrain));
    float coreField = smoothstep(0.30, 0.88, coreMask);
    float dissolve = smoothstep(
        0.18,
        0.76,
        fbm(texCoord * vec2(11.0, 8.5) + vec2(time * 0.42, -time * 0.32))
    );
    float edgeDissolve = mix(0.42, 1.06, dissolve) * (1.0 - disperseMask * 0.18 + recoverMask * 0.10);
    softField *= edgeDissolve;
    glowField *= mix(0.62, 1.0, dissolve);

    float life = fbm(texCoord * vec2(10.0, 8.0) + vec2(time * 0.84, -time * 0.62));
    float strengthCurve = 1.0 - exp(-clamp(strength, 0.0, 2.0) * 1.02);
    float brightnessCurve = 0.64 + (1.0 - exp(-clamp(brightness, 0.0, 2.0) * 0.66)) * 0.54;
    float source = softField * 0.38 + glowField * 0.38 + coreField * 0.18;
    source *= strengthCurve * brightnessCurve * colorAlpha * (0.94 + life * 0.12);
    source = clamp(source, 0.0, 0.72);

    vec3 smokeColor = min(vaporColor * 1.04 + vec3(0.065), vec3(1.0));
    vec3 glowColor = min(mix(vaporColor * 1.18, vec3(1.0), 0.42), vec3(1.0));
    vec3 coreColor = min(mix(vaporColor * 1.34, vec3(1.0), 0.68), vec3(1.0));
    vec3 color = mix(smokeColor, glowColor, clamp(glowField * 0.54, 0.0, 1.0));
    color = mix(color, coreColor, clamp(coreField * 0.38, 0.0, 1.0));
    color *= 0.98 + life * 0.06 + sin(time * 2.4 + texCoord.x * 7.0 + texCoord.y * 5.0) * 0.010;

    fragColor = vec4(max(color, vec3(0.0)), source);
}
