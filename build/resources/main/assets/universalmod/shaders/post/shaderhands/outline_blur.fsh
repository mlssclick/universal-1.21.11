#version 150

in vec2 vUv;
out vec4 fragColor;

uniform sampler2D SourceSampler;

layout(std140) uniform HandsBlurData {
    vec4 TexelAndDirection;
    vec4 RadiusData;
};

float gaussian(float x, float sigma) {
    return exp(-(x * x) / (2.0 * sigma * sigma));
}

void main() {
    vec2 texel = TexelAndDirection.xy;
    vec2 direction = TexelAndDirection.zw;
    float radius = max(1.0, RadiusData.x);
    float sigma = max(radius * 0.5, 0.5);

    float sum = texture(SourceSampler, vUv).a;
    float weightSum = 1.0;

    for (int i = 1; i <= 24; i++) {
        if (float(i) > radius) {
            break;
        }
        float weight = gaussian(float(i), sigma);
        vec2 offset = texel * direction * float(i);
        sum += texture(SourceSampler, vUv + offset).a * weight;
        sum += texture(SourceSampler, vUv - offset).a * weight;
        weightSum += weight * 2.0;
    }

    float blurred = sum / max(weightSum, 0.0001);
    fragColor = vec4(blurred, blurred, blurred, blurred);
}
