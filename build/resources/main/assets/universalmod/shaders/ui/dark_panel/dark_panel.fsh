#version 150

#moj_import <universalmod:common.glsl>

in vec2 FragCoord;
flat in int QuadIndex;

layout(std140) uniform DarkPanelParamsArray {
    vec4 params[2048];
};

out vec4 OutColor;

void main() {
    int base = QuadIndex * 4;
    vec4 radius = max(params[base], vec4(0.0));
    vec4 sizeAlphaSmooth = params[base + 1];
    vec4 appearance = params[base + 2];
    vec3 userDarkColor = clamp(params[base + 3].rgb, vec3(0.0), vec3(1.0));

    vec2 size = max(sizeAlphaSmooth.xy, vec2(1.0));
    float globalAlpha = clamp(sizeAlphaSmooth.z, 0.0, 1.0);
    float smoothness = max(sizeAlphaSmooth.w, 0.5);
    float gradientStrength = clamp(appearance.x, 0.0, 1.0);
    float shadowEnabled = step(0.5, appearance.y);

    const float SHADOW_PAD = 2.0;
    vec2 expandedSize = size + vec2(SHADOW_PAD * 2.0 * shadowEnabled);
    vec2 localPx = FragCoord * expandedSize - vec2(SHADOW_PAD * shadowEnabled);
    vec2 panelUv = localPx / size;

    vec2 center = size * 0.5;
    vec2 halfSize = max(center - 1.0, vec2(0.0));
    vec2 panelPos = center - localPx;
    float panelDistance = rdist(panelPos, halfSize, radius);
    float panelMask = 1.0 - smoothstep(1.0 - smoothness, 1.0, panelDistance);

    vec2 shadowLocal = localPx - vec2(0.0, 0.6);
    float shadowDistance = rdist(center - shadowLocal, halfSize, radius);
    float outside = max(shadowDistance, 0.0);
    float shadowMask = (1.0 - smoothstep(0.0, 1.6, outside)) * (1.0 - clamp(panelMask, 0.0, 1.0));
    float shadowAlpha = shadowEnabled * shadowMask * 0.16 * globalAlpha;

    if (panelMask <= 0.001) {
        if (shadowAlpha <= 0.001) discard;
        OutColor = vec4(0.0, 0.0, 0.0, shadowAlpha);
        return;
    }

    vec2 uv = clamp(panelUv, vec2(0.0), vec2(1.0));

    float diagonal = clamp((1.0 - uv.y) * 0.68 + (1.0 - uv.x) * 0.32, 0.0, 1.0);
    float gradient = smoothstep(0.03, 0.97, diagonal);

    vec3 flatTone = userDarkColor;
    vec3 blackTone = userDarkColor * 0.24;
    vec3 silverTone = vec3(0.315, 0.340, 0.390);
    vec3 graded = mix(blackTone, silverTone, gradient * 0.88);
    vec3 color = mix(flatTone, graded, gradientStrength);

    vec2 p = (uv - vec2(0.25, 0.14)) * vec2(0.78, 1.08);
    float bloom = exp(-dot(p, p) * 2.55);
    color += vec3(0.070, 0.078, 0.095) * bloom * gradientStrength;

    float silverSweep = exp(-pow((uv.x + uv.y * 0.70) - 0.60, 2.0) * 8.0);
    color += vec3(0.030, 0.034, 0.044) * silverSweep * gradientStrength;

    vec2 edgeUv = abs(uv * 2.0 - 1.0);
    float vignette = smoothstep(0.30, 1.0, max(edgeUv.x, edgeUv.y));
    color *= 1.0 - vignette * mix(0.07, 0.20, gradientStrength);

    OutColor = vec4(clamp(color, vec3(0.0), vec3(1.0)), globalAlpha * panelMask * 0.97);
}
