#version 150

// The title-screen scene: night sky with a square (Minecraft) moon and square stars, glow and rays,
// an ocean with a glittering moon path, horizon haze, vignette and film grain.
// uMode 0 = the scene itself, 1 = frosted glass behind a button, 2 = the same, hovered.

in vec2 uv;
out vec4 fragColor;

uniform float uTime;
uniform float uMode;
uniform vec2 uRes;

float hash(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), f.x),
               mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), f.x), f.y);
}

float sdBox(vec2 p, vec2 b) {
    vec2 d = abs(p) - b;
    return length(max(d, 0.0)) + min(max(d.x, d.y), 0.0);
}

vec3 scene(vec2 p) {
    float aspect = uRes.x / uRes.y;
    float t = uTime;
    float hz = 0.52;
    vec2 moonC = vec2(0.66, 0.20);
    vec2 q = (p - moonC) * vec2(aspect, 1.0);
    vec3 col;

    if (p.y < hz) {
        float k = p.y / hz;
        vec3 a = vec3(1.0, 3.0, 10.0) / 255.0;
        vec3 b = vec3(7.0, 26.0, 54.0) / 255.0;
        vec3 c = vec3(18.0, 64.0, 106.0) / 255.0;
        col = k < 0.7 ? mix(a, b, k / 0.7) : mix(b, c, (k - 0.7) / 0.3);
    } else {
        float k = (p.y - hz) / (1.0 - hz);
        vec3 a = vec3(14.0, 59.0, 94.0) / 255.0;
        vec3 b = vec3(5.0, 34.0, 55.0) / 255.0;
        vec3 c = vec3(1.0, 6.0, 8.0) / 255.0;
        col = k < 0.35 ? mix(a, b, k / 0.35) : mix(b, c, (k - 0.35) / 0.65);
    }

    float dyh = (p.y - hz) / 0.025;
    col += vec3(60.0, 120.0, 170.0) / 255.0 * 0.35 * exp(-dyh * dyh);

    if (p.y < hz - 0.01) {
        vec2 cell = floor(p * vec2(aspect, 1.0) * 150.0);
        float h = hash(cell);
        if (h > 0.985) {
            float tw = 0.5 + 0.5 * sin(t * (1.0 + h * 40.0) + h * 100.0);
            col += vec3(0.82, 0.91, 1.0) * (0.3 + 0.7 * tw) * smoothstep(0.15, 0.3, length(q));
        }
    }

    float pulse = 0.95 + 0.05 * sin(t * 0.8);
    float d = sdBox(q, vec2(0.06));
    col += vec3(170.0, 215.0, 255.0) / 255.0 * pulse * (0.5 * exp(-max(d, 0.0) / 0.05) + 0.2 * exp(-length(q) / 0.25));
    float ang = atan(q.y, q.x);
    float rays = 0.10 * exp(-length(q) / 0.55) * (0.5 + 0.5 * sin(ang * 11.0 + t * 0.05) * sin(ang * 5.0 + 1.0));
    col += vec3(170.0, 215.0, 255.0) / 255.0 * max(rays, 0.0);

    if (d < 0.0) {
        vec2 cell = floor((q + 0.06) / 0.015);
        float dark = (hash(cell + 3.0) > 0.78 ? 0.28 : 0.0) + (hash(cell * 1.7) > 0.6 ? 0.08 : 0.0);
        vec3 m = mix(vec3(235.0, 246.0, 255.0), vec3(170.0, 200.0, 228.0), clamp(length(q) / 0.085, 0.0, 1.0)) / 255.0;
        col = m * (1.0 - dark);
    }

    if (p.y > hz) {
        float wy = p.y - hz;
        float u = (p.x - moonC.x) * aspect;
        float width = 0.015 + 0.16 * wy;
        float band = exp(-(u * u) / (width * width));
        float row = floor(wy * 110.0);
        float ripple = noise(vec2(u * 55.0 * (0.3 + wy), row - t * 0.8));
        float glit = smoothstep(0.55, 0.85, ripple + noise(vec2(u * 22.0, row * 0.4 + t * 0.5)) * 0.4);
        col += vec3(190.0, 226.0, 255.0) / 255.0 * band * (0.22 + 0.9 * glit) * max(0.0, 1.0 - wy * 1.3);
    }

    vec2 e = vec2(p.x - 0.5, (p.y - 0.45) * 0.9);
    col *= 1.0 - 0.7 * smoothstep(0.45, 1.2, length(e) * 1.6);
    return col;
}

void main() {
    vec2 p = uv;
    vec3 col;
    if (uMode > 0.5) {
        vec3 s = vec3(0.0);
        for (int i = -1; i <= 1; i++) {
            for (int j = -1; j <= 1; j++) {
                s += scene(p + vec2(float(i), float(j)) * 0.012);
            }
        }
        col = s / 9.0;
        col = col * (uMode > 1.5 ? 1.35 : 0.9) + vec3(0.005, 0.02, 0.04);
    } else {
        col = scene(p);
    }
    col += (hash(p * uRes + fract(uTime) * 100.0) - 0.5) * 0.035;
    fragColor = vec4(col, 1.0);
}
