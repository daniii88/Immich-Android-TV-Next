#!/usr/bin/env python3
"""Uploads one photo WITH camera, lens, exposure and GPS data (the stock media carry none), so the viewer's info
panel has everything to show. Needs the seeded server (./.state/credentials.txt) and Pillow.

    python3 add_exif_sample.py [http://localhost:2283]
"""
import json
import re
import sys
import tempfile
import uuid
from datetime import datetime, timezone
from pathlib import Path
from urllib import request

from PIL import Image

HERE = Path(__file__).parent
BASE = (sys.argv[1] if len(sys.argv) > 1 else "http://localhost:2283").rstrip("/")


def api_key() -> str:
    text = (HERE / ".state" / "credentials.txt").read_text()
    return re.search(r"api key:\s*(\S+)", text).group(1)


def with_exif(source: Path, target: Path) -> None:
    image = Image.open(source).convert("RGB")
    exif = Image.Exif()
    exif[0x010F] = "Google"                      # Make
    exif[0x0110] = "Pixel 8 Pro"                 # Model
    exif[0x0132] = "2025:08:14 08:02:19"         # DateTime
    exif_ifd = exif.get_ifd(0x8769)
    exif_ifd[0x9003] = "2025:08:14 08:02:19"     # DateTimeOriginal
    exif_ifd[0x829A] = 1 / 250         # ExposureTime
    exif_ifd[0x829D] = 1.8             # FNumber
    exif_ifd[0x8827] = 100                       # ISO
    exif_ifd[0x920A] = 6.9             # FocalLength
    exif_ifd[0xA434] = "Pixel 8 Pro back camera 6.9mm f/1.68"  # LensModel
    gps = exif.get_ifd(0x8825)
    gps[1], gps[2] = "N", (46.0, 56.0, 40.0)    # Bern, Switzerland
    gps[3], gps[4] = "E", (7.0, 26.0, 30.0)
    image.save(target, "JPEG", quality=90, exif=exif)


def upload(path: Path, key: str) -> str:
    boundary = uuid.uuid4().hex
    iso = datetime(2025, 8, 14, 8, 2, 19, tzinfo=timezone.utc).strftime("%Y-%m-%dT%H:%M:%S.000Z")
    fields = {"deviceAssetId": path.name, "deviceId": "test-seed", "fileCreatedAt": iso,
              "fileModifiedAt": iso, "isFavorite": "false"}
    parts = [f'--{boundary}\r\nContent-Disposition: form-data; name="{n}"\r\n\r\n{v}\r\n'.encode() for n, v in fields.items()]
    parts.append(f'--{boundary}\r\nContent-Disposition: form-data; name="assetData"; filename="{path.name}"\r\n'
                 "Content-Type: image/jpeg\r\n\r\n".encode())
    parts += [path.read_bytes(), f"\r\n--{boundary}--\r\n".encode()]
    req = request.Request(BASE + "/api/assets", data=b"".join(parts), method="POST",
                          headers={"x-api-key": key, "Accept": "application/json",
                                   "Content-Type": f"multipart/form-data; boundary={boundary}"})
    with request.urlopen(req, timeout=300) as response:
        return json.loads(response.read())["id"]


def main():
    source = sorted((HERE / "media").rglob("mountains-*.jpg"))[0]
    with tempfile.TemporaryDirectory() as tmp:
        sample = Path(tmp) / "exif-sample-pixel8.jpg"
        with_exif(source, sample)
        print("uploaded", sample.name, upload(sample, api_key()))


if __name__ == "__main__":
    main()
