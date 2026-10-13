#version 150

// The title screen's figure: a dark body with two raised hands, every finger on its own, lit from behind by a soft moon-white light with
// a little haze in the air, and the threads of the menu hanging from the fingertips. Everything is built from smooth shapes (capsules and
// rounded boxes) and ray-marched, so the hands can be posed finger by finger from the game (uCurl) and nothing is a block.
// uv is the screen, 0..1, y down. In the game this file is the fragment shader of a full-screen quad; the same file renders still frames
// for review without starting Minecraft (tools/ShaderStill.java).

in vec2 uv;
out vec4 fragColor;

uniform float uTime;
uniform vec2 uRes;
uniform float uCurl[10];      // how far each finger is bent, 0..1: the figure's right hand (screen left) thumb..little, then the left hand
uniform float uSpread;        // how far the fingers are fanned, 0..1
uniform vec2 uLift;       // how high each hand is raised, metres (0 = at rest)
uniform float uThreadEnd[24]; // where each thread ends, world x,y,z: per hand (screen left first) thumb, index, middle, ring
uniform float uThreadOn[8];   // 0 = the thread is cut

const float PI = 3.14159265;

float sdCapsule(vec3 p, vec3 a, vec3 b, float r1, float r2) {
    vec3 pa = p - a, ba = b - a;
    float h = clamp(dot(pa, ba) / dot(ba, ba), 0.0, 1.0);
    return length(pa - ba * h) - mix(r1, r2, h);
}

float sdEllipsoid(vec3 p, vec3 r) {
    float k0 = length(p / r);
    float k1 = length(p / (r * r));
    return k0 * (k0 - 1.0) / k1;
}

float sdBox(vec3 p, vec3 b) {
    vec3 q = abs(p) - b;
    return length(max(q, 0.0)) + min(max(q.x, max(q.y, q.z)), 0.0);
}

// a box between two points (a limb or a finger bone): half width hw across, hd deep
float sdBoxBetween(vec3 p, vec3 a, vec3 b, float hw, float hd) {
    vec3 ay = normalize(b - a);
    vec3 ax = normalize(cross(vec3(0.0, 0.0, 1.0), ay));
    vec3 az = cross(ax, ay);
    vec3 q = p - (a + b) * 0.5;
    vec3 l = vec3(dot(q, ax), dot(q, ay), dot(q, az));
    return sdBox(l, vec3(hw, length(b - a) * 0.5 + 0.004, hd));
}

float sdRoundBox(vec3 p, vec3 b, float r) {
    vec3 q = abs(p) - b;
    return length(max(q, 0.0)) + min(max(q.x, max(q.y, q.z)), 0.0) - r;
}

float smin(float a, float b, float k) {
    float h = clamp(0.5 + 0.5 * (b - a) / k, 0.0, 1.0);
    return mix(b, a, h) - k * h * (1.0 - h);
}

// ------------------------------------------------------------------ the figure

const float FINGER_SCALE = 1.35;                  // a little long: the hands are what you look at
const float HS = 1.85;                             // and big: the hands are drawn larger than life so every finger can be seen

// a finger of three bones from a base point, along the hand's down axis, fanned sideways by `fan`, bent backwards by `curl`
float finger(vec3 p, vec3 base, vec3 X, vec3 Y, vec3 Z, float side, float fan, float curl, vec3 len, float r) {
    vec3 a = base;
    float d = 1e5;
    float ang = 0.0;
    for (int k = 0; k < 2; k++) {
        ang += curl * (k == 0 ? 0.80 : 1.15) * 1.5;
        float sp = fan * (k == 0 ? 1.0 : 0.55);
        vec3 dir = normalize(X * sin(sp) * side + Y * cos(sp) * cos(ang) + Z * cos(sp) * sin(ang));
        vec3 b = a + dir * len[k] * FINGER_SCALE * HS;
        float rr0 = r * (1.0 - 0.1 * float(k)), rr1 = r * (1.0 - 0.18 * float(k + 1));
        d = min(d, sdBoxBetween(p, a, b, rr0 * 1.05, rr0 * 0.95));
        a = b;
    }
    return d;
}

