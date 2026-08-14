#version 150

in vec2 texCoord;
out vec4 fragColor;

uniform sampler2D SceneSampler;
uniform sampler2D BeforeSampler;
uniform sampler2D TrailSampler;
uniform sampler2D DepthSampler;

layout(std140) uniform HandsFlameData {
    vec4 flameColor;
    vec4 params0;
    vec4 params1;
    vec4 screen;
    vec4 smokeMotion;
};

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
        p = p * 2.02 + vec2(19.17, 11.31);
        amplitude *= 0.5;
    }
    return value;
}

float depthItemMask(vec2 uv) {
    return texture(DepthSampler, clamp(uv, vec2(0.0), vec2(1.0))).r < 0.9999 ? 1.0 : 0.0;
}

void main() {
    vec2 px = screen.zw;
    float brightness = params1.x;
    float time = params1.y;
    float wobble = params0.z;
    float motionPower = clamp(smokeMotion.z, 0.0, 1.0);
    vec2 motionDir = length(smokeMotion.xy) > 0.015 ? normalize(smokeMotion.xy) : vec2(0.0, -1.0);
    vec2 motionNormal = vec2(-motionDir.y, motionDir.x);

    float cycle = 0.5 + 0.5 * sin(time * 1.10 + texCoord.x * 3.5 - texCoord.y * 2.5);
    float disperse = smoothstep(0.28, 0.92, cycle);
    float recover = 1.0 - smoothstep(0.44, 1.0, cycle);
    float loopPhase = fract(time * 0.18);
    float pushOut = sin(loopPhase * 6.283185);
    float pushUp = sin(fract(loopPhase + 0.22) * 6.283185);
    float secondLift = smoothstep(0.38, 0.62, loopPhase) * (1.0 - smoothstep(0.78, 0.98, loopPhase));
    vec2 loopDrift = vec2(
        pushOut * 0.62 + sin(time * 0.43 + texCoord.y * 1.8) * 0.19,
        -abs(pushUp) * 0.42 - secondLift * 0.40 + cos(time * 0.34 + texCoord.x * 1.5) * 0.13
    );
    vec2 xyFlow = vec2(
        sin(time * 0.56 + texCoord.y * 4.6) * 1.05 + sin(time * 0.29 + texCoord.x * 8.0) * 0.45 + loopDrift.x,
        cos(time * 0.49 + texCoord.x * 4.0) * 0.88 + sin(time * 0.33 - texCoord.y * 6.2) * 0.48 + loopDrift.y
    );
    vec2 diagFlow = vec2(
        sin(time * 0.41 + texCoord.x * 2.2 + texCoord.y * 3.1),
        cos(time * 0.37 - texCoord.x * 3.0 + texCoord.y * 2.4)
    );
    vec2 vaporRise = normalize(vec2(
        sin(time * 0.82 + texCoord.y * 5.0) * 0.42,
        -1.0
    ));
    float hillWave = smoothstep(0.18, 0.86, 0.5 + 0.5 * sin(time * 0.44 + texCoord.x * 5.4 + texCoord.y * 2.6));
    vec2 deform = vec2(
        fbm(texCoord * vec2(8.5, 6.0) + xyFlow * 0.38 + vec2(time * 0.20, -time * 0.16)) - 0.5,
        fbm(texCoord * vec2(6.4, 9.0) - diagFlow * 0.32 + vec2(-time * 0.16, time * 0.22)) - 0.5
    ) * px * (2.6 + hillWave * 2.0 + disperse * 1.3);
    vec2 liveOffset = motionDir * px * (motionPower * 3.65 + disperse * 0.70)
        + vaporRise * px * (2.36 + recover * 0.55 + hillWave * 1.36 + secondLift * 1.08)
        + xyFlow * px * (2.38 + disperse * 1.25 + hillWave * 1.02)
        + loopDrift * px * (4.32 + disperse * 1.68 + hillWave * 1.12)
        + diagFlow * px * (1.36 + recover * 0.58 + hillWave * 0.84)
        + motionNormal * px * (
            sin(texCoord.y * 13.0 + time * 1.35)
            + sin((texCoord.x + texCoord.y) * 7.0 - time * 0.96) * 0.55
            + sin(texCoord.x * 15.0 + time * 0.72) * 0.22
        ) * (1.14 + wobble * 1.47 + disperse * 1.14 + hillWave * 0.91 + abs(pushOut) * 0.32);
    vec2 loopArc = vec2(
        sin(time * 0.86 + texCoord.y * 3.2) * px.x * (2.24 + secondLift * 1.60),
        -abs(sin(time * 0.62 + texCoord.x * 2.6)) * px.y * (1.92 + abs(pushOut) * 1.44)
    );
    vec2 trailUv = clamp(texCoord + liveOffset * 1.18 + deform * 1.15 + loopArc, vec2(0.0), vec2(1.0));

    vec4 trail = texture(TrailSampler, trailUv);

    vec4 scene = texture(SceneSampler, texCoord);
    vec4 sceneBefore = texture(BeforeSampler, texCoord);

    float depth = texture(DepthSampler, texCoord).r;
    float depthMask = depth < 0.9999 ? 1.0 : 0.0;

    vec3 delta = abs(scene.rgb - sceneBefore.rgb);
    float peak = max(max(delta.r, delta.g), delta.b);
    float luma = dot(delta, vec3(0.299, 0.587, 0.114));
    float maskValue = peak * 0.78 + luma * 0.88 + abs(scene.a - sceneBefore.a);
    float colorMask = smoothstep(0.004, 0.060, maskValue);
    float mask = max(depthMask, colorMask);
    float itemCover = smoothstep(0.04, 0.40, mask);
    float itemCut = depthMask;
    float edgeRadius = 5.5;
    float nearAround = depthMask;
    nearAround = max(nearAround, depthItemMask(texCoord + vec2(edgeRadius, 0.0) * px));
    nearAround = max(nearAround, depthItemMask(texCoord - vec2(edgeRadius, 0.0) * px));
    nearAround = max(nearAround, depthItemMask(texCoord + vec2(0.0, edgeRadius) * px));
    nearAround = max(nearAround, depthItemMask(texCoord - vec2(0.0, edgeRadius) * px));
    nearAround = max(nearAround, depthItemMask(texCoord + vec2(edgeRadius, edgeRadius) * px));
    nearAround = max(nearAround, depthItemMask(texCoord - vec2(edgeRadius, edgeRadius) * px));
    nearAround = max(nearAround, depthItemMask(texCoord + vec2(edgeRadius, -edgeRadius) * px));
    nearAround = max(nearAround, depthItemMask(texCoord + vec2(-edgeRadius, edgeRadius) * px));
    float itemEdge = (1.0 - itemCut) * smoothstep(0.18, 1.0, nearAround);

    if (trail.a < 0.003 && itemCover < 0.01) {
        fragColor = vec4(scene.rgb, 1.0);
        return;
    }

    float occlusionNoise = 0.5
        + sin(texCoord.x * 16.0 + texCoord.y * 9.0 + time * 0.72) * 0.20
        + sin(texCoord.x * 7.0 - texCoord.y * 12.0 - time * 0.48) * 0.16
        + (fbm(texCoord * vec2(6.0, 4.8) + vec2(time * 0.18, -time * 0.14)) - 0.5) * 0.28;
    float layerWave = 0.5 + 0.5 * sin(time * 0.62 + texCoord.x * 4.0 - texCoord.y * 5.5);
    float sideLayer = 0.5 + 0.5 * sin(time * 0.48 - texCoord.x * 6.2 + texCoord.y * 3.8);
    float frontLayer = smoothstep(0.36, 0.82, layerWave * 0.62 + sideLayer * 0.38 + (occlusionNoise - 0.5) * 0.55);
    float underLayer = smoothstep(0.30, 0.76, (1.0 - layerWave) * 0.54 + (1.0 - sideLayer) * 0.46 + (occlusionNoise - 0.5) * 0.35);
    float edgeVisibility = mix(0.12, 0.96, frontLayer) * (1.0 - underLayer * 0.34);
    float itemSmokeVisibility = itemEdge * edgeVisibility;
    float behindItem = (1.0 - itemCut) * max(1.0 - itemCover, itemSmokeVisibility);
    behindItem = clamp(behindItem, 0.0, 1.0);

    float softField = smoothstep(0.002, 0.18, trail.a) * behindItem;
    float glowField = smoothstep(0.045, 0.40, trail.a) * behindItem;
    float coreField = smoothstep(0.24, 0.72, trail.a) * behindItem;
    float field = max(softField * 0.72, glowField);
    float smokeBody = fbm(texCoord * vec2(8.0, 5.8) + xyFlow * 0.55 + motionDir * motionPower * 2.4 + vec2(time * 0.30, -time * 0.24));
    float smokeCuts = fbm(texCoord * vec2(18.0, 12.0) + diagFlow * 0.85 + motionNormal * motionPower * 3.2 + vec2(-time * 0.56, time * 0.42));
    float smokeRecover = fbm(texCoord * vec2(10.0, 7.2) - xyFlow * 0.45 - motionDir * motionPower * 1.4 + vec2(-time * 0.24, time * 0.18));
    float disperseMask = smoothstep(0.48, 0.88, smokeCuts) * disperse;
    float recoverMask = smoothstep(0.22, 0.76, smokeRecover) * recover;
    float smokeShape = mix(0.64, 1.06, smoothstep(0.24, 0.88, smokeBody))
        * mix(0.78, 1.04, smokeCuts)
        * (1.0 - disperseMask * 0.24 + recoverMask * 0.14);
    softField *= smokeShape;
    glowField *= mix(0.90, 1.10, smokeBody) * (1.0 - disperseMask * 0.12 + recoverMask * 0.10);
    coreField *= mix(0.96, 1.07, smokeCuts);

    float sceneLuma = dot(scene.rgb, vec3(0.299, 0.587, 0.114));
    float darkCover = 1.0 - smoothstep(0.16, 0.72, sceneLuma);
    float edgeBreath = 0.90
        + sin(time * 1.02 + texCoord.x * 4.5 + texCoord.y * 5.5) * 0.08
        + sin(time * 0.64 - texCoord.x * 7.0 + texCoord.y * 3.0) * 0.055
        + (disperse - 0.5) * 0.06;
    float brightnessCurve = 0.66 + (1.0 - exp(-clamp(brightness, 0.0, 2.0) * 0.64)) * 0.56;
    
    float glowIntensity = pow(trail.a, 0.78) * glowField;
    float softVeil = softField * (0.245 + brightnessCurve * 0.084) * edgeBreath * (1.0 + recoverMask * 0.08);
    float veil = softVeil + glowIntensity * (0.145 + brightnessCurve * 0.150) * (1.0 + darkCover * 0.05);
    veil = clamp(veil, 0.0, 0.66);
    
    veil += itemEdge * smoothstep(0.050, 0.34, trail.a) * 0.022 * (1.0 - smoothstep(0.20, 0.75, occlusionNoise));

    float blurAmount = max(softField * 0.58, glowField * smoothstep(0.012, 0.25, trail.a) * 0.44) * edgeBreath;
    vec3 sceneBase;
    if (blurAmount > 0.01) {
        vec2 bgD = px * (6.2 + blurAmount * 7.0);
        vec2 bgH = bgD * 0.55;
        vec3 blurred = vec3(0.0);
        blurred += texture(BeforeSampler, clamp(texCoord + vec2( bgD.x,  bgD.y), vec2(0.0), vec2(1.0))).rgb * 0.145;
        blurred += texture(BeforeSampler, clamp(texCoord + vec2(-bgD.x,  bgD.y), vec2(0.0), vec2(1.0))).rgb * 0.145;
        blurred += texture(BeforeSampler, clamp(texCoord + vec2( bgD.x, -bgD.y), vec2(0.0), vec2(1.0))).rgb * 0.145;
        blurred += texture(BeforeSampler, clamp(texCoord + vec2(-bgD.x, -bgD.y), vec2(0.0), vec2(1.0))).rgb * 0.145;
        blurred += texture(BeforeSampler, clamp(texCoord + vec2( bgH.x, 0.0), vec2(0.0), vec2(1.0))).rgb * 0.105;
        blurred += texture(BeforeSampler, clamp(texCoord - vec2( bgH.x, 0.0), vec2(0.0), vec2(1.0))).rgb * 0.105;
        blurred += texture(BeforeSampler, clamp(texCoord + vec2(0.0,  bgH.y), vec2(0.0), vec2(1.0))).rgb * 0.105;
        blurred += texture(BeforeSampler, clamp(texCoord - vec2(0.0,  bgH.y), vec2(0.0), vec2(1.0))).rgb * 0.105;
        
        sceneBase = mix(scene.rgb, blurred, min(blurAmount * 0.58, 1.0));
    } else {
        sceneBase = scene.rgb;
    }

    sceneBase *= (1.0 - veil * darkCover * 0.18);

    vec3 vaporWhite = vec3(0.96, 0.985, 1.0);
    vec3 softFill = mix(sceneBase, min(mix(trail.rgb * 1.14, vaporWhite, 0.68), vec3(1.0)), 0.58);
    vec3 glowFill = min(mix(trail.rgb * 1.12, vaporWhite, 0.62) + vec3(0.012), vec3(1.0));
    vec3 coreFill = min(mix(trail.rgb * 1.28, vec3(1.0), 0.76), vec3(1.0));
    vec3 flameFill = mix(softFill, glowFill, clamp(glowField, 0.0, 1.0));
    flameFill = mix(flameFill, coreFill, clamp(coreField * 0.62, 0.0, 1.0));
    vec3 covered = mix(sceneBase, flameFill, veil);

    float glowPulse = 0.88
        + sin((texCoord.y + texCoord.x * 0.35) * 13.0 + time * 3.0) * 0.045
        + sin((texCoord.x - texCoord.y * 0.7) * 7.0 - time * 1.8) * 0.032;
    vec3 glow = min(mix(trail.rgb * 1.18, vec3(1.0), 0.58), vec3(1.0)) * pow(trail.a, 0.82) * coreField * glowPulse;

    float coreIntensity = pow(trail.a * coreField, 2.15);
    vec3 core = min(mix(trail.rgb * 1.34, vec3(1.0), 0.82), vec3(1.0)) * coreIntensity;

    vec3 color = covered
        + glow * (0.28 + brightnessCurve * 0.17)
        + core * (0.060 + brightnessCurve * 0.052);

    fragColor = vec4(clamp(color, 0.0, 1.0), 1.0);
}
