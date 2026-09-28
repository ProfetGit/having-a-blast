#!/usr/bin/env python3
"""Pixel-art FX sheet for Having a Blast: src/main/resources/assets/havingablast/textures/fx/fx.png (256x768).

The round look (0.2.4, after Explosive Enhancement's style): colours painted into the frames, no ink outlines, and
every element animates by frames at one quad size, so its pixels never change size. 32 px cells (col, row):
  row 1        col 6 spark streak (greyscale, tinted), col 7 ember (greyscale, tinted)
  row 16-17    fireball frames 0-11: a lit sphere growing from a white-hot dot, full, paling, light lumps
  row 17-18    mushroom puff frames 0-11 (16 px art at 2x): hot yellow, orange, brick red, greys, breaking up
  row 19       dust puff frames 0-3 (greyscale, tinted): whole, then breaking up
  y 640-767    shockwave ring frames 0-7, 64 px cells, 4 per row
Also writes dev/fx/preview.png (the sheet at 2x on sky blue, plus tints) for review.
"""
import math
import random
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "src/main/resources/assets/havingablast/textures/fx/fx.png"


def streak(img, ox, oy):
    """A pointed spark streak, vertical, 32 long: bright core, softer edge."""
    for y in range(32):
        t = y / 31
        w = 3.2 * math.sin(math.pi * t) ** 0.7
        for x in range(32):
            d = abs(x + 0.5 - 16)
            if d <= w:
                v = 255 if d <= w * 0.45 else 210
                img.putpixel((ox + x, oy + y), (v, v, v, 255))


def ember(img, ox, oy):
    for (x, y) in [(15, 14), (16, 14), (14, 15), (15, 15), (16, 15), (17, 15), (14, 16), (15, 16), (16, 16), (17, 16), (15, 17), (16, 17)]:
        v = 255 if (x, y) in ((15, 15), (16, 15), (15, 16)) else 200
        img.putpixel((ox + x, oy + y), (v, v, v, 255))



# --- round look (0.2.4): colour baked in, no ink outlines, a fixed quad per element so the pixels never change size ---
BAYER = [[0, 8, 2, 10], [12, 4, 14, 6], [3, 11, 1, 9], [15, 7, 13, 5]]


def hexrgb(h):
    return tuple(int(h[i:i + 2], 16) for i in (1, 3, 5))


def dissolve(x, y, amount):
    """Ordered-dither dissolve: True where the pixel is gone at this amount (0..1)."""
    return BAYER[y % 4][x % 4] < amount * 16


def disc(cx, cy, r, X, Y):
    return (X - cx) ** 2 + (Y - cy) ** 2 <= r * r


# fireball palettes per stage: rim, body, shade, yellow, core
FIRE_HOT = ["#E8561C", "#FF9A24", "#F07A1E", "#FFD640", "#FFF6CF"]
FIRE_COOL = [["#E8702A", "#FFC266", "#F5A84A", "#FFE2A0", "#FFF6DE"],
             ["#D8A07A", "#F6DFC0", "#E8C8A6", "#FCEEDB", "#FFFFFF"],
             ["#A8A09C", "#D6D0CC", "#C2BCB8", "#EEEAE6", "#FFFFFF"],
             ["#A8A29E", "#D0CAC6", "#BEB8B4", "#E8E4E0", "#FFFFFF"]]


