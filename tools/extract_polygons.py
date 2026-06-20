"""
Шаг 2 пайплайна: извлечь из .osm.pbf полигоны районов Казани и зоны-исключения.

Запуск:
    python extract_polygons.py            # разведка + извлечение
    python extract_polygons.py --inspect  # только разведка (что за границы в данных)

На выходе (в tools/output/):
    kazan_districts.geojson   — 7 районов, поле district_id = 1..7
    kazan_excluded.geojson    — непроходимое (вода/заводы/военное/ж-д/карьеры)
    kazan_city.geojson        — граница города (объединение районов)

ВАЖНО: OSM-тегирование РФ-городов неоднородно. Сначала смотрим, что реально есть
(--inspect), и при необходимости правим DISTRICT_NAMES / admin_level ниже.
"""
import sys
import os
from pyrosm import OSM
from shapely.ops import unary_union
import geopandas as gpd
import pandas as pd

import grid_config as gc

# Щедрый bbox для ПОИСКА в OSM — намеренно шире финальной сетки, чтобы не отрезать
# крайние районы (Советский на востоке, Приволжский на юге, Авиастроительный на севере).
# Реальные границы сетки в grid_config выставим по факту (см. вывод --inspect).
SEARCH_BBOX = [48.70, 55.55, 49.50, 56.00]   # [minx, miny, maxx, maxy]

PBF = os.path.join(os.path.dirname(__file__), "data", "volga-fed-district-latest.osm.pbf")
OUT = os.path.join(os.path.dirname(__file__), "output")

# 7 районов Казани в порядке id=1..7 (ключ — по чему матчим имя из OSM, в нижнем регистре)
DISTRICT_NAMES = {
    1: "авиастроительный",
    2: "вахитовский",
    3: "кировский",
    4: "московский",
    5: "ново-савиновский",
    6: "приволжский",
    7: "советский",
}

# landuse-теги, которые считаем непроходимыми (парки/лес НЕ сюда!)
EXCLUDE_LANDUSE = {"industrial", "military", "railway", "quarry", "landfill"}
# natural-теги для исключения (вода). Лес (wood) НЕ исключаем — часто проходим.
EXCLUDE_NATURAL = {"water"}


def load_osm() -> OSM:
    if not os.path.exists(PBF):
        sys.exit(f"Нет файла OSM: {PBF}\nСкачай volga-fed-district-latest.osm.pbf в tools/data/")
    # complete_relations=True — иначе крупные районы (Советский, Авиастроительный),
    # чьи части выходят за bbox, возвращаются с битой геометрией и отсекаются.
    return OSM(PBF, bounding_box=SEARCH_BBOX, complete_relations=True)


def load_osm_tight() -> OSM:
    """Загрузчик для ИСКЛЮЧЕНИЙ: узкий bbox по границам сетки + complete_relations=False.

    Иначе get_natural() тянет гигантские водные отношения (Волга/Куйбышевское вдхр),
    выходящие далеко за город, и падает по памяти (GEOS bad allocation). Воду/landuse
    нам всё равно достаточно в пределах города — потом обрезаем по его границе.
    """
    bbox = [gc.LON_ORIGIN, gc.LAT_ORIGIN, gc.LON_MAX, gc.LAT_MAX]
    return OSM(PBF, bounding_box=bbox, complete_relations=False)


def inspect(osm: OSM):
    """Разведка: какие административные границы есть в районе Казани."""
    b = osm.get_boundaries(boundary_type="administrative")
    if b is None or b.empty:
        print("Границы не найдены в bbox — проверь BBOX/данные.")
        return
    cols = [c for c in ("name", "admin_level") if c in b.columns]
    print(f"Найдено границ: {len(b)}")
    view = b[cols].copy()
    if "admin_level" in view.columns:
        view = view.sort_values("admin_level")
    for _, row in view.iterrows():
        print(f"  admin_level={row.get('admin_level','?'):>3}  {row.get('name','<no name>')}")
    d9 = b[b["admin_level"].astype(str) == "9"]
    if not d9.empty:
        minx, miny, maxx, maxy = d9.total_bounds
        print(f"\nРайонов lvl9: {len(d9)}.  Реальный bbox районов: "
              f"lon {minx:.4f}..{maxx:.4f}, lat {miny:.4f}..{maxy:.4f}")
    city6 = b[(b["admin_level"].astype(str) == "6") &
              (b["name"].astype(str).str.contains("Казань", na=False))]
    if not city6.empty:
        minx, miny, maxx, maxy = city6.total_bounds
        print(f"Город (lvl6 'городской округ Казань'): "
              f"lon {minx:.4f}..{maxx:.4f}, lat {miny:.4f}..{maxy:.4f}")


