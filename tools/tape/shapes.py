"""Les formes du ruban de chantier (MC BlockConstructionTape), faites à la main dans le style des huttes : au centre du
bloc, un piquet de bois brut à pointe taillée, planté dans une motte de terre et ceint d'un tour de corde ; de lui,
une corde vers chaque côté raccordé, qui s'affaisse jusqu'au bord, où elle rejoint celle du voisin.

Écart avec MC (spec 2026-10-02 ruban de chantier) : MC met un piquet au bord de chaque bras, si bien que deux rubans
voisins posent deux piquets collés ; ici un seul piquet par bloc, au centre, et la corde pend entre deux piquets
comme chez MC. Au lacet 0, un droit joint le nord et le sud, un coin le nord et l'est, un T le nord, l'est et l'ouest
(core TapeShape).
"""

import math

from models import box_shape, multiply, node, rotate

# Forme (nom de l'état Hytale, comme les formes des vitres de HyDomum) -> côtés raccordés au lacet 0.
SHAPES = {
    "Straight": {"north", "south"},
    "Corner": {"north", "east"},
    "T_Junction": {"north", "east", "west"},
    "Cross_Junction": {"north", "east", "south", "west"},
}
# Le lacet qui amène une corde tendue vers le nord (-z) de chaque côté (+x à l'est).
YAWS = {"north": 0, "east": -90, "south": 180, "west": 90}
ALL = ("front", "back", "left", "right", "top", "bottom")
# Hauteur du tour de corde et des cordes à leur départ, inclinaison des cordes vers le bord (degrés).
ROPE_Y, SAG_DEGREES = 20, 14
# Une corde part de l'intérieur du piquet (z -1) et, inclinée, atteint le bord du bloc (z -16).
ROPE_LENGTH = 16


def parts(shape):
    """Les nœuds de la forme shape : la motte, le piquet, sa pointe, le tour de corde, une corde par côté raccordé."""
    nodes = [
        node("Mound", (0, 0.5, 0), box_shape((8, 1, 8), ("front", "back", "left", "right", "top"))),
        node("Stake", (0, 12, 0), box_shape((3, 24, 3), ("front", "back", "left", "right", "top"))),
        node("Stake_Tip", (0, 25, 0), box_shape((2, 2, 2), ("front", "back", "left", "right", "top"))),
        node("Stake_Point", (0, 26.5, 0), box_shape((1, 1, 1), ("front", "back", "left", "right", "top"))),
        node("Wrap", (0, ROPE_Y, 0), box_shape((4, 2, 4), ALL)),
    ]
    for side in sorted(SHAPES[shape], key=list(YAWS).index):
        nodes.append(rope(side))
    return nodes


def rope(side):
    """La corde tendue vers side : de l'intérieur du piquet jusqu'au bord, penchée de SAG_DEGREES vers le bas ; sa face
    de départ, dans le piquet, est retirée."""
    yaw = about((0, 1, 0), YAWS[side])
    shape = box_shape((2, 2, ROPE_LENGTH), ("back", "left", "right", "top", "bottom"), offset=(0, 0, -ROPE_LENGTH / 2))
    rope_node = node("Rope_" + side.capitalize(), rotate(yaw, (0, ROPE_Y, -1)), shape)
    rope_node["orientation"] = dict(zip("xyzw", multiply(yaw, about((1, 0, 0), -SAG_DEGREES))))
    return rope_node


def about(axis, degrees):
    """Le quaternion d'une rotation de degrees autour de axis."""
    half = math.radians(degrees) / 2
    return (*(c * math.sin(half) for c in axis), math.cos(half))
