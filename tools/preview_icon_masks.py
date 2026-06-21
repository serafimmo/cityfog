"""Превью: как adaptive-иконка будет выглядеть после системной маски
(круг / squircle Pixel / скруглённый квадрат). Проверяем, что арт «в край»
смотрится хорошо и герой (пин+крест) не обрезается."""
from PIL import Image, ImageDraw
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
art = Image.open(os.path.join(ROOT, "icon.png")).convert("RGBA")
S = 300
art = art.resize((S, S), Image.LANCZOS)
bg = (0, 2, 23, 255)


def masked(shape):
    base = Image.new("RGBA", (S, S), bg)
    base.alpha_composite(art)
    mask = Image.new("L", (S, S), 0)
    d = ImageDraw.Draw(mask)
    if shape == "circle":
        d.ellipse((0, 0, S - 1, S - 1), fill=255)
    elif shape == "squircle":
        d.rounded_rectangle((0, 0, S - 1, S - 1), radius=int(S * 0.42), fill=255)
    else:  # rounded
        d.rounded_rectangle((0, 0, S - 1, S - 1), radius=int(S * 0.22), fill=255)
    out = Image.new("RGBA", (S, S), (20, 24, 40, 255))
    out.paste(base, (0, 0), mask)
    return out


shapes = ["circle", "squircle", "rounded"]
pad = 24
montage = Image.new("RGBA", (S * 3 + pad * 4, S + pad * 2), (15, 18, 30, 255))
for i, sh in enumerate(shapes):
    montage.alpha_composite(masked(sh), (pad + (S + pad) * i, pad))
out = os.path.join(ROOT, "tools", "output", "icon_preview_masks.png")
montage.convert("RGB").save(out)
print("saved", out)
