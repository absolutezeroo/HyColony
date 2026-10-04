"""Checks of the illustration pass (spec 2026-10-04 blockpaint illustration): python
tools/blockpaint/check_illustration.py (no assets needed). The demonstration crate (check_effects), painted in
layers, illustrated one operation at a time. An AssertionError names the case."""

import contextlib
import io
import unittest

from PIL import Image

import catalog
import conditions
import illustration
import semantics
from bake import LIGHT
from check_effects import DECLARED, crate_model, crate_module
from critique import lightness
from vectors import dot


def gap(a, b):
    return sum(abs(a[k] - b[k]) for k in range(3))


def unlit(records, size):
    """The texture paint makes of records, before the light."""
    image = Image.new("RGBA", size, (0, 0, 0, 0))
    for _, _, (u, v, _, _), _, island_image in records:
        image.paste(island_image, (u, v))
    return image


def painted(**declared):
    """(records, unlit texture) of the crate declaring declared over the demonstration's."""
    nodes = crate_model()
    records = semantics.records_of(crate_module(**dict(DECLARED, **declared)), nodes)
    return records, unlit(records, catalog.texture_size(nodes))


def illustrated(records, image, steps, importance=None, focus=()):
    """(image illustrated by steps only, its report)."""
    report = []

    def level(part):
        return illustration.importance_of(part, importance or {}, focus)

    out = illustration.illustrate(image.copy(), records, illustration.Illustration(report, steps), level)
    return out, report


def cells_of(record, keep):
    """[(texture x, y, texel)] of record's texels keep(texel) holds for."""
    part, side, (u, v, _, _), island, _ = record
    return [(u + i, v + j, t) for (i, j), t in island.texels.items() if keep(t)]


def mean_light(image, cells):
    return sum(lightness(image.getpixel((x, y))) for x, y, _ in cells) / len(cells)


class BaseTest(unittest.TestCase):
    def test_an_island_keeps_its_image_before_the_effects(self):
        records, _ = painted(CONDITION=conditions.PRISTINE, HISTORY=())
        for _, _, _, island, image in records:
            self.assertEqual(image.tobytes(), island.base.tobytes(), "no effect: the base is the image")
        records, _ = painted()
        self.assertTrue(any(image.tobytes() != island.base.tobytes() for _, _, _, island, image in records))


class IllustrationTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.records, cls.image = painted()

    def test_rest_areas_lose_part_of_their_secondary_effects_and_nothing_else_moves(self):
        # Neglected and new: dust (secondary) settles in the middle of faces, with no ageing to crowd it out.
        records, image = painted(CONDITION=conditions.NEGLECTED, AGE=conditions.NEW, HISTORY=())
        out, report = illustrated(records, image, ("rest",))
        rest, moved = set(), 0
        for (_, _, (u, v, _, _), island, _) in records:
            told = {c for name, zone in island.zones.items() if name not in illustration.SECONDARY for c in zone}
            for name in illustration.SECONDARY & set(island.zones):
                for cell in island.zones[name]:
                    t, base = island.texels[cell], island.base.getpixel(cell)
                    if t.rim < illustration.REST_RIM or t.occlusion >= illustration.CONTACT_OCCLUSION or cell in told:
                        continue
                    at = (u + cell[0], v + cell[1])
                    rest.add(at)
                    gap = sum(abs(image.getpixel(at)[k] - base[k]) for k in range(3))
                    if gap > 3:
                        self.assertLess(sum(abs(out.getpixel(at)[k] - base[k]) for k in range(3)), gap, at)
                        moved += 1
        self.assertTrue(moved)
        changed = {(x, y) for x in range(image.width) for y in range(image.height)
                   if image.getpixel((x, y)) != out.getpixel((x, y))}
        self.assertLessEqual(changed, rest, "edges, contacts and the main effects keep theirs")
        self.assertTrue(any("repos" in line for line in report), report)

    def test_form_shading_lights_a_part_s_top_over_its_foot(self):
        out, report = illustrated(self.records, self.image, ("form",))
        body = next(r for r in self.records if r[:2] == ("Body", "front"))
        ys = [t.point[1] for _, _, t in cells_of(body, lambda t: True)]
        middle = (min(ys) + max(ys)) / 2
        top, foot = (cells_of(body, lambda t, s=s: (t.point[1] - middle) * s > 2) for s in (1, -1))

        def gap(image):
            return mean_light(image, top) - mean_light(image, foot)

        self.assertGreater(gap(out), gap(self.image) + 3)
        self.assertTrue(report)

    def test_each_face_is_framed_darker_by_its_border_and_more_so_away_from_the_light(self):
        out, report = illustrated(self.records, self.image, ("planes",))

        def darkening(record, keep):
            cells = cells_of(record, keep)
            return mean_light(self.image, cells) - mean_light(out, cells)

        for record in self.records:
            if min(record[2][2:]) >= 8:
                self.assertGreater(darkening(record, lambda t: t.rim == 0), 2, record[:2])
                self.assertEqual(0, darkening(record, lambda t: t.rim >= illustration.FRAME_RINGS), record[:2])

        def framed(side):
            record = next(r for r in self.records if r[:2] == ("Body", side))
            border = cells_of(record, lambda t: t.rim == 0)
            return (mean_light(self.image, border) - mean_light(out, border)) / mean_light(self.image, border)

        # The top faces the light (0.79): framed 1 - FRAME_LIT * 0.79 as much as the back, which faces away.
        self.assertGreater(framed("back"), 1.3 * framed("top"), "a face turned from the light is framed more")
        self.assertTrue(any("cadre" in line for line in report), report)

    def test_edge_highlights_are_selective_and_interrupted(self):
        out, report = illustrated(self.records, self.image, ("edges",))
        outer = [(x, y, t) for record in self.records
                 for x, y, t in cells_of(record, lambda t: t.edge is not None and t.edge[1] == 1.0)]
        lit = [(x, y) for x, y, _ in outer if lightness(out.getpixel((x, y))) > lightness(self.image.getpixel((x, y)))]
        self.assertTrue(0.05 < len(lit) / len(outer) < 0.5, len(lit) / len(outer))
        u, v, w, h = next(r for r in self.records if r[:2] == ("Lid", "top"))[2]
        borders = ([(x, v) for x in range(u, u + w)], [(x, v + h - 1) for x in range(u, u + w)],
                   [(u, y) for y in range(v, v + h)], [(u + w - 1, y) for y in range(v, v + h)])

        def turns(border):
            flags = [lightness(out.getpixel(c)) > lightness(self.image.getpixel(c)) for c in border]
            return sum(1 for a, b in zip(flags, flags[1:]) if a != b)

        self.assertGreaterEqual(max(map(turns, borders)), 2, "interrupted runs along the lid's lit border")
        self.assertTrue(any("reflet" in line for line in report), report)

    def test_junctions_darken(self):
        out, report = illustrated(self.records, self.image, ("contact",))
        corner = [c for r in self.records
                  for c in cells_of(r, lambda t: t.occlusion >= illustration.CONTACT_OCCLUSION)]
        self.assertTrue(corner)
        self.assertLess(mean_light(out, corner), mean_light(self.image, corner) - 2)
        open_cells = [c for r in self.records for c in cells_of(r, lambda t: t.occlusion < 0.1)]
        self.assertEqual(mean_light(out, open_cells), mean_light(self.image, open_cells))

    def test_the_pass_changes_no_layer(self):
        def layers_of(island):
            return (island.substrate.tobytes(), island.base.tobytes(), {k: set(v) for k, v in island.zones.items()},
                    {k: set(v) for k, v in island.signals.items()}, dict(island.depth), dict(island.relief),
                    repr(island.deposits), repr(island.coat_tints), repr(island.coats))

        snapshot = [layers_of(r[3]) for r in self.records]
        illustrated(self.records, self.image, illustration.STEPS, {"Lid": 3})
        self.assertEqual(snapshot, [layers_of(r[3]) for r in self.records])

    def test_without_an_illustration_catalog_paints_as_before_and_with_one_prints_its_report(self):
        nodes = crate_model()
        plain = catalog.model_texture(crate_module(**DECLARED), nodes, None)
        own = []
        module = crate_module(**dict(DECLARED, IMPORTANCE={"Lid": 2}, ILLUSTRATION=illustration.Illustration(own)))
        printed = io.StringIO()
        with contextlib.redirect_stdout(printed):
            drawn = catalog.model_texture(module, nodes, None)
            catalog.model_texture(module, nodes, None)
        self.assertNotEqual(plain.tobytes(), drawn.tobytes())
        lines = printed.getvalue().splitlines()
        self.assertTrue(lines and len(lines) % 2 == 0 and lines[:len(lines) // 2] == lines[len(lines) // 2:],
                        "each paint prints its own report")
        self.assertEqual([], own, "the module's list is left as it was")
        self.assertEqual(plain.tobytes(), catalog.model_texture(crate_module(**DECLARED), nodes, None).tobytes())

    def test_an_illustration_without_a_layered_condition_is_refused(self):
        module = crate_module(ILLUSTRATION=illustration.Illustration())
        with self.assertRaises(SystemExit):
            catalog.model_texture(module, crate_model(), None)

    def test_a_focus_part_is_important_and_a_named_importance_wins(self):
        self.assertEqual(1, illustration.importance_of("Body", {}, ()))
        self.assertEqual(2, illustration.importance_of("Lid", {}, ("Lid",)))
        self.assertEqual(3, illustration.importance_of("Lid", {"Lid": 3}, ("Lid",)))


class RestTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        # Neglected and new: dust (secondary) settles in the middle of faces, with no ageing to crowd it out.
        cls.records, cls.image = painted(CONDITION=conditions.NEGLECTED, AGE=conditions.NEW, HISTORY=())

    def resting(self):
        """[(record, cell, texture x, y)] of the dust in rest areas, where it changed the base."""
        found = []
        for record in self.records:
            (u, v, _, _), island = record[2], record[3]
            for cell in island.zones.get("dust", ()):
                t, at = island.texels[cell], (u + cell[0], v + cell[1])
                if t.rim >= illustration.REST_RIM and t.occlusion < illustration.CONTACT_OCCLUSION and gap(
                        self.image.getpixel(at), island.base.getpixel(cell)) > 3:
                    found.append((record, cell, at))
        return found

    def test_an_important_part_keeps_its_secondary_effects_and_a_calm_one_loses_twice_as_much(self):
        parts = {r[0] for r in self.records}
        kept, _ = illustrated(self.records, self.image, ("rest",), {p: 2 for p in parts})
        self.assertEqual(self.image.tobytes(), kept.tobytes())
        normal, _ = illustrated(self.records, self.image, ("rest",), {p: 1 for p in parts})
        calm, _ = illustrated(self.records, self.image, ("rest",), {p: 0 for p in parts})
        for record, cell, at in self.resting():
            base, before = record[3].base.getpixel(cell), self.image.getpixel(at)
            self.assertLess(gap(calm.getpixel(at), base), gap(normal.getpixel(at), base) - 1, at)
            self.assertLess(gap(normal.getpixel(at), base), gap(before, base), at)

    def test_a_neglected_model_keeps_more_of_its_secondary_effects_in_rest_areas(self):
        def kept(show):
            out = illustration.illustrate(self.image.copy(), self.records, illustration.Illustration(None, ("rest",)),
                                          lambda part: 1, show)
            return sum(gap(out.getpixel(at), record[3].base.getpixel(cell)) for record, cell, at in self.resting())

        self.assertGreater(kept(2), kept(0) * 1.3)

    def test_a_texel_another_effect_reached_keeps_every_effect(self):
        record, cell, at = self.resting()[0]
        record[3].zones.setdefault("rust", set()).add(cell)
        try:
            out, _ = illustrated(self.records, self.image, ("rest",))
            self.assertEqual(self.image.getpixel(at), out.getpixel(at))
        finally:
            record[3].zones["rust"].discard(cell)


class FormAndEdgesTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.records, cls.image = painted()

    def test_form_is_the_part_s_so_its_top_face_is_lit_as_its_top(self):
        out, _ = illustrated(self.records, self.image, ("form",))
        top = cells_of(next(r for r in self.records if r[:2] == ("Body", "top")), lambda t: True)
        self.assertGreater(mean_light(out, top), mean_light(self.image, top) + 3)

    def lit_share(self, importance, keep):
        out, _ = illustrated(self.records, self.image, ("edges",), importance)
        cells = [c for r in self.records if r[0] == "Lid" for c in cells_of(r, keep)]
        return sum(lightness(out.getpixel((x, y))) > lightness(self.image.getpixel((x, y)))
                   for x, y, _ in cells) / len(cells)

    def test_only_the_outer_ring_of_edges_turned_to_the_light_is_lit_and_more_on_an_accent(self):
        def outer(t):
            return t.edge is not None and t.edge[1] == 1.0 and dot(t.edge[0], LIGHT) >= illustration.FACING

        def elsewhere(t):
            return not outer(t)

        self.assertEqual(0, self.lit_share({"Lid": 3}, elsewhere), "never an inner ring nor an edge in shade")
        self.assertEqual(0, self.lit_share({"Lid": 0}, outer), "a calm part takes none")
        self.assertGreater(self.lit_share({"Lid": 3}, outer), self.lit_share({"Lid": 1}, outer))


if __name__ == "__main__":
    unittest.main()
