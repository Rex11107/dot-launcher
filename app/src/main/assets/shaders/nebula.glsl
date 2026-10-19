// Nebulosa: nubi di gas colorato che si deformano piano, con polvere scura e stelle.
void mainImage(out vec4 fragColor, vec2 fragCoord) {
    vec2 p = (fragCoord - 0.5 * uRes) / uRes.y * 1.6;
    p.x += uShift * 0.06;
    float t = uTime * 0.015;
    vec2 q = vec2(fbm(p + vec2(0.0, t)), fbm(p + vec2(5.2, 1.3) - t));
    vec2 w = vec2(fbm(p + 3.5 * q + vec2(1.7, 9.2) + t * 0.6), fbm(p + 3.5 * q + vec2(8.3, 2.8)));
    float f = fbm(p + 3.0 * w);
    vec3 col = mix(vec3(0.02, 0.01, 0.05), vec3(0.45, 0.08, 0.45), smoothstep(0.25, 0.75, f));
    col = mix(col, vec3(1.0, 0.45, 0.3), smoothstep(0.45, 0.85, f * length(q) * 1.2) * 0.9);
    col += vec3(1.0, 0.45, 0.55) * pow(smoothstep(0.5, 0.8, f), 2.0) * 0.9;   // nuclei più luminosi
    col += vec3(0.3, 0.7, 1.0) * pow(smoothstep(0.55, 0.85, w.y), 2.0) * 0.35;
    col = mix(col, vec3(0.1, 0.45, 0.75), smoothstep(0.4, 0.9, w.x) * 0.45);
    col *= 0.6 + 2.2 * f * f;
    col *= 1.0 - 0.75 * smoothstep(0.55, 0.85, fbm(p * 2.2 + w * 1.5 + 30.0));   // polvere scura
    col += stars(p + vec2(t * 0.3, 0.0), 70.0) + stars(p * 1.9 + 7.0, 60.0) * 0.5;
    col = 1.0 - exp(-col * 2.2);
    fragColor = vec4(col, 1.0);
}
