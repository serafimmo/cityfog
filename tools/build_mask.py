"""
Шаг 3 пайплайна: растеризовать город/районы/исключения в маску доступности.

Вход (tools/output/, из extract_polygons.py):
    kazan_city.geojson, kazan_districts.geojson, kazan_excluded.geojson

Выход (android/app/src/main/assets/):
    kazan_mask.bin.gz   — uint8 row-major (HEIGHT x WIDTH), gzip. Значения пикселя:
                            0      = недоступно (вне города ИЛИ исключено: вода/завод/...)
                            1..7   = доступно, id известного района (см. DISTRICT_NAMES)
                            8      = доступно, город без района (Авиастроительный+Советский+мелочь)
    grid_manifest.json  — параметры сетки (origin/cellSize/width/height) = grid_config.
    district_totals.json— totalAvailable + число доступных клеток на район.

Плюс tools/output/kazan_mask_preview.png — цветной превью для глаза (в assets НЕ идёт).

КРИТИЧНО: пиксель маски (row,col) ⇔ клетка (cellX,cellY) считается ровно по grid_config —
теми же origin/cellSize, что и Kotlin GridMapper. Иначе доступность/районы поедут.
"""
import json
import os

import cv2
import geopandas as gpd
import numpy as np
from shapely.geometry import MultiPolygon, Polygon

import grid_config as gc

HERE = os.path.dirname(__file__)
OUT = os.path.join(HERE, "output")
ASSETS = os.path.abspath(os.path.join(HERE, "..", "android", "app", "src", "main", "assets"))

AVAIL_CITY = 8  # доступно, но район неизвестен

DISTRICT_NAMES = {
    1: "Авиастроительный",
    2: "Вахитовский",
    3: "Кировский",
    4: "Московский",
    5: "Ново-Савиновский",
    6: "Приволжский",
    7: "Советский",
    AVAIL_CITY: "Остальной город",
}

# Цвета превью (BGR) по значению пикселя.
PREVIEW_COLORS = {
    0: (20, 16, 10),     # недоступно — почти чёрный
    1: (180, 120, 60), 2: (60, 160, 220), 3: (80, 200, 120),
    4: (200, 120, 200), 5: (90, 210, 230), 6: (210, 160, 70),
    7: (120, 120, 240), 8: (90, 90, 90),  # город без района — серый
}


def polys(geom):
    """Итерировать Polygon'ы из Polygon/MultiPolygon, пропуская пустые."""
    if geom is None or geom.is_empty:
        return
    if isinstance(geom, Polygon):
        yield geom
    elif isinstance(geom, MultiPolygon):
        for g in geom.geoms:
            if not g.is_empty:
                yield g


def to_px(coords) -> np.ndarray:
    """Список (lon,lat) -> массив пикселей (N,1,2) int32 в виде (col,row)."""
    pts = []
    inv_lon = gc.M_PER_DEG_LON / gc.CELL_SIZE_M
    inv_lat = gc.M_PER_DEG_LAT / gc.CELL_SIZE_M
    for lon, lat in coords:
        col = (lon - gc.LON_ORIGIN) * inv_lon
        row = gc.HEIGHT - (lat - gc.LAT_ORIGIN) * inv_lat
        pts.append([col, row])
    return np.round(np.asarray(pts, dtype=np.float64)).astype(np.int32).reshape(-1, 1, 2)


def fill(mask, geom, value, hole_value=None):
    """Залить полигоны geom значением value; их дыры — hole_value (если задан)."""
    for poly in polys(geom):
        cv2.fillPoly(mask, [to_px(poly.exterior.coords)], int(value))
        if hole_value is not None:
            for ring in poly.interiors:
                cv2.fillPoly(mask, [to_px(ring.coords)], int(hole_value))


