// Buco nero realistico: disco di accrescimento quasi di taglio, immagine del lato lontano piegata
// sopra e sotto l'ombra, anello di fotoni, effetto Doppler e stelle deviate dalla gravità.
vec3 diskColor(float rd, float ang, float rin, float rout) {
    float rot = ang - uTime * 0.35 / pow(rd / rin, 1.5);            // ruota più veloce vicino al centro
    float n = fbm(vec2(rot * 2.5, log(rd) * 7.0));
    float n2 = fbm(vec2(rot * 6.0 + 3.0, log(rd) * 18.0));
    float t = pow(rin / rd, 0.9);                                     // più caldo vicino al centro
    vec3 c = mix(vec3(0.9, 0.22, 0.04), vec3(1.0, 0.62, 0.25), t);
    c = mix(c, vec3(1.0, 0.95, 0.85), t * t * t);
    float edge = smoothstep(rin, rin * 1.25, rd) * (1.0 - smoothstep(rout * 0.55, rout, rd));
    return c * edge * (0.45 + 0.9 * n * n2 * 1.6) * (0.6 + 1.6 * t);
}

void mainImage(out vec4 fragColor, vec2 fragCoord) {
    vec2 p = (fragCoord - 0.5 * uRes) / uRes.y;
    p.x += uShift * 0.04;
    p.y -= 0.06;
    float r = length(p);
    float sh = 0.115;                       // raggio dell'ombra
    float rin = sh * 1.45, rout = sh * 5.2; // disco
    float incl = 0.18;                      // quanto è schiacciato (visto quasi di taglio)

    // sfondo: stelle e un velo di galassia, spostati dalla gravità attorno all'ombra
    vec2 q = p * (1.0 + (sh * sh * 2.2) / max(r * r, 0.0004));
    vec3 col = stars(q + vec2(uTime * 0.002, 0.0), 120.0) + stars(q * 1.7 + 4.0, 90.0) * 0.6;
    col += vec3(0.05, 0.04, 0.07) * fbm3(q * 2.5 + 10.0) * smoothstep(0.0, 0.6, abs(q.y + q.x * 0.4));

    // immagine secondaria: il lato lontano del disco, piegato in un anello attorno all'ombra
    if (r > sh && r < sh * 2.2) {           // oltre, l'anello è già spento: si salta
        float a = atan(p.y, p.x);
        float k = (r - sh) / (sh * 0.6);
        float rd2 = rin + k * (rout - rin) * 0.35;
        float top = p.y > 0.0 ? 1.0 : 0.55;
        vec3 halo = diskColor(rd2, -a, rin, rout) * exp(-k * 4.5) * top * 1.3;
        col += halo * (0.9 + 0.5 * cos(a + 3.14159));
    }

    // disco principale (ellisse schiacciata); la metà davanti passa sopra l'ombra
    vec2 d = vec2(p.x, p.y / incl);
    float rd = length(d);
    float ang = atan(d.y, d.x);
    bool front = p.y < 0.0;
    float hidden = (!front && r < sh * 1.02) ? 0.0 : 1.0;   // la metà dietro è coperta dall'ombra
    if (rd > rin && rd < rout) {
        float doppler = 1.0 + 0.75 * cos(ang);               // il lato che viene verso di noi è più acceso
        vec3 dc = diskColor(rd, ang, rin, rout) * doppler * hidden;
        dc *= 0.75 + 0.25 * smoothstep(0.0, 0.01, abs(p.y));   // il piano del disco è appena più scuro
        col += dc;
    }

    // ombra: nera, tranne dove il disco ci passa davanti
    if (r < sh && !(front && rd > rin && rd < rout)) col = vec3(0.0);

    // anello di fotoni e bagliore
    col += vec3(1.0, 0.8, 0.55) * exp(-pow((r - sh * 1.02) / 0.0035, 2.0)) * 1.4;
    col += vec3(1.0, 0.45, 0.12) * exp(-(r - sh) * 9.0) * 0.12 * step(sh, r);

    col = 1.0 - exp(-col * 1.35);                            // esposizione morbida
    fragColor = vec4(col, 1.0);
}
