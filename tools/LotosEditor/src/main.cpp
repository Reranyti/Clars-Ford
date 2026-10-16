// Lotos Editor: the engine's editor for every cutscene of the game. A project is a list of cuts (scenes); a cut has its music, its shots
// (a camera each) and its events on the tracks. Two modes: the STORYBOARD (the shots as cards) and the EVENTS AND ANIMATION (the timeline).
// The viewport is drawn with OpenGL shaders: the game's own figure, the floor, the light of the close-up.
#define MINIAUDIO_IMPLEMENTATION
#include "miniaudio.h"

#include "gl.h"
#include "imgui.h"
#include "imgui_impl_glfw.h"
#include "imgui_impl_opengl3.h"
#include "scene.h"

#define STB_IMAGE_WRITE_IMPLEMENTATION
#include "stb_image_write.h"
#include <cstring>
#include <windows.h>

#include <algorithm>
#include <cmath>
#include <cstdio>
#include <fstream>
#include <sstream>
#include <string>
#include <vector>

namespace {

const std::string kRoot = "D:/The LOTOS/tools/LotosEditor";
const std::string kProject = kRoot + "/project.txt";

// ---- the project
struct Ev {
    int track;
    double t0, t1;
    std::string label;
    float value = 1;
};
struct Shot {
    std::string name;
    double t0, t1;
    Camera cam;
    std::string note;
    bool move = false;      // glides to the camera of the next shot over its own length
};
struct Cut {
    std::string name = "Новая сцена";
    std::string song = "D:/The LOTOS/build/song-test/song2.wav";
    std::vector<Shot> shots;
    std::vector<Ev> events;
};

const char* kTracks[] = {"Камера", "Текст", "Эффекты", "Персонаж", "Змеи"};
const int kTrackCount = 5;
const ImU32 kTrackCol[] = {IM_COL32(90, 150, 255, 255), IM_COL32(240, 240, 240, 255), IM_COL32(255, 120, 150, 255),
                           IM_COL32(170, 120, 255, 255), IM_COL32(110, 220, 170, 255)};
const char* kPresets[kTrackCount][4] = {{"Тряска", "Наезд", "Наклон", "Рывок"},
                                         {"Субтитр", "Чёрный экран", "Красный текст", "Колонки"},
                                         {"Вспышка", "Глитч", "Свечение", "Трещины"},
                                         {"Удар", "Крик", "Усталость", "Ладонь на стекле"},
                                         {"Змея", "Телевизор", "Серия", "Ошибка"}};

std::vector<Cut> gCuts;
int gCut = 0;
int gSelShot = 0, gSelEv = -1;
std::string gStatus = "Готов";
int gMode = 0;                              // 0 storyboard, 1 events and animation

Cut& cut() { return gCuts[gCut]; }

std::string esc(std::string s) {
    for (char& c : s)
        if (c == '\t') c = ' ';
    std::string o;
    for (char c : s) {
        if (c == '\n') o += "\\n";
        else if (c != '\r') o += c;
    }
    return o;
}
std::string unesc(const std::string& s) {
    std::string o;
    for (size_t i = 0; i < s.size(); i++) {
        if (s[i] == '\\' && i + 1 < s.size() && s[i + 1] == 'n') {
            o += '\n';
            i++;
        } else {
            o += s[i];
        }
    }
    return o;
}

void defaultShots(Cut& c) {
    c.shots.push_back({"Общий", 0, 19, {{0, 18, 62}, {0, 16, 0}, 40, 0}, "Фигура целиком", false});
    c.shots.push_back({"Лицо", 19, 38, {{0, 29, 30}, {0, 28, 0}, 34, 0}, "Крупно, лицо", false});
    c.shots.push_back({"Рука", 38, 56, {{-16, 19, 24}, {-6, 19, 0}, 36, 4}, "Рука у стекла", false});
}

void saveProject() {
    std::ofstream f(kProject, std::ios::binary);
    f << "LOTOS-EDITOR 1\n";
    for (const Cut& c : gCuts) {
        f << "CUT\t" << esc(c.name) << '\t' << c.song << '\n';
        for (const Shot& s : c.shots)
            f << "S\t" << esc(s.name) << '\t' << s.t0 << '\t' << s.t1 << '\t' << s.cam.pos.x << ' ' << s.cam.pos.y << ' ' << s.cam.pos.z << '\t'
              << s.cam.target.x << ' ' << s.cam.target.y << ' ' << s.cam.target.z << '\t' << s.cam.fov << '\t' << s.cam.roll << '\t' << esc(s.note) << '\t' << (s.move ? 1 : 0) << '\n';
        for (const Ev& e : c.events) f << "E\t" << e.track << '\t' << e.t0 << '\t' << e.t1 << '\t' << esc(e.label) << '\t' << e.value << '\n';
    }
    gStatus = "Сохранено";
}

// the real fight, started from the second where the play head stands, with the scenes straight from this project
void launchFight(double t) {
    saveProject();
    char cmd[512];
    std::snprintf(cmd, sizeof cmd, "pythonw \"%s/../FightLab/run_fight.py\" song2 %.2f", kRoot.c_str(), t);
    STARTUPINFOA si = {};
    si.cb = sizeof si;
    PROCESS_INFORMATION pi = {};
    if (CreateProcessA(nullptr, cmd, nullptr, nullptr, FALSE, CREATE_NO_WINDOW, nullptr, nullptr, &si, &pi)) {
        CloseHandle(pi.hProcess);
        CloseHandle(pi.hThread);
        gStatus = "Бой запускается с " + std::to_string((int) t) + " с (собираю изменения…)";
    } else {
        gStatus = "Не удалось запустить бой (нужен pythonw в PATH)";
    }
}

std::vector<std::string> split(const std::string& line) {
    std::vector<std::string> out;
    std::string cur;
    for (char c : line) {
        if (c == '\t') {
            out.push_back(cur);
            cur.clear();
        } else if (c != '\r') {
            cur += c;
        }
    }
    out.push_back(cur);
    return out;
}

void loadProject() {
    gCuts.clear();
    std::ifstream f(kProject, std::ios::binary);
    std::string line;
    while (f && std::getline(f, line)) {
        std::vector<std::string> p = split(line);
        try {
            if (p[0] == "CUT" && p.size() >= 3) {
                gCuts.emplace_back();
                gCuts.back().name = unesc(p[1]);
                gCuts.back().song = p[2];
            } else if (p[0] == "S" && p.size() >= 9 && !gCuts.empty()) {
                Shot s;
                s.name = unesc(p[1]);
                s.t0 = std::stod(p[2]);
                s.t1 = std::stod(p[3]);
                std::stringstream a(p[4]), b(p[5]);
                a >> s.cam.pos.x >> s.cam.pos.y >> s.cam.pos.z;
                b >> s.cam.target.x >> s.cam.target.y >> s.cam.target.z;
                s.cam.fov = std::stof(p[6]);
                s.cam.roll = std::stof(p[7]);
                s.note = unesc(p[8]);
                s.move = p.size() > 9 && p[9] == "1";
                gCuts.back().shots.push_back(s);
            } else if (p[0] == "E" && p.size() >= 5 && !gCuts.empty()) {
                Ev e;
                e.track = std::stoi(p[1]);
                e.t0 = std::stod(p[2]);
                e.t1 = std::stod(p[3]);
                e.label = unesc(p[4]);
                e.value = p.size() > 5 ? std::stof(p[5]) : 1.0f;
                gCuts.back().events.push_back(e);
            }
        } catch (...) {
        }
    }
    if (gCuts.empty()) {
        gCuts.emplace_back();
        gCuts.back().name = "Песня 2";
    }
    for (Cut& c : gCuts)
        if (c.shots.empty()) defaultShots(c);
    gCut = 0;
    gSelShot = 0;
    gSelEv = -1;
}

// ---- the music
ma_engine gEngine;
ma_sound gSound;
bool gEngineOk = false, gSoundOk = false;
double gLength = 185.0, gHead = 0.0;
std::vector<float> gPeaks;      // the loudest sample of every 10 ms
bool gPlaying = false;

void unloadSong() {
    if (gSoundOk) ma_sound_uninit(&gSound);
    gSoundOk = false;
    gPeaks.clear();
    gPlaying = false;
}

void loadSong() {
    unloadSong();
    if (!gEngineOk) gEngineOk = ma_engine_init(nullptr, &gEngine) == MA_SUCCESS;
    if (!gEngineOk) {
        gStatus = "Не удалось запустить звук";
        return;
    }
    if (ma_sound_init_from_file(&gEngine, cut().song.c_str(), MA_SOUND_FLAG_DECODE | MA_SOUND_FLAG_NO_SPATIALIZATION, nullptr, nullptr, &gSound) != MA_SUCCESS) {
        gStatus = "Не найдена музыка: " + cut().song;
        return;
    }
    gSoundOk = true;
    float len = 0;
    ma_sound_get_length_in_seconds(&gSound, &len);
    gLength = len;
    ma_decoder_config cfg = ma_decoder_config_init(ma_format_f32, 1, 22050);
    ma_decoder dec;
    if (ma_decoder_init_file(cut().song.c_str(), &cfg, &dec) == MA_SUCCESS) {
        std::vector<float> buf(220);
        ma_uint64 got = 0;
        while (ma_decoder_read_pcm_frames(&dec, buf.data(), 220, &got) == MA_SUCCESS && got > 0) {
            float m = 0;
            for (ma_uint64 i = 0; i < got; i++) m = std::max(m, std::fabs(buf[i]));
            gPeaks.push_back(m);
            if (got < 220) break;
        }
        ma_decoder_uninit(&dec);
    }
}

double soundPos() {
    ma_uint64 f = 0;
    ma_sound_get_cursor_in_pcm_frames(&gSound, &f);
    return (double) f / ma_engine_get_sample_rate(&gEngine);
}
void seek(double t) {
    gHead = std::max(0.0, std::min(gLength, t));
    if (gSoundOk) ma_sound_seek_to_pcm_frame(&gSound, (ma_uint64) (gHead * ma_engine_get_sample_rate(&gEngine)));
}
void play() {
    if (!gSoundOk) return;
    seek(gHead);
    ma_sound_start(&gSound);
    gPlaying = true;
}
void pause() {
    if (!gSoundOk) return;
    gHead = soundPos();
    ma_sound_stop(&gSound);
    gPlaying = false;
}
void switchCut(int i) {
    gCut = i;
    gSelShot = 0;
    gSelEv = -1;
    gHead = 0;
    loadSong();
}

// ---- the viewport: an orbit camera of its own, or the camera of the shot
struct Orbit {
    float yaw = 0.35f, pitch = 0.18f, dist = 62;
    V3 target{0, 16, 0};
    Camera camera() const {
        Camera c;
        c.target = target;
        c.pos = target + V3{std::sin(yaw) * std::cos(pitch), std::sin(pitch), std::cos(yaw) * std::cos(pitch)} * dist;
        c.fov = 40;
        return c;
    }
    void from(const Camera& c) {
        V3 d = c.pos - c.target;
        dist = std::max(1.0f, std::sqrt(dot(d, d)));
        yaw = std::atan2(d.x, d.z);
        pitch = std::asin(std::max(-1.0f, std::min(1.0f, d.y / dist)));
        target = c.target;
    }
};
Orbit gOrbit;
bool gFree = false;
Look gLook;
Scene gScene;
Target gViewTarget;
std::vector<Target> gThumbs;

int activeShot(double t) {
    int best = -1;
    for (int i = 0; i < (int) cut().shots.size(); i++)
        if (cut().shots[i].t0 <= t && (best < 0 || cut().shots[i].t0 >= cut().shots[best].t0)) best = i;
    return best < 0 ? 0 : best;
}

// the picture, with the mouse: the right button turns, the middle one moves, the wheel comes nearer
void viewport(const char* id, ImVec2 size, const Camera& shotCam) {
    if (size.x < 40 || size.y < 40) return;
    gViewTarget.ensure((int) size.x, (int) size.y);
    Camera cam = gFree ? gOrbit.camera() : shotCam;
    gLook.time = (float) ImGui::GetTime();
    gScene.render(gViewTarget, cam, gLook);
    ImGui::Image((ImTextureID) (intptr_t) gViewTarget.tex, size, ImVec2(0, 1), ImVec2(1, 0));
    if (ImGui::IsItemHovered()) {
        ImGuiIO& io = ImGui::GetIO();
        if (!gFree && (io.MouseWheel != 0 || ImGui::IsMouseDragging(1) || ImGui::IsMouseDragging(2))) {
            gOrbit.from(shotCam);                     // the first touch frees the view, starting from the shot's own
            gFree = true;
        }
        if (gFree) {
            if (ImGui::IsMouseDragging(1)) {
                gOrbit.yaw -= io.MouseDelta.x * 0.008f;
                gOrbit.pitch = std::max(-1.4f, std::min(1.4f, gOrbit.pitch + io.MouseDelta.y * 0.008f));
            }
            if (ImGui::IsMouseDragging(2)) {
                Camera c = gOrbit.camera();
                V3 f = norm(c.target - c.pos), r = norm(cross(f, V3{0, 1, 0})), u = cross(r, f);
                float k = gOrbit.dist * 0.0016f;
                gOrbit.target = gOrbit.target + r * (-io.MouseDelta.x * k) + u * (io.MouseDelta.y * k);
            }
            if (io.MouseWheel != 0) gOrbit.dist = std::max(3.0f, gOrbit.dist * std::pow(0.9f, io.MouseWheel));
        }
    }
}

// ---- the timeline
double gPxPerSec = 40.0, gScroll = 0.0;
int gDragEv = -1, gDragMode = 0;
double gDragOff = 0.0;

const float kRulerH = 26, kWaveH = 64, kShotH = 26, kTrackH = 46;
float timelineHeight() { return kRulerH + kWaveH + kShotH + kTrackH * kTrackCount + 6; }

void timeline() {
    const float headW = 110;
    ImVec2 p0 = ImGui::GetCursorScreenPos();
    ImVec2 avail = ImGui::GetContentRegionAvail();
    ImDrawList* dl = ImGui::GetWindowDrawList();
    float totalH = timelineHeight();
    ImGui::InvisibleButton("##tl", ImVec2(avail.x, std::max(avail.y, totalH)), ImGuiButtonFlags_MouseButtonLeft | ImGuiButtonFlags_MouseButtonRight);
    ImGuiIO& io = ImGui::GetIO();
    ImVec2 mp = io.MousePos;
    bool hover = ImGui::IsItemHovered();
    float x0 = p0.x + headW, x1 = p0.x + avail.x;
    auto xOf = [&](double t) { return (float) (x0 + (t - gScroll) * gPxPerSec); };
    auto tOf = [&](float x) { return (x - x0) / gPxPerSec + gScroll; };
    std::vector<Ev>& events = cut().events;

    if (hover && io.MouseWheel != 0) {
        if (io.KeyCtrl) {
            double tm = tOf(mp.x);
            gPxPerSec = std::max(4.0, std::min(1500.0, gPxPerSec * std::pow(1.15, io.MouseWheel)));
            gScroll = tm - (mp.x - x0) / gPxPerSec;
        } else {
            gScroll -= io.MouseWheel * 240.0 / gPxPerSec;
        }
        gScroll = std::max(0.0, gScroll);
    }
    dl->AddRectFilled(p0, ImVec2(x1, p0.y + totalH), IM_COL32(18, 18, 24, 255));
    double step = 1;
    for (double s : {0.1, 0.25, 0.5, 1.0, 2.0, 5.0, 10.0, 30.0}) {
        step = s;
        if (s * gPxPerSec >= 70) break;
    }
    for (double t = std::floor(gScroll / step) * step; xOf(t) < x1; t += step) {
        float x = xOf(t);
        if (x < x0) continue;
        dl->AddLine(ImVec2(x, p0.y + 6), ImVec2(x, p0.y + totalH), IM_COL32(255, 255, 255, 18));
        char b[32];
        int m = (int) (t / 60);
        std::snprintf(b, sizeof b, "%d:%05.2f", m, t - m * 60);
        dl->AddText(ImVec2(x + 3, p0.y + 4), IM_COL32(170, 170, 190, 255), b);
    }
    float wy = p0.y + kRulerH, wm = wy + kWaveH / 2;
    for (float x = x0; x < x1; x += 1.0f) {
        double a = tOf(x), b = tOf(x + 1);
        size_t i0 = (size_t) std::max(0.0, a * 100), i1 = (size_t) std::max(0.0, b * 100) + 1;
        float m = 0;
        for (size_t i = i0; i < i1 && i < gPeaks.size(); i++) m = std::max(m, gPeaks[i]);
        if (m > 0) dl->AddLine(ImVec2(x, wm - m * kWaveH * 0.48f), ImVec2(x, wm + m * kWaveH * 0.48f), IM_COL32(110, 120, 200, 255));
    }
    // the lane of the shots
    float sy = wy + kWaveH;
    dl->AddRectFilled(ImVec2(p0.x, sy), ImVec2(x1, sy + kShotH - 2), IM_COL32(24, 24, 32, 255));
    dl->AddRectFilled(ImVec2(p0.x, sy), ImVec2(x0 - 4, sy + kShotH - 2), IM_COL32(40, 40, 54, 255));
    dl->AddText(ImVec2(p0.x + 10, sy + 4), IM_COL32(255, 210, 120, 255), "Кадры");
    int hoverShot = -1;
    for (int i = 0; i < (int) cut().shots.size(); i++) {
        const Shot& s = cut().shots[i];
        float xa = xOf(s.t0), xb = std::max(xOf(s.t1), xa + 6);
        if (xb < x0 || xa > x1) continue;
        dl->PushClipRect(ImVec2(x0, sy), ImVec2(x1, sy + kShotH), true);
        dl->AddRectFilled(ImVec2(xa, sy + 2), ImVec2(xb - 1, sy + kShotH - 4), i == gSelShot ? IM_COL32(255, 200, 90, 200) : IM_COL32(255, 200, 90, 90), 3);
        dl->AddText(ImVec2(xa + 4, sy + 4), IM_COL32(255, 255, 255, 255), s.name.c_str());
        dl->PopClipRect();
        if (hover && mp.x >= std::max(xa, x0) && mp.x <= xb && mp.y >= sy && mp.y <= sy + kShotH) hoverShot = i;
    }
    float ty = sy + kShotH;
    for (int k = 0; k < kTrackCount; k++) {
        float y = ty + kTrackH * k;
        dl->AddRectFilled(ImVec2(p0.x, y), ImVec2(x1, y + kTrackH - 2), (k % 2) ? IM_COL32(26, 26, 34, 255) : IM_COL32(30, 30, 40, 255));
        dl->AddRectFilled(ImVec2(p0.x, y), ImVec2(x0 - 4, y + kTrackH - 2), IM_COL32(40, 40, 54, 255));
        dl->AddText(ImVec2(p0.x + 10, y + kTrackH / 2 - 9), kTrackCol[k], kTracks[k]);
    }
    int hoverEv = -1, hoverEdge = 0;
    for (int i = 0; i < (int) events.size(); i++) {
        const Ev& e = events[i];
        float y = ty + kTrackH * e.track + 4;
        float xa = xOf(e.t0), xb = std::max(xOf(e.t1), xa + 6);
        if (xb < x0 || xa > x1) continue;
        ImU32 c = kTrackCol[e.track];
        ImU32 fill = (c & 0x00FFFFFF) | (i == gSelEv ? 0xB0000000 : 0x70000000);
        dl->PushClipRect(ImVec2(x0, y), ImVec2(x1, y + kTrackH), true);
        dl->AddRectFilled(ImVec2(xa, y), ImVec2(xb, y + kTrackH - 10), fill, 4);
        dl->AddRect(ImVec2(xa, y), ImVec2(xb, y + kTrackH - 10), c, 4, 0, i == gSelEv ? 2.5f : 1.0f);
        dl->AddText(ImVec2(xa + 5, y + 6), IM_COL32(255, 255, 255, 255), e.label.c_str());
        dl->PopClipRect();
        if (hover && mp.x >= std::max(xa, x0) && mp.x <= xb && mp.y >= y && mp.y <= y + kTrackH - 10) {
            hoverEv = i;
            hoverEdge = (mp.x > xb - 7) ? 2 : 1;
        }
    }
    int laneTrack = (hover && mp.x > x0 && mp.y > ty) ? (int) ((mp.y - ty) / kTrackH) : -1;
    if (laneTrack >= kTrackCount) laneTrack = -1;
    if (hoverEv >= 0 && hoverEdge == 2) ImGui::SetMouseCursor(ImGuiMouseCursor_ResizeEW);

    if (hover && ImGui::IsMouseClicked(0)) {
        if (mp.y < wy + kWaveH && mp.x > x0) {
            seek(tOf(mp.x));
            gDragMode = 3;
        } else if (hoverShot >= 0) {
            gSelShot = hoverShot;
        } else if (hoverEv >= 0) {
            gSelEv = hoverEv;
            gDragEv = hoverEv;
            gDragMode = hoverEdge;
            gDragOff = tOf(mp.x) - (hoverEdge == 2 ? events[hoverEv].t1 : events[hoverEv].t0);
        } else {
            gSelEv = -1;
        }
    }
    if (hover && ImGui::IsMouseDoubleClicked(0) && hoverEv < 0 && laneTrack >= 0) {
        double t = std::max(0.0, tOf(mp.x));
        events.push_back({laneTrack, t, t + 1.0, kPresets[laneTrack][0], 1});
        gSelEv = (int) events.size() - 1;
        gDragMode = 0;
    }
    if (ImGui::IsMouseDown(0)) {
        if (gDragMode == 3) seek(tOf(mp.x));
        if (gDragEv >= 0 && gDragEv < (int) events.size()) {
            Ev& e = events[gDragEv];
            double t = tOf(mp.x) - gDragOff;
            if (gDragMode == 1) {
                double len = e.t1 - e.t0;
                e.t0 = std::max(0.0, t);
                e.t1 = e.t0 + len;
                int nt = (int) ((mp.y - ty) / kTrackH);
                if (nt >= 0 && nt < kTrackCount) e.track = nt;
            } else if (gDragMode == 2) {
                e.t1 = std::max(e.t0 + 0.05, t);
            }
        }
    } else {
        gDragEv = -1;
        gDragMode = 0;
    }
    if (gPlaying && gSoundOk) gHead = soundPos();
    float hx = xOf(gHead);
    if (hx >= x0 && hx <= x1) {
        dl->AddLine(ImVec2(hx, p0.y), ImVec2(hx, p0.y + totalH), IM_COL32(255, 80, 80, 255), 2.0f);
        dl->AddTriangleFilled(ImVec2(hx - 6, p0.y), ImVec2(hx + 6, p0.y), ImVec2(hx, p0.y + 10), IM_COL32(255, 80, 80, 255));
    }
    if (gPlaying && (hx > x1 - 60 || hx < x0)) gScroll = gHead - 1;
}

// ---- the storyboard: the shots as cards, each with its own picture
void storyboardStrip(float h) {
    std::vector<Shot>& shots = cut().shots;
    if (gThumbs.size() < shots.size()) gThumbs.resize(shots.size());
    const float cw = 250, ch = cw * 9 / 16;
    ImGui::BeginChild("strip", ImVec2(0, h), ImGuiChildFlags_Borders, ImGuiWindowFlags_HorizontalScrollbar);
    for (int i = 0; i < (int) shots.size(); i++) {
        gThumbs[i].ensure((int) cw, (int) ch);
        gScene.render(gThumbs[i], shots[i].cam, gLook);
        ImGui::BeginGroup();
        ImVec2 pos = ImGui::GetCursorScreenPos();
        ImGui::Image((ImTextureID) (intptr_t) gThumbs[i].tex, ImVec2(cw, ch), ImVec2(0, 1), ImVec2(1, 0));
        if (ImGui::IsItemClicked()) {
            gSelShot = i;
            gFree = false;
            seek(shots[i].t0);
        }
        ImGui::GetWindowDrawList()->AddRect(pos, ImVec2(pos.x + cw, pos.y + ch), i == gSelShot ? IM_COL32(255, 200, 90, 255) : IM_COL32(70, 70, 100, 255), 3, 0,
                                            i == gSelShot ? 3.0f : 1.0f);
        ImGui::Text("%d. %s", i + 1, shots[i].name.c_str());
        ImGui::TextDisabled("%.2f - %.2f с", shots[i].t0, shots[i].t1);
        ImGui::EndGroup();
        ImGui::SameLine();
    }
    ImGui::EndChild();
}

void shotInspector() {
    std::vector<Shot>& shots = cut().shots;
    if (shots.empty()) return;
    gSelShot = std::max(0, std::min((int) shots.size() - 1, gSelShot));
    Shot& s = shots[gSelShot];
    char buf[160];
    std::snprintf(buf, sizeof buf, "%s", s.name.c_str());
    ImGui::TextDisabled("КАДР %d", gSelShot + 1);
    if (ImGui::InputText("Название", buf, sizeof buf)) s.name = buf;
    float t0 = (float) s.t0, t1 = (float) s.t1;
    if (ImGui::DragFloat("Начало, с", &t0, 0.01f, 0, (float) gLength, "%.2f")) s.t0 = t0;
    if (ImGui::DragFloat("Конец, с", &t1, 0.01f, 0, (float) gLength, "%.2f")) s.t1 = std::max(t1, t0 + 0.05f);
    ImGui::Separator();
    ImGui::TextDisabled("КАМЕРА");
    ImGui::DragFloat3("Позиция", &s.cam.pos.x, 0.1f);
    ImGui::DragFloat3("Куда смотрит", &s.cam.target.x, 0.1f);
    ImGui::SliderFloat("Угол обзора", &s.cam.fov, 10, 100, "%.0f°");
    ImGui::SliderFloat("Наклон", &s.cam.roll, -45, 45, "%.1f°");
    ImGui::Checkbox("Плавно к камере следующего кадра", &s.move);
    if (ImGui::Button("Записать текущий вид в кадр")) {
        Camera c = gFree ? gOrbit.camera() : s.cam;
        c.fov = s.cam.fov;
        c.roll = s.cam.roll;
        s.cam = c;
    }
    if (ImGui::Button("Показать камеру кадра")) gFree = false;
    ImGui::Separator();
    std::snprintf(buf, sizeof buf, "%s", s.note.c_str());
    ImGui::TextDisabled("ЗАМЕТКА");
    static char note[512];
    std::snprintf(note, sizeof note, "%s", s.note.c_str());
    if (ImGui::InputTextMultiline("##note", note, sizeof note, ImVec2(-1, 100))) s.note = note;
    ImGui::Separator();
    if (ImGui::Button("Добавить кадр")) {
        Shot n = s;
        n.name = "Кадр " + std::to_string(shots.size() + 1);
        n.t0 = s.t1;
        n.t1 = s.t1 + 5;
        shots.push_back(n);
        gSelShot = (int) shots.size() - 1;
    }
    ImGui::SameLine();
    if (ImGui::Button("Удалить кадр") && shots.size() > 1) {
        shots.erase(shots.begin() + gSelShot);
        gSelShot = std::max(0, gSelShot - 1);
    }
}

void eventInspector() {
    std::vector<Ev>& events = cut().events;
    if (gSelEv < 0 || gSelEv >= (int) events.size()) {
        ImGui::TextDisabled("Выбери блок на шкале\nили сделай двойной клик\nпо дорожке, чтобы создать.");
        return;
    }
    Ev& e = events[gSelEv];
    ImGui::TextDisabled("СОБЫТИЕ");
    ImGui::Combo("Дорожка", &e.track, kTracks, kTrackCount);
    char buf[160];
    std::snprintf(buf, sizeof buf, "%s", e.label.c_str());
    if (ImGui::InputText("Название", buf, sizeof buf)) e.label = buf;
    ImGui::TextDisabled("Заготовки:");
    for (int i = 0; i < 4; i++) {
        if (i % 2) ImGui::SameLine();
        if (ImGui::Button(kPresets[e.track][i], ImVec2(130, 0))) e.label = kPresets[e.track][i];
    }
    float t0 = (float) e.t0, t1 = (float) e.t1;
    if (ImGui::DragFloat("Начало, с", &t0, 0.01f, 0, (float) gLength, "%.2f")) e.t0 = t0;
    if (ImGui::DragFloat("Конец, с", &t1, 0.01f, 0, (float) gLength, "%.2f")) e.t1 = std::max(t1, t0 + 0.02f);
    ImGui::SliderFloat("Сила", &e.value, 0, 3, "%.2f");
    if (ImGui::Button("Удалить событие")) {
        events.erase(events.begin() + gSelEv);
        gSelEv = -1;
    }
}

void topBar() {
    if (ImGui::Button(gPlaying ? "  Пауза  " : "  Играть  ")) gPlaying ? pause() : play();
    ImGui::SameLine();
    if (ImGui::Button("В начало")) seek(0);
    ImGui::SameLine();
    int m = (int) (gHead / 60);
    ImGui::Text("  %d:%06.3f / %d:%05.2f", m, gHead - m * 60, (int) (gLength / 60), gLength - (int) (gLength / 60) * 60);
    ImGui::SameLine(0, 30);
    ImGui::SetNextItemWidth(220);
    if (ImGui::BeginCombo("##cut", cut().name.c_str())) {
        for (int i = 0; i < (int) gCuts.size(); i++)
            if (ImGui::Selectable(gCuts[i].name.c_str(), i == gCut)) switchCut(i);
        ImGui::EndCombo();
    }
    ImGui::SameLine();
    if (ImGui::Button("+ сцена")) {
        gCuts.emplace_back();
        defaultShots(gCuts.back());
        gCuts.back().name = "Сцена " + std::to_string(gCuts.size());
        switchCut((int) gCuts.size() - 1);
    }
    ImGui::SameLine(0, 30);
    ImVec4 on(0.30f, 0.34f, 0.68f, 1), off(0.16f, 0.17f, 0.30f, 1);
    ImGui::PushStyleColor(ImGuiCol_Button, gMode == 0 ? on : off);
    if (ImGui::Button("  РАСКАДРОВКА  ")) gMode = 0;
    ImGui::PopStyleColor();
    ImGui::SameLine();
    ImGui::PushStyleColor(ImGuiCol_Button, gMode == 1 ? on : off);
    if (ImGui::Button("  СОБЫТИЯ И АНИМАЦИЯ  ")) gMode = 1;
    ImGui::PopStyleColor();
    ImGui::SameLine(0, 30);
    if (ImGui::Button("Сохранить")) saveProject();
    ImGui::SameLine();
    if (ImGui::Button("▶ Бой отсюда")) launchFight(gHead);
    ImGui::SameLine();
    ImGui::TextDisabled("%s", gStatus.c_str());
    ImGui::Separator();
}

}  // namespace

