#!/usr/bin/env python3
"""Boot a real dedicated server with the Having a Blast jar on every loader × release and check it loaded.

smoke.py [--mc 26.2,26.3] [--loader fabric,quilt,neoforge,forge] [-j 4] [--cmds file]

A run passes when the server reaches "Done", logs "Having a Blast loaded on <loader>", answers the probe commands
and prints no WARN/ERROR line beyond known loader noise (ModJar/smoke.py NOISE). No Fabric API: the mod doesn't need it.
The launchers come from ModJar/smoke.py (ModrinthApp vanilla + Fabric, Tidy Pockets' Forge/NeoForge/Quilt installs).
Work dirs: dev/server/.work/<mc>-<loader>/server.log.
"""
import argparse
import re
import shutil
import subprocess
import sys
import time
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
sys.path.insert(0, str(ROOT.parents[1] / "tools/ModJar"))
import importlib.util  # noqa: E402

# ModJar/smoke.py (launchers, noise filter) under its own name, so this file can be imported as "smoke" too
_spec = importlib.util.spec_from_file_location("modjar_smoke", ROOT.parents[1] / "tools/ModJar" / "smoke.py")
ms = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(ms)

FAMILY = {"fabric": "fabric", "quilt": "fabric", "neoforge": "neoforge", "forge": "forge"}


def jar(mc: str, loader: str) -> Path:
    found = sorted((ROOT / "dist").glob(f"havingablast-*+{mc}-{FAMILY[loader]}.jar"))
    if len(found) != 1:
        sys.exit(f"expected one {mc}-{FAMILY[loader]} jar in {ROOT / 'dist'} (./gradlew dist), found {[f.name for f in found]}")
    return found[0]


def run(mc: str, loader: str, port: int, cmds: list[str], props: str = "", extra_jvm=()) -> tuple[bool, list[str], Path]:
    work = HERE / ".work" / f"{mc}-{loader}"
    shutil.rmtree(work, ignore_errors=True)
    (work / "mods").mkdir(parents=True)
    (work / "tmp").mkdir()
    (work / "eula.txt").write_text("eula=true\n")
    (work / "server.properties").write_text(
        "online-mode=false\nserver-ip=127.0.0.1\n"
        f"server-port={port}\nlevel-type=minecraft\\:flat\ngenerate-structures=false\n"
        "view-distance=3\nsimulation-distance=3\nspawn-protection=0\ndifficulty=peaceful\n"
        "sync-chunk-writes=false\nenable-query=false\nenable-rcon=false\n" + props)
    shutil.copy(jar(mc, loader), work / "mods")
    log = work / "server.log"
    cmd = ms.command(mc, loader, work)
    if extra_jvm:
        cmd = cmd[:1] + list(extra_jvm) + cmd[1:]
    (work / "launch.txt").write_text(" ".join(cmd) + "\n")
    notes = []
    with open(log, "w") as out:
        proc = subprocess.Popen(cmd, cwd=work, stdin=subprocess.PIPE, stdout=out, stderr=subprocess.STDOUT, text=True)
        try:
            if not ms.wait_for(log, proc, r"Done \(\d", ms.BOOT_TIMEOUT):
                return False, [f"server did not finish starting (exit {proc.poll()})"] + ms.tail(log), log
            for c in cmds + ["say hab-smoke-end"]:
                proc.stdin.write(c + "\n")
            proc.stdin.flush()
            if not ms.wait_for(log, proc, r"\[Server\] hab-smoke-end", 120):
                notes.append("commands got no answer within 120 s")
            proc.stdin.write("stop\n")
            proc.stdin.flush()
            try:
                proc.wait(90)
            except subprocess.TimeoutExpired:
                notes.append("server did not stop within 90 s")
        finally:
            if proc.poll() is None:
                proc.kill()
                proc.wait()
    text = log.read_text(errors="replace")
    ok = not notes
    if not re.search(rf"Having a Blast loaded on {'fabric' if loader in ('fabric', 'quilt') else loader}", text):
        ok = False
        notes.append("FAIL no 'Having a Blast loaded' line")
    for line in text.splitlines():
        if not ms.PROBLEM.search(line) or any(re.search(n, line) for n in ms.NOISE):
            continue
        ok = False
        notes.append("LOG " + line.strip()[:300])
    return ok, notes, log


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--mc", default="26.2,26.3")
    ap.add_argument("--loader", default="fabric,quilt,neoforge,forge")
    ap.add_argument("-j", type=int, default=4)
    ap.add_argument("--cmds", help="file with console commands to run after boot")
    a = ap.parse_args()
    cmds = [l for l in Path(a.cmds).read_text().splitlines() if l.strip() and not l.startswith("//")] if a.cmds else []
    combos = [(m, l) for m in a.mc.split(",") if m for l in a.loader.split(",") if l]

    def one(i_combo):
        i, (m, l) = i_combo
        t = time.time()
        with ms.slots.slot("server", f"HavingABlast smoke {m}-{l}"):  # machine-wide server slot (ModTest/slots.py)
            ok, notes, log = run(m, l, 25710 + i, cmds)
        with ms.PRINT:
            print(f"{'PASS' if ok else 'FAIL'} {m} {l} ({time.time() - t:.0f} s)  {log}")
            for n in notes:
                print("     " + n)
        return ok

    with ThreadPoolExecutor(max_workers=max(1, a.j)) as ex:
        results = list(ex.map(one, enumerate(combos)))
    print(f"{sum(results)}/{len(results)} passed")
    sys.exit(0 if all(results) else 1)


if __name__ == "__main__":
    main()
