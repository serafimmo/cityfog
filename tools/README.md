# tools/ — OSM-препроцессинг (Python, на PC)

Эти скрипты запускаются **один раз на твоём компьютере** и готовят данные, которые
Android-приложение просто загружает из `assets/`. На телефоне OSM НЕ парсится.

## Что на выходе (попадёт в `app/.../assets/`)
- `kazan_mask.png` — индексированная маска: 1 пиксель = 1 клетка 12 м.
  Значение `0` = вне города / непроходимо (вода, завод, военное, частное),
  `1..7` = id административного района. (Это как маска в OpenCV.)
- `grid_manifest.json` — параметры сетки (origin, cellSize, размеры). Берётся из `grid_config.py`.
- `district_totals.json` — число доступных клеток на район + общий total (знаменатель %).
- `kazan_districts.geojson`, `kazan_excluded.geojson` — для отрисовки слоёв на карте.

## Шаги пайплайна (реализуем по порядку)
1. **`grid_config.py`** — единый источник правды для сетки (уже готов). ⚠️ Должен совпадать с Kotlin `GridMapper`.
2. **Скачать данные OSM**: экстракт Приволжского ФО / Татарстана с Geofabrik:
   https://download.geofabrik.de/russia/volga-fo.html → `volga-fo-latest.osm.pbf` (~150–250 МБ).
   Положить в `tools/data/` (эта папка в .gitignore — большой файл в репо не тащим).
3. **Извлечь полигоны** (`extract_polygons.py`, напишем след. шагом): граница Казани, 7 районов,
   исключения (`natural=water`, `waterway`, `landuse=industrial/military`, `access=private`,
   аэропорт, крупные ж/д-узлы, карьеры). Парки/лесопарки НЕ исключаем.
4. **Растеризовать** (`build_mask.py`): полигоны -> пиксели через `grid_config.latlon_to_cell`,
   заливка `cv2.fillPoly` в numpy-массив, сохранить PNG + посчитать totals.

## Установка и запуск
```powershell
cd tools
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -r requirements.txt
python grid_config.py        # проверка: печатает размер сетки и манифест
```
