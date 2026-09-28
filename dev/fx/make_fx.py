#!/usr/bin/env python3
"""Pixel-art FX sheet for Having a Blast: src/main/resources/assets/havingablast/textures/fx/fx.png (256x256).

Greyscale sprites, tinted at runtime. Grid of 32 px cells (col, row) in the top half, 64 px ring cells below:
  row 0  cols 0-3  billow silhouettes, flat (fire layers)        cols 4-7  the same with a dark rim (outer fire layer)
  row 1  cols 0-3  smoke puffs, shaded (lobes, creases, rim)      cols 4-5  dust puffs   col 6 spark streak   col 7 ember
  row 2  breakup frames 1-3 of puff 0, 1-3 of puff 1, 1-2 of puff 2
  row 3  col 0 breakup 3 of puff 2, cols 1-3 breakup 1-3 of puff 3, cols 4-7 burst star frames 0-3
  y 128-255: rings, 64 px cells, 4 per row: frames 0-7 (thick, thinning, breaking up)
Also writes dev/fx/preview.png (the sheet at 4x on sky blue, plus fire/smoke/dust tints) for review.
Tones: highlight 255, body 232, shade 196, rim 92 (flat silhouettes: 255 only).
"""
import math
import random
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "src/main/resources/assets/havingablast/textures/fx/fx.png"
HI, BODY, SHADE, RIM = 255, 232, 196, 92


def inside(circles, X, Y):
    return any((X - cx) ** 2 + (Y - cy) ** 2 <= r * r for cx, cy, r in circles)


def mask(circles, size=32, holes=()):
    px = {(x, y) for y in range(size) for x in range(size)
          if inside(circles, x + 0.5, y + 0.5) and not inside(holes, x + 0.5, y + 0.5)}
    if holes:
        return px
    # fill pinholes: every empty pixel not reachable from the border belongs to the cloud
    outside, todo = set(), [(x, y) for x in range(size) for y in (0, size - 1)] + [(x, y) for y in range(size) for x in (0, size - 1)]
    while todo:
        p = todo.pop()
        if p in outside or p in px or not (0 <= p[0] < size and 0 <= p[1] < size):
            continue
        outside.add(p)
        todo += [(p[0] + 1, p[1]), (p[0] - 1, p[1]), (p[0], p[1] + 1), (p[0], p[1] - 1)]
    return {(x, y) for y in range(size) for x in range(size) if (x, y) not in outside}


def edge(px):
    return {p for p in px if any((p[0] + dx, p[1] + dy) not in px for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))}


def billow(seed, cx=16, cy=17.5, r=8.6, bumps=7, size=32):
    """Cauliflower cloud: a core disc and bumps around it, bigger on top."""
    rnd = random.Random(seed)
    circles = [(cx, cy, r)]
    a0 = rnd.uniform(0, math.pi * 2)
    for i in range(bumps):
        a = a0 + i * math.pi * 2 / bumps + rnd.uniform(-0.25, 0.25)
        up = -math.sin(a)  # 1 at the top
        br = r * (0.46 + 0.16 * up + rnd.uniform(-0.05, 0.08))
        d = r * (0.84 + rnd.uniform(-0.05, 0.06))
        circles.append((cx + math.cos(a) * d, cy + math.sin(a) * d * 0.92, br))
    return circles


def draw_flat(img, ox, oy, px, rim=False):
    e = edge(px)
    for (x, y) in px:
        v = RIM if rim and (x, y) in e else 255
        img.putpixel((ox + x, oy + y), (v, v, v, 255))


def draw_shaded(img, ox, oy, circles, px, light=(-0.62, -0.78)):
    """Clean cartoon cloud: dark rim, a 2 px highlight band along the upper-left edge, a 3 px shade band along the
    lower-right edge."""
    e = edge(px)
    lx, ly = light
    for (x, y) in px:
        X, Y = x + 0.5, y + 0.5
        if (x, y) in e:
            v = RIM
        elif (round(X + lx * 2.2 - 0.5), round(Y + ly * 2.2 - 0.5)) not in px:
            v = HI
        elif (round(X - lx * 3.0 - 0.5), round(Y - ly * 3.0 - 0.5)) not in px:
            v = SHADE
        else:
            v = BODY
        img.putpixel((ox + x, oy + y), (v, v, v, 255))


def breakup(circles, stage, seed, size=32):
    """Stage 1: the cloud splits into its big lobes, pulled apart; 2: three shrunken lobes rise and drift apart;
    3: two small wisps."""
    rnd = random.Random(seed * 31 + stage)
    cx = sum(c[0] for c in circles) / len(circles)
    cy = sum(c[1] for c in circles) / len(circles)
    lobes = sorted(circles[1:], key=lambda c: -c[2])
    if stage == 1:
        keep, spread, shrink, lift = lobes[:5] + [circles[0]], 0.22, 0.92, 0.6
    elif stage == 2:
        keep, spread, shrink, lift = lobes[:3], 0.55, 0.78, 2.2
    else:
        keep, spread, shrink, lift = lobes[:2], 0.8, 0.5, 4.0
    out = []
    for (x, y, r) in keep:
        if (x, y, r) == circles[0]:
            out.append((x, y + 0.5, r * 0.62))
            continue
        out.append((x + (x - cx) * spread + rnd.uniform(-0.5, 0.5), y + (y - cy) * spread * 0.6 - lift, max(1.6, r * shrink * (1.25 if stage > 1 else 1))))
    return out, []


