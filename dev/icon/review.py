#!/usr/bin/env python3
"""Texture review sheet: every sprite plus an isometric mockup of the icon's block cast (Python approximation)."""
import sys
from pathlib import Path

from PIL import Image, ImageDraw

sys.path.insert(0, str(Path.home() / "Projects/Minecraft Datapacks/.claude/skills/pack-icon-animation/assets"))
import texture_review as tr

SPR = Path(__file__).resolve().parent / "sprites"
CAST = [((1, 0, 0), "grass"), ((0, 0, 0), "grass"), ((0, 0, -1), "stone"), ((0, 1, -1), "tnt")]
FACES = {"grass": ("grass_top", "grass_side_left", "grass_side_right"), "stone": ("stone", "stone_left", "stone_right"),
         "tnt": ("tnt_top", "tnt_side_left", "tnt_side_right")}


def mock(sprites, size=(560, 470), scale=100):
    im = Image.new("RGBA", size, tr.BG)
    off = (size[0] // 2 - 20, int(size[1] * 0.78))
    for g, kind in sorted(CAST, key=lambda c: tr.proj((c[0][0] + .5, c[0][1] + .5, c[0][2] + .5), 1)[2] * 10 + c[0][1]):
        t, l, r = (sprites[n] for n in FACES[kind])
        x, y, z = g[0] - 0.5, g[1], g[2] - 0.5
        tr.face(im, l, [(x, y + 1, z), (x, y + 1, z + 1), (x, y, z)], scale, off)
        tr.face(im, r, [(x, y + 1, z + 1), (x + 1, y + 1, z + 1), (x, y, z + 1)], scale, off)
        tr.face(im, t, [(x, y + 1, z), (x + 1, y + 1, z), (x, y + 1, z + 1)], scale, off)
    return im


def main(out):
    files = sorted(p for p in SPR.glob("*.png") if not p.name.startswith(("bg_", "banner_")))
    sprites = {p.stem: Image.open(p).convert("RGBA") for p in files}
    cols, cell = 8, 150
    rows = (len(files) + cols - 1) // cols
    W = cols * cell + 580
    sheet = Image.new("RGBA", (W, max(rows * (cell + 16), 480) + 330), tr.BG)
    d = ImageDraw.Draw(sheet)
    for i, (name, im) in enumerate(sprites.items()):
        k = max(1, 128 // max(im.width, im.height))
        x, y = (i % cols) * cell + 10, (i // cols) * (cell + 16) + 10
        sheet.alpha_composite(im.resize((im.width * k, im.height * k), Image.NEAREST), (x, y))
        d.text((x, y + 132), name, fill=(220, 224, 235, 255))
    sheet.alpha_composite(mock(sprites), (cols * cell + 10, 10))
    y = sheet.height - 310
    for n, k in (("banner_title_top", 6), ("banner_title", 10), ("banner_tagline", 5)):
        im = Image.open(SPR / f"{n}.png").convert("RGBA")
        sheet.alpha_composite(im.resize((im.width * k, im.height * k), Image.NEAREST), (20, y))
        y += im.height * k + 12
    sheet.save(out)
    print(out)


main(sys.argv[1])
