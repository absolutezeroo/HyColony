"""Blocs Domum Ornamentum -> formes du mod HyDomum.

Les données viennent du générateur HyDomum (tools/domum), jamais écrites à la main :
- domum/.../hydomum/shapes.json : pour chaque forme, son bloc Domum source (et son `type`), le nom de son gabarit
  et les clés `textureData` de ses emplacements de matériau, dans l'ordre ;
- domum/.../hydomum/id-map.json (`ornamentTags`) : les blocs Hytale que chaque emplacement accepte ;
- data/domum-materials.csv : chaque matériau Minecraft (ou « extra » Domum) -> bloc Hytale.

Rotation : les blocs « compat » de HyDomum (escalier, dalle, clôture, muret, portillon, porte, trappe) ont les
rotations et les états des blocs vanilla (vérifié par tools/domum/check_connected.py), donc les adaptateurs de
families.py s'appliquent tels quels ; les bardeaux suivent les escaliers ; les colombages ne tournent pas.

Deux modes :
- gabarit (défaut) : la forme dans ses matériaux par défaut, affichable tout de suite ;
- matériaux : `<gabarit>__<matériau 1>__<matériau 2>` (domum/core/.../VariantKey.blockTypeKey). HyDomum doit
  savoir créer ce matériau au chargement du prefab (tâche DO-3) : d'ici là, un tel bloc s'affiche inconnu.
"""
from __future__ import annotations

import csv
import json
from functools import lru_cache
from pathlib import Path

from . import families as fam
from .geometry import prop_true, wall_outward_yaw, yaw_for
from .model import Mapping, place, skip

REPO = Path(__file__).resolve().parents[2]
RESOURCES = REPO / "domum" / "plugin" / "src" / "main" / "resources" / "hydomum"
MATERIALS_CSV = Path(__file__).resolve().parent / "data" / "domum-materials.csv"
PREFIX = "domum_ornamentum:"
KEY_SEPARATOR = "__"

# Propriété `column` des piliers Domum -> état HyDomum ; `full_pillar` garde le gabarit.
PILLAR_STATES = {"pillar_base": "Base", "pillar_column": "Middle", "pillar_capital": "Base_Inverted"}
# Propriété `shape` des demi-bardeaux -> état HyDomum ; `top` garde le gabarit.
SHINGLE_SLAB_STATES = {"one_way": "One_Way", "two_way": "Two_Way", "three_way": "Three_Way",
                       "four_way": "Four_Way", "curved": "Curved"}


@lru_cache(maxsize=1)
def shapes() -> dict[tuple[str, str | None], dict]:
    """(bloc Domum, type) -> forme HyDomum ; vide si le mod HyDomum n'est pas à côté."""
    path = RESOURCES / "shapes.json"
    if not path.exists():
        return {}
    entries = json.loads(path.read_text(encoding="utf-8"))["shapes"]
    return {(s["source"]["block"], s["source"]["type"]): s for s in entries if "source" in s}


@lru_cache(maxsize=1)
def slot_tags() -> dict[str, frozenset[str]]:
    """Tag d'emplacement -> blocs Hytale acceptés."""
    path = RESOURCES / "id-map.json"
    if not path.exists():
        return {}
    tags = json.loads(path.read_text(encoding="utf-8")).get("ornamentTags", {})
    return {tag: frozenset(ids) for tag, ids in tags.items()}


@lru_cache(maxsize=1)
def material_table() -> dict[str, str]:
    """Matériau Minecraft -> bloc Hytale ; une cible vide = pas d'équivalent (matériau par défaut)."""
    with MATERIALS_CSV.open("r", encoding="utf-8-sig", newline="") as fh:
        return {r["minecraft_material"].strip(): (r["hytale_material"] or "").strip() for r in csv.DictReader(fh)}


def template_ids() -> set[str]:
    """Les gabarits HyDomum, pour la validation des IDs émis."""
    return {s["template"] for s in shapes().values()}


def shape_for(name: str, p: dict) -> dict | None:
    table = shapes()
    return table.get((name, p.get("type"))) or table.get((name, None))


