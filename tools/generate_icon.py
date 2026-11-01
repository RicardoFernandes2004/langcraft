"""Gera o ícone do mod em assets/langcraft/icon.png: uma roleta com 가 no centro.

Rode com: python tools/generate_icon.py
"""
import math
import random
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ASSETS = Path(__file__).resolve().parent.parent / "common/src/main/resources/assets/langcraft"
GRID = 32
MOD_ICON_SCALE = 8

BACKGROUND = (32, 40, 74)
BACKGROUND_DARK = (26, 32, 62)
HUB = (250, 244, 225)
INK = (28, 24, 22)
GOLD = (244, 197, 66)
SLICES = [(200, 50, 31), (244, 197, 66), (47, 111, 179), (93, 145, 62), (120, 60, 150), (230, 130, 40), (40, 170, 170), (240, 240, 235)]
WHEEL_RADIUS = 13
WHEEL_CENTER = (16, 17.5)
HUB_RATIO = 0.42
TEXT_SIZE = 7.5


def render(scale):
    """Desenha o ícone em GRID * scale pixels. A letra é traçada de novo, então fica nítida em qualquer tamanho."""
    size = GRID * scale
    image = Image.new("RGB", (size, size))
    draw = ImageDraw.Draw(image)

    def cell(x, y, color):
        draw.rectangle((x, y, x + scale - 1, y + scale - 1), fill=color)

    rng = random.Random(11)
    for y in range(0, size, scale):
        for x in range(0, size, scale):
            cell(x, y, BACKGROUND_DARK if rng.random() < 0.22 else BACKGROUND)

    cx, cy = WHEEL_CENTER[0] * scale, int(WHEEL_CENTER[1] * scale)
    radius = WHEEL_RADIUS * scale
    for gy in range(-radius - scale, radius + scale, scale):
        for gx in range(-radius - scale, radius + scale, scale):
            distance = math.hypot(gx + scale / 2, gy + scale / 2)
            if distance > radius + scale / 2:
                continue
            if distance < radius * HUB_RATIO:
                color = HUB
            elif distance > radius - scale / 2:
                color = INK
            else:
                angle = (math.atan2(gx + scale / 2, -(gy + scale / 2)) + 2 * math.pi + 0.2) % (2 * math.pi)
                color = SLICES[int(angle / (2 * math.pi) * len(SLICES)) % len(SLICES)]
            cell(cx + gx, cy + gy, color)

    tip_y = cy - radius
    for row in range(4):
        half = (3 - row) * scale
        draw.rectangle((cx - half - scale // 2, tip_y - (4 - row) * scale, cx + half + scale // 2 - 1, tip_y - (3 - row) * scale - 1), fill=GOLD)

    font = ImageFont.truetype(str(ASSETS / "font/hangul.ttf"), int(TEXT_SIZE * scale))
    box = draw.textbbox((0, 0), "가", font=font)
    draw.text((cx - (box[0] + box[2]) / 2, cy - (box[1] + box[3]) / 2), "가", font=font, fill=INK)
    return image


def main():
    render(MOD_ICON_SCALE).save(ASSETS / "icon.png", optimize=True)


if __name__ == "__main__":
    main()
