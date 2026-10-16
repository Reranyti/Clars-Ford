#include "scene.h"

#define STB_IMAGE_IMPLEMENTATION
#include "stb_image.h"

#include <algorithm>
#include <cstdio>
#include <fstream>
#include <sstream>

#define LOTOS_DEFINE(type, name) type name = nullptr;
LOTOS_GL_FUNCS(LOTOS_DEFINE)
#undef LOTOS_DEFINE

bool loadGL() {
#define LOTOS_LOAD(type, name) \
    name = (type) glfwGetProcAddress(#name); \
    if (!name) return false;
    LOTOS_GL_FUNCS(LOTOS_LOAD)
#undef LOTOS_LOAD
    return true;
}

void Target::ensure(int width, int height) {
    width = std::max(16, width);
    height = std::max(16, height);
    if (fbo && w == width && h == height) return;
    w = width;
    h = height;
    if (!fbo) {
        glGenFramebuffers(1, &fbo);
        glGenTextures(1, &tex);
        glGenRenderbuffers(1, &depth);
    }
    glBindTexture(GL_TEXTURE_2D, tex);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, w, h, 0, GL_RGBA, GL_UNSIGNED_BYTE, nullptr);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glBindRenderbuffer(GL_RENDERBUFFER, depth);
    glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH_COMPONENT24, w, h);
    glBindFramebuffer(GL_FRAMEBUFFER, fbo);
    glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, tex, 0);
    glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_RENDERBUFFER, depth);
    glBindFramebuffer(GL_FRAMEBUFFER, 0);
}

namespace {

const char* kFigureVS = R"(#version 330 core
layout(location=0) in vec3 aPos;
layout(location=1) in vec3 aNormal;
layout(location=2) in vec2 aUV;
uniform mat4 uVP;
out vec3 vWorld;
out vec3 vNormal;
out vec2 vUV;
void main() {
    vWorld = aPos;
    vNormal = aNormal;
    vUV = aUV;
    gl_Position = uVP * vec4(aPos, 1.0);
}
)";

// the cold light of the second song's close-up: a key from the upper left, a blue edge against the dark, a little fog, optional posterize
const char* kFigureFS = R"(#version 330 core
in vec3 vWorld;
in vec3 vNormal;
in vec2 vUV;
uniform sampler2D uSkin;
uniform vec3 uCam;
uniform float uPosterize;
uniform float uRim;
uniform float uFog;
out vec4 outColor;
void main() {
    vec4 t = texture(uSkin, vUV);
    if (t.a < 0.1) discard;
    vec3 n = normalize(vNormal);
    vec3 v = normalize(uCam - vWorld);
    vec3 keyDir = normalize(vec3(-0.45, 0.8, 0.65));
    float diff = max(dot(n, keyDir), 0.0);
    vec3 amb = vec3(0.55, 0.52, 0.72);
    vec3 key = vec3(0.85, 0.82, 1.05);
    vec3 col = t.rgb * (amb + key * diff);
    float rim = pow(1.0 - max(dot(n, v), 0.0), 3.0);
    col += uRim * rim * vec3(0.42, 0.62, 1.0);
    if (uPosterize > 0.5) col = floor(col * uPosterize + 0.5) / uPosterize;
    float fog = 1.0 - exp(-uFog * length(uCam - vWorld));
    col = mix(col, vec3(0.04, 0.04, 0.09), fog);
    outColor = vec4(col, 1.0);
}
)";

const char* kGridVS = R"(#version 330 core
layout(location=0) in vec3 aPos;
uniform mat4 uVP;
out vec3 vWorld;
void main() {
    vWorld = aPos;
    gl_Position = uVP * vec4(aPos, 1.0);
}
)";

// the floor: lines every 8 units, thicker every 40, fading with distance
const char* kGridFS = R"(#version 330 core
in vec3 vWorld;
uniform vec3 uCam;
out vec4 outColor;
float lineOf(vec2 p, float size) {
    vec2 g = abs(fract(p / size - 0.5) - 0.5) / fwidth(p / size);
    return 1.0 - min(min(g.x, g.y), 1.0);
}
void main() {
    float a = lineOf(vWorld.xz, 8.0) * 0.35 + lineOf(vWorld.xz, 40.0) * 0.5;
    float d = length(vWorld.xz - uCam.xz);
    a *= clamp(1.0 - d / 420.0, 0.0, 1.0);
    outColor = vec4(0.42, 0.5, 0.95, a);
}
)";

GLuint compile(GLenum type, const char* src, std::string& error) {
    GLuint s = glCreateShader(type);
    glShaderSource(s, 1, &src, nullptr);
    glCompileShader(s);
    GLint ok = 0;
    glGetShaderiv(s, GL_COMPILE_STATUS, &ok);
    if (!ok) {
        char log[1024];
        glGetShaderInfoLog(s, sizeof log, nullptr, log);
        error += std::string("shader: ") + log;
    }
    return s;
}

GLuint program(const char* vs, const char* fs, std::string& error) {
    GLuint v = compile(GL_VERTEX_SHADER, vs, error), f = compile(GL_FRAGMENT_SHADER, fs, error);
    GLuint p = glCreateProgram();
    glAttachShader(p, v);
    glAttachShader(p, f);
    glLinkProgram(p);
    GLint ok = 0;
    glGetProgramiv(p, GL_LINK_STATUS, &ok);
    if (!ok) {
        char log[1024];
        glGetProgramInfoLog(p, sizeof log, nullptr, log);
        error += std::string("link: ") + log;
    }
    glDeleteShader(v);
    glDeleteShader(f);
    return p;
}