def materials(shape: dict, texture_data: dict | None) -> tuple[list[str] | None, list[str]]:
    """Les blocs Hytale des emplacements, dans l'ordre, ou None s'il en manque un ; et les notes.

    Une clé de composant inconnue (ancienne version de Domum : `acacia_planks` au lieu de `oak_planks`) prend,
    dans l'ordre, les entrées restantes de textureData."""
    data = dict(texture_data or {})
    comps = shape["components"]
    rest = [v for k, v in data.items() if k not in comps]
    table, tags = material_table(), slot_tags()
    out: list[str] = []
    notes: list[str] = []
    for comp, tag in zip(comps, shape["slots"]):
        source = data.get(comp) or (rest.pop(0) if rest else None)
        if source is None:
            notes.append(f"matériau {comp} absent de l'entité de bloc")
            return None, notes
        target = table.get(str(source))
        if not target:
            notes.append(f"matériau {source} sans équivalent (data/domum-materials.csv)")
            return None, notes
        if target not in tags.get(tag, frozenset()):
            notes.append(f"{target} refusé par l'emplacement {tag}")
            return None, notes
        out.append(target)
    return out, notes


def rule(bp, pos, name: str, p: dict, with_materials: bool) -> Mapping | None:
    """La case d'un bloc Domum porté par HyDomum ; None pour les autres (règles existantes)."""
    shape = shape_for(name, p)
    if shape is None:
        target = material_table().get(name)
        # Blocs Domum posés tels quels (briques, pavés « extra ») : leur bloc Hytale de la table des matériaux.
        return place(target, 0, f"{name} -> {target}", rule="domum_block") if target else None
    base = shape["template"]
    notes = []
    if with_materials:
        mats, notes = materials(shape, (bp.tile_entity_at(pos) or {}).get("textureData"))
        if mats:
            base = base + KEY_SEPARATOR + KEY_SEPARATOR.join(mats)
        else:
            notes.append("matériaux par défaut du gabarit")
    m = _shaped(shape["id"], base, p)
    if m is None:
        return None
    m.rule = "domum_" + (m.rule or "shape")
    m.notes = [f"{name} -> {shape['template']}"] + m.notes + notes
    return m


def _state(base: str, state: str) -> str:
    return f"*{base}_State_Definitions_{state}"


def _shaped(shape_id: str, base: str, p: dict) -> Mapping | None:
    """La cible et la rotation d'une forme ; None pour celles qu'on laisse aux règles existantes."""
    if shape_id == "Stairs" or (shape_id.startswith("Shingle") and not shape_id.startswith("ShingleSlab")):
        return fam.stairs(base)(p)
    if shape_id == "ShingleSlab":
        state = SHINGLE_SLAB_STATES.get(p.get("shape", ""))
        return place(_state(base, state) if state else base, yaw_for(p), rule="shingle_slab")
    if shape_id == "Slab":
        if p.get("type") == "double":
            return place(_state(base, "Block"), 0, rule="slab")
        return fam.slab(base)(p)
    if shape_id == "Fence":
        return fam.fence(base, beam=base)(p)
    if shape_id == "Wall":
        return fam.wall(base, beam=base)(p)
    if shape_id == "FenceGate":
        return place(_state(base, "OpenDoorOut") if prop_true(p, "open") else base, yaw_for(p), rule="fence_gate")
    if shape_id.startswith(("Door_", "FancyDoor_")):
        if p.get("half") == "upper":
            return skip("moitié haute de porte : la porte Hytale occupe déjà cette case", rule="door")
        m = place(base, yaw_for(p), rule="door")
        if prop_true(p, "open"):
            m.notes.append("porte ouverte posée fermée")
        return m
    if shape_id.startswith(("Trapdoor_", "FancyTrapdoor_")):
        # Même convention que la trappe vanilla (families.trapdoor) : le générateur a tourné le modèle DO de 180°.
        m = place(_state(base, "OpenDoorOut") if prop_true(p, "open") else base, wall_outward_yaw(p), rule="trapdoor")
        if p.get("half") == "top":
            m.notes.append("trappe en haut de case approximée")
        return m
    if shape_id.startswith("Pillar_"):
        state = PILLAR_STATES.get(p.get("column", ""))
        return place(_state(base, state) if state else base, 0, rule="pillar")
    if shape_id.startswith("TimberFrame_"):
        return place(base, 0, rule="timber_frame")
    # Panneaux, cloisons de papier, poteaux : rotation HyDomum pas encore vérifiée, règles existantes.
    return None
