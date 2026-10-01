"""Blocs Domum Ornamentum -> formes du mod HyDomum ; et les clôtures, portillons et murets de Minecraft -> ceux de
HyDomum (vanilla_rule, demandé le 2026-10-01), dans le matériau de leur bloc de base, avec leur poteau seul, bout, T
et croix de Minecraft.

Les données viennent du générateur HyDomum (tools/domum), jamais écrites à la main :
- domum/.../hydomum/shapes.json : pour chaque forme, son bloc Domum source (et son `type`), le nom de son gabarit
  et les clés `textureData` de ses emplacements de matériau, dans l'ordre ;
- domum/.../hydomum/id-map.json (`ornamentTags`) : les blocs Hytale que chaque emplacement accepte ;
- data/domum-materials.csv : chaque matériau Minecraft (ou « extra » Domum) -> bloc Hytale.

Rotation : les blocs « compat » de HyDomum (escalier, dalle, clôture, muret, portillon, porte) ont les rotations et
les états des blocs vanilla (vérifié par tools/domum/check_connected.py), donc les adaptateurs de families.py
s'appliquent tels quels ; clôtures et murets prennent la forme du gabarit HyDomum (CONNECTED) ; les bardeaux
suivent les escaliers. Les colombages à motif orienté tournent en DoublePipe depuis leur modèle dessiné vers le haut,
les autres ne tournent pas ; les trappes gardent le côté de charnière de Domum (tools/domum/blocks/door.py).

Deux modes :
- gabarit (défaut) : la forme dans ses matériaux par défaut, affichable tout de suite ;
- matériaux : `<gabarit>__<matériau 1>__<matériau 2>` (domum/core/.../VariantKey.blockTypeKey). HyDomum crée ces
  variantes au démarrage si elles sont dans son variants.json : option --variantes-hydomum (register_variants).
"""
from __future__ import annotations

import csv
import json
import os
from functools import lru_cache
from pathlib import Path

from . import families as fam
from . import tables as T
from .geometry import prop_true, rot_index, yaw_for
from .model import Mapping, place, skip

REPO = Path(__file__).resolve().parents[2]
RESOURCES = REPO / "domum" / "plugin" / "src" / "main" / "resources" / "hydomum"
MATERIALS_CSV = Path(__file__).resolve().parent / "data" / "domum-materials.csv"
UPSTREAM_CSV = Path(__file__).resolve().parent / "data" / "default-block-overrides.csv"
PREFIX = "domum_ornamentum:"
KEY_SEPARATOR = "__"

# Propriété `column` des piliers Domum -> état HyDomum ; `full_pillar` garde le gabarit.
PILLAR_STATES = {"pillar_base": "Base", "pillar_column": "Middle", "pillar_capital": "Base_Inverted"}
# Colombages à motif orienté : HyDomum les dessine `facing=up` et les tourne en DoublePipe (tools/domum/blocks/
# static.py, DIRECTED_FRAMES). Pitch 90 pointe le haut du modèle vers le sud au lacet 0 (Rotation.rotateX : y -> z),
# puis chaque lacet le tourne d'un quart (sud -> est -> nord -> ouest).
DIRECTED_FRAME_ROTATIONS = {"up": 0, "down": rot_index(0, 2, 0), "south": rot_index(0, 1, 0),
                            "east": rot_index(1, 1, 0), "north": rot_index(2, 1, 0), "west": rot_index(3, 1, 0)}
DIRECTED_FRAMES = ("TimberFrame_SideFramed", "TimberFrame_UpGated", "TimberFrame_DownGated",
                   "TimberFrame_SideFramedHorizontal")
# Formes du gabarit des clôtures HyDomum (tools/domum/blocks/compat.py SHAPES, domum/core ConnectedShape) : forme ->
# (état, bras au lacet 0) ; le droit est le bloc lui-même, le poteau seul l'état Post. Chaque lacet tourne un côté
# vers le suivant de TURN, comme le coin (geometry.CORNER_YAW).
CONNECTED = (("End", {"north"}), (None, {"east", "west"}), ("Corner", {"west", "south"}),
             ("T", {"east", "west", "south"}), ("Cross", {"north", "south", "east", "west"}))
