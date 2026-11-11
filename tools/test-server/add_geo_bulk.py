#!/usr/bin/env python3
"""Uploads many small photos with random GPS positions inside a region (default: Germany), to try the map page with a
big library in one country: a few hundred places, a few photos in most, many in some. Needs the seeded server and Pillow.

    python3 add_geo_bulk.py [count] [http://localhost:2283]
"""
import io
import random
import sys
import tempfile
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

from PIL import Image

COUNT = int(sys.argv[1]) if len(sys.argv) > 1 else 600
sys.argv = [sys.argv[0]] + sys.argv[2:]
import add_exif_sample as exif_upload  # noqa: E402
from add_geo_samples import gps  # noqa: E402

# a rough box around Germany, plus a few big cities that get many photos
BOX = (47.5, 54.8, 6.0, 14.8)
HOTSPOTS = [(52.52, 13.405), (48.137, 11.575), (53.55, 9.99), (50.94, 6.96), (50.11, 8.68), (48.78, 9.18)]


def tiny_photo(rng: random.Random, lat: float, lon: float) -> bytes:
    image = Image.new("RGB", (96, 64), tuple(rng.randrange(40, 220) for _ in range(3)))
    for _ in range(12):  # noise so every file has its own checksum
        image.putpixel((rng.randrange(96), rng.randrange(64)), tuple(rng.randrange(256) for _ in range(3)))
    exif = Image.Exif()
    exif.get_ifd(0x8769)[0x9003] = "2025:05:01 12:00:00"
    g = exif.get_ifd(0x8825)
    g[1], g[2] = gps(lat, "N", "S")
    g[3], g[4] = gps(lon, "E", "W")
    buffer = io.BytesIO()
    image.save(buffer, "JPEG", quality=70, exif=exif)
    return buffer.getvalue()


def main():
    rng = random.Random(11)
    key = exif_upload.api_key()
    tmp = Path(tempfile.mkdtemp())

    def one(n: int):
        r = random.Random(n)
        if r.random() < 0.45:  # big cities
            lat, lon = r.choice(HOTSPOTS)
            lat, lon = lat + r.gauss(0, 0.08), lon + r.gauss(0, 0.1)
        else:
            lat, lon = r.uniform(BOX[0], BOX[1]), r.uniform(BOX[2], BOX[3])
        path = tmp / f"bulk-{n}.jpg"
        path.write_bytes(tiny_photo(r, lat, lon))
        exif_upload.upload(path, key)
        path.unlink()

    with ThreadPoolExecutor(max_workers=6) as pool:
        for done, _ in enumerate(pool.map(one, range(COUNT)), 1):
            if done % 100 == 0:
                print("uploaded", done)
    print("done", COUNT)


if __name__ == "__main__":
    main()
