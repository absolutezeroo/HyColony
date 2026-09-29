"""Détecteurs d'assemblages multi-blocs.

Chaque détecteur parcourt le blueprint UNE fois et « réserve » des cases :
il renvoie {position: Mapping}. Une case réservée ne passe pas par les tables.

Ordre d'application : chaises, racks, doubles portes, doubles coffres.
Les détecteurs portent sur des blocs différents, ils ne se chevauchent pas.
"""
from __future__ import annotations

from dataclasses import dataclass, field

from .blueprint import Blueprint
from .geometry import (DIR, FACING_TO_YAW, HORIZ, LOCAL_X_OFFSET, Pos,
                       add, yaw_for)
from .model import Mapping, place, skip

RACK = "minecolonies:blockminecoloniesrack"

# Validé en jeu (calibration puis retest, 4 orientations) : le modèle Furniture_Crude_Chest_Large
# est tourné de 180° par rapport à la convention des doubles portes. À la
# rotation r, il s'étend vers -LOCAL_X_OFFSET[r] et sa face regarde à l'opposé
# du yaw r. Les détecteurs raisonnent avec la convention « porte » (ancre,
# 2e case en +X local, face = yaw), puis on ajoute 2 à la rotation émise.
LARGE_CHEST_YAW_OFFSET = 2


def large_chest(yaw: int, note: str, rule: str) -> Mapping:
    """Grand coffre dont la 2e case est en LOCAL_X_OFFSET[yaw] et la face vers yaw."""
    return place("Furniture_Crude_Chest_Large", (yaw + LARGE_CHEST_YAW_OFFSET) % 4, note, rule=rule)


def is_wall_sign(name: str) -> bool:
    return name.startswith("minecraft:") and name.endswith("_wall_sign")


def is_normal_door(name: str) -> bool:
    return name.startswith("minecraft:") and name.endswith("_door") and not name.endswith("_trapdoor")


@dataclass
class Detection:
    claims: dict[Pos, Mapping] = field(default_factory=dict)
    stats: dict[str, int] = field(default_factory=dict)
    details: dict[str, list] = field(default_factory=dict)


# ---------------------------------------------------------------------------
# Chaise Minecraft : escalier droit + un panneau mural de chaque côté
# ---------------------------------------------------------------------------

_LATERAL = {"north": ("west", "east"), "south": ("west", "east"),
            "east": ("north", "south"), "west": ("north", "south")}


def detect_chairs(bp: Blueprint, out: Detection) -> None:
    chairs = 0
    for pos, e in bp.grid.items():
        name, p = e.get("Name", ""), e.get("Properties", {}) or {}
        if not (name.endswith("_stairs") or name == "domum_ornamentum:vanilla_stairs_compat"):
            continue
        if p.get("half", "bottom") != "bottom" or p.get("shape", "straight") != "straight":
            continue
        f = p.get("facing")
        if f not in _LATERAL:
            continue
        signs = []
        for side in _LATERAL[f]:
            q = add(pos, DIR[side])
            s = bp.get(q)
            if not s or not is_wall_sign(s.get("Name", "")) or (s.get("Properties") or {}).get("facing") != side:
                break
            signs.append(q)
        else:
            # Calibré en jeu : la chaise garde le yaw de l'escalier source.
            out.claims[pos] = place("Furniture_Village_Chair", yaw_for(p),
                                    f"escalier + 2 panneaux -> chaise native (facing={f})", rule="chair")
            for q in signs:
                out.claims[q] = skip("accoudoir de chaise (panneau) retiré", rule="chair")
            chairs += 1
    out.stats["chaises"] = chairs


# ---------------------------------------------------------------------------
# Racks MineColonies -> coffres fonctionnels
# ---------------------------------------------------------------------------

def _is_openish(name: str) -> bool:
    if not name or name == "minecraft:air" or name.startswith("structurize:blocksubstitution"):
        return True
    return name.endswith("_carpet") or name in ("minecraft:dirt_path", "minecraft:grass_block", "minecraft:grass")


_SOLID_HINTS = ("wall", "fence", "bars", "brick", "planks", "log", "timber", "stone")


def _front_score(bp: Blueprint, cells, direction: Pos) -> int:
    """Plus le côté est dégagé (allée), plus le score est haut."""
    score = 0
    for c in cells:
        for dy in (0, 1):
            name = bp.name_at((c[0] + direction[0], c[1] + dy, c[2] + direction[2])) or ""
            if _is_openish(name):
                score += 3
            elif any(k in name for k in _SOLID_HINTS):
                score -= 3
            else:
                score -= 1
    return score