TURN = ("north", "west", "south", "east")
# Clôtures, portillons et murets de Minecraft -> forme HyDomum (demandé le 2026-10-01), et les noms possibles de leur
# bloc de base (planches de leur bois, pierre du muret).
_WOOD_BASES = ("{}_planks", "{}s", "{}")
VANILLA_SHAPES = (("_fence_gate", "FenceGate", _WOOD_BASES), ("_fence", "Fence", _WOOD_BASES),
                  ("_wall", "Wall", ("{}s", "{}")))
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


@lru_cache(maxsize=1)
def upstream_table() -> dict[str, str]:
    """Table de secours HytalesHub (data/default-block-overrides.csv) : bloc Minecraft -> bloc Hytale."""
    with UPSTREAM_CSV.open("r", encoding="utf-8-sig", newline="") as fh:
        return {r["minecraft_block"].strip(): r["hytale_block"].strip() for r in csv.DictReader(fh)
                if r.get("minecraft_block") and r.get("hytale_block")}


def template_ids() -> set[str]:
    """Les gabarits HyDomum, pour la validation des IDs émis."""
    return {s["template"] for s in shapes().values()}


def variant_ids(targets) -> set[str]:
    """Les variantes HyDomum que nomment ces cibles, au format de son variants.json (VariantKey.id :
    `<forme>|<matériau 1>|<matériau 2>`) ; les gabarits seuls et les autres blocs n'en donnent aucune."""
    shape_of = {s["template"]: s["id"] for s in shapes().values()}
    out = set()
    for target in targets:
        base = target.lstrip("*").split("_State_Definitions_")[0]
        template, _, rest = base.partition(KEY_SEPARATOR)
        if rest and template in shape_of:
            out.add("|".join([shape_of[template], *rest.split(KEY_SEPARATOR)]))
    return out


def register_variants(path: Path, ids: set[str]) -> int:
    """Ajoute à la fin du variants.json de HyDomum (créé au besoin) les variantes absentes, sans rien retirer ; HyDomum
    les crée au démarrage, avant les chunks et les prefabs (VariantStore). Renvoie le nombre d'ajouts.

    Serveur arrêté : HyDomum garde la liste en mémoire et la réécrit en entier à chaque variante créée en jeu, ce qui
    effacerait ces ajouts. Écriture atomique (.tmp puis remplacement), comme VariantStore."""
    try:
        data = json.loads(path.read_text(encoding="utf-8")) if path.exists() else {"schemaVersion": 1, "variants": []}
    except ValueError as e:
        raise ValueError(f"{path} illisible ({e}) : non modifié") from None
    if isinstance(data, list):  # l'ancien format de SavedVariants : la liste seule
        data = {"schemaVersion": 1, "variants": data}
    version = data.get("schemaVersion", 1)
    if isinstance(version, int) and version > 1:  # comme SavedVariants.isNewer
        raise ValueError(f"{path} vient d'une version plus récente de HyDomum : non modifié")
    saved = data.setdefault("variants", [])
    new = sorted(ids - {v for v in saved if isinstance(v, str)})  # une entrée étrangère est gardée telle quelle
    if new:
        saved.extend(new)
        path.parent.mkdir(parents=True, exist_ok=True)
        tmp = path.with_name(path.name + ".tmp")
        tmp.write_text(json.dumps(data, separators=(",", ":")), encoding="utf-8")
        os.replace(tmp, path)
    return len(new)


def register_from_prefabs(prefabs, variants_file: Path) -> int:
    """Enregistre dans le variants.json de HyDomum les variantes que nomment ces fichiers .prefab.json."""
    names = (b["name"] for p in prefabs for b in json.loads(Path(p).read_text(encoding="utf-8"))["blocks"])
    return register_variants(Path(variants_file), variant_ids(names))


def shape_for(name: str, p: dict) -> dict | None:
    """La forme HyDomum d'un bloc Domum et de son `type` (celui-ci d'abord, puis le bloc seul) ; None sinon."""
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


