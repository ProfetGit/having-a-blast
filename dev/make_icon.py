#!/usr/bin/env python3
"""Compose the mod icon from Blockbench renders in dev/icon/frames/ (transparent 1600px PNGs, one per frame).

Outputs go to dev/icon/out/ (./gradlew dist wipes dist/): icon-animated.gif (Modrinth, <= 256 KiB), icon-512.png,
plus src/main/resources/assets/havingablast/icon.png (the mod icon in the jars) and dev/icon/contact.png.
Scene + animation: dev/icon/build_scene.js -> dev/icon/having_a_blast_icon.bbmodel (sprites in dev/icon/sprites).
Background: dev/icon/sprites/bg_flat.png (64px, flat colour + ground shadow placed for CROP).

  python3 dev/make_icon.py             # the icon
  python3 dev/make_icon.py --options   # dev/icon/bg_options.png: candidate backgrounds with their GIF sizes
"""
import io
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parent.parent
ICON = ROOT / "dev" / "icon"
OUT = ICON / "out"
BACKGROUND = ICON / "sprites" / "bg_flat.png"
MOD_ICON = ROOT / "src/main/resources/assets/havingablast/icon.png"
S = 512
FPS = 25
STILL_FRAME = 0
GIF_SIZE = 256
GIF_LIMIT = 256 * 1024
PALETTE = 255
# fixed crop of the 1600px renders: holds the landed blocks and the fireball; boom crumbs and the TNT's drop may leave it
CROP = (435, 470, 1155, 1190)
OUTLINE = 19
MOD_ICON_BOX = (100, 100, 420, 420)   # the mod icon is the rest pose, cut tighter out of the 512px still

# candidate backgrounds for --options: (name, flat, shadow)
OPTIONS = [("dusk blue", "#35508A", "#2A4072"), ("night navy", "#23305E", "#1A2449"),
           ("slate teal", "#2A5C66", "#204950"), ("plum", "#4A3470", "#3A285A")]
SHADOW = (32, 48, 28, 8)   # ellipse centre x, y, radii on the 64px grid


def flat_bg(flat: str, shadow: str) -> Image.Image:
    im = Image.new("RGBA", (64, 64), flat)
    d = ImageDraw.Draw(im)
    cx, cy, a, b = SHADOW
    for y in range(64):
        for x in range(64):
            if ((x + 0.5 - cx) / a) ** 2 + ((y + 0.5 - cy) / b) ** 2 <= 1:
                d.point((x, y), shadow)
    return im.resize((S, S), Image.NEAREST)


def compose(art: Image.Image, bg: Image.Image) -> Image.Image:
    outline_layer = Image.new("RGBA", (S, S), (10, 12, 18, 0))
    outline_layer.putalpha(art.getchannel("A").filter(ImageFilter.MaxFilter(OUTLINE)))
    return Image.alpha_composite(Image.alpha_composite(bg, outline_layer), art)


def gif_bytes(frames: list) -> bytes:
    small = [f.convert("RGB").resize((GIF_SIZE, GIF_SIZE), Image.NEAREST) for f in frames]
    sheet = Image.new("RGB", (GIF_SIZE * len(small), GIF_SIZE))
    for i, f in enumerate(small):
        sheet.paste(f, (i * GIF_SIZE, 0))
    palette = sheet.quantize(colors=PALETTE, method=Image.Quantize.MEDIANCUT)
    gif = [f.quantize(palette=palette, dither=Image.Dither.NONE) for f in small]
    buf = io.BytesIO()
    gif[0].save(buf, "GIF", save_all=True, append_images=gif[1:], duration=1000 // FPS, loop=0, optimize=True, disposal=1)
    return buf.getvalue()


def arts() -> list:
    files = sorted((ICON / "frames").glob("frame_*.png"))
    if not files:
        sys.exit("no frames in dev/icon/frames - render them from Blockbench first")
    return [Image.open(f).convert("RGBA").crop(CROP).resize((S, S), Image.NEAREST) for f in files]


def options(art: list) -> None:
    picks = [0, 20, 24, 34, 50]
    cell = 200
    sheet = Image.new("RGB", (len(picks) * cell + 260, len(OPTIONS) * (cell + 24)), (24, 24, 30))
    d = ImageDraw.Draw(sheet)
    for r, (name, flat, shadow) in enumerate(OPTIONS):
        frames = [compose(a, flat_bg(flat, shadow)) for a in art]
        kib = len(gif_bytes(frames)) / 1024
        y = r * (cell + 24)
        for c, i in enumerate(picks):
            sheet.paste(frames[i].convert("RGB").resize((cell, cell), Image.LANCZOS), (c * cell, y))
        for k, back in enumerate(((20, 20, 24), (240, 240, 240))):
            x0 = len(picks) * cell + 20 + k * 120
            d.rectangle((x0, y, x0 + 110, y + 110), fill=back)
            sheet.paste(frames[picks[1]].convert("RGB").resize((96, 96), Image.LANCZOS), (x0 + 7, y + 7))
        d.text((6, y + cell + 4), f"{name}  {flat}  GIF {kib:.0f} KiB", fill=(230, 230, 235))
    out = ICON / "bg_options.png"
    sheet.save(out)
    print(out)


def main() -> None:
    art = arts()
    if "--options" in sys.argv:
        options(art)
        return
    bg = Image.open(BACKGROUND).convert("RGBA").resize((S, S), Image.NEAREST)
    frames = [compose(a, bg) for a in art]
    OUT.mkdir(exist_ok=True)
    still = frames[STILL_FRAME].convert("RGB")
    still.save(OUT / "icon-512.png", optimize=True)
    still.crop(MOD_ICON_BOX).resize((128, 128), Image.LANCZOS).save(MOD_ICON, optimize=True)
    data = gif_bytes(frames)
    out = OUT / "icon-animated.gif"
    out.write_bytes(data)

    cols = 10
    rows = (len(frames) + cols - 1) // cols
    contact = Image.new("RGB", (cols * 128, rows * 128))
    for i, f in enumerate(frames):
        contact.paste(f.convert("RGB").resize((128, 128), Image.LANCZOS), ((i % cols) * 128, (i // cols) * 128))
    contact.save(ICON / "contact.png")

    size = len(data)
    print(f"{len(frames)} frames @ {FPS} fps -> {out.relative_to(ROOT)} {size / 1024:.0f} KiB "
          f"({'OK' if size <= GIF_LIMIT else 'OVER'} Modrinth 256 KiB limit); still = frame {STILL_FRAME}")
    if size > GIF_LIMIT:
        sys.exit(1)


if __name__ == "__main__":
    main()
