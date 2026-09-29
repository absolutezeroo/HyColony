"""Tapis, pots de fleurs et lits Minecraft -> blocs du mod HyVanilla (vanilla/plugin).

Les identifiants viennent du mod lui-même, jamais écrits à la main :
- vanilla/.../Server/Item/Items/HyVanilla/*.json : les blocs du mod ;
- vanilla/.../hyvanilla/id-map.json (`flowerPots`) : pour chaque pot, l'objet plante -> l'état du pot garni.

Couleurs : Hytale a 20 laines (11 teintes, 9 en `_Light`) et pas les 16 de Minecraft
(docs/research/carpets-flower-pots.md § 1) ; les couleurs absentes prennent la plus proche par le nom.
Le pot de Minecraft est en terre cuite : le pot HyVanilla en argile lisse orange (« marron »,
tools/vanilla/flower_pots.py).
Lits : le lit HyVanilla (1×2) a sa tête sur la case d'origine et s'étend vers +Z au lacet 0
(Server/Item/Block/Hitboxes/HyVanilla/HyVanilla_Bed.json), soit un lit Minecraft facing=north ; il se pose sur la
case « head », tourné comme le facing, et le jeu recrée la case du pied.
"""
from __future__ import annotations

import json
from functools import lru_cache
from pathlib import Path

from .geometry import yaw_for
from .model import Mapping, place, skip

REPO = Path(__file__).resolve().parents[2]
RESOURCES = REPO / "vanilla" / "plugin" / "src" / "main" / "resources"
UPSTREAM_CSV = Path(__file__).resolve().parent / "data" / "default-block-overrides.csv"

# Couleur Minecraft -> suffixe de HyVanilla_Carpet_<suffixe> et HyVanilla_Bed_<suffixe> (mêmes 20 laines).
CARPET_COLORS = {
    "white": "White", "orange": "Orange", "magenta": "Purple_Light", "light_blue": "Blue_Light",
    "yellow": "Yellow", "lime": "Green_Light", "pink": "Pink", "gray": "Gray", "light_gray": "Gray_Light",
    "cyan": "Cyan", "purple": "Purple", "blue": "Blue", "brown": "Orange", "green": "Green", "red": "Red",
    "black": "Black",
}
# Couleurs sans laine Hytale de ce nom : note dans le rapport.
APPROXIMATED = {"magenta", "light_blue", "lime", "brown"}
FLOWER_POT = "HyVanilla_Flower_Pot_Orange"


@lru_cache(maxsize=1)
def item_ids() -> frozenset[str]:
    """Les blocs du mod ; vide s'il n'est pas à côté."""
    folder = RESOURCES / "Server" / "Item" / "Items" / "HyVanilla"
    return frozenset(p.stem for p in folder.glob("*.json")) if folder.is_dir() else frozenset()


@lru_cache(maxsize=1)
def pot_states() -> dict[str, str]:
    """Objet plante -> état du pot de terre cuite garni ; vide sans le mod."""
    path = RESOURCES / "hyvanilla" / "id-map.json"
    if not path.exists():
        return {}
    return json.loads(path.read_text(encoding="utf-8")).get("flowerPots", {}).get(FLOWER_POT, {})


@lru_cache(maxsize=1)
def _plants() -> dict[str, str]:
    """Plante Minecraft -> objet plante Hytale, d'après la table HytalesHub (minecraft:poppy -> Plant_Flower_...)."""
    from .converter import load_mapping_csv  # noqa: PLC0415 (import circulaire au chargement)
    return load_mapping_csv(UPSTREAM_CSV)


def _colored(name: str, kind: str) -> tuple[str, str] | None:
    """(couleur Minecraft, suffixe HyVanilla) d'un `minecraft:<couleur>_<kind>` ; None sinon."""
    if not (name.startswith("minecraft:") and name.endswith(f"_{kind}")):
        return None
    color = name[len("minecraft:"):-len(kind) - 1]
    return (color, CARPET_COLORS[color]) if color in CARPET_COLORS else None


def rule(name: str, p: dict) -> Mapping | None:
    """Le bloc HyVanilla d'un tapis, d'un pot de fleurs ou d'un lit Minecraft ; None pour les autres blocs."""
    for kind, prefix in (("carpet", "HyVanilla_Carpet"), ("bed", "HyVanilla_Bed")):
        colored = _colored(name, kind)
        if colored is None:
            continue
        if kind == "bed" and p.get("part") == "foot":
            return skip("pied de lit : le lit HyVanilla occupe déjà cette case", rule="bed")
        target = f"{prefix}_{colored[1]}"
        note = f"{name} -> {target}" + (" (couleur la plus proche)" if colored[0] in APPROXIMATED else "")
        return place(target, yaw_for(p) if kind == "bed" else 0, note, rule="hyvanilla")
    if name == "minecraft:flower_pot":
        return place(FLOWER_POT, 0, f"pot de fleurs -> {FLOWER_POT}", rule="hyvanilla")
    if name.startswith("minecraft:potted_"):
        plant = name.split("potted_", 1)[1]
        state = pot_states().get(_plants().get(f"minecraft:{plant}", ""))
        if state:
            return place(state, 0, f"{name} -> {state}", rule="hyvanilla")
        return place(FLOWER_POT, 0, f"{name} -> {FLOWER_POT}, plante {plant} non conservée (pas en pot HyVanilla)",
                     rule="hyvanilla")
    return None
