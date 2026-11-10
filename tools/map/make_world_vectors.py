#!/usr/bin/env python3
"""Builds the coastline and border vectors the map page draws when zoomed in:
app/src/main/res/raw/world_land_50m.bin and world_borders_50m.bin.

    python3 make_world_vectors.py ne_50m_land.geojson ne_50m_admin_0_boundary_lines_land.geojson

Input: Natural Earth 1:50m land polygons and country boundary lines (public domain),
https://github.com/nvkelso/natural-earth-vector (geojson/). The raw files are not part of the repository.

The shapes are simplified (Douglas-Peucker, 0.02 degrees), tiny islands and everything south of the drawn map are
left out. Layout (keep in sync with WorldVectors.kt), little endian: repeated parts, each
    uint16 point count, then that many (int16 longitude * 100, int16 latitude * 100) pairs.
Land parts are rings (exterior rings and holes alike, drawn with the even-odd rule), border parts are open lines.
"""
import json
import struct
import sys
from pathlib import Path

TOLERANCE = 0.02  # degrees
MIN_EXTENT = 0.08  # degrees: smaller islands are not worth their bytes
LAT_BOTTOM = -57.0  # the map ends here (WorldDots.LAT_BOTTOM)
OUT = Path(__file__).resolve().parents[2] / "app/src/main/res/raw"


def simplify(points, tolerance):
    """Douglas-Peucker, iterative."""
    if len(points) < 3:
        return points
    keep = [False] * len(points)
    keep[0] = keep[-1] = True
    stack = [(0, len(points) - 1)]
    while stack:
        a, b = stack.pop()
        (ax, ay), (bx, by) = points[a], points[b]
        dx, dy = bx - ax, by - ay
        length2 = dx * dx + dy * dy
        far, far_dist = -1, tolerance
        for i in range(a + 1, b):
            px, py = points[i]
            if length2 == 0:
                dist = ((px - ax) ** 2 + (py - ay) ** 2) ** 0.5
            else:
                t = max(0.0, min(1.0, ((px - ax) * dx + (py - ay) * dy) / length2))
                dist = ((px - (ax + t * dx)) ** 2 + (py - (ay + t * dy)) ** 2) ** 0.5
            if dist > far_dist:
                far, far_dist = i, dist
        if far >= 0:
            keep[far] = True
            stack += [(a, far), (far, b)]
    return [p for p, k in zip(points, keep) if k]


def parts(geometry):
    kind, c = geometry["type"], geometry["coordinates"]
    if kind == "Polygon":
        return list(c)
    if kind == "MultiPolygon":
        return [ring for polygon in c for ring in polygon]
    if kind == "LineString":
        return [c]
    if kind == "MultiLineString":
        return list(c)
    return []


def encode(path_in, path_out, closed):
    data = json.loads(Path(path_in).read_text())
    out = bytearray()
    kept = points_total = 0
    for feature in data["features"]:
        for part in parts(feature["geometry"]):
            xs, ys = [p[0] for p in part], [p[1] for p in part]
            if max(ys) < LAT_BOTTOM or (max(xs) - min(xs) < MIN_EXTENT and max(ys) - min(ys) < MIN_EXTENT):
                continue
            simple = simplify([(p[0], p[1]) for p in part], TOLERANCE)
            if len(simple) < (4 if closed else 2):
                continue
            out += struct.pack("<H", len(simple))
            for lon, lat in simple:
                out += struct.pack("<hh", round(lon * 100), round(lat * 100))
            kept += 1
            points_total += len(simple)
    Path(path_out).write_bytes(bytes(out))
    print(f"{path_out}: {len(out)} bytes, {kept} parts, {points_total} points")


if __name__ == "__main__":
    OUT.mkdir(parents=True, exist_ok=True)
    encode(sys.argv[1], OUT / "world_land_50m.bin", closed=True)
    encode(sys.argv[2], OUT / "world_borders_50m.bin", closed=False)
