#version 330

uniform sampler2D InSampler;
layout(std140) uniform CropConfig { vec4 SourceRect; };

in vec2 texCoord;
out vec4 fragColor;

void main() {
    fragColor = texture(InSampler, SourceRect.xy + texCoord * SourceRect.zw);
}
