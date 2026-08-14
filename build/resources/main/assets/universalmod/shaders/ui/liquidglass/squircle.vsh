#version 150
#moj_import <universalmod:common.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>
in vec3 Position;
in vec4 Color;
in float LineWidth;
out vec2 FragCoord;
out vec4 FragColor;
flat out int QuadIndex;
layout(std140) uniform SquircleParamsArray { vec4 params[1536]; };
void main(){int i=max(int(LineWidth+0.5)-1,0);vec4 z=params[i*3+2];gl_Position=ProjMat*ModelViewMat*vec4(Position.xy,z.x,1.0);FragCoord=rvertexcoord(gl_VertexID);FragColor=Color;QuadIndex=i;}
