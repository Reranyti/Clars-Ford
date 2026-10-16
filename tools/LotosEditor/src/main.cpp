// Lotos Editor: the engine's editor for every cutscene of the game. A project is a list of cuts (scenes); a cut has its music, its shots
// (a camera each) and its events on the tracks. Two modes: the STORYBOARD (the shots as cards) and the EVENTS AND ANIMATION (the timeline).
// The viewport is drawn with OpenGL shaders: the game's own figure, the floor, the light of the close-up.
#define MINIAUDIO_IMPLEMENTATION
#include "miniaudio.h"

#include "gl.h"
#include "imgui.h"
#include "imgui_internal.h"
#include "imgui_impl_glfw.h"
#include "imgui_impl_opengl3.h"
#include "scene.h"

#define STB_IMAGE_WRITE_IMPLEMENTATION
#include "stb_image_write.h"
#include "stb_image.h"
#include <cstring>
#include <windows.h>
#include <atomic>
#include <mutex>
#include <thread>

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
    bool autogen = false;      // made by the draft from the music: the draft can be remade, these go and come again
};
struct Shot {
    std::string name;
    double t0, t1;
    Camera cam;
    std::string note;
    bool move = false;      // glides to the camera of the next shot over its own length
};
struct Layer {
    std::string id, name;
    bool visible = true;
    float dx = 0, dy = 0, scale = 1, rot = 0, alpha = 1;      // shift (parts of the picture), scale, turn (degrees), opacity
};
struct Cut {
    std::string name = "Новая сцена";
    bool imported = false;                // the events the game has in its own code have been put on the timeline already
    std::string program = "song2";       // which scene program of the game draws it (the game lists them, see ScenePrograms)
    std::vector<Layer> layers;
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

// what the game says its scene programs are made of: {id, title, layers {id, name}}
struct Program {
    std::string id, title;
    std::vector<std::pair<std::string, std::string>> layers;
};
std::vector<Program> gPrograms;

// the layers of a cut are the ones its scene program lists; the ones already set up stay as they are, the new ones are added on top
void ensureLayers(Cut& c) {
    for (const Program& pr : gPrograms) {
        if (pr.id != c.program) continue;
        for (auto& l : pr.layers) {
            bool have = false;
            for (const Layer& y : c.layers) have = have || y.id == l.first;
            if (!have) {
                Layer y;
                y.id = l.first;
                y.name = l.second;
                c.layers.push_back(y);
            }
        }
        return;
    }
    if (!c.layers.empty() || c.program != "song2") return;     // before the game has answered: the old default for the second song
    const char* list[][2] = {{"backdrop", "Фон"}, {"columns", "Колонки"}, {"glitcher", "Глитчер"}, {"eye", "Глаз"},
                             {"windows", "Окна (след)"}, {"windows2", "Окна Глитчера"}, {"snakes", "Змеи и телевизор"}, {"phrases", "Чёрные экраны с фразами"}};
    for (auto& l : list) {
        Layer y;
        y.id = l[0];
        y.name = l[1];
        c.layers.push_back(y);
    }
}
int gSelLayer = -1;
int gTool = 0;          // 0 select (click picks a layer, drag moves it), 1 camera

std::vector<double> gBeats;       // seconds
bool gSnap = true;

void loadBeats(const std::string& osu) {
    std::ifstream f(osu);
    std::string line;
    bool in = false;
    while (std::getline(f, line)) {
        if (line.rfind("[HitObjects]", 0) == 0) {
            in = true;
            continue;
        }
        if (!in) continue;
        int commas = 0;
        size_t a = 0, b = 0;
        for (size_t i = 0; i < line.size(); i++) {
            if (line[i] == ',') {
                if (++commas == 2) a = i + 1;
                if (commas == 3) {
                    b = i;
                    break;
                }
            }
        }
        if (b > a) gBeats.push_back(std::atof(line.substr(a, b - a).c_str()) / 1000.0);
    }
    std::sort(gBeats.begin(), gBeats.end());
}

double snapT(double t, double pxPerSec) {
    if (!gSnap || gBeats.empty()) return t;
    auto it = std::lower_bound(gBeats.begin(), gBeats.end(), t);
    double best = t, bd = 1e9;
    if (it != gBeats.end() && std::fabs(*it - t) < bd) { best = *it; bd = std::fabs(*it - t); }
    if (it != gBeats.begin() && std::fabs(*(it - 1) - t) < bd) { best = *(it - 1); bd = std::fabs(*(it - 1) - t); }
    return bd * pxPerSec < 9 ? best : t;
}

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

std::string projectText() {
    std::ostringstream f;
    f << "LOTOS-EDITOR 1\n";
    for (const Cut& c : gCuts) {
        f << "CUT\t" << esc(c.name) << '\t' << c.song << '\t' << c.program << '\t' << (c.imported ? "I" : "-") << '\n';
        for (const Shot& s : c.shots)
            f << "S\t" << esc(s.name) << '\t' << s.t0 << '\t' << s.t1 << '\t' << s.cam.pos.x << ' ' << s.cam.pos.y << ' ' << s.cam.pos.z << '\t'
              << s.cam.target.x << ' ' << s.cam.target.y << ' ' << s.cam.target.z << '\t' << s.cam.fov << '\t' << s.cam.roll << '\t' << esc(s.note) << '\t' << (s.move ? 1 : 0) << '\n';
        for (const Layer& y : c.layers)
            f << "O\t" << y.id << '\t' << (y.visible ? 1 : 0) << '\t' << y.dx << '\t' << y.dy << '\t' << y.scale << '\t' << y.rot * 3.14159265358979 / 180.0 << '\t' << y.alpha << '\t' << esc(y.name) << '\n';
        for (const Ev& e : c.events) f << "E\t" << e.track << '\t' << e.t0 << '\t' << e.t1 << '\t' << esc(e.label) << '\t' << e.value << '\t' << (e.autogen ? 'A' : '-') << '\n';
    }
    return f.str();
}

std::string gLastSaved;

void writeProject(const std::string& text) {
    std::ofstream f(kProject, std::ios::binary);
    f << text;
    gLastSaved = text;
}

void saveProject() {
    writeProject(projectText());
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

void parseProject(std::istream& f) {
    gCuts.clear();
    std::string line;
    while (f && std::getline(f, line)) {
        std::vector<std::string> p = split(line);
        try {
            if (p[0] == "CUT" && p.size() >= 3) {
                gCuts.emplace_back();
                gCuts.back().name = unesc(p[1]);
                gCuts.back().song = p[2];
                gCuts.back().program = p.size() > 3 && !p[3].empty() ? p[3] : "song2";
                gCuts.back().imported = p.size() > 4 && p[4] == "I";
                ensureLayers(gCuts.back());
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
            } else if (p[0] == "O" && p.size() >= 8 && !gCuts.empty()) {
                for (Layer& y : gCuts.back().layers) {
                    if (y.id != p[1]) continue;
                    y.visible = p[2] == "1";
                    y.dx = std::stof(p[3]);
                    y.dy = std::stof(p[4]);
                    y.scale = std::stof(p[5]);
                    y.rot = std::stof(p[6]) * 180.0f / 3.14159265f;
                    y.alpha = std::stof(p[7]);
                }
            } else if (p[0] == "E" && p.size() >= 5 && !gCuts.empty()) {
                Ev e;
                e.track = std::stoi(p[1]);
                e.t0 = std::stod(p[2]);
                e.t1 = std::stod(p[3]);
                e.label = unesc(p[4]);
                e.value = p.size() > 5 ? std::stof(p[5]) : 1.0f;
                e.autogen = p.size() > 6 && p[6] == "A";
                gCuts.back().events.push_back(e);
            }
        } catch (...) {
        }
    }
    if (gCuts.empty()) {
        gCuts.emplace_back();
        gCuts.back().name = "Песня 2";
    }
    for (Cut& c : gCuts) ensureLayers(c);
    for (Cut& c : gCuts)
        if (c.shots.empty()) defaultShots(c);
}

void loadProject() {
    std::ifstream f(kProject, std::ios::binary);
    parseProject(f);
    gCut = 0;
    gSelShot = 0;
    gSelEv = -1;
}

// the undo history: the whole project text after every finished change
std::vector<std::string> gHist;
int gHistAt = -1;

void restoreText(const std::string& text) {
    std::istringstream in(text);
    parseProject(in);
    gCut = std::max(0, std::min(gCut, (int) gCuts.size() - 1));
    gSelShot = std::max(0, std::min(gSelShot, (int) cut().shots.size() - 1));
    if (gSelEv >= (int) cut().events.size()) gSelEv = -1;
}

void undo(int dir) {
    int n = gHistAt + dir;
    if (n < 0 || n >= (int) gHist.size()) return;
    gHistAt = n;
    restoreText(gHist[n]);
}

void trackHistory() {
    std::string cur = projectText();
    if (gHistAt >= 0 && gHist[gHistAt] == cur) return;
    gHist.resize(gHistAt + 1);
    gHist.push_back(cur);
    if (gHist.size() > 300) gHist.erase(gHist.begin());
    gHistAt = (int) gHist.size() - 1;
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


// ---- the game's own picture: a Java process (PreviewMain) draws the visuals of the song at the asked moment, the editor shows it
struct Preview {
    HANDLE proc = nullptr, toJava = nullptr, fromJava = nullptr;
    std::thread worker, reader;
    std::atomic<bool> ready{false}, busy{false}, failed{false};
    std::string state = "Запускаю игровое превью…";
    std::mutex lock;
    std::vector<unsigned char> png;       // the newest picture that has come (raw pixels), not yet shown
    int rawW = 0, rawH = 0;
    bool fresh = false;
    GLuint tex = 0;
    int texW = 0, texH = 0;
    std::string pickAnswer, boxAnswer, programsAnswer, program = "song2";
    bool programsReady = false, programsWanted = true;
    std::string eventsAnswer, eventsFor;
    bool eventsReady = false, eventsWanted = false;
    bool pickReady = false, boxReady = false;
    bool pickWanted = false, boxWanted = false;
    int pickX = 0, pickY = 0;
    std::string boxId;
    double askedT = -1;
    int askedW = 0, askedH = 0;
    double lastAsk = 0;

    static bool readAll(HANDLE h, void* buf, DWORD n) {
        char* b = (char*) buf;
        while (n > 0) {
            DWORD got = 0;
            if (!ReadFile(h, b, n, &got, nullptr) || got == 0) return false;
            b += got;
            n -= got;
        }
        return true;
    }

    void start() {
        worker = std::thread([this] {
            // 1. compile what changed, get the classpath
            std::string py = "pythonw \"" + kRoot + "/../FightLab/run_fight.py\" build";
            STARTUPINFOA si = {};
            si.cb = sizeof si;
            PROCESS_INFORMATION pi = {};
            std::vector<char> c1(py.begin(), py.end());
            c1.push_back(0);
            state = "Собираю игру (первый раз дольше)…";
            if (!CreateProcessA(nullptr, c1.data(), nullptr, nullptr, FALSE, CREATE_NO_WINDOW, nullptr, nullptr, &si, &pi)) {
                state = "Не запустился pythonw";
                failed = true;
                return;
            }
            WaitForSingleObject(pi.hProcess, 300000);
            CloseHandle(pi.hProcess);
            CloseHandle(pi.hThread);
            std::ifstream cpf("D:/The LOTOS/build/fight-lab/preview.cp");
            std::string cp;
            std::getline(cpf, cp);
            if (cp.empty()) {
                state = "Сборка не удалась: см. build/fight-lab/last_run.log";
                failed = true;
                return;
            }
            // 2. the Java process with two pipes
            SECURITY_ATTRIBUTES sa = {sizeof sa, nullptr, TRUE};
            HANDLE inR, inW, outR, outW;
            CreatePipe(&inR, &inW, &sa, 0);
            CreatePipe(&outR, &outW, &sa, 1 << 20);
            SetHandleInformation(inW, HANDLE_FLAG_INHERIT, 0);
            SetHandleInformation(outR, HANDLE_FLAG_INHERIT, 0);
            STARTUPINFOA s2 = {};
            s2.cb = sizeof s2;
            s2.dwFlags = STARTF_USESTDHANDLES;
            s2.hStdInput = inR;
            s2.hStdOutput = outW;
            HANDLE log = CreateFileA("D:/The LOTOS/build/fight-lab/preview.log", GENERIC_WRITE, FILE_SHARE_READ, &sa, CREATE_ALWAYS, FILE_ATTRIBUTE_NORMAL, nullptr);
            s2.hStdError = log;
            std::string cmd = "D:/gradle-home/jdks/eclipse_adoptium-17-amd64-windows/jdk-17.0.20.1+1/bin/java.exe -Xmx900m -Djava.awt.headless=true -Dlotos.scenes=\"" +
                              kProject + "\" -cp \"" + cp + "\" com.lotusblight.overlay.PreviewMain";
            std::vector<char> c2(cmd.begin(), cmd.end());
            c2.push_back(0);
            PROCESS_INFORMATION p2 = {};
            if (!CreateProcessA(nullptr, c2.data(), nullptr, nullptr, TRUE, CREATE_NO_WINDOW, nullptr, nullptr, &s2, &p2)) {
                state = "Не запустилась Java";
                failed = true;
                return;
            }
            CloseHandle(p2.hThread);
            CloseHandle(inR);
            CloseHandle(outW);
            proc = p2.hProcess;
            toJava = inW;
            fromJava = outR;
            ready = true;
            state = "Превью из игры";
            // 3. the pictures come one by one
            reader = std::thread([this] {
                for (;;) {
                    unsigned char hdr[4];
                    if (!readAll(fromJava, hdr, 4)) break;
                    DWORD n = (hdr[0] << 24) | (hdr[1] << 16) | (hdr[2] << 8) | hdr[3];
                    if (n == 0 || n > (64u << 20)) break;
                    std::vector<unsigned char> b(n);
                    if (!readAll(fromJava, b.data(), n)) break;
                    {
                        std::lock_guard<std::mutex> g(lock);
                        if (b[0] == 3 && b.size() > 9) {                            // a picture: width, height, then the pixels as B G R A
                            rawW = (b[1] << 24) | (b[2] << 16) | (b[3] << 8) | b[4];
                            rawH = (b[5] << 24) | (b[6] << 16) | (b[7] << 8) | b[8];
                            png.assign(b.begin() + 9, b.end());
                            fresh = true;
                        } else if (b[0] == 5) {                                     // the events a scene has in its code
                            eventsAnswer.assign((const char*) b.data() + 1, b.size() - 1);
                            eventsReady = true;
                        } else if (b[0] == 4) {                                     // the list of the scene programs
                            programsAnswer.assign((const char*) b.data() + 1, b.size() - 1);
                            programsReady = true;
                        } else if (b[0] == 1) {                                     // the layer under the point
                            pickAnswer.assign((const char*) b.data() + 1, b.size() - 1);
                            pickReady = true;
                        } else if (b[0] == 2) {                                     // the box of a layer
                            boxAnswer.assign((const char*) b.data() + 1, b.size() - 1);
                            boxReady = true;
                        }
                    }
                    busy = false;
                }
                state = "Превью остановилось (см. build/fight-lab/preview.log)";
                ready = false;
                failed = true;
            });
        });
    }

    // asks for the picture at a moment, if the last one has arrived
    bool send(const char* line, int n) {
        DWORD wrote = 0;
        busy = true;
        if (!WriteFile(toJava, line, n, &wrote, nullptr)) {
            ready = false;
            failed = true;
            busy = false;
            return false;
        }
        return true;
    }

    bool keepLayers = true;

    void ask(double t, int w, int h, bool force) {
        if (!ready || busy) return;
        char line[160];
        if (programsWanted) {
            programsWanted = false;
            send("S\n", 2);
            return;
        }
        if (eventsWanted && !eventsFor.empty()) {
            eventsWanted = false;
            send(line, std::snprintf(line, sizeof line, "E %s\n", eventsFor.c_str()));
            return;
        }
        if (pickWanted) {
            pickWanted = false;
            send(line, std::snprintf(line, sizeof line, "P %d %d\n", pickX, pickY));
            return;
        }
        if (boxWanted && !boxId.empty()) {
            boxWanted = false;
            send(line, std::snprintf(line, sizeof line, "B %s\n", boxId.c_str()));
            return;
        }
        if (!force && t == askedT && w == askedW && h == askedH) return;
        int n = std::snprintf(line, sizeof line, "R %.1f %d %d %d %s\n", t * 1000.0, w, h, keepLayers ? 1 : 0, program.c_str());
        DWORD wrote = 0;
        busy = true;
        if (!WriteFile(toJava, line, n, &wrote, nullptr)) {
            ready = false;
            failed = true;
            busy = false;
        }
        askedT = t;
        askedW = w;
        askedH = h;
    }

    // puts the newest picture into the texture
    void upload() {
        std::vector<unsigned char> b;
        {
            std::lock_guard<std::mutex> g(lock);
            if (!fresh) return;
            b.swap(png);
            fresh = false;
        }
        int w, h;
        {
            std::lock_guard<std::mutex> g(lock);
            w = rawW;
            h = rawH;
        }
        if (w <= 0 || h <= 0 || b.size() < (size_t) w * h * 4) return;
        if (!tex) glGenTextures(1, &tex);
        glBindTexture(GL_TEXTURE_2D, tex);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, w, h, 0, GL_BGRA, GL_UNSIGNED_BYTE, b.data());
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        texW = w;
        texH = h;
    }

    void stop() {
        if (proc) TerminateProcess(proc, 0);
        if (worker.joinable()) worker.join();
        if (reader.joinable()) reader.detach();
    }
};
Preview gPreview;
bool gGameView = true;          // the game's own picture, or the 3D view of the editor
bool gForceAsk = true;
double gLastEdit = 0;

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

const float kRulerH = 26, kWaveH = 64, kShotH = 26, kTrackH = 64;
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
    for (double bt : gBeats) {                                   // the beats of the map, as small ticks under the sound
        if (gPxPerSec < 18) break;
        float x = xOf(bt);
        if (x < x0 || x > x1) continue;
        dl->AddLine(ImVec2(x, wy + kWaveH - 7), ImVec2(x, wy + kWaveH - 1), IM_COL32(255, 200, 90, 150));
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
    // blocks that overlap in a track are put in lanes one under another, as in a video editor
    std::vector<int> laneOf(events.size(), 0);
    int laneCount[kTrackCount];
    for (int k = 0; k < kTrackCount; k++) {
        std::vector<int> ids;
        for (int i = 0; i < (int) events.size(); i++)
            if (events[i].track == k) ids.push_back(i);
        std::sort(ids.begin(), ids.end(), [&](int a, int b) { return events[a].t0 < events[b].t0; });
        std::vector<double> ends;
        for (int id : ids) {
            int l = 0;
            while (l < (int) ends.size() && ends[l] > events[id].t0 + 0.001) l++;
            if (l == (int) ends.size()) ends.push_back(0);
            ends[l] = events[id].t1;
            laneOf[id] = l;
        }
        laneCount[k] = std::max(1, (int) ends.size());
    }
    for (int i = 0; i < (int) events.size(); i++) {
        const Ev& e = events[i];
        int lanes = std::min(4, laneCount[e.track]);
        float bh = (kTrackH - 8) / lanes;
        float y = ty + kTrackH * e.track + 3 + std::min(laneOf[i], lanes - 1) * bh;
        float xa = xOf(e.t0), xb = std::max(xOf(e.t1), xa + 6);
        if (xb < x0 || xa > x1) continue;
        ImU32 c = kTrackCol[e.track];
        ImU32 fill = (c & 0x00FFFFFF) | (i == gSelEv ? 0xB0000000 : 0x70000000);
        dl->PushClipRect(ImVec2(std::max(x0, xa), y), ImVec2(std::min(x1, xb), y + bh), true);
        dl->AddRectFilled(ImVec2(xa, y), ImVec2(xb, y + bh - 2), fill, 3);
        dl->AddRect(ImVec2(xa, y), ImVec2(xb, y + bh - 2), c, 3, 0, i == gSelEv ? 2.5f : 1.0f);
        if (bh >= 15) dl->AddText(ImVec2(xa + 4, y + (bh - 2 - ImGui::GetFontSize()) / 2), IM_COL32(255, 255, 255, 255), e.label.c_str());
        dl->PopClipRect();
        if (hover && mp.x >= std::max(xa, x0) && mp.x <= xb && mp.y >= y && mp.y <= y + bh - 2) {
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
            double t = snapT(tOf(mp.x) - gDragOff, gPxPerSec);
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
        dl->AddLine(ImVec2(hx, p0.y), ImVec2(hx, p0.y + totalH), IM_COL32(77, 156, 255, 255), 2.0f);
        dl->AddTriangleFilled(ImVec2(hx - 6, p0.y), ImVec2(hx + 6, p0.y), ImVec2(hx, p0.y + 10), IM_COL32(77, 156, 255, 255));
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

// ---- the look: the grey of a video editor, one blue accent
// ---- the draft of the dynamics, made from the music and from the author's own map of circles; every block of it can be moved, changed or deleted
void clearDraft() {
    std::vector<Ev>& ev = cut().events;
    ev.erase(std::remove_if(ev.begin(), ev.end(), [](const Ev& e) { return e.autogen; }), ev.end());
}

void makeDraft() {
    clearDraft();
    std::vector<Ev>& ev = cut().events;
    auto add = [&](int track, double t, double len, const char* label, float value) {
        Ev e;
        e.track = track;
        e.t0 = t;
        e.t1 = t + len;
        e.label = label;
        e.value = value;
        e.autogen = true;
        ev.push_back(e);
    };
    // 1. after every stop of the circles the picture jerks, as if the music kicks it
    for (size_t i = 1; i < gBeats.size(); i++)
        if (gBeats[i] - gBeats[i - 1] >= 0.6) add(0, gBeats[i], 0.3, "Рывок", 1.0f);
    // 2. every fourth circle gives the picture a small tremble
    for (size_t i = 0; i < gBeats.size(); i += 4) add(0, gBeats[i], 0.14, "Тряска", 0.35f);
    // 3. where the music grows suddenly (the level of the next second against the last one): a flash, a push in and a shake
    auto level = [&](long a, long b) {
        a = std::max(0L, a);
        b = std::min((long) gPeaks.size(), b);
        if (b <= a) return 0.0;
        double sum = 0;
        for (long i = a; i < b; i++) sum += gPeaks[i];
        return sum / (b - a);
    };
    double mx = 0;
    for (float v : gPeaks) mx = std::max(mx, (double) v);
    for (long i = 150; i < (long) gPeaks.size() - 150; i += 5) {
        double before = level(i - 120, i - 10), after = level(i + 10, i + 120);
        if (after > before * 1.7 && after > 0.12 * mx) {
            double t = i / 100.0;
            add(2, t, 0.4, "Вспышка", 1.0f);
            add(0, t, 1.8, "Наезд", 1.0f);
            add(0, t, 0.6, "Тряска", 1.3f);
            i += 400;
        }
    }
    std::sort(ev.begin(), ev.end(), [](const Ev& a, const Ev& b) { return a.t0 < b.t0; });
    gSelEv = -1;
    gStatus = "Черновик динамики собран: его можно править";
}

void applyTheme() {
    ImGuiStyle& st = ImGui::GetStyle();
    st.WindowRounding = 0;
    st.ChildRounding = 0;
    st.FrameRounding = 3;
    st.GrabRounding = 3;
    st.TabRounding = 0;
    st.ScrollbarRounding = 3;
    st.WindowPadding = ImVec2(10, 8);
    st.FramePadding = ImVec2(8, 4);
    st.ItemSpacing = ImVec2(8, 6);
    st.WindowBorderSize = 1;
    st.FrameBorderSize = 0;
    st.TabBarBorderSize = 1;
    ImVec4* c = st.Colors;
    auto rgb = [](int r, int g, int b, float a = 1) { return ImVec4(r / 255.f, g / 255.f, b / 255.f, a); };
    c[ImGuiCol_WindowBg] = rgb(35, 35, 35);
    c[ImGuiCol_ChildBg] = rgb(30, 30, 30);
    c[ImGuiCol_PopupBg] = rgb(40, 40, 40);
    c[ImGuiCol_Border] = rgb(18, 18, 18);
    c[ImGuiCol_FrameBg] = rgb(24, 24, 24);
    c[ImGuiCol_FrameBgHovered] = rgb(50, 50, 50);
    c[ImGuiCol_FrameBgActive] = rgb(56, 56, 56);
    c[ImGuiCol_TitleBg] = rgb(29, 29, 29);
    c[ImGuiCol_TitleBgActive] = rgb(29, 29, 29);
    c[ImGuiCol_MenuBarBg] = rgb(29, 29, 29);
    c[ImGuiCol_Tab] = rgb(35, 35, 35);
    c[ImGuiCol_TabHovered] = rgb(55, 55, 55);
    c[ImGuiCol_TabSelected] = rgb(46, 46, 46);
    c[ImGuiCol_TabSelectedOverline] = rgb(45, 140, 235);
    c[ImGuiCol_TabDimmed] = rgb(30, 30, 30);
    c[ImGuiCol_TabDimmedSelected] = rgb(40, 40, 40);
    c[ImGuiCol_Header] = rgb(52, 52, 52);
    c[ImGuiCol_HeaderHovered] = rgb(64, 64, 64);
    c[ImGuiCol_HeaderActive] = rgb(45, 140, 235, 0.55f);
    c[ImGuiCol_Button] = rgb(58, 58, 58);
    c[ImGuiCol_ButtonHovered] = rgb(78, 78, 78);
    c[ImGuiCol_ButtonActive] = rgb(45, 140, 235);
    c[ImGuiCol_SliderGrab] = rgb(45, 140, 235);
    c[ImGuiCol_SliderGrabActive] = rgb(90, 170, 250);
    c[ImGuiCol_CheckMark] = rgb(45, 140, 235);
    c[ImGuiCol_DockingPreview] = rgb(45, 140, 235, 0.5f);
    c[ImGuiCol_Separator] = rgb(18, 18, 18);
    c[ImGuiCol_Text] = rgb(217, 217, 217);
    c[ImGuiCol_TextDisabled] = rgb(130, 130, 130);
    c[ImGuiCol_ScrollbarBg] = rgb(30, 30, 30);
    c[ImGuiCol_ScrollbarGrab] = rgb(70, 70, 70);
}

ImFont* gBigFont = nullptr;
bool gSceneOk = false;
bool gSafe = false;
bool gResetLayout = false;

std::string timecode(double t) {
    int f = (int) std::floor(t * 25 + 0.0001);
    char b[40];
    std::snprintf(b, sizeof b, "%02d:%02d:%02d:%02d", f / 25 / 3600, f / 25 / 60 % 60, f / 25 % 60, f % 25);
    return b;
}

// the monitor: the picture, the safe zones and, under it, the timecode with the transport
bool gMonMax = false;
std::string gBox;

// drags the camera of a shot by the mouse: mode 0 slides it sideways and up, 1 turns it about what it looks at
void dragCamera(Camera& c, float dx, float dy, int mode, float imageH) {
    V3 v = c.pos - c.target;
    float dist = std::max(1.0f, std::sqrt(dot(v, v)));
    V3 fwd = v * (-1.0f / dist);
    V3 right = norm(cross(fwd, V3{0, 1, 0}));
    V3 up = cross(right, fwd);
    if (mode == 0) {
        float k = dist * 2.0f * std::tan(c.fov * 3.14159265f / 360.0f) / std::max(1.0f, imageH);
        V3 shift = right * (-dx * k) + up * (dy * k);
        c.pos = c.pos + shift;
        c.target = c.target + shift;
    } else {
        float yaw = std::atan2(v.x, v.z) - dx * 0.006f;
        float pitch = std::max(-1.45f, std::min(1.45f, std::asin(std::max(-1.0f, std::min(1.0f, v.y / dist))) + dy * 0.006f));
        float r = dist * std::cos(pitch);
        c.pos = c.target + V3{r * std::sin(yaw), dist * std::sin(pitch), r * std::cos(yaw)};
    }
}

// the mouse over the picture works the camera of the shot that is on: the left button slides it, the right one turns it, the wheel comes
// nearer, with Shift the wheel changes the angle of view
void cameraByMouse(ImVec2 pos, ImVec2 size) {
    ImGui::SetCursorScreenPos(pos);
    ImGui::InvisibleButton("##monitor", size, ImGuiButtonFlags_MouseButtonLeft | ImGuiButtonFlags_MouseButtonRight | ImGuiButtonFlags_MouseButtonMiddle);
    std::vector<Shot>& shots = cut().shots;
    if (shots.empty()) return;
    ImGuiIO& io = ImGui::GetIO();
    bool hover = ImGui::IsItemHovered();
    // the frame of the chosen layer, from the box the game's side measured
    if (gSelLayer >= 0 && gSelLayer < (int) cut().layers.size()) {
        std::string box;
        {
            std::lock_guard<std::mutex> lk(gPreview.lock);
            if (gPreview.boxReady) {
                gBox = gPreview.boxAnswer;
                gPreview.boxReady = false;
            }
        }
        float x0, y0, x1, y1;
        if (std::sscanf(gBox.c_str(), "%f %f %f %f", &x0, &y0, &x1, &y1) == 4 && gPreview.askedW > 0) {
            float k = size.x / gPreview.askedW;
            ImVec2 a(pos.x + x0 * k, pos.y + y0 * k), b(pos.x + x1 * k, pos.y + y1 * k);
            ImGui::GetWindowDrawList()->AddRect(a, b, IM_COL32(45, 140, 235, 255), 0, 0, 2.0f);
            ImGui::GetWindowDrawList()->AddText(ImVec2(a.x + 3, a.y + 2), IM_COL32(45, 140, 235, 255), cut().layers[gSelLayer].name.c_str());
        }
    }
    if (gTool == 0) {
        if (hover) ImGui::SetTooltip("Клик — выбрать слой, перетащить — двигать, колесо — размер, Shift+колесо — наклон, Delete — скрыть");
        if (hover && ImGui::IsMouseClicked(0) && gPreview.askedW > 0) {
            gPreview.pickX = (int) ((io.MousePos.x - pos.x) / size.x * gPreview.askedW);
            gPreview.pickY = (int) ((io.MousePos.y - pos.y) / size.y * gPreview.askedH);
            gPreview.pickWanted = true;
        }
        if (gSelLayer >= 0 && gSelLayer < (int) cut().layers.size()) {
            Layer& y = cut().layers[gSelLayer];
            if (ImGui::IsItemActive() && ImGui::IsMouseDragging(0, 2)) {
                y.dx += io.MouseDelta.x / size.x;
                y.dy += io.MouseDelta.y / size.y;
            }
            if (hover && io.MouseWheel != 0) {
                if (io.KeyShift) y.rot += io.MouseWheel * 3.0f;
                else y.scale = std::max(0.05f, y.scale * std::pow(1.06f, io.MouseWheel));
            }
        }
        return;
    }
    int idx = std::min(activeShot(gHead), (int) shots.size() - 1);
    if (hover) ImGui::SetTooltip("ЛКМ — двигать камеру, ПКМ — вращать, колесо — приближать, Shift+колесо — угол обзора");
    Camera& c = shots[idx].cam;
    if (ImGui::IsItemActive() && (ImGui::IsMouseDragging(0, 0) || ImGui::IsMouseDragging(1, 0) || ImGui::IsMouseDragging(2, 0))) {
        gSelShot = idx;
        dragCamera(c, io.MouseDelta.x, io.MouseDelta.y, ImGui::IsMouseDragging(0, 0) && !io.KeyShift ? 0 : 1, size.y);
        gFree = false;
    }
    if (hover && io.MouseWheel != 0) {
        gSelShot = idx;
        if (io.KeyShift) {
            c.fov = std::max(8.0f, std::min(110.0f, c.fov - io.MouseWheel * 2.0f));
        } else {
            V3 v = c.pos - c.target;
            c.pos = c.target + v * std::pow(0.9f, io.MouseWheel);
        }
    }
}

std::string gBoxDummy;

void monitorBody() {
    ImGui::PushStyleColor(ImGuiCol_Button, gTool == 0 ? ImVec4(0.18f, 0.55f, 0.92f, 1) : ImVec4(0.23f, 0.23f, 0.23f, 1));
    if (ImGui::Button("Выбор (V)")) gTool = 0;
    ImGui::PopStyleColor();
    ImGui::SameLine();
    ImGui::PushStyleColor(ImGuiCol_Button, gTool == 1 ? ImVec4(0.18f, 0.55f, 0.92f, 1) : ImVec4(0.23f, 0.23f, 0.23f, 1));
    if (ImGui::Button("Камера (C)")) gTool = 1;
    ImGui::PopStyleColor();
    ImGui::SameLine();
    ImGui::Checkbox("Вид из игры", &gGameView);
    ImGui::SameLine();
    ImGui::Checkbox("Безопасные зоны", &gSafe);
    ImGui::SameLine();
    if (ImGui::Button(gMonMax ? "Свернуть (F11)" : "Развернуть (F11)")) gMonMax = !gMonMax;
    ImGui::SameLine();
    ImGui::TextDisabled("%s", gPreview.state.c_str());
    const float controlsH = 58;
    ImVec2 a = ImGui::GetContentRegionAvail();
    float w = std::min(a.x, (a.y - controlsH) * 16.0f / 9.0f), h = w * 9.0f / 16.0f;
    if (w > 40) {
        ImVec2 origin = ImGui::GetCursorScreenPos();
        ImGui::SetCursorPosX(ImGui::GetCursorPosX() + (a.x - w) / 2);
        ImVec2 imgPos = ImGui::GetCursorScreenPos();
        std::vector<Shot>& shots = cut().shots;
        int idx = std::min(activeShot(gHead), (int) shots.size() - 1);
        if (gGameView && gPreview.ready) {
            gPreview.upload();
            // while it plays the picture is small and quick; standing still it is drawn as large as the monitor shows it
            static bool wasDown = false;
            bool down = ImGui::IsMouseDown(0) || ImGui::IsMouseDown(1);
            if (wasDown && !down) gForceAsk = true;
            wasDown = down;
            int qw = (gPlaying || down) ? 960 : (w >= 1000 ? 1920 : 1280), qh = qw * 9 / 16;
            gPreview.keepLayers = !gPlaying;
            gPreview.program = cut().program;
            if (!gPrograms.empty() && !gPreview.eventsWanted && !gPreview.eventsReady) {        // the events the game has in its code, once for each cut
                for (Cut& c : gCuts) {
                    if (c.imported) continue;
                    gPreview.eventsFor = c.program;
                    gPreview.eventsWanted = true;
                    break;
                }
            }
            {
                std::string ev;
                std::string forProgram;
                {
                    std::lock_guard<std::mutex> lk(gPreview.lock);
                    if (gPreview.eventsReady) {
                        ev = gPreview.eventsAnswer;
                        forProgram = gPreview.eventsFor;
                        gPreview.eventsReady = false;
                    }
                }
                if (!forProgram.empty()) {
                    std::istringstream in(ev);
                    std::string row;
                    std::vector<Ev> got;
                    while (std::getline(in, row)) {
                        std::vector<std::string> f = split(row);
                        if (f.size() < 5) continue;
                        Ev e;
                        e.track = std::atoi(f[0].c_str());
                        e.t0 = std::atof(f[1].c_str());
                        e.t1 = std::atof(f[2].c_str());
                        e.value = (float) std::atof(f[3].c_str());
                        e.label = f[4];
                        got.push_back(e);
                    }
                    for (Cut& c : gCuts) {
                        if (c.imported || c.program != forProgram) continue;
                        if (c.events.empty()) c.events = got;
                        c.imported = true;
                    }
                }
            }
            {
                std::string list;
                {
                    std::lock_guard<std::mutex> lk(gPreview.lock);
                    if (gPreview.programsReady) {
                        list = gPreview.programsAnswer;
                        gPreview.programsReady = false;
                    }
                }
                if (!list.empty()) {
                    gPrograms.clear();
                    std::istringstream in(list);
                    std::string row;
                    while (std::getline(in, row)) {
                        std::vector<std::string> f = split(row);
                        if (f.size() < 3) continue;
                        Program pr;
                        pr.id = f[0];
                        pr.title = f[1];
                        std::istringstream ls(f[2]);
                        std::string item;
                        while (std::getline(ls, item, ';')) {
                            size_t eq = item.find('=');
                            if (eq != std::string::npos) pr.layers.push_back({item.substr(0, eq), item.substr(eq + 1)});
                        }
                        gPrograms.push_back(pr);
                    }
                    for (Cut& c : gCuts) ensureLayers(c);
                }
            }
            gPreview.ask(gHead, qw, qh, gForceAsk);
            gForceAsk = false;
            std::string pr;
            {
                std::lock_guard<std::mutex> lk(gPreview.lock);
                if (gPreview.pickReady) {
                    pr = gPreview.pickAnswer;
                    gPreview.pickReady = false;
                    gSelLayer = -1;
                    for (int i = 0; i < (int) cut().layers.size(); i++)
                        if (cut().layers[i].id == pr) gSelLayer = i;
                    gPreview.boxWanted = gSelLayer >= 0;
                    gPreview.boxId = pr;
                }
            }
            if (gPreview.fresh && gSelLayer >= 0) {
                gPreview.boxWanted = true;
                gPreview.boxId = cut().layers[gSelLayer].id;
            }
            if (gPreview.tex) ImGui::Image((ImTextureID) (intptr_t) gPreview.tex, ImVec2(w, h));
            else ImGui::Dummy(ImVec2(w, h));
        } else if (gSceneOk) {
            viewport("view", ImVec2(w, h), shots[idx].cam);
        } else {
            ImGui::Dummy(ImVec2(w, h));
        }
        if (gGameView && gPreview.ready) cameraByMouse(imgPos, ImVec2(w, h));
        ImDrawList* dl = ImGui::GetWindowDrawList();
        dl->AddRect(imgPos, ImVec2(imgPos.x + w, imgPos.y + h), IM_COL32(18, 18, 18, 255));
        if (gSafe) {
            for (float k : {0.9f, 0.8f}) {
                float mx = w * (1 - k) / 2, my = h * (1 - k) / 2;
                dl->AddRect(ImVec2(imgPos.x + mx, imgPos.y + my), ImVec2(imgPos.x + w - mx, imgPos.y + h - my), IM_COL32(255, 255, 255, 90));
            }
            dl->AddLine(ImVec2(imgPos.x + w / 2, imgPos.y), ImVec2(imgPos.x + w / 2, imgPos.y + h), IM_COL32(255, 255, 255, 40));
            dl->AddLine(ImVec2(imgPos.x, imgPos.y + h / 2), ImVec2(imgPos.x + w, imgPos.y + h / 2), IM_COL32(255, 255, 255, 40));
        }
        (void) origin;
    }
    // the timecode on the left, the length on the right, the transport in the middle
    ImGui::Separator();
    ImVec2 c0 = ImGui::GetCursorPos();
    if (gBigFont) ImGui::PushFont(gBigFont);
    ImGui::PushStyleColor(ImGuiCol_Text, ImVec4(0.18f, 0.55f, 0.92f, 1));
    ImGui::TextUnformatted(timecode(gHead).c_str());
    ImGui::PopStyleColor();
    float tcW = ImGui::CalcTextSize("00:00:00:00").x;
    ImGui::SameLine(std::max(tcW + 40, ImGui::GetWindowWidth() - tcW - 20));
    ImGui::TextDisabled("%s", timecode(gLength).c_str());
    if (gBigFont) ImGui::PopFont();
    float cx = ImGui::GetWindowWidth() / 2;
    ImGui::SetCursorPos(ImVec2(cx - 150, c0.y));
    std::vector<Shot>& shots = cut().shots;
    if (ImGui::Button("|◀", ImVec2(48, 0))) {
        double prev = 0;
        for (const Shot& sh : shots) if (sh.t0 < gHead - 0.05 && sh.t0 > prev) prev = sh.t0;
        seek(prev);
    }
    ImGui::SameLine();
    if (ImGui::Button("◀", ImVec2(48, 0))) seek(gHead - 0.04);
    ImGui::SameLine();
    if (ImGui::Button(gPlaying ? "❚❚" : "▶", ImVec2(60, 0))) gPlaying ? pause() : play();
    ImGui::SameLine();
    if (ImGui::Button("▶|", ImVec2(48, 0))) seek(gHead + 0.04);
    ImGui::SameLine();
    if (ImGui::Button("▶▶|", ImVec2(48, 0))) {
        double next = gLength;
        for (const Shot& sh : shots) if (sh.t0 > gHead + 0.05 && sh.t0 < next) next = sh.t0;
        seek(next);
    }
}

void monitorPanel() {
    if (gMonMax) return;
    ImGui::Begin("Монитор", nullptr, ImGuiWindowFlags_NoScrollbar | ImGuiWindowFlags_NoScrollWithMouse);
    monitorBody();
    ImGui::End();
}

// the monitor over the whole window (F11), for working with the picture large
void monitorFull() {
    if (!gMonMax) return;
    ImGuiViewport* vp = ImGui::GetMainViewport();
    ImGui::SetNextWindowPos(vp->WorkPos);
    ImGui::SetNextWindowSize(vp->WorkSize);
    ImGui::SetNextWindowFocus();
    ImGui::Begin("Монитор (во весь экран)", nullptr,
                 ImGuiWindowFlags_NoDecoration | ImGuiWindowFlags_NoMove | ImGuiWindowFlags_NoSavedSettings | ImGuiWindowFlags_NoDocking | ImGuiWindowFlags_NoScrollWithMouse);
    monitorBody();
    ImGui::End();
}

// the project: the scenes, and the shots of the chosen one
void projectPanel() {
    ImGui::Begin("Проект");
    ImGui::TextDisabled("СЦЕНЫ");
    for (int i = 0; i < (int) gCuts.size(); i++)
        if (ImGui::Selectable(gCuts[i].name.c_str(), i == gCut)) switchCut(i);
    if (ImGui::Button("+ сцена", ImVec2(-1, 0))) ImGui::OpenPopup("newcut");
    if (ImGui::BeginPopup("newcut")) {
        ImGui::TextDisabled("Что рисует сцена:");
        for (const Program& pr : gPrograms) {
            if (!ImGui::MenuItem(pr.title.c_str())) continue;
            gCuts.emplace_back();
            gCuts.back().program = pr.id;
            defaultShots(gCuts.back());
            gCuts.back().name = pr.title + " " + std::to_string(gCuts.size());
            ensureLayers(gCuts.back());
            switchCut((int) gCuts.size() - 1);
        }
        if (gPrograms.empty()) ImGui::TextDisabled("(игра ещё не ответила)");
        ImGui::EndPopup();
    }
    ImGui::Separator();
    ImGui::TextDisabled("КАДРЫ");
    std::vector<Shot>& shots = cut().shots;
    for (int i = 0; i < (int) shots.size(); i++) {
        char b[200];
        std::snprintf(b, sizeof b, "%d  %s##shot%d", i + 1, shots[i].name.c_str(), i);
        if (ImGui::Selectable(b, i == gSelShot)) {
            gSelShot = i;
            gFree = false;
            seek(shots[i].t0);
        }
        ImGui::SameLine(ImGui::GetWindowWidth() - 90);
        ImGui::TextDisabled("%s", timecode(shots[i].t0).substr(3, 5).c_str());
    }
    ImGui::Separator();
    ImGui::TextDisabled("СЛОИ (сверху вниз)");
    for (int i = (int) cut().layers.size() - 1; i >= 0; i--) {
        Layer& y = cut().layers[i];
        ImGui::PushID(i);
        ImGui::Checkbox("##vis", &y.visible);
        ImGui::SameLine();
        if (ImGui::Selectable(y.name.c_str(), i == gSelLayer)) {
            gSelLayer = i;
            gPreview.boxWanted = true;
            gPreview.boxId = y.id;
        }
        ImGui::PopID();
    }
    ImGui::Separator();
    ImGui::TextDisabled("СОБЫТИЯ: %d", (int) cut().events.size());
    ImGui::End();
}

// the properties of what is chosen (the effect controls of the editor)
void propsPanel() {
    ImGui::Begin("Свойства");
    if (gSelLayer >= 0 && gSelLayer < (int) cut().layers.size() && ImGui::CollapsingHeader("Слой", ImGuiTreeNodeFlags_DefaultOpen)) {
        Layer& y = cut().layers[gSelLayer];
        ImGui::Text("%s", y.name.c_str());
        ImGui::Checkbox("Виден", &y.visible);
        ImGui::SliderFloat("Сдвиг по X", &y.dx, -1.0f, 1.0f, "%.3f");
        ImGui::SliderFloat("Сдвиг по Y", &y.dy, -1.0f, 1.0f, "%.3f");
        ImGui::SliderFloat("Размер", &y.scale, 0.1f, 4.0f, "%.2f");
        ImGui::SliderFloat("Наклон", &y.rot, -180.0f, 180.0f, "%.1f°");
        ImGui::SliderFloat("Прозрачность", &y.alpha, 0.0f, 1.0f, "%.2f");
        if (ImGui::Button("Сбросить слой")) {
            y.dx = y.dy = 0;
            y.scale = 1;
            y.rot = 0;
            y.alpha = 1;
            y.visible = true;
        }
    }
    if (ImGui::CollapsingHeader("Кадр", ImGuiTreeNodeFlags_DefaultOpen)) shotInspector();
    if (ImGui::CollapsingHeader("Событие", ImGuiTreeNodeFlags_DefaultOpen)) eventInspector();
    ImGui::End();
}

void timelinePanel() {
    ImGui::Begin("Таймлайн", nullptr, ImGuiWindowFlags_NoScrollWithMouse);
    ImGui::Checkbox("Привязка к битам", &gSnap);
    ImGui::SameLine();
    ImGui::TextDisabled("двойной клик по дорожке — создать, колесо — прокрутка, Ctrl+колесо — масштаб");
    timeline();
    ImGui::End();
}

void storyboardPanel() {
    ImGui::Begin("Раскадровка");
    if (gSceneOk) storyboardStrip(ImGui::GetContentRegionAvail().y);
    ImGui::End();
}

void buildLayout(ImGuiID dock, ImVec2 size) {
    ImGui::DockBuilderRemoveNode(dock);
    ImGui::DockBuilderAddNode(dock, ImGuiDockNodeFlags_DockSpace);
    ImGui::DockBuilderSetNodeSize(dock, size);
    ImGuiID center = dock, left, right, bottom;
    left = ImGui::DockBuilderSplitNode(center, ImGuiDir_Left, 0.16f, nullptr, &center);
    right = ImGui::DockBuilderSplitNode(center, ImGuiDir_Right, 0.22f, nullptr, &center);
    bottom = ImGui::DockBuilderSplitNode(center, ImGuiDir_Down, 0.42f, nullptr, &center);
    ImGui::DockBuilderDockWindow("Проект", left);
    ImGui::DockBuilderDockWindow("Свойства", right);
    ImGui::DockBuilderDockWindow("Монитор", center);
    ImGui::DockBuilderDockWindow("Таймлайн", bottom);
    ImGui::DockBuilderDockWindow("Раскадровка", bottom);
    ImGui::DockBuilderFinish(dock);
}

void menuBar() {
    if (!ImGui::BeginMainMenuBar()) return;
    if (ImGui::BeginMenu("Файл")) {
        if (ImGui::MenuItem("Сохранить", "Ctrl+S")) saveProject();
        if (ImGui::MenuItem("Запустить бой отсюда")) launchFight(gHead);
        ImGui::EndMenu();
    }
    if (ImGui::BeginMenu("Правка")) {
        if (ImGui::MenuItem("Отменить", "Ctrl+Z", false, gHistAt > 0)) undo(-1);
        if (ImGui::MenuItem("Повторить", "Ctrl+Shift+Z", false, gHistAt + 1 < (int) gHist.size())) undo(+1);
        if (ImGui::MenuItem("Удалить событие", "Delete", false, gSelEv >= 0)) {
            cut().events.erase(cut().events.begin() + gSelEv);
            gSelEv = -1;
        }
        ImGui::EndMenu();
    }
    if (ImGui::BeginMenu("Сцена")) {
        if (ImGui::MenuItem("Загрузить события из игры заново")) {
            cut().events.clear();
            cut().imported = false;
        }
        if (ImGui::MenuItem("Собрать черновик динамики по музыке")) makeDraft();
        if (ImGui::MenuItem("Убрать черновик")) clearDraft();
        ImGui::EndMenu();
    }
    if (ImGui::BeginMenu("Вид")) {
        ImGui::MenuItem("Привязка к битам", nullptr, &gSnap);
        ImGui::MenuItem("Безопасные зоны", nullptr, &gSafe);
        ImGui::MenuItem("Вид из игры", nullptr, &gGameView);
        ImGui::MenuItem("Сетка (3D)", nullptr, &gLook.grid);
        if (ImGui::MenuItem("Сбросить раскладку")) gResetLayout = true;
        ImGui::EndMenu();
    }
    // the workspaces, in the middle of the bar
    float mid = ImGui::GetWindowWidth() / 2;
    ImGui::SetCursorPosX(mid - 150);
    ImGui::PushStyleColor(ImGuiCol_Button, gMode == 0 ? ImVec4(0.18f, 0.55f, 0.92f, 1) : ImVec4(0.2f, 0.2f, 0.2f, 1));
    if (ImGui::SmallButton("Раскадровка")) {
        gMode = 0;
        ImGui::SetWindowFocus("Раскадровка");
    }
    ImGui::PopStyleColor();
    ImGui::PushStyleColor(ImGuiCol_Button, gMode == 1 ? ImVec4(0.18f, 0.55f, 0.92f, 1) : ImVec4(0.2f, 0.2f, 0.2f, 1));
    if (ImGui::SmallButton("События и анимация")) {
        gMode = 1;
        ImGui::SetWindowFocus("Таймлайн");
    }
    ImGui::PopStyleColor();
    ImGui::SameLine(ImGui::GetWindowWidth() - 360);
    ImGui::TextDisabled("%s", gStatus.c_str());
    ImGui::SameLine(ImGui::GetWindowWidth() - 130);
    if (ImGui::SmallButton("▶ Бой отсюда")) launchFight(gHead);
    ImGui::EndMainMenuBar();
}

}  // namespace

int main(int argc, char** argv) {
    bool selftest = argc > 2 && std::strcmp(argv[1], "--selftest") == 0;
    bool uishot = argc > 2 && std::strcmp(argv[1], "--uishot") == 0;      // draws the whole window for some frames, writes a picture of it, quits
    bool previewtest = argc > 2 && std::strcmp(argv[1], "--previewtest") == 0;      // starts the game picture, asks one frame, writes it, quits      // draws the shots to picture files and quits (for checking)
    if (!glfwInit()) return 1;
    if (selftest || previewtest || uishot) glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
    if (uishot) glfwWindowHint(GLFW_MAXIMIZED, GLFW_FALSE);
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
    static std::string iniPath = uishot ? kRoot + "/layout_test.ini" : kRoot + "/layout.ini";
    if (uishot) std::remove(iniPath.c_str());
    bool haveLayout = std::ifstream(iniPath).good();
    io.IniFilename = iniPath.c_str();
    io.ConfigFlags |= ImGuiConfigFlags_DockingEnable;
    ImGui::StyleColorsDark();
    applyTheme();
    ImFont* font = io.Fonts->AddFontFromFileTTF("C:/Windows/Fonts/segoeui.ttf", 19.0f, nullptr, io.Fonts->GetGlyphRangesCyrillic());
    if (!font) io.Fonts->AddFontDefault();
    static const ImWchar symbols[] = {0x2190, 0x23FF, 0x25A0, 0x25FF, 0x2700, 0x27BF, 0};
    ImFontConfig merge;
    merge.MergeMode = true;
    io.Fonts->AddFontFromFileTTF("C:/Windows/Fonts/msyh.ttc", 19.0f, &merge, io.Fonts->GetGlyphRangesChineseSimplifiedCommon());
    static const ImWchar punct[] = {0x2010, 0x2027, 0x2030, 0x203A, 0};      // the dashes and quotes the Cyrillic set of the face leaves out
    io.Fonts->AddFontFromFileTTF("C:/Windows/Fonts/segoeui.ttf", 19.0f, &merge, punct);
    io.Fonts->AddFontFromFileTTF("C:/Windows/Fonts/seguisym.ttf", 17.0f, &merge, symbols);
    gBigFont = io.Fonts->AddFontFromFileTTF("C:/Windows/Fonts/segoeui.ttf", 30.0f, nullptr, io.Fonts->GetGlyphRangesDefault());
    ImGui_ImplGlfw_InitForOpenGL(win, true);
    ImGui_ImplOpenGL3_Init("#version 330");

    std::string sceneError;
    bool sceneOk = glOk && gScene.init(kRoot + "/data", sceneError);
    if (!glOk) sceneError = "Видеодрайвер не даёт OpenGL 3.3";
    loadProject();
    loadBeats("D:/The LOTOS/src/main/resources/assets/lotusblight/overlay/map_2.osu");
    gLastSaved = projectText();
    loadSong();
    if (!selftest) gPreview.start();
    if (previewtest) {
        for (int i = 0; i < 1200 && !gPreview.ready && !gPreview.failed; i++) Sleep(100);
        std::printf("state: %s\n", gPreview.state.c_str());
        if (!gPreview.ready) return 3;
        gPreview.ask(45.0, 960, 540, true);
        for (int i = 0; i < 300; i++) {
            Sleep(100);
            std::lock_guard<std::mutex> g(gPreview.lock);
            if (gPreview.fresh) {
                std::vector<unsigned char> rgba(gPreview.png.size());
                for (size_t i = 0; i + 3 < rgba.size(); i += 4) {
                    rgba[i] = gPreview.png[i + 2];
                    rgba[i + 1] = gPreview.png[i + 1];
                    rgba[i + 2] = gPreview.png[i];
                    rgba[i + 3] = 255;
                }
                stbi_write_png(argv[2], gPreview.rawW, gPreview.rawH, 4, rgba.data(), gPreview.rawW * 4);
                std::printf("frame written: %dx%d\n", gPreview.rawW, gPreview.rawH);
                break;
            }
        }
        // how many pictures a second the game's side can give through the pipe
        struct Case { int w, h; bool keep; } cases[] = {{960, 540, false}, {1920, 1080, false}, {1920, 1080, true}};
        for (Case c : cases) {
            int got = 0;
            DWORD t0 = GetTickCount();
            double t = 45.0;
            gPreview.keepLayers = c.keep;
            while (GetTickCount() - t0 < 4000) {
                gPreview.ask(t, c.w, c.h, true);
                {
                    std::lock_guard<std::mutex> g(gPreview.lock);
                    if (gPreview.fresh) {
                        gPreview.fresh = false;
                        got++;
                        t += 0.04;
                    }
                }
                Sleep(1);
            }
            std::printf("%dx%d keep=%d: %.1f pictures a second\n", c.w, c.h, c.keep ? 1 : 0, got / 4.0);
        }
        gPreview.stop();
        return 0;
    }
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
            if (ImGui::IsKeyPressed(ImGuiKey_Delete)) {
                if (gSelEv >= 0 && gSelEv < (int) cut().events.size()) {
                    cut().events.erase(cut().events.begin() + gSelEv);
                    gSelEv = -1;
                } else if (gSelLayer >= 0 && gSelLayer < (int) cut().layers.size()) {
                    cut().layers[gSelLayer].visible = false;
                }
            }
            if (!io.KeyCtrl && ImGui::IsKeyPressed(ImGuiKey_V, false)) gTool = 0;
            if (!io.KeyCtrl && ImGui::IsKeyPressed(ImGuiKey_C, false)) gTool = 1;
            if (io.KeyCtrl && ImGui::IsKeyPressed(ImGuiKey_S)) saveProject();
            if (io.KeyCtrl && ImGui::IsKeyPressed(ImGuiKey_Z)) undo(io.KeyShift ? +1 : -1);
            if (io.KeyCtrl && ImGui::IsKeyPressed(ImGuiKey_Y)) undo(+1);
            if (ImGui::IsKeyPressed(ImGuiKey_Home)) seek(0);
            if (ImGui::IsKeyPressed(ImGuiKey_End)) seek(gLength);
            if (ImGui::IsKeyPressed(ImGuiKey_LeftArrow)) seek(gHead - (io.KeyShift ? 1.0 : 0.04));
            if (ImGui::IsKeyPressed(ImGuiKey_RightArrow)) seek(gHead + (io.KeyShift ? 1.0 : 0.04));
            if (ImGui::IsKeyPressed(ImGuiKey_F)) gFree = !gFree;
            if (ImGui::IsKeyPressed(ImGuiKey_F11)) gMonMax = !gMonMax;
            if (gMonMax && ImGui::IsKeyPressed(ImGuiKey_Escape)) gMonMax = false;
        }
        if (gPlaying && gSoundOk) gHead = soundPos();
        if (!ImGui::IsMouseDown(0)) trackHistory();
        // whatever is changed is written at once, so the game's picture (which reads the file) follows
        if (ImGui::GetTime() - gLastEdit > (ImGui::IsMouseDown(0) || ImGui::IsMouseDown(1) ? 0.03 : 0.2)) {
            std::string now = projectText();
            if (now != gLastSaved && !uishot) {
                writeProject(now);
                gForceAsk = true;
            }
            gLastEdit = ImGui::GetTime();
        }
        if (gPlaying) gForceAsk = true;

        if (uishot && argc > 3 && ImGui::GetFrameCount() == 20) {                    // for the picture of the timeline
            gScroll = 36;
            gPxPerSec = 40;
        }
        if (uishot && argc > 3 && ImGui::GetFrameCount() == 40) ImGui::SetWindowFocus("Таймлайн");
        menuBar();
        ImGuiViewport* vp = ImGui::GetMainViewport();
        ImGuiID dock = ImGui::GetID("LotosDock");
        ImGui::DockSpaceOverViewport(dock, vp, ImGuiDockNodeFlags_None);
        if (!haveLayout || gResetLayout) {
            buildLayout(dock, vp->WorkSize);
            haveLayout = true;
            gResetLayout = false;
        }
        gSceneOk = sceneOk;
        monitorPanel();
        monitorFull();
        projectPanel();
        propsPanel();
        timelinePanel();
        storyboardPanel();

        static int frameNo = 0;
        ImGui::Render();
        int w, h;
        glfwGetFramebufferSize(win, &w, &h);
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        glViewport(0, 0, w, h);
        glClearColor(0.05f, 0.05f, 0.07f, 1);
        glClear(GL_COLOR_BUFFER_BIT);
        ImGui_ImplOpenGL3_RenderDrawData(ImGui::GetDrawData());
        bool allImported = true;
        for (const Cut& c : gCuts) allImported = allImported && c.imported;
        static int settled = 0;
        if (allImported && gPreview.fresh == false) settled++;
        if (uishot && ++frameNo >= 90 && ((allImported && settled > 25) || frameNo >= 2400)) {
            std::vector<unsigned char> px((size_t) w * h * 4);
            glReadBuffer(GL_BACK);
            glReadPixels(0, 0, w, h, GL_RGBA, GL_UNSIGNED_BYTE, px.data());
            std::printf("programs %zu, cuts %zu, imported %d, events %zu, frames %d, preview: %s\n", gPrograms.size(), gCuts.size(), (int) gCuts[0].imported, gCuts[0].events.size(), frameNo, gPreview.state.c_str());
            stbi_flip_vertically_on_write(1);
            stbi_write_png(argv[2], w, h, 4, px.data(), w * 4);
            break;
        }
        glfwSwapBuffers(win);
    }
    if (!uishot) saveProject();
    gPreview.stop();
    unloadSong();
    if (gEngineOk) ma_engine_uninit(&gEngine);
    ImGui_ImplOpenGL3_Shutdown();
    ImGui_ImplGlfw_Shutdown();
    ImGui::DestroyContext();
    glfwTerminate();
    return 0;
}