# Rack double MineColonies : une case principale + une case `blockrackair`
# voisine qui n'est que sa 2e moitié.
DOUBLE_RACK_MAIN = {"blockrackempty", "blockrackfull"}


def _large_chest_for_pair(bp: Blueprint, a: Pos, b: Pos):
    """Ancre, partenaire, yaw et face d'un grand coffre posé sur 2 cases
    voisines. Le grand coffre s'étend en +X local : on choisit ancre et yaw
    pour que sa 2e case tombe sur la 2e case source, face tournée vers
    l'allée (le côté le plus dégagé)."""
    a, b = sorted((a, b))
    if b[0] != a[0]:  # paire alignée sur X
        n_s, s_s = _front_score(bp, (a, b), (0, 0, -1)), _front_score(bp, (a, b), (0, 0, 1))
        anchor, partner, rot, front = (b, a, 2, "south") if s_s > n_s else (a, b, 0, "north")
        return anchor, partner, rot, front, {"north": n_s, "south": s_s}
    w_s, e_s = _front_score(bp, (a, b), (-1, 0, 0)), _front_score(bp, (a, b), (1, 0, 0))
    anchor, partner, rot, front = (a, b, 3, "east") if e_s > w_s else (b, a, 1, "west")
    return anchor, partner, rot, front, {"west": w_s, "east": e_s}


def detect_racks(bp: Blueprint, out: Detection) -> None:
    racks: dict[Pos, dict] = {}
    air: set[Pos] = set()
    for pos, e in bp.grid.items():
        if e.get("Name") != RACK:
            continue
        if (e.get("Properties") or {}).get("variant") == "blockrackair":
            air.add(pos)
            out.claims[pos] = skip("case de remplissage de rack", rule="rack")
        else:
            racks[pos] = e

    # 1) Racks doubles natifs MineColonies -> grand coffre sur les 2 cases.
    doubles = []
    for pos in sorted(racks):
        if (racks[pos].get("Properties") or {}).get("variant") not in DOUBLE_RACK_MAIN:
            continue
        half = next((q for q in (add(pos, d) for d in HORIZ) if q in air), None)
        if half is None:
            continue
        air.discard(half)
        anchor, partner, rot, front, scores = _large_chest_for_pair(bp, pos, half)
        out.claims[anchor] = large_chest(rot, f"rack double MineColonies -> grand coffre natif (36 cases), "
                                              f"face {front}", "rack_double")
        out.claims[partner] = skip("2e moitié du rack double, absorbée par le grand coffre", rule="rack_double")
        doubles.append({"cases": [list(pos), list(half)], "face": front,
                        "rotation": (rot + LARGE_CHEST_YAW_OFFSET) % 4, "scores": scores})
    for d in doubles:
        racks.pop(tuple(d["cases"][0]))
    out.stats["racks_doubles_grand_coffre"] = len(doubles)
    out.details["detail_racks_doubles"] = doubles

    # 2) Racks simples : deux racks voisins forment un grand coffre.

    def small(p: Pos) -> None:
        out.claims[p] = place("Furniture_Crude_Chest_Small", yaw_for(racks[p].get("Properties") or {}),
                              "rack isolé -> petit coffre natif (18 cases)", rule="rack")

    large = smalls = 0
    diagnostics = []
    seen: set[Pos] = set()
    for start in sorted(racks):
        if start in seen:
            continue
        stack, comp = [start], []
        seen.add(start)
        while stack:
            p = stack.pop()
            comp.append(p)
            for d in HORIZ:
                q = add(p, d)
                if q in racks and q not in seen:
                    seen.add(q)
                    stack.append(q)
        comp.sort()
        if len(comp) != 2:
            for p in comp:
                small(p)
            smalls += len(comp)
            continue
        anchor, partner, rot, front, scores = _large_chest_for_pair(bp, *comp)
        out.claims[anchor] = large_chest(rot, f"paire de racks -> grand coffre natif (36 cases), face {front}", "rack")
        out.claims[partner] = skip("2e rack absorbé par le grand coffre", rule="rack")
        diagnostics.append({"cases": [list(p) for p in comp], "face": front,
                            "rotation": (rot + LARGE_CHEST_YAW_OFFSET) % 4, "scores": scores})
        large += 1
    out.stats["racks_grand_coffre"] = large
    out.stats["racks_petit_coffre"] = smalls
    out.details["detail_racks"] = diagnostics


