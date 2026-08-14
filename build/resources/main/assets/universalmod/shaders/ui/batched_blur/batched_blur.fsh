#version 150

#moj_import <universalmod:common.glsl>

in vec2 FragCoord;
in vec4 FragColor;
flat in int QuadIndex;

uniform sampler2D Sampler0;

layout(std140) uniform BlurParamsArray {
    vec4 params[3072];
};

layout(std140) uniform BlurRegion {
    vec4 Region;
};

out vec4 OutColor;

void main() {
    int base = QuadIndex * 3;
    vec4 Radius = params[base];
    vec4 sizeSmoothSplit = params[base + 1];
    vec4 splitColor = params[base + 2];
    vec2 Size = sizeSmoothSplit.xy;
    float Smoothness = sizeSmoothSplit.z;

    vec2 texCoord = clamp((gl_FragCoord.xy - Region.xy) / max(Region.zw, vec2(1.0)), vec2(0.0), vec2(1.0));
    vec3 blurred = texture(Sampler0, texCoord).rgb;

    vec4 tint = FragColor;
    float splitY = sizeSmoothSplit.w;
    if (splitY >= 0.0 && FragCoord.y * Size.y < splitY) {
        tint = splitColor;
    }

    float tintStrength = clamp(tint.a * (1.0 - dot(tint.rgb, vec3(0.299, 0.587, 0.114))) * 0.72, 0.0, 0.82);
    vec4 color = vec4(mix(blurred, tint.rgb, tintStrength), tint.a);
    color.a *= ralpha(Size, FragCoord, Radius, Smoothness);

    if (color.a == 0.0) { discard; }
    OutColor = color;
}