void setMat(GLuint prog, const char* name, const M4& m) { glUniformMatrix4fv(glGetUniformLocation(prog, name), 1, GL_FALSE, m.m); }

}  // namespace

bool Scene::init(const std::string& dataDir, std::string& error) {
    progFigure = program(kFigureVS, kFigureFS, error);
    progGrid = program(kGridVS, kGridFS, error);
    if (!error.empty()) return false;

    // the body, exactly as the game builds it (written by exporter/ExportFigure.java): four corners a face, "x y z u v"
    std::ifstream f(dataDir + "/figure.txt");
    if (!f) {
        error = "нет файла figure.txt (запусти exporter/ExportFigure)";
        return false;
    }
    std::vector<float> data;
    std::vector<float> c(5 * 4);
    int idx = 0;
    std::string line;
    while (std::getline(f, line)) {
        std::stringstream ss(line);
        for (int k = 0; k < 5; k++) ss >> c[idx * 5 + k];
        if (++idx == 4) {
            idx = 0;
            V3 p[4];
            for (int i = 0; i < 4; i++) p[i] = {c[i * 5], c[i * 5 + 1], c[i * 5 + 2]};
            V3 n = norm(cross(p[3] - p[0], p[1] - p[0]));
            for (int tri : {0, 1, 2, 0, 2, 3}) {
                float v[8] = {p[tri].x, p[tri].y, p[tri].z, n.x, n.y, n.z, c[tri * 5 + 3], c[tri * 5 + 4]};
                data.insert(data.end(), v, v + 8);
            }
            faceCount++;
        }
    }
    vertCount = (int) data.size() / 8;
    glGenVertexArrays(1, &vaoFigure);
    glBindVertexArray(vaoFigure);
    glGenBuffers(1, &vboFigure);
    glBindBuffer(GL_ARRAY_BUFFER, vboFigure);
    glBufferData(GL_ARRAY_BUFFER, data.size() * sizeof(float), data.data(), GL_STATIC_DRAW);
    glEnableVertexAttribArray(0);
    glVertexAttribPointer(0, 3, GL_FLOAT, GL_FALSE, 8 * sizeof(float), (void*) 0);
    glEnableVertexAttribArray(1);
    glVertexAttribPointer(1, 3, GL_FLOAT, GL_FALSE, 8 * sizeof(float), (void*) (3 * sizeof(float)));
    glEnableVertexAttribArray(2);
    glVertexAttribPointer(2, 2, GL_FLOAT, GL_FALSE, 8 * sizeof(float), (void*) (6 * sizeof(float)));

    // the skin
    int w, h, ch;
    unsigned char* px = stbi_load((dataDir + "/glitcher.png").c_str(), &w, &h, &ch, 4);
    if (!px) {
        error = "нет текстуры glitcher.png";
        return false;
    }
    glGenTextures(1, &skin);
    glBindTexture(GL_TEXTURE_2D, skin);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, w, h, 0, GL_RGBA, GL_UNSIGNED_BYTE, px);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
    stbi_image_free(px);

    // the floor
    float s = 600;
    float quad[] = {-s, 0, -s, s, 0, -s, s, 0, s, -s, 0, -s, s, 0, s, -s, 0, s};
    glGenVertexArrays(1, &vaoGrid);
    glBindVertexArray(vaoGrid);
    glGenBuffers(1, &vboGrid);
    glBindBuffer(GL_ARRAY_BUFFER, vboGrid);
    glBufferData(GL_ARRAY_BUFFER, sizeof quad, quad, GL_STATIC_DRAW);
    glEnableVertexAttribArray(0);
    glVertexAttribPointer(0, 3, GL_FLOAT, GL_FALSE, 3 * sizeof(float), (void*) 0);
    glBindVertexArray(0);
    return true;
}

void Scene::render(Target& t, const Camera& cam, const Look& look) {
    glBindFramebuffer(GL_FRAMEBUFFER, t.fbo);
    glViewport(0, 0, t.w, t.h);
    glClearColor(0.04f, 0.04f, 0.09f, 1);
    glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
    glEnable(GL_DEPTH_TEST);
    M4 vp = mul(perspective(cam.fov, (float) t.w / t.h, 0.5f, 900.0f), lookAt(cam.pos, cam.target, cam.roll));

    if (look.grid) {
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glUseProgram(progGrid);
        setMat(progGrid, "uVP", vp);
        glUniform3f(glGetUniformLocation(progGrid, "uCam"), cam.pos.x, cam.pos.y, cam.pos.z);
        glBindVertexArray(vaoGrid);
        glDrawArrays(GL_TRIANGLES, 0, 6);
        glDisable(GL_BLEND);
    }
    glUseProgram(progFigure);
    setMat(progFigure, "uVP", vp);
    glUniform3f(glGetUniformLocation(progFigure, "uCam"), cam.pos.x, cam.pos.y, cam.pos.z);
    glUniform1f(glGetUniformLocation(progFigure, "uPosterize"), look.posterize);
    glUniform1f(glGetUniformLocation(progFigure, "uRim"), look.rim);
    glUniform1f(glGetUniformLocation(progFigure, "uFog"), look.fog);
    glUniform1i(glGetUniformLocation(progFigure, "uSkin"), 0);
    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D, skin);
    glBindVertexArray(vaoFigure);
    glDrawArrays(GL_TRIANGLES, 0, vertCount);
    glBindVertexArray(0);
    glUseProgram(0);
    glDisable(GL_DEPTH_TEST);
    glBindFramebuffer(GL_FRAMEBUFFER, 0);
}
