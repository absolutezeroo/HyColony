"""Adaptateurs d'orientation par famille de blocs.

Règle d'or du projet : un bloc directionnel passe TOUJOURS par l'adaptateur de
sa famille. Il n'existe pas de conversion générique `facing -> rotation`, car
chaque famille Hytale a sa propre convention (ça a causé des régressions).

Chaque fonction prend l'ID Hytale de base et les propriétés Minecraft, et
renvoie un Mapping.
"""
from __future__ import annotations

from .geometry import (CORNER_YAW, FACING_TO_YAW, prop_true, rot_index,
                       wall_outward_yaw, yaw_for)
from .model import Mapping, place, skip

CARDINALS = ("north", "east", "south", "west")


# --- Escaliers, dalles, troncs ---------------------------------------------

_STAIR_STATE = {
    "outer_left": "Corner_Left", "outer_right": "Corner_Right",
    "inner_left": "Inverted_Corner_Left", "inner_right": "Inverted_Corner_Right",
}
_STAIR_STATE_FLIP = {
    "Corner_Left": "Corner_Right", "Corner_Right": "Corner_Left",
    "Inverted_Corner_Left": "Inverted_Corner_Right",
    "Inverted_Corner_Right": "Inverted_Corner_Left",
}


def stair_rotation(p: dict) -> int:
    # Hytale applique le pitch avant le yaw : un pitch de 180° inverse l'avant
    # et l'arrière, donc half=top demande aussi +180° de yaw.
    yaw = yaw_for(p)
    if p.get("half") == "top":
        return rot_index((yaw + 2) % 4, 2, 0)
    return rot_index(yaw, 0, 0)


def stair_target(base: str, p: dict) -> str:
    # outer_* Minecraft = Corner_* Hytale ; inner_* = Inverted_Corner_*.
    # Hytale inverse gauche/droite pour les escaliers retournés.
    state = _STAIR_STATE.get(p.get("shape", "straight"))
    if state and p.get("half") == "top":
        state = _STAIR_STATE_FLIP[state]
    return f"*{base}_State_Definitions_{state}" if state else base


def stairs(base: str, note: str | None = None):
    def rule(p: dict) -> Mapping:
        return place(stair_target(base, p), stair_rotation(p), note, rule="stairs")
    return rule


def slab_rotation(p: dict) -> int:
    return rot_index(0, 2 if p.get("type") == "top" else 0, 0)


def slab(base: str, note: str | None = None):
    def rule(p: dict) -> Mapping:
        return place(base, slab_rotation(p), note, rule="slab")
    return rule


def axis_rotation(p: dict) -> int:
    """Rotation « Pipe » (troncs, chaînes) : Y par défaut, pitch 90° -> Z,
    yaw 90° + pitch 90° -> X."""
    return {"y": 0, "z": rot_index(0, 1, 0), "x": rot_index(1, 1, 0)}.get(p.get("axis", "y"), 0)


def trunk(base: str, note: str | None = None):
    """Tronc/poutre : axe conservé et marqué déco (support=15)."""
    def rule(p: dict) -> Mapping:
        return place(base, axis_rotation(p), note, rule="trunk", deco=True)
    return rule


def chain(base: str, note: str | None = None):
    """Bloc orienté selon un axe, sans le marquage déco des troncs (chaîne, foin…)."""
    def rule(p: dict) -> Mapping:
        return place(base, axis_rotation(p), note, rule="chain")
    return rule


# --- Blocs connectés : murs, barrières, barreaux -----------------------------

def _connections(p: dict, *, wall_style: bool) -> set[str]:
    if wall_style:  # murs 1.16+ : none / low / tall
        return {d for d in CARDINALS
                if str(p.get(d, "none")).lower() not in ("none", "false", "0", "")}
    return {d for d in CARDINALS if prop_true(p, d)}


