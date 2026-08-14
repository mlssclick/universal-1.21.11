#version 330

uniform sampler2D InSampler;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec2 oneTexel = 1.0 / InSize;
    vec4 center = texture(InSampler, texCoord);
    vec4 left = texture(InSampler, texCoord - vec2(oneTexel.x, 0.0));
    vec4 right = texture(InSampler, texCoord + vec2(oneTexel.x, 0.0));
    vec4 up = texture(InSampler, texCoord - vec2(0.0, oneTexel.y));
    vec4 down = texture(InSampler, texCoord + vec2(0.0, oneTexel.y));

    float total = clamp(
        abs(center.a - left.a)
        + abs(center.a - right.a)
        + abs(center.a - up.a)
        + abs(center.a - down.a),
        0.0,
        1.0
    );
    vec3 outColor = center.rgb * center.a
        + left.rgb * left.a
        + right.rgb * right.a
        + up.rgb * up.a
        + down.rgb * down.a;
    fragColor = vec4(outColor * 0.2, total);
}
