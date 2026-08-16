#version 150

#moj_import <universalmod:common.glsl>

in vec2 FragCoord;
flat in int QuadIndex;

layout(std140) uniform HudChromeParams {
    vec4 params[1536];
};

out vec4 OutColor;

void main() {
    int base = QuadIndex * 3;
    vec4 radii = max(params[base], vec4(0.0));
    vec4 sizeAlphaSmooth = params[base + 1];
    float darkness = clamp(params[base + 2].x, 0.0, 1.0);
    vec2 size = max(sizeAlphaSmooth.xy, vec2(1.0));
    float alpha = clamp(sizeAlphaSmooth.z, 0.0, 1.0);
    float smoothness = max(sizeAlphaSmooth.w, 0.45);

    vec2 center = size * 0.5;
    vec2 pos = FragCoord * size;
    float distance = rdist(center - pos, max(center - 0.75, vec2(0.0)), radii);
    float mask = 1.0 - smoothstep(0.75 - smoothness, 0.75, distance);
    if (mask <= 0.001) discard;

    vec2 uv = clamp(FragCoord, vec2(0.0), vec2(1.0));
    float topGlow = pow(1.0 - uv.y, 2.0) * 0.15;
    float leftGlow = pow(1.0 - uv.x, 2.0) * 0.028;
    float centerLift = exp(-dot((uv - vec2(0.36, 0.14)) * vec2(1.10, 1.0), (uv - vec2(0.36, 0.14)) * vec2(1.10, 1.0)) * 4.2) * 0.055;
    vec3 baseColor = vec3(0.028, 0.034, 0.046);
    vec3 color = baseColor + vec3(0.028, 0.034, 0.047) * (topGlow + leftGlow + centerLift);
    color *= mix(1.0, 0.78, darkness);

    float edge = 1.0 - smoothstep(0.0, 1.15, abs(distance));
    vec3 border = vec3(0.66, 0.72, 0.86);
    color = mix(color, border, edge * 0.12);

    float vignette = smoothstep(0.30, 0.98, max(abs(uv.x * 2.0 - 1.0), abs(uv.y * 2.0 - 1.0)));
    color *= 1.0 - vignette * 0.16;
    OutColor = vec4(clamp(color, vec3(0.0), vec3(1.0)), alpha * mask * 0.96);
}