int main(int argc, char** argv) {
    bool selftest = argc > 2 && std::strcmp(argv[1], "--selftest") == 0;      // draws the shots to picture files and quits (for checking)
    if (!glfwInit()) return 1;
    if (selftest) glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
    glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
    glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
    glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
    glfwWindowHint(GLFW_MAXIMIZED, GLFW_TRUE);
    GLFWwindow* win = glfwCreateWindow(1600, 900, "Lotos Editor", nullptr, nullptr);
    if (!win) return 1;
    glfwMakeContextCurrent(win);
    glfwSwapInterval(1);
    bool glOk = loadGL();

    IMGUI_CHECKVERSION();
    ImGui::CreateContext();
    ImGuiIO& io = ImGui::GetIO();
    io.IniFilename = nullptr;
    ImGui::StyleColorsDark();
    ImGuiStyle& st = ImGui::GetStyle();
    st.WindowRounding = 6;
    st.FrameRounding = 5;
    st.GrabRounding = 5;
    st.ChildRounding = 5;
    st.Colors[ImGuiCol_WindowBg] = ImVec4(0.07f, 0.07f, 0.10f, 1);
    st.Colors[ImGuiCol_ChildBg] = ImVec4(0.09f, 0.09f, 0.13f, 1);
    st.Colors[ImGuiCol_Button] = ImVec4(0.25f, 0.28f, 0.50f, 1);
    st.Colors[ImGuiCol_ButtonHovered] = ImVec4(0.34f, 0.38f, 0.66f, 1);
    ImFont* font = io.Fonts->AddFontFromFileTTF("C:/Windows/Fonts/segoeui.ttf", 19.0f, nullptr, io.Fonts->GetGlyphRangesCyrillic());
    if (!font) io.Fonts->AddFontDefault();
    ImGui_ImplGlfw_InitForOpenGL(win, true);
    ImGui_ImplOpenGL3_Init("#version 330");

    std::string sceneError;
    bool sceneOk = glOk && gScene.init(kRoot + "/data", sceneError);
    if (!glOk) sceneError = "Видеодрайвер не даёт OpenGL 3.3";
    loadProject();
    loadSong();
    if (!sceneOk) gStatus = sceneError;
    if (selftest) {
        if (!sceneOk) {
            std::fprintf(stderr, "scene: %s\n", sceneError.c_str());
            return 2;
        }
        for (size_t i = 0; i < cut().shots.size(); i++) {
            Target t;
            t.ensure(960, 540);
            gScene.render(t, cut().shots[i].cam, gLook);
            std::vector<unsigned char> px(960 * 540 * 4);
            glBindFramebuffer(GL_FRAMEBUFFER, t.fbo);
            glReadPixels(0, 0, 960, 540, GL_RGBA, GL_UNSIGNED_BYTE, px.data());
            glBindFramebuffer(GL_FRAMEBUFFER, 0);
            stbi_flip_vertically_on_write(1);
            stbi_write_png((std::string(argv[2]) + "_" + std::to_string(i) + ".png").c_str(), 960, 540, 4, px.data(), 960 * 4);
        }
        std::printf("faces %d, shots %zu\n", gScene.faceCount, cut().shots.size());
        return 0;
    }

    while (!glfwWindowShouldClose(win)) {
        glfwPollEvents();
        ImGui_ImplOpenGL3_NewFrame();
        ImGui_ImplGlfw_NewFrame();
        ImGui::NewFrame();

        if (!io.WantTextInput) {
            if (ImGui::IsKeyPressed(ImGuiKey_Space, false)) gPlaying ? pause() : play();
            if (ImGui::IsKeyPressed(ImGuiKey_Delete) && gMode == 1 && gSelEv >= 0 && gSelEv < (int) cut().events.size()) {
                cut().events.erase(cut().events.begin() + gSelEv);
                gSelEv = -1;
            }
            if (io.KeyCtrl && ImGui::IsKeyPressed(ImGuiKey_S)) saveProject();
            if (ImGui::IsKeyPressed(ImGuiKey_Home)) seek(0);
            if (ImGui::IsKeyPressed(ImGuiKey_F)) gFree = !gFree;
        }
        if (gPlaying && gSoundOk) gHead = soundPos();

        ImGuiViewport* vp = ImGui::GetMainViewport();
        ImGui::SetNextWindowPos(vp->WorkPos);
        ImGui::SetNextWindowSize(vp->WorkSize);
        ImGui::Begin("root", nullptr, ImGuiWindowFlags_NoDecoration | ImGuiWindowFlags_NoMove | ImGuiWindowFlags_NoSavedSettings | ImGuiWindowFlags_NoBringToFrontOnFocus);
        topBar();

        ImVec2 avail = ImGui::GetContentRegionAvail();
        float bottomH = gMode == 0 ? 220.0f : timelineHeight() + 14;
        float mainH = std::max(160.0f, avail.y - bottomH - 8);
        float sideW = 340;
        // the middle: the picture, with the side panel
        ImGui::BeginChild("viewarea", ImVec2(avail.x - sideW - 8, mainH), ImGuiChildFlags_None, ImGuiWindowFlags_NoScrollbar);
        {
            ImVec2 a = ImGui::GetContentRegionAvail();
            std::vector<Shot>& shots = cut().shots;
            int idx = gMode == 0 ? std::min(gSelShot, (int) shots.size() - 1) : activeShot(gHead);
            if (sceneOk) {
                ImGui::Checkbox("Свободный вид (F)", &gFree);
                ImGui::SameLine();
                ImGui::Checkbox("Сетка", &gLook.grid);
                ImGui::SameLine();
                bool post = gLook.posterize > 0;
                if (ImGui::Checkbox("Постеризация", &post)) gLook.posterize = post ? 6.0f : 0.0f;
                ImGui::SameLine();
                ImGui::SetNextItemWidth(140);
                ImGui::SliderFloat("Контур", &gLook.rim, 0, 2);
                ImGui::SameLine();
                ImGui::TextDisabled("  ПКМ — вращать, колесо — приближать, СКМ — двигать");
                a = ImGui::GetContentRegionAvail();
                float w = std::min(a.x, a.y * 16.0f / 9.0f), h = w * 9.0f / 16.0f;
                ImGui::SetCursorPosX((a.x - w) / 2 + ImGui::GetCursorPosX());
                viewport("view", ImVec2(w, h), shots[idx].cam);
            } else {
                ImGui::TextWrapped("3D не запустился: %s", sceneError.c_str());
            }
        }
        ImGui::EndChild();
        ImGui::SameLine();
        ImGui::BeginChild("side", ImVec2(sideW, mainH), ImGuiChildFlags_Borders);
        if (gMode == 0) shotInspector();
        else eventInspector();
        ImGui::EndChild();

        // the bottom
        if (gMode == 0) {
            if (sceneOk) storyboardStrip(bottomH);
        } else {
            ImGui::BeginChild("timeline", ImVec2(0, 0), ImGuiChildFlags_None, ImGuiWindowFlags_NoScrollbar | ImGuiWindowFlags_NoScrollWithMouse);
            timeline();
            ImGui::EndChild();
        }
        ImGui::End();

        ImGui::Render();
        int w, h;
        glfwGetFramebufferSize(win, &w, &h);
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        glViewport(0, 0, w, h);
        glClearColor(0.05f, 0.05f, 0.07f, 1);
        glClear(GL_COLOR_BUFFER_BIT);
        ImGui_ImplOpenGL3_RenderDrawData(ImGui::GetDrawData());
        glfwSwapBuffers(win);
    }
    saveProject();
    unloadSong();
    if (gEngineOk) ma_engine_uninit(&gEngine);
    ImGui_ImplOpenGL3_Shutdown();
    ImGui_ImplGlfw_Shutdown();
    ImGui::DestroyContext();
    glfwTerminate();
    return 0;
}
