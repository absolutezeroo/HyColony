"""Pipeline de conversion : une seule passe, une seule écriture.

Ordre de résolution d'une case (le premier qui répond gagne) :

  1. Assemblages détectés (chaises, racks, doubles portes, doubles coffres)
  2. Cases toujours vides (air, marqueurs de hutte, détails retirés, lits…)
  3. Règles contextuelles (torche murale, bibliothèque, bloc de verre)
  4. Blocs Domum portés par HyDomum (domum.py), puis tables : FAMILY (directionnels) puis SIMPLE
  5. Motifs (panneaux, portes, trappes, couleurs, tapis et pots HyVanilla, vitres…)
  6. Table de secours HytalesHub (uniquement blocs non directionnels)
  7. Sinon : non mappé (listé dans le rapport)

Le fichier --overrides de l'utilisateur remplace la cible des étapes 2 à 7.
"""
from __future__ import annotations

import csv
from collections import Counter
from dataclasses import dataclass, field
from pathlib import Path

from . import domum
from . import families as fam
from . import tables as T
from .blueprint import Blueprint, load_blueprint
from .detectors import is_bed, is_normal_door, is_wall_sign, run_detectors
from .geometry import DIR, OPPOSITE, Pos, sub, yaw_for
from .model import Mapping, fluid, place, skip, unmapped

DATA = Path(__file__).resolve().parent / "data"
UPSTREAM_CSV = DATA / "default-block-overrides.csv"

# Propriétés qui rendent une correspondance « nom -> nom » dangereuse.
ORIENTATION_SENSITIVE = {
    "facing", "axis", "shape", "half", "north", "east", "south", "west",
    "hinge", "open", "part", "type", "face", "attachment", "hanging",
}

CHEST_CAPACITY = {"Furniture_Crude_Chest_Small": 18, "Furniture_Crude_Chest_Large": 36}


def load_mapping_csv(path: str | Path) -> dict[str, str]:
    """CSV `minecraft_block,hytale_block` (format HytalesHubConverter)."""
    out: dict[str, str] = {}
    with Path(path).open("r", encoding="utf-8-sig", newline="") as fh:
        for row in csv.DictReader(fh):
            src = (row.get("minecraft_block") or "").strip()
            dst = (row.get("hytale_block") or "").strip()
            if src and dst and not src.startswith("#"):
                out[src] = dst
    return out


# ---------------------------------------------------------------------------
# Étape 2 : cases toujours vides
# ---------------------------------------------------------------------------

def always_empty(name: str, p: dict) -> Mapping | None:
    if T.is_placeholder(name):
        return skip("air / substitution / marqueur de hutte", rule="placeholder")
    if name in T.REMOVED:
        return skip(f"retiré volontairement : {T.REMOVED[name]}", rule="removed")
    if name.startswith("minecraft:") and name.endswith("_wall_banner"):
        return skip("bannière murale retirée", rule="removed")
    # Les lits Hytale ne correspondent pas à ceux de Minecraft : on les
    # retire, tu les poses toi-même en jeu.
    if is_bed(name):
        return skip("lit retiré : à poser en jeu", rule="removed")
    if is_normal_door(name) and p.get("half") == "upper":
        return skip("moitié haute de porte : la porte Hytale occupe déjà cette case", rule="door")
    return None


# ---------------------------------------------------------------------------
# Étape 3 : règles qui regardent les voisins
# ---------------------------------------------------------------------------

def _is_solid_neighbour(bp: Blueprint, pos: Pos) -> bool:
    n = bp.name_at(pos)
    return n is not None and not T.is_placeholder(n)


def wall_torch(bp: Blueprint, pos: Pos, p: dict) -> Mapping:
    f = p.get("facing")
    support = bp.name_at(sub(pos, DIR[f])) if f in DIR else None
    if support not in T.FULL_SUPPORT_SOURCE:
        return unmapped(f"torche murale ignorée : pas de support plein derrière ({support})", rule="wall_torch")
    return place("Wood_Torch_Wall", fam.wall_outward_yaw(p), rule="wall_torch")


