"""Blocs spéciaux MineColonies / Structurize (air, substitutions, ancre) et fluides -> Hytale.

Voir docs/research/structurize-placeholders.md et la spec 2026-09-29-hycolony-blueprint-placeholders-design.md.
"""
from __future__ import annotations

from .blueprint import Blueprint
from .geometry import Pos
from .model import Mapping, fluid, place, skip

# Cases retirées volontairement qui doivent rester vides dans le bâtiment
# (tapis, leviers, accoudoirs de chaise…). Les moitiés absorbées par un
# modèle multi-cases (tête de lit, haut de porte, 2e case d'un grand coffre)
# ne reçoivent PAS de vide forcé : il pourrait effacer le modèle.
_EMPTY_AFTER_SKIP = {"removed", "chair", "upstream"}

# Blocs de dev du mod HyColony (plugin/.../Server/Item/Items/HyColony).
PLACEHOLDER_SOLID = "HyColony_Placeholder_Solid"
PLACEHOLDER_FLUID = "HyColony_Placeholder_Fluid"
# Fluides Minecraft -> fluide Hytale du tableau `fluids` (niveau 1 : une source, comme les prefabs vanilla).
FLUIDS = {"minecraft:water": "Water_Source", "minecraft:lava": "Lava_Source"}


def fluid_rule(name: str, p: dict) -> Mapping | None:
    """Une source Minecraft (level 0) -> la source Hytale ; un fluide qui coule -> rien (Hytale le refait couler
    depuis les sources, une source à sa place déborderait) ; None pour un autre bloc."""
    if name not in FLUIDS:
        return None
    if str(p.get("level", "0")) != "0":
        return skip(f"{name} qui coule (level {p.get('level')}) : recréé par l'écoulement", rule="fluid_flowing")
    return fluid(FLUIDS[name], f"{name} -> fluide {FLUIDS[name]}")


def editor_block(bp: Blueprint, pos: Pos, name: str, m: Mapping) -> Mapping:
    """Équivalents Hytale des blocs spéciaux MineColonies / Structurize (docs/research/structurize-placeholders.md).

    minecraft:air, blocktagsubstitution -> Empty                      (vide forcé)
    structurize:blocksolidsubstitution  -> HyColony_Placeholder_Solid (bloc de remplissage si le sol n'est pas plein)
    structurize:blockfluidsubstitution  -> HyColony_Placeholder_Fluid (eau si ni fluide ni bloc plein)
    structurize:blocksubstitution       -> rien                       (terrain laissé intact)
    bloc de hutte à l'ancre             -> Editor_Anchor              (l'ancre survit à l'éditeur de prefabs)
    Le niveau de styles.json doit porter "minecolonies": true pour que HyColony lise ces blocs.
    """
    if name == "minecraft:air":
        return place("Empty", 0, "air du blueprint -> vide forcé", rule="editor_empty")
    if name == "structurize:blocktagsubstitution":
        # Sans bloc de remplacement (le cas des plans medievaloak), Structurize le traite comme de l'air.
        return place("Empty", 0, "substitution à étiquettes -> vide forcé", rule="editor_empty")
    if name == "structurize:blocksolidsubstitution":
        return place(PLACEHOLDER_SOLID, 0, "substitution pleine -> substitut solide", rule="editor_solid")
    if name == "structurize:blockfluidsubstitution":
        return place(PLACEHOLDER_FLUID, 0, "substitution de fluide -> substitut de fluide", rule="editor_fluid")
    if pos == bp.anchor and name.startswith("minecolonies:blockhut"):
        return place("Editor_Anchor", 0, f"{name} -> Editor Anchor", rule="editor_anchor")
    if m.skip and m.rule in _EMPTY_AFTER_SKIP:
        return place("Empty", 0, m.notes[0] if m.notes else "retiré -> vide forcé", rule="editor_empty")
    return m
