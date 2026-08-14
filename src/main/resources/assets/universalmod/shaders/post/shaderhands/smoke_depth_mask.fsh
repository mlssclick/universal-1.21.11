#version 150

in vec2 vUv;
out vec4 fragColor;

uniform sampler2D BeforeDepth;
uniform sampler2D AfterDepth;

layout(std140) uniform MaskData {
    vec4 TexelData;
};

void main() {
    float depthBefore = texture(BeforeDepth, vUv).r;
    float depthAfter = texture(AfterDepth, vUv).r;
    float handMask = step(0.0001, depthBefore - depthAfter);
    float resultDepth = mix(1.0, depthAfter, handMask);
    fragColor = vec4(resultDepth, resultDepth, resultDepth, 1.0);
}