def _connected(base: str, sides: set[str], rule: str) -> Mapping:
    """La forme HyDomum d'une clôture ou d'un muret dont les bras vont vers sides (ses connexions Minecraft) : poteau
    seul, bout, droit, angle, T ou croix, au plus petit lacet."""
    if not sides:
        return place(_state(base, "Post"), 0, rule=rule)
    for state, arms in CONNECTED:
        for yaw in range(len(TURN)):
            if {TURN[(TURN.index(side) + yaw) % len(TURN)] for side in arms} == sides:
                return place(_state(base, state) if state else base, yaw, rule=rule)
    raise ValueError(f"pas de forme pour {sorted(sides)}")


def vanilla_rule(name: str, p: dict, with_materials: bool) -> Mapping | None:
    """Avec les matériaux, une clôture, un portillon ou un muret de Minecraft devient celui de HyDomum (demandé le
    2026-10-01), dans le matériau de son bloc de base quand l'emplacement l'accepte, sinon dans celui du gabarit ;
    None pour un autre bloc, sans HyDomum à côté, ou sans les matériaux (la conversion rapide garde les clôtures et
    murets Hytale, choix de l'utilisateur : le gabarit HyDomum est en bois)."""
    if not with_materials or not name.startswith("minecraft:"):
        return None
    for suffix, shape_id, bases in VANILLA_SHAPES:
        if name.endswith(suffix):
            break
    else:
        return None
    shape = next((s for s in shapes().values() if s["id"] == shape_id), None)
    if shape is None:
        return None
    base, notes = shape["template"], [f"{name} -> {shape['template']}"]
    stem = name[len("minecraft:"):-len(suffix)]
    material, note = _vanilla_material(shape, [f"minecraft:{b.format(stem)}" for b in bases])
    base = base + KEY_SEPARATOR + material if material else base
    notes.append(note)
    m = _shaped(shape_id, base, p)
    m.rule = "domum_" + (m.rule or "shape")
    m.notes = notes + m.notes
    return m


def _vanilla_material(shape: dict, candidates: list[str]) -> tuple[str | None, str]:
    """Le bloc Hytale du premier bloc de base connu parmi candidates (table des matériaux Domum, table simple, puis
    table de secours HytalesHub, comme le convertisseur pour ces blocs) que l'emplacement de shape accepte ; None et
    une note sinon."""
    tables, upstream = material_table(), upstream_table()
    accepted = slot_tags().get(shape["slots"][0], frozenset())
    for source in candidates:
        target = tables.get(source) or (T.SIMPLE[source][0] if source in T.SIMPLE else None) or upstream.get(source)
        if target:
            if target in accepted:
                return target, f"matériau {source} -> {target}"
            return None, f"{target} refusé par l'emplacement {shape['slots'][0]} : matériaux par défaut du gabarit"
    return None, f"aucun bloc de base connu parmi {candidates} : matériaux par défaut du gabarit"


def _state(base: str, state: str) -> str:
    """La clé d'un état de bloc Hytale (`*<bloc>_State_Definitions_<état>`), gabarit ou variante."""
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
    if shape_id in ("Fence", "Wall"):
        return _connected(base, fam._connections(p, wall_style=shape_id == "Wall"), shape_id.lower())
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
        # Gabarit au lacet 0 = trappe Domum facing=north posée au sol, charnière en +Z. En haut de case, le pitch 180
        # passe la charnière en -Z : un demi-tour de lacet la remet du côté de Domum, comme pour les escaliers.
        yaw = yaw_for(p)
        rotation = rot_index((yaw + 2) % 4, 2, 0) if p.get("half") == "top" else rot_index(yaw, 0, 0)
        return place(_state(base, "OpenDoorOut") if prop_true(p, "open") else base, rotation, rule="trapdoor")
    if shape_id.startswith("Pillar_"):
        state = PILLAR_STATES.get(p.get("column", ""))
        return place(_state(base, state) if state else base, 0, rule="pillar")
    if shape_id in DIRECTED_FRAMES:
        return place(base, DIRECTED_FRAME_ROTATIONS.get(p.get("facing", "up"), 0), rule="timber_frame")
    if shape_id.startswith("TimberFrame_"):
        return place(base, 0, rule="timber_frame")
    # Panneaux, cloisons de papier, poteaux : rotation HyDomum pas encore vérifiée, règles existantes.
    return None
