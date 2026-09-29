"""Conventions d'axes et de rotations, validées en jeu.

Index de yaw Hytale : 0 = -Z (nord), 1 = -X (ouest), 2 = +Z (sud), 3 = +X (est).
Minecraft utilise les mêmes axes du monde.
Index de rotation Hytale : roll*16 + pitch*4 + yaw.
"""
from __future__ import annotations

Pos = tuple[int, int, int]

FACING_TO_YAW = {"north": 0, "west": 1, "south": 2, "east": 3}

DIR: dict[str, Pos] = {
    "north": (0, 0, -1), "south": (0, 0, 1),
    "east": (1, 0, 0), "west": (-1, 0, 0),
}
OPPOSITE = {"north": "south", "south": "north", "east": "west", "west": "east"}
HORIZ: list[Pos] = [(1, 0, 0), (-1, 0, 0), (0, 0, 1), (0, 0, -1)]

# Axe +X local d'un modèle Hytale après application du yaw. Sert aux doubles
# portes (DoorInteraction.getDoubleDoor) et aux grands coffres.
LOCAL_X_OFFSET: dict[int, Pos] = {
    0: (1, 0, 0),    # yaw nord  -> +X = est
    1: (0, 0, -1),   # yaw ouest -> +X = nord
    2: (-1, 0, 0),   # yaw sud   -> +X = ouest
    3: (0, 0, 1),    # yaw est   -> +X = sud
}

# Coins des blocs connectés (murs, barrières, barreaux) : le coin canonique
# Hytale relie Ouest+Sud, on le tourne vers la paire source.
CORNER_YAW = {
    frozenset(("west", "south")): 0,
    frozenset(("south", "east")): 1,
    frozenset(("east", "north")): 2,
    frozenset(("north", "west")): 3,
}


def add(a: Pos, b: Pos) -> Pos:
    return (a[0] + b[0], a[1] + b[1], a[2] + b[2])


def sub(a: Pos, b: Pos) -> Pos:
    return (a[0] - b[0], a[1] - b[1], a[2] - b[2])


def rot_index(yaw: int = 0, pitch: int = 0, roll: int = 0) -> int:
    return roll * 16 + pitch * 4 + yaw


def yaw_for(props: dict) -> int:
    """Yaw direct : le modèle Hytale regarde dans la même direction."""
    return FACING_TO_YAW.get(props.get("facing", "north"), 0)


def wall_outward_yaw(props: dict) -> int:
    """Modèles adossés à un mur (panneau, échelle, trappe, étagère…).

    Leur support canonique est au nord, donc leur face visible est au sud
    au yaw 0 : on décale le facing Minecraft de 180°.
    """
    return (yaw_for(props) + 2) % 4


def prop_true(props: dict, key: str) -> bool:
    return str(props.get(key, "false")).lower() == "true"