def fireball_frame(f):
    """12 frames: 0-5 grow from a white-hot dot, 6-7 full, 8-9 pale out, 10-11 light grey round lumps. A lit
    sphere: the bright bands sit up and to the left, a shade crescent lower right, a darker rim."""
    radii = [3.2, 5.6, 8.2, 10.6, 12.6, 14.0, 14.6, 15.0, 15.0, 15.0, 15.0, 15.0]
    R = radii[f]
    if f == 0:
        pal = ["#FFD640", "#FFF6CF", "#FFE88A", "#FFFFFF", "#FFFFFF"]
    elif f == 1:
        pal = ["#FF9A24", "#FFE070", "#FFC640", "#FFF6CF", "#FFFFFF"]
    elif f < 8:
        pal = FIRE_HOT
    else:
        pal = FIRE_COOL[f - 8]
    rim, body, shade, yellow, core = (hexrgb(c) for c in pal)
    c = 15.5
    if f >= 10:
        rnd = random.Random(f)
        n, dist, rr = (6, 7.5, (5.2, 6.4)) if f == 10 else (4, 10.5, (3.0, 4.0))
        a0 = rnd.uniform(0, 6.28)
        lumps = [(c + math.cos(a0 + i * 6.283 / n) * dist * rnd.uniform(0.8, 1.05),
                  c + math.sin(a0 + i * 6.283 / n) * dist * rnd.uniform(0.8, 1.05) - (f - 9) * 1.2, rnd.uniform(*rr)) for i in range(n)]
        if f == 10:
            lumps.append((c, c - 0.5, 6.0))
        out = {}
        for y in range(32):
            for x in range(32):
                X, Y = x + 0.5, y + 0.5
                inside = [l for l in lumps if disc(l[0], l[1], l[2] - 0.3, X, Y)]
                if not inside:
                    continue
                edge = not any(disc(l[0], l[1], l[2] - 1.3, X, Y) for l in lumps)
                lit = any(disc(l[0] - 0.3 * l[2], l[1] - 0.35 * l[2], 0.5 * l[2], X, Y) for l in inside)
                out[(x, y)] = rim if edge else yellow if lit else body
        return out
    ky = [0.74, 0.74, 0.72, 0.7, 0.68, 0.66, 0.64, 0.6, 0.5, 0.42][f]
    kc = [0.5, 0.5, 0.46, 0.44, 0.42, 0.4, 0.36, 0.3, 0.24, 0.2][f]
    out = {}
    for y in range(32):
        for x in range(32):
            X, Y = x + 0.5, y + 0.5
            if not disc(c, c, R - 0.3, X, Y):
                continue
            if not disc(c, c, R - 1.3, X, Y):
                col = rim
            elif disc(c - 0.16 * R, c - 0.18 * R, kc * R, X, Y):
                col = core
            elif disc(c - 0.1 * R, c - 0.12 * R, ky * R, X, Y):
                col = yellow
            elif not disc(c - 0.08 * R, c - 0.1 * R, 0.96 * R, X, Y):
                col = shade
            else:
                col = body
            out[(x, y)] = col
    return out


def ring_frame(f):
    """8 frames of a flat shockwave, 64 px: a yellow front with an orange trail, thinning, then red and broken."""
    r = [9, 15.5, 20.5, 24.5, 27.5, 29.5, 30.5, 31.2][f]
    thick = [6.0, 5.2, 4.6, 4.0, 3.4, 2.8, 2.2, 1.6][f]
    front, trail = [("#FFF2A0", "#FFD640"), ("#FFE670", "#FFB030"), ("#FFD640", "#FF9A24"), ("#FFC23A", "#FF8420"),
                    ("#FFA830", "#F06A1E"), ("#FF8A24", "#D8461C"), ("#E8561C", "#B8341A"), ("#C83A1A", "#962A18")][f]
    front, trail = hexrgb(front), hexrgb(trail)
    rnd = random.Random(f * 13 + 5)
    gaps = [rnd.uniform(0, 2 * math.pi) for _ in range([0, 0, 0, 0, 0, 2, 4, 7][f])]
    out = {}
    for y in range(64):
        for x in range(64):
            X, Y = x + 0.5 - 32, y + 0.5 - 32
            d = math.hypot(X, Y)
            if r - thick <= d <= r:
                a = math.atan2(Y, X) % (2 * math.pi)
                if any(abs((a - g + math.pi) % (2 * math.pi) - math.pi) < 0.2 for g in gaps):
                    continue
                out[(x, y)] = front if d >= r - max(1.0, thick * 0.5) else trail
    return out


PUFF_RAMP = ["#FFF0A8", "#FFC23A", "#FF9A30", "#EC6E3A", "#C4543E", "#9A6A5E",
             "#8A8482", "#A19D9A", "#B6B2AF", "#BEBAB7", "#C6C2BF", "#CECAC7"]


def puff_circles(seed):
    rnd = random.Random(seed)
    cs = [(8, 8.4, 5.6)]
    a0 = rnd.uniform(0, 2 * math.pi)
    for i in range(3):
        a = a0 + i * math.pi * 2 / 3 + rnd.uniform(-0.3, 0.3)
        cs.append((8 + math.cos(a) * 3.4, 8.2 + math.sin(a) * 2.8, rnd.uniform(3.0, 3.6)))
    return cs


