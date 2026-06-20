"""
Единый источник правды для координатной сетки.

КРИТИЧНО: эти же константы и формулы ОБЯЗАНЫ один-в-один повторяться в Kotlin
(app/.../grid/GridMapper.kt). Если Python и Android разойдутся в origin/cellSize/формуле —
пиксели маски не совпадут с клетками на телефоне, и доступность/районы поедут.

Проекция — локальная equirectangular ("плоская земля") вокруг Казани.
На масштабе одного города искажение пренебрежимо.
"""
from math import cos, radians, floor

# --- Параметры сетки ---------------------------------------------------------
CELL_SIZE_M = 12.0          # размер клетки, метры

M_PER_DEG_LAT = 111_320.0   # метров на 1° широты (≈ const)
LAT0 = 55.79                # опорная широта Казани для масштаба долготы (cos)
M_PER_DEG_LON = M_PER_DEG_LAT * cos(radians(LAT0))   # ≈ 62571 м/°

# Юго-западный угол охватывающего прямоугольника Казани (origin сетки).
# Подобран с запасом, чтобы покрыть весь город. cellX/cellY от него >= 0.
# Границы выставлены по реальному охвату границы города (lvl6 'городской округ Казань':
# lon 48.821..49.381, lat 55.603..55.938) с небольшим запасом.
LAT_ORIGIN = 55.58          # юг  (город уходит до 55.603 — Приволжский)
LON_ORIGIN = 48.78          # запад (город от 48.821)
LAT_MAX = 55.96             # север (город до 55.938 — Авиастроительный)
LON_MAX = 49.40             # восток (город до 49.381 — Советский)

# Размер растровой маски в клетках (пикселях).
# Финальные значения должны совпадать с GridMapper.kt и уже сгенерированной маской.
WIDTH = floor((LON_MAX - LON_ORIGIN) * M_PER_DEG_LON / CELL_SIZE_M)   # 3233
HEIGHT = floor((LAT_MAX - LAT_ORIGIN) * M_PER_DEG_LAT / CELL_SIZE_M)  # 3525


# --- Преобразования ----------------------------------------------------------
def latlon_to_cell(lat: float, lon: float) -> tuple[int, int]:
    """Координаты -> индекс клетки (cellX вправо/восток, cellY вверх/север)."""
    cell_x = floor((lon - LON_ORIGIN) * M_PER_DEG_LON / CELL_SIZE_M)
    cell_y = floor((lat - LAT_ORIGIN) * M_PER_DEG_LAT / CELL_SIZE_M)
    return cell_x, cell_y


def cell_center_latlon(cell_x: int, cell_y: int) -> tuple[float, float]:
    """Индекс клетки -> координаты центра клетки."""
    lon = LON_ORIGIN + (cell_x + 0.5) * CELL_SIZE_M / M_PER_DEG_LON
    lat = LAT_ORIGIN + (cell_y + 0.5) * CELL_SIZE_M / M_PER_DEG_LAT
    return lat, lon


# В PNG-маске пиксель (row, col): row растёт ВНИЗ, поэтому cellY переворачиваем.
def cell_to_pixel(cell_x: int, cell_y: int) -> tuple[int, int]:
    """Индекс клетки -> (row, col) в массиве маски (numpy/cv2)."""
    col = cell_x
    row = HEIGHT - 1 - cell_y
    return row, col


def manifest() -> dict:
    """Параметры сетки для grid_manifest.json (читает Android при загрузке маски)."""
    return {
        "cellSizeM": CELL_SIZE_M,
        "mPerDegLat": M_PER_DEG_LAT,
        "lat0": LAT0,
        "mPerDegLon": M_PER_DEG_LON,
        "latOrigin": LAT_ORIGIN,
        "lonOrigin": LON_ORIGIN,
        "latMax": LAT_MAX,
        "lonMax": LON_MAX,
        "width": WIDTH,
        "height": HEIGHT,
    }


if __name__ == "__main__":
    import json
    print(f"Сетка Казани: {WIDTH} x {HEIGHT} клеток = {WIDTH * HEIGHT:,} клеток "
          f"(uint8-маска ≈ {WIDTH * HEIGHT / 1e6:.1f} МБ в RAM)")
    print(json.dumps(manifest(), ensure_ascii=False, indent=2))
