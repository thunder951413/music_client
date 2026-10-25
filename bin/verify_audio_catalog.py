#!/usr/bin/env python3
"""Check bundled public RSS and live streams, including enclosure/HLS media bytes.

Run manually, not as a network-dependent unit test:
  python3 bin/verify_audio_catalog.py --output /tmp/audio-source-health.json
"""
import argparse
import concurrent.futures
import datetime
import json
from pathlib import Path
import urllib.parse
import urllib.request
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
USER_AGENT = "Tempus/1.0 PodcastReader"


def request(url):
    return urllib.request.urlopen(urllib.request.Request(url, headers={"User-Agent": USER_AGENT}), timeout=25)


def feed_episode(url):
    parser = ET.XMLPullParser(["end"])
    count = 0
    title = None
    with request(url) as response:
        while True:
            chunk = response.read(8192)
            if not chunk:
                break
            count += len(chunk)
            if count > 8 * 1024 * 1024:
                raise ValueError("No audio entry within 8 MiB")
            parser.feed(chunk)
            for _, element in parser.read_events():
                name = element.tag.rsplit("}", 1)[-1]
                if name == "title" and title is None:
                    title = element.text
                if name in ("enclosure", "link"):
                    if name == "link" and element.get("rel") != "enclosure":
                        continue
                    mime = element.get("type", "audio/mpeg")
                    audio = element.get("url") or element.get("href")
                    if audio and mime.startswith("audio/"):
                        return title, urllib.parse.urljoin(response.url, audio)
    raise ValueError("No audio enclosure")


def media_bytes(url):
    for _ in range(4):
        with request(url) as response:
            mime = response.headers.get("Content-Type", "")
            chunk = response.read(65536 if "mpegurl" in mime.lower() or ".m3u8" in url else 2048)
            resolved = response.url
        if chunk.startswith(b"#EXTM3U"):
            links = [line.strip() for line in chunk.decode("utf-8-sig").splitlines() if line.strip() and not line.startswith("#")]
            if not links:
                raise ValueError("Empty HLS playlist")
            url = urllib.parse.urljoin(resolved, links[0])
        else:
            if len(chunk) < 100 or "html" in mime.lower():
                raise ValueError("No media bytes")
            return mime, len(chunk)
    raise ValueError("Too many nested HLS playlists")


def check(pair):
    kind, entry = pair
    result = {"id": entry["id"], "name": entry["name"], "kind": kind, "url": entry["url"]}
    try:
        url = entry["url"]
        if kind == "podcast":
            result["feedTitle"], url = feed_episode(url)
        result["mediaContentType"], result["mediaBytesRead"] = media_bytes(url)
        result["ok"] = True
    except Exception as error:
        result.update(ok=False, error=str(error))
    return result


if __name__ == "__main__":
    args = argparse.ArgumentParser()
    args.add_argument("--output", required=True, type=Path)
    options = args.parse_args()
    catalog = json.loads((ROOT / "app/src/main/assets/chinese_audio_catalog.json").read_text())
    sources = [(kind, entry) for key, kind in (("radio", "radio"), ("podcasts", "podcast")) for entry in catalog[key]]
    with concurrent.futures.ThreadPoolExecutor(max_workers=6) as pool:
        results = list(pool.map(check, sources))
    options.output.write_text(json.dumps({"checkedAt": datetime.datetime.now(datetime.timezone.utc).isoformat(), "results": results}, ensure_ascii=False, indent=2))
    failed = [row for row in results if not row["ok"]]
    print(f"{len(results) - len(failed)}/{len(results)} sources returned media bytes; report: {options.output}")
    for row in failed:
        print(f"  {row['name']}: {row['error']}")
    raise SystemExit(bool(failed))
