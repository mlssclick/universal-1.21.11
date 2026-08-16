#version 150

#moj_import <universalmod:common.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
in float LineWidth;

out vec2 FragCoord;
flat out int QuadIndex;

void main() {
    QuadIndex = max(int(LineWidth + 0.5) - 1, 0);
    gl_Position = ProjMat * ModelViewMat * vec4(Position.xy, 0.0, 1.0);
    FragCoord = rvertexcoord(gl_VertexID);
}
