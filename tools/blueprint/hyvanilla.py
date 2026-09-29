"""Tapis et pots de fleurs Minecraft -> blocs du mod HyVanilla (vanilla/plugin).

Les identifiants viennent du mod lui-même, jamais écrits à la main :
- vanilla/.../Server/Item/Items/HyVanilla/*.json : les blocs du mod ;
- vanilla/.../hyvanilla/id-map.json (`flowerPots`) : pour chaque pot, l'objet plante -> l'état du pot garni.

Couleurs : Hytale a 20 laines (11 teintes, 9 en `_Light`) et pas les 16 de Minecraft
(docs/research/carpets-flower-pots.md § 1) ; les couleurs absentes prennent la plus proche par le nom.
Le pot de Minecraft est en terre cuite : le pot HyVanilla en argile lisse orange (« marron », tools/vanilla/flower_pots.py).
"""
from __future__ import annotations

import json
from functools import lru_cache
from pathlib import Path

from .model import Mapping, place

REPO = Path(__file__).resolve().parents[2]
RESOURCES = REPO / "vanilla" / "plugin" / "src" / "main" / "resources"
UPSTREAM_CSV = Path(__file__).resolve().parent / "data" / "default-block-overrides.csv"

# Couleur Minecraft -> suffixe de HyVanilla_Carpet_<suffixe>.
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


def rule(name: str) -> Mapping | None:
    """Le bloc HyVanilla d'un tapis ou d'un pot de fleurs Minecraft ; None pour les autres blocs."""
    if name.startswith("minecraft:") and name.endswith("_carpet"):
        color = name[len("minecraft:"):-len("_carpet")]
        if color not in CARPET_COLORS:
            return None
        target = f"HyVanilla_Carpet_{CARPET_COLORS[color]}"
        note = f"tapis {color} -> {target}" + (" (couleur la plus proche)" if color in APPROXIMATED else "")
        return place(target, 0, note, rule="hyvanilla")
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