def main():
    city = gpd.read_file(os.path.join(OUT, "kazan_city.geojson"))
    districts = gpd.read_file(os.path.join(OUT, "kazan_districts.geojson"))
    excluded = gpd.read_file(os.path.join(OUT, "kazan_excluded.geojson"))

    mask = np.zeros((gc.HEIGHT, gc.WIDTH), dtype=np.uint8)

    # 1) База: весь город = доступно (8), внутренние дыры города -> 0.
    for g in city.geometry:
        fill(mask, g, AVAIL_CITY, hole_value=0)

    # 2) Поверх — известные районы (их 5 из 7); дыры района -> остаёмся городом (8).
    for _, r in districts.iterrows():
        fill(mask, r.geometry, int(r["district_id"]), hole_value=AVAIL_CITY)

    # 3) Поверх — исключения (вода/заводы/...) = недоступно (0), сплошняком.
    for g in excluded.geometry:
        fill(mask, g, 0)

    # --- Статистика ---
    total = int((mask != 0).sum())
    per = {v: int((mask == v).sum()) for v in range(1, 9)}
    print(f"Сетка {gc.WIDTH}x{gc.HEIGHT} = {gc.WIDTH*gc.HEIGHT:,} клеток")
    print(f"Доступно (знаменатель %): {total:,} клеток "
          f"= {total*gc.CELL_SIZE_M**2/1e6:.1f} км²")
    for v in range(1, 9):
        if per[v]:
            print(f"  [{v}] {DISTRICT_NAMES[v]:<18} {per[v]:>9,} клеток "
                  f"({per[v]*gc.CELL_SIZE_M**2/1e6:5.1f} км²)")

    # --- Запись в assets ---
    os.makedirs(ASSETS, exist_ok=True)
    # Пишем СЫРОЙ bin (без .gz!): Android Gradle сам жмёт ассеты deflate'ом в APK, а файл
    # с расширением .gz он бы РАСПАКОВАЛ и переименовал (kazan_mask.bin.gz -> kazan_mask.bin),
    # и поиск по старому имени падал бы. AssetManager.open() отдаёт уже распакованный поток.
    mask_path = os.path.join(ASSETS, "kazan_mask.bin")
    with open(mask_path, "wb") as f:
        f.write(mask.tobytes(order="C"))
    old_gz = os.path.join(ASSETS, "kazan_mask.bin.gz")
    if os.path.exists(old_gz):
        os.remove(old_gz)

    manifest = gc.manifest()
    with open(os.path.join(ASSETS, "grid_manifest.json"), "w", encoding="utf-8") as f:
        json.dump(manifest, f, ensure_ascii=False, indent=2)

    totals = {
        "totalAvailable": total,
        "cellAreaM2": gc.CELL_SIZE_M ** 2,
        "perDistrict": {str(v): per[v] for v in range(1, 9)},
        "names": {str(v): DISTRICT_NAMES[v] for v in range(1, 9)},
    }
    with open(os.path.join(ASSETS, "district_totals.json"), "w", encoding="utf-8") as f:
        json.dump(totals, f, ensure_ascii=False, indent=2)

    # --- Цветной превью (для глаза, не в assets) ---
    preview = np.zeros((gc.HEIGHT, gc.WIDTH, 3), dtype=np.uint8)
    for v, color in PREVIEW_COLORS.items():
        preview[mask == v] = color
    scale = 1000.0 / max(gc.WIDTH, gc.HEIGHT)
    small = cv2.resize(preview, None, fx=scale, fy=scale, interpolation=cv2.INTER_NEAREST)
    cv2.imwrite(os.path.join(OUT, "kazan_mask_preview.png"), small)

    sz = os.path.getsize(mask_path)
    print(f"\nЗаписано в assets: kazan_mask.bin ({sz/1024/1024:.1f} МБ сырой, "
          f"в APK ужмётся deflate'ом до ~0.2 МБ), grid_manifest.json, district_totals.json")
    print(f"Превью: {os.path.join(OUT, 'kazan_mask_preview.png')}")


if __name__ == "__main__":
    main()