def _connected(straight: str, corner: str, c: set[str], *, t_uses_main_axis: bool, post: str | None = None):
    """Hytale fournit Droit (E-O au yaw 0), Coin (O+S au yaw 0) et, pour les
    barrières et les murs, une poutre (`*_Beam`) pour un poteau sans aucune
    connexion. Renvoie (cible, rotation, note)."""
    n = len(c)
    if n == 0 and post:
        return post, 0, "poteau isolé -> poutre"
    if c and c <= {"east", "west"}:
        return straight, 0, "bout isolé approximé par un droit E-O" if n == 1 else None
    if c and c <= {"north", "south"}:
        return straight, 1, "bout isolé approximé par un droit N-S" if n == 1 else None
    if n == 2:
        return corner, CORNER_YAW[frozenset(c)], None
    if n == 0:
        return straight, 0, "élément isolé approximé par un droit"
    if t_uses_main_axis and {"north", "south"} <= c and not {"east", "west"} <= c:
        return straight, 1, "jonction en T approximée sur l'axe N-S"
    return straight, 0, "jonction en T/croix approximée sur l'axe E-O"


def _beam_of(base: str) -> str:
    """Wood_Hardwood_Fence -> Wood_Hardwood_Beam, Rock_Stone_Brick_Wall -> Rock_Stone_Brick_Beam."""
    for suffix in ("_Fence", "_Wall"):
        if base.endswith(suffix):
            return base[: -len(suffix)] + "_Beam"
    raise ValueError(f"pas de poutre connue pour {base}")


def wall(base: str, note: str | None = None, beam: str | None = None):
    post = beam or _beam_of(base)

    def rule(p: dict) -> Mapping:
        t, r, n = _connected(base, f"*{base}_State_Definitions_Corner",
                             _connections(p, wall_style=True), t_uses_main_axis=True, post=post)
        m = place(t, r, note, rule="wall")
        if n:
            m.notes.append(n)
        return m
    return rule


def fence(base: str, note: str | None = None, beam: str | None = None):
    post = beam or _beam_of(base)

    def rule(p: dict) -> Mapping:
        t, r, n = _connected(base, f"*{base}_State_Definitions_Corner",
                             _connections(p, wall_style=False), t_uses_main_axis=False, post=post)
        m = place(t, r, note, rule="fence")
        if n:
            m.notes.append(n)
        return m
    return rule


def beam(post: str, axis: str, note: str) -> Mapping:
    """Poutre : verticale (axe y) ou couchée (x / z), rotation « Pipe » des troncs."""
    return place(post, axis_rotation({"axis": axis}), note, rule="fence")


def iron_bars(p: dict) -> Mapping:
    t, r, n = _connected("Deco_Iron_Bars", "Deco_Iron_Bars_Corner",
                         _connections(p, wall_style=False), t_uses_main_axis=True)
    return place(t, r, n or "barreaux -> famille native Deco_Iron_Bars", rule="iron_bars")


# --- Vitres ----------------------------------------------------------------

def glass_pane(p: dict) -> Mapping:
    """Furniture_Village_Window (VariantRotation=Wall) : 0 = plan E-O, 1 = plan N-S."""
    c = {d for d in CARDINALS if prop_true(p, d)}
    ew, ns = {"east", "west"} <= c, {"north", "south"} <= c
    if ew and not ns:
        r, n = 0, None
    elif ns and not ew:
        r, n = 1, None
    elif ew and ns:
        r, n = 0, "vitre en croix approximée E-O"
    elif c and c <= {"east", "west"}:
        r, n = 0, "vitre à un bras approximée E-O"
    elif c and c <= {"north", "south"}:
        r, n = 1, "vitre à un bras approximée N-S"
    else:
        r, n = 0, "axe de vitre ambigu, approximé E-O"
    m = place("Furniture_Village_Window", r, "vitre -> fenêtre Village native", rule="glass_pane")
    if n:
        m.notes.append(n)
    return m


# --- Rails -----------------------------------------------------------------

_RAIL_CORNERS = {
    "south_west": ("*Rail_State_Definitions_Corner_Left", 0),
    "south_east": ("*Rail_State_Definitions_Corner_Right", 0),
    "north_east": ("*Rail_State_Definitions_Corner_Left", 2),
    "north_west": ("*Rail_State_Definitions_Corner_Right", 2),
}


