#!/usr/bin/env python3
"""Frame-time report of a capture scene (run with FRAMES=0 so screenshots don't cost time).
perf.py <cap>/<scene> [<cap2>/<scene> ...] [--from 0 --to 120]  (ticks after the first blast frame)
Prints per capture: frames, mean fps, p50/p95/p99/max frame time (ms), frames over 16.7 ms and 33 ms, peak debris drawn,
and the mod's own render-thread time per frame (mean, p99, max ms; timing column 8, when present)."""
import sys
from pathlib import Path


def pct(v, p):
    v = sorted(v)
    return v[min(len(v) - 1, int(round(p / 100 * (len(v) - 1))))]


def main() -> None:
    args = sys.argv[1:]
    t0 = float(args[args.index("--from") + 1]) if "--from" in args else 0
    t1 = float(args[args.index("--to") + 1]) if "--to" in args else 120
    caps = [a for i, a in enumerate(args) if not a.startswith("--") and (i == 0 or not args[i - 1].startswith("--"))]
    print(f"{'capture':44} {'frames':>6} {'fps':>6} {'p50':>6} {'p95':>6} {'p99':>6} {'max':>6} {'>16.7':>6} {'>33':>5} {'debris':>6} {'mod ms: mean':>12} {'p99':>6} {'max':>6}")
    for c in caps:
        d = Path(c)
        lines = (d / "timing.txt").read_text().splitlines()
        head = lines[0].split()
        bf = int(head[head.index("blast_frame") + 1])
        rows = [l.split() for l in lines[1:]]
        zero = int(rows[max(bf, 0)][1])
        sel = [r for r in rows if t0 * 5e7 <= int(r[1]) - zero <= t1 * 5e7]
        ns = [int(r[1]) for r in sel]
        dt = [(b - a) / 1e6 for a, b in zip(ns, ns[1:])]
        if not dt:
            print(c, "no frames")
            continue
        drawn = max(int(r[5]) for r in sel)
        fps = len(dt) / (sum(dt) / 1000)
        mod = [int(r[8]) / 1000 for r in sel if len(r) > 8]
        ms = f"{sum(mod) / len(mod):12.3f} {pct(mod, 99):6.2f} {max(mod):6.2f}" if mod else ""
        print(f"{c[-44:]:44} {len(dt):6d} {fps:6.0f} {pct(dt, 50):6.1f} {pct(dt, 95):6.1f} {pct(dt, 99):6.1f} {max(dt):6.1f} {sum(x > 16.7 for x in dt):6d} {sum(x > 33.4 for x in dt):5d} {drawn:6d} {ms}")


if __name__ == "__main__":
    main()
