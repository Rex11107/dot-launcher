// Aurora: tende di luce verde e viola che ondeggiano sopra una catena di montagne.
void mainImage(out vec4 fragColor, vec2 fragCoord) {
    vec2 uv = fragCoord / uRes;
    vec2 p = vec2((fragCoord.x - 0.5 * uRes.x) / uRes.y, uv.y);
    p.x += uShift * 0.05;
    vec3 col = mix(vec3(0.015, 0.04, 0.07), vec3(0.0, 0.004, 0.02), smoothstep(0.1, 1.0, uv.y));
    col += stars(p + 2.0, 140.0) * smoothstep(0.25, 0.6, uv.y);
    for (int i = 0; i < 3; i++) {
        float fi = float(i);
        float x = p.x * (1.2 + fi * 0.35) + fi * 3.7;
        // bordo basso della tenda, che ondeggia
        float base = 0.3 + 0.07 * fi + 0.07 * sin(x * 1.6 + uTime * 0.11 + fi * 2.0)
                   + 0.05 * (fbm(vec2(x * 0.7, uTime * 0.04 + fi)) - 0.5);
        float dy = uv.y - base;
        float h = 0.22 + 0.1 * fbm(vec2(x * 1.5, fi + uTime * 0.03));     // altezza della tenda
        float curtain = smoothstep(-0.015, 0.01, dy) * (1.0 - smoothstep(0.0, h, dy));
        float rays = 0.35 + 0.9 * pow(fbm(vec2(x * 9.0 + uTime * 0.06, fi * 3.0)), 1.5);
        vec3 green = vec3(0.12, 1.0, 0.5), violet = vec3(0.6, 0.2, 1.0);
        vec3 a = mix(green, violet, smoothstep(0.02, h, dy));
        float bright = 1.0 + 1.5 * exp(-max(dy, 0.0) * 40.0);             // il bordo basso è il più luminoso
        col += a * curtain * rays * bright * (0.55 - fi * 0.13);
    }
    // chiarore verde all'orizzonte, riflesso dell'aurora
    col += vec3(0.02, 0.09, 0.07) * exp(-max(uv.y - 0.15, 0.0) * 7.0);
    // montagne in controluce
    float far = 0.15 + 0.16 * (fbm(vec2(p.x * 2.2 + 20.0, 2.0)) - 0.3);
    float ridge = 0.06 + 0.18 * (fbm(vec2(p.x * 3.0 + 5.0, 1.0)) - 0.25) + 0.03 * fbm(vec2(p.x * 12.0, 3.0));
    col = mix(col, vec3(0.008, 0.016, 0.026), smoothstep(far + 0.003, far, uv.y));
    col = mix(col, vec3(0.0), smoothstep(ridge + 0.003, ridge, uv.y));
    col = 1.0 - exp(-col * 1.4);
    fragColor = vec4(col, 1.0);
}
