#version 150

in vec2 texCoord;
out vec4 fragColor;

uniform sampler2D InputSampler;

layout(std140) uniform HandsFlameData {
    vec4 flameColor;
    vec4 params0;
    vec4 params1;
    vec4 screen;
    vec4 smokeMotion;
};

void main() {
    vec2 texel = screen.zw * 2.0;
    float offset = max(smokeMotion.w, 0.0) + 0.5;

    vec2 d = texel * offset;
    vec2 h = texel * (offset * 0.55);

    vec4 color = vec4(0.0);
    color += texture(InputSampler, clamp(texCoord + vec2( d.x,  d.y), vec2(0.0), vec2(1.0))) * 0.145;
    color += texture(InputSampler, clamp(texCoord + vec2(-d.x,  d.y), vec2(0.0), vec2(1.0))) * 0.145;
    color += texture(InputSampler, clamp(texCoord + vec2( d.x, -d.y), vec2(0.0), vec2(1.0))) * 0.145;
    color += texture(InputSampler, clamp(texCoord + vec2(-d.x, -d.y), vec2(0.0), vec2(1.0))) * 0.145;

    color += texture(InputSampler, clamp(texCoord + vec2( h.x, 0.0), vec2(0.0), vec2(1.0))) * 0.105;
    color += texture(InputSampler, clamp(texCoord - vec2( h.x, 0.0), vec2(0.0), vec2(1.0))) * 0.105;
    color += texture(InputSampler, clamp(texCoord + vec2(0.0,  h.y), vec2(0.0), vec2(1.0))) * 0.105;
    color += texture(InputSampler, clamp(texCoord - vec2(0.0,  h.y), vec2(0.0), vec2(1.0))) * 0.105;

    fragColor = color;
}
