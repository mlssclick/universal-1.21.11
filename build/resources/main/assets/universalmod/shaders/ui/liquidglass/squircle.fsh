#version 150
in vec2 FragCoord;
in vec4 FragColor;
flat in int QuadIndex;
layout(std140) uniform SquircleParamsArray { vec4 params[1536]; };
out vec4 OutColor;
float roundedBoxSDF(vec2 p, vec2 b, vec4 r, float smoothness) {
    r.xy = (p.x > 0.0) ? r.xy : r.zw;
    r.x = (p.y > 0.0) ? r.x : r.y;
    vec2 q = abs(p) - b + r.x;
    vec2 q_clamped = max(q, 0.0);
    float len = pow(pow(q_clamped.x, smoothness) + pow(q_clamped.y, smoothness), 1.0/smoothness);
    return min(max(q.x, q.y), 0.0) + len - r.x;
}
void main(){
    int b=QuadIndex*3;vec4 Radius=params[b];vec4 s=params[b+1];vec2 Size=s.xy;float Smoothness=s.z;float CornerSmoothness=s.w;
    vec2 center=Size*0.5;
    float distance=roundedBoxSDF(center-(FragCoord*Size),center-1.0,Radius,CornerSmoothness);
    float alpha=1.0-smoothstep(1.0-Smoothness,1.0,distance);
    vec4 finalColor=vec4(FragColor.rgb,FragColor.a*alpha);
    if(finalColor.a==0.0)discard;
    OutColor=finalColor;
}