def bookshelf(bp: Blueprint, pos: Pos, p: dict) -> Mapping:
    """Devine le mur d'appui : voisin plein avec du vide en face."""
    best = None
    for d, off in DIR.items():
        n = bp.name_at((pos[0] + off[0], pos[1] + off[1], pos[2] + off[2]))
        oo = DIR[OPPOSITE[d]]
        on = bp.name_at((pos[0] + oo[0], pos[1] + oo[1], pos[2] + oo[2]))
        score = 4 if n in T.FULL_SUPPORT_SOURCE else (1 if n and not T.is_placeholder(n) else 0)
        if on is None or T.is_placeholder(on):
            score += 3
        cand = (score, d)
        if best is None or cand > best:
            best = cand
    score, d = best
    return place("Furniture_Village_Bookcase", yaw_for({"facing": d}),
                 f"bibliothèque -> Village Bookcase, mur déduit : {d} (score {score})", rule="bookshelf")


def full_glass(bp: Blueprint, pos: Pos, p: dict) -> Mapping:
    """Bloc de verre plein -> fenêtre fine : plan déduit des voisins."""
    ew = sum(_is_solid_neighbour(bp, (pos[0] + dx, pos[1], pos[2] + dz)) for dx, dz in ((1, 0), (-1, 0)))
    ns = sum(_is_solid_neighbour(bp, (pos[0] + dx, pos[1], pos[2] + dz)) for dx, dz in ((0, 1), (0, -1)))
    rot = 1 if ns > ew else 0
    return place("Furniture_Village_Window", rot, f"verre plein -> fenêtre fine, plan {'N-S' if rot else 'E-O'}",
                 rule="glass")


def _is_fence_like(name: str | None) -> bool:
    return bool(name) and (name.endswith("_fence") or name.endswith("_fence_gate"))


# Blocs sur lesquels un bras de barrière peut s'accrocher même si le blueprint
# n'a pas enregistré la connexion (état Minecraft pas mis à jour).
_TRUNK_SOURCES = {n for n, rule in T.FAMILY.items() if n.endswith(("_log", "_wood"))}


def fence_in_context(base: str):
    """Barrière selon ses voisins réels (règle définie d'après la croix de faîtage de builder3) :

    0. plaque de pression ou tapis dessus, sans barrière à côté : pied de table
                                                          -> poutre verticale
    1. reliée sur le côté à une barrière/un portillon, ou tendue entre deux blocs
       de part et d'autre (barreau de fenêtre)            -> barrière (famille habituelle)
    2. dans une colonne (barrière au-dessus ou en dessous) -> poutre verticale
    3. accrochée d'un seul côté (tronc, mur…), rien dessus/dessous
       (bras de croix)                                    -> poutre couchée dans cet axe
    4. sinon                                              -> poutre verticale
    Accroche = bloc plein (FULL_SUPPORT_SOURCE) avec connexion enregistrée, ou tronc
    voisin même sans connexion (le blueprint oublie parfois de l'enregistrer). Les
    escaliers, trappes, tonneaux… ne comptent pas (tables de taverne = poteau).
    """
    fence_rule = fam.fence(base)
    post = fam._beam_of(base)

    def rule(bp: Blueprint, pos: Pos, p: dict) -> Mapping:
        fence_sides, attach = [], set()
        for d, (dx, _, dz) in DIR.items():
            n = bp.name_at((pos[0] + dx, pos[1], pos[2] + dz))
            if _is_fence_like(n):
                fence_sides.append(d)
            elif n in _TRUNK_SOURCES or (fam.prop_true(p, d) and n in T.FULL_SUPPORT_SOURCE):
                # Seuls les blocs pleins accrochent : pas les escaliers d'une table
                # de taverne, les trappes, les tonneaux…
                attach.add(d)
        above = bp.name_at((pos[0], pos[1] + 1, pos[2])) or ""
        if not fence_sides and above.endswith(("_pressure_plate", "_carpet")):
            return fam.beam(post, "y", "pied de table (plaque ou tapis dessus) -> poutre verticale")
        spans = {"east", "west"} <= attach or {"north", "south"} <= attach
        if fence_sides or spans:
            return fence_rule(p)
        column = _is_fence_like(bp.name_at((pos[0], pos[1] + 1, pos[2]))) or \
            _is_fence_like(bp.name_at((pos[0], pos[1] - 1, pos[2])))
        if column:
            return fam.beam(post, "y", "barrière dans une colonne -> poutre verticale")
        if len(attach) == 1:
            d = next(iter(attach))
            axis = "x" if d in ("east", "west") else "z"
            return fam.beam(post, axis, f"bras de barrière accroché au {d} -> poutre couchée")
        if attach:
            return fam.beam(post, "y", "barrière accrochée dans deux axes -> poutre verticale")
        return fam.beam(post, "y", "poteau sans voisin sur les côtés -> poutre verticale")
    return rule


