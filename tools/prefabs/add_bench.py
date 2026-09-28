"""Test plans for the farmer hut (SP3b-2): each style's courier-hut prefab of a level, with a Farmingbench of that
level (BenchBlock.TierLevel) on a free floor cell next to the hut block. The farmer's real plans come later; these let
the hut learn its seed recipes (the Farmingbench) and be tested.

Run from the repository root: python tools/prefabs/add_bench.py. It rewrites the generated prefabs under
plugin/src/main/resources/Server/Prefabs/HyColony/Farmer/ and the hycolony:farmer entries of each style's styles.json.
"""

import copy
import json
import os
import zipfile

ASSETS = os.path.expandvars(r"%APPDATA%\Hytale\install\release\package\game\latest\Assets.zip")
OUT = "plugin/src/main/resources/Server/Prefabs/HyColony/Farmer"
STYLES = {"outlander": "plugin/src/subplugins/Styles_Outlander/hycolony/styles.json",
          "kweebec": "plugin/src/subplugins/Styles_Kweebec/hycolony/styles.json"}
SOURCE_HUT = "hycolony:deliveryman"
FARMER = "hycolony:farmer"
BENCH = "Bench_Farming"
MAX_RING = 6
# The hut's own height first, then one below and one above: some huts stand on a step of their house.
HEIGHTS = (0, -1, 1)


def bench_cell(blocks, hut):
    """The first free cell (itself and the one above empty, solid below) on rings 1..MAX_RING around the hut block,
    at the hut's height or one off (HEIGHTS), in a fixed order; None if there is none."""
    occupied = {(b["x"], b["y"], b["z"]) for b in blocks if b.get("name") not in (None, "Empty")}
    hx, hy, hz = hut
    for ring in range(1, MAX_RING + 1):
        cells = [(hx + dx, hy + dy, hz + dz) for dy in HEIGHTS
                 for dx, dz in sorted({(dx, dz) for dx in range(-ring, ring + 1) for dz in range(-ring, ring + 1)
                                       if max(abs(dx), abs(dz)) == ring})]
        for x, y, z in cells:
            if (x, y, z) not in occupied and (x, y + 1, z) not in occupied and (x, y - 1, z) in occupied:
                return x, y, z
    return None


def with_bench(prefab, hut, tier):
    """A copy of the prefab with the bench at tier {@code tier} added; raises if no cell fits."""
    cell = bench_cell(prefab["blocks"], hut)
    if cell is None:
        raise ValueError(f"no free floor cell near the hut {hut}")
    out = copy.deepcopy(prefab)
    x, y, z = cell
    out["blocks"].append({"x": x, "y": y, "z": z, "name": BENCH,
                          "components": {"Components": {"BenchBlock": {"TierLevel": tier, "UpgradeItems": []}}}})
    return out


def to_json(value, indent=0):
    """styles.json's own layout: objects one key per line, arrays of numbers on one line."""
    pad = "  " * indent
    if isinstance(value, dict):
        items = [f'{pad}  {json.dumps(k)}: {to_json(v, indent + 1)}' for k, v in value.items()]
        return "{\n" + ",\n".join(items) + "\n" + pad + "}"
    if isinstance(value, list):
        return "[" + ", ".join(json.dumps(v) for v in value) + "]"
    return json.dumps(value)


def main():
    zf = zipfile.ZipFile(ASSETS)
    os.makedirs(OUT, exist_ok=True)
    for style, styles_path in STYLES.items():
        with open(styles_path, encoding="utf-8") as f:
            styles = json.load(f)
        levels = styles[style][SOURCE_HUT]
        farmer = {}
        for level, entry in sorted(levels.items()):
            prefab = json.loads(zf.read("Server/Prefabs/" + entry["prefab"]))
            name = f"{style.capitalize()}_L{level}.prefab.json"
            with open(os.path.join(OUT, name), "w", encoding="utf-8", newline="\n") as f:
                json.dump(with_bench(prefab, tuple(entry["hutOffset"]), int(level)), f)
                f.write("\n")
            farmer[level] = {**entry, "prefab": "HyColony/Farmer/" + name}
        styles[style][FARMER] = farmer
        with open(styles_path, "w", encoding="utf-8", newline="\n") as f:
            f.write(to_json(styles) + "\n")
        print(style, "->", len(farmer), "levels")


if __name__ == "__main__":
    main()
