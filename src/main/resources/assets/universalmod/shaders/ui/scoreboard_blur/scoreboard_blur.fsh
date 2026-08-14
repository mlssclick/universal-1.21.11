#version 330
uniform sampler2D InSampler;
layout(std140) uniform SamplerInfo { vec2 OutSize; vec2 InSize; };
layout(std140) uniform BlurConfig { vec2 BlurDir; float Radius; };
in vec2 texCoord;
out vec4 fragColor;
void main() {
    vec2 sampleStep = (1.0 / InSize) * BlurDir;
    float actualRadius = max(round(Radius), 1.0);
    vec4 blurred = vec4(0.0);
    for (float offset = -actualRadius + 0.5; offset <= actualRadius; offset += 2.0) blurred += texture(InSampler, texCoord + sampleStep * offset);
    blurred += texture(InSampler, texCoord + sampleStep * actualRadius) / 2.0;
    fragColor = blurred / (actualRadius + 0.5);
}