CONTEXTUAL = {
    "minecraft:wall_torch": wall_torch,
    "minecraft:bookshelf": bookshelf,
    "minecraft:oak_fence": fence_in_context("Wood_Hardwood_Fence"),
}


# ---------------------------------------------------------------------------
# Étape 5 : motifs
# ---------------------------------------------------------------------------

def pattern_rule(bp: Blueprint, pos: Pos, name: str, p: dict) -> Mapping | None:
    if is_wall_sign(name):
        return fam.wall_backed("Furniture_Village_Sign", f"{name} -> panneau Village natif")(p)
    if is_normal_door(name):
        return fam.door(name, p)
    if name.startswith("minecraft:") and name.endswith("_trapdoor"):
        return fam.trapdoor(name, p)
    clay = T.colored_clay(name)
    if clay:
        return place(clay[0], 0, clay[1], rule="clay")
    carpet = T.mod_carpet(name)
    if carpet:
        return place(carpet, 0, f"tapis -> {carpet} (mod {T.MOD_PREFIX})", rule="mod")
    if name == "minecraft:flower_pot" or name.startswith("minecraft:potted_"):
        note = f"pot de fleurs -> {T.MOD_FLOWER_POT} (mod {T.MOD_PREFIX})"
        if name != "minecraft:flower_pot":
            note += f", plante {name.split('potted_', 1)[1]} non conservée"
        return place(T.MOD_FLOWER_POT, 0, note, rule="mod")
    if name == "minecraft:glass_pane" or name.endswith("_stained_glass_pane"):
        return fam.glass_pane(p)
    if name == "minecraft:glass" or name.endswith("_stained_glass"):
        return full_glass(bp, pos, p)
    if name.endswith(T.MODDED_COLORED_SUFFIXES):
        return place("Soil_Clay_Brick", 0, "bloc coloré (mod) approximé en brique d'argile", rule="modded_colored")
    return None


# ---------------------------------------------------------------------------
# Convertisseur
# ---------------------------------------------------------------------------

@dataclass
class Options:
    use_upstream_csv: bool = True
    overrides: dict[str, str] = field(default_factory=dict)
    # Traduit les blocs spéciaux MineColonies en blocs éditeur Hytale (voir
    # editor_block()). False = comportement V32 : ces cases sont omises.
    editor_blocks: bool = True
    # Blocs Domum : True = nom complet avec les matériaux (<gabarit>__<m1>__<m2>, demande DO-3 côté HyDomum),
    # False = le gabarit HyDomum dans ses matériaux par défaut.
    domum_materials: bool = False


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


@dataclass
class Cell:
    pos: Pos
    source: dict
    mapping: Mapping


