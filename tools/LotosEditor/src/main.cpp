// Lotos Editor: the timeline of the fights and episodes. The first core: music, a ruler, tracks with blocks, saving.
// The picture of the game itself (the preview) will come from the game's own renderer; here only a place for it.
#define MINIAUDIO_IMPLEMENTATION
#include "miniaudio.h"

#include <GLFW/glfw3.h>
#include "imgui.h"
#include "imgui_impl_glfw.h"
#include "imgui_impl_opengl3.h"

#include <algorithm>
#include <cmath>
#include <cstdio>
#include <fstream>
#include <sstream>
#include <string>
#include <vector>

namespace {

struct Ev {
    int track;
    double t0, t1;
    std::string label;
};

const char* kTracks[] = {"Камера", "Текст", "Эффекты", "Персонаж", "Змеи"};
const int kTrackCount = 5;
const ImU32 kTrackCol[] = {IM_COL32(90, 150, 255, 255), IM_COL32(240, 240, 240, 255), IM_COL32(255, 120, 150, 255),
                           IM_COL32(170, 120, 255, 255), IM_COL32(110, 220, 170, 255)};

std::string gSongPath = "D:/The LOTOS/build/song-test/song2.wav";
std::string gProjectPath = "D:/The LOTOS/tools/LotosEditor/project.txt";

ma_engine gEngine;
ma_sound gSound;
bool gSoundOk = false;
double gLength = 185.0;
std::vector<float> gPeaks;               // one per 10 ms
double gHead = 0.0;                      // the play head, seconds
bool gPlaying = false;

std::vector<Ev> gEvents;
int gSel = -1;
std::string gStatus = "Готов";

// ---- the music
void loadSong() {
    if (ma_engine_init(nullptr, &gEngine) != MA_SUCCESS) {
        gStatus = "Не удалось запустить звук";
        return;
    }
    if (ma_sound_init_from_file(&gEngine, gSongPath.c_str(), MA_SOUND_FLAG_DECODE | MA_SOUND_FLAG_NO_SPATIALIZATION, nullptr, nullptr, &gSound) != MA_SUCCESS) {
        gStatus = "Не найдена песня: " + gSongPath;
        return;
    }
    gSoundOk = true;
    float len = 0;
    ma_sound_get_length_in_seconds(&gSound, &len);
    gLength = len;
    // the picture of the sound: the loudest sample of every 10 ms
    ma_decoder_config cfg = ma_decoder_config_init(ma_format_f32, 1, 22050);
    ma_decoder dec;
    if (ma_decoder_init_file(gSongPath.c_str(), &cfg, &dec) == MA_SUCCESS) {
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

// ---- the project file: one block a line, "track<TAB>from<TAB>to<TAB>label"
void saveProject() {
    std::ofstream f(gProjectPath, std::ios::binary);
    for (const Ev& e : gEvents) f << e.track << '\t' << e.t0 << '\t' << e.t1 << '\t' << e.label << '\n';
    gStatus = "Сохранено: " + std::to_string(gEvents.size()) + " блоков";
}

void loadProject() {
    std::ifstream f(gProjectPath, std::ios::binary);
    if (!f) {
        gStatus = "Файла проекта ещё нет";
        return;
    }
    gEvents.clear();
    std::string line;
    while (std::getline(f, line)) {
        std::stringstream ss(line);
        std::string a, b, c, d;
        std::getline(ss, a, '\t');
        std::getline(ss, b, '\t');
        std::getline(ss, c, '\t');
        std::getline(ss, d);
        if (a.empty() || b.empty() || c.empty()) continue;
        gEvents.push_back({std::stoi(a), std::stod(b), std::stod(c), d});
    }
    gSel = -1;
    gStatus = "Загружено: " + std::to_string(gEvents.size()) + " блоков";
}

// ---- the timeline
double gPxPerSec = 40.0, gScroll = 0.0;
int gDragEv = -1, gDragMode = 0;       // 1 move, 2 stretch the right edge
double gDragOff = 0.0;
char gLabelBuf[128];

void timeline() {
    const float headW = 110, rulerH = 26, waveH = 64, trackH = 46;
    ImVec2 p0 = ImGui::GetCursorScreenPos();
    ImVec2 avail = ImGui::GetContentRegionAvail();
    ImDrawList* dl = ImGui::GetWindowDrawList();
    float totalH = rulerH + waveH + trackH * kTrackCount;
    ImGui::InvisibleButton("##tl", ImVec2(avail.x, std::max(avail.y, totalH)), ImGuiButtonFlags_MouseButtonLeft | ImGuiButtonFlags_MouseButtonRight);
    ImGuiIO& io = ImGui::GetIO();
    ImVec2 mp = io.MousePos;
    bool hover = ImGui::IsItemHovered();
    float x0 = p0.x + headW, x1 = p0.x + avail.x;
    auto xOf = [&](double t) { return (float) (x0 + (t - gScroll) * gPxPerSec); };
    auto tOf = [&](float x) { return (x - x0) / gPxPerSec + gScroll; };

    // zoom with Ctrl+wheel, scroll with the wheel
    if (hover && io.MouseWheel != 0) {
        if (io.KeyCtrl) {
            double tm = tOf(mp.x);
            gPxPerSec = std::max(4.0, std::min(1500.0, gPxPerSec * std::pow(1.15, io.MouseWheel)));
            gScroll = tm - (mp.x - x0) / gPxPerSec;
        } else {
            gScroll -= io.MouseWheel * 80.0 / gPxPerSec * 3;
        }
        gScroll = std::max(0.0, gScroll);
    }

    dl->AddRectFilled(p0, ImVec2(x1, p0.y + totalH), IM_COL32(18, 18, 24, 255));
    // the ruler
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
    // the sound
    float wy = p0.y + rulerH, wm = wy + waveH / 2;
    for (float x = x0; x < x1; x += 1.0f) {
        double t0 = tOf(x), t1 = tOf(x + 1);
        size_t i0 = (size_t) std::max(0.0, t0 * 100), i1 = (size_t) std::max(0.0, t1 * 100) + 1;
        float m = 0;
        for (size_t i = i0; i < i1 && i < gPeaks.size(); i++) m = std::max(m, gPeaks[i]);
        if (m > 0) dl->AddLine(ImVec2(x, wm - m * waveH * 0.48f), ImVec2(x, wm + m * waveH * 0.48f), IM_COL32(110, 120, 200, 255));
    }
    // the tracks
    for (int k = 0; k < kTrackCount; k++) {
        float y = wy + waveH + trackH * k;
        dl->AddRectFilled(ImVec2(p0.x, y), ImVec2(x1, y + trackH - 2), (k % 2) ? IM_COL32(26, 26, 34, 255) : IM_COL32(30, 30, 40, 255));
        dl->AddRectFilled(ImVec2(p0.x, y), ImVec2(x0 - 4, y + trackH - 2), IM_COL32(40, 40, 54, 255));
        dl->AddText(ImVec2(p0.x + 10, y + trackH / 2 - 9), kTrackCol[k], kTracks[k]);
    }
    // the blocks
    int hoverEv = -1, hoverEdge = 0;
    for (int i = 0; i < (int) gEvents.size(); i++) {
        const Ev& e = gEvents[i];
        float y = wy + waveH + trackH * e.track + 4;
        float xa = xOf(e.t0), xb = std::max(xOf(e.t1), xa + 6);
        if (xb < x0 || xa > x1) continue;
        ImU32 c = kTrackCol[e.track];
        ImU32 fill = (c & 0x00FFFFFF) | (i == gSel ? 0xB0000000 : 0x70000000);
        dl->PushClipRect(ImVec2(x0, y), ImVec2(x1, y + trackH), true);
        dl->AddRectFilled(ImVec2(xa, y), ImVec2(xb, y + trackH - 10), fill, 4);
        dl->AddRect(ImVec2(xa, y), ImVec2(xb, y + trackH - 10), c, 4, 0, i == gSel ? 2.5f : 1.0f);
        dl->AddText(ImVec2(xa + 5, y + 6), IM_COL32(255, 255, 255, 255), e.label.c_str());
        dl->PopClipRect();
        if (hover && mp.x >= std::max(xa, x0) && mp.x <= xb && mp.y >= y && mp.y <= y + trackH - 10) {
            hoverEv = i;
            hoverEdge = (mp.x > xb - 7) ? 2 : 1;
        }
    }
    bool inLane = hover && mp.x > x0 && mp.y > wy + waveH;
    int laneTrack = inLane ? (int) ((mp.y - wy - waveH) / trackH) : -1;
    if (laneTrack >= kTrackCount) laneTrack = -1;

    if (hoverEv >= 0 && hoverEdge == 2) ImGui::SetMouseCursor(ImGuiMouseCursor_ResizeEW);
    // the mouse
    if (hover && ImGui::IsMouseClicked(0)) {
        if (mp.y < wy + waveH && mp.x > x0) {
            seek(tOf(mp.x));
            gDragMode = 3;                                        // dragging the play head
        } else if (hoverEv >= 0) {
            gSel = hoverEv;
            gDragEv = hoverEv;
            gDragMode = hoverEdge;
            gDragOff = tOf(mp.x) - (hoverEdge == 2 ? gEvents[hoverEv].t1 : gEvents[hoverEv].t0);
        } else {
            gSel = -1;
        }
    }
    if (hover && ImGui::IsMouseDoubleClicked(0) && hoverEv < 0 && laneTrack >= 0) {
        double t = std::max(0.0, tOf(mp.x));
        gEvents.push_back({laneTrack, t, t + 1.0, "событие"});
        gSel = (int) gEvents.size() - 1;
        gDragMode = 0;
    }
    if (ImGui::IsMouseDown(0)) {
        if (gDragMode == 3) seek(tOf(mp.x));
        if (gDragEv >= 0 && gDragEv < (int) gEvents.size()) {
            Ev& e = gEvents[gDragEv];
            double t = tOf(mp.x) - gDragOff;
            if (gDragMode == 1) {
                double len = e.t1 - e.t0;
                e.t0 = std::max(0.0, t);
                e.t1 = e.t0 + len;
                int nt = (int) ((mp.y - wy - waveH) / trackH);
                if (nt >= 0 && nt < kTrackCount) e.track = nt;
            } else if (gDragMode == 2) {
                e.t1 = std::max(e.t0 + 0.05, t);
            }
        }
    } else {
        gDragEv = -1;
        gDragMode = 0;
    }
    if (hover && ImGui::IsMouseClicked(1) && hoverEv >= 0) {
        gSel = hoverEv;
        std::snprintf(gLabelBuf, sizeof gLabelBuf, "%s", gEvents[hoverEv].label.c_str());
        ImGui::OpenPopup("block");
    }
    // the play head
    if (gPlaying && gSoundOk) gHead = soundPos();
    float hx = xOf(gHead);
    if (hx >= x0 && hx <= x1) {
        dl->AddLine(ImVec2(hx, p0.y), ImVec2(hx, p0.y + totalH), IM_COL32(255, 80, 80, 255), 2.0f);
        dl->AddTriangleFilled(ImVec2(hx - 6, p0.y), ImVec2(hx + 6, p0.y), ImVec2(hx, p0.y + 10), IM_COL32(255, 80, 80, 255));
    }
    // the view follows the head while playing
    if (gPlaying && (hx > x1 - 60 || hx < x0)) gScroll = gHead - 1;
}

}  // namespace

int main() {
    if (!glfwInit()) return 1;
    glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
    glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
    glfwWindowHint(GLFW_MAXIMIZED, GLFW_TRUE);
    GLFWwindow* win = glfwCreateWindow(1600, 900, "Lotos Editor", nullptr, nullptr);
    if (!win) return 1;
    glfwMakeContextCurrent(win);
    glfwSwapInterval(1);

    IMGUI_CHECKVERSION();
    ImGui::CreateContext();
    ImGuiIO& io = ImGui::GetIO();
    io.IniFilename = "D:/The LOTOS/tools/LotosEditor/layout.ini";
    ImGui::StyleColorsDark();
    ImGuiStyle& st = ImGui::GetStyle();
    st.WindowRounding = 6;
    st.FrameRounding = 5;
    st.GrabRounding = 5;
    st.Colors[ImGuiCol_WindowBg] = ImVec4(0.07f, 0.07f, 0.10f, 1);
    st.Colors[ImGuiCol_Button] = ImVec4(0.25f, 0.28f, 0.50f, 1);
    st.Colors[ImGuiCol_ButtonHovered] = ImVec4(0.34f, 0.38f, 0.66f, 1);
    ImFont* font = io.Fonts->AddFontFromFileTTF("C:/Windows/Fonts/segoeui.ttf", 19.0f, nullptr, io.Fonts->GetGlyphRangesCyrillic());
    if (!font) io.Fonts->AddFontDefault();
    ImGui_ImplGlfw_InitForOpenGL(win, true);
    ImGui_ImplOpenGL3_Init("#version 330");

    loadSong();
    loadProject();

    while (!glfwWindowShouldClose(win)) {
        glfwPollEvents();
        ImGui_ImplOpenGL3_NewFrame();
        ImGui_ImplGlfw_NewFrame();
        ImGui::NewFrame();

        // keys: space plays and stops, Delete removes the chosen block, Ctrl+S saves
        if (!io.WantTextInput) {
            if (ImGui::IsKeyPressed(ImGuiKey_Space, false)) gPlaying ? pause() : play();
            if (ImGui::IsKeyPressed(ImGuiKey_Delete) && gSel >= 0 && gSel < (int) gEvents.size()) {
                gEvents.erase(gEvents.begin() + gSel);
                gSel = -1;
            }
            if (io.KeyCtrl && ImGui::IsKeyPressed(ImGuiKey_S)) saveProject();
            if (ImGui::IsKeyPressed(ImGuiKey_Home)) seek(0);
        }

        ImGuiViewport* vp = ImGui::GetMainViewport();
        ImGui::SetNextWindowPos(vp->WorkPos);
        ImGui::SetNextWindowSize(vp->WorkSize);
        ImGui::Begin("root", nullptr, ImGuiWindowFlags_NoDecoration | ImGuiWindowFlags_NoMove | ImGuiWindowFlags_NoSavedSettings | ImGuiWindowFlags_NoBringToFrontOnFocus);

        // the top: the buttons
        if (ImGui::Button(gPlaying ? "  Пауза  " : "  Играть  ")) gPlaying ? pause() : play();
        ImGui::SameLine();
        if (ImGui::Button("В начало")) seek(0);
        ImGui::SameLine();
        int m = (int) (gHead / 60);
        ImGui::Text("   %d:%06.3f / %d:%05.2f", m, gHead - m * 60, (int) (gLength / 60), gLength - (int) (gLength / 60) * 60);
        ImGui::SameLine(0, 40);
        if (ImGui::Button("Сохранить")) saveProject();
        ImGui::SameLine();
        if (ImGui::Button("Загрузить")) loadProject();
        ImGui::SameLine(0, 40);
        ImGui::TextDisabled("%s", gStatus.c_str());
        ImGui::Separator();

        // the middle: the place of the game's own picture
        float bottomH = 26 + 64 + 46 * kTrackCount + 16;
        ImVec2 avail = ImGui::GetContentRegionAvail();
        float prevH = std::max(120.0f, avail.y - bottomH - 40);
        ImVec2 q0 = ImGui::GetCursorScreenPos();
        float pw = std::min(avail.x, prevH * 16.0f / 9.0f), ph = pw * 9.0f / 16.0f;
        ImVec2 a(q0.x + (avail.x - pw) / 2, q0.y), b(a.x + pw, a.y + ph);
        ImGui::GetWindowDrawList()->AddRectFilled(a, b, IM_COL32(0, 0, 0, 255));
        ImGui::GetWindowDrawList()->AddRect(a, b, IM_COL32(70, 70, 110, 255));
        const char* msg = "Здесь будет кадр из игры";
        ImVec2 ts = ImGui::CalcTextSize(msg);
        ImGui::GetWindowDrawList()->AddText(ImVec2((a.x + b.x - ts.x) / 2, (a.y + b.y - ts.y) / 2), IM_COL32(120, 120, 150, 255), msg);
        ImGui::Dummy(ImVec2(avail.x, ph + 6));

        // the bottom: the timeline
        ImGui::BeginChild("timeline", ImVec2(0, 0), false, ImGuiWindowFlags_NoScrollbar | ImGuiWindowFlags_NoScrollWithMouse);
        timeline();
        if (ImGui::BeginPopup("block")) {
            if (gSel >= 0 && gSel < (int) gEvents.size()) {
                ImGui::SetNextItemWidth(220);
                if (ImGui::InputText("##lbl", gLabelBuf, sizeof gLabelBuf)) gEvents[gSel].label = gLabelBuf;
                if (ImGui::Button("Удалить")) {
                    gEvents.erase(gEvents.begin() + gSel);
                    gSel = -1;
                    ImGui::CloseCurrentPopup();
                }
            }
            ImGui::EndPopup();
        }
        ImGui::EndChild();
        ImGui::End();

        ImGui::Render();
        int w, h;
        glfwGetFramebufferSize(win, &w, &h);
        glViewport(0, 0, w, h);
        glClearColor(0.05f, 0.05f, 0.07f, 1);
        glClear(GL_COLOR_BUFFER_BIT);
        ImGui_ImplOpenGL3_RenderDrawData(ImGui::GetDrawData());
        glfwSwapBuffers(win);
    }
    if (gSoundOk) ma_sound_uninit(&gSound);
    ma_engine_uninit(&gEngine);
    ImGui_ImplOpenGL3_Shutdown();
    ImGui_ImplGlfw_Shutdown();
    ImGui::DestroyContext();
    glfwTerminate();
    return 0;
}
