#version 150

in vec2 TexCoord;
uniform sampler2D Sampler0;
layout(std140) uniform KawaseParams { vec4 Params; };
out vec4 OutColor;

void main() {
    vec2 texel = Params.xy;
    float off = Params.z + 0.5;
    vec2 d = texel * off;
    vec2 o1 = vec2( d.x,  d.y);
    vec2 o2 = vec2(-d.x,  d.y);
    vec2 o3 = vec2( d.x, -d.y);
    vec2 o4 = vec2(-d.x, -d.y);
    vec3 sum = texture(Sampler0, TexCoord + o1).rgb;
    sum += texture(Sampler0, TexCoord + o2).rgb;
    sum += texture(Sampler0, TexCoord + o3).rgb;
    sum += texture(Sampler0, TexCoord + o4).rgb;
    OutColor = vec4(sum * 0.25, 1.0);
}