# ---------------------------------------------------------------------------
# Doubles portes : Hytale ne les détecte que si le partenaire est tourné de 180°
# ---------------------------------------------------------------------------

def detect_double_doors(bp: Blueprint, out: Detection) -> None:
    doors = {pos: e for pos, e in bp.grid.items()
             if is_normal_door(e.get("Name", "")) and (e.get("Properties") or {}).get("half") == "lower"}
    used: set[Pos] = set()
    pairs = []
    for pos in sorted(doors):
        if pos in used:
            continue
        p = doors[pos].get("Properties") or {}
        facing, hinge = p.get("facing"), p.get("hinge")
        if facing not in FACING_TO_YAW or hinge not in ("left", "right"):
            continue
        yaw = yaw_for(p)
        candidates = []
        for d in HORIZ:
            q = add(pos, d)
            e2 = doors.get(q)
            if not e2:
                continue
            p2 = e2.get("Properties") or {}
            if p2.get("facing") != facing or p2.get("hinge") == hinge or p2.get("hinge") not in ("left", "right"):
                continue
            # Le partenaire doit être sur l'axe latéral de la porte.
            if facing in ("east", "west") and d[2] == 0:
                continue
            if facing in ("north", "south") and d[0] == 0:
                continue
            candidates.append(q)
        if not candidates:
            continue
        partner = sorted(candidates)[0]
        if partner in used:
            continue
        first = second = None
        for y in (yaw, (yaw + 2) % 4):
            off = LOCAL_X_OFFSET[y]
            if add(pos, off) == partner:
                first, second, yaw = pos, partner, y
                break
            if add(partner, off) == pos:
                first, second, yaw = partner, pos, y
                break
        if first is None:
            continue
        note = "double porte -> rotation Hytale, partenaire tourné de 180°"
        out.claims[first] = place("Furniture_Village_Door", yaw, note, rule="double_door")
        out.claims[second] = place("Furniture_Village_Door", (yaw + 2) % 4, note, rule="double_door")
        pairs.append({"premiere": list(first), "seconde": list(second), "facing": facing,
                      "rotations": [yaw, (yaw + 2) % 4]})
        used.update((first, second))
    out.stats["doubles_portes"] = len(pairs)
    out.details["detail_doubles_portes"] = pairs


# ---------------------------------------------------------------------------
# Doubles coffres Minecraft (type=left/right) -> un grand coffre
# ---------------------------------------------------------------------------

def detect_double_chests(bp: Blueprint, out: Detection) -> None:
    chests = {pos: e for pos, e in bp.grid.items() if e.get("Name") == "minecraft:chest"}
    used: set[Pos] = set()
    count = 0
    for pos in sorted(chests):
        if pos in used:
            continue
        p = chests[pos].get("Properties") or {}
        ctype, facing = p.get("type", "single"), p.get("facing")
        if ctype not in ("left", "right") or facing not in FACING_TO_YAW:
            continue
        partner = None
        for d in HORIZ:
            q = add(pos, d)
            e2 = chests.get(q)
            if e2 and (e2.get("Properties") or {}).get("facing") == facing \
                    and {ctype, (e2.get("Properties") or {}).get("type")} == {"left", "right"}:
                partner = q
                break
        if partner is None or partner in used:
            continue
        yaw = yaw_for(p)
        off = LOCAL_X_OFFSET[yaw]
        if add(pos, off) == partner:
            anchor, other = pos, partner
        elif add(partner, off) == pos:
            anchor, other = partner, pos
        else:
            # La géométrie fait foi : on prend le yaw qui colle aux cases réelles.
            found = None
            for r, o in LOCAL_X_OFFSET.items():
                if add(pos, o) == partner:
                    found = (pos, partner, r)
                    break
                if add(partner, o) == pos:
                    found = (partner, pos, r)
                    break
            if not found:
                continue
            anchor, other, yaw = found
        out.claims[anchor] = large_chest(yaw, "double coffre -> grand coffre natif", "double_chest")
        out.claims[other] = skip("2e moitié du double coffre", rule="double_chest")
        used.update((anchor, other))
        count += 1
    out.stats["doubles_coffres"] = count


DETECTORS = [detect_chairs, detect_racks, detect_double_doors, detect_double_chests]


def run_detectors(bp: Blueprint) -> Detection:
    out = Detection()
    for detector in DETECTORS:
        detector(bp, out)
    return out
