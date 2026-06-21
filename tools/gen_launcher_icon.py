"""Сгенерировать ресурсы лаунчер-иконки из готового арта icon.png.

Пользователь нарисовал финальную квадратную иконку «ночной город» (1254×1254,
со скруглением и тёмной рамкой по краям). minSdk=26 → на устройстве всегда
используется adaptive-icon, поэтому раскладываем арт по слоям:
  • foreground = арт «в край» (full-bleed) — систему сама обрежет под форму;
  • background = сплошной тёмный навигационный синий, взятый из угла арта
    (чтобы при круглой маске обрезанные углы сливались с краем арта без шва).
Плюс перегенерируем legacy ic_launcher.webp/ic_launcher_round.webp на всякий.
"""
import os
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "icon.png")
RES = os.path.join(ROOT, "android", "app", "src", "main", "res")

# Плотности: множитель к 1x.
DENS = {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}
ADAPTIVE_DP = 108   # размер слоя adaptive-icon в dp
LEGACY_DP = 48      # размер legacy-иконки в dp

art = Image.open(SRC).convert("RGBA")
W, H = art.size

# Цвет фона = средний по уголкам (тёмный навигационный синий).
corners = [art.getpixel(p) for p in ((4, 4), (W - 5, 4), (4, H - 5), (W - 5, H - 5))]
br = sum(c[0] for c in corners) // 4
bg_g = sum(c[1] for c in corners) // 4
bg_b = sum(c[2] for c in corners) // 4
BG_HEX = f"#FF{br:02X}{bg_g:02X}{bg_b:02X}"
print("corner colors:", corners)
print("background hex:", BG_HEX)

# Квадратная маска со скруглением для legacy round-иконки (полный круг).
def make_round(img):
    from PIL import ImageDraw
    size = img.size[0]
    mask = Image.new("L", (size, size), 0)
    d = ImageDraw.Draw(mask)
    d.ellipse((0, 0, size - 1, size - 1), fill=255)
    out = img.copy()
    out.putalpha(mask)
    return out

for name, mul in DENS.items():
    # --- adaptive foreground: арт в полный размер слоя 108dp ---
    fg_px = int(round(ADAPTIVE_DP * mul))
    fg = art.resize((fg_px, fg_px), Image.LANCZOS)
    fdir = os.path.join(RES, f"mipmap-{name}")
    os.makedirs(fdir, exist_ok=True)
    fg.save(os.path.join(fdir, "ic_launcher_art.png"))

    # --- legacy квадратная и круглая иконки 48dp ---
    lg_px = int(round(LEGACY_DP * mul))
    lg = art.resize((lg_px, lg_px), Image.LANCZOS)
    lg.convert("RGB").save(os.path.join(fdir, "ic_launcher.webp"), "WEBP", quality=92)
    make_round(lg).save(os.path.join(fdir, "ic_launcher_round.webp"), "WEBP", quality=92)
    print(f"{name}: fg {fg_px}px, legacy {lg_px}px")

# Иконка для Play/документации (512px) — пригодится, кладём в tools/output.
out = os.path.join(ROOT, "tools", "output")
os.makedirs(out, exist_ok=True)
art.resize((512, 512), Image.LANCZOS).convert("RGB").save(
    os.path.join(out, "ic_launcher_512.png"))
print("done; bg color =", BG_HEX)
