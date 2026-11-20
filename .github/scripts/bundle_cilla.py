#!/usr/bin/env python3
"""Download permitted Cilla Mini weights at build time; never embed HF_TOKEN."""
import os
import pathlib
import shutil
import urllib.error
import urllib.parse
import urllib.request
import json

REPO = "mehdi-hf/cilla-mini"
TOKEN = os.environ["HF_TOKEN"]
DEST = pathlib.Path("app/src/main/assets/cilla-mini")
HEADERS = {"Authorization": "Bearer " + TOKEN, "User-Agent": "tts-app-actions/1.0"}

def fetch(url):
    req = urllib.request.Request(url, headers=HEADERS)
    return urllib.request.urlopen(req, timeout=180)

def walk(folder=""):
    endpoint = f"https://huggingface.co/api/models/{REPO}/tree/main"
    if folder:
        endpoint += "/" + urllib.parse.quote(folder, safe="/")
    endpoint += "?recursive=true&expand=false"
    with fetch(endpoint) as response:
        items = json.load(response)
    return [x for x in items if x.get("type") == "file"]

def main():
    # Query the nested folders explicitly; the HF tree API root listing may be paginated.
    entries = walk("phonon/model")
    # Bundle one checkpoint only: the Phonon runtime consumes phonon/model/*.
    # Legacy root weights and ONNX are deliberately excluded to avoid ~100MB duplication.
    selected = [
        entry for entry in entries
        if entry["path"].startswith("phonon/model/")
        and entry["path"].endswith((".json", ".safetensors"))
    ]
    print("Discovered Phonon assets:", sorted(e["path"] for e in selected), flush=True)
    names = {entry["path"] for entry in selected}
    phonon_required = {
        "phonon/model/config.json",
        "phonon/model/tokenizer.json",
        "phonon/model/model.safetensors",
        "phonon/model/voices/parisa.safetensors",
        "phonon/model/voices/voice1.safetensors",
        "phonon/model/voices/voice2.safetensors",
    }
    if not phonon_required.issubset(names):
        raise RuntimeError("Phonon checkpoint is incomplete: " + str(sorted(phonon_required - names)))
    # Ensure stale files from earlier builds cannot leak into the APK.
    if DEST.exists():
        shutil.rmtree(DEST)
    for entry in selected:
        path = entry["path"]
        target = DEST / path
        target.parent.mkdir(parents=True, exist_ok=True)
        url = f"https://huggingface.co/{REPO}/resolve/main/" + urllib.parse.quote(path, safe="/")
        print("Downloading", path, flush=True)
        with fetch(url) as response, target.open("wb") as output:
            while chunk := response.read(1024 * 1024):
                output.write(chunk)
        expected = entry.get("size", 0)
        if expected and target.stat().st_size != expected:
            raise RuntimeError(f"Size mismatch for {path}")
    for required in sorted(phonon_required):
        target = DEST / required
        if not target.is_file() or target.stat().st_size < 1024:
            raise RuntimeError(f"Missing or empty bundled model asset: {required}")
    print("Bundled", len(selected), "model files")

if __name__ == "__main__":
    main()
