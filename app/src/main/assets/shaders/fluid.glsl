// Gradiente fluido: macchie di colore morbide che si muovono, a partire dal colore d'accento.
vec3 hue(vec3 c, float a) {          // ruota la tinta di un colore (angolo in radianti)
    const vec3 k = vec3(0.57735);
    float ca = cos(a);
    return c * ca + cross(k, c) * sin(a) + k * dot(k, c) * (1.0 - ca);
}

void mainImage(out vec4 fragColor, vec2 fragCoord) {
    vec2 p = (fragCoord - 0.5 * uRes) / uRes.y;
    p.x += uShift * 0.08;
    float t = uTime * 0.07;
    vec3 base = max(uAccent, vec3(0.05));
    vec3 c1 = base, c2 = hue(base, 0.9), c3 = hue(base, -0.8), c4 = base * 0.25;
    vec2 b1 = vec2(0.25 * sin(t * 1.1), 0.3 * cos(t * 0.8) + 0.15);
    vec2 b2 = vec2(0.3 * cos(t * 0.9 + 2.0), 0.35 * sin(t * 0.7 + 1.0) - 0.2);
    vec2 b3 = vec2(0.28 * sin(t * 0.6 + 4.0), 0.4 * sin(t * 1.2 + 3.0));
    float w1 = 1.0 / (0.02 + pow(length(p - b1), 2.0));
    float w2 = 1.0 / (0.02 + pow(length(p - b2), 2.0));
    float w3 = 1.0 / (0.02 + pow(length(p - b3), 2.0));
    float w4 = 6.0;
    vec3 col = (c1 * w1 + c2 * w2 + c3 * w3 + c4 * w4) / (w1 + w2 + w3 + w4);
    col *= (0.45 + 0.4 * smoothstep(0.9, 0.1, length(p)));                  // bordi più scuri e niente colori sparati dietro le icone
    col += (hash(fragCoord + fract(uTime)) - 0.5) / 255.0 * 2.0;            // grana fine: niente bande
    fragColor = vec4(col, 1.0);
}
