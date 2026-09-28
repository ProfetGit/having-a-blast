#!/usr/bin/env python3
"""Contact sheet of a capture scene: frames every `step` ticks from the first frame that drew the blast (t = 0).
Usage: strip.py <capture>/<scene> <out.png> [--from -1 --to 40 --step 2 --cols 6 --width 320 --crop x0,y0,x1,y1]"""
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

TICK = 50_000_000


def main() -> None:
    d, out = Path(sys.argv[1]), Path(sys.argv[2])
    arg = lambda k, v: type(v)(sys.argv[sys.argv.index(k) + 1]) if k in sys.argv else v
    t0, t1, step, cols, width = arg("--from", -1.0), arg("--to", 40.0), arg("--step", 2.0), arg("--cols", 6), arg("--width", 320)
    crop = arg("--crop", "")
    lines = (d / "timing.txt").read_text().splitlines()
    head = lines[0].split()
    bf = int(head[head.index("blast_frame") + 1])
    rows = [l.split() for l in lines[1:]]
    zero = int(next(r for r in rows if int(r[0]) == max(bf, 0))[1])
    font = ImageFont.truetype("/usr/share/fonts/TTF/FiraSans-SemiBold.ttf", 14)
    tiles, want, i = [], t0, 0
    while want <= t1 + 1e-9:
        target = zero + int(want * TICK)
        while i + 1 < len(rows) and int(rows[i + 1][1]) <= target:
            i += 1
        r = rows[i]
        im = Image.open(d / f"f{int(r[0]):05d}.png").convert("RGB")
        if crop:
            im = im.crop(tuple(int(v) for v in crop.split(",")))
        im = im.resize((width, round(width * im.height / im.width)), Image.LANCZOS)
        ImageDraw.Draw(im).text((5, 3), f"t{(int(r[1]) - zero) / TICK:+.1f}  drawn {r[5]}", font=font, fill=(255, 255, 255), stroke_width=2, stroke_fill=(0, 0, 0))
        tiles.append(im)
        want += step
    w, h = tiles[0].size
    sheet = Image.new("RGB", (cols * w, ((len(tiles) + cols - 1) // cols) * h), (16, 16, 16))
    for k, t in enumerate(tiles):
        sheet.paste(t, ((k % cols) * w, (k // cols) * h))
    sheet.save(out)
    print(out, sheet.size)


if __name__ == "__main__":
    main()
