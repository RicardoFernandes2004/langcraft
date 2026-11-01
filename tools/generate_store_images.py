"""Gera o ícone e o banner das páginas do mod em docs/deploy/images.

Rode com: python tools/generate_store_images.py
Tudo é desenhado aqui, sem usar texturas ou fontes do Minecraft.
"""
import math
import random
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

import generate_icon

ROOT = Path(__file__).resolve().parent.parent
FONTS = ROOT / "common/src/main/resources/assets/langcraft/font"
OUT = ROOT / "docs/deploy/images"

ICON_SCALE = 16
WIDTH, HEIGHT = 1920, 640
CELL = 8

SKY_TOP = (38, 64, 122)
SKY_BOTTOM = (120, 178, 214)
CLOUD = (240, 245, 250)
CLOUD_SHADE = (205, 219, 233)
GRASS = (93, 145, 62)
GRASS_DARK = (74, 117, 49)
DIRT = (121, 85, 58)
DIRT_DARK = (94, 66, 45)
STONE = (112, 112, 116)
STONE_DARK = (92, 92, 97)
INK = (28, 24, 22)
PAPER = (250, 244, 225)
PAPER_SHADE = (222, 212, 184)
GOLD = (244, 197, 66)
WHITE = (255, 255, 255)
WHEEL = [(200, 50, 31), (244, 197, 66), (47, 111, 179), (93, 145, 62), (120, 60, 150), (230, 130, 40), (40, 170, 170), (240, 240, 235)]

GLYPHS = {
    "A": "01110 10001 10001 11111 10001 10001 10001", "B": "11110 10001 10001 11110 10001 10001 11110",
    "C": "01111 10000 10000 10000 10000 10000 01111", "D": "11110 10001 10001 10001 10001 10001 11110",
    "E": "11111 10000 10000 11110 10000 10000 11111", "F": "11111 10000 10000 11110 10000 10000 10000",
    "G": "01111 10000 10000 10011 10001 10001 01111", "H": "10001 10001 10001 11111 10001 10001 10001",
    "I": "11111 00100 00100 00100 00100 00100 11111", "J": "00111 00010 00010 00010 00010 10010 01100",
    "K": "10001 10010 10100 11000 10100 10010 10001", "L": "10000 10000 10000 10000 10000 10000 11111",
    "M": "10001 11011 10101 10101 10001 10001 10001", "N": "10001 11001 10101 10011 10001 10001 10001",
    "O": "01110 10001 10001 10001 10001 10001 01110", "P": "11110 10001 10001 11110 10000 10000 10000",
    "Q": "01110 10001 10001 10001 10101 10010 01101", "R": "11110 10001 10001 11110 10100 10010 10001",
    "S": "01111 10000 10000 01110 00001 00001 11110", "T": "11111 00100 00100 00100 00100 00100 00100",
    "U": "10001 10001 10001 10001 10001 10001 01110", "V": "10001 10001 10001 10001 10001 01010 00100",
    "W": "10001 10001 10001 10101 10101 11011 10001", "X": "10001 10001 01010 00100 01010 10001 10001",
    "Y": "10001 10001 01010 00100 00100 00100 00100", "Z": "11111 00001 00010 00100 01000 10000 11111",
    "a": "00000 00000 01110 00001 01111 10001 01111", "e": "00000 00000 01110 10001 11111 10000 01110",
    "l": "01100 00100 00100 00100 00100 00100 01110", "o": "00000 00000 01110 10001 10001 10001 01110",
}


def cell_rect(draw, x, y, color, size=CELL):
    draw.rectangle((x, y, x + size - 1, y + size - 1), fill=color)


def text_width(text, scale):
    return sum((6 if char != " " else 4) * scale for char in text) - scale


def pixel_text(draw, text, x, y, scale, color):
    for char in text:
        if char == " ":
            x += 4 * scale
            continue
        for row, line in enumerate(GLYPHS[char].split()):
            for column, bit in enumerate(line):
                if bit == "1":
                    shade = color(row) if callable(color) else color
                    draw.rectangle((x + column * scale, y + row * scale, x + (column + 1) * scale - 1, y + (row + 1) * scale - 1), fill=shade)
        x += 6 * scale


