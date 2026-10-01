"""The farmer's Farmingbench (SP3b-2): MineColonies' farmer crafts at its hut, Hytale crafts at a bench, so each
converted Medieval Oak farmer plan of level L gets a Farmingbench of tier L (BenchBlock.TierLevel) on a free floor
cell next to the hut block, which stands at the prefab's anchor. Without it the hut learns no seed recipe.

Run from the repository root after converting the plans (tools/blueprint): python tools/prefabs/add_bench.py. It
rewrites the farmer prefabs in place; a rerun leaves the bench where it is.
"""

import copy
import json

PREFAB = ("plugin/src/subplugins/Styles_MedievalOak/Server/Prefabs/MedievalOak/agriculture/horticulture/"
          "farmer{}.prefab.json")
LEVELS = range(1, 6)
HUT = (0, 0, 0)
BENCH = "Bench_Farming"
MAX_RING = 6
# The hut's own height first, then one below and one above: some huts stand on a step of their house.
HEIGHTS = (0, -1, 1)
# Bench_Farming's hitbox (Server/Item/Block/Hitboxes/Bench/Bench_Farming.json) spans X -1..1 and Y 0..2, unrotated.
FOOTPRINT = ((0, 0, 0), (-1, 0, 0), (0, 1, 0), (-1, 1, 0))
FLOOR = ((0, -1, 0), (-1, -1, 0))


def _cells(cell, offsets):
    x, y, z = cell
    return [(x + dx, y + dy, z + dz) for dx, dy, dz in offsets]


def bench_cell(blocks, hut):
    """The first cell whose bench footprint (FOOTPRINT) is empty and stands on solid blocks (FLOOR), on rings
    1..MAX_RING around the hut block, at the hut's height or one off (HEIGHTS), in a fixed order; None if none fits."""
    occupied = {(b["x"], b["y"], b["z"]) for b in blocks if b.get("name") not in (None, "Empty")}
    hx, hy, hz = hut
    for ring in range(1, MAX_RING + 1):
        cells = [(hx + dx, hy + dy, hz + dz) for dy in HEIGHTS
                 for dx, dz in sorted({(dx, dz) for dx in range(-ring, ring + 1) for dz in range(-ring, ring + 1)
                                       if max(abs(dx), abs(dz)) == ring})]
        for cell in cells:
            if (not occupied.intersection(_cells(cell, FOOTPRINT))
                    and occupied.issuperset(_cells(cell, FLOOR))):
                return cell
    return None


def with_bench(prefab, hut, tier):
    """A copy of the prefab with the bench at tier {@code tier} added, in place of the Empty entries of its footprint;
    raises if no cell fits."""
    out = copy.deepcopy(prefab)
    # A rerun replaces the bench it added instead of placing a second one.
    out["blocks"] = [b for b in out["blocks"] if b.get("name") != BENCH]
    cell = bench_cell(out["blocks"], hut)
    if cell is None:
        raise ValueError(f"no free floor cell near the hut {hut}")
    x, y, z = cell
    # Explicit Empty entries may stand on the footprint: Hytale refuses two blocks at one position, and air would
    # clear a part of the bench.
    footprint = set(_cells(cell, FOOTPRINT))
    out["blocks"] = [b for b in out["blocks"] if (b["x"], b["y"], b["z"]) not in footprint]
    out["blocks"].append({"x": x, "y": y, "z": z, "name": BENCH,
                          "components": {"Components": {"BenchBlock": {"TierLevel": tier, "UpgradeItems": []}}}})
    return out


def main():
    for level in LEVELS:
        path = PREFAB.format(level)
        with open(path, encoding="utf-8") as f:
            prefab = json.load(f)
        with open(path, "w", encoding="utf-8", newline="\n") as f:
            f.write(json.dumps(with_bench(prefab, HUT, level), indent=2, ensure_ascii=False))
        print(path, "-> tier", level)


if __name__ == "__main__":
    main()
