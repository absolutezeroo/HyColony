"""Lecture (et écriture, pour les tests) du format NBT de Minecraft.

Aucune dépendance externe : les .blueprint de Structurize sont du NBT
compressé en gzip avec un compound racine.
"""
from __future__ import annotations

import gzip
import struct
from pathlib import Path

TAG_END, TAG_BYTE, TAG_SHORT, TAG_INT, TAG_LONG = 0, 1, 2, 3, 4
TAG_FLOAT, TAG_DOUBLE, TAG_BYTE_ARRAY, TAG_STRING = 5, 6, 7, 8
TAG_LIST, TAG_COMPOUND, TAG_INT_ARRAY, TAG_LONG_ARRAY = 9, 10, 11, 12


class NBTError(ValueError):
    pass


class _Reader:
    def __init__(self, data: bytes):
        self.data = data
        self.pos = 0

    def read(self, n: int) -> bytes:
        out = self.data[self.pos:self.pos + n]
        if len(out) != n:
            raise NBTError(f"fin de fichier inattendue à l'octet {self.pos}")
        self.pos += n
        return out

    def unpack(self, fmt: str, size: int):
        return struct.unpack(fmt, self.read(size))[0]

    def string(self) -> str:
        n = self.unpack(">H", 2)
        return self.read(n).decode("utf-8", "replace")

    def payload(self, tag: int):
        if tag == TAG_BYTE:
            return self.unpack(">b", 1)
        if tag == TAG_SHORT:
            return self.unpack(">h", 2)
        if tag == TAG_INT:
            return self.unpack(">i", 4)
        if tag == TAG_LONG:
            return self.unpack(">q", 8)
        if tag == TAG_FLOAT:
            return self.unpack(">f", 4)
        if tag == TAG_DOUBLE:
            return self.unpack(">d", 8)
        if tag == TAG_BYTE_ARRAY:
            return list(self.read(self.unpack(">i", 4)))
        if tag == TAG_STRING:
            return self.string()
        if tag == TAG_LIST:
            elem = self.unpack(">B", 1)
            n = self.unpack(">i", 4)
            return [self.payload(elem) for _ in range(n)]
        if tag == TAG_COMPOUND:
            out = {}
            while True:
                t = self.unpack(">B", 1)
                if t == TAG_END:
                    return out
                name = self.string()
                out[name] = self.payload(t)
        if tag == TAG_INT_ARRAY:
            n = self.unpack(">i", 4)
            return list(struct.unpack(f">{n}i", self.read(4 * n)))
        if tag == TAG_LONG_ARRAY:
            n = self.unpack(">i", 4)
            return list(struct.unpack(f">{n}q", self.read(8 * n)))
        raise NBTError(f"type de tag NBT non géré : {tag}")


def loads(data: bytes) -> dict:
    if data[:2] == b"\x1f\x8b":
        data = gzip.decompress(data)
    r = _Reader(data)
    root = r.unpack(">B", 1)
    r.string()  # nom du compound racine, inutilisé
    if root != TAG_COMPOUND:
        raise NBTError("le fichier ne commence pas par un compound NBT")
    return r.payload(root)


def load(path: str | Path) -> dict:
    return loads(Path(path).read_bytes())


# --------------------------------------------------------------------------
# Écriture minimale, utilisée par les tests pour fabriquer des blueprints.
# Les valeurs sont des tuples (tag, valeur).
# --------------------------------------------------------------------------

def _s(text: str) -> bytes:
    b = text.encode("utf-8")
    return struct.pack(">H", len(b)) + b


def _payload(tag: int, value) -> bytes:
    if tag == TAG_BYTE:
        return struct.pack(">b", value)
    if tag == TAG_SHORT:
        return struct.pack(">h", value)
    if tag == TAG_INT:
        return struct.pack(">i", value)
    if tag == TAG_STRING:
        return _s(value)
    if tag == TAG_LIST:
        elem, items = value
        return (struct.pack(">B", elem) + struct.pack(">i", len(items))
                + b"".join(_payload(elem, v) for v in items))
    if tag == TAG_COMPOUND:
        return b"".join(struct.pack(">B", t) + _s(k) + _payload(t, v)
                        for k, (t, v) in value.items()) + b"\x00"
    if tag == TAG_INT_ARRAY:
        return struct.pack(">i", len(value)) + struct.pack(f">{len(value)}i", *value)
    raise NBTError(f"écriture du tag {tag} non gérée")


def dumps(root: dict) -> bytes:
    return gzip.compress(struct.pack(">B", TAG_COMPOUND) + _s("") + _payload(TAG_COMPOUND, root))
