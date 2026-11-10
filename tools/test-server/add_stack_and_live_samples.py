#!/usr/bin/env python3
"""Adds a stack (three photos) and a live photo (a still with a motion video) to the seeded test server, so the app's
stack badge, series viewer and live photo playback can be tried. Needs ./.state/credentials.txt and Pillow.

    python3 add_stack_and_live_samples.py [http://localhost:2283]
"""
import io
import json
import re
import sys
import uuid
from datetime import datetime, timezone
from pathlib import Path
from urllib import request

from PIL import Image

HERE = Path(__file__).parent
BASE = (sys.argv[1] if len(sys.argv) > 1 else "http://localhost:2283").rstrip("/")
KEY = re.search(r"api key:\s*(\S+)", (HERE / ".state" / "credentials.txt").read_text()).group(1)


def call(path, payload=None, method=None):
    data = None if payload is None else json.dumps(payload).encode()
    req = request.Request(BASE + path, data=data, method=method or ("POST" if data else "GET"),
                          headers={"x-api-key": KEY, "Content-Type": "application/json", "Accept": "application/json"})
    with request.urlopen(req, timeout=120) as response:
        body = response.read()
    return json.loads(body) if body else None


def upload(name: str, content: bytes, mime: str, extra=None) -> str:
    boundary = uuid.uuid4().hex
    iso = datetime(2025, 9, 1, 10, 0, 0, tzinfo=timezone.utc).strftime("%Y-%m-%dT%H:%M:%S.000Z")
    fields = {"deviceAssetId": name, "deviceId": "test-seed", "fileCreatedAt": iso, "fileModifiedAt": iso, **(extra or {})}
    parts = [f'--{boundary}\r\nContent-Disposition: form-data; name="{n}"\r\n\r\n{v}\r\n'.encode() for n, v in fields.items()]
    parts.append(f'--{boundary}\r\nContent-Disposition: form-data; name="assetData"; filename="{name}"\r\nContent-Type: {mime}\r\n\r\n'.encode())
    parts += [content, f"\r\n--{boundary}--\r\n".encode()]
    req = request.Request(BASE + "/api/assets", data=b"".join(parts), method="POST",
                          headers={"x-api-key": KEY, "Accept": "application/json",
                                   "Content-Type": f"multipart/form-data; boundary={boundary}"})
    with request.urlopen(req, timeout=300) as response:
        return json.loads(response.read())["id"]


def unique_jpeg(source: Path, shade: int) -> bytes:
    """A copy of a stock photo with a tweak, so every upload has its own checksum."""
    image = Image.open(source).convert("RGB").resize((1600, 1067))
    image = Image.eval(image, lambda value: min(255, value + shade))
    buffer = io.BytesIO()
    image.save(buffer, "JPEG", quality=88)
    return buffer.getvalue()


def main():
    photos = sorted((HERE / "media" / "photos").glob("city-*.jpg"))
    stack_ids = [upload(f"stack-sample-{i}.jpg", unique_jpeg(photos[i % len(photos)], 10 * i), "image/jpeg") for i in range(3)]
    call("/api/stacks", {"assetIds": stack_ids})
    print("stack of", len(stack_ids), "photos, primary", stack_ids[0])

    video = sorted((HERE / "media" / "videos").glob("*.mp4"))[0].read_bytes() + b"\x00\x00\x00\x08free"  # own checksum
    video_id = upload("live-sample.mov", video, "video/quicktime")
    live_id = upload("live-sample.jpg", unique_jpeg(sorted((HERE / "media" / "photos").glob("beach-*.jpg"))[0], 40),
                     "image/jpeg", {"livePhotoVideoId": video_id})
    print("live photo", live_id, "with video", video_id)


if __name__ == "__main__":
    main()
