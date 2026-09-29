"""Plateforme d'atelier : un sol plat avec un emplacement par blueprint.

Disposition : une rangée par type de hutte, une colonne par niveau.
- Le sol des emplacements est en herbe : c'est le terrain que MineColonies
  garde (blocs de substitution).
- Les allées sont en pierre lisse.
- Un bloc rouge marque, dans le sol, la verticale de l'ancre de chaque hutte.
- Des blocs jaunes dans l'allée, devant chaque emplacement, indiquent son
  niveau (1 à 5).
- Les cases que le blueprint veut vides au niveau du sol (descentes de
  cave…) sont laissées ouvertes.

Niveau du sol d'une hutte : on prend le tag Structurize `groundlevel` s'il
existe, sinon la plus haute couche majoritairement composée de
substitution (le terrain à garder). Tous les niveaux d'une hutte partagent
la même ancre, on garde donc le niveau du sol le plus haut trouvé parmi eux.
"""
from __future__ import annotations

import json
import re
from dataclasses import dataclass
from pathlib import Path

from . import nbt
from .blueprint import Blueprint, load_blueprint

FLOOR = "Rock_Stone_Brick_Smooth"
GROUND = "Soil_Grass"
ANCHOR_MARK = "Soil_Clay_Smooth_Red"
LEVEL_MARK = "Soil_Clay_Smooth_Yellow"

GAP = 6        # largeur des allées
MARGIN = 6     # bordure autour de la plateforme
SUBST_RATIO = 0.6

TYPE_ORDER = ["townhall", "warehouse", "builder", "deliveryman", "residence", "tavern", "lumberjack", "farmer"]


def _is_substitution(name: str) -> bool:
    return name.startswith("structurize:blocksubstitution") or name.startswith("structurize:blocksolidsubstitution")


def ground_from_tag(bp: Blueprint) -> int | None:
    root = nbt.load(bp.path)
    for te in root.get("tile_entities", []) or []:
        prov = te.get("blueprintDataProvider") if isinstance(te, dict) else None
        if not isinstance(prov, dict):
            continue
        for tag in prov.get("posTagMap", []) or []:
            if any(n.get("tagName") == "groundlevel" for n in tag.get("tagNameList", [])):
                return int(te["y"]) + int(tag["tagPos"]["y"])
    return None


def ground_from_substitution(bp: Blueprint) -> int | None:
    sx, _, sz = bp.size
    area = sx * sz
    counts: dict[int, int] = {}
    for (x, y, z), e in bp.grid.items():
        if y <= bp.anchor[1] and _is_substitution(e.get("Name", "")):
            counts[y] = counts.get(y, 0) + 1
    levels = [y for y, n in counts.items() if n / area >= SUBST_RATIO]
    return max(levels) if levels else None


def hut_type(bp: Blueprint) -> tuple[str, int]:
    m = re.match(r"^(.*?)(\d+)$", bp.path.stem.split(".")[0])
    return (m.group(1), int(m.group(2))) if m else (bp.path.stem, 1)


@dataclass
class Slot:
    bp: Blueprint
    kind: str
    level: int
    ground: int
    ground_source: str
    x0: int = 0
    z0: int = 0

    @property
    def paste(self) -> tuple[int, int, int]:
        """Position de l'ancre du prefab, relative à l'ancre de la plateforme."""
        ax, ay, az = self.bp.anchor
        return (self.x0 + ax, ay - self.ground, self.z0 + az)


def plan_slots(paths: list[Path]) -> tuple[list[Slot], tuple[int, int]]:
    bps = [load_blueprint(p) for p in paths]
    groups: dict[str, list[Blueprint]] = {}
    for bp in bps:
        groups.setdefault(hut_type(bp)[0], []).append(bp)

    slots: list[Slot] = []
    for kind, members in groups.items():
        tag = next((g for g in (ground_from_tag(b) for b in members) if g is not None), None)
        if tag is not None:
            ground, src = tag, "tag groundlevel"
        else:
            found = [g for g in (ground_from_substitution(b) for b in members) if g is not None]
            ground, src = (max(found), "couche de substitution") if found else (members[0].anchor[1] - 1,
                                                                                 "par défaut (ancre - 1)")
        for b in members:
            slots.append(Slot(b, kind, hut_type(b)[1], ground, src))

    order = {k: i for i, k in enumerate(TYPE_ORDER)}
    kinds = sorted(groups, key=lambda k: (order.get(k, len(order)), k))
    z = MARGIN
    width = 0
    for kind in kinds:
        row = sorted((s for s in slots if s.kind == kind), key=lambda s: (s.level, s.bp.name))
        x = MARGIN
        depth = 0
        for s in row:
            s.x0, s.z0 = x, z
            x += s.bp.size[0] + GAP
            depth = max(depth, s.bp.size[2])
        width = max(width, x - GAP + MARGIN)
        z += depth + GAP
    return slots, (width, z - GAP + MARGIN)