def rail(p: dict) -> Mapping:
    shape = p.get("shape", "north_south")
    if shape == "north_south":
        return place("Rail", 0, rule="rail")
    if shape == "east_west":
        return place("Rail", 1, rule="rail")
    if shape.startswith("ascending_"):
        return place("*Rail_State_Definitions_Slope",
                     FACING_TO_YAW.get(shape[len("ascending_"):], 0), rule="rail")
    if shape in _RAIL_CORNERS:
        t, r = _RAIL_CORNERS[shape]
        return place(t, r, rule="rail")
    return place("Rail", 0, f"forme de rail {shape} approximée en droit", rule="rail")


# --- Portes, trappes, portillons -------------------------------------------

def trapdoor(name: str, p: dict) -> Mapping:
    # Le modèle OpenDoorOut suit la convention « adossé au mur ».
    rot = wall_outward_yaw(p)
    note = f"{name} -> trappe Village native"
    if prop_true(p, "open"):
        m = place("*Furniture_Village_Trapdoor_State_Definitions_OpenDoorOut", rot, note, rule="trapdoor")
        if p.get("half") == "top":
            m.notes.append("hauteur de charnière haute non représentable")
        return m
    m = place("Furniture_Village_Trapdoor", rot, note, rule="trapdoor")
    if p.get("half") == "top":
        m.notes.append("trappe fermée en haut approximée par une trappe fermée native")
    return m


def door(name: str, p: dict) -> Mapping:
    if p.get("half") == "upper":
        return skip("moitié haute de porte : la porte Hytale occupe déjà cette case", rule="door")
    return place("Furniture_Village_Door", yaw_for(p), f"{name} -> porte Village native", rule="door")


def fence_gate(p: dict) -> Mapping:
    target = ("*Wood_Hardwood_Fence_Gate_State_Definitions_OpenDoorOut"
              if prop_true(p, "open") else "Wood_Hardwood_Fence_Gate")
    return place(target, yaw_for(p), rule="fence_gate")


# --- Divers directionnels ---------------------------------------------------

def facing(target: str, note: str | None = None):
    """Modèle qui regarde dans la même direction que la source."""
    def rule(p: dict) -> Mapping:
        return place(target, yaw_for(p), note, rule="facing")
    return rule


# Les coffres Crude (petit et grand) regardent à l'opposé de leur lacet : calibré en jeu sur le grand coffre
# (detectors.LARGE_CHEST_YAW_OFFSET), même modèle pour le petit (couvercle et charnière du même côté).
CHEST_YAW_OFFSET = 2


def chest(target: str, note: str | None = None):
    """Coffre Crude qui regarde comme la source (facing Minecraft = face avant)."""
    def rule(p: dict) -> Mapping:
        return place(target, (yaw_for(p) + CHEST_YAW_OFFSET) % 4, note, rule="chest")
    return rule


def front_south(target: str, note: str | None = None):
    """Modèle dont la face avant est sa face « front » de blockymodel, +Z (sud) à la rotation 0 (citrouille sculptée) :
    il regarde comme le facing Minecraft avec un demi-tour."""
    def rule(p: dict) -> Mapping:
        return place(target, (yaw_for(p) + 2) % 4, note, rule="front_south")
    return rule


def wall_backed(target: str, note: str | None = None):
    """Modèle adossé à un mur, facing Minecraft = côté visible."""
    def rule(p: dict) -> Mapping:
        return place(target, wall_outward_yaw(p), note, rule="wall_backed")
    return rule


def lantern(p: dict) -> Mapping:
    # La lanterne suspendue DOIT utiliser la variante plafond, sinon le
    # placement/support est invalide.
    if prop_true(p, "hanging"):
        return place("Deco_Lantern_Ceiling", 0, "lanterne suspendue -> lanterne de plafond", rule="lantern")
    return place("Deco_Lantern", 0, "lanterne posée -> lanterne au sol", rule="lantern")


def domum_panel(p: dict) -> Mapping:
    # Grille à barreaux horizontaux au-dessus des cheminées -> barreaux fins.
    rot = 0 if p.get("facing", "north") in ("north", "south") else 1
    return place("Deco_Iron_Bars", rot, "panneau Domum à barreaux -> barreaux fins natifs", rule="domum_panel")
