#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Боевая лаборатория: одно окно, из которого запускается всё, что связано с боем Глитчера на рабочем столе.

Кнопки запускают сцену с ошибкой, песни, урок, интерлюдию, финал с нужного места, со звуком, как раньше делали .bat файлы.
Код пересобирается сам, когда исходники меняются (папка src/main/java/com/lotusblight/overlay и ресурсы), так что каждый
новый запуск уже с последними правками. Работающий бой не трогается: новая сборка кладётся в новую папку.
Только стандартный Python (tkinter), ничего ставить не нужно.
"""
from __future__ import annotations

import os
import queue
import shutil
import subprocess
import sys
import threading
import time
import tkinter as tk
from pathlib import Path
from tkinter import ttk

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent.parent                       # tools/FightLab -> repo root
SRC = ROOT / "src" / "main" / "java"
OVERLAY = SRC / "com" / "lotusblight" / "overlay"
RES = ROOT / "src" / "main" / "resources"
TRACKS = ROOT / "build" / "song-test"           # the decoded tracks the old .bat files used
VIDEO = ROOT / "art" / "video" / "finale.mp4"
OUT = ROOT / "build" / "fight-lab"
NO_WINDOW = 0x08000000
DETACHED = 0x00000008 | 0x00000200              # DETACHED_PROCESS | CREATE_NEW_PROCESS_GROUP: a started fight outlives this window

BG, PANEL, BTN, BTN_HI, FG, MUTED = "#0c0a16", "#151226", "#231e3f", "#33295f", "#ece9fa", "#8e89ad"
ACCENT, OK, WARN, BAD = "#7a5cff", "#4ade80", "#fbbf24", "#f87171"


def find_jdk() -> Path:
    gh = Path("D:/gradle-home")
    if not gh.exists():
        gh = Path.home() / ".gradle"
    cands = sorted(gh.glob("jdks/*/bin/java.exe")) + sorted(gh.glob("jdks/*/*/bin/java.exe"))
    for c in cands:
        if "17" in str(c):
            return c.parent.parent
    if cands:
        return cands[0].parent.parent
    jh = os.environ.get("JAVA_HOME")
    return Path(jh) if jh else Path("java")


def find_jars() -> list[str]:
    gh = Path("D:/gradle-home")
    if not gh.exists():
        gh = Path.home() / ".gradle"
    jars = []
    for name in ("jcodec", "jcodec-javase"):
        hit = sorted((gh / "caches" / "modules-2" / "files-2.1" / "org.jcodec" / name).rglob(f"{name}-0.2.5.jar"))
        if hit:
            jars.append(str(hit[0]))
    return jars


JDK = find_jdk()
JAVA = str(JDK / "bin" / "java.exe")
JAVAC = str(JDK / "bin" / "javac.exe")
JARS = find_jars()
NAMES_RU = {"phrases": "фразы", "snakes": "змеи", "columns": "колонки"}
ROOTS = ["ExitMain", "GameMain", "RhythmMain", "AnimTools"]


def newest_source() -> float:
    newest = 0.0
    for base in (OVERLAY, RES / "assets" / "lotusblight" / "overlay"):
        if base.exists():
            for p in base.rglob("*"):
                if p.is_file():
                    try:
                        newest = max(newest, p.stat().st_mtime)
                    except OSError:
                        pass
    return newest


class Lab:
    def __init__(self, root: tk.Tk):
        self.root = root
        root.title("Боевая лаборатория: Глитчер")
        root.configure(bg=BG)
        root.geometry("900x860")
        root.minsize(760, 560)
        self.classes: Path | None = None
        self.built_at = 0.0
        self.building = False
        self.procs: list[subprocess.Popen] = []
        self.auto = tk.BooleanVar(value=True)
        self.safe = tk.BooleanVar(value=False)
        self.start_s = tk.StringVar(value="0")
        self.mark_s = tk.StringVar(value="30")
        self.last_change = 0.0
        self.q: queue.Queue = queue.Queue()      # what worker threads want done in the window: only the main thread touches it
        self.log_lines: list[str] = []
        self._ui()
        self.latest_dir()
        self.pump()
        self.tick()

    # ------------------------------------------------------------------ the window

    def _ui(self):
        top = tk.Frame(self.root, bg=BG)
        top.pack(fill="x", padx=12, pady=(10, 4))
        self.status = tk.Label(top, text="…", bg=BG, fg=MUTED, font=("Segoe UI", 11, "bold"), anchor="w")
        self.status.pack(side="left")
        self.stop_btn = self._button(top, "СТОП (закрыть бой, вернуть окна)", self.stop_all, bg="#5a1a28")
        self.stop_btn.pack(side="right")
        self._button(top, "Пересобрать", lambda: self.rebuild(force=True)).pack(side="right", padx=6)

        opts = tk.Frame(self.root, bg=BG)
        opts.pack(fill="x", padx=12)
        tk.Checkbutton(opts, text="пересобирать сам при изменении кода", variable=self.auto, bg=BG, fg=FG, selectcolor=PANEL,
                       activebackground=BG, activeforeground=FG).pack(side="left")
        tk.Checkbutton(opts, text="безопасно: не сворачивать мои окна (чёрное окно вместо рабочего стола)", variable=self.safe, bg=BG, fg=FG,
                       selectcolor=PANEL, activebackground=BG, activeforeground=FG).pack(side="left", padx=14)

        mid = tk.Frame(self.root, bg=BG)
        mid.pack(fill="both", expand=True, padx=12, pady=6)
        canvas = tk.Canvas(mid, bg=BG, highlightthickness=0)
        scroll = ttk.Scrollbar(mid, orient="vertical", command=canvas.yview)
        self.body = tk.Frame(canvas, bg=BG)
        self.body.bind("<Configure>", lambda e: canvas.configure(scrollregion=canvas.bbox("all")))
        win = canvas.create_window((0, 0), window=self.body, anchor="nw")
        canvas.bind("<Configure>", lambda e: canvas.itemconfigure(win, width=e.width))
        canvas.configure(yscrollcommand=scroll.set)
        canvas.pack(side="left", fill="both", expand=True)
        scroll.pack(side="right", fill="y")
        canvas.bind_all("<MouseWheel>", lambda e: canvas.yview_scroll(int(-e.delta / 120), "units"))

        self._presets()

        bottom = tk.Frame(self.root, bg=BG)
        bottom.pack(fill="x", padx=12, pady=(0, 10))
        self.logbox = tk.Text(bottom, height=9, bg="#07060d", fg="#c9c4e8", insertbackground=FG, relief="flat", font=("Consolas", 9))
        self.logbox.pack(fill="x")
        self.logbox.configure(state="disabled")

    def _button(self, parent, text, cmd, bg=BTN, width=None):
        b = tk.Button(parent, text=text, command=cmd, bg=bg, fg=FG, activebackground=BTN_HI, activeforeground=FG, relief="flat",
                      bd=0, padx=10, pady=6, font=("Segoe UI", 10), cursor="hand2", width=width)
        b.bind("<Enter>", lambda e: b.configure(bg=BTN_HI if bg == BTN else bg))
        b.bind("<Leave>", lambda e: b.configure(bg=bg))
        return b

    def _group(self, title, note=""):
        box = tk.LabelFrame(self.body, text=" " + title + " ", bg=PANEL, fg=ACCENT, font=("Segoe UI", 10, "bold"), bd=1, relief="solid",
                            labelanchor="nw")
        box.pack(fill="x", pady=5)
        if note:
            tk.Label(box, text=note, bg=PANEL, fg=MUTED, font=("Segoe UI", 9), anchor="w", justify="left", wraplength=800).pack(fill="x", padx=8, pady=(4, 0))
        inner = tk.Frame(box, bg=PANEL)
        inner.pack(fill="x", padx=6, pady=6)
        return inner

    def _row(self, inner, items):
        for i, (label, fn) in enumerate(items):
            if not label:
                continue                         # an empty slot keeps the rows lined up
            self._button(inner, label, fn).grid(row=i // 4, column=i % 4, padx=3, pady=3, sticky="ew")
        for c in range(4):
            inner.grid_columnconfigure(c, weight=1)

    # ------------------------------------------------------------------ what can be started

    def _presets(self):
        g = self._group("Разметка под музыку (песня 2)",
                        "Слушай песню и жми ПРОБЕЛ или КЛИК мышью на каждой нужной секунде. Время пишется в файл. Esc: пауза, Alt+F4: выход. "
                        "Потом «Принять»: фразы запишутся в phrases.txt, змеи в snakes.txt.")
        row = tk.Frame(g, bg=PANEL)
        row.grid(row=0, column=0, columnspan=4, sticky="w", pady=(0, 4))
        tk.Label(row, text="Начать песню с секунды:", bg=PANEL, fg=FG).pack(side="left")
        tk.Entry(row, textvariable=self.mark_s, width=6, bg=BTN, fg=FG, insertbackground=FG, relief="flat").pack(side="left", padx=6)
        g2 = tk.Frame(g, bg=PANEL)
        g2.grid(row=1, column=0, columnspan=4, sticky="ew")
        self._row(g2, [("Разметить ФРАЗЫ (чёрные экраны)", lambda: self.run_record("phrases")), ("Разметить ЗМЕЙ", lambda: self.run_record("snakes")),
                       ("Разметить КОЛОНКИ (有 / 线)", lambda: self.run_record("columns")), ("", lambda: None),
                       ("Показать записанное: фразы", lambda: self.show_taps("phrases")), ("Показать записанное: змеи", lambda: self.show_taps("snakes")),
                       ("Показать записанное: колонки", lambda: self.show_taps("columns")), ("", lambda: None),
                       ("ПРИНЯТЬ фразы", self.accept_phrases), ("ПРИНЯТЬ змей", self.accept_snakes), ("ПРИНЯТЬ колонки", self.accept_columns)])
        g = self._group("Весь бой", "Как в настоящей игре: сцена с ошибкой, потом три песни, урок, интерлюдия, финал. Со звуком и видео.")
        self._row(g, [("Весь бой", lambda: self.run_exit(chromo=False)), ("Весь бой (Хромо)", lambda: self.run_exit(chromo=True)),
                      ("Только сцена с ошибкой", lambda: self.run_exit(fight=False))])
        g = self._group("Сцена с ошибкой «Не найден файл Glitcher_.jar»", "Начать с нужной стадии (дальше идёт до конца и в бой).")
        self._row(g, [(t, (lambda s=s: self.run_exit(stage=s))) for t, s in [
            ("С начала", None), ("Разбитие окна", "SHATTER"), ("Выход из щели", "EMERGE"), ("Окна Архитекторов", "WINDOWS"),
            ("Вторая ошибка", "ERROR2"), ("Печать «Me=Deleted?»", "TYPE"), ("Топот", "STOMP")]])

        g = self._group("Песня 1", "С нужного места. Событие -> с какой секунды:")
        self._row(g, [(f"{t}  ({s} с)", (lambda s=s: self.run_song1(s))) for t, s in [
            ("С начала", 0), ("Стук по стеклу", 11), ("Сбор иконок", 23), ("Aero + броски", 38), ("Гнев", 68), ("Топот", 77),
            ("Поле глаз", 80), ("Глаза отъезжают", 91), ("Возврат", 103), ("Взгляд", 110), ("Распад", 120)]])
        self._row(g, [("Песня 1 Хромо (бой с песни 1)", lambda: self.run_game(chromo=True))])

        g = self._group("Песня 2 и урок Хончо")
        self._row(g, [("Урок Хончо", lambda: self.run_game(lesson=True)), ("Песня 2 с начала", lambda: self.run_game(start2=0)),
                      ("Песня 2: окна, 0:55", lambda: self.run_game(start2=55)), ("Песня 2: разрыв, 1:17", lambda: self.run_game(start2=77)),
                      ("Песня 2 Хромо", lambda: self.run_game(start2=0, chromo=True))])

        g = self._group("Интерлюдия, песня 3, финал")
        self._row(g, [("Интерлюдия с начала", lambda: self.run_game(cutscene=0)), ("Интерлюдия: удушение, 0:18", lambda: self.run_game(cutscene=18)),
                      ("Песня 3 с начала", lambda: self.run_game(start3=0)), ("Песня 3: мир крутится, 0:41", lambda: self.run_game(start3=41)),
                      ("Песня 3: глаза смотрят, 1:08", lambda: self.run_game(start3=68)), ("Песня 3: экран в руках, 1:34", lambda: self.run_game(start3=94)),
                      ("Песня 3: туннель, 1:47", lambda: self.run_game(start3=107)), ("Песня 3: разрыв экрана, 2:25", lambda: self.run_game(start3=145)),
                      ("Финал", lambda: self.run_game(finale=0))])

        g = self._group("С любой секунды")
        row = tk.Frame(g, bg=PANEL)
        row.grid(row=0, column=0, columnspan=4, sticky="w")
        tk.Label(row, text="Секунды:", bg=PANEL, fg=FG).pack(side="left")
        tk.Entry(row, textvariable=self.start_s, width=7, bg=BTN, fg=FG, insertbackground=FG, relief="flat").pack(side="left", padx=6)
        self._button(row, "Песня 1", lambda: self.run_song1(self._sec())).pack(side="left", padx=3)
        self._button(row, "Песня 2", lambda: self.run_game(start2=self._sec())).pack(side="left", padx=3)
        self._button(row, "Песня 3", lambda: self.run_game(start3=self._sec())).pack(side="left", padx=3)
        self._button(row, "Интерлюдия", lambda: self.run_game(cutscene=self._sec())).pack(side="left", padx=3)
        self._button(row, "Финал", lambda: self.run_game(finale=self._sec())).pack(side="left", padx=3)

    def _sec(self) -> float:
        try:
            return max(0.0, float(self.start_s.get().replace(",", ".")))
        except ValueError:
            return 0.0

    # ------------------------------------------------------------------ commands

    def _classpath(self) -> str | None:
        if self.classes is None:
            return None
        return ";".join([str(self.classes), str(RES)] + JARS)

    def _tracks(self, song3=True, interlude=True, contact=True, video=True) -> list[str]:
        a = ["--song1", str(TRACKS / "song1.wav"), "--song2", str(TRACKS / "song2.wav")]
        if song3:
            a += ["--song3", str(TRACKS / "song3.wav")]
        if interlude:
            a += ["--interlude", str(TRACKS / "interlude.wav")]
        if contact:
            a += ["--contact", str(TRACKS / "contact.wav")]
        if video and VIDEO.exists():
            a += ["--video", str(VIDEO)]
        return a

    def run_exit(self, stage=None, fight=True, chromo=False):
        args = ["--jar", "lotusblight-1.8000.jar"] + self._tracks()
        if stage:
            args += ["--from", stage]
        if not fight:
            args += ["--no-fight"]
        if chromo:
            args += ["--chromo"]
        if self.safe.get():
            args += ["--no-minimize"]
        self.launch("com.lotusblight.overlay.ExitMain", args, f"Сцена/бой{' с ' + stage if stage else ''}")

    # ------------------------------------------------------------------ marking to the music

    def _taps_file(self, name: str) -> Path:
        return TRACKS / f"taps_{name}.txt"                 # must stay inside "song-test": the game writes only there

    def _read_taps(self, name: str) -> list[float]:
        f = self._taps_file(name)
        if not f.exists():
            return []
        out = []
        for line in f.read_text(encoding="utf-8", errors="replace").splitlines():
            try:
                out.append(float(line.strip().replace(",", ".")))
            except ValueError:
                pass
        return sorted(out)

    def run_record(self, name: str):
        try:
            start = max(0.0, float(self.mark_s.get().replace(",", ".")))
        except ValueError:
            start = 0.0
        args = ["--song1", str(TRACKS / "song1.wav"), "--song2", str(TRACKS / "song2.wav"), "--start2", str(start), "--record", str(self._taps_file(name))]
        self.launch("com.lotusblight.overlay.GameMain", args, f"Разметка: {NAMES_RU.get(name, name)} (с {start:g} с)")

    def show_taps(self, name: str):
        taps = self._read_taps(name)
        if not taps:
            self.say("Пока ничего не записано.")
            return
        sec = [t / 1000 for t in taps]
        shown = ", ".join(f"{x:.2f}" for x in sec[:40]) + (" …" if len(sec) > 40 else "")
        self.say(f"Записано {len(sec)} нажатий ({NAMES_RU.get(name, name)}), секунды: {shown}")

    def accept_phrases(self):
        taps = self._read_taps("phrases")
        target = RES / "assets" / "lotusblight" / "overlay" / "phrases.txt"
        old = [l for l in target.read_text(encoding="utf-8").splitlines() if l.strip() and not l.startswith("#")]
        texts = [l.split("\t", 1)[1] for l in old if "\t" in l]
        if not taps:
            self.say("Сначала размети фразы.")
            return
        if len(taps) == 2 * len(texts):
            rows = [(taps[2 * i], taps[2 * i + 1], texts[i]) for i in range(len(texts))]          # two taps a phrase: where it starts, where it ends
        elif len(taps) == len(texts):
            starts = [float(l.split("	")[0]) * 1000 for l in old if "	" in l]                   # one tap a phrase: the ends; the starts stay as they are
            rows = [(st, t, txt) for st, t, txt in zip(starts, taps, texts)]
        else:
            self.say(f"Нажатий {len(taps)}, а фраз {len(texts)}. Нужно по ОДНОМУ нажатию (конец фразы) или по два (начало и конец), по порядку: " + " | ".join(texts))
            return
        OUT.mkdir(parents=True, exist_ok=True)
        shutil.copy2(target, OUT / "phrases.txt.bak")
        target.write_text("".join(f"{a / 1000:.2f}\t{txt}" + (f"\t{b / 1000:.2f}" if b is not None else "") + "\n" for a, b, txt in rows), encoding="utf-8")
        self.say("Фразы приняты: " + ", ".join(f"{a / 1000:.2f}" + (f"-{b / 1000:.2f}" if b is not None else "") for a, b, _ in rows) + ". Копия старого: build/fight-lab/phrases.txt.bak. Пересоберу сам.")

    def accept_columns(self):
        taps = [t / 1000 for t in self._read_taps("columns")]
        if not taps:
            self.say("Сначала размети колонки.")
            return
        # One tap = one more sign in the columns; a pause of more than 0.8 s starts a new series. A tap far closer to the one before it than the
        # usual step is a slip of the finger and is dropped.
        steps = sorted(b - a for a, b in zip(taps, taps[1:]) if b - a < 0.8)
        med = steps[len(steps) // 2] if steps else 0.3
        clean, dropped = [taps[0]], []
        for t in taps[1:]:
            if t - clean[-1] < med * 0.75:
                dropped.append(t)
            else:
                clean.append(t)
        series = [[clean[0]]]
        for a, b in zip(clean, clean[1:]):
            if b - a > 0.8:
                series.append([b])
            else:
                series[-1].append(b)
        target = RES / "assets" / "lotusblight" / "overlay" / "columns.txt"
        text = "# секунды: когда в колонках по краям экрана появляется следующий знак (размечено автором под музыку). Пустая строка: колонки сбросились.\n"
        text += "\n".join("".join(f"{t:.3f}\n" for t in sr) for sr in series)
        target.write_text(text, encoding="utf-8")
        self.say(f"Колонки приняты: серий {len(series)}, знаков " + ", ".join(str(len(sr)) for sr in series) + f", шаг {med:.2f} с."
                 + (f" Лишние нажатия убраны: {', '.join(f'{d:.2f}' for d in dropped)}." if dropped else ""))

    def accept_snakes(self):
        taps = self._read_taps("snakes")
        if not taps:
            self.say("Сначала размети змей.")
            return
        target = RES / "assets" / "lotusblight" / "overlay" / "snakes.txt"
        target.write_text("# секунды, когда из телика вылетают змеи (размечено автором под музыку)\n" + "".join(f"{t / 1000:.3f}\n" for t in taps), encoding="utf-8")
        self.say(f"Змеи приняты: {len(taps)} моментов записано в snakes.txt.")

    def run_song1(self, at):
        args = ["--song", "1", "--audio", str(TRACKS / "song1.wav"), "--at", str(at)]
        if self.safe.get():
            args += ["--window"]
        self.launch("com.lotusblight.overlay.RhythmMain", args, f"Песня 1 с {at} с")

    def run_game(self, start2=None, start3=None, cutscene=None, finale=None, lesson=False, chromo=False, extra=None):
        args = self._tracks(song3=True, interlude=True, contact=True, video=True)
        if start2 is not None:
            args += ["--start2", str(start2)]
        if start3 is not None:
            args += ["--start3", str(start3)]
        if cutscene is not None:
            args += ["--cutscene", str(cutscene)]
        if finale is not None:
            args += ["--finale", str(finale)]
        if lesson:
            args += ["--lesson"]
        if chromo:
            args += ["--chromo"]
        if not self.safe.get():
            args += ["--minimize"]
        title = "Бой: " + ", ".join(k for k, v in [("песня 2", start2), ("песня 3", start3), ("интерлюдия", cutscene), ("финал", finale)] if v is not None)
        self.launch("com.lotusblight.overlay.GameMain", args + (extra or []), title or "Бой")

    # ------------------------------------------------------------------ running

    def launch(self, main: str, args: list[str], title: str):
        if self.building:
            self.say("Идёт сборка, подожди несколько секунд и нажми снова.")
            return
        if self.stale():
            self.say("Код изменился: пересобираю перед запуском…")
            self.rebuild(force=True, then=lambda: self.launch(main, args, title))
            return
        cp = self._classpath()
        if cp is None:
            self.say("Ещё нет сборки: нажми «Пересобрать».")
            return
        missing = [p for p in [TRACKS / "song1.wav", TRACKS / "song2.wav"] if not p.exists()]
        if missing:
            self.say("Нет дорожек для боя: " + ", ".join(str(m) for m in missing))
            return
        cmd = [JAVA, "-Xmx1500m", "-cp", cp, main] + args
        log = OUT / "last_run.log"
        OUT.mkdir(parents=True, exist_ok=True)
        try:
            out = open(log, "wb")
            p = subprocess.Popen(cmd, cwd=str(ROOT), stdout=out, stderr=subprocess.STDOUT, creationflags=DETACHED)
        except Exception as e:  # noqa: BLE001
            self.say(f"Не удалось запустить: {e}")
            return
        self.procs.append(p)
        self.say(f"Запущено: {title}. Выход: Alt+F4, Esc в песнях (пауза) или кнопка «СТОП» тут.")
        threading.Thread(target=self._watch_proc, args=(p, log, title), daemon=True).start()

    def _watch_proc(self, p, log, title):
        t0 = time.time()
        code = p.wait()
        took = time.time() - t0
        tail = ""
        try:
            tail = log.read_text(encoding="utf-8", errors="replace").strip()[-900:]
        except OSError:
            pass
        if code != 0 and took < 20:
            self.q.put(lambda: self.say(f"«{title}» завершился сразу (код {code}):\n{tail}"))
        else:
            self.q.put(lambda: self.say(f"«{title}» закрыт ({int(took)} с)."))

    def stop_all(self):
        for p in list(self.procs):
            if p.poll() is None:
                subprocess.run(["taskkill", "/PID", str(p.pid), "/T", "/F"], creationflags=NO_WINDOW, capture_output=True)
        self.procs.clear()
        # the windows the fight minimized come back
        subprocess.Popen(["powershell", "-NoProfile", "-NonInteractive", "-Command", "(New-Object -ComObject Shell.Application).UndoMinimizeALL()"],
                         creationflags=NO_WINDOW)
        self.say("Остановлено, окна возвращены.")

    # ------------------------------------------------------------------ building

    def latest_dir(self):
        OUT.mkdir(parents=True, exist_ok=True)
        dirs = sorted([d for d in OUT.glob("classes-*") if (d / "com").exists()], key=lambda d: d.stat().st_mtime)
        if dirs:
            self.classes = dirs[-1]
            self.built_at = dirs[-1].stat().st_mtime

    def stale(self) -> bool:
        return self.classes is None or newest_source() > self.built_at

    def rebuild(self, force=False, then=None):
        if self.building:
            return
        self.building = True
        self.say("Собираю из исходников…")
        threading.Thread(target=self._build, args=(then,), daemon=True).start()

    def _build(self, then):
        stamp = time.time()
        out = OUT / f"classes-{int(stamp)}"
        prev = self.classes
        incremental = prev is not None and (prev / "com").exists()
        if incremental:
            # Start from the last build: javac then recompiles only the sources that are newer than their classes (and what they need).
            shutil.copytree(prev, out)
        else:
            out.mkdir(parents=True, exist_ok=True)
        files = [str(f) for f in sorted(OVERLAY.glob("*.java"))]          # every source of the package: javac does not look for what changed behind classes it thinks are up to date
        cmd = [JAVAC, "-encoding", "UTF-8", "-nowarn", "-cp", ";".join([str(out), str(RES)] + JARS), "-sourcepath", str(SRC), "-d", str(out)] + files
        try:
            r = subprocess.run(cmd, cwd=str(ROOT), capture_output=True, creationflags=NO_WINDOW, timeout=300)
            text = (r.stdout + r.stderr).decode("utf-8", errors="replace")
            ok = r.returncode == 0
        except Exception as e:  # noqa: BLE001
            ok, text = False, str(e)
        shown = "\n".join(l for l in text.splitlines() if not l.startswith("Note:"))[-1500:]

        def done():
            self.building = False
            if ok:
                self.classes, self.built_at = out, stamp
                self.say("Собрано. " + time.strftime("%H:%M:%S"))
                for old in sorted(OUT.glob("classes-*"), key=lambda d: d.stat().st_mtime)[:-3]:
                    subprocess.run(["cmd", "/c", "rmdir", "/s", "/q", str(old)], creationflags=NO_WINDOW, capture_output=True)
                if then:
                    then()
            else:
                self.say("СБОРКА НЕ УДАЛАСЬ:\n" + shown)
        self.q.put(done)

    def pump(self):
        """Runs what the worker threads queued, then looks again soon."""
        try:
            while True:
                self.q.get_nowait()()
        except queue.Empty:
            pass
        self.root.after(200, self.pump)

    def tick(self):
        stale = self.stale()
        running = sum(1 for p in self.procs if p.poll() is None)
        if self.building:
            self.status.configure(text="⏳ собираю…", fg=WARN)
        elif stale:
            self.status.configure(text="● код изменился" + (" (пересоберу сам)" if self.auto.get() else " (нажми «Пересобрать»)"), fg=WARN)
            if self.auto.get():
                if self.last_change == 0:
                    self.last_change = time.time()
                elif time.time() - self.last_change > 1.5:
                    self.last_change = 0
                    self.rebuild()
        else:
            self.last_change = 0
            self.status.configure(text="● актуально" + (f"   ·   запущено: {running}" if running else ""), fg=OK)
        self.root.after(1000, self.tick)

    def say(self, text: str):
        self.log_lines.append(time.strftime("%H:%M:%S ") + text)
        self.log_lines = self.log_lines[-60:]
        self.logbox.configure(state="normal")
        self.logbox.delete("1.0", "end")
        self.logbox.insert("end", "\n".join(self.log_lines))
        self.logbox.see("end")
        self.logbox.configure(state="disabled")


def main():
    if "--selftest" in sys.argv:
        print("JAVA", JAVA, Path(JAVA).exists())
        print("JARS", JARS)
        print("tracks", [(p.name, p.exists()) for p in [TRACKS / "song1.wav", TRACKS / "song2.wav", TRACKS / "song3.wav", TRACKS / "interlude.wav", TRACKS / "contact.wav", VIDEO]])
        print("newest source", newest_source())
        return
    root = tk.Tk()
    lab = Lab(root)
    if lab.stale():
        lab.rebuild()
    root.mainloop()


if __name__ == "__main__":
    main()