vec3 tipOf(vec3 base, vec3 X, vec3 Y, vec3 Z, float side, float fan, float curl, vec3 len) {
    vec3 a = base;
    float ang = 0.0;
    for (int k = 0; k < 2; k++) {
        ang += curl * (k == 0 ? 0.80 : 1.15) * 1.5;
        float sp = fan * (k == 0 ? 1.0 : 0.55);
        a += normalize(X * sin(sp) * side + Y * cos(sp) * cos(ang) + Z * cos(sp) * sin(ang)) * len[k] * FINGER_SCALE * HS;
    }
    return a;
}

struct Hand { vec3 O, X, Y, Z; float inner; };

Hand handFrame(float s, int which) {
    // wrist and elbow: the elbow is raised and out, the forearm comes down and a little forward
    float lift = uLift[which];
    vec3 shoulder = vec3(s * 0.46, 1.74, 4.05);
    vec3 elbow = vec3(s * 1.16, 2.02 + lift * 0.5, 3.98);
    vec3 wrist = vec3(s * 1.04, 1.42 + lift, 3.56);
    Hand h;
    h.Y = normalize(wrist - elbow);
    vec3 back = vec3(0.0, 0.0, -1.0);
    h.X = normalize(cross(back, h.Y));
    if (h.X.x < 0.0) h.X = -h.X;
    h.Z = normalize(cross(h.X, h.Y));
    if (h.Z.z < 0.0) h.Z = -h.Z;   // the fingers bend away from the viewer: the back of the hand faces the screen
    h.O = wrist;
    h.inner = -s;
    return h;
}

// bone lengths of the two-bone fingers of Rig15 / PlayerFingers (pixels 0.95/0.85, 1.05/0.95, 1.15/1.0, 1.05/0.9, 0.85/0.75), scaled
const vec3 LEN_INDEX = vec3(0.0504, 0.0456, 0.0);
const vec3 LEN_MIDDLE = vec3(0.0552, 0.0480, 0.0);
const vec3 LEN_RING = vec3(0.0504, 0.0432, 0.0);
const vec3 LEN_PINKY = vec3(0.0408, 0.0360, 0.0);
const vec3 LEN_THUMB = vec3(0.0456, 0.0408, 0.0);

void fingerBases(Hand h, out vec3 bs[5], out float fans[5], out vec3 lens[5], out float rad[5]) {
    float ix = h.inner;
    vec3 palm = h.O + h.Y * 0.095 * HS;
    bs[1] = palm + h.X * ix * 0.058 * HS;
    bs[2] = palm + h.X * ix * 0.020 * HS + h.Y * 0.004 * HS;
    bs[3] = palm - h.X * ix * 0.019 * HS;
    bs[4] = palm - h.X * ix * 0.054 * HS - h.Y * 0.012 * HS;
    bs[0] = h.O + h.Y * 0.030 * HS + h.X * ix * 0.058 * HS - h.Z * 0.005 * HS;
    float sp = 0.4 + 0.9 * uSpread;
    fans[1] = 0.10 * sp; fans[2] = -0.02 * sp; fans[3] = -0.16 * sp; fans[4] = -0.34 * sp;
    fans[0] = 0.50 + 0.30 * uSpread;
    lens[0] = LEN_THUMB; lens[1] = LEN_INDEX; lens[2] = LEN_MIDDLE; lens[3] = LEN_RING; lens[4] = LEN_PINKY;
    rad[0] = 0.0125 * HS; rad[1] = 0.0105 * HS; rad[2] = 0.0110 * HS; rad[3] = 0.0100 * HS; rad[4] = 0.0085 * HS;
}

float hand(vec3 p, float s, int which, int base) {
    Hand h = handFrame(s, which);
    // away from the hand the exact shape doesn't matter: a bounding sphere keeps the march cheap
    float bound = length(p - (h.O + h.Y * 0.2)) - 0.62;
    if (bound > 0.08) return bound;
    float ix = h.inner;
    vec3 pl = p - (h.O + h.Y * 0.058 * HS);
    float palm = sdBox(vec3(dot(pl, h.X), dot(pl, h.Y), dot(pl, h.Z)), vec3(0.066, 0.062, 0.022) * HS);
    vec3 bs[5]; float fans[5]; vec3 lens[5]; float rad[5];
    fingerBases(h, bs, fans, lens, rad);
    float fingers = 1e5;
    for (int f = 0; f < 5; f++) {
        float curl = uCurl[base + f];
        float side = f == 0 ? ix : ix;
        fingers = min(fingers, finger(p, bs[f], h.X, h.Y, h.Z, side, f == 0 ? fans[f] : fans[f], curl * (f == 0 ? 0.55 : 1.0) + 0.08, lens[f], rad[f]));
    }
    return min(palm, fingers);
}

