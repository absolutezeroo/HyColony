"""Chargement d'un .blueprint Structurize (MineColonies)."""
from __future__ import annotations

from dataclasses import dataclass, field
from pathlib import Path

from . import nbt
from .geometry import Pos
from .tables import is_placeholder


@dataclass
class Blueprint:
    path: Path
    size: tuple[int, int, int]
    grid: dict[Pos, dict]          # position locale -> état de bloc {Name, Properties}
    tile_entities: list[dict]
    anchor: Pos
    anchor_method: str
    warnings: list[str] = field(default_factory=list)
    _te_by_pos: dict[Pos, dict] | None = field(default=None, repr=False)

    @property
    def name(self) -> str:
        return self.path.name

    def get(self, pos: Pos) -> dict | None:
        return self.grid.get(pos)

    def name_at(self, pos: Pos) -> str | None:
        e = self.grid.get(pos)
        return e.get("Name") if e else None

    def tile_entity_at(self, pos: Pos) -> dict | None:
        """L'entité de bloc de la case (matériaux Domum, contenu de coffre…), ou None."""
        if self._te_by_pos is None:
            self._te_by_pos = {_te_pos(te): te for te in self.tile_entities
                               if isinstance(te, dict) and all(k in te for k in "xyz")}
        return self._te_by_pos.get(pos)


def _unpack_indices(ints: list[int], volume: int) -> list[int]:
    """Structurize range deux index de palette 16 bits par entier 32 bits."""
    out: list[int] = []
    for signed in ints:
        u = signed & 0xFFFFFFFF
        out.append((u >> 16) & 0xFFFF)
        out.append(u & 0xFFFF)
    return out[:volume]


def _te_pos(te: dict) -> Pos:
    return (int(te["x"]), int(te["y"]), int(te["z"]))


def _primary_offset(root: dict) -> Pos | None:
    """Offset principal stocké par Structurize dans optional_data.

    Vérifié sur de vrais blueprints (mcversion 3465) : compound {x,y,z} en
    minuscules. On accepte aussi {X,Y,Z} et un tableau [x,y,z] par sécurité.
    """
    opt = root.get("optional_data")
    if not isinstance(opt, dict):
        return None
    for sub in opt.values():
        if not isinstance(sub, dict) or "primary_offset" not in sub:
            continue
        po = sub["primary_offset"]
        if isinstance(po, dict):
            # Vu dans les vrais fichiers : {x,y,z} en minuscules ; on accepte aussi {X,Y,Z}.
            for keys in (("x", "y", "z"), ("X", "Y", "Z")):
                if all(k in po for k in keys):
                    return (int(po[keys[0]]), int(po[keys[1]]), int(po[keys[2]]))
        if isinstance(po, list) and len(po) == 3:
            return (int(po[0]), int(po[1]), int(po[2]))
    return None


def _find_anchor(root: dict, grid: dict[Pos, dict]) -> tuple[Pos, str]:
    tes = [te for te in root.get("tile_entities", []) or [] if isinstance(te, dict)]

    # 0) L'ancre de Structurize, celle que MineColonies utilise (la caserne, pas sa première tour).
    po = _primary_offset(root)
    if po is not None:
        return po, "optional_data primary_offset (Structurize)"
    # 1) Entrepôt : tile entity dédiée, puis bloc de hutte.
    for te in tes:
        if te.get("id") == "minecolonies:warehouse":
            return _te_pos(te), "tile entity minecolonies:warehouse"
    for pos, e in grid.items():
        if e.get("Name") == "minecolonies:blockhutwarehouse":
            return pos, "bloc minecolonies:blockhutwarehouse"
    # 2) Cas général des huttes.
    for te in tes:
        if te.get("id") == "minecolonies:colonybuilding":
            return _te_pos(te), "tile entity minecolonies:colonybuilding"
    # 3) N'importe quel bloc de hutte.
    for pos, e in grid.items():
        if is_placeholder(e.get("Name", "")) and e.get("Name", "").startswith("minecolonies:blockhut"):
            return pos, f"bloc {e['Name']}"
    return (0, 0, 0), "aucune (0,0,0)"


def anchor_warnings(grid: dict[Pos, dict], anchor: Pos) -> list[str]:
    """L'avertissement d'un plan qui a une hutte ailleurs qu'à son ancre ; vide sinon."""
    def hut(e: dict) -> bool:
        return e.get("Name", "").startswith("minecolonies:blockhut")

    if hut(grid.get(anchor, {})) or not any(hut(e) for e in grid.values()):
        return []
    return ["L'ancre n'est pas un bloc de hutte : dans styles.json, donner le hutOffset de la hutte, sinon HyColony "
            "met la hutte à l'ancre."]


def load_blueprint(path: str | Path) -> Blueprint:
    path = Path(path)
    root = nbt.load(path)
    try:
        sx, sy, sz = int(root["size_x"]), int(root["size_y"]), int(root["size_z"])
        palette = root["palette"]
        blocks = root["blocks"]
    except KeyError as exc:
        raise ValueError(f"{path.name} : ce n'est pas un blueprint Structurize (champ {exc} absent)") from None

    indices = _unpack_indices(blocks, sx * sy * sz)
    grid: dict[Pos, dict] = {}
    # Ordre Structurize : x varie le plus vite, puis z, puis y.
    for idx, pal in enumerate(indices):
        x = idx % sx
        t = idx // sx
        grid[(x, t // sz, t % sz)] = palette[pal]

    anchor, method = _find_anchor(root, grid)
    bp = Blueprint(path, (sx, sy, sz), grid, root.get("tile_entities", []) or [], anchor, method)
    bp.warnings.extend(anchor_warnings(grid, anchor))
    if method.startswith("aucune"):
        bp.warnings.append(
            "Aucune ancre de hutte trouvée : l'origine du prefab est le coin (0,0,0) du blueprint. "
            "Le bâtiment risque d'être décalé.")
    return bp
