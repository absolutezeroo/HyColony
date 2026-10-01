"""Cases de remplissage des blocs Hytale à plusieurs cases (établi, chaise, épouvantail…). Une case `Empty` du plan qui
tombe dedans effacerait le modèle : le constructeur viderait la case après la pose, et casserait ainsi tout le bloc.

Calcul de Hytale : FillerBlockUtil.forEachFillerBlock (seuil 0) sur la boîte englobante des hitbox
(BlockBoundingBoxes.RotatedVariantBoxes), tournée en lacet par BlockBoundingBoxes.rotate90Y (x' = z, z' = 1 - x).
Seul le lacet compte : les blocs tournés en tangage ou en roulis des plans (escaliers, dalles, troncs) tiennent sur
une case.
"""
from __future__ import annotations

import json
import math
import zipfile
from functools import lru_cache
from pathlib import Path, PurePosixPath

from .validation import REPO, base_id, pinned_assets_zip

Offset = tuple[int, int, int]
_ITEMS = "Server/Item/Items/"
_HITBOXES = "Server/Item/Block/Hitboxes/"
# Les plugins du dépôt dont les blocs peuvent finir dans un plan.
_MODS = ("plugin", "domum/plugin", "vanilla/plugin")


def filler_offsets(boxes: list[dict], rotation: int) -> set[Offset]:
    """Les cases que le bloc occupe en plus de la sienne, pour une rotation de lacet de `rotation` quarts de tour."""
    lo = [min(b["Min"][k] for b in boxes) for k in "XYZ"]
    hi = [max(b["Max"][k] for b in boxes) for k in "XYZ"]
    for _ in range(rotation % 4):
        lo, hi = [lo[2], lo[1], 1 - hi[0]], [hi[2], hi[1], 1 - lo[0]]
    start = [math.floor(v) for v in lo]
    size = [max(math.ceil(h) - s, 1) for h, s in zip(hi, start)]
    return {(start[0] + x, start[1] + y, start[2] + z)
            for x in range(size[0]) for y in range(size[1]) for z in range(size[2])} - {(0, 0, 0)}


class Hitboxes:
    """Le type de hitbox de chaque objet (`<objet>`) et de chaque état qui a le sien (`<objet>#<état>`), et les boîtes
    de chaque type ; un bloc sans type tient sur sa case."""

    def __init__(self, hitbox_of: dict[str, str], boxes: dict[str, list[dict]]):
        self._hitbox_of = hitbox_of
        self._boxes = boxes
        self._cache: dict[tuple[str, int], set[Offset]] = {}

    def offsets(self, block: str, rotation: int) -> set[Offset]:
        """Les cases de remplissage du bloc (avec son état : chaque état est son propre BlockType dans Hytale)."""
        key = (block, rotation % 4)
        if key not in self._cache:
            base = base_id(block)
            _, _, state = block.partition("_State_Definitions_")
            hitbox = self._hitbox_of.get(f"{base}#{state}") or self._hitbox_of.get(base, "")
            boxes = self._boxes.get(hitbox)
            self._cache[key] = filler_offsets(boxes, key[1]) if boxes else set()
        return self._cache[key]

    def drop_covered_empties(self, blocks: list[dict]) -> list[dict]:
        """`blocks` sans les entrées `Empty` qui tombent dans les cases de remplissage d'un autre bloc."""
        covered = set()
        for b in blocks:
            if b["name"] != "Empty":
                for dx, dy, dz in self.offsets(b["name"], b.get("rotation", 0)):
                    covered.add((b["x"] + dx, b["y"] + dy, b["z"] + dz))
        return [b for b in blocks if b["name"] != "Empty" or (b["x"], b["y"], b["z"]) not in covered]


def _read(text: bytes | str) -> dict | None:
    try:
        return json.loads(text.decode("utf-8-sig") if isinstance(text, bytes) else text.lstrip("﻿"))
    except ValueError:
        return None


@lru_cache(maxsize=1)
def default() -> Hitboxes | None:
    """Les hitbox du jeu épinglé et des mods du dépôt ; None sans le zip d'assets (la passe est alors sautée)."""
    zip_path = pinned_assets_zip()
    if zip_path is None:
        print("attention : pas de zip d'assets épinglé, les cases vides dans les blocs à plusieurs cases restent")
        return None
    items: dict[str, dict] = {}
    boxes: dict[str, list[dict]] = {}

    def add(name: str, data: dict | None, hitbox: bool) -> None:
        if data is not None:
            if hitbox and "Boxes" in data:
                boxes[name] = data["Boxes"]
            elif not hitbox:
                items[name] = data

    with zipfile.ZipFile(zip_path) as z:
        for n in z.namelist():
            if n.endswith(".json") and n.startswith((_ITEMS, _HITBOXES)):
                add(PurePosixPath(n).stem, _read(z.read(n)), n.startswith(_HITBOXES))
    for mod in _MODS:
        for p in (REPO / mod / "src/main/resources/Server/Item").glob("**/*.json"):
            add(p.stem, _read(p.read_text(encoding="utf-8")), "/Block/Hitboxes/" in p.as_posix())
    hitbox_of = {}
    for name in items:
        for state, hitbox in _hitbox_types(items, name).items():
            hitbox_of[f"{name}#{state}" if state else name] = hitbox
    return Hitboxes(hitbox_of, boxes)


def _hitbox_types(items: dict[str, dict], name: str) -> dict[str, str]:
    """Le HitboxType du BlockType de l'objet (clé "") et de chacun de ses états (BlockType.State.Definitions), le plus
    proche l'emportant le long des parents (Parent)."""
    found: dict[str, str] = {}
    for _ in range(10):
        item = items.get(name)
        if item is None:
            break
        block = item.get("BlockType") or {}
        if block.get("HitboxType"):
            found.setdefault("", block["HitboxType"])
        for state, definition in ((block.get("State") or {}).get("Definitions") or {}).items():
            if isinstance(definition, dict) and definition.get("HitboxType"):
                found.setdefault(state, definition["HitboxType"])
        if "Parent" not in item:
            break
        name = item["Parent"]
    return found
