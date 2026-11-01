"""Gera o ícone do mod em assets/langcraft/icon.png.

Rode com: python tools/generate_icon.py
"""
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ASSETS = Path(__file__).resolve().parent.parent / "common/src/main/resources/assets/langcraft"
GRID = 32
SCALE = 8
SIZE = GRID * SCALE

GRASS = (93, 145, 62)
GRASS_DARK = (74, 117, 49)
DIRT = (121, 85, 58)
DIRT_DARK = (94, 66, 45)
PAGE = (250, 244, 225)
PAGE_SHADE = (222, 212, 184)
RULE = (205, 217, 234)
MARGIN = (214, 90, 80)
INK = (38, 30, 24)
SPIRAL = (70, 70, 78)


def block_side(draw):
    """O fundo é um bloco de grama, para o ícone ser reconhecido como Minecraft."""
    for y in range(GRID):
        for x in range(GRID):
            noise = (x * 7 + y * 13 + x * y) % 5 == 0
            if y < 8 or (y == 8 and x % 3 != 0) or (y == 9 and x % 5 == 1):
                color = GRASS_DARK if noise else GRASS
            else:
                color = DIRT_DARK if noise else DIRT
            draw.rectangle((x * SCALE, y * SCALE, (x + 1) * SCALE - 1, (y + 1) * SCALE - 1), fill=color)


def cell(draw, x0, y0, x1, y1, color):
    draw.rectangle((x0 * SCALE, y0 * SCALE, x1 * SCALE - 1, y1 * SCALE - 1), fill=color)


def notebook(draw):
    cell(draw, 5, 4, 28, 29, PAGE_SHADE)
    cell(draw, 4, 3, 27, 28, PAGE)
    for y in range(8, 28, 4):
        cell(draw, 8, y, 27, y + 1, RULE)
    cell(draw, 8, 3, 9, 28, MARGIN)
    for y in range(5, 27, 4):
        cell(draw, 3, y, 6, y + 2, SPIRAL)


def text(draw, font_file, glyph, size, center):
    font = ImageFont.truetype(str(ASSETS / "font" / font_file), size * SCALE)
    box = draw.textbbox((0, 0), glyph, font=font)
    x = center[0] * SCALE - (box[0] + box[2]) / 2
    y = center[1] * SCALE - (box[1] + box[3]) / 2
    draw.text((x, y), glyph, font=font, fill=INK)


def main():
    image = Image.new("RGB", (SIZE, SIZE))
    draw = ImageDraw.Draw(image)
    block_side(draw)
    notebook(draw)
    text(draw, "hangul.ttf", "가", 15, (18, 14))
    text(draw, "japanese.ttf", "あ", 9, (18, 24))
    image.save(ASSETS / "icon.png")


if __name__ == "__main__":
    main()
