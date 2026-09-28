#!/usr/bin/env python3
"""Explosion FX sprites for the icon, cut from the mod's own FX sheet (dev/fx/make_fx.py -> textures/fx/fx.png) and
tinted with the colours Boom.java uses, so the icon's boom looks like the in-game one.

  python3 dev/icon/fx_sprites.py   -> dev/icon/sprites/fx_*.png
"""
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
SHEET = ROOT / "src/main/resources/assets/havingablast/textures/fx/fx.png"
OUT = Path(__file__).resolve().parent / "sprites"

HOT, YELLOW, ORANGE, RED = 0xFFFFFF, 0xFFD83A, 0xFF9A1F, 0xE8531A
SMOKE, RING = 0xF4F4F0, 0xF6F2EA


def cell(sheet, col, row, size=32, y0=0):
    return sheet.crop((col * size, y0 + row * size, (col + 1) * size, y0 + (row + 1) * size))


def tint(im, rgb):
    r, g, b = rgb >> 16, (rgb >> 8) & 255, rgb & 255
    px = im.load()
    out = Image.new("RGBA", im.size)
    o = out.load()
    for y in range(im.height):
        for x in range(im.width):
            v, _, _, a = px[x, y]
            if a:
                o[x, y] = (v * r // 255, v * g // 255, v * b // 255, 255)
    return out


def main():
    sheet = Image.open(SHEET).convert("RGBA")
    OUT.mkdir(exist_ok=True)
    made = {}
    for s in range(2):
        made[f"fx_fire_white_{s}"] = tint(cell(sheet, s, 0), HOT)
        made[f"fx_fire_yellow_{s}"] = tint(cell(sheet, s, 0), YELLOW)
        made[f"fx_fire_orange_{s}"] = tint(cell(sheet, s, 0), ORANGE)
        made[f"fx_fire_red_{s}"] = tint(cell(sheet, 4 + s, 0), RED)
        made[f"fx_smoke_{s}"] = tint(cell(sheet, s, 1), SMOKE)
    made["fx_dust"] = tint(cell(sheet, 4, 1), 0xDCD2C2)
    for f in range(4):
        made[f"fx_star_{f}"] = tint(cell(sheet, 4 + f, 3), HOT)
    for f in range(4):
        made[f"fx_ring_{f}"] = tint(cell(sheet, f, 0, 64, 128), RING)
    for name, im in made.items():
        im.save(OUT / f"{name}.png")
    print(f"{len(made)} sprites -> {OUT.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
