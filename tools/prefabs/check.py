"""Checks of add_bench.py: python tools/prefabs/check.py (no assets needed)."""

import unittest

from add_bench import bench_cell, with_bench


def block(x, y, z, name="Rock_Stone"):
    return {"x": x, "y": y, "z": z, "name": name}


class BenchCellTest(unittest.TestCase):
    def test_first_free_floor_cell_of_the_first_ring(self):
        floor = [block(x, 0, z) for x in range(-2, 3) for z in range(-2, 3)]
        self.assertEqual((-1, 1, -1), bench_cell(floor, (0, 1, 0)))

    def test_cell_without_floor_or_with_a_block_is_skipped(self):
        floor = [block(1, 0, 1), block(0, 0, 1), block(0, 1, 1)]
        self.assertEqual((1, 1, 1), bench_cell(floor, (0, 1, 0)))

    def test_empty_blocks_do_not_count_as_occupied(self):
        floor = [block(1, 0, 1), block(1, 1, 1, "Empty")]
        self.assertEqual((1, 1, 1), bench_cell(floor, (0, 1, 0)))

    def test_bench_carries_its_tier(self):
        prefab = {"blocks": [block(1, 0, 1)]}
        out = with_bench(prefab, (0, 1, 0), 3)
        bench = out["blocks"][-1]
        self.assertEqual("Bench_Farming", bench["name"])
        self.assertEqual(3, bench["components"]["Components"]["BenchBlock"]["TierLevel"])
        self.assertEqual(1, len(prefab["blocks"]), "the source prefab is left untouched")

    def test_no_room_raises(self):
        with self.assertRaises(ValueError):
            with_bench({"blocks": []}, (0, 1, 0), 1)


if __name__ == "__main__":
    unittest.main()
