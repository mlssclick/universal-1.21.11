#version 150

in vec2 FragCoord;
flat in int QuadIndex;

layout(std140) uniform EmotionWheelArcParams {
    vec4 params[704];
};

out vec4 OutColor;

vec4 sampleColor(vec2 uv, int base) {
    uv = clamp(uv, 0.0, 1.0);
    float u = uv.x;
    float v = uv.y;
    float u0 = (1.0 - u) * (1.0 - u);
    float u1 = 2.0 * u * (1.0 - u);
    float u2 = u * u;
    float v0 = (1.0 - v) * (1.0 - v);
    float v1 = 2.0 * v * (1.0 - v);
    float v2 = v * v;
    vec4 result = vec4(0.0);
    result += params[base + 2] * u0 * v0;
    result += params[base + 3] * u1 * v0;
    result += params[base + 4] * u2 * v0;
    result += params[base + 5] * u0 * v1;
    result += params[base + 6] * u1 * v1;
    result += params[base + 7] * u2 * v1;
    result += params[base + 8] * u0 * v2;
    result += params[base + 9] * u1 * v2;
    result += params[base + 10] * u2 * v2;
    return result;
}

float arcDistance(vec2 p, float outerRadius, float thickness, float halfArc) {
    vec2 q = p;
    q.y = abs(q.y);
    float halfThickness = thickness * 0.5;
    float midRadius = outerRadius - halfThickness;
    vec2 endPoint = vec2(cos(halfArc), sin(halfArc)) * midRadius;
    vec2 tangent = vec2(-sin(halfArc), cos(halfArc));
    float radialDist = abs(length(q) - midRadius) - halfThickness;
    float capDist = dot(q - endPoint, tangent);
    return max(radialDist, capDist);
}

float shapeMask(vec2 p, float outerRadius, float thickness, float halfArc) {
    float dist = arcDistance(p, outerRadius, thickness, halfArc);
    float aa = max(0.70, fwidth(length(p)));
    return 1.0 - smoothstep(-aa, aa, dist);
}

float kawaseMask(vec2 p, float outerRadius, float thickness, float halfArc, float radius) {
    float r1 = max(1.0, radius * 0.24);
    float r2 = max(1.5, radius * 0.50);
    float r3 = max(2.0, radius * 0.78);
    float sum = shapeMask(p, outerRadius, thickness, halfArc) * 4.0;
    float weight = 4.0;
    vec2 d1 = vec2(r1, r1);
    sum += shapeMask(p + vec2( d1.x,  d1.y), outerRadius, thickness, halfArc) * 2.0;
    sum += shapeMask(p + vec2(-d1.x,  d1.y), outerRadius, thickness, halfArc) * 2.0;
    sum += shapeMask(p + vec2( d1.x, -d1.y), outerRadius, thickness, halfArc) * 2.0;
    sum += shapeMask(p + vec2(-d1.x, -d1.y), outerRadius, thickness, halfArc) * 2.0;
    weight += 8.0;
    vec2 d2 = vec2(r2, r2);
    sum += shapeMask(p + vec2( d2.x,  d2.y), outerRadius, thickness, halfArc) * 1.35;
    sum += shapeMask(p + vec2(-d2.x,  d2.y), outerRadius, thickness, halfArc) * 1.35;
    sum += shapeMask(p + vec2( d2.x, -d2.y), outerRadius, thickness, halfArc) * 1.35;
    sum += shapeMask(p + vec2(-d2.x, -d2.y), outerRadius, thickness, halfArc) * 1.35;
    weight += 5.4;
    vec2 d3 = vec2(r3, r3);
    sum += shapeMask(p + vec2( d3.x,  d3.y), outerRadius, thickness, halfArc) * 0.75;
    sum += shapeMask(p + vec2(-d3.x,  d3.y), outerRadius, thickness, halfArc) * 0.75;
    sum += shapeMask(p + vec2( d3.x, -d3.y), outerRadius, thickness, halfArc) * 0.75;
    sum += shapeMask(p + vec2(-d3.x, -d3.y), outerRadius, thickness, halfArc) * 0.75;
    weight += 3.0;
    return clamp(sum / weight, 0.0, 1.0);
}

void main() {
    int base = QuadIndex * 11;
    vec4 arc = params[base];
    float blurRadius = max(params[base + 1].x, 0.0);
    vec2 coord = clamp(FragCoord, vec2(0.0), vec2(1.0));
    float drawSize = max(arc.x + blurRadius * 2.0, 1.0);
    vec2 p = (coord - vec2(0.5)) * drawSize;
    float rotation = radians(arc.w);
    float c = cos(rotation);
    float s = sin(rotation);
    vec2 rotated = vec2(p.x * c + p.y * s, -p.x * s + p.y * c);
    float halfArc = radians(arc.z) * 0.5;
    float outerRadius = arc.x * 0.5;
    float thickness = arc.y;
    float core = shapeMask(rotated, outerRadius, thickness, halfArc);
    float blur = blurRadius > 0.01 ? kawaseMask(rotated, outerRadius, thickness, halfArc, blurRadius) : core;
    float softAlpha = clamp(core * 0.34 + blur * 0.54, 0.0, 0.88);
    if (softAlpha <= 0.002) discard;
    vec4 color = sampleColor(coord, base);
    OutColor = vec4(color.rgb, color.a * softAlpha);
}
