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
    float result = step(0.0001, depthBefore - depthAfter);

    float editorSide = TexelData.z;
    if (editorSide < -0.5 && vUv.x > 0.52) {
        result = 0.0;
    } else if (editorSide > 0.5 && vUv.x < 0.48) {
        result = 0.0;
    }

    fragColor = vec4(result, result, result, result);
}
