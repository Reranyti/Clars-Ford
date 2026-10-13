#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Lotos Studio: a local helper for the Lotos mod.

Runs a small web server on 127.0.0.1 only (nothing leaves this computer) and serves one page with:
  * the episode editor (storyboard: shots, camera, light, sound, text, references, confirm/redo marks),
  * scene and shader checks (compile, run on screenshots, before/after, scene frames),
  * the mod panel (build, copy to mods, git, disk space, logs).
Only python's standard library is used, so nothing has to be downloaded.
"""
from __future__ import annotations

import datetime
import json
import mimetypes
import os
import re
import shutil
import subprocess
import sys
import threading
import time
import uuid
import webbrowser
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import parse_qs, unquote, urlparse

HERE = Path(__file__).resolve().parent
STATIC = HERE / "static"
DATA = HERE / "data"
PROJECT = HERE.parent.parent          # tools/lotos-studio -> repo root
for _d in ("episodes", "history", "refs", "checks", "docs", "backups"):
    (DATA / _d).mkdir(parents=True, exist_ok=True)

ID_RE = re.compile(r"^[a-z0-9_-]{1,40}$")
NO_WINDOW = 0x08000000 if os.name == "nt" else 0
# hot swap: "since" = when the running game was started (classes compiled earlier are what it has), "watch" = auto-apply on
HOT: dict = {"since": 0, "watch": False, "applied_mtime": 0.0, "thread": None}


# ------------------------------------------------------------------ config

def find_java(gradle_home: Path) -> str:
    cands = list(gradle_home.glob("jdks/*/bin/java.exe")) + list(gradle_home.glob("jdks/*/*/bin/java.exe"))
    for c in cands:
        if "17" in str(c):
            return str(c.parent.parent)
    if cands:
        return str(cands[0].parent.parent)
    return os.environ.get("JAVA_HOME", "")


def find_lwjgl(gradle_home: Path) -> list[str]:
    base = gradle_home / "caches" / "forge_gradle" / "maven_downloader" / "org" / "lwjgl"
    names = ["lwjgl-3.3.1.jar", "lwjgl-3.3.1-natives-windows.jar",
             "lwjgl-glfw-3.3.1.jar", "lwjgl-glfw-3.3.1-natives-windows.jar",
             "lwjgl-opengl-3.3.1.jar", "lwjgl-opengl-3.3.1-natives-windows.jar"]
    out = []
    for n in names:
        hit = list(base.rglob(n)) if base.exists() else []
        if hit:
            out.append(str(hit[0]))
    return out


def load_config() -> dict:
    gh = Path("D:/gradle-home")
    if not gh.exists():
        gh = Path.home() / ".gradle"
    cfg = {
        "project": str(PROJECT),
        "minecraft": str(Path(os.environ.get("APPDATA", str(Path.home()))) / ".minecraft"),
        "gradle_home": str(gh),
        "tmp": "D:/tmp" if Path("D:/").exists() else str(Path.home() / "tmp"),
        "java_home": "",
        "jar_name": "lotusblight-2.0008.jar",
        "port": 8765,
    }
    f = DATA / "config.json"
    if f.exists():
        try:
            cfg.update(json.loads(f.read_text(encoding="utf-8")))
        except Exception:
            pass
    if not cfg["java_home"]:
        cfg["java_home"] = find_java(Path(cfg["gradle_home"]))
    if os.environ.get("LOTOS_STUDIO_PORT"):                 # a second copy for testing, next to the one in use
        cfg["port"] = int(os.environ["LOTOS_STUDIO_PORT"])
    return cfg


CFG = load_config()


def build_env() -> dict:
    env = os.environ.copy()
    tmp = CFG["tmp"]
    Path(tmp).mkdir(parents=True, exist_ok=True)
    env["TEMP"] = env["TMP"] = tmp
    env["GRADLE_USER_HOME"] = CFG["gradle_home"]
    if CFG["java_home"]:
        env["JAVA_HOME"] = CFG["java_home"]
    env["JAVA_TOOL_OPTIONS"] = f"-Djava.io.tmpdir={tmp}"
    return env


def java_exe() -> str:
    jh = CFG["java_home"]
    return str(Path(jh) / "bin" / "java.exe") if jh else "java"


def status_report() -> dict:
    proj = Path(CFG["project"])
    mc = Path(CFG["minecraft"])
    lw = find_lwjgl(Path(CFG["gradle_home"]))
    return {
        "config": CFG,
        "checks": {
            "project": proj.exists(),
            "gradlew": (proj / "gradlew.bat").exists(),
            "minecraft": mc.exists(),
            "mods_dir": (mc / "mods").exists(),
            "java": bool(CFG["java_home"]) and Path(java_exe()).exists(),
            "lwjgl": len(lw) == 6,
            "shaderpack": (proj / "shaderpack" / "LotosCinema" / "shaders").exists(),
            "classes": (proj / "build" / "classes" / "java" / "main").exists(),
        },
    }


# ------------------------------------------------------------------ episodes

def new_shot(title="Новый кадр") -> dict:
    return {
        "id": uuid.uuid4().hex[:8], "title": title, "location": "", "characters": [], "action": "",
        "size": "Средний план", "angle": "На уровне глаз", "move": "Статика",
        "lightPreset": "Холодный фиолет (как 13-я концовка)", "keyColor": "#6a4cff", "fillColor": "#0a0818",
        "contrast": 70, "duration": 4, "sound": "", "text": "", "emotion": "", "transition": "Резкая склейка",
        "notes": "", "refs": [], "status": "draft", "comment": "",
    }


def default_episode(eid: str, title: str) -> dict:
    return {"id": eid, "title": title, "notes": "", "shots": [new_shot("Вступление")]}


def episode_path(eid: str) -> Path:
    return DATA / "episodes" / f"{eid}.json"


def load_episode(eid: str) -> dict:
    p = episode_path(eid)
    if p.exists():
        return json.loads(p.read_text(encoding="utf-8"))
    ep = default_episode(eid, "Эпизод 1" if eid == "episode1" else eid)
    p.write_text(json.dumps(ep, ensure_ascii=False, indent=2), encoding="utf-8")
    return ep


STATUS_MARK = {"draft": "✎ черновик", "ask": "? нужно подтвердить", "ok": "✔ подтверждено", "redo": "✖ переделать"}


def to_markdown(ep: dict) -> str:
    shots = ep.get("shots", [])
    total = sum(float(s.get("duration") or 0) for s in shots)
    lines = [f"# {ep.get('title', 'Эпизод')}", "",
             f"Кадров: {len(shots)}. Общая длительность: {total:g} с.", ""]
    if ep.get("notes"):
        lines += [ep["notes"], ""]
    for i, s in enumerate(shots, 1):
        lines += [f"## Кадр {i}. {s.get('title', '')}", "",
                  f"- Статус: {STATUS_MARK.get(s.get('status'), s.get('status', ''))}",
                  f"- Место: {s.get('location', '') or '—'}",
                  f"- В кадре: {', '.join(s.get('characters', [])) or '—'}",
                  f"- Что происходит: {s.get('action', '') or '—'}",
                  f"- Камера: {s.get('size', '')}, {s.get('angle', '')}, {s.get('move', '')}",
                  f"- Свет: {s.get('lightPreset', '')} (основной {s.get('keyColor', '')}, фон {s.get('fillColor', '')}, контраст {s.get('contrast', '')})",
                  f"- Эмоция: {s.get('emotion', '') or '—'}",
                  f"- Длительность: {s.get('duration', '')} с. Переход: {s.get('transition', '')}",
                  f"- Звук: {s.get('sound', '') or '—'}",
                  f"- Текст/субтитр: {s.get('text', '') or '—'}"]
        if s.get("notes"):
            lines.append(f"- Заметки: {s['notes']}")
        if s.get("comment"):
            lines.append(f"- Комментарий автора: {s['comment']}")
        if s.get("refs"):
            lines.append("- Референсы: " + ", ".join(f"refs/{ep['id']}/{r}" for r in s["refs"]))
        lines.append("")
    return "\n".join(lines)


def save_episode(eid: str, ep: dict) -> dict:
    ep["id"] = eid
    if not isinstance(ep.get("shots"), list):
        raise ValueError("shots must be a list")
    text = json.dumps(ep, ensure_ascii=False, indent=2)
    p = episode_path(eid)
    if p.exists() and p.read_text(encoding="utf-8") == text:
        return {"ok": True, "changed": False}
    if p.exists():
        stamp = datetime.datetime.now().strftime("%Y%m%d_%H%M%S")
        shutil.copy2(p, DATA / "history" / f"{eid}_{stamp}.json")
        old = sorted((DATA / "history").glob(f"{eid}_*.json"))
        for f in old[:-40]:
            f.unlink(missing_ok=True)
    p.write_text(text, encoding="utf-8")
    # export where the project (and Claude) can read it
    out = PROJECT / "art" / "episodes"
    (out / "refs" / eid).mkdir(parents=True, exist_ok=True)
    (out / f"{eid}.json").write_text(text, encoding="utf-8")
    (out / f"{eid}.md").write_text(to_markdown(ep), encoding="utf-8")
    for s in ep["shots"]:
        for r in s.get("refs", []):
            src = DATA / "refs" / Path(r).name
            if src.exists():
                shutil.copy2(src, out / "refs" / eid / Path(r).name)
    return {"ok": True, "changed": True, "exported": str(out / f"{eid}.md")}


def list_episodes() -> list:
    res = []
    for f in sorted((DATA / "episodes").glob("*.json")):
        try:
            ep = json.loads(f.read_text(encoding="utf-8"))
            res.append({"id": f.stem, "title": ep.get("title", f.stem), "shots": len(ep.get("shots", [])),
                        "duration": sum(float(s.get("duration") or 0) for s in ep.get("shots", []))})
        except Exception:
            continue
    if not res:
        load_episode("episode1")
        return list_episodes()
    return res


# ------------------------------------------------------------------ jobs

class Job:
    def __init__(self, kind: str, title: str):
        self.id = uuid.uuid4().hex[:10]
        self.kind = kind
        self.title = title
        self.lines: list[str] = []
        self.status = "running"
        self.images: list[dict] = []
        self.started = time.time()
        self.lock = threading.Lock()
        self.proc = None            # the process this job started (so it can be stopped)

    def log(self, s: str):
        with self.lock:
            self.lines.append(s)
            if len(self.lines) > 6000:
                del self.lines[:1000]

    def to_json(self, since: int = 0) -> dict:
        with self.lock:
            return {"id": self.id, "kind": self.kind, "title": self.title, "status": self.status,
                    "lines": self.lines[since:], "next": len(self.lines), "images": self.images,
                    "seconds": round(time.time() - self.started, 1)}


JOBS: dict[str, Job] = {}


def run_cmd(job: Job, args: list[str], cwd: str | None = None, env: dict | None = None) -> int:
    job.log("$ " + " ".join(f'"{a}"' if " " in a else a for a in args))
    try:
        p = subprocess.Popen(args, cwd=cwd, env=env or build_env(), stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                             creationflags=NO_WINDOW)
    except Exception as e:  # noqa: BLE001
        job.log(f"Не удалось запустить: {e}")
        return 127
    job.proc = p
    assert p.stdout is not None
    for raw in iter(p.stdout.readline, b""):
        line = raw.decode("utf-8", errors="replace").rstrip()
        if "Picked up JAVA_TOOL_OPTIONS" in line or not line.strip():
            continue
        job.log(line)
    return p.wait()


def lwjgl_cp() -> str:
    return ";".join(find_lwjgl(Path(CFG["gradle_home"])))


def shaders_dir() -> Path:
    return Path(CFG["project"]) / "shaderpack" / "LotosCinema" / "shaders"


def job_disk(job: Job, p: dict) -> bool:
    for letter in "CD":
        root = f"{letter}:\\"
        if Path(root).exists():
            u = shutil.disk_usage(root)
            job.log(f"{letter}: свободно {u.free / 2**30:.1f} ГБ из {u.total / 2**30:.0f} ГБ ({100 * u.free / u.total:.0f}% свободно)")
            if u.free < 3 * 2**30:
                job.log(f"  ВНИМАНИЕ: на {letter}: мало места, лаунчер и сборка могут падать (нужно хотя бы 3 ГБ).")
    return True


def job_git_status(job: Job, p: dict) -> bool:
    proj = CFG["project"]
    a = run_cmd(job, ["git", "-C", proj, "status", "--short", "-b"])
    b = run_cmd(job, ["git", "-C", proj, "log", "--oneline", "-6"])
    return a == 0 and b == 0


ALLOWED_SCOPES = ["src", "shaderpack", "art", "tools", "build.gradle", "gradle.properties", "README.md"]


def job_git_commit(job: Job, p: dict) -> bool:
    proj = CFG["project"]
    msg = (p.get("message") or "").strip()
    if not msg:
        job.log("Нужно написать сообщение коммита.")
        return False
    scope = [s for s in p.get("scope", ALLOWED_SCOPES) if s in ALLOWED_SCOPES and (Path(proj) / s).exists()]
    if not scope:
        job.log("Нечего добавлять: не выбрана ни одна папка.")
        return False
    if run_cmd(job, ["git", "-C", proj, "add", "--"] + scope) != 0:
        return False
    rc = run_cmd(job, ["git", "-C", proj, "commit", "-m", msg])
    if rc != 0:
        job.log("Коммит не создан (возможно, нет изменений).")
        return False
    if p.get("push"):
        return run_cmd(job, ["git", "-C", proj, "push", "origin", "HEAD"]) == 0
    return True


def job_git_push(job: Job, p: dict) -> bool:
    return run_cmd(job, ["git", "-C", CFG["project"], "push", "origin", "HEAD"]) == 0


def job_build(job: Job, p: dict) -> bool:
    gw = str(Path(CFG["project"]) / "gradlew.bat")
    rc = run_cmd(job, ["cmd", "/c", gw, "build", "-q", "--no-daemon", "-x", "test"], cwd=CFG["project"])
    jar = Path(CFG["project"]) / "build" / "libs" / CFG["jar_name"]
    if jar.exists():
        job.log(f"Готово: {jar.name}, {jar.stat().st_size / 2**20:.1f} МБ, {datetime.datetime.fromtimestamp(jar.stat().st_mtime):%H:%M:%S}")
    return rc == 0 and jar.exists()


def job_copy_jar(job: Job, p: dict) -> bool:
    src = Path(CFG["project"]) / "build" / "libs" / CFG["jar_name"]
    dst = Path(CFG["minecraft"]) / "mods" / CFG["jar_name"]
    if not src.exists():
        job.log("Сначала собери мод: файла нет в build/libs.")
        return False
    try:
        shutil.copy2(src, dst)
    except PermissionError:
        job.log("Файл занят: закрой Minecraft и повтори.")
        return False
    job.log(f"Скопировано в {dst} ({dst.stat().st_size / 2**20:.1f} МБ)")
    return True


def job_build_copy(job: Job, p: dict) -> bool:
    return job_build(job, p) and job_copy_jar(job, p)


def job_shader_compile(job: Job, p: dict) -> bool:
    tool = Path(CFG["project"]) / "shaderpack" / "tools" / "CompileAll.java"
    if not CFG["java_home"] or not lwjgl_cp():
        job.log("Не найдены Java или LWJGL (см. вкладку «Состояние»).")
        return False
    rc = run_cmd(job, [java_exe(), "-cp", lwjgl_cp(), str(tool), str(shaders_dir())])
    return rc == 0 and any("ALL OK" in ln for ln in job.lines)


def job_shader_run(job: Job, p: dict) -> bool:
    tool = Path(CFG["project"]) / "shaderpack" / "tools" / "ShaderCheck.java"
    shots_dir = Path(CFG["minecraft"]) / "screenshots"
    names = [Path(n).name for n in p.get("names", [])]
    if not names:
        names = [f.name for f in sorted(shots_dir.glob("*.png"), key=lambda f: f.stat().st_mtime)[-2:]]
    if not names:
        job.log("Скриншотов нет: сделай F2 в игре.")
        return False
    out = DATA / "checks" / job.id
    out.mkdir(parents=True, exist_ok=True)
    ok = True
    for i, n in enumerate(names):
        src = shots_dir / n
        if not src.exists():
            job.log(f"Нет файла {n}")
            ok = False
            continue
        before, after = out / f"before_{i}.png", out / f"after_{i}.png"
        shutil.copy2(src, before)
        rc = run_cmd(job, [java_exe(), "-cp", lwjgl_cp(), str(tool), str(shaders_dir()), str(src), str(after)])
        if rc == 0 and after.exists():
            job.images.append({"name": n, "before": f"/checks/{job.id}/before_{i}.png", "after": f"/checks/{job.id}/after_{i}.png"})
        else:
            ok = False
    return ok


def job_shader_install(job: Job, p: dict) -> bool:
    src = Path(CFG["project"]) / "shaderpack" / "LotosCinema"
    dst = Path(CFG["minecraft"]) / "shaderpacks" / "LotosCinema"
    shutil.copytree(src, dst, dirs_exist_ok=True)
    job.log(f"Пак скопирован в {dst}")
    job.log("В игре: Видео → Шейдеры → LotosCinema. F3+R перезагружает шейдеры без перезапуска.")
    return True


def job_scene_frames(job: Job, p: dict) -> bool:
    proj = Path(CFG["project"])
    classes = proj / "build" / "classes" / "java" / "main"
    if not classes.exists():
        job.log("Нет скомпилированных классов: сначала собери мод.")
        return False
    times = [t for t in re.findall(r"\d+(?:\.\d+)?", str(p.get("times", ""))) ][:12]
    if not times:
        job.log("Укажи времена в секундах, например: 83, 100.5, 120")
        return False
    out = DATA / "checks" / job.id
    out.mkdir(parents=True, exist_ok=True)
    skin = proj / "src/main/resources/assets/lotusblight/textures/entity/honcho.png"
    beats = proj / "src/main/resources/assets/lotusblight/ending13/beats.json"
    cp = f"{classes};{proj / 'build' / 'resources' / 'main'}"
    rc = run_cmd(job, [java_exe(), "-Djava.awt.headless=true", "-cp", cp, "com.lotusblight.client.Ending13Scene",
                       str(out), str(skin), str(beats)] + times)
    for f in sorted(out.glob("t*.png")):
        job.images.append({"name": f.stem, "after": f"/checks/{job.id}/{f.name}"})
    return rc == 0 and bool(job.images)


def sandbox_root() -> Path:
    return Path(CFG["tmp"]) / "lotos-sandbox"


def sandbox_path(name: str) -> Path:
    if not ID_RE.match(name or ""):
        raise ValueError("имя песочницы: латиница, цифры, - и _")
    return sandbox_root() / name


def job_sandbox_create(job: Job, p: dict) -> bool:
    path = sandbox_path(p.get("name", ""))
    if path.exists():
        job.log("Такая песочница уже есть.")
        return False
    sandbox_root().mkdir(parents=True, exist_ok=True)
    rc = run_cmd(job, ["git", "-C", CFG["project"], "worktree", "add", "--detach", str(path)])
    if rc == 0:
        job.log(f"Песочница готова: {path}. Это отдельная копия проекта: ничего из неё не попадёт в основной, пока ты не нажмёшь «Применить».")
    return rc == 0


def job_sandbox_status(job: Job, p: dict) -> bool:
    path = sandbox_path(p.get("name", ""))
    a = run_cmd(job, ["git", "-C", str(path), "status", "--short", "-b"])
    b = run_cmd(job, ["git", "-C", str(path), "diff", "--stat", "HEAD"])
    return a == 0 and b == 0


def job_sandbox_apply(job: Job, p: dict) -> bool:
    path = sandbox_path(p.get("name", ""))
    if run_cmd(job, ["git", "-C", str(path), "add", "-A"]) != 0:
        return False
    r = subprocess.run(["git", "-C", str(path), "diff", "--cached", "--binary"], capture_output=True, creationflags=NO_WINDOW)
    if not r.stdout.strip():
        job.log("В песочнице нет изменений, применять нечего.")
        return True
    patch = Path(CFG["tmp"]) / f"sandbox_{path.name}.patch"
    patch.write_bytes(r.stdout)
    if run_cmd(job, ["git", "-C", CFG["project"], "apply", "--check", str(patch)]) != 0:
        job.log("Патч не ложится на основной проект: там есть пересекающиеся изменения. Ничего не изменено.")
        return False
    ok = run_cmd(job, ["git", "-C", CFG["project"], "apply", str(patch)]) == 0
    if ok:
        job.log("Изменения из песочницы перенесены в основной проект (в рабочие файлы, не закоммичены).")
    return ok


def job_sandbox_remove(job: Job, p: dict) -> bool:
    path = sandbox_path(p.get("name", ""))
    rc = run_cmd(job, ["git", "-C", CFG["project"], "worktree", "remove", "--force", str(path)])
    run_cmd(job, ["git", "-C", CFG["project"], "worktree", "prune"])
    return rc == 0


def job_sandbox_compile(job: Job, p: dict) -> bool:
    path = sandbox_path(p.get("name", ""))
    tool = path / "shaderpack" / "tools" / "CompileAll.java"
    sh = path / "shaderpack" / "LotosCinema" / "shaders"
    if not tool.exists() or not sh.exists():
        job.log("В этой песочнице нет шейдер-пака.")
        return False
    rc = run_cmd(job, [java_exe(), "-cp", lwjgl_cp(), str(tool), str(sh)])
    return rc == 0 and any("ALL OK" in ln for ln in job.lines)


def job_sandbox_build(job: Job, p: dict) -> bool:
    path = sandbox_path(p.get("name", ""))
    rc = run_cmd(job, ["cmd", "/c", str(path / "gradlew.bat"), "build", "-q", "--no-daemon", "-x", "test"], cwd=str(path))
    jar = path / "build" / "libs" / CFG["jar_name"]
    if jar.exists():
        job.log(f"Собрано в песочнице: {jar} ({jar.stat().st_size / 2**20:.1f} МБ). В mods не копируется.")
    return rc == 0 and jar.exists()


def test_game_dir() -> Path:
    return Path(CFG.get("test_game", "D:/mc-sandbox"))


def job_game_install(job: Job, p: dict) -> bool:
    """Puts the built mod into the separate TEST copy of the game (its own mods, saves and config), never the real one.
    Dependency jars are copied from the real mods folder once; our own jars are always replaced by the fresh build."""
    proj, mc, game = Path(CFG["project"]), Path(CFG["minecraft"]), test_game_dir()
    if game.resolve() == mc.resolve():
        job.log("Тестовая папка совпадает с настоящей: так нельзя.")
        return False
    jar = proj / "build" / "libs" / CFG["jar_name"]
    if not jar.exists():
        job.log("Сначала собери мод: файла нет в build/libs.")
        return False
    mods = game / "mods"
    mods.mkdir(parents=True, exist_ok=True)
    copied = 0
    for src in sorted((mc / "mods").glob("*.jar")):
        if src.name.lower().startswith("lotusblight-"):
            continue                                         # our main jar comes from the fresh build below
        dst = mods / src.name
        if not dst.exists() or dst.stat().st_size != src.stat().st_size:
            shutil.copy2(src, dst)
            copied += 1
    for old in mods.glob("lotusblight-*.jar"):
        if old.name != jar.name:
            old.unlink()                                     # an older build of our own jar
    try:
        shutil.copy2(jar, mods / jar.name)
    except PermissionError:
        job.log("Файл занят: закрой тестовую игру и повтори.")
        return False
    pack = proj / "shaderpack" / "LotosCinema"
    if pack.exists():
        shutil.copytree(pack, game / "shaderpacks" / "LotosCinema", dirs_exist_ok=True)
    job.log(f"Тестовая игра: {game}")
    job.log(f"Моды: добавлено {copied} зависимостей, мод {jar.name} ({jar.stat().st_size / 2**20:.1f} МБ) обновлён. Шейдер-пак на месте.")
    job.log("Настоящая папка mods не тронута.")
    return True


def job_build_game(job: Job, p: dict) -> bool:
    return job_build(job, p) and job_game_install(job, p)


def job_game_run(job: Job, p: dict) -> bool:
    """Starts Minecraft with the mod straight from the sources (ForgeGradle runClient): its own folder `run`, its own worlds,
    no launcher and no account. The job stays open while the game runs and shows its console; it can be stopped from the page."""
    proj = Path(CFG["project"])
    gw = proj / "gradlew.bat"
    if not gw.exists() or not (proj / "run").exists() or not CFG["java_home"]:
        job.log("Не хватает gradlew.bat, папки run или Java: см. «Состояние».")
        return False
    env = build_env()
    env["JAVA_TOOL_OPTIONS"] = env.get("JAVA_TOOL_OPTIONS", "") + " -Xmx3G"
    cmd = ["cmd", "/c", str(gw), "runClient", "--no-daemon"]
    if p.get("hotswap"):
        cmd.append("-Photswap")                              # debug port on this computer only: lets "apply changes" swap code in live
        HOT["since"] = int(time.time() * 1000)              # classes compiled before this moment are what the game starts with
    if p.get("dry"):
        job.log("Проверка без запуска: всё готово.")
        job.log("Команда: " + " ".join(cmd))
        job.log(f"Папка игры: {proj / 'run'} (отдельные миры)")
        job.log("Память для игры: до 3 ГБ. Свободно сейчас: %.1f ГБ из %.1f." % (memory_gb()[1], memory_gb()[0]))
        return True
    job.log("Запускаю игру из исходников. Первый запуск долгий, окно Minecraft откроется само. Миры лежат в папке run, настоящие не затронуты.")
    rc = run_cmd(job, cmd, cwd=str(proj), env=env)
    job.log(f"Игра закрыта (код {rc}).")
    return rc == 0


def job_hot_apply(job: Job, p: dict) -> bool:
    """Compiles what changed and swaps the changed classes into the RUNNING game (tools/HotSwap.java). Method bodies only:
    a new field or method in an already loaded class needs a restart, and the tool says which."""
    proj = Path(CFG["project"])
    gw = str(proj / "gradlew.bat")
    rc = run_cmd(job, ["cmd", "/c", gw, "compileJava", "processResources", "-q", "-Dorg.gradle.daemon.idletimeout=120000"], cwd=str(proj))
    if rc != 0:
        job.log("Сборка не прошла: исправь ошибки выше. Подмена не делалась.")
        return False
    classes = proj / "build" / "classes" / "java" / "main"
    rc = run_cmd(job, [java_exe(), str(proj / "tools" / "HotSwap.java"), str(classes), str(DATA / "hotswap_state.txt"), str(HOT["since"]), "5005"])
    job.log("Ресурсы (текстуры, модели, тексты) обновлены на диске: в игре нажми F3+T. Данные (рецепты и т. п.): /reload.")
    return rc == 0


def newest_source_mtime() -> float:
    newest = 0.0
    for root in (Path(CFG["project"]) / "src" / "main",):
        for dirpath, _dirs, files in os.walk(root):
            for f in files:
                try:
                    newest = max(newest, os.stat(os.path.join(dirpath, f)).st_mtime)
                except OSError:
                    pass
    return newest


def watch_loop() -> None:
    HOT["applied_mtime"] = newest_source_mtime()
    while HOT["watch"]:
        time.sleep(3)
        try:
            newest = newest_source_mtime()
            busy = any(j.status == "running" and j.kind == "hot_apply" for j in JOBS.values())
            if newest > HOT["applied_mtime"] and not busy:
                time.sleep(1.5)                                  # let a save in progress finish
                HOT["applied_mtime"] = newest_source_mtime()
                start_job("hot_apply", {})
        except Exception:  # noqa: BLE001
            pass


def set_watch(on: bool) -> dict:
    HOT["watch"] = bool(on)
    if on and not (HOT["thread"] and HOT["thread"].is_alive()):
        HOT["thread"] = threading.Thread(target=watch_loop, daemon=True)
        HOT["thread"].start()
    return {"on": HOT["watch"]}


def stop_job(jid: str) -> dict:
    job = JOBS.get(jid)
    if not job or not job.proc or job.proc.poll() is not None:
        return {"ok": False, "why": "нет запущенного процесса"}
    subprocess.run(["taskkill", "/T", "/F", "/PID", str(job.proc.pid)], capture_output=True, creationflags=NO_WINDOW)   # only what this job started
    job.log("Остановлено по твоей кнопке.")
    return {"ok": True}


def job_backup(job: Job, p: dict) -> bool:
    import zipfile
    stamp = datetime.datetime.now().strftime("%Y%m%d_%H%M%S")
    out = DATA / "backups" / f"studio_{stamp}.zip"
    n = 0
    with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as z:
        for sub in ("episodes", "docs", "refs"):
            for f in (DATA / sub).rglob("*"):
                if f.is_file():
                    z.write(f, f.relative_to(DATA))
                    n += 1
    for old in sorted((DATA / "backups").glob("studio_*.zip"))[:-20]:
        old.unlink(missing_ok=True)
    job.log(f"Сохранено файлов: {n}. Архив: {out} ({out.stat().st_size / 2**20:.2f} МБ)")
    return True


JOB_KINDS = {
    "hot_apply": ("Применить изменения в запущенной игре", job_hot_apply),
    "game_run": ("Запуск игры с модом (тест)", job_game_run),
    "game_install": ("Мод в тестовую игру", job_game_install),
    "build_game": ("Сборка и мод в тестовую игру", job_build_game),
    "sandbox_create": ("Песочница: создать", job_sandbox_create),
    "sandbox_status": ("Песочница: что изменилось", job_sandbox_status),
    "sandbox_apply": ("Песочница: применить в проект", job_sandbox_apply),
    "sandbox_remove": ("Песочница: удалить", job_sandbox_remove),
    "sandbox_compile": ("Песочница: проверка шейдера", job_sandbox_compile),
    "sandbox_build": ("Песочница: сборка", job_sandbox_build),
    "backup": ("Резервная копия данных Studio", job_backup),
    "disk": ("Свободное место", job_disk),
    "git_status": ("Git: состояние", job_git_status),
    "git_commit": ("Git: коммит", job_git_commit),
    "git_push": ("Git: отправка", job_git_push),
    "build": ("Сборка мода", job_build),
    "copy_jar": ("Копирование в mods", job_copy_jar),
    "build_copy": ("Сборка и копирование", job_build_copy),
    "shader_compile": ("Компиляция шейдера", job_shader_compile),
    "shader_run": ("Шейдер на скриншотах", job_shader_run),
    "shader_install": ("Установка пака в игру", job_shader_install),
    "scene_frames": ("Кадры сцены 13", job_scene_frames),
}


def start_job(kind: str, params: dict) -> Job:
    title, fn = JOB_KINDS[kind]
    job = Job(kind, title)
    JOBS[job.id] = job
    for old in sorted(JOBS.values(), key=lambda j: j.started)[:-30]:
        JOBS.pop(old.id, None)

    def runner():
        try:
            ok = fn(job, params)
        except Exception as e:  # noqa: BLE001
            job.log(f"Ошибка: {e}")
            ok = False
        job.log("— готово —" if ok else "— завершено с ошибкой —")
        job.status = "ok" if ok else "fail"
        try:
            act = load_doc("activity")
            act.append({"time": datetime.datetime.now().isoformat(timespec="seconds"), "title": job.title, "status": job.status,
                        "seconds": round(time.time() - job.started, 1), "tail": job.lines[-4:]})
            save_doc("activity", act[-300:], None)
        except Exception:  # noqa: BLE001
            pass

    threading.Thread(target=runner, daemon=True).start()
    return job


# ------------------------------------------------------------------ logs and screenshots

def tail_text(path: Path, lines: int = 400) -> str:
    size = path.stat().st_size
    with path.open("rb") as f:
        f.seek(max(0, size - 400_000))
        data = f.read()
    if size > 400_000 and b"\n" in data:
        data = data[data.index(b"\n") + 1:]          # drop the line the seek cut in half
    try:
        text = data.decode("utf-8")
    except UnicodeDecodeError:
        text = data.decode("cp1251", errors="replace")   # the game writes the log in the system code page
    return "\n".join(text.splitlines()[-lines:])


def list_crashes() -> list:
    mc = Path(CFG["minecraft"])
    files = list(mc.glob("hs_err_pid*.log")) + list((mc / "crash-reports").glob("*.txt"))
    files.sort(key=lambda f: f.stat().st_mtime, reverse=True)
    return [{"name": f.name, "time": datetime.datetime.fromtimestamp(f.stat().st_mtime).strftime("%d.%m %H:%M"),
             "kb": round(f.stat().st_size / 1024)} for f in files[:40]]


def git_log_list() -> list:
    out = git_out("log", "-60", "--format=%h|%ad|%s", "--date=format:%d.%m %H:%M")
    return [dict(zip(("hash", "when", "msg"), ln.split("|", 2))) for ln in out.splitlines() if ln.count("|") >= 2]


def read_log(name: str, lines: int = 400, file: str = "") -> dict:
    mc = Path(CFG["minecraft"])
    lines = max(50, min(int(lines), 5000))
    if name == "crashfile":
        fn = Path(file).name
        path = next((p for p in (mc / fn, mc / "crash-reports" / fn) if p.exists()), mc / fn)
        if not path.exists():
            return {"path": str(path), "text": "Файла нет."}
        return {"path": str(path), "mtime": datetime.datetime.fromtimestamp(path.stat().st_mtime).strftime("%d.%m %H:%M:%S"),
                "text": tail_text(path, lines)}
    if name == "crash":
        cands = list(mc.glob("hs_err_pid*.log")) + list((mc / "crash-reports").glob("*.txt"))
        if not cands:
            return {"path": "", "text": "Отчётов о вылете нет."}
        path = max(cands, key=lambda f: f.stat().st_mtime)
    elif name == "debug":
        path = mc / "logs" / "debug.log"
    elif name == "launcher":
        path = mc / "launcher_log3.txt"
    else:
        path = mc / "logs" / "latest.log"
    if not path.exists():
        return {"path": str(path), "text": "Файла нет."}
    return {"path": str(path), "mtime": datetime.datetime.fromtimestamp(path.stat().st_mtime).strftime("%d.%m %H:%M:%S"),
            "text": tail_text(path, lines)}


def list_screenshots() -> list:
    d = Path(CFG["minecraft"]) / "screenshots"
    if not d.exists():
        return []
    files = sorted(d.glob("*.png"), key=lambda f: f.stat().st_mtime, reverse=True)[:80]
    return [{"name": f.name, "mb": round(f.stat().st_size / 2**20, 1),
             "time": datetime.datetime.fromtimestamp(f.stat().st_mtime).strftime("%d.%m %H:%M")} for f in files]


# ------------------------------------------------------------------ shared documents (world, ideas, messages, tasks, screenshot notes)

DOC_NAMES = {"world", "ideas", "messages", "tasks", "shotnotes", "wiki", "diagrams", "ratings", "activity", "wincheck", "timeline"}
DOC_FILE = {"messages": "for_claude", "timeline": "meta_line_timeline"}      # exported as art/episodes/<file>.md
LINE_COLORS = ["#7a5cff", "#ff4d6d", "#ffb703", "#2ec4b6", "#4cc9f0", "#f72585", "#90be6d", "#f8961e", "#9d4edd", "#43aa8b", "#e76f51", "#8ecae6"]


def default_world() -> dict:
    def hero(name, role, look="", notes="", color="#7a5cff"):
        return {"id": uuid.uuid4().hex[:8], "name": name, "role": role, "look": look, "voice": "", "notes": notes, "color": color, "refs": []}
    heroes = [
        hero("Рей (игрок)", "Главный герой, его играет игрок", "", "Как DoorKeeper из DOORS (со слов автора).", "#7a5cff"),
        hero("Хончо", "Герой", "", "Описание ждёт автора.", "#ff4d6d"),
        hero("Сейри", "Герой", "", "Описание ждёт автора.", "#2ec4b6"),
        hero("Люйдок", "Герой", "", "Описание ждёт автора.", "#ffb703"),
        hero("Альтер", "Герой", "", "Описание ждёт автора.", "#4cc9f0"),
        hero("Делантор", "Герой", "", "Описание ждёт автора.", "#90be6d"),
        hero("Сержант 418", "Главный похититель и полуантагонист, враг-помеха",
             "Молодой, «хороший военный». Подробная внешность позже.",
             "Посланник нового директора лаборатории. Похищает игрока на многих ветках после катсцены с лестницей, выпытывает, что игрок узнал, и снова стирает память (со слов автора).", "#e76f51"),
        hero("Директор лаборатории", "Новый директор", "", "Сержант 418 действует по его поручению.", "#9d4edd"),
        hero("Архитекторы", "Есть примерный шаблон", "", "Подробности автор даст сам.", "#f72585"),
        hero("Фигура с клинками", "Загадочная фигура", "", "Связана со ВСЕМИ Архитекторами. Оружие: клинки. Зрителю известно только из анимации (со слов автора).", "#cfd8ff"),
    ]
    places = [{"id": uuid.uuid4().hex[:8], "name": n, "desc": "", "look": "", "refs": []} for n in
              ["Лаборатория", "Лестничная клетка", "Кабинет директора", "Камера", "Тёмная комната", "Коридор", "Лимб", "Зал Архитекторов"]]
    lines = [{"n": i + 1, "name": f"Линия {i + 1}", "color": LINE_COLORS[i], "summary": "", "status": "idea"} for i in range(12)]
    return {"heroes": heroes, "places": places, "lines": lines}


def _id() -> str:
    return uuid.uuid4().hex[:8]


def default_wiki() -> list:
    now = datetime.datetime.now().isoformat(timespec="minutes")

    def page(title, body, tags=()):
        return {"id": _id(), "title": title, "tags": list(tags), "body": body, "updated": now}
    return [
        page("Начало", "Это вики мода. Пиши страницы про героев, места, правила мира и сюжет.\n\nСсылка на другую страницу: [[Сержант 418]].\nЗаголовки: # Большой, ## Поменьше. Списки: строки с «- ».\n\nВсё, что ты здесь пишешь, лежит только у тебя и в `art/episodes` (в git не попадает).", ["справка"]),
        page("Сержант 418", "# Сержант 418\n\n- Главный похититель и полуантагонист.\n- Молодой, «хороший военный». Внешность подробно позже.\n- Посланник нового директора лаборатории ([[Директор лаборатории]]).\n- Похищает игрока на многих ветках после катсцены с лестницей.\n- Выпытывает, что игрок узнал, и снова стирает память.\n- Роль в сюжете: враг-помеха.\n\n_Всё выше со слов автора._", ["герой", "антагонист"]),
        page("Директор лаборатории", "# Директор лаборатории\n\nНовый директор. Сержант 418 действует по его поручению ([[Сержант 418]]).\n\nПодробности ждут автора.", ["герой"]),
        page("Фигура с клинками", "# Фигура с клинками\n\n- Загадочная фигура.\n- Связана со ВСЕМИ Архитекторами ([[Архитекторы]]).\n- Оружие: клинки.\n- Зрителю известно только из анимации.", ["герой", "тайна"]),
        page("Архитекторы", "# Архитекторы\n\nЕсть примерный шаблон. Подробности автор даст сам.\n\nСвязаны с [[Фигура с клинками]].", ["сюжет"]),
        page("Тринадцатая концовка", "# Тринадцатая концовка\n\nГлавное раскрытие лора (со слов автора). Сейчас на втором плане: сначала эпизод 1.", ["сюжет", "концовка"]),
    ]


def default_diagrams() -> list:
    def n(i, x, y, text, color):
        return {"id": i, "x": x, "y": y, "text": text, "color": color}
    return [{
        "id": _id(), "name": "Связи героев",
        "nodes": [n("a", 80, 80, "Директор лаборатории", "#9d4edd"), n("b", 380, 80, "Сержант 418", "#e76f51"), n("c", 680, 80, "Рей (игрок)", "#7a5cff"),
                  n("d", 80, 260, "Фигура с клинками", "#cfd8ff"), n("e", 380, 260, "Архитекторы", "#f72585")],
        "edges": [{"from": "a", "to": "b", "label": "посылает"}, {"from": "b", "to": "c", "label": "похищает, стирает память"},
                  {"from": "d", "to": "e", "label": "связана со всеми"}],
    }]


def default_ideas() -> list:
    now = datetime.datetime.now().isoformat(timespec="minutes")

    def it(kind, text, tags=()):
        return {"id": _id(), "kind": kind, "text": text, "status": "new", "votes": 0, "tags": list(tags), "source": "claude", "created": now}
    return [
        it("suggestion", "Тестовый мир-эталон: стенд блоков (камень, металл, лампы, вода, листва) и фиксированное время. Один и тот же кадр после каждой правки шейдера.", ["шейдер"]),
        it("suggestion", "Отдельный профиль лаунчера «Лотос-эпизод» с нужными модами и шейдером, чтобы не трогать основной.", ["сборка"]),
        it("improve", "Освободить место на диске C: (сейчас около 4 ГБ). Лаунчер и сборка падают, когда свободно меньше 3 ГБ.", ["диск"]),
        it("improve", "Система лиц: моргание и эмоции подменой участка текстуры (глаза, брови), до сцен эпизода 1.", ["анимация"]),
        it("suggestion", "Каталог звуков под метки «Звук» из раскадровки (Freesound, только CC0).", ["звук"]),
        it("suggestion", "Два режима шейдера: «игра» (быстрее) и «видео» (максимум качества для записи).", ["шейдер"]),
        it("improve", "Раз в неделю делать резервную копию данных Studio (кнопка в «Панели мода»).", ["сборка"]),
        it("idea", "Аниматик с голосом браузера для проверки ритма сцены до сборки (в Studio уже есть, проверь на первом кадре).", ["эпизод"]),
    ]


def default_ratings() -> list:
    def r(title, kind):
        return {"id": _id(), "title": title, "kind": kind, "stars": 0, "comment": "", "date": ""}
    return [r("Lotos Studio (эта программа)", "программа"), r("Шейдер-пак LotosCinema", "шейдер"),
            r("Сцены 13-й концовки (как есть сейчас)", "сцена"), r("Редактор раскадровки", "функция")]


def default_timeline() -> dict:
    # The author's first marks of the long track (about 6:59): animation up to 0:18, actions from 0:18, the minigame from 0:38.
    def m(t, title, kind):
        return {"id": _id(), "t": t, "title": title, "kind": kind, "text": ""}
    return {"track": {"name": "", "duration": 419.84},
            "marks": [m(0, "Анимация", "anim"), m(18, "Начало действий", "action"), m(38, "Начало мини-игры", "game")]}


def default_doc(name: str):
    if name == "world":
        return default_world()
    if name == "shotnotes":
        return {}
    if name == "wiki":
        return default_wiki()
    if name == "diagrams":
        return default_diagrams()
    if name == "ideas":
        return default_ideas()
    if name == "ratings":
        return default_ratings()
    if name == "timeline":
        return default_timeline()
    return []


def doc_path(name: str) -> Path:
    return DATA / "docs" / f"{name}.json"


def load_doc(name: str):
    p = doc_path(name)
    if p.exists():
        return json.loads(p.read_text(encoding="utf-8"))
    d = default_doc(name)
    p.write_text(json.dumps(d, ensure_ascii=False, indent=2), encoding="utf-8")
    return d


def save_doc(name: str, data, md) -> dict:
    p = doc_path(name)
    text = json.dumps(data, ensure_ascii=False, indent=2)
    if p.exists() and p.read_text(encoding="utf-8") != text:
        stamp = datetime.datetime.now().strftime("%Y%m%d_%H%M%S")
        shutil.copy2(p, DATA / "history" / f"doc_{name}_{stamp}.json")
        for f in sorted((DATA / "history").glob(f"doc_{name}_*.json"))[:-15]:
            f.unlink(missing_ok=True)
    p.write_text(text, encoding="utf-8")
    if isinstance(md, str):
        out = PROJECT / "art" / "episodes"
        out.mkdir(parents=True, exist_ok=True)
        (out / f"{DOC_FILE.get(name, name)}.md").write_text(md, encoding="utf-8")
    return {"ok": True}


# ------------------------------------------------------------------ dashboard, diagnostics, mods, folders

def git_out(*args: str, timeout: int = 25) -> str:
    try:
        r = subprocess.run(["git", "-C", CFG["project"], *args], capture_output=True, text=True,
                           encoding="utf-8", errors="replace", timeout=timeout, creationflags=NO_WINDOW)
        return r.stdout.strip()
    except Exception:  # noqa: BLE001
        return ""


def dashboard() -> dict:
    eps = []
    for f in sorted((DATA / "episodes").glob("*.json")):
        try:
            ep = json.loads(f.read_text(encoding="utf-8"))
        except Exception:  # noqa: BLE001
            continue
        st = {"draft": 0, "ask": 0, "ok": 0, "redo": 0}
        for s in ep.get("shots", []):
            st[s.get("status", "draft")] = st.get(s.get("status", "draft"), 0) + 1
        eps.append({"id": f.stem, "title": ep.get("title", f.stem), "shots": len(ep.get("shots", [])),
                    "duration": sum(float(s.get("duration") or 0) for s in ep.get("shots", [])), "status": st})
    disks = []
    for letter in "CD":
        root = f"{letter}:\\"
        if Path(root).exists():
            u = shutil.disk_usage(root)
            disks.append({"letter": letter, "free_gb": round(u.free / 2**30, 1), "total_gb": round(u.total / 2**30), "free_pct": round(100 * u.free / u.total)})
    lines = git_out("status", "-sb").splitlines()
    last = git_out("log", "-1", "--format=%h|%s|%ad", "--date=format:%d.%m %H:%M").split("|", 2)
    jar = Path(CFG["project"]) / "build" / "libs" / CFG["jar_name"]
    modjar = Path(CFG["minecraft"]) / "mods" / CFG["jar_name"]
    mc = Path(CFG["minecraft"])
    errors, log_time = 0, ""
    lp = mc / "logs" / "latest.log"
    if lp.exists():
        txt = tail_text(lp, 3000)
        errors = len(re.findall(r"/ERROR\]|Exception", txt))
        log_time = datetime.datetime.fromtimestamp(lp.stat().st_mtime).strftime("%d.%m %H:%M")
    crashes = list(mc.glob("hs_err_pid*.log")) + list((mc / "crash-reports").glob("*.txt"))
    crash = max(crashes, key=lambda f: f.stat().st_mtime) if crashes else None
    docs = {n: load_doc(n) for n in ("ideas", "messages", "tasks")}
    return {
        "episodes": eps, "disks": disks,
        "git": {"branch": lines[0] if lines else "", "dirty": len([ln for ln in lines[1:] if ln and not ln.startswith("??")]),
                "untracked": len([ln for ln in lines[1:] if ln.startswith("??")]),
                "last": {"hash": last[0] if len(last) > 2 else "", "msg": last[1] if len(last) > 2 else "", "when": last[2] if len(last) > 2 else ""}},
        "build": {"jar": jar.exists(), "built": datetime.datetime.fromtimestamp(jar.stat().st_mtime).strftime("%d.%m %H:%M") if jar.exists() else "",
                  "in_mods": modjar.exists(), "same": jar.exists() and modjar.exists() and jar.stat().st_size == modjar.stat().st_size},
        "log": {"errors": errors, "time": log_time},
        "crash": {"name": crash.name, "time": datetime.datetime.fromtimestamp(crash.stat().st_mtime).strftime("%d.%m %H:%M")} if crash else None,
        "counts": {"screenshots": len(list((mc / "screenshots").glob("*.png"))) if (mc / "screenshots").exists() else 0,
                   "mods": len(list((mc / "mods").glob("*.jar"))) if (mc / "mods").exists() else 0,
                   "ideas": len(docs["ideas"]), "messages_new": len([m for m in docs["messages"] if m.get("status") == "new"]),
                   "tasks_open": [t for t in docs["tasks"] if not t.get("done")][:6], "tasks_open_n": len([t for t in docs["tasks"] if not t.get("done")])},
    }


DIAG_PATTERNS = [
    (r"OutOfMemoryError", "bad", "Не хватает памяти Java", "В лаунчере: Установки → профиль → Дополнительные → JVM-аргументы. Поставь -Xmx4G (или больше, если позволяет память)."),
    (r"EXCEPTION_ACCESS_VIOLATION|GLFW error|nvoglv64|atio6axx|ig9icd", "bad", "Сбой видеодрайвера или OpenGL", "Обнови драйвер видеокарты. Проверь, вылетает ли игра без шейдера (Видео → Шейдеры → Выкл)."),
    (r"Failed to compile|GLSL|(?i:shader).{0,40}(?i:error|failed)", "warn", "Ошибка шейдера", "Открой «Шейдер» → «Проверить компиляцию». Если ошибка в LotosCinema, пришли строку Claude."),
    (r"Mixin apply (failed|for mod)|MixinApplyError|InvalidMixinException", "bad", "Конфликт модов (Mixin)", "Один из модов не стыкуется с другим. В строке рядом есть названия модов: проверь, не обновлялся ли один из них."),
    (r"Found duplicate mods|DuplicateModsFoundException", "bad", "Два одинаковых мода", "В папке mods лежат две версии одного мода. Оставь одну."),
    (r"Missing or unsupported mandatory dependencies|ModLoadingException", "bad", "Не хватает мода-зависимости", "В тексте ошибки названа нужная зависимость и версия. Положи её в mods."),
    (r"NoSuchMethodError|NoClassDefFoundError|ClassNotFoundException|AbstractMethodError", "bad", "Несовместимые версии модов", "Обычно один мод собран под другую версию другого. Смотри, какой класс указан."),
    (r"No space left|недостаточно места|not enough space", "bad", "Нет места на диске", "Освободи место на диске C: (нужно хотя бы 3 ГБ)."),
    (r"Unable to download the launcher update|Failed to initialize GDK|LoadErrorNotPresent", "warn", "Проблема лаунчера Minecraft", "Проверь место на C: и что Xbox App и Службы игр обновлены; перезапусти лаунчер."),
]


DIAG_IGNORE = re.compile(r"\[mixin/\]|Error loading class|No translation found")   # known harmless noise


def _age_days(path: Path) -> float:
    return round((time.time() - path.stat().st_mtime) / 86400, 1)


def diagnose() -> dict:
    mc = Path(CFG["minecraft"])
    sources = []            # (name, text, age in days)
    lp = mc / "logs" / "latest.log"
    if lp.exists():
        sources.append(("latest.log", tail_text(lp, 3000), _age_days(lp)))
    cr = read_log("crash")
    if cr.get("path") and Path(cr["path"]).exists():
        sources.append((Path(cr["path"]).name, cr["text"], _age_days(Path(cr["path"]))))
    lp3 = mc / "launcher_log3.txt"
    if lp3.exists() and _age_days(lp3) < 2:                 # an old launcher log says nothing about now
        sources.append(("launcher_log3.txt", tail_text(lp3, 600), _age_days(lp3)))

    def finding(level, title, hint, src, line, age):
        old = age > 3
        return {"level": "info" if old else level, "title": title + (f" (старое, {age:g} дн.)" if old else ""), "hint": hint, "source": src,
                "evidence": line.strip()[:300], "age_days": age}
    found = []
    for pat, level, title, hint in DIAG_PATTERNS:
        for src, text, age in sources:
            line = next((ln for ln in text.splitlines() if re.search(pat, ln) and not DIAG_IGNORE.search(ln)), None)
            if line:
                found.append(finding(level, title, hint, src, line, age))
                break
    for src, text, age in sources:
        own = [ln for ln in text.splitlines() if re.search(r"lotusblight|lotuslib", ln, re.I) and re.search(r"Exception|/ERROR\]", ln)]
        if own:
            found.append(finding("warn", "Ошибка в нашем моде (Lotos)", "Скопируй эту строку и пришли Claude: это правится в коде мода.", src, own[0], age))
            break
    return {"findings": found, "checked": [s for s, _, _ in sources]}


def list_mods() -> dict:
    d = Path(CFG["minecraft"]) / "mods"
    mods, groups = [], {}
    for f in sorted(d.glob("*.jar*")) if d.exists() else []:
        mods.append({"name": f.name, "mb": round(f.stat().st_size / 2**20, 2), "disabled": f.name.endswith(".disabled")})
        base = re.sub(r"\.jar(\.disabled)?$", "", f.name)
        base = re.sub(r"[-_+ ]?(mc)?\d[\w.+-]*$", "", base, flags=re.I).lower()
        groups.setdefault(base, []).append(f.name)
    return {"mods": mods, "total_mb": round(sum(m["mb"] for m in mods), 1),
            "duplicates": {k: v for k, v in groups.items() if k and len(v) > 1}}


def open_folder(key: str) -> dict:
    mc, proj = Path(CFG["minecraft"]), Path(CFG["project"])
    folders = {"project": proj, "mods": mc / "mods", "screenshots": mc / "screenshots", "logs": mc / "logs",
               "shaderpacks": mc / "shaderpacks", "art": proj / "art", "studio": DATA, "episodes": proj / "art" / "episodes"}
    if key not in folders:
        raise ValueError("unknown folder")
    p = folders[key]
    p.mkdir(parents=True, exist_ok=True)
    os.startfile(str(p))  # type: ignore[attr-defined]
    return {"ok": True, "path": str(p)}


# ------------------------------------------------------------------ environments: Windows Sandbox, WSL, project sandboxes

def memory_gb() -> tuple:
    import ctypes

    class MS(ctypes.Structure):
        _fields_ = [("dwLength", ctypes.c_ulong), ("dwMemoryLoad", ctypes.c_ulong), ("ullTotalPhys", ctypes.c_ulonglong),
                    ("ullAvailPhys", ctypes.c_ulonglong), ("ullTotalPageFile", ctypes.c_ulonglong), ("ullAvailPageFile", ctypes.c_ulonglong),
                    ("ullTotalVirtual", ctypes.c_ulonglong), ("ullAvailVirtual", ctypes.c_ulonglong), ("sullAvailExtendedVirtual", ctypes.c_ulonglong)]
    m = MS()
    m.dwLength = ctypes.sizeof(MS)
    ctypes.windll.kernel32.GlobalMemoryStatusEx(ctypes.byref(m))  # type: ignore[attr-defined]
    return round(m.ullTotalPhys / 2**30, 1), round(m.ullAvailPhys / 2**30, 1)


def wsl_info() -> dict:
    exe = Path(os.environ.get("windir", "C:\\Windows")) / "System32" / "wsl.exe"
    if not exe.exists():
        return {"exe": False, "distros": [], "ready": False}
    try:
        r = subprocess.run([str(exe), "-l", "-q"], capture_output=True, timeout=12, creationflags=NO_WINDOW)
    except Exception:  # noqa: BLE001
        return {"exe": True, "distros": [], "ready": False}
    raw = r.stdout
    text = raw.decode("utf-16-le", errors="ignore") if b"\x00" in raw else raw.decode("utf-8", errors="ignore")
    distros = [ln.strip().replace("\x00", "") for ln in text.splitlines() if ln.strip()] if r.returncode == 0 else []
    return {"exe": True, "distros": distros, "ready": bool(distros)}


def list_sandboxes() -> list:
    out = git_out("worktree", "list", "--porcelain")
    root = str(sandbox_root()).replace("\\", "/").lower()
    items = []
    for block in out.split("\n\n"):
        d = dict(ln.split(" ", 1) for ln in block.splitlines() if " " in ln)
        path = d.get("worktree", "").replace("\\", "/")
        if path.lower().startswith(root):
            items.append({"name": Path(path).name, "path": path, "head": d.get("HEAD", "")[:7]})
    return items


def envs_status() -> dict:
    total, free = memory_gb()
    sb = Path(os.environ.get("windir", "C:\\Windows")) / "System32" / "WindowsSandbox.exe"
    return {"ram_total": total, "ram_free": free, "sandbox_exe": sb.exists(), "wsl": wsl_info(), "sandboxes": list_sandboxes()}


def windows_sandbox_xml(share_mods: bool, network: bool, mem_mb: int) -> str:
    from xml.sax.saxutils import escape
    proj, mc = Path(CFG["project"]), Path(CFG["minecraft"])
    folders = [(proj, "LOTOS")] + ([(mc / "mods", "mods")] if share_mods else [])
    mapped = "".join(f"<MappedFolder><HostFolder>{escape(str(p))}</HostFolder><SandboxFolder>C:\\Users\\WDAGUtilityAccount\\Desktop\\{n}</SandboxFolder><ReadOnly>true</ReadOnly></MappedFolder>" for p, n in folders)
    mem = max(2048, min(int(mem_mb), 4096))
    return (f"<Configuration><VGpu>Disable</VGpu><Networking>{'Enable' if network else 'Disable'}</Networking><MemoryInMB>{mem}</MemoryInMB>"
            f"<MappedFolders>{mapped}</MappedFolders><LogonCommand><Command>explorer.exe C:\\Users\\WDAGUtilityAccount\\Desktop</Command></LogonCommand></Configuration>")


def launch_windows_sandbox(req: dict) -> dict:
    exe = Path(os.environ.get("windir", "C:\\Windows")) / "System32" / "WindowsSandbox.exe"
    if not exe.exists():
        raise ValueError("Windows Sandbox не включена в системе")
    xml = windows_sandbox_xml(bool(req.get("share_mods")), bool(req.get("network")), int(req.get("mem_mb", 3072)))
    if req.get("dry_run"):
        return {"ok": True, "launched": False, "xml": xml}
    wsb = Path(CFG["tmp"]) / "lotos.wsb"
    wsb.write_text(xml, encoding="utf-8")
    os.startfile(str(wsb))  # type: ignore[attr-defined]
    return {"ok": True, "launched": True, "xml": xml, "path": str(wsb)}


# ------------------------------------------------------------------ shader options (the sliders in the pack's #define lines)

OPT_RE = re.compile(r"^(#define\s+)([A-Z][A-Z0-9_]+)(\s+)(-?\d+(?:\.\d+)?)(\s*//\s*\[([^\]]*)\])", re.M)


def read_shader_options() -> list:
    opts: dict[str, dict] = {}
    for f in sorted(shaders_dir().glob("*.fsh")):
        for m in OPT_RE.finditer(f.read_text(encoding="utf-8")):
            o = opts.setdefault(m.group(2), {"name": m.group(2), "value": m.group(4), "choices": m.group(6).split(), "files": []})
            o["files"].append(f.name)
    return list(opts.values())


def apply_shader_options(values: dict) -> dict:
    known = {o["name"]: o for o in read_shader_options()}
    clean = {}
    for name, val in values.items():
        o = known.get(name)
        if not o:
            raise ValueError(f"неизвестная настройка {name}")
        v = str(val)
        if not any(abs(float(v) - float(c)) < 1e-9 for c in o["choices"]):
            raise ValueError(f"{name}: значение {v} не из списка {o['choices']}")
        clean[name] = v
    changed = 0
    for f in sorted(shaders_dir().glob("*.fsh")):
        raw = f.read_bytes().decode("utf-8")
        new = raw
        for name, v in clean.items():
            new = re.sub(rf"^(#define\s+{name}\s+)-?\d+(?:\.\d+)?", lambda m, v=v: m.group(1) + v, new, flags=re.M)
        if new != raw:
            f.write_bytes(new.encode("utf-8"))
            changed += 1
    return {"ok": True, "files_changed": changed}


# ------------------------------------------------------------------ http

class Handler(BaseHTTPRequestHandler):
    server_version = "LotosStudio"

    def log_message(self, *a):  # keep the console quiet
        pass

    def host_ok(self) -> bool:
        return self.headers.get("Host", "").split(":")[0] in ("127.0.0.1", "localhost")

    def send_bytes(self, data: bytes, ctype: str, code: int = 200):
        self.send_response(code)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(data)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(data)

    def send_json(self, obj, code: int = 200):
        self.send_bytes(json.dumps(obj, ensure_ascii=False).encode("utf-8"), "application/json; charset=utf-8", code)

    def fail(self, code: int, msg: str):
        self.send_bytes(msg.encode("utf-8"), "text/plain; charset=utf-8", code)

    def serve_file(self, base: Path, rel: str):
        p = (base / rel).resolve()
        try:
            p.relative_to(base.resolve())
        except ValueError:
            return self.fail(403, "forbidden")
        if not p.is_file():
            return self.fail(404, "not found")
        ctype = mimetypes.guess_type(str(p))[0] or "application/octet-stream"
        if p.suffix in (".html", ".js", ".css", ".json", ".md", ".txt"):
            ctype += "; charset=utf-8"
        self.send_bytes(p.read_bytes(), ctype)

    def body(self, limit: int = 3_000_000) -> bytes:
        n = int(self.headers.get("Content-Length", "0") or 0)
        if n > limit:
            raise ValueError("слишком большой запрос")
        return self.rfile.read(n)

    def do_GET(self):
        if not self.host_ok():
            return self.fail(403, "bad host")
        u = urlparse(self.path)
        q = {k: v[0] for k, v in parse_qs(u.query).items()}
        path = unquote(u.path)
        try:
            if path in ("/", "/index.html"):
                return self.serve_file(STATIC, "index.html")
            if path == "/style.css":
                return self.serve_file(STATIC, "style.css")
            if path.startswith("/js/"):
                return self.serve_file(STATIC / "js", path[4:])
            if path == "/api/status":
                return self.send_json(status_report())
            if path == "/api/episodes":
                return self.send_json(list_episodes())
            if path == "/api/episode":
                eid = q.get("id", "episode1")
                if not ID_RE.match(eid):
                    return self.fail(400, "bad id")
                return self.send_json(load_episode(eid))
            if path == "/api/history":
                eid = q.get("id", "episode1")
                if not ID_RE.match(eid):
                    return self.fail(400, "bad id")
                files = sorted((DATA / "history").glob(f"{eid}_*.json"), reverse=True)[:40]
                return self.send_json([f.name for f in files])
            if path == "/api/history_get":
                name = Path(q.get("file", "")).name
                return self.serve_file(DATA / "history", name)
            if path == "/api/job":
                job = JOBS.get(q.get("id", ""))
                if not job:
                    return self.fail(404, "no such job")
                return self.send_json(job.to_json(int(q.get("since", "0") or 0)))
            if path == "/api/log":
                return self.send_json(read_log(q.get("name", "latest"), int(q.get("lines", "400") or 400), q.get("file", "")))
            if path == "/api/crashes":
                return self.send_json(list_crashes())
            if path == "/api/gitlog":
                return self.send_json(git_log_list())
            if path == "/api/screenshots":
                return self.send_json(list_screenshots())
            if path == "/api/doc":
                if q.get("name") not in DOC_NAMES:
                    return self.fail(400, "bad doc")
                return self.send_json(load_doc(q["name"]))
            if path == "/api/dashboard":
                return self.send_json(dashboard())
            if path == "/api/diagnose":
                return self.send_json(diagnose())
            if path == "/api/mods":
                return self.send_json(list_mods())
            if path == "/api/envs":
                return self.send_json(envs_status())
            if path == "/api/watch":
                return self.send_json({"on": HOT["watch"]})
            if path == "/api/shader_options":
                return self.send_json(read_shader_options())
            if path.startswith("/refs/"):
                return self.serve_file(DATA / "refs", path[6:])
            if path.startswith("/checks/"):
                return self.serve_file(DATA / "checks", path[8:])
            if path.startswith("/mcshots/"):
                return self.serve_file(Path(CFG["minecraft"]) / "screenshots", path[9:])
            return self.fail(404, "not found")
        except Exception as e:  # noqa: BLE001
            return self.fail(500, f"{type(e).__name__}: {e}")

    def do_POST(self):
        if not self.host_ok():
            return self.fail(403, "bad host")
        u = urlparse(self.path)
        q = {k: v[0] for k, v in parse_qs(u.query).items()}
        path = unquote(u.path)
        try:
            if path == "/api/episode":
                eid = q.get("id", "episode1")
                if not ID_RE.match(eid):
                    return self.fail(400, "bad id")
                return self.send_json(save_episode(eid, json.loads(self.body().decode("utf-8"))))
            if path == "/api/episode_new":
                title = (json.loads(self.body().decode("utf-8")).get("title") or "Новый эпизод").strip()[:80]
                n = 1
                while episode_path(f"episode{n}").exists():
                    n += 1
                eid = f"episode{n}"
                ep = default_episode(eid, title)
                episode_path(eid).write_text(json.dumps(ep, ensure_ascii=False, indent=2), encoding="utf-8")
                return self.send_json({"id": eid})
            if path == "/api/upload":
                name = re.sub(r"[^A-Za-z0-9._-]", "_", q.get("name", "ref.png"))[-60:]
                ext = Path(name).suffix.lower()
                if ext not in (".png", ".jpg", ".jpeg", ".webp", ".gif"):
                    return self.fail(400, "only images")
                fname = f"{uuid.uuid4().hex[:8]}_{name}"
                (DATA / "refs" / fname).write_bytes(self.body(15_000_000))
                return self.send_json({"file": fname})
            if path == "/api/doc":
                if q.get("name") not in DOC_NAMES:
                    return self.fail(400, "bad doc")
                req = json.loads(self.body(8_000_000).decode("utf-8"))
                return self.send_json(save_doc(q["name"], req.get("data"), req.get("md")))
            if path == "/api/open":
                return self.send_json(open_folder(json.loads(self.body().decode("utf-8")).get("key", "")))
            if path == "/api/watch":
                return self.send_json(set_watch(bool(json.loads(self.body().decode("utf-8")).get("on"))))
            if path == "/api/job_stop":
                return self.send_json(stop_job(json.loads(self.body().decode("utf-8")).get("id", "")))
            if path == "/api/sandbox_win":
                return self.send_json(launch_windows_sandbox(json.loads(self.body().decode("utf-8"))))
            if path == "/api/shader_options":
                return self.send_json(apply_shader_options(json.loads(self.body().decode("utf-8")).get("values", {})))
            if path == "/api/job":
                req = json.loads(self.body().decode("utf-8"))
                kind = req.get("kind", "")
                if kind not in JOB_KINDS:
                    return self.fail(400, "unknown job")
                return self.send_json({"id": start_job(kind, req.get("params", {})).id})
            return self.fail(404, "not found")
        except Exception as e:  # noqa: BLE001
            return self.fail(500, f"{type(e).__name__}: {e}")


def main():
    port = int(CFG["port"])
    try:
        srv = ThreadingHTTPServer(("127.0.0.1", port), Handler)
    except OSError as e:
        print(f"Не удалось занять порт {port}: {e}\nВозможно, Lotos Studio уже запущена: открой http://127.0.0.1:{port}")
        sys.exit(1)
    url = f"http://127.0.0.1:{port}"
    print(f"Lotos Studio: {url}\nПроект: {CFG['project']}\nЗакрой это окно, чтобы остановить.")
    if "--no-browser" not in sys.argv:
        threading.Timer(0.6, lambda: webbrowser.open(url)).start()
    try:
        srv.serve_forever()
    except KeyboardInterrupt:
        pass


if __name__ == "__main__":
    main()
