#!/usr/bin/env python3
"""Builds app/src/main/res/raw/world_land_dots.bin, the land mask the map page draws as a dot grid.

    python3 make_world_dots.py ne_110m_land.geojson [out.bin]

Input: Natural Earth 110m land polygons (public domain), https://github.com/nvkelso/natural-earth-vector (geojson/ne_110m_land.geojson).
The raw file is not part of the repository, only the generated mask. Layout (keep in sync with WorldDots.kt):
COLS x ROWS cells of 1.5 degrees (equirectangular, west to east, from LAT_TOP down to LAT_BOTTOM), one bit per cell,
row by row, most significant bit first; a set bit means the cell centre is on land.
"""
import json
import sys
from pathlib import Path

COLS, ROWS = 240, 94
LAT_TOP, LAT_BOTTOM = 84.0, -57.0  # no Antarctica, no polar cap: the photos are not there
STEP = 1.5


def inside(ring, x, y):
    result = False
    j = len(ring) - 1
    for i in range(len(ring)):
        xi, yi = ring[i][0], ring[i][1]
        xj, yj = ring[j][0], ring[j][1]
        if (yi > y) != (yj > y) and x < (xj - xi) * (y - yi) / (yj - yi) + xi:
            result = not result
        j = i
    return result


def polygons(geometry):
    if geometry["type"] == "Polygon":
        yield geometry["coordinates"]
    elif geometry["type"] == "MultiPolygon":
        yield from geometry["coordinates"]


def main():
    data = json.loads(Path(sys.argv[1]).read_text())
    polys = [p for f in data["features"] for p in polygons(f["geometry"])]
    boxes = []
    for p in polys:
        xs = [c[0] for c in p[0]]
        ys = [c[1] for c in p[0]]
        boxes.append((min(xs), max(xs), min(ys), max(ys)))
    bits = []
    for row in range(ROWS):
        lat = LAT_TOP - (row + 0.5) * STEP
        for col in range(COLS):
            lon = -180.0 + (col + 0.5) * STEP
            land = False
            for p, (x0, x1, y0, y1) in zip(polys, boxes):
                if x0 <= lon <= x1 and y0 <= lat <= y1 and inside(p[0], lon, lat) and not any(inside(h, lon, lat) for h in p[1:]):
                    land = True
                    break
            bits.append(land)
    out = bytearray()
    for i in range(0, len(bits), 8):
        byte = 0
        for k in range(8):
            byte = (byte << 1) | (1 if i + k < len(bits) and bits[i + k] else 0)
        out.append(byte)
    target = Path(sys.argv[2]) if len(sys.argv) > 2 else Path(__file__).resolve().parents[2] / "app/src/main/res/raw/world_land_dots.bin"
    target.write_bytes(bytes(out))
    print(f"{target}: {len(out)} bytes, {sum(bits)} land cells of {len(bits)}")
    if "--ascii" in sys.argv:
        for row in range(0, ROWS, 2):
            print("".join("#" if bits[row * COLS + c] else "." for c in range(0, COLS, 2)))


if __name__ == "__main__":
    main()
