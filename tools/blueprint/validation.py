"""Vérifie que chaque ID Hytale émis existe vraiment.

Sans ça, un ID invalide (Soil_Clay_Grey2, Soil_Clay_Yellow2…) ne se voit
qu'une fois en jeu.

Sources des IDs, fusionnées :
- data/hytale-block-ids.txt   : liste de HytalesHubConverter (janvier 2026).
                                Elle peut être en retard sur ta version du jeu.
- data/extra-block-ids.txt    : IDs validés en jeu mais absents de cette liste.
- --ids FICHIER               : ta propre liste (remplace la liste HytalesHub).
"""
from __future__ import annotations

from collections import Counter
from pathlib import Path

DATA = Path(__file__).resolve().parent / "data"


def _read_ids(path: Path) -> set[str]:
    out = set()
    for line in path.read_text(encoding="utf-8-sig").splitlines():
        line = line.strip()
        if line and not line.startswith("#"):
            out.add(line)
    return out


def load_known_ids(custom: str | Path | None = None) -> tuple[set[str], str]:
    if custom:
        ids, src = _read_ids(Path(custom)), str(custom)
    else:
        ids, src = _read_ids(DATA / "hytale-block-ids.txt"), "liste HytalesHubConverter (janv. 2026)"
    extra = DATA / "extra-block-ids.txt"
    if extra.exists():
        ids |= _read_ids(extra)
    return ids, src


def base_id(target: str) -> str:
    """`*Wood_Hardwood_Stairs_State_Definitions_Corner_Left` -> `Wood_Hardwood_Stairs`."""
    return target.lstrip("*").split("_State_Definitions_", 1)[0]


def unknown_targets(targets: Counter, known: set[str]) -> dict[str, int]:
    return {t: n for t, n in sorted(targets.items()) if base_id(t) not in known}
