#!/usr/bin/env python3
"""Real-client GIFs from run.sh captures, aligned on the first frame that drew the blast (t = 0).

gif.py <cap>/<scene>[@x0:y0:x1:y1][,...] <out.gif|out.mp4> [--labels A,B,..] [--from -1] [--to 50] [--slow 1] [--width 480]
       [--cols n] [--fps 50] [--title "..."]
Panels side by side (n per row with --cols), each labelled; --slow k plays k times slower (real time sampled every
20/k ms). The capture's frames carry real timestamps (the client renders at ~120 fps), so the GIF is resampled at
--fps from them. Crop is in capture pixels (default: the whole frame).
"""
import subprocess
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

TICK = 50_000_000
FONT = "/usr/share/fonts/TTF/FiraSans-SemiBold.ttf"


def read(scene_dir: Path):
    lines = (scene_dir / "timing.txt").read_text().splitlines()
    head = lines[0].split()
    bf = int(head[head.index("blast_frame") + 1]) if "blast_frame" in head else 0
    rows = [(int(r[0]), int(r[1])) for r in (l.split() for l in lines[1:])]
    zero = next(ns for f, ns in rows if f == max(bf, 0))
    return rows, zero


def frames(scene_dir: Path, t_from: float, t_to: float, step_ns: int):
    rows, zero = read(scene_dir)
    out, i, t = [], 0, zero + int(t_from * TICK)
    while t <= zero + t_to * TICK:
        while i + 1 < len(rows) and rows[i + 1][1] <= t:
            i += 1
        out.append((rows[i][0], (t - zero) / TICK))
        t += step_ns
    return out


def main() -> None:
    args = sys.argv[1:]
    kw = {}
    pos = []
    i = 0
    while i < len(args):
        if args[i].startswith("--"):
            kw[args[i][2:]] = args[i + 1]
            i += 2
        else:
            pos.append(args[i])
            i += 1
    caps, out = pos[0].split(","), Path(pos[1])
    labels = kw.get("labels", "").split(",") if "labels" in kw else [""] * len(caps)
    t_from, t_to = float(kw.get("from", -1)), float(kw.get("to", 50))
    slow, width, fps = float(kw.get("slow", 1)), int(kw.get("width", 480)), float(kw.get("fps", 50))
    cols = int(kw.get("cols", len(caps)))
    title = kw.get("title", "")
    step_ns = int(1e9 / fps / slow)
    font = ImageFont.truetype(FONT, 18)
    small = ImageFont.truetype(FONT, 14)
    panels = []
    for c in caps:
        crop = None
        if "@" in c:
            c, cr = c.split("@")
            crop = tuple(int(v) for v in cr.split(":"))
        panels.append((Path(c), crop, frames(Path(c), t_from, t_to, step_ns)))
    n = min(len(p[2]) for p in panels)
    out_frames = []
    for k in range(n):
        tiles = []
        for (d, crop, fr), lab in zip(panels, labels):
            f, t = fr[k]
            im = Image.open(d / f"f{f:05d}.png").convert("RGB")
            if crop:
                im = im.crop(crop)
            im = im.resize((width, round(width * im.height / im.width)), Image.LANCZOS)
            dr = ImageDraw.Draw(im)
            if lab:
                dr.text((8, 5), lab, font=font, fill=(255, 255, 255), stroke_width=2, stroke_fill=(0, 0, 0))
            dr.text((8, im.height - 20), f"t {t:+.1f} ticks" + (f"   {slow:g}x slower" if slow != 1 else ""), font=small, fill=(255, 255, 255), stroke_width=2, stroke_fill=(0, 0, 0))
            tiles.append(im)
        w, h = tiles[0].size
        top = 30 if title else 0
        rows = (len(tiles) + cols - 1) // cols
        sheet = Image.new("RGB", (cols * w + (cols - 1) * 4, top + rows * h + (rows - 1) * 4), (20, 20, 24))
        if title:
            ImageDraw.Draw(sheet).text((8, 5), title, font=font, fill=(255, 255, 255))
        for j, t in enumerate(tiles):
            sheet.paste(t, ((j % cols) * (w + 4), top + (j // cols) * (h + 4)))
        out_frames.append(sheet if out.suffix == ".mp4" else sheet.quantize(colors=255, method=Image.Quantize.MEDIANCUT, dither=Image.Dither.NONE))
    out.parent.mkdir(parents=True, exist_ok=True)
    if out.suffix == ".mp4":
        w, h = out_frames[0].size
        w2, h2 = w + w % 2, h + h % 2
        proc = subprocess.Popen(["ffmpeg", "-y", "-loglevel", "error", "-f", "rawvideo", "-pix_fmt", "rgb24", "-s", f"{w2}x{h2}", "-r", f"{fps:g}", "-i", "-",
                                 "-c:v", "libx264", "-pix_fmt", "yuv420p", "-crf", "18", "-preset", "slow", str(out)], stdin=subprocess.PIPE)
        for fr in out_frames:
            if fr.size != (w2, h2):
                pad = Image.new("RGB", (w2, h2), (20, 20, 24))
                pad.paste(fr, (0, 0))
                fr = pad
            proc.stdin.write(fr.tobytes())
        proc.stdin.close()
        proc.wait()
    else:
        out_frames[0].save(out, save_all=True, append_images=out_frames[1:], duration=int(round(1000 / fps)), loop=0, optimize=True, disposal=1)
    print(out, f"{len(out_frames)} frames, {out.stat().st_size / 1e6:.1f} MB")


if __name__ == "__main__":
    main()
