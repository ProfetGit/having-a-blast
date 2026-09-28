#!/usr/bin/env python3
"""Auto-repair tests on real dedicated servers: every loader × release (the mod's -Dhavingablast.test driver).

repairtest.py [--mc 26.2,26.3] [--loader fabric,quilt,neoforge,forge] [-j 3] [--only creeper,tnt]

Per server: boot with -Dhavingablast.test=1, `habtest run <all scenarios>`, collect "[habtest] PASS|FAIL" lines until
"[habtest] done"; then stop mid-repair (restart_a), boot the same world again and `habtest run restart_b`. A run
passes when every check passed and the log has no WARN/ERROR beyond known loader noise.
Work dirs: dev/server/.work/repair-<mc>-<loader>.
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
sys.path.insert(0, str(HERE))
import smoke  # noqa: E402

ms = smoke.ms
# The bed scenario force-loads fresh Nether chunks: under a busy machine their generation alone stalls the server for a
# couple of seconds. The rebuild's own cost is measured separately (big_crater/server_cost).
NOISE = [r"Can't keep up! Is the server overloaded"]
SCENARIOS = "defaults_off,creeper,creeper_charged,tnt,minecart,only_own_toggle,other_sources,player_block,mixed_chain,big_crater,decor,crystal,fireball,bed,anchor,wither,restart_a"


def boot(work: Path, mc: str, loader: str, port: int, fresh: bool):
    if fresh:
        shutil.rmtree(work, ignore_errors=True)
        (work / "mods").mkdir(parents=True)
        (work / "tmp").mkdir()
        (work / "eula.txt").write_text("eula=true\n")
        (work / "server.properties").write_text(
            "online-mode=false\nserver-ip=127.0.0.1\n"
            f"server-port={port}\nlevel-type=minecraft\\:flat\ngenerate-structures=false\n"
            "view-distance=4\nsimulation-distance=4\nspawn-protection=0\ndifficulty=easy\npause-when-empty-seconds=0\n"
            "sync-chunk-writes=false\nenable-query=false\nenable-rcon=false\n")
        shutil.copy(smoke.jar(mc, loader), work / "mods")
    cmd = ms.command(mc, loader, work)
    cmd = cmd[:1] + ["-Dhavingablast.test=1"] + cmd[1:]
    log = work / ("server.log" if fresh else "server2.log")
    out = open(log, "w")
    proc = subprocess.Popen(cmd, cwd=work, stdin=subprocess.PIPE, stdout=out, stderr=subprocess.STDOUT, text=True)
    return proc, log, out


def send(proc, *lines):
    try:
        for l in lines:
            proc.stdin.write(l + "\n")
        proc.stdin.flush()
    except (BrokenPipeError, OSError):
        pass


def stop(proc, out):
    try:
        send(proc, "stop")
        proc.wait(120)
    except Exception:
        proc.kill()
        proc.wait()
    try:
        proc.stdin.close()
    except (BrokenPipeError, OSError):
        pass
    out.close()


def run(mc: str, loader: str, port: int, scenarios: str):
    work = HERE / ".work" / f"repair-{mc}-{loader}"
    notes, ok = [], True
    proc, log, out = boot(work, mc, loader, port, True)
    try:
        if not ms.wait_for(log, proc, r"Done \(\d", ms.BOOT_TIMEOUT):
            return False, ["server did not start"] + ms.tail(log), log
        send(proc, "habtest run " + scenarios)
        if not ms.wait_for(log, proc, r"\[habtest\] done", 900):
            ok = False
            notes.append("no '[habtest] done' within 900 s")
        if "restart_a" in scenarios:
            send(proc, "save-all flush")
            time.sleep(3)
    finally:
        stop(proc, out)
    text = log.read_text(errors="replace")
    logs = [log]
    if "restart_a" in scenarios and "restart-ready" in text:
        proc, log2, out = boot(work, mc, loader, port, False)
        try:
            if ms.wait_for(log2, proc, r"Done \(\d", ms.BOOT_TIMEOUT):
                send(proc, "habtest run restart_b")
                if not ms.wait_for(log2, proc, r"\[habtest\] done", 300):
                    ok = False
                    notes.append("restart_b: no '[habtest] done'")
            else:
                ok = False
                notes.append("restart: server did not start again")
        finally:
            stop(proc, out)
        text += log2.read_text(errors="replace")
        logs.append(log2)
    checks = re.findall(r"\[habtest\] (PASS|FAIL) (\S+)\s+(.*)", text)
    for res, name, detail in checks:
        if res == "FAIL":
            ok = False
            notes.append(f"FAIL {name}  {detail[:300]}")
    passed = sum(1 for r, _, _ in checks if r == "PASS")
    notes.insert(0, f"{passed}/{len(checks)} checks passed")
    if not checks:
        ok = False
    for line in text.splitlines():
        if not ms.PROBLEM.search(line) or any(re.search(n, line) for n in ms.NOISE + NOISE):
            continue
        ok = False
        notes.append("LOG " + line.strip()[:300])
    return ok, notes, logs[0]


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--mc", default="26.2,26.3")
    ap.add_argument("--loader", default="fabric,quilt,neoforge,forge")
    ap.add_argument("-j", type=int, default=3)
    ap.add_argument("--only", default=SCENARIOS)
    a = ap.parse_args()
    combos = [(m, l) for m in a.mc.split(",") if m for l in a.loader.split(",") if l]

    def one(i_combo):
        i, (m, l) = i_combo
        t = time.time()
        with ms.slots.slot("server", f"HavingABlast repair {m}-{l}"):  # machine-wide server slot (ModTest/slots.py)
            ok, notes, log = run(m, l, 25810 + i, a.only)
        with ms.PRINT:
            print(f"{'PASS' if ok else 'FAIL'} {m} {l} ({time.time() - t:.0f} s)  {log}")
            for n in notes[:30]:
                print("     " + n)
        return ok

    with ThreadPoolExecutor(max_workers=max(1, a.j)) as ex:
        results = list(ex.map(one, enumerate(combos)))
    print(f"{sum(results)}/{len(results)} passed")
    sys.exit(0 if all(results) else 1)


if __name__ == "__main__":
    main()
