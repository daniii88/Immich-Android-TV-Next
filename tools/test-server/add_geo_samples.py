#!/usr/bin/env python3
"""Uploads photos with GPS positions in cities all over the world (the stock media carry none), so the app's map page
has something to show. Needs the seeded server (./.state/credentials.txt) and Pillow.

    python3 add_geo_samples.py [http://localhost:2283]
"""
import io
import sys
from pathlib import Path

from PIL import Image

sys.argv = [sys.argv[0]] + sys.argv[1:]  # add_exif_sample reads the server url from argv[1]
import add_exif_sample as exif_upload  # noqa: E402

# (name, latitude, longitude, photos)
PLACES = [
    ("Zurich", 47.3769, 8.5417, 4), ("Paris", 48.8566, 2.3522, 6), ("London", 51.5072, -0.1276, 5),
    ("Rome", 41.9028, 12.4964, 3), ("New York", 40.7128, -74.0060, 7), ("San Francisco", 37.7749, -122.4194, 3),
    ("Mexico City", 19.4326, -99.1332, 2), ("Rio de Janeiro", -22.9068, -43.1729, 4), ("Cape Town", -33.9249, 18.4241, 2),
    ("Cairo", 30.0444, 31.2357, 2), ("Dubai", 25.2048, 55.2708, 3), ("Tokyo", 35.6762, 139.6503, 8),
    ("Sydney", -33.8688, 151.2093, 5), ("Reykjavik", 64.1466, -21.9426, 2),
]


def gps(value: float, positive: str, negative: str):
    ref = positive if value >= 0 else negative
    value = abs(value)
    degrees = int(value)
    minutes = int((value - degrees) * 60)
    seconds = round((value - degrees - minutes / 60) * 3600, 2)
    return ref, (float(degrees), float(minutes), seconds)


def photo(source: Path, lat: float, lon: float, shade: int) -> bytes:
    image = Image.open(source).convert("RGB").resize((1280, 853))
    image = Image.eval(image, lambda v: min(255, v + shade))
    exif = Image.Exif()
    exif[0x010F], exif[0x0110] = "Google", "Pixel 8 Pro"
    exif.get_ifd(0x8769)[0x9003] = "2025:06:01 12:00:00"
    gps_ifd = exif.get_ifd(0x8825)
    gps_ifd[1], gps_ifd[2] = gps(lat, "N", "S")
    gps_ifd[3], gps_ifd[4] = gps(lon, "E", "W")
    buffer = io.BytesIO()
    image.save(buffer, "JPEG", quality=85, exif=exif)
    return buffer.getvalue()


def main():
    sources = sorted((Path(__file__).parent / "media" / "photos").glob("*.jpg"))
    key = exif_upload.api_key()
    count = 0
    for place, lat, lon, photos in PLACES:
        for n in range(photos):
            source = sources[(count * 7) % len(sources)]
            # a little spread around the city centre so the photos are not all the same point
            data = photo(source, lat + 0.01 * (n % 3), lon + 0.01 * (n // 3), shade=count % 40)
            path = Path(f"/tmp/geo-{place.replace(' ', '-').lower()}-{n}.jpg")
            path.write_bytes(data)
            exif_upload.upload(path, key)
            path.unlink()
            count += 1
        print("uploaded", photos, "photos in", place)


if __name__ == "__main__":
    main()