def extract_districts(osm: OSM) -> gpd.GeoDataFrame:
    b = osm.get_boundaries(boundary_type="administrative")
    b = b[b["name"].notna()].copy()
    b = b[b["admin_level"].astype(str) == "9"]   # только внутригородские районы
    b["lname"] = b["name"].str.lower()
    rows = []
    for did, key in DISTRICT_NAMES.items():
        hit = b[b["lname"].str.contains(key, na=False)]
        if hit.empty:
            print(f"  ⚠ район id={did} ('{key}') не найден — поправь DISTRICT_NAMES после --inspect")
            continue
        # берём самую крупную геометрию с таким именем (на случай дублей)
        geom = unary_union(hit.geometry.values)
        rows.append({"district_id": did, "name": key, "geometry": geom})
        print(f"  ✓ район id={did}: {key}")
    return gpd.GeoDataFrame(rows, crs="EPSG:4326")


def extract_city(osm: OSM):
    """Полная граница города из lvl6 'городской округ Казань' (целая, все 7 районов).

    ВАЖНО: НЕ объединение районов — их в данных только 5 из 7. Объединение дало бы
    дырявый город (без Авиастроительного и Советского) и кривой глобальный %.
    """
    b = osm.get_boundaries(boundary_type="administrative")
    b = b[b["name"].notna()]
    city = b[(b["admin_level"].astype(str) == "6") &
             (b["name"].astype(str).str.contains("Казань", na=False))]
    if city.empty:
        return None
    return unary_union(city.geometry.values)


def _safe(label, fn):
    """Вызвать fn() терпимо к ошибкам (битая геометрия/память) — вернуть None при сбое."""
    try:
        return fn()
    except Exception as e:
        print(f"  ! слой '{label}' пропущен: {e}")
        return None


def extract_exclusions(osm: OSM, city_geom) -> gpd.GeoDataFrame:
    parts = []
    lu = _safe("landuse", osm.get_landuse)
    if lu is not None and not lu.empty and "landuse" in lu.columns:
        parts.append(lu[lu["landuse"].isin(EXCLUDE_LANDUSE)])
    nat = _safe("natural", osm.get_natural)
    if nat is not None and not nat.empty and "natural" in nat.columns:
        parts.append(nat[nat["natural"].isin(EXCLUDE_NATURAL)])
    if not parts:
        return gpd.GeoDataFrame(columns=["geometry"], crs="EPSG:4326")
    exc = gpd.GeoDataFrame(pd.concat(parts, ignore_index=True), geometry="geometry", crs="EPSG:4326")
    # обрезаем по границе города и оставляем только полигоны
    exc = exc[exc.geometry.notna()]
    exc = gpd.clip(exc, city_geom)
    exc = exc[exc.geometry.geom_type.isin(["Polygon", "MultiPolygon"])]
    return exc


def main():
    os.makedirs(OUT, exist_ok=True)
    osm = load_osm()

    if "--inspect" in sys.argv:
        inspect(osm)
        return

    print("Извлекаю границу города (lvl6)...")
    city_geom = extract_city(osm)
    if city_geom is None:
        sys.exit("Граница города lvl6 'Казань' не найдена — проверь данные/bbox.")
    city = gpd.GeoDataFrame({"name": ["Казань"], "geometry": [city_geom]}, crs="EPSG:4326")

    print("Извлекаю районы (для разметки, в данных их 5 из 7)...")
    districts = extract_districts(osm)
    if districts.empty:
        sys.exit("Ни один район не найден — запусти с --inspect и поправь DISTRICT_NAMES.")

    print("Извлекаю зоны-исключения (узкий bbox, без complete_relations)...")
    osm_tight = load_osm_tight()
    excluded = extract_exclusions(osm_tight, city_geom)
    print(f"  исключений: {len(excluded)}")

    districts.to_file(os.path.join(OUT, "kazan_districts.geojson"), driver="GeoJSON")
    city.to_file(os.path.join(OUT, "kazan_city.geojson"), driver="GeoJSON")
    excluded.to_file(os.path.join(OUT, "kazan_excluded.geojson"), driver="GeoJSON")
    print(f"Готово -> {OUT}")


if __name__ == "__main__":
    main()
