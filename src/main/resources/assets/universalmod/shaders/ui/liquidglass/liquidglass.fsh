#version 150

in vec2 FragCoord;
in vec4 FragColor;
flat in int QuadIndex;

uniform sampler2D Sampler0;

layout(std140) uniform LiquidGlassParamsArray {
    vec4 params[2560];
};

layout(std140) uniform LiquidGlassFrame {
    vec4 Frame;
};

out vec4 OutColor;

float roundedBoxSDF(vec2 p, vec2 b, vec4 r, float smoothness) {
    r.xy = (p.x > 0.0) ? r.xy : r.zw;
    r.x = (p.y > 0.0) ? r.x : r.y;
    vec2 q = abs(p) - b + r.x;
    vec2 q_clamped = max(q, 0.0);
    float len = pow(pow(q_clamped.x, smoothness) + pow(q_clamped.y, smoothness), 1.0/smoothness);
    return min(max(q.x, q.y), 0.0) + len - r.x;
}

void main() {
    int baseIndex = QuadIndex * 5;
    vec4 Radius = params[baseIndex];
    vec4 sizeSmoothCorner = params[baseIndex + 1];
    vec4 alphaPowerMix = params[baseIndex + 2];
    vec4 FresnelColorPacked = params[baseIndex + 3];
    vec4 flagsDistortZ = params[baseIndex + 4];

    vec2 Size = sizeSmoothCorner.xy;
    float Smoothness = sizeSmoothCorner.z;
    float CornerSmoothness = sizeSmoothCorner.w;
    float GlobalAlpha = alphaPowerMix.x;
    float FresnelPower = alphaPowerMix.y;
    float BaseAlpha = alphaPowerMix.z;
    float FresnelMix = alphaPowerMix.w;
    vec3 FresnelColor = FresnelColorPacked.rgb;
    float FresnelAlpha = FresnelColorPacked.a;
    bool FresnelInvert = flagsDistortZ.x > 0.5;
    float DistortStrength = flagsDistortZ.y;

    vec2 center = Size * 0.5;
    vec2 box_half_size = center - 1.0;
    vec2 pos = (FragCoord * Size) - center;

    float distance = roundedBoxSDF(-pos, box_half_size, Radius, CornerSmoothness);
    float alpha = 1.0 - smoothstep(1.0 - Smoothness, 1.0, distance);

    float distToEdge = abs(roundedBoxSDF(pos, box_half_size, Radius, CornerSmoothness));
    float max_dist_norm = min(box_half_size.x, box_half_size.y);
    float edge_gradient = 1.0 - clamp(distToEdge / max_dist_norm, 0.0, 1.0);

    float fresnel;
    float fresnelBase = FresnelInvert ? edge_gradient : (1.0 - edge_gradient);
    if (FresnelPower > 20.0) {
        fresnel = exp(FresnelPower * log(clamp(fresnelBase, 0.001, 1.0)));
    } else {
        fresnel = pow(fresnelBase, FresnelPower);
    }
    fresnel = clamp(fresnel, 0.0, 1.0);

    vec2 dir = normalize(pos);
    vec2 TexCoord = gl_FragCoord.xy / max(Frame.xy, vec2(1.0));
    vec2 distortedTexCoord = TexCoord + dir * fresnel * DistortStrength;

    vec4 texColor = texture(Sampler0, distortedTexCoord) * FragColor;
    vec3 finalColor = mix(texColor.rgb, FresnelColor, fresnel * FresnelMix);
    float finalAlpha = mix(BaseAlpha, FresnelAlpha, fresnel) * alpha;

    if (finalAlpha < 0.001) discard;
    OutColor = vec4(finalColor, finalAlpha * GlobalAlpha);
}