def build_platform(slots: list[Slot], size: tuple[int, int]) -> tuple[dict, dict]:
    w, d = size
    cells: dict[tuple[int, int], str | None] = {(x, z): FLOOR for x in range(w) for z in range(d)}
    holes = 0
    for s in slots:
        sx, _, sz = s.bp.size
        for x in range(sx):
            for z in range(sz):
                cells[(s.x0 + x, s.z0 + z)] = GROUND
        # Cases que le blueprint veut vides au niveau du sol.
        for (x, y, z), e in s.bp.grid.items():
            if y == s.ground and e.get("Name") == "minecraft:air":
                cells[(s.x0 + x, s.z0 + z)] = None
                holes += 1
        px, _, pz = s.paste
        cells[(px, pz)] = ANCHOR_MARK
        for i in range(s.level):
            cells[(s.x0 + i, s.z0 - 2)] = LEVEL_MARK
    blocks = [{"x": x, "y": 0, "z": z, "name": n} for (x, z), n in sorted(cells.items()) if n]
    prefab = {"version": 8, "blockIdVersion": 3, "anchorX": 0, "anchorY": 0, "anchorZ": 0, "blocks": blocks}
    return prefab, {"trous": holes, "blocs": len(blocks)}


def assemble(slots: list[Slot], platform: dict, converter) -> tuple[dict, dict]:
    """Plateforme + tous les bâtiments posés à leur place, en un seul prefab.

    - Le bâtiment l'emporte sur la plateforme, y compris ses cases `Empty`
      (descentes de cave, terrain plus bas).
    - Là où le bâtiment ne dit rien (substitution : terrain gardé), la
      plateforme reste : elle joue le rôle du terrain.
    - Les Editor_Anchor des huttes sont retirés : un prefab ne peut avoir
      qu'une ancre, celle de la plateforme (son coin nord-ouest).
    """
    cells = {(b["x"], b["y"], b["z"]): b for b in platform["blocks"]}
    stats = {"batiments": 0, "blocs_batiments": 0, "ancres_retirees": 0}
    for s in slots:
        px, py, pz = s.paste
        for b in converter.convert(s.bp).prefab()["blocks"]:
            if b["name"] == "Editor_Anchor":
                stats["ancres_retirees"] += 1
                continue
            pos = (px + b["x"], py + b["y"], pz + b["z"])
            cells[pos] = {**b, "x": pos[0], "y": pos[1], "z": pos[2]}
            stats["blocs_batiments"] += 1
        stats["batiments"] += 1
    blocks = sorted(cells.values(), key=lambda b: (b["x"], b["z"], b["y"]))
    stats["blocs_total"] = len(blocks)
    return {**{k: v for k, v in platform.items() if k != "blocks"}, "blocks": blocks}, stats


