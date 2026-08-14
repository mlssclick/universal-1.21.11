#version 150

layout(std140) uniform NebulaTimeData {
    vec4 NebulaData;
    vec4 NebulaTint;
};

out vec4 fragColor;

float plasma(vec2 uv, float t) {
    float v = 0.0;
    v += sin(uv.x * 10.0 + t);
    v += sin((uv.y * 10.0 + t) * 0.5);
    v += sin((uv.x * 10.0 + uv.y * 10.0 + t) * 0.5);

    vec2 c = uv * 10.0 - vec2(5.0);
    v += sin(length(c) + t);

    c = uv * 10.0 - vec2(5.0 + sin(t * 0.5) * 3.0, 5.0 + cos(t * 0.7) * 3.0);
    v += sin(length(c) + t * 1.5);
    return v * 0.2;
}

vec3 hsv2rgb(vec3 c) {
    vec4 K = vec4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);
    vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
    return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
}

void main() {
    float time = NebulaData.x;
    vec2 screenSize = max(NebulaData.yz, vec2(1.0));
    float alpha = NebulaData.w;
    vec3 baseColor = NebulaTint.rgb;

    vec2 uv = gl_FragCoord.xy / screenSize;
    float t = time * 0.8;
    float p = plasma(uv, t);

    float hue = p + time * 0.1;
    vec3 rainbow = hsv2rgb(vec3(hue, 0.8, 1.0));

    float pulse = 0.8 + sin(time * 2.0 + p * 5.0) * 0.2;
    vec3 color = rainbow * pulse;
    color = mix(color, color * baseColor * 2.0, 0.55);
    color += baseColor * pow(abs(sin(p * 3.14159 * 2.0)), 2.0) * 0.25;
    fragColor = vec4(clamp(color, 0.0, 1.0), alpha);
}