class Converter:
    def __init__(self, options: Options | None = None):
        self.options = options or Options()
        self.upstream = load_mapping_csv(UPSTREAM_CSV) if self.options.use_upstream_csv else {}

    # -- résolution d'une case ------------------------------------------------

    def resolve(self, bp: Blueprint, pos: Pos, entry: dict, claims: dict[Pos, Mapping]) -> Mapping:
        name = entry.get("Name", "")
        if pos in claims:
            m = claims[pos]
        else:
            p = entry.get("Properties") or {}
            m = self._resolve_rules(bp, pos, name, p)
            if name in self.options.overrides:
                m = self._apply_override(m, name, p)
        if self.options.editor_blocks:
            m = editor_block(bp, pos, name, m)
        return m

    def _resolve_rules(self, bp: Blueprint, pos: Pos, name: str, p: dict) -> Mapping:
        m = always_empty(name, p)
        if m:
            return m
        if name in FLUIDS:
            return fluid(FLUIDS[name], f"{name} -> fluide {FLUIDS[name]}")
        if name.startswith(domum.PREFIX):
            m = domum.rule(bp, pos, name, p, self.options.domum_materials)
            if m:
                return m
        if name in CONTEXTUAL:
            m = CONTEXTUAL[name](bp, pos, p)
        elif name in T.FAMILY:
            m = T.FAMILY[name](p)
        elif name in T.SIMPLE:
            m = T.simple_rule(name)(p)
        else:
            m = pattern_rule(bp, pos, name, p) or unmapped(f"non mappé : {name}")
        if m.unmapped:
            m = self._upstream_fallback(m, name, p)
        return m

    def _upstream_fallback(self, m: Mapping, name: str, p: dict) -> Mapping:
        target = self.upstream.get(name)
        if not target:
            return m
        sensitive = sorted(k for k in ORIENTATION_SENSITIVE if k in p)
        if sensitive:
            m.notes.append(f"secours HytalesHub {name} -> {target} refusé : bloc directionnel "
                           f"({','.join(sensitive)}), il faut un adaptateur de famille")
            m.rule = "upstream_deferred"
            return m
        if target == "Empty":
            return skip(f"secours HytalesHub : {name} -> vide", rule="upstream")
        return place(target, 0, f"secours HytalesHub : {name} -> {target}", rule="upstream")

    def _apply_override(self, m: Mapping, name: str, p: dict) -> Mapping:
        target = self.options.overrides[name]
        if target == "Empty":
            return skip(f"override utilisateur : {name} -> vide", rule="override")
        rot = m.rotation if m.target else 0
        out = place(target, rot, f"override utilisateur : {name} -> {target}", rule="override", deco=m.deco)
        if not m.target and any(k in p for k in ORIENTATION_SENSITIVE):
            out.notes.append("attention : bloc directionnel sans adaptateur, rotation 0")
        return out

    # -- conversion complète --------------------------------------------------

    def convert(self, bp: Blueprint) -> "Result":
        detection = run_detectors(bp)
        cells = [Cell(pos, e, self.resolve(bp, pos, e, detection.claims)) for pos, e in bp.grid.items()]
        return Result(bp, detection, cells)

    def convert_file(self, path: str | Path) -> "Result":
        return self.convert(load_blueprint(path))


@dataclass
class Result:
    blueprint: Blueprint
    detection: object
    cells: list[Cell]

    def prefab(self) -> dict:
        ax, ay, az = self.blueprint.anchor
        blocks = []
        for c in self.cells:
            m = c.mapping
            if m.target is None:
                continue
            b = {"x": c.pos[0] - ax, "y": c.pos[1] - ay, "z": c.pos[2] - az, "name": m.target}
            # support=15 : bloc « posé par un joueur ». Validé en jeu : empêche
            # les troncs structurels de tomber comme un arbre.
            if m.deco or c.source.get("Name", "").endswith("_log"):
                b["support"] = 15
            if m.rotation:
                b["rotation"] = int(m.rotation)
            comps = m.components
            if m.target in CHEST_CAPACITY:
                # Composant explicite : le coffre reste utilisable même si le
                # collage du prefab n'instancie pas l'entité de bloc par défaut.
                comps = {"Components": {"ItemContainerBlock": {"Capacity": CHEST_CAPACITY[m.target]}}}
            if comps:
                b["components"] = comps
            blocks.append(b)
        blocks.sort(key=lambda b: (b["x"], b["z"], b["y"]))
        prefab = {"version": 8, "blockIdVersion": 3, "anchorX": 0, "anchorY": 0, "anchorZ": 0, "blocks": blocks}
        fluids = [{"x": c.pos[0] - ax, "y": c.pos[1] - ay, "z": c.pos[2] - az, "name": c.mapping.fluid, "level": 1}
                  for c in self.cells if c.mapping.fluid]
        if fluids:
            prefab["fluids"] = sorted(fluids, key=lambda f: (f["x"], f["z"], f["y"]))
        return prefab

    def unmapped(self) -> Counter:
        return Counter(c.source.get("Name", "") for c in self.cells if c.mapping.unmapped)

    def trace(self) -> list[dict]:
        ax, ay, az = self.blueprint.anchor
        return [{
            "local": [c.pos[0] - ax, c.pos[1] - ay, c.pos[2] - az],
            "source": c.source.get("Name", ""),
            "proprietes": c.source.get("Properties") or {},
            "cible": c.mapping.target,
            "rotation": c.mapping.rotation,
            "regle": c.mapping.rule,
            "notes": c.mapping.notes,
        } for c in self.cells if not T.is_placeholder(c.source.get("Name", ""))]
