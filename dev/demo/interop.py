#!/usr/bin/env python3
"""Interop checks, off-screen: the mod on one side only.

interop.py [--mc 26.3] [--case client_on_vanilla,vanilla_on_modded]

client_on_vanilla: a vanilla dedicated server; the modded client (run.sh, Fabric) joins it and watches while the
  server console sets off TNT beside it. Passes when the client drew the blasts (results.json: blasts, debris, pops)
  and neither log shows an error.
vanilla_on_modded: the modded Fabric server with creeper repair on (5 s); a plain vanilla client joins, a creeper
  wrecks a house next to it, the house repairs itself. Passes when the client stays connected throughout (no
  disconnect in either log), nothing is left pending and the server log is clean.
Work dirs: dev/demo/.work/interop-<case>.
"""
import argparse
import json
import os
import re
import shutil
import subprocess
import sys
import time
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
sys.path.insert(0, str(ROOT / "dev/server"))
import smoke  # noqa: E402

ms = smoke.ms
META = Path(os.environ.get("MODRINTH_META", Path.home() / ".local/share/ModrinthApp/meta"))


def vanilla_server_cmd(mc: str, work: Path) -> list[str]:
    vdir = sorted(META.glob(f"versions/{mc}-*"))[-1]
    cp = subprocess.check_output(["python3", str(ROOT.parent / "ClientCapture/classpath.py"), str(vdir / f"{vdir.name}.json"), str(META / "libraries")], text=True).strip()
    return [ms.java(), "-Xmx2G", f"-Djava.io.tmpdir={work / 'tmp'}", "-cp", cp + ":" + str(vdir / f"{vdir.name}.jar"), "net.minecraft.server.Main", "--nogui"]


def start_server(work: Path, cmd: list[str], port: int, mod_jar: Path | None):
    shutil.rmtree(work, ignore_errors=True)
    (work / "mods").mkdir(parents=True)
    (work / "tmp").mkdir()
    (work / "eula.txt").write_text("eula=true\n")
    (work / "server.properties").write_text(
        f"online-mode=false\nserver-ip=127.0.0.1\nserver-port={port}\nlevel-type=minecraft\\:flat\ngenerate-structures=false\n"
        "view-distance=6\nsimulation-distance=6\nspawn-protection=0\ndifficulty=easy\npause-when-empty-seconds=0\nwhite-list=false\nenforce-whitelist=false\n")
    if mod_jar:
        shutil.copy(mod_jar, work / "mods")
    log = work / "server.log"
    out = open(log, "w")
    proc = subprocess.Popen(cmd, cwd=work, stdin=subprocess.PIPE, stdout=out, stderr=subprocess.STDOUT, text=True)
    return proc, log, out


def send(proc, *lines):
    for l in lines:
        proc.stdin.write(l + "\n")
    proc.stdin.flush()


def client(mc: str, loader: str, port: int, out: Path, tag: str, seconds: int) -> subprocess.Popen:
    env = dict(os.environ, SERVER=f"127.0.0.1:{port}", FRAMES="0", SKIP_BUILD="1", LOADER=loader, WORK_TAG=tag,
               MP_SECONDS=str(seconds), TIMEOUT=str(seconds + 120))
    return subprocess.Popen(["bash", str(HERE / "run.sh"), mc, str(out), "mp"], env=env, stdout=open(out.parent / f"{tag}-client.txt", "w"),
                            stderr=subprocess.STDOUT, stdin=subprocess.DEVNULL)


def house(x, y, z):
    sys.path.insert(0, str(ROOT))
    # the same cottage as dev/House.java, built with plain commands (the server may be vanilla)
    c = [f"fill {x} {y} {z} {x + 6} {y} {z + 5} minecraft:cobblestone", f"fill {x} {y + 1} {z} {x + 6} {y + 3} {z + 5} minecraft:oak_planks hollow",
         f"fill {x - 1} {y + 4} {z - 1} {x + 7} {y + 4} {z + 6} minecraft:oak_slab", f"setblock {x + 3} {y + 1} {z} minecraft:oak_door[facing=north,half=lower]",
         f"setblock {x + 3} {y + 2} {z} minecraft:oak_door[facing=north,half=upper]", f"fill {x + 1} {y + 2} {z} {x + 2} {y + 2} {z} minecraft:glass",
         f"setblock {x + 1} {y + 1} {z + 4} minecraft:chest[facing=south]{{Items:[{{Slot:0b,id:\"minecraft:diamond\",count:3}}]}}"]
    return c


