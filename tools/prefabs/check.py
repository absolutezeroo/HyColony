"""Checks of add_bench.py: python tools/prefabs/check.py (no assets needed)."""

import unittest

from add_bench import bench_cell, with_bench


def block(x, y, z, name="Rock_Stone"):
    return {"x": x, "y": y, "z": z, "name": name}


def floor_5x5():
    return [block(x, 0, z) for x in range(-2, 3) for z in range(-2, 3)]


class BenchCellTest(unittest.TestCase):
    def test_first_free_floor_cell_of_the_first_ring(self):
        self.assertEqual((-1, 1, -1), bench_cell(floor_5x5(), (0, 1, 0)))

    def test_cell_without_floor_or_with_a_block_is_skipped(self):
        floor = [block(1, 0, 1), block(0, 0, 1), block(0, 0, -1), block(-1, 0, -1), block(0, 1, -1)]
        self.assertEqual((1, 1, 1), bench_cell(floor, (0, 1, 0)))

    def test_empty_blocks_do_not_count_as_occupied(self):
        floor = [block(1, 0, 1), block(0, 0, 1), block(1, 1, 1, "Empty"), block(0, 1, 1, "Empty")]
        self.assertEqual((1, 1, 1), bench_cell(floor, (0, 1, 0)))

    def test_the_bench_west_half_needs_a_free_cell_on_a_floor(self):
        # Bench_Farming's hitbox spans X -1..1 and Y 0..2: the cell west of it and both cells above are the bench's.
        floor = floor_5x5() + [block(-2, 1, -1, "Ingredient_Hay")]
        self.assertEqual((-1, 1, 0), bench_cell(floor, (0, 1, 0)))

    def test_bench_carries_its_tier(self):
        prefab = {"blocks": [block(1, 0, 1), block(0, 0, 1)]}
        out = with_bench(prefab, (0, 1, 0), 3)
        bench = out["blocks"][-1]
        self.assertEqual("Bench_Farming", bench["name"])
        self.assertEqual(3, bench["components"]["Components"]["BenchBlock"]["TierLevel"])
        self.assertEqual(2, len(prefab["blocks"]), "the source prefab is left untouched")

    def test_bench_replaces_the_empty_entries_of_its_cells(self):
        empties = [block(x, y, 1, "Empty") for x in (0, 1) for y in (1, 2)]
        prefab = {"blocks": [block(1, 0, 1), block(0, 0, 1)] + empties}
        out = with_bench(prefab, (0, 1, 0), 1)
        left = [b["name"] for b in out["blocks"] if b["y"] > 0]
        self.assertEqual(["Bench_Farming"], left, "an Empty entry would clear a part of the bench")

    def test_a_rerun_keeps_a_single_bench_on_the_same_cell(self):
        once = with_bench({"blocks": floor_5x5()}, (0, 1, 0), 2)
        twice = with_bench(once, (0, 1, 0), 2)
        benches = [(b["x"], b["y"], b["z"]) for b in twice["blocks"] if b["name"] == "Bench_Farming"]
        self.assertEqual([(-1, 1, -1)], benches)

    def test_no_room_raises(self):
        with self.assertRaises(ValueError):
            with_bench({"blocks": []}, (0, 1, 0), 1)


if __name__ == "__main__":
    unittest.main()