def puff_frame(f, seed=7, tint=None):
    """12 frames on a 16 px grid (drawn at 2x): a round clump that pops from hot yellow through orange and brick red
    to a dark then light grey, then breaks into round bits. Shaded as a lump: light upper left, shade lower right."""
    base = puff_circles(seed)
    grow = [0.62, 0.8, 0.92, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0][f]
    if f <= 8:
        cs = [(8 + (x - 8) * grow, 8.5 + (y - 8.5) * grow, r * grow) for x, y, r in base]
    else:
        stage = f - 8
        rnd = random.Random(seed * 5 + stage)
        keep = sorted(base, key=lambda c: -c[2])[:[4, 3, 2][stage - 1]]
        spread, shrink = [0.35, 0.8, 1.2][stage - 1], [0.8, 0.6, 0.42][stage - 1]
        cs = [(8 + (x - 8) * (1 + spread) + rnd.uniform(-0.4, 0.4), 8.5 + (y - 8.5) * (1 + spread) - stage * 0.8, max(1.0, r * shrink)) for x, y, r in keep]
    body = hexrgb(tint or PUFF_RAMP[f])
    hi = tuple(min(255, int(v * 1.12 + 14)) for v in body)
    sh = tuple(int(v * 0.9) for v in body)
    out = {}
    for y in range(16):
        for x in range(16):
            X, Y = x + 0.5, y + 0.5
            if not any(disc(cx, cy, r - 0.2, X, Y) for cx, cy, r in cs):
                continue
            lit = any(disc(cx - 0.9, cy - 0.9, r - 0.6, X, Y) for cx, cy, r in cs)
            dark = not any(disc(cx - 0.5, cy - 0.7, r, X, Y) for cx, cy, r in cs)
            col = sh if dark else hi if not any(disc(cx + 0.7, cy + 0.8, r, X, Y) for cx, cy, r in cs) and lit else body
            out[(x, y)] = col
    return {(x * 2 + dx, y * 2 + dy): c for (x, y), c in out.items() for dx in (0, 1) for dy in (0, 1)}


def put(img, ox, oy, px):
    for (x, y), c in px.items():
        img.putpixel((ox + x, oy + y), c + (255,))


def round_fx(sheet):
    """y 512+: row 16 fireball 0-7, row 17 fireball 8-11 + puff 0-3, row 18 puff 4-11, row 19 dust 0-3 (greyscale),
    y 640+: 64 px ring frames 0-7 (4 per row)."""
    cells = [(c, r) for r in (16, 17, 18, 19) for c in range(8)]
    for f in range(12):
        c, r = cells[f]
        put(sheet, c * 32, r * 32, fireball_frame(f))
    for f in range(12):
        c, r = cells[12 + f]
        put(sheet, c * 32, r * 32, puff_frame(f))
    for k, f in enumerate((6, 9, 10, 11)):
        c, r = cells[24 + k]
        put(sheet, c * 32, r * 32, puff_frame(f, seed=11, tint="#FFFFFF"))
    for f in range(8):
        put(sheet, (f % 4) * 64, 640 + (f // 4) * 64, ring_frame(f))


def main() -> None:
    sheet = Image.new("RGBA", (256, 768), (0, 0, 0, 0))
    streak(sheet, 6 * 32, 32)
    ember(sheet, 7 * 32, 32)
    round_fx(sheet)
    OUT.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(OUT)
    tints = [(255, 255, 255), (255, 150, 40), (240, 240, 236), (216, 205, 187)]
    prev = Image.new("RGBA", (256 * 2 + 16, 768 * 2 + 16), (112, 162, 232, 255))
    for k, t in enumerate(tints):
        tinted = Image.new("RGBA", sheet.size)
        tp = tinted.load()
        sp = sheet.load()
        for y in range(sheet.height):
            for x in range(sheet.width):
                r, g, b, a = sp[x, y]
                tp[x, y] = (r * t[0] // 255, g * t[1] // 255, b * t[2] // 255, a)
        prev.alpha_composite(tinted, ((k % 2) * 272, (k // 2) * 784))
    prev.resize((prev.width * 2, prev.height * 2), Image.NEAREST).save(Path(__file__).parent / "preview.png")
    print("wrote", OUT)


if __name__ == "__main__":
    main()
