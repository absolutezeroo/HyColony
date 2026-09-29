"""Vérifie que chaque ID Hytale émis existe vraiment.

Sans ça, un ID invalide (Soil_Clay_Grey2, Soil_Clay_Yellow2…) ne se voit
qu'une fois en jeu.

Sources des IDs, fusionnées :
- les assets du jeu épinglé (gradle.properties : patchline, hytale_version), lus dans
  ~/.gradle/caches/hytale-assets/<patchline>-<version>-Assets.zip, que le build télécharge ;
- à défaut, data/hytale-block-ids.txt : liste de HytalesHubConverter (janvier 2026), en retard
  sur le jeu (elle connaissait Hay_Bale et Ore_Cobalt_Stone, absents de 0.7.0-pre.4).
- data/extra-block-ids.txt    : IDs validés en jeu mais absents de cette liste.
- --ids FICHIER               : ta propre liste (remplace les assets épinglés et la liste HytalesHub).
- les blocs du mod HyVanilla (hyvanilla.item_ids, lus dans ses assets).
- les gabarits du mod HyDomum et les matériaux qu'il accepte (générés depuis les assets du jeu épinglé).
"""
from __future__ import annotations

import json
import os
import zipfile
from collections import Counter
from functools import lru_cache
from pathlib import Path, PurePosixPath

DATA = Path(__file__).resolve().parent / "data"
REPO = Path(__file__).resolve().parents[2]


def _read_ids(path: Path) -> set[str]:
    out = set()
    for line in path.read_text(encoding="utf-8-sig").splitlines():
        line = line.strip()
        if line and not line.startswith("#"):
            out.add(line)
    return out


def pinned_assets_zip() -> Path | None:
    """Le zip d'assets de la version épinglée dans gradle.properties ; None s'il n'est pas téléchargé."""
    props = {}
    path = REPO / "gradle.properties"
    if not path.exists():
        return None
    for line in path.read_text(encoding="utf-8").splitlines():
        key, sep, value = line.partition("=")
        if sep and not key.strip().startswith("#"):
            props[key.strip()] = value.strip()
    if "hytale_version" not in props or "patchline" not in props:
        return None
    gradle_home = Path(os.environ.get("GRADLE_USER_HOME") or Path.home() / ".gradle")
    zip_path = gradle_home / "caches" / "hytale-assets" / f"{props['patchline']}-{props['hytale_version']}-Assets.zip"
    return zip_path if zip_path.exists() else None


@lru_cache(maxsize=1)
def _asset_ids(zip_path: Path) -> frozenset[str]:
    """Les blocs du jeu : les objets de Server/Item/Items qui ont leur propre clé `BlockType`, et le bloc Empty.
    Item.processConfig ne crée un bloc que pour eux ; un enfant sans cette clé pose le bloc de son parent, et un objet
    seul (Ore_Cobalt, le minerai ramassé) n'en pose aucun."""
    ids = {"Empty"}
    with zipfile.ZipFile(zip_path) as z:
        for n in z.namelist():
            if n.startswith("Server/Item/Items/") and n.endswith(".json"):
                try:
                    if "BlockType" in json.loads(z.read(n).decode("utf-8-sig")):
                        ids.add(PurePosixPath(n).stem)
                except (ValueError, UnicodeDecodeError):
                    continue
    return frozenset(ids)


def _mod_block_ids() -> set[str]:
    """Les blocs du mod HyColony (huttes, champ, blocs de dev des plans), lus dans ses assets ; pas ses outils."""
    folder = REPO / "plugin" / "src" / "main" / "resources" / "Server" / "Item" / "Items" / "HyColony"
    if not folder.is_dir():
        return set()
    return {p.stem for p in folder.glob("*.json") if "BlockType" in json.loads(p.read_text(encoding="utf-8-sig"))}


def load_known_ids(custom: str | Path | None = None) -> tuple[set[str], str]:
    pinned = pinned_assets_zip()
    if custom:
        ids, src = _read_ids(Path(custom)), str(custom)
    elif pinned:
        ids, src = set(_asset_ids(pinned)), f"assets du jeu épinglé ({pinned.name})"
    else:
        ids, src = _read_ids(DATA / "hytale-block-ids.txt"), "liste HytalesHubConverter (janv. 2026)"
    extra = DATA / "extra-block-ids.txt"
    if extra.exists():
        ids |= _read_ids(extra)
    from . import domum  # noqa: PLC0415 (validation reste utilisable sans le mod HyDomum)
    ids |= domum.template_ids()
    from . import hyvanilla  # noqa: PLC0415
    ids |= hyvanilla.item_ids()
    ids |= _mod_block_ids()
    ids |= set().union(*domum.slot_tags().values()) if domum.slot_tags() else set()  # matériaux vérifiés par HyDomum
    return ids, src


def base_id(target: str) -> str:
    """`*Wood_Hardwood_Stairs_State_Definitions_Corner_Left` -> `Wood_Hardwood_Stairs` ; un bloc HyDomum dans
    ses matériaux (`HyDomum_Stairs__Rock_Stone`) -> son gabarit."""
    return target.lstrip("*").split("_State_Definitions_", 1)[0].split("__", 1)[0]


def unknown_targets(targets: Counter, known: set[str]) -> dict[str, int]:
    return {t: n for t, n in sorted(targets.items()) if base_id(t) not in known}
