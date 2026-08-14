#version 150

layout(std140) uniform NebulaTimeData {
    vec4 NebulaData;
    vec4 NebulaTint;
};

out vec4 fragColor;

#define iterations 14
#define formuparam2 0.79
#define volsteps 5
#define stepsize 0.390
#define zoom 0.900
#define tile 0.850
#define brightness 0.003
#define distfading 0.560
#define saturation 0.800
#define transverseSpeed 1.8
#define cloud 0.011

float field(in vec3 p, float time) {
    float strength = 7.0 + 0.03 * log(1.0e-6 + fract(sin(time) * 4373.11));
    float accum = 0.0;
    float prev = 0.0;
    float tw = 0.0;

    float mag = dot(p, p);
    p = abs(p) / mag + vec3(-0.5, -0.8 + 0.1 * sin(time * 0.7 + 2.0), -1.1 + 0.3 * cos(time * 0.3));
    float w = exp(0.0);
    accum += w * exp(-strength * pow(abs(mag - prev), 2.3));
    tw += w;
    prev = mag;

    return max(0.0, 5.0 * accum / tw - 0.7);
}

void main() {
    float time = NebulaData.x * 0.4;
    vec2 screenSize = max(NebulaData.yz, vec2(1.0));
    float alpha = NebulaData.w;
    vec3 baseColor = NebulaTint.rgb;

    vec2 uv2 = gl_FragCoord.xy / screenSize;
    vec2 uvs = uv2 * 2.0 - 1.0;
    float speed = 0.01 * cos(time * 0.02 + 3.1415926 / 4.0);
    float formuparam = formuparam2;

    vec2 uv = uvs;
    float a_xz = 0.9;
    float a_yz = -0.6;
    float a_xy = 0.9 + time * 0.08;

    mat2 rot_xz = mat2(cos(a_xz), sin(a_xz), -sin(a_xz), cos(a_xz));
    mat2 rot_yz = mat2(cos(a_yz), sin(a_yz), -sin(a_yz), cos(a_yz));
    mat2 rot_xy = mat2(cos(a_xy), sin(a_xy), -sin(a_xy), cos(a_xy));

    vec3 dir = vec3(uv * zoom, 1.0);
    vec3 from = vec3(0.0);
    vec3 forward = vec3(0.0, 0.0, 1.0);

    from.x += transverseSpeed * cos(0.01 * time) + 0.001 * time;
    from.y += transverseSpeed * sin(0.01 * time) + 0.001 * time;
    from.z += 0.003 * time;

    dir.xy *= rot_xy;
    forward.xy *= rot_xy;
    dir.xz *= rot_xz;
    forward.xz *= rot_xz;
    dir.yz *= rot_yz;
    forward.yz *= rot_yz;

    from.xy *= -rot_xy;
    from.xz *= rot_xz;
    from.yz *= rot_yz;

    float zooom = (time - 3311.0) * speed;
    from += forward * zooom;
    float sampleShift = mod(zooom, stepsize);
    float zoffset = -sampleShift;
    sampleShift /= stepsize;

    float s = 0.24;
    float s3 = s + stepsize / 2.0;
    vec3 v = vec3(0.0);
    float t3 = 0.0;
    vec3 backCol2 = vec3(0.0);

    for (int r = 0; r < volsteps; r++) {
        vec3 p2 = from + (s + zoffset) * dir;
        vec3 p3 = from + (s3 + zoffset) * dir;

        p2 = abs(vec3(tile) - mod(p2, vec3(tile * 2.0)));
        p3 = abs(vec3(tile) - mod(p3, vec3(tile * 2.0)));
        t3 = field(p3, time);

        float pa = 0.0;
        float a = 0.0;
        for (int i = 0; i < iterations; i++) {
            p2 = abs(p2) / dot(p2, p2) - formuparam;
            float d = abs(length(p2) - pa);
            a += i > 7 ? min(12.0, d) : d;
            pa = length(p2);
        }

        a *= a * a;
        float s1 = s + zoffset;
        float fade = pow(distfading, max(0.0, float(r) - sampleShift));
        v += fade;

        if (r == 0) fade *= (1.0 - sampleShift);
        if (r == volsteps - 1) fade *= sampleShift;

        v += vec3(s1, s1 * s1, s1 * s1 * s1 * s1) * a * brightness * fade;
        backCol2 += vec3(1.15 * t3 * t3, 0.35 * t3 * t3 * t3, 1.7 * t3) * fade * 0.1;

        s += stepsize;
        s3 += stepsize;
    }

    v = mix(vec3(length(v)), v, saturation);

    vec4 forCol2 = vec4(v * 0.01, 1.0);
    backCol2 *= cloud;
    backCol2.r *= 1.25;
    backCol2.g *= 0.35;
    backCol2.b *= 1.75;
    backCol2.bg = mix(backCol2.gb, backCol2.bg, 0.5 * (cos(time * 0.01) + 1.0));

    vec4 result = forCol2 + vec4(backCol2, 1.0);
    result.rgb = mix(result.rgb, result.rgb * baseColor * 2.0, 0.1);
    result.rgb *= 0.8;
    fragColor = vec4(clamp(result.rgb, 0.0, 1.0), alpha);
}