def sky(draw):
    bands = 14
    for band in range(bands):
        amount = band / (bands - 1)
        color = tuple(round(a + (b - a) * amount) for a, b in zip(SKY_TOP, SKY_BOTTOM))
        draw.rectangle((0, band * HEIGHT // bands, WIDTH, (band + 1) * HEIGHT // bands), fill=color)


def cloud(draw, x, y, cells, rng):
    for row, inset in enumerate((3, 0, 1)):
        for column in range(inset, cells - inset):
            color = CLOUD_SHADE if row == 2 else CLOUD
            cell_rect(draw, x + column * CELL * 2, y + row * CELL * 2, color, CELL * 2)


def ground(draw, rng):
    top = HEIGHT - 96
    for x in range(0, WIDTH, CELL):
        surface = top + (0 if math.sin(x / 170) > 0.25 else CELL * 2)
        for y in range(surface, HEIGHT, CELL):
            depth = y - surface
            noisy = rng.random() < 0.25
            if depth < CELL * 2:
                color = GRASS_DARK if noisy else GRASS
            elif depth == CELL * 2 and rng.random() < 0.4:
                color = GRASS_DARK
            elif depth < CELL * 8:
                color = DIRT_DARK if noisy else DIRT
            else:
                color = STONE_DARK if noisy else STONE
            cell_rect(draw, x, y, color)


def wheel(image, center, radius):
    """Uma roleta com o mesmo jeito das que aparecem no jogo, em blocos."""
    draw = ImageDraw.Draw(image)
    slices = len(WHEEL)
    cx, cy = center
    span = radius + 2 * CELL
    for gy in range(-span, span, CELL):
        for gx in range(-span, span, CELL):
            distance = math.hypot(gx + CELL / 2, gy + CELL / 2)
            if distance > radius + CELL:
                continue
            if distance > radius - CELL or distance < radius * 0.2:
                color = INK
            else:
                angle = (math.atan2(gx + CELL / 2, -(gy + CELL / 2)) + 2 * math.pi + 0.2) % (2 * math.pi)
                color = WHEEL[int(angle / (2 * math.pi) * slices) % slices]
            cell_rect(draw, cx + gx, cy + gy, color)
    for row in range(5):
        half = (4 - row) * CELL
        draw.rectangle((cx - half - CELL // 2, cy - radius - 5 * CELL + row * CELL, cx + half + CELL // 2 - 1, cy - radius - 4 * CELL + row * CELL - 1), fill=GOLD)


def bubble(image, x, y, word, font_file=None, tail=-1):
    """Um balão de fala com uma palavra. As palavras latinas usam a letra em blocos, porque as fontes do mod só têm os outros alfabetos."""
    draw = ImageDraw.Draw(image)
    padding = 20
    size = 44
    if font_file:
        font = ImageFont.truetype(str(FONTS / font_file), size)
        box = draw.textbbox((0, 0), word, font=font)
        inner_w, inner_h = box[2] - box[0], box[3] - box[1]
    else:
        scale = 6
        inner_w, inner_h = text_width(word, scale), 7 * scale
    w, h = inner_w + padding * 2, inner_h + padding * 2
    draw.rectangle((x + CELL, y + CELL, x + w + CELL, y + h + CELL), fill=(0, 0, 0))
    draw.rectangle((x, y, x + w, y + h), fill=INK)
    draw.rectangle((x + 4, y + 4, x + w - 4, y + h - 4), fill=PAPER)
    draw.rectangle((x + 4, y + h - 10, x + w - 4, y + h - 4), fill=PAPER_SHADE)
    tail_x = x + (w // 4 if tail < 0 else w * 3 // 4)
    for row in range(3):
        width = (3 - row) * 2 * 4
        draw.rectangle((tail_x - width // 2 - 4, y + h + row * 8, tail_x + width // 2 + 4, y + h + (row + 1) * 8), fill=INK)
        draw.rectangle((tail_x - width // 2, y + h - 4 + row * 8, tail_x + width // 2, y + h + (row + 1) * 8 - 4), fill=PAPER)
    if font_file:
        draw.text((x + padding - box[0], y + padding - box[1]), word, font=font, fill=INK)
    else:
        pixel_text(draw, word, x + padding, y + padding, scale, INK)


def title(draw, x, y):
    """Cada ponto da letra é um bloco: grama em cima, depois terra e pedra, com contorno escuro para destacar do céu."""
    scale = 22
    outline = 7
    text = "LANGCRAFT"
    points = []
    cursor = x
    for char in text:
        for row, line in enumerate(GLYPHS[char].split()):
            for column, bit in enumerate(line):
                if bit == "1":
                    points.append((cursor + column * scale, y + row * scale, row))
        cursor += 6 * scale
    for px, py, _ in points:
        draw.rectangle((px - outline + 8, py - outline + 8, px + scale + outline + 7, py + scale + outline + 7), fill=(14, 20, 44))
    for px, py, _ in points:
        draw.rectangle((px - outline, py - outline, px + scale + outline - 1, py + scale + outline - 1), fill=INK)
    for px, py, row in points:
        base, light, dark = (GRASS, (150, 205, 100), GRASS_DARK) if row < 2 else (DIRT, (150, 110, 78), DIRT_DARK) if row < 5 else (STONE, (150, 150, 155), STONE_DARK)
        draw.rectangle((px, py, px + scale - 1, py + scale - 1), fill=base)
        draw.rectangle((px, py, px + scale - 1, py + 3), fill=light)
        draw.rectangle((px, py + scale - 4, px + scale - 1, py + scale - 1), fill=dark)


def banner():
    rng = random.Random(1201)
    image = Image.new("RGB", (WIDTH, HEIGHT), SKY_BOTTOM)
    draw = ImageDraw.Draw(image)
    sky(draw)
    cloud(draw, 90, 36, 8, rng)
    cloud(draw, 640, 70, 6, rng)
    cloud(draw, 1120, 28, 7, rng)
    cloud(draw, 1680, 92, 6, rng)
    ground(draw, rng)

    title(draw, 80, 150)
    subtitle = "LEARN A LANGUAGE OR PAY FOR IT"
    pixel_text(draw, subtitle, 88, 356, 6, INK)
    pixel_text(draw, subtitle, 84, 352, 6, WHITE)

    wheel(image, (1600, 330), 176)

    bubble(image, 84, 440, "안녕", "hangul.ttf")
    bubble(image, 250, 452, "こんにちは", "japanese.ttf")
    bubble(image, 556, 440, "HELLO")
    bubble(image, 800, 452, "HALLO")
    bubble(image, 1044, 440, "HOLA")
    OUT.mkdir(parents=True, exist_ok=True)
    image.save(OUT / "banner.png", optimize=True)


def icon():
    generate_icon.render(ICON_SCALE).save(OUT / "icon.png", optimize=True)


if __name__ == "__main__":
    banner()
    icon()
