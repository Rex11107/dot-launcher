// Parte comune degli sfondi realistici: rumore e stelle. Ogni scena definisce mainImage(out vec4, vec2).
uniform vec2 uRes;      // dimensione della superficie in pixel
uniform float uTime;    // secondi (riparte da zero ogni ora: niente perdita di precisione)
uniform float uShift;   // parallasse tra le pagine della home, da -1 a 1
uniform vec3 uAccent;   // colore d'accento del launcher

float hash(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

float noise(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), u.x),
               mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), u.x), u.y);
}

float fbm(vec2 p) {
    float v = 0.0, a = 0.5;
    for (int i = 0; i < 5; i++) {
        v += a * noise(p);
        p = p * 2.03 + vec2(1.7, 9.2);
        a *= 0.5;
    }
    return v;
}

// versione più leggera (3 ottave) per le deformazioni morbide, dove i dettagli fini non si vedono
float fbm3(vec2 p) {
    float v = 0.5 * noise(p);
    p = p * 2.03 + vec2(1.7, 9.2);
    v += 0.25 * noise(p);
    p = p * 2.03 + vec2(1.7, 9.2);
    return v + 0.125 * noise(p) + 0.0625;
}

// stelle: una per cella, con luminosità e scintillio diversi
vec3 stars(vec2 p, float density) {
    vec2 g = p * density;
    vec2 id = floor(g), f = fract(g) - 0.5;
    float h = hash(id);
    if (h < 0.86) return vec3(0.0);
    vec2 off = vec2(hash(id + 3.1), hash(id + 7.7)) - 0.5;
    float d = length(f - off * 0.7);
    float tw = 0.65 + 0.35 * sin(uTime * (1.0 + h * 2.0) + h * 40.0);
    float s = smoothstep(0.08, 0.0, d) * (h - 0.86) * 7.0 * tw;
    vec3 tint = mix(vec3(0.75, 0.82, 1.0), vec3(1.0, 0.9, 0.75), hash(id + 1.3));
    return tint * s;
}
