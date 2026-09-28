"""Domum Ornamentum's block models at a pinned commit: download (cached under build/), parent resolution and the list
of shapes the test bench turns into items."""

import json
import urllib.request
from pathlib import Path

COMMIT = "82729d6c9dc0499b256b36b4506d0d9ef20e8aec"  # version/latest, 2026-08-30 (docs/research/domum-ornamentum.md)
REPO = "ldtteam/Domum-Ornamentum"
MODELS = "src/main/resources/assets/domum_ornamentum/models/block/"
CACHE = Path(__file__).resolve().parents[2] / "build" / "domum-cache" / COMMIT
DO_PARENT = "domum_ornamentum:block/"

# Door halves: only the closed, left-hinged ones, bottom and top merged into one two-block model.
DOOR_BOTTOM = "_bottom_left_spec"
DOOR_TOP = "_top_left_spec"


def fetch():
    """Downloads every block model of the pinned commit into CACHE once; returns CACHE."""
    marker = CACHE / ".complete"
    if marker.exists():
        return CACHE
    tree_url = f"https://api.github.com/repos/{REPO}/git/trees/{COMMIT}?recursive=1"
    tree = json.loads(urllib.request.urlopen(tree_url).read())
    for entry in tree["tree"]:
        path = entry["path"]
        if path.startswith(MODELS) and path.endswith(".json"):
            target = CACHE / path[len(MODELS):]
            target.parent.mkdir(parents=True, exist_ok=True)
            raw = f"https://raw.githubusercontent.com/{REPO}/{COMMIT}/{path}"
            target.write_bytes(urllib.request.urlopen(raw).read())
    marker.write_text("ok\n")
    return CACHE


def load(root, name):
    """The model root/name.json with its DO-internal parents merged: the child's textures win, and its elements
    replace the parent's (Minecraft's rule). Vanilla parents (block/block, cube_all...) carry no DO geometry."""
    model = json.loads((root / (name + ".json")).read_text(encoding="utf-8"))
    parent = model.get("parent", "")
    if not parent.startswith(DO_PARENT):
        return {"textures": model.get("textures", {}), "elements": model.get("elements", [])}
    base = load(root, parent[len(DO_PARENT):])
    return {"textures": {**base["textures"], **model.get("textures", {})},
            "elements": model.get("elements") or base["elements"]}


def shapes(root):
    """(folder, stem, model) for every block model with elements of its own, sorted; a door is its closed left
    bottom half with its top half raised one block on top (open and right-hinged halves are left out)."""
    result = []
    for path in sorted(root.rglob("*.json")):
        name = path.relative_to(root).with_suffix("").as_posix()
        folder, _, stem = name.rpartition("/")
        if folder.startswith("door"):
            if not stem.endswith(DOOR_BOTTOM):
                continue
            model = door(root, folder + "/" + stem)
            stem = stem[: -len(DOOR_BOTTOM)]
        elif "elements" in json.loads(path.read_text(encoding="utf-8")):
            model = load(root, name)
        else:
            continue  # a copy of its DO parent, a forge:composite of other models, or vanilla geometry only
        if model["elements"]:
            result.append((folder, stem, model))
    return result


def door(root, bottom):
    low = load(root, bottom)
    high = load(root, bottom.replace(DOOR_BOTTOM, DOOR_TOP))
    raised = []
    for element in high["elements"]:
        element = json.loads(json.dumps(element))
        element["from"][1] += 16
        element["to"][1] += 16
        if "rotation" in element:
            element["rotation"]["origin"][1] += 16
        raised.append(element)
    return {"textures": {**high["textures"], **low["textures"]}, "elements": low["elements"] + raised}
