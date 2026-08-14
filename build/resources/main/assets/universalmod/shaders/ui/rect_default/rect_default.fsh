#version 150

#moj_import <universalmod:common.glsl>

in vec2 FragCoord;
flat in int QuadIndex;

layout(std140) uniform RectangleParamsArray {
    vec4 params[3584];
};

out vec4 OutColor;

float roundedAlpha(vec2 coord, vec2 size, vec4 radius, float smoothness) {
    return ralpha(size, coord, radius, smoothness);
}

vec4 rectangleColor(vec2 coord, vec4 topLeft, vec4 topRight, vec4 bottomRight, vec4 bottomLeft) {
    vec4 top = mix(topLeft, topRight, coord.x);
    vec4 bottom = mix(bottomLeft, bottomRight, coord.x);
    return mix(top, bottom, coord.y);
}

void main() {
    int base = QuadIndex * 7;
    vec4 radius = params[base];
    vec4 sizeSmoothSplit = params[base + 1];
    vec4 topLeft = params[base + 2];
    vec4 topRight = params[base + 3];
    vec4 bottomRight = params[base + 4];
    vec4 bottomLeft = params[base + 5];
    vec4 splitColor = params[base + 6];

    vec2 coord = clamp(FragCoord, vec2(0.0), vec2(1.0));
    vec2 size = max(sizeSmoothSplit.xy, vec2(1.0));
    float alpha = roundedAlpha(coord, size, max(radius, vec4(0.0)), sizeSmoothSplit.z);
    vec4 color = rectangleColor(coord, topLeft, topRight, bottomRight, bottomLeft);

    float splitY = sizeSmoothSplit.w;
    if (splitY >= 0.0 && coord.y * size.y < splitY) {
        color = splitColor;
    }

    color.a *= alpha;
    if (color.a <= 0.001) {
        discard;
    }

    OutColor = color;
}
