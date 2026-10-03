"""Reads the vanilla assets zip of the pinned Hytale version and writes a pack's files: the asset side shared by the
generators (HyVanilla, HyColony's huts, items and construction tape, HyDomum). pack_rules.py validates the result."""

import io
import json
import zipfile
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]


def _gradle_property(name):
    for line in (ROOT / "gradle.properties").read_text(encoding="utf-8").splitlines():
        key, sep, value = line.partition("=")
        if sep and key.strip() == name:
            return value.strip()
    raise KeyError(name)


# The assets zip the Gradle plugin caches for the pinned Hytale version (downloadAssetsZip), so a version bump in
# gradle.properties needs no edit here.
GRADLE_ASSETS = (Path.home() / ".gradle" / "caches" / "hytale-assets"
                 / f"{_gradle_property('patchline')}-{_gradle_property('hytale_version')}-Assets.zip")


class Assets:
    """Read-only view of the vanilla assets zip."""

    def __init__(self, path):
        self.zip = zipfile.ZipFile(path)
        self.names = set(self.zip.namelist())

    def json(self, name):
        return json.loads(self.zip.read(name).decode("utf-8"))

    def image(self, name):
        return Image.open(io.BytesIO(self.zip.read(name))).convert("RGBA")

    def has(self, name):
        return name in self.names

    def item(self, item_id):
        """The item JSON with its Parent chain merged (BlockType merged key by key, like the codec's inheritance)."""
        if not hasattr(self, "_items"):
            self._items = {n.rsplit("/", 1)[1][:-5]: n for n in self.names
                           if n.startswith("Server/Item/Items/") and n.endswith(".json")}
        path = self._items[item_id]
        data = self.json(path)
        parent = data.get("Parent")
        if not parent:
            return data
        merged = self.item(parent)
        for key, value in data.items():
            if key == "BlockType" and isinstance(value, dict):
                merged["BlockType"] = {**merged.get("BlockType", {}), **value}
            else:
                merged[key] = value
        return merged


def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8", newline="\n")


def rounded(data):
    """data with every float rounded to 4 decimals (1/1000 of a pixel), for compact generated models."""
    if isinstance(data, float):
        return round(data, 4)
    if isinstance(data, dict):
        return {k: rounded(v) for k, v in data.items()}
    if isinstance(data, list):
        return [rounded(v) for v in data]
    return data


def save_png(image, path):
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path, optimize=True)