// the thread hung from a fingertip: a gently sagging line down to where a button would hang
float thread(vec3 p, vec3 a, vec3 b) {
    vec3 m = mix(a, b, 0.5) + vec3(0.0, -0.05, 0.0);
    return min(sdCapsule(p, a, m, 0.0030, 0.0030), sdCapsule(p, m, b, 0.0030, 0.0030));
}

float body(vec3 p) {
    // the head sits right on the torso, a little wider than it, the shoulders wider still: no gap, nothing hangs
    float head = sdBox(p - vec3(0.0, 2.17, 4.05), vec3(0.34, 0.31, 0.31));
    float torso = sdBox(p - vec3(0.0, 0.82, 4.05), vec3(0.30, 1.04, 0.16));
    float sh = min(sdBox(p - vec3(-0.41, 1.74, 4.05), vec3(0.13, 0.13, 0.14)), sdBox(p - vec3(0.41, 1.74, 4.05), vec3(0.13, 0.13, 0.14)));
    return min(head, min(torso, sh));
}

float arms(vec3 p) {
    float d = 1e5;
    for (int i = 0; i < 2; i++) {
        float s = i == 0 ? -1.0 : 1.0;
        Hand h = handFrame(s, i);
        vec3 shoulder = vec3(s * 0.46, 1.74, 4.05);
        vec3 elbow = vec3(s * 1.16, 2.02 + uLift[i] * 0.5, 3.98);
        vec3 mid = mix(elbow, h.O, 0.55);
        d = min(d, sdBoxBetween(p, shoulder, elbow, 0.115, 0.125));
        d = min(d, sdBoxBetween(p, elbow, mid, 0.098, 0.108));
        d = min(d, sdBoxBetween(p, mid, h.O + (h.O - mid) * 0.12, 0.076, 0.088));
    }
    return d;
}

float handsAndThreads(vec3 p, out float matId) {
    float d = 1e5;
    matId = 0.0;
    for (int i = 0; i < 2; i++) {
        float s = i == 0 ? -1.0 : 1.0;
        d = min(d, hand(p, s, i, i * 5));
    }
    return d;
}

float threads(vec3 p) {
    float d = 1e5;
    for (int i = 0; i < 2; i++) {
        float s = i == 0 ? -1.0 : 1.0;
        Hand h = handFrame(s, i);
        vec3 bs[5]; float fans[5]; vec3 lens[5]; float rad[5];
        fingerBases(h, bs, fans, lens, rad);
        for (int f = 0; f < 4; f++) {
            int idx = i * 4 + f;
            if (uThreadOn[idx] < 0.5) continue;
            vec3 tip = tipOf(bs[f], h.X, h.Y, h.Z, h.inner, fans[f], uCurl[i * 5 + f] * (f == 0 ? 0.55 : 1.0) + 0.08, lens[f]);
            vec3 end = vec3(uThreadEnd[idx * 3], uThreadEnd[idx * 3 + 1], uThreadEnd[idx * 3 + 2]);
            d = min(d, thread(p, tip, end));
        }
    }
    return d;
}

float shapeDist(vec3 p) {
    return min(body(p), arms(p));
}

float mapAll(vec3 p, out float kind) {
    float b = body(p);
    float a = arms(p);
    float m;
    float hh = handsAndThreads(p, m);
    float th = threads(p);
    float figure = smin(min(b, a), hh, 0.0);
    figure = min(min(b, a), hh);
    kind = th < figure ? 1.0 : 0.0;
    return min(figure, th);
}

float map(vec3 p) {
    float k;
    return mapAll(p, k);
}

vec3 normalAt(vec3 p) {
    const vec2 e = vec2(0.0012, -0.0012);
    return normalize(e.xyy * map(p + e.xyy) + e.yyx * map(p + e.yyx) + e.yxy * map(p + e.yxy) + e.xxx * map(p + e.xxx));
}

// ------------------------------------------------------------------ light

const vec3 LIGHT_POS = vec3(0.0, 2.45, 7.2);       // behind the head
const vec3 MOON = vec3(0.62, 0.78, 1.0);