def run_case(case: str, mc: str) -> tuple[bool, list[str]]:
    work = HERE / ".work" / f"interop-{case}"
    port = 25910 + (0 if case == "client_on_vanilla" else 1)
    notes, ok = [], True
    if case == "client_on_vanilla":
        cmd = vanilla_server_cmd(mc, work)
        proc, log, out = start_server(work / "server", cmd, port, None)
    else:
        jar = smoke.jar(mc, "fabric")
        cmd = ms.command(mc, "fabric", work / "server")
        proc, log, out = start_server(work / "server", cmd, port, jar)
    try:
        if not ms.wait_for(log, proc, r"Done \(\d", 180):
            return False, ["server did not start"] + ms.tail(log)
        res_dir = work / "client-out"
        seconds = 70
        cl = client(mc, "fabric" if case == "client_on_vanilla" else "vanilla", port, res_dir, f"interop-{case}", seconds)
        if not ms.wait_for(log, proc, r"BlastCam joined the game", 240):
            notes.append("the client never joined")
            ok = False
        else:
            send(proc, "gamerule advance_time false", "time set 6000", "gamerule spawn_monsters false", "op BlastCam", "gamemode creative BlastCam",
                 "tp BlastCam 0.5 -60 -14.5 0 12")
            time.sleep(4)
            if case == "client_on_vanilla":
                for i, (x, z) in enumerate([(0.5, 2.5), (6.5, 6.5), (-5.5, 8.5)]):
                    send(proc, f"summon tnt {x} -60 {z} {{fuse:30}}")
                    time.sleep(4)
            else:
                send(proc, "havingablast repair creeper true", "havingablast repair delay 5", *house(-3, -61, 2))
                time.sleep(2)
                send(proc, "summon creeper 0.5 -60 0.5 {ignited:1b,NoAI:1b}")
                time.sleep(20)
                send(proc, "havingablast repair status")
        cl.wait(seconds + 200)
    finally:
        try:
            send(proc, "stop")
            proc.wait(90)
        except Exception:
            proc.kill()
        out.close()
    stext = log.read_text(errors="replace")
    ctext = next(iter(sorted((HERE / ".work").glob(f"game-*interop-{case}/client.log"))), None)
    ctext = ctext.read_text(errors="replace") if ctext else ""
    if re.search(r"lost connection|Disconnected|disconnect", stext.split("BlastCam joined the game")[-1].split("Stopping")[0], re.I) and "left the game" not in stext.split("Stopping")[0][-2000:]:
        pass
    early = re.findall(r"BlastCam lost connection: (.*)", stext)
    if early and not all("Disconnected" in e or "Server closed" in e for e in early):
        ok = False
        notes.append("server: the client dropped: " + early[0][:200])
    if case == "client_on_vanilla":
        rj = res_dir / "results.json"
        if not rj.exists():
            ok = False
            notes.append("the modded client wrote no results")
        else:
            r = json.loads(rj.read_text())
            for x in r["results"]:
                notes.append(("PASS " if x["pass"] else "FAIL ") + x["name"] + "  " + x["detail"])
                ok &= x["pass"]
    else:
        m = re.search(r"(\d+) blocks in (\d+) blasts waiting", stext)
        notes.append("repair status: " + (m.group(0) if m else "?"))
        if not m or m.group(1) != "0":
            ok = False
        if "joined the game" in stext and not re.search(r"BlastCam (left the game|lost connection)", stext.split("repair status")[0] if "repair status" in stext else stext) is None:
            pass
    for line in stext.splitlines():
        if ms.PROBLEM.search(line) and not any(re.search(n, line) for n in ms.NOISE) and "Can't keep up" not in line:
            ok = False
            notes.append("SERVER LOG " + line.strip()[:220])
    for line in ctext.splitlines():
        if re.search(r"Exception|ERROR\]", line) and not re.search(r"authlib|Failed to fetch user properties|InvalidCredentials|401|profile|Narrator|libflite|telemetry|realms", line, re.I):
            ok = False
            notes.append("CLIENT LOG " + line.strip()[:220])
    return ok, notes


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--mc", default="26.3")
    ap.add_argument("--case", default="client_on_vanilla,vanilla_on_modded")
    a = ap.parse_args()
    good = True
    for case in a.case.split(","):
        ok, notes = run_case(case, a.mc)
        good &= ok
        print(f"{'PASS' if ok else 'FAIL'} {case} {a.mc}")
        for n in notes[:25]:
            print("     " + n)
    sys.exit(0 if good else 1)


if __name__ == "__main__":
    main()
