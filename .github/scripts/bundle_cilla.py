#!/usr/bin/env python3
"""Download permitted Cilla Mini weights at build time; never embed HF_TOKEN."""
import os
import pathlib
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
    entries = walk()
    selected = []
    for entry in entries:
        path = entry["path"]
        if path in ("model.safetensors", "tokenizer.model", "config.yaml") or (
            path.startswith("prompts/") and path.endswith((".wav", ".safetensors"))
        ) or (path.startswith("onnx/") and path.endswith(".onnx")) or (
            path.startswith("phonon/model/") and path.endswith((".json", ".safetensors"))
        ):
            selected.append(entry)
    names = {x["path"] for x in selected}
    if "tokenizer.model" not in names or not (
        "model.safetensors" in names or any(x.startswith("onnx/") for x in names)
    ):
        raise RuntimeError("Required tokenizer or model weights missing from Hugging Face tree")
    phonon_required = {
        "phonon/model/config.json",
        "phonon/model/tokenizer.json",
        "phonon/model/model.safetensors",
        "phonon/model/voices/parisa.safetensors",
    }
    if not phonon_required.issubset(names):
        raise RuntimeError("Phonon checkpoint is incomplete: " + str(sorted(phonon_required - names)))
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
    print("Bundled", len(selected), "model files")

if __name__ == "__main__":
    main()