float hash(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

float shadow(vec3 ro, vec3 rd, float tmax) {
    float res = 1.0;
    float t = 0.05;
    for (int i = 0; i < 5; i++) {
        float h = shapeDist(ro + rd * t);
        res = min(res, 8.0 * h / t);
        t += clamp(h, 0.06, 0.5);
        if (res < 0.01 || t > tmax) break;
    }
    return clamp(res, 0.0, 1.0);
}

void main() {
    float aspect = uRes.x / uRes.y;
    vec2 q = vec2((uv.x - 0.5) * aspect, 0.5 - uv.y) * 2.0;
    float focal = 2.4;
    vec3 ro = vec3(0.0, 1.45, 0.8);
    vec3 rd = normalize(vec3(q.x, q.y, focal));

    // the soft light behind the figure
    vec3 bgPos = ro + rd * 6.0;
    float toLight = length(vec2(q.x * 0.9, (q.y + 0.55) * 1.0));
    vec3 col = MOON * (0.30 * exp(-toLight * toLight * 0.55) + 0.34 * exp(-toLight * toLight * 3.2));
    col *= 0.9;

    // march
    float t = 0.3;
    float kind = 0.0;
    float dmin = 1e5;
    bool hit = false;
    for (int i = 0; i < 72; i++) {
        vec3 p = ro + rd * t;
        float d = mapAll(p, kind);
        dmin = min(dmin, shapeDist(p));
        if (d < 0.0006) { hit = true; break; }
        t += d * 0.9;
        if (t > 9.0) break;
    }

    // light shafts: haze lit by the light behind, blocked by the figure
    float scatter = 0.0;
    float tEnd = hit ? t : 9.0;
    float jitter = hash(gl_FragCoord.xy + fract(uTime) * 17.0);
    for (int i = 0; i < 10; i++) {
        float ts = (float(i) + jitter) / 10.0 * tEnd;
        vec3 sp = ro + rd * ts;
        vec3 toL = LIGHT_POS - sp;
        float dl = length(toL);
        float vis = shadow(sp, toL / dl, min(dl, 3.0));
        float falloff = 1.0 / (1.0 + 0.10 * dl * dl);
        scatter += vis * falloff * (tEnd / 10.0);
    }
    float fwd = pow(max(dot(rd, normalize(LIGHT_POS - ro)), 0.0), 3.0);
    col += MOON * scatter * 0.035 * (0.35 + fwd);

    // light wrapping round the edges of the figure: the closer the ray passed to it, the more of the light behind reaches the eye
    col += MOON * 0.40 * exp(-max(dmin, 0.0) * 9.0) * (hit ? 0.0 : 1.0);
    if (hit) {
        vec3 p = ro + rd * t;
        vec3 n = normalAt(p);
        vec3 v = -rd;
        vec3 l = normalize(LIGHT_POS - p);
        float ndl = max(dot(n, l), 0.0);
        float ndv = max(dot(n, v), 0.0);
        float sh = shadow(p + n * 0.01, l, 6.0);
        float fres = pow(1.0 - ndv, 2.0);
        vec3 h = normalize(l + v);
        float spec = pow(max(dot(n, h), 0.0), 60.0);
        vec3 base = kind > 0.5 ? vec3(0.55, 0.60, 0.68) : vec3(0.010, 0.012, 0.016);
        vec3 lit = base * (0.04 + ndl * sh * 0.8);
        float rim = fres * (0.25 + 0.75 * ndl) * sh;
        lit += MOON * rim * (kind > 0.5 ? 1.1 : 1.15);
        if (kind > 0.5) lit += vec3(0.46, 0.55, 0.66) * 0.55;
        lit += MOON * spec * sh * 0.25;
        // the body is hidden in the dark from the front; only edges and the glint of the knuckles show
        col = mix(col * 0.0, lit, 1.0) + col * 0.0;
        col += MOON * scatter * 0.035 * (0.35 + fwd) * 0.35;
    }

    // vignette, grade, grain
    vec2 e = vec2(uv.x - 0.5, (uv.y - 0.45) * 0.9);
    col *= 1.0 - 0.6 * smoothstep(0.35, 1.05, length(e) * 1.5);
    col = col / (1.0 + col);
    col = pow(col, vec3(0.92));
    col += (hash(uv * uRes + fract(uTime)) - 0.5) * 0.02;
    fragColor = vec4(col, 1.0);
}
