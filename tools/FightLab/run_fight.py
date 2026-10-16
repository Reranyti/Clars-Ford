"""Starts the real fight from a given second, with the scenes straight from the editor: used by the Lotos Editor's "run the fight from here".

    pythonw run_fight.py song2 38.5      (song2 | song3 | cutscene | finale, then the second to start from)

It compiles what changed (incrementally, into build/fight-lab/classes-cli) and starts the game detached; the log goes to build/fight-lab/last_run.log.
"""
import importlib.util
import subprocess
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location("fight_lab", HERE / "fight_lab.pyw")
lab = importlib.util.module_from_spec(spec)
spec.loader.exec_module(lab)


def main():
    kind = sys.argv[1] if len(sys.argv) > 1 else "song2"
    start = sys.argv[2] if len(sys.argv) > 2 else "0"
    out = lab.OUT / "classes-cli"
    out.mkdir(parents=True, exist_ok=True)
    files = [str(f) for f in sorted(lab.OVERLAY.glob("*.java"))]          # every source of the package: javac does not look for what changed behind classes it thinks are up to date
    cmd = [lab.JAVAC, "-encoding", "UTF-8", "-nowarn", "-cp", ";".join([str(out), str(lab.RES)] + lab.JARS),
           "-sourcepath", str(lab.SRC), "-d", str(out)] + files
    r = subprocess.run(cmd, cwd=str(lab.ROOT), capture_output=True, creationflags=lab.NO_WINDOW)
    if r.returncode != 0:
        (lab.OUT / "last_run.log").write_bytes(r.stdout + r.stderr)
        return 1
    cp = ";".join([str(out), str(lab.RES)] + lab.JARS)
    if kind == "build":                                  # for the editor's preview: only the classpath of the fresh build is wanted
        (lab.OUT / "preview.cp").write_text(cp, encoding="utf-8")
        return 0
    tracks = lab.TRACKS
    args = ["--song1", str(tracks / "song1.wav"), "--song2", str(tracks / "song2.wav"), "--song3", str(tracks / "song3.wav"),
            "--interlude", str(tracks / "interlude.wav"), "--contact", str(tracks / "contact.wav")]
    if lab.VIDEO.exists():
        args += ["--video", str(lab.VIDEO)]
    flag = {"song2": "--start2", "song3": "--start3", "cutscene": "--cutscene", "finale": "--finale"}.get(kind, "--start2")
    args += [flag, start]
    java = [lab.JAVA, "-Xmx1500m", "-Dlotos.scenes=" + str(lab.ROOT / "tools" / "LotosEditor" / "project.txt"), "-cp",
            ";".join([str(out), str(lab.RES)] + lab.JARS), "com.lotusblight.overlay.GameMain"] + args
    log = open(lab.OUT / "last_run.log", "wb")
    subprocess.Popen(java, cwd=str(lab.ROOT), stdout=log, stderr=subprocess.STDOUT, creationflags=lab.DETACHED)
    return 0


if __name__ == "__main__":
    sys.exit(main())
