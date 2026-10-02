"""Les formes du ruban de chantier (MC BlockConstructionTape) en éléments Minecraft : les trois modèles de MC
(models/block/blockconstructiontape*.json, recopiés ici depuis la branche version/main) assemblés comme son
blockstate multipart les assemble pour chaque forme.

Le multipart de MC : le nœud central toujours ; un bras (piquet au bord et corde jusqu'au centre) par côté raccordé,
le bras nord tourné de y = 90, 180, 270 pour l'est, le sud et l'ouest ; le piquet central quand les deux raccords font
un coin. Au lacet 0, un droit joint le nord et le sud, un coin le nord et l'est, un T le nord, l'est et l'ouest
(core TapeShape).
"""

import copy


def _faces(texture, uvs):
    return {side: {"texture": texture, "uv": uv} for side, uv in uvs.items()}


_ROPE_UVS = {"north": [0, 0, 2, 1], "east": [0, 0, 3, 1], "south": [0, 0, 2, 1], "west": [0, 0, 3, 1],
             "up": [0, 0, 2, 3], "down": [0, 0, 2, 3]}
# blockconstructiontape.json : le bras nord.
ARM = [
    {"name": "post", "from": [6, 0, 0], "to": [10, 16, 1], "faces": _faces("#post", {
        "north": [1, 0, 5, 16], "east": [5, 0, 6, 16], "south": [6, 0, 10, 16], "west": [10, 0, 11, 16],
        "up": [1, 0, 2, 4], "down": [1, 4, 2, 8]})},
    {"name": "chain_outer", "from": [7, 14, 1], "to": [9, 15, 4], "faces": _faces("#tape", _ROPE_UVS)},
    {"name": "chain_mid", "from": [7, 13, 4], "to": [9, 14, 7], "faces": _faces("#tape", _ROPE_UVS)},
]
# blockconstructiontape_corner.json : le piquet central d'un coin.
CORNER_POST = [
    {"name": "center_post", "from": [7, 0, 7], "to": [9, 13, 9], "faces": _faces("#post", {
        "north": [0, 0, 4, 16], "east": [0, 0, 4, 16], "south": [0, 0, 4, 16], "west": [0, 0, 4, 16],
        "up": [4, 13, 8, 14], "down": [0, 0, 4, 4]})},
]
# blockconstructiontape_center.json : le nœud central.
CENTER = [
    {"name": "center", "from": [7, 13, 7], "to": [9, 14, 9], "faces": _faces("#tape", {
        "north": [0, 0, 2, 1], "east": [0, 0, 2, 1], "south": [0, 0, 2, 1], "west": [0, 0, 2, 1],
        "up": [6, 0, 8, 2], "down": [0, 0, 2, 2]})},
]
# Les textures des modèles de MC : planches de chêne pour le piquet, laine blanche pour la corde.
TEXTURES = {"post": "block/oak_planks", "tape": "block/white_wool"}
# Forme (nom de l'état Hytale, comme les formes des vitres de HyDomum) -> côtés raccordés au lacet 0.
SHAPES = {
    "Straight": {"north", "south"},
    "Corner": {"north", "east"},
    "T_Junction": {"north", "east", "west"},
    "Cross_Junction": {"north", "east", "south", "west"},
}
# Le quart de tour du blockstate de MC qui amène le bras nord de chaque côté.
_ANGLES = {"north": 0, "east": 90, "south": 180, "west": 270}
# y = 90 : la face nord devient la face est, et ainsi de suite.
_NEXT_SIDE = {"north": "east", "east": "south", "south": "west", "west": "north", "up": "up", "down": "down"}


def turned(element, angle):
    """element tourné de angle degrés autour de l'axe vertical du bloc, comme le "y" d'un blockstate de MC (sens
    horaire vu de dessus : (x, z) -> (16 - z, x) par quart de tour) ; ses faces suivent."""
    out = copy.deepcopy(element)
    for _ in range(angle // 90 % 4):
        (x0, y0, z0), (x1, y1, z1) = out["from"], out["to"]
        out["from"], out["to"] = [16 - z1, y0, x0], [16 - z0, y1, x1]
        out["faces"] = {_NEXT_SIDE[side]: face for side, face in out["faces"].items()}
    return out


def elements(shape):
    """Les éléments Minecraft de la forme shape : le nœud, un bras par côté raccordé, le piquet d'un coin."""
    parts = copy.deepcopy(CENTER)
    for side in sorted(SHAPES[shape], key=_ANGLES.get):
        parts += [turned(e, _ANGLES[side]) for e in ARM]
    if shape == "Corner":
        parts += copy.deepcopy(CORNER_POST)
    return parts
