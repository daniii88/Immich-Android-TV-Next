# Local test server

A real Immich server filled with stock media (Pexels), so the app can be tested and README screenshots
taken without private photos.

1. Install Docker (once): `sudo apt install docker.io docker-compose-v2 && sudo usermod -aG docker $USER` (log in again).
2. `docker compose up -d` (first start pulls the images; add `--profile ml` for faces/smart search).
3. `python3 fetch_media.py` downloads the curated Pexels set from `media.json` (about 55 MB, no API key).
4. `python3 seed.py` -> creates the admin user, an API key and uploads `./media` (albums per theme).
5. `python3 add_exif_sample.py` (optional, needs Pillow) uploads one photo with camera, lens, exposure and GPS data, since the stock media carry none; handy for the viewer's info panel.
6. `python3 add_geo_samples.py` (optional, needs Pillow) uploads photos with GPS positions in 14 cities worldwide for the map page; `python3 add_stack_and_live_samples.py` adds a stack and a live photo.
7. `python3 add_geo_bulk.py 500` (optional, needs Pillow) uploads many small photos at random positions in Germany, to try the map with a big library in one country.
8. In the app use the URL and API key from `.state/credentials.txt`. In the emulator the host is `http://10.0.2.2:2283`.

`media/`, `data/` and `.state/` are git-ignored. `docker compose down && rm -rf data` resets the server.
