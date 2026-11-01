"""Gera as texturas dos temas em assets/langcraft/textures/gui/sprites/theme.

Rode com: python tools/generate_theme_sprites.py
O sorteio usa sementes fixas, então o resultado é sempre igual.
"""
import json
import math
import random
from pathlib import Path

from PIL import Image

OUT = Path(__file__).resolve().parent.parent / "common/src/main/resources/assets/langcraft/textures/gui/sprites/theme"


def rgb(hex_color, alpha=255):
    value = hex_color.lstrip("#")
    return (int(value[:2], 16), int(value[2:4], 16), int(value[4:6], 16), alpha)


def mix(color, other, amount):
    return tuple(round(a + (b - a) * amount) for a, b in zip(color, other))


def tile(width, height, pick):
    image = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    for y in range(height):
        for x in range(width):
            color = pick(x, y)
            if color is not None:
                image.putpixel((x, y), color)
    return image


def mask(rows, palette):
    return tile(len(rows[0]), len(rows), lambda x, y: None if rows[y][x] == "." else palette[rows[y][x]])


def speckle(seed, base, rest):
    rng = random.Random(seed)

    def pick(x, y):
        value = rng.random()
        for limit, color in rest:
            if value < limit:
                return rgb(color)
        return rgb(base)

    return pick


def planks(seed, high, middle, low):
    rng = random.Random(seed)

    def pick(x, y):
        row = y >> 2
        if y % 4 == 3 or x == (row * 5 + 3) % 16:
            return rgb(low)
        return rgb(high if rng.random() < 0.25 else middle)

    return pick


def seigaiha(background, line):
    """Ondas japonesas. O desenho se repete a cada 16 por 8."""

    def pick(x, y):
        x %= 16
        y %= 8
        for row in range(8, -3, -1):
            center_y = 4 * row
            offset = 8 if row & 1 else 0
            for column in range(-1, 3):
                center_x = offset + 16 * column
                distance = math.hypot(x + 0.5 - center_x, y + 0.5 - center_y)
                if distance < 8 and y + 0.5 <= center_y:
                    return rgb(line if distance > 6.6 or 3.4 < distance < 4.5 else background)
        return rgb(background)

    return pick


def rings(image, colors):
    """Pinta molduras de 1 pixel, de fora para dentro."""
    width, height = image.size
    for depth, color in enumerate(colors):
        value = rgb(color)
        for x in range(depth, width - depth):
            image.putpixel((x, depth), value)
            image.putpixel((x, height - 1 - depth), value)
        for y in range(depth, height - depth):
            image.putpixel((depth, y), value)
            image.putpixel((width - 1 - depth, y), value)
    return image


def save(theme, name, image, scaling=None):
    folder = OUT / theme
    folder.mkdir(parents=True, exist_ok=True)
    image.save(folder / f"{name}.png")
    if scaling:
        meta = {"gui": {"scaling": {**scaling, "width": image.width, "height": image.height}}}
        (folder / f"{name}.png.mcmeta").write_text(json.dumps(meta, indent="\t") + "\n", encoding="utf-8", newline="\n")


def panel(theme, image, border):
    save(theme, "panel", image, {"type": "nine_slice", "border": border})


def button(theme, base, outline, high, low, off, off_outline):
    def paint(fill, top, bottom, edge):
        image = fill.copy()
        for x in range(1, 31):
            image.putpixel((x, 1), rgb(top))
            image.putpixel((x, 14), rgb(bottom))
        for y in range(1, 15):
            image.putpixel((1, y), rgb(top))
            image.putpixel((30, y), rgb(bottom))
        return rings(image, [edge])

    fill = base if isinstance(base, Image.Image) else Image.new("RGBA", (32, 16), rgb(base))
    hover = tile(32, 16, lambda x, y: mix(fill.getpixel((x, y)), (255, 255, 255, 255), 0.16))
    flat = Image.new("RGBA", (32, 16), rgb(off))
    scaling = {"type": "nine_slice", "border": 2}
    save(theme, "button", paint(fill, high, low, outline), scaling)
    save(theme, "button_hover", paint(hover, "#ffffff", low, outline), scaling)
    save(theme, "button_off", paint(flat, off, off, off_outline), scaling)


def pips(theme, rows, palette):
    save(theme, "pip_on", mask(rows, palette))
    dull = (128, 128, 128, 110)
    save(theme, "pip_off", mask(rows, {key: dull for key in palette}))


def inventario():
    image = Image.new("RGBA", (16, 16), rgb("#c6c6c6"))
    for depth in (1, 2):
        for i in range(depth, 16 - depth):
            image.putpixel((i, depth), rgb("#ffffff"))
            image.putpixel((depth, i), rgb("#ffffff"))
            image.putpixel((i, 15 - depth), rgb("#555555"))
            image.putpixel((15 - depth, i), rgb("#555555"))
    panel("inventario", rings(image, ["#000000"]), 3)


def cerejeira():
    wood = tile(32, 32, planks(31, "#efbccc", "#e6a9bd", "#c07f98"))
    for y in range(3, 29):
        for x in range(3, 29):
            wood.putpixel((x, y), rgb("#2c1522"))
    panel("cerejeira", rings(wood, ["#6a3350"]), 3)
    button("cerejeira", tile(32, 16, planks(47, "#f3cbd8", "#f0c4d2", "#e8b8c8")), "#6a3350", "#fbe3ea", "#b7728c", "#55344a", "#2c1522")
    save("cerejeira", "flower", mask(
        ["..a.a..", ".aabaa.", "aabcbaa", ".bcccb.", "aabcbaa", ".aabaa.", "..a.a.."],
        {"a": rgb("#f7c6d6"), "b": rgb("#ef9ab5"), "c": rgb("#ffe08a")}))
    pips("cerejeira", [".a.a.", "aaaaa", "aabaa", ".aaa.", "..a.."], {"a": rgb("#ffb3cd"), "b": rgb("#ffe08a")})


