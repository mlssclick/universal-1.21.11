#version 330

uniform sampler2D InSampler;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform BlurConfig {
    vec2 BlurDir;
};

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec2 sampleStep = BlurDir / InSize;
    vec4 blurred = vec4(0.0);
    for (float radius = -2.0; radius <= 2.0; radius += 1.0) {
        blurred += texture(InSampler, texCoord + sampleStep * radius);
    }
    fragColor = vec4(blurred.rgb / 5.0, blurred.a);
}
