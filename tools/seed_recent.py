"""visited.bin с клетками, размазанными по последним 14 дням —
чтобы на эмуляторе проверить график активности (столбики по дням)."""
import math
import struct
import sys
import time
sys.path.insert(0, "tools")
import grid_config as gc

LAT0, LON0 = 55.7963, 49.1088  # центр (Кремль)
cx0 = int(math.floor((LON0 - gc.LON_ORIGIN) * gc.M_PER_DEG_LON / gc.CELL_SIZE_M))
cy0 = int(math.floor((LAT0 - gc.LAT_ORIGIN) * gc.M_PER_DEG_LAT / gc.CELL_SIZE_M))

# Сколько клеток открыто в каждый из 14 дней (индекс 0 = 13 дней назад … 13 = сегодня).
counts = [0, 0, 8, 20, 5, 0, 30, 42, 15, 0, 12, 50, 28, 35]
total = sum(counts)

# Набираем `total` различных клеток по расширяющемуся диску.
cells = []
r = 0
while len(cells) < total:
    for dx in range(-r, r + 1):
        for dy in range(-r, r + 1):
            if max(abs(dx), abs(dy)) == r:  # «кольцо» текущего радиуса
                cells.append((cx0 + dx, cy0 + dy))
    r += 1
cells = cells[:total]

now_ms = int(time.time() * 1000)
DAY = 86_400_000

records = []
idx = 0
for day_i, c in enumerate(counts):
    days_ago = (len(counts) - 1) - day_i
    ts = now_ms - days_ago * DAY  # то же время суток N дней назад
    for _ in range(c):
        cx, cy = cells[idx]; idx += 1
        cid = (cx << 32) | (cy & 0xFFFFFFFF)
        records.append((cid, ts))

with open("tools/output/visited.bin", "wb") as f:
    for cid, ts in records:
        f.write(struct.pack("<qq", cid, ts))

print(f"wrote {len(records)} cells across {len(counts)} days -> tools/output/visited.bin")
print("per-day:", counts, "total", total)