def cidade():
    # Discreto, porque o texto fica por cima. O brilho forte fica só na moldura e nos veios.
    sculk = [(0.006, "#1d6f7a"), (0.05, "#0f323b"), (0.14, "#0a232b")]
    panel("cidade", rings(tile(32, 32, speckle(9, "#06161b", sculk)), ["#02080a", "#1d6f7a"]), 2)
    button("cidade", "#0f2a31", "#02080a", "#1f5f6b", "#051014", "#0a1a1f", "#02080a")
    rng = random.Random(5)

    def veins(x, y):
        value = rng.random()
        if y == 2:
            return rgb("#29dfeb" if value < 0.5 else "#0f5a66")
        if y == 1:
            return rgb("#1aa3b0") if value < 0.3 else None
        return rgb("#7df0f7") if value < 0.08 else None

    save("cidade", "veins", tile(32, 3, veins), {"type": "tile"})
    pips("cidade", ["..a..", ".aba.", "ababa", ".aba.", "..a.."], {"a": rgb("#39e6f2"), "b": rgb("#e4ffff")})


def hanji():
    paper = [(0.05, "#d8c9a6"), (0.12, "#f7efdc"), (0.2, "#e6d9ba")]
    panel("hanji", rings(tile(32, 32, speckle(41, "#eee3c9", paper)), ["#2a2219", "#eee3c9", "#b9a986"]), 3)
    button("hanji", "#f7f0dd", "#2a2219", "#fffaf0", "#d3c5a3", "#d9cdb1", "#7d715d")
    save("hanji", "seal", mask([
        "aaaaaaaaaaaaaa",
        "a............a",
        "a.aaaa..aaaa.a",
        "a.a..a..a..a.a",
        "a.aaaa..aaaa.a",
        "a............a",
        "a..aaaaaaaa..a",
        "a..a......a..a",
        "a..aaaaaaaa..a",
        "a............a",
        "a.aaaaaaaaaa.a",
        "a.a........a.a",
        "a............a",
        "aaaaaaaaaaaaaa"], {"a": rgb("#a8261c")}))
    pips("hanji", ["aaaaa", "a.a.a", "aaaaa", "a.a.a", "aaaaa"], {"a": rgb("#a8261c")})


def dancheong():
    panel("dancheong", rings(Image.new("RGBA", (16, 16), rgb("#0e3f3a")), ["#f4c542", "#0a2825"]), 2)
    button("dancheong", "#b5321f", "#2a0d07", "#e2654a", "#6e1a0f", "#3f2a25", "#1c110e")
    colors = ["#2f6fb3", "#c8321f", "#f4c542", "#e6e1d3", "#141414"]
    save("dancheong", "band", tile(20, 5, lambda x, y: rgb("#f4c542" if y in (0, 4) else colors[x // 4])), {"type": "tile"})
    pips("dancheong", ["..a..", "a.a.a", "aaaaa", ".aaa.", "bbbbb"], {"a": rgb("#f4c542"), "b": rgb("#e2654a")})


def festival():
    waves = seigaiha("#141b3f", "#232d62")
    panel("festival", rings(tile(34, 34, lambda x, y: waves(x - 1, y - 1)), ["#0a0e26"]), 1)
    button("festival", "#f0a93a", "#3a1a05", "#ffd887", "#a8641a", "#2a2f55", "#0a0e26")
    save("festival", "lintel", tile(8, 6, lambda x, y: rgb("#1a1010" if y < 2 else "#d8402f" if y < 5 else "#8f2318")), {"type": "tile"})
    pips("festival", [".bbb.", "aaaaa", "aaaaa", "aaaaa", ".bbb."], {"a": rgb("#ffb84d"), "b": rgb("#d8402f")})


def caderno():
    """Folha pautada com espiral e margem vermelha, para as telas de estudo. As linhas se repetem a cada 12."""
    left, top, right, bottom = 22, 6, 3, 3
    width, height = left + 24 + right, top + 24 + bottom
    rng = random.Random(77)

    def pick(x, y):
        inner = (y - top) % 24
        ruled = top <= y < height - bottom
        if ruled and x < 8 and inner in (5, 6):
            return rgb("#a4a9b1" if inner == 5 else "#6c7178")
        if ruled and 6 <= x <= 8 and inner in (4, 7):
            return rgb("#5b5647")
        if x == 20:
            return rgb("#e6a6a6")
        if ruled and x > 20 and (y - top) % 12 == 11:
            return rgb("#cdd9ea")
        return rgb("#f4eedb" if rng.random() < 0.05 else "#fbf6e6")

    image = tile(width, height, pick)
    for x in range(width):
        image.putpixel((x, 0), rgb("#b9ad8e"))
        image.putpixel((x, height - 1), rgb("#b9ad8e"))
    for y in range(height):
        image.putpixel((width - 1, y), rgb("#b9ad8e"))
    save("caderno", "panel", image, {"type": "nine_slice", "border": {"left": left, "top": top, "right": right, "bottom": bottom}})
    button("caderno", "#f7e58a", "#b79f3e", "#fff3b0", "#d9bf54", "#efe8cf", "#c9c0a4")


if __name__ == "__main__":
    caderno()
    inventario()
    cerejeira()
    cidade()
    hanji()
    dancheong()
    festival()
