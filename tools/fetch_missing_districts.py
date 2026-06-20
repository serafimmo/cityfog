"""
Добрать 2 района Казани, которых нет в pbf-экстракте как admin_level=9
(Авиастроительный, Советский), напрямую из живого OSM через Nominatim
(он отдаёт готовый полигон границы в GeoJSON — не нужно собирать кольца из ways).

Дополняет tools/output/kazan_districts.geojson (id 1 и 7) на месте.
Запуск: python fetch_missing_districts.py  (нужен интернет; Nominatim, ~1 запрос/сек).
Потом перегенерировать маску: python build_mask.py
"""
import json
import os
import time
import urllib.parse
import urllib.request

import geopandas as gpd
from shapely.geometry import shape

OUT = os.path.join(os.path.dirname(__file__), "output")
DISTRICTS = os.path.join(OUT, "kazan_districts.geojson")

# id -> (поисковая строка, ключ-имя как в DISTRICT_NAMES)
MISSING = {
    1: ("Авиастроительный район, Казань", "авиастроительный"),
    7: ("Советский район, Казань", "советский"),
}
UA = "cityfog-dev/1.0 (personal fog-of-war app; contact: local)"


def fetch_polygon(query: str):
    params = urllib.parse.urlencode({
        "q": query,
        "format": "jsonv2",
        "polygon_geojson": "1",
        "limit": "10",
        "countrycodes": "ru",
        "accept-language": "ru",
    })
    url = f"https://nominatim.openstreetmap.org/search?{params}"
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    with urllib.request.urlopen(req, timeout=25) as r:
        data = json.loads(r.read().decode("utf-8"))
    # Берём первый результат-границу с полигональной геометрией внутри Казани.
    for item in data:
        if item.get("category") != "boundary":
            continue
        gj = item.get("geojson") or {}
        if gj.get("type") not in ("Polygon", "MultiPolygon"):
            continue
        if "Казань" not in item.get("display_name", "") and \
           "Казан" not in item.get("display_name", ""):
            continue
        return shape(gj), item.get("display_name", "")
    return None, None


def main():
    if not os.path.exists(DISTRICTS):
        raise SystemExit(f"Нет {DISTRICTS} — сначала запусти extract_polygons.py")
    gdf = gpd.read_file(DISTRICTS)
    have = set(int(x) for x in gdf["district_id"])
    print(f"Уже есть районы: {sorted(have)}")

    new_rows = []
    for i, (did, (query, key)) in enumerate(MISSING.items()):
        if did in have:
            print(f"  id={did} уже есть — пропуск")
            continue
        if i > 0:
            time.sleep(1.2)  # вежливо к Nominatim (лимит ~1 req/sec)
        geom, name = fetch_polygon(query)
        if geom is None:
            print(f"  ⚠ id={did} '{query}': граница не найдена")
            continue
        print(f"  ✓ id={did}: {name}  (площадь ~{geom.area:.4f}°²)")
        new_rows.append({"district_id": did, "name": key, "geometry": geom})

    if not new_rows:
        print("Нечего добавлять.")
        return

    add = gpd.GeoDataFrame(new_rows, crs="EPSG:4326")
    out = gpd.GeoDataFrame(
        __import__("pandas").concat([gdf, add], ignore_index=True),
        crs="EPSG:4326",
    )
    out.to_file(DISTRICTS, driver="GeoJSON")
    print(f"Записано {len(out)} районов -> {DISTRICTS}")


if __name__ == "__main__":
    main()
