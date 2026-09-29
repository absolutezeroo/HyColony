"""Résultat du mapping d'une case source."""
from __future__ import annotations

from dataclasses import dataclass, field


@dataclass
class Mapping:
    """Ce que devient une case du blueprint.

    target     : ID Hytale à poser, ou None si rien n'est posé.
    rotation   : index de rotation Hytale (0 = pas de champ dans le prefab).
    notes      : explications lisibles pour le rapport.
    rule       : nom de la règle qui a décidé (pour --trace).
    skip       : True si la case est volontairement vide (air, marqueur de
                 hutte, moitié absorbée par un bloc multi-cases, etc.).
                 Une case avec target=None et skip=False est « non mappée ».
    deco       : True pour forcer support=15 (posé par un joueur ; empêche
                 les troncs de se comporter comme des arbres).
    components : composants de bloc à écrire dans le prefab.
    """
    target: str | None
    rotation: int = 0
    notes: list[str] = field(default_factory=list)
    rule: str = ""
    skip: bool = False
    deco: bool = False
    components: dict | None = None

    @property
    def unmapped(self) -> bool:
        return self.target is None and not self.skip


def place(target: str, rotation: int = 0, note: str | None = None, *,
          rule: str = "", deco: bool = False, components: dict | None = None) -> Mapping:
    return Mapping(target, int(rotation or 0), [note] if note else [], rule,
                   False, deco, components)


def skip(note: str, *, rule: str = "") -> Mapping:
    return Mapping(None, 0, [note], rule, True)


def unmapped(note: str, *, rule: str = "unmapped") -> Mapping:
    return Mapping(None, 0, [note], rule, False)