def plan_markdown(slots: list[Slot], size, stats, pack: bool = False) -> str:
    w, d = size
    lines = [
        "# Plateforme d'atelier",
        "",
        f"Taille : **{w} × {d}** blocs, 1 bloc d'épaisseur ({stats['blocs']} blocs).",
        "",
        "L'ancre de la plateforme est son coin **nord-ouest**, au niveau du sol :",
        "la plateforme s'étend vers l'est (+X) et vers le sud (+Z).",
        "",
        "- herbe : emplacement d'un bâtiment (le terrain que MineColonies garde) ;",
        "- bloc rouge : la verticale de l'ancre du prefab ;",
        "- blocs jaunes dans l'allée : le niveau (1 à 5).",
        "",
        *(["`plateforme_complete.prefab.json` contient la plateforme avec tous les bâtiments déjà posés :",
           "colle-le comme la plateforme seule (même ancre). Les prefabs séparés sont rangés par type de hutte.",
           ""] if pack else []),
        "**Coller un prefab** : place son ancre à la position ci-dessous, relative à l'ancre de la plateforme,",
        "sans rotation. Autrement dit, colle-le à la verticale du bloc rouge, `hauteur` blocs au-dessus du sol.",
        "",
        "| Prefab | Colonne X | Hauteur Y | Ligne Z | Emprise | Sol |",
        "|---|---:|---:|---:|---|---|",
    ]
    for s in sorted(slots, key=lambda s: (s.z0, s.x0)):
        px, py, pz = s.paste
        sx, _, sz = s.bp.size
        name = f"{s.kind}/{s.bp.path.stem}" if pack else s.bp.path.stem
        lines.append(f"| {name} | {px} | +{py} | {pz} | {sx}×{sz} | {s.ground_source} |")
    lines += [
        "",
        "## Caves et fondations",
        "",
        "Tout ce que MineColonies met sous le niveau du sol (fondations, caves) se retrouve",
        "**sous la plateforme**. Pose-la en hauteur, à au moins 8 blocs du terrain : les caves",
        "restent ouvertes et modifiables. Les prefabs ne contiennent pas d'air, donc collés",
        "dans le terrain, les caves resteraient pleines de terre.",
    ]
    return "\n".join(lines) + "\n"


def plan_svg(slots: list[Slot], size) -> str:
    w, d = size
    k = 6
    out = [f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {w * k} {d * k}" '
           f'font-family="sans-serif">', f'<rect width="{w * k}" height="{d * k}" fill="#b9b9b4"/>']
    for s in slots:
        sx, _, sz = s.bp.size
        px, _, pz = s.paste
        out.append(f'<rect x="{s.x0 * k}" y="{s.z0 * k}" width="{sx * k}" height="{sz * k}" '
                   f'fill="#7fae5a" stroke="#4d6e36"/>')
        out.append(f'<rect x="{px * k}" y="{pz * k}" width="{k}" height="{k}" fill="#c0392b"/>')
        out.append(f'<text x="{s.x0 * k + 4}" y="{s.z0 * k + 14}" font-size="11" fill="#1b2a12">'
                   f'{s.bp.path.stem}</text>')
    out.append(f'<text x="6" y="{d * k - 6}" font-size="10" fill="#333">N ↑  (+X vers la droite, +Z vers le bas)</text>')
    out.append("</svg>")
    return "\n".join(out)


def generate(paths: list[Path], out_dir: Path, with_buildings: bool = False, converter=None,
             pack: bool = False) -> dict:
    """pack=True : dossier prêt pour le jeu, rangé par type de hutte :
        builder/builder1.prefab.json … warehouse/warehouse5.prefab.json
        plateforme.prefab.json, plateforme_complete.prefab.json, plan.md
    """
    slots, size = plan_slots(paths)
    prefab, stats = build_platform(slots, size)
    out_dir.mkdir(parents=True, exist_ok=True)
    (out_dir / "plateforme.prefab.json").write_text(json.dumps(prefab, separators=(",", ":")), encoding="utf-8")
    if converter is None and (with_buildings or pack):
        from .converter import Converter
        converter = Converter()
    if pack:
        with_buildings = True
        for s in slots:
            folder = out_dir / s.kind
            folder.mkdir(exist_ok=True)
            (folder / f"{s.bp.path.stem}.prefab.json").write_text(
                json.dumps(converter.convert(s.bp).prefab(), separators=(",", ":")), encoding="utf-8")
    if with_buildings:
        full, full_stats = assemble(slots, prefab, converter)
        (out_dir / "plateforme_complete.prefab.json").write_text(json.dumps(full, separators=(",", ":")),
                                                                 encoding="utf-8")
        stats["complete"] = full_stats
    (out_dir / "plan.md").write_text(plan_markdown(slots, size, stats, pack), encoding="utf-8")
    if pack:
        return {"taille": size, **stats, "emplacements": len(slots)}
    (out_dir / "plan.svg").write_text(plan_svg(slots, size), encoding="utf-8")
    positions = {s.bp.path.stem: {"ancre": list(s.paste), "emprise": [s.bp.size[0], s.bp.size[2]],
                                  "coin": [s.x0, 0, s.z0], "sol_du_blueprint": s.ground,
                                  "source_sol": s.ground_source} for s in slots}
    (out_dir / "positions.json").write_text(json.dumps(positions, indent=2, ensure_ascii=False), encoding="utf-8")
    return {"taille": size, **stats, "emplacements": len(slots)}