def star(points, r_out, r_in, rot=0.0, cx=16, cy=16, size=32):
    poly = []
    for i in range(points * 2):
        a = rot + math.pi * i / points
        r = r_out if i % 2 == 0 else r_in
        poly.append((cx + math.cos(a) * r, cy + math.sin(a) * r))
    px = set()
    for y in range(size):
        for x in range(size):
            X, Y = x + 0.5, y + 0.5
            inside_ = False
            j = len(poly) - 1
            for i in range(len(poly)):
                xi, yi = poly[i]
                xj, yj = poly[j]
                if (yi > Y) != (yj > Y) and X < (xj - xi) * (Y - yi) / (yj - yi) + xi:
                    inside_ = not inside_
                j = i
            if inside_:
                px.add((x, y))
    return px


def ring(frame):
    """64 px shockwave ring: thick and solid first, thinning, then breaking into arcs."""
    thick = [9, 7, 5.5, 4.2, 3.2, 2.6, 2.0, 1.6][frame]
    outer = 31
    gaps = [0, 0, 0, 0, 3, 5, 7, 9][frame]
    rnd = random.Random(frame * 7 + 1)
    cuts = [rnd.uniform(0, 2 * math.pi) for _ in range(gaps)]
    width = 0.22 + 0.03 * frame
    px = set()
    for y in range(64):
        for x in range(64):
            X, Y = x + 0.5 - 32, y + 0.5 - 32
            d = math.hypot(X, Y)
            if outer - thick <= d <= outer:
                a = math.atan2(Y, X) % (2 * math.pi)
                if not any(abs((a - c + math.pi) % (2 * math.pi) - math.pi) < width for c in cuts):
                    px.add((x, y))
    return px


def draw_ring(img, ox, oy, px):
    """Ring body white with a darker inner edge, so it reads on bright ground too."""
    for (x, y) in px:
        X, Y = x + 0.5 - 32, y + 0.5 - 32
        inner = (x, y) in edge(px) and math.hypot(X, Y) < 29
        v = SHADE if inner else 255
        img.putpixel((ox + x, oy + y), (v, v, v, 255))


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


def main() -> None:
    sheet = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
    shapes = [billow(s, bumps=b) for s, b in ((3, 7), (11, 6), (29, 8), (47, 7))]
    for i, c in enumerate(shapes):
        px = mask(c)
        draw_flat(sheet, i * 32, 0, px)
        draw_flat(sheet, (4 + i) * 32, 0, px, rim=True)
        draw_shaded(sheet, i * 32, 32, c, px)
    for i, (s, b) in enumerate(((5, 6), (17, 5))):
        c = billow(s, cx=16, cy=18, r=6.2, bumps=b)
        draw_shaded(sheet, (4 + i) * 32, 32, c, mask(c))
    streak(sheet, 6 * 32, 32)
    ember(sheet, 7 * 32, 32)
    cells = [(c, 2) for c in range(8)] + [(c, 3) for c in range(4)]
    k = 0
    for i, c in enumerate(shapes):
        for stage in (1, 2, 3):
            circles, holes = breakup(c, stage, i)
            col, row = cells[k]
            draw_shaded(sheet, col * 32, row * 32, circles, mask(circles, holes=holes))
            k += 1
    bursts = [
        star(8, 13.5, 6.0, 0.2),
        star(10, 15.5, 8.5, 0.5),
        star(10, 15.8, 10.5, 0.8) - mask([(16, 16, 7.2)]),
        star(4, 15.5, 2.6, 0.785) | star(4, 9.5, 2.0, 0.0),
    ]
    for i, px in enumerate(bursts):
        draw_flat(sheet, (4 + i) * 32, 96, px)
    for f in range(8):
        draw_ring(sheet, (f % 4) * 64, 128 + (f // 4) * 64, ring(f))
    OUT.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(OUT)
    tints = [(255, 255, 255), (255, 150, 40), (240, 240, 236), (216, 205, 187)]
    prev = Image.new("RGBA", (256 * 2 + 16, 256 * 2 + 16), (112, 162, 232, 255))
    for k, t in enumerate(tints):
        tinted = Image.new("RGBA", sheet.size)
        tp = tinted.load()
        sp = sheet.load()
        for y in range(256):
            for x in range(256):
                r, g, b, a = sp[x, y]
                tp[x, y] = (r * t[0] // 255, g * t[1] // 255, b * t[2] // 255, a)
        prev.alpha_composite(tinted, ((k % 2) * 272, (k // 2) * 272))
    prev.resize((prev.width * 2, prev.height * 2), Image.NEAREST).save(Path(__file__).parent / "preview.png")
    print("wrote", OUT)


if __name__ == "__main__":
    main()
