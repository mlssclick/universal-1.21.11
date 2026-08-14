#version 150

in vec2 vUv;
out vec4 fragColor;

uniform sampler2D MaskSampler;
uniform sampler2D BlurSampler;

layout(std140) uniform HandsOutlineData {
    vec4 TexelOutlineGlow;
    vec4 StrengthOpacityTimeStyle;
    vec4 ColorTop;
    vec4 ColorBottom;
};

float maskAt(vec2 uv) {
    return texture(MaskSampler, uv).a;
}

void main() {
    float mask = maskAt(vUv);
    float blurred = texture(BlurSampler, vUv).a;

    float outlineWidth = TexelOutlineGlow.z;
    float glowStrength = TexelOutlineGlow.w;
    float outlineStrength = StrengthOpacityTimeStyle.x;
    float opacity = StrengthOpacityTimeStyle.y;
    float time = StrengthOpacityTimeStyle.z;
    float staticColor = StrengthOpacityTimeStyle.w;

    float primaryFlow = sin(vUv.x * 5.2 + time * 0.58);
    float secondaryFlow = sin(vUv.x * 11.0 - time * 0.24);
    float gradientPosition = clamp(0.5 + primaryFlow * 0.42 + secondaryFlow * 0.08, 0.0, 1.0);
    gradientPosition = smoothstep(0.04, 0.96, gradientPosition);

    vec3 topColor = ColorTop.rgb;
    vec3 bottomColor = ColorBottom.rgb;
    vec3 gradient = staticColor >= 0.5 ? topColor : mix(bottomColor, topColor, gradientPosition);

    vec2 d = TexelOutlineGlow.xy * max(outlineWidth, 0.5);
    vec2 h = d * 0.5;

    float nearby = mask;
    nearby = max(nearby, maskAt(vUv + vec2(d.x, 0.0)));
    nearby = max(nearby, maskAt(vUv - vec2(d.x, 0.0)));
    nearby = max(nearby, maskAt(vUv + vec2(0.0, d.y)));
    nearby = max(nearby, maskAt(vUv - vec2(0.0, d.y)));
    nearby = max(nearby, maskAt(vUv + d));
    nearby = max(nearby, maskAt(vUv - d));
    nearby = max(nearby, maskAt(vUv + vec2(d.x, -d.y)));
    nearby = max(nearby, maskAt(vUv + vec2(-d.x, d.y)));
    nearby = max(nearby, maskAt(vUv + vec2(h.x, 0.0)));
    nearby = max(nearby, maskAt(vUv - vec2(h.x, 0.0)));
    nearby = max(nearby, maskAt(vUv + vec2(0.0, h.y)));
    nearby = max(nearby, maskAt(vUv - vec2(0.0, h.y)));

    float outside = 1.0 - mask;
    float outline = max(nearby - mask, 0.0) * outside;
    float glow = pow(clamp(blurred, 0.0, 1.0), 0.72) * outside;
    vec3 hotEdge = mix(gradient, vec3(1.0), 0.22);

    float glowAlpha = 1.0 - exp(-glow * glowStrength);
    float outlineAlpha = clamp(outline * outlineStrength, 0.0, 1.0);
    float alpha = max(glowAlpha, outlineAlpha) * opacity;
    vec3 color = mix(gradient, hotEdge, outlineAlpha);

    if (alpha <= 0.001) {
        discard;
    }

    fragColor = vec4(color, alpha);
}
