#pragma once
#include <cmath>

struct V3 {
    float x, y, z;
};
inline V3 operator+(V3 a, V3 b) { return {a.x + b.x, a.y + b.y, a.z + b.z}; }
inline V3 operator-(V3 a, V3 b) { return {a.x - b.x, a.y - b.y, a.z - b.z}; }
inline V3 operator*(V3 a, float s) { return {a.x * s, a.y * s, a.z * s}; }
inline float dot(V3 a, V3 b) { return a.x * b.x + a.y * b.y + a.z * b.z; }
inline V3 cross(V3 a, V3 b) { return {a.y * b.z - a.z * b.y, a.z * b.x - a.x * b.z, a.x * b.y - a.y * b.x}; }
inline V3 norm(V3 a) {
    float l = std::sqrt(dot(a, a));
    return l > 1e-9f ? a * (1 / l) : V3{0, 1, 0};
}

// column-major, as OpenGL wants it
struct M4 {
    float m[16];
};

inline M4 mul(const M4& a, const M4& b) {
    M4 r;
    for (int c = 0; c < 4; c++)
        for (int rI = 0; rI < 4; rI++) {
            float s = 0;
            for (int k = 0; k < 4; k++) s += a.m[k * 4 + rI] * b.m[c * 4 + k];
            r.m[c * 4 + rI] = s;
        }
    return r;
}

inline M4 perspective(float fovDeg, float aspect, float n, float f) {
    float t = 1.0f / std::tan(fovDeg * 3.14159265f / 360.0f);
    M4 r = {};
    r.m[0] = t / aspect;
    r.m[5] = t;
    r.m[10] = (f + n) / (n - f);
    r.m[11] = -1;
    r.m[14] = 2 * f * n / (n - f);
    return r;
}

// roll: the camera turned about its own axis, in degrees
inline M4 lookAt(V3 eye, V3 target, float rollDeg) {
    V3 f = norm(target - eye);
    V3 up = {0, 1, 0};
    V3 s = norm(cross(f, up));
    V3 u = cross(s, f);
    float a = rollDeg * 3.14159265f / 180.0f, c = std::cos(a), sn = std::sin(a);
    V3 s2 = s * c + u * sn, u2 = u * c - s * sn;
    M4 r = {};
    r.m[0] = s2.x; r.m[4] = s2.y; r.m[8] = s2.z;
    r.m[1] = u2.x; r.m[5] = u2.y; r.m[9] = u2.z;
    r.m[2] = -f.x; r.m[6] = -f.y; r.m[10] = -f.z;
    r.m[12] = -dot(s2, eye);
    r.m[13] = -dot(u2, eye);
    r.m[14] = dot(f, eye);
    r.m[15] = 1;
    return r;
}
