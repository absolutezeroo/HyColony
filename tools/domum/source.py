"""Domum Ornamentum's block models and blockstates at a pinned commit: download (cached under build/), parent
resolution."""

import json
import urllib.request
from pathlib import Path

COMMIT = "82729d6c9dc0499b256b36b4506d0d9ef20e8aec"  # version/latest, 2026-08-30 (docs/research/domum-ornamentum.md)
REPO = "ldtteam/Domum-Ornamentum"
MODEL_PATH = "src/main/resources/assets/domum_ornamentum/models/block/"
# Every blockstate's "model" field (assemble.parts) names a thin, datagen-generated wrapper under this path
# (e.g. block/door/door_full_bottom_left), not the hand-authored "_spec" model under MODEL_PATH that carries
# the actual geometry; that wrapper's own "parent" points back to the matching "_spec" file under MODEL_PATH.
GENERATED_MODEL_PATH = "src/datagen/generated/domum_ornamentum/assets/domum_ornamentum/models/block/"
BLOCKSTATE_PATH = "src/datagen/generated/domum_ornamentum/assets/domum_ornamentum/blockstates/"
TIMEOUT_S = 30  # seconds per GitHub request: a stalled download fails instead of hanging
CACHE = Path(__file__).resolve().parents[2] / "build" / "domum-cache" / COMMIT
DO_PARENT = "domum_ornamentum:block/"


def is_complete(root):
    """Whether fetch() finished downloading into root: a cache without its final marker is never trusted."""
    return (root / ".complete").exists()


def fetch():
    """Downloads every block model and blockstate of the pinned commit into CACHE once; returns CACHE.

    Files land under CACHE/models/, CACHE/models-generated/ and CACHE/blockstates/. The .complete marker
    is written last, so an interrupted download leaves is_complete() false and the next call refetches
    everything.
    """
    if is_complete(CACHE):
        return CACHE
    tree_url = f"https://api.github.com/repos/{REPO}/git/trees/{COMMIT}?recursive=1"
    tree = json.loads(urllib.request.urlopen(tree_url, timeout=TIMEOUT_S).read())
    for entry in tree["tree"]:
        path = entry["path"]
        if path.startswith(MODEL_PATH) and path.endswith(".json"):
            _download(path, CACHE / "models" / path[len(MODEL_PATH):])
        elif path.startswith(GENERATED_MODEL_PATH) and path.endswith(".json"):
            _download(path, CACHE / "models-generated" / path[len(GENERATED_MODEL_PATH):])
        elif path.startswith(BLOCKSTATE_PATH) and path.endswith(".json"):
            _download(path, CACHE / "blockstates" / path[len(BLOCKSTATE_PATH):])
    (CACHE / ".complete").write_text("ok\n")
    return CACHE


def _download(repo_path, target):
    target.parent.mkdir(parents=True, exist_ok=True)
    raw = f"https://raw.githubusercontent.com/{REPO}/{COMMIT}/{repo_path}"
    target.write_bytes(urllib.request.urlopen(raw, timeout=TIMEOUT_S).read())


def blockstate(root, block_id):
    """The generated blockstate (variants or multipart) of a DO block id, downloaded by fetch()."""
    return json.loads((root / "blockstates" / (block_id + ".json")).read_text(encoding="utf-8"))


def load(root, name):
    """The model root/models/name.json (falling back to root/models-generated/name.json, the thin
    per-state wrapper a blockstate's own "model" field names) with its DO-internal parents merged: the
    child's textures win, and its elements replace the parent's (Minecraft's rule). Vanilla parents
    (block/block, cube_all...) carry no DO geometry."""
    path = root / "models" / (name + ".json")
    if not path.exists():
        path = root / "models-generated" / (name + ".json")
    model = json.loads(path.read_text(encoding="utf-8"))
    parent = model.get("parent", "")
    if not parent.startswith(DO_PARENT):
        return {"textures": model.get("textures", {}), "elements": model.get("elements", [])}
    base = load(root, parent[len(DO_PARENT):])
    return {"textures": {**base["textures"], **model.get("textures", {})},
            "elements": model.get("elements") or base["elements"]}

