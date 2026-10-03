"""Checks of the history of a layered model (spec 2026-10-03 blockpaint surfaces § 3.6): python
tools/blockpaint/check_history.py (no assets needed). An AssertionError names the case."""

import math
import unittest
from types import SimpleNamespace

import bake
import brushes
import catalog
import coatings
import conditions
import effects
import history
import marks
import semantics
import surface
from check_surfaces import by_face, model
from models import scale


def tower():
    """A post standing on a slab: faces facing every way, one above the other."""
    return model(("Slab", (0, 1, 0), (16, 2, 16)), ("Post", (0, 10, 0), (4, 16, 4)))


class LocateTest(unittest.TestCase):
    def setUp(self):
        _, self.contexts = bake.survey(tower(), grounded=True)

    def test_a_point_source_sits_at_its_face_s_centre_shifted_by_its_offset(self):
        origin = history.locate(history.point("Post", (0, 4, 0)), self.contexts)
        for k, expected in enumerate((0.0, 14.0, 2.0)):
            self.assertAlmostEqual(expected, origin[k], delta=0.6)
        inside = history.locate(history.point("Post", side=None), self.contexts)
        for k, expected in enumerate((0.0, 10.0, 0.0)):
            self.assertAlmostEqual(expected, inside[k], delta=0.6)

    def test_battle_damage_strikes_in_the_plane_of_its_face(self):
        for side, normal in (("front", 2), ("right", 0), ("top", 1)):
            for blow in history.battle_damage(3, "Post", side=side):
                self.assertEqual(0.0, blow.source.offset[normal], side)
                self.assertEqual(side, blow.source.side)

    def test_an_edge_source_sits_on_the_middle_of_a_face_s_border(self):
        origin = history.locate(history.edge("Post", "front", "up"), self.contexts)
        self.assertAlmostEqual(18.0, origin[1], delta=0.6)
        self.assertAlmostEqual(2.0, origin[2], delta=0.6)

    def test_a_wrong_history_is_refused_with_a_clear_message(self):
        with self.assertRaises(SystemExit) as wrong:
            history.locate(history.edge("Post", "front", "upward"), self.contexts)
        self.assertIn("upward", str(wrong.exception))
        with self.assertRaises(SystemExit) as lone:
            history.resolved(history.impact("Post"), self.contexts)
        self.assertIn("tuple", str(lone.exception))
        with self.assertRaises(SystemExit):
            history.resolved(("impact",), self.contexts)


class ReachTest(unittest.TestCase):
    def setUp(self):
        self.nodes = tower()
        _, self.contexts = bake.survey(self.nodes, grounded=True)

    def reached(self, event):
        origin = history.locate(event.source, self.contexts)
        return {t: history.reach_of(event, t, origin) for t in self.contexts.values()}

    def test_a_radiating_event_fades_with_distance_and_stops_at_its_radius(self):
        blast = history.Event("test", history.point("Post"), None, 6.0, 1.0, 1.0, 0.0, ())
        reach = self.reached(blast)
        front = by_face(self.contexts, "Post", "front")
        near = max(front, key=lambda t: reach[t])
        self.assertGreater(reach[near], 0.6)
        far = [t for t in self.contexts.values() if sum((a - b) ** 2 for a, b in zip(t.point, (0, 10, 2))) > 36.5]
        self.assertTrue(all(reach[t] == 0.0 for t in far))

    def test_a_patch_is_whole_up_to_its_radius_square_on_its_own_face_and_nothing_past_it(self):
        # A radius of 2: the texels 1.5 off both ways lie 2.1 away, out of a round patch, in a square one.
        patch = history.repaired("Post", radius=2.0)
        reach = self.reached(patch)
        front = by_face(self.contexts, "Post", "front")
        inside = [t for t in front if max(abs(t.point[0]), abs(t.point[1] - 10)) < 2.0]
        self.assertTrue(any(abs(t.point[0]) == 1.5 and abs(t.point[1] - 10) == 1.5 for t in inside))
        self.assertTrue(all(reach[t] == 1.0 for t in inside))
        self.assertTrue(all(reach[t] == 0.0 for t in self.contexts.values() if t not in inside))

    def test_smoke_gathers_under_what_hangs_over_it_and_leaves_the_tops(self):
        nodes = model(("Floor", (0, 1, 0), (16, 2, 16)), ("Lintel", (0, 13, 3), (16, 2, 6)))
        _, contexts = bake.survey(nodes, grounded=True)
        smoke = history.Event("test", history.point("Floor", (0, 0, 3), side="top"), (0.0, 1.0, 0.0), 14.0, 0.6, 1.0,
                              0.0, ())
        origin = history.locate(smoke.source, contexts)
        self.assertTrue(any(history.reach_of(smoke, t, origin) > 0 for t in by_face(contexts, "Lintel", "bottom")))
        self.assertTrue(all(history.reach_of(smoke, t, origin) == 0 for t in by_face(contexts, "Lintel", "top")))

    def test_a_face_turned_away_from_the_source_is_not_reached(self):
        blast = history.Event("test", history.point("Post", (0, 0, 1)), None, 8.0, 1.0, 1.0, 0.0, ())
        reach = self.reached(blast)
        self.assertTrue(all(reach[t] == 0.0 for t in by_face(self.contexts, "Post", "back")))
        self.assertTrue(any(reach[t] > 0.0 for t in by_face(self.contexts, "Post", "front")))

    def test_a_flow_runs_down_from_its_source_never_up_and_thins_on_its_way(self):
        drip = history.Event("test", history.edge("Post", "front", "up"), history.GRAVITY, 14.0, 1.0, 1.0, 0.0, ())
        reach = self.reached(drip)
        front = by_face(self.contexts, "Post", "front")
        wet = [t for t in front if reach[t] > 0]
        self.assertTrue(wet)
        self.assertTrue(all(t.point[1] <= 18.5 for t in wet))
        # Rows of texels on the post's front (y 2 to 18): near the source (y 16.5), and near the flow's end (y 5.5,
        # its radius of 14 ending at y 4).
        high = [t for t in wet if abs(t.point[1] - 16.5) < 0.1]
        low = [t for t in wet if abs(t.point[1] - 5.5) < 0.1]
        self.assertTrue(high and low)
        self.assertGreater(len(high), len(low) - 1)
        self.assertGreater(max(reach[t] for t in high), max(reach[t] for t in low))

    def test_a_flow_reaches_the_slab_below_but_never_a_face_turned_down(self):
        drip = history.Event("test", history.edge("Post", "front", "up"), history.GRAVITY, 30.0, 0.5, 1.0, 0.0, ())
        reach = self.reached(drip)
        self.assertTrue(any(reach[t] > 0 for t in by_face(self.contexts, "Slab", "top")))
        self.assertTrue(all(reach[t] == 0 for t in by_face(self.contexts, "Slab", "bottom")))

    def test_the_sky_reaches_what_sees_the_sky(self):
        rain = history.Event("test", history.sky(), None, 1.0, 1.0, 1.0, 0.0, ())
        reach = self.reached(rain)
        self.assertTrue(all(reach[t] > 0.5 for t in by_face(self.contexts, "Post", "top")))
        self.assertTrue(all(reach[t] == 0 for t in by_face(self.contexts, "Post", "front")))


def tower_module(*events):
    """A catalog module painting the tower, pristine (nothing but its history): a painted wooden post on an iron
    slab."""
    wood = effects.material(brushes.wood((150, 104, 62)), coats=(coatings.paint_coat((60, 96, 150)),))
    iron = effects.material(brushes.metal((118, 120, 126)))
    return SimpleNamespace(MODEL="Blocks/Test/Tower", PICTURES=frozenset(), tiles=lambda assets: {"wood": wood,
                                                                                                "iron": iron},
                           material=lambda name, side: "iron" if name == "Slab" else "wood",
                           CONDITION=conditions.PRISTINE, SEED=2, HISTORY=events)


EVENTS = {
    "impact": history.impact("Post", (0, 2, 0)), "battle_damage": history.battle_damage(3, "Post"),
    "dropped": history.dropped("Post"), "fire": history.fire("Post", (0, -6, 1)), "water": history.water("Post"),
    "blood": history.blood("Post"), "rust_from": history.rust_from("Post", (0, 6, 0)),
    "chemical": history.chemical("Post"), "magic": history.magic("Post"), "repaired": history.repaired("Post"),
}


class EventsTest(unittest.TestCase):
    def test_every_event_lays_its_marks_within_its_reach_steadily_and_opaque(self):
        nodes = tower()
        for name, event in EVENTS.items():
            module = tower_module(event)
            records = semantics.records_of(module, nodes)
            marked = {n for r in records for n, zone in r[3].zones.items() if zone}
            for one in event if isinstance(event, tuple) and not isinstance(event, history.Event) else (event,):
                self.assertTrue(marked & {effect.name for effect, _ in one.effects}, f"{name} {one.kind}")
            self.assertTrue(semantics.answers(module, nodes)[-1].ok, name)
            image = catalog.model_texture(module, nodes, None)
            self.assertEqual(image.tobytes(), catalog.model_texture(module, nodes, None).tobytes(), name)
            for _, _, _, _, painted in records:
                self.assertEqual({255}, {p[3] for p in painted.get_flattened_data()}, name)

    def test_a_fire_blackens_above_its_hearth(self):
        nodes = tower()
        records = semantics.records_of(tower_module(EVENTS["fire"]), nodes)
        soot = [r[3].texels[c].point[1] for r in records for c in r[3].zones.get("smoke_soot", ())]
        hearth = history.locate(EVENTS["fire"][1].source, bake.survey(nodes, True)[1])
        self.assertTrue(soot)
        self.assertGreater(sum(soot) / len(soot), hearth[1])

    def test_a_mark_out_of_its_event_s_reach_is_seen(self):
        nodes = tower()
        contexts = bake.survey(nodes, True, frozenset(), 0.0, 0.0)[1]
        for name, mark in (("blood", "blood"), ("fire", "smoke_soot")):
            module = tower_module(EVENTS[name])
            records = semantics.records_of(module, nodes)
            back = next(r for r in records if r[:2] == ("Post", "back"))
            back[3].zones[mark] = set(back[3].texels)
            self.assertFalse(semantics.history_in_place(records, history.resolved(module.HISTORY, contexts)).ok, name)

    def test_planned_chips_far_from_an_impact_are_not_taken_for_its_own(self):
        nodes = tower()
        contexts = bake.survey(nodes, True, frozenset(), 0.0, 0.0)[1]
        module = tower_module(EVENTS["impact"])
        records = semantics.records_of(module, nodes)
        back = next(r for r in records if r[:2] == ("Post", "back"))
        back[3].zones["chips"] = set(back[3].texels)
        self.assertTrue(semantics.history_in_place(records, history.resolved(module.HISTORY, contexts)).ok)

    def test_the_marks_bear_names_the_plan_never_lays(self):
        self.assertFalse(set(marks.MARKS) & set(surface.CATALOGUE))

    def test_an_event_on_a_part_that_is_not_layered_is_still_asked_about(self):
        nodes = tower()
        module = tower_module(history.impact("Slab", side="top"))
        plain = brushes.metal((118, 120, 126))
        module.tiles = lambda assets: {"wood": tower_module().tiles(None)["wood"], "iron": plain}
        self.assertIsNot(semantics.answers(module, nodes)[-1].ok, False)

    def test_blood_flies_in_drops_beyond_its_blot_and_magic_in_veins_beyond_its_wash(self):
        nodes = tower()
        contexts = bake.survey(nodes, True, frozenset(), 0.0, 0.0)[1]
        # Past the core, only a drop or a vein may be reached: the world noise says where they are, apart from marks.
        def drop(t):
            return bake.value_noise(scale(t.point, marks.DROP_SCALE)) > marks.DROP_LEVEL

        def vein(t):
            return abs(bake.value_noise(scale(t.point, marks.VEIN_SCALE))) < marks.VEIN_WIDTH

        for event, mark, core, shape in ((history.blood("Slab", side="top"), "blood", 4.0, drop),
                                         (history.magic("Slab", side="top"), "glow", 3.7, vein)):
            records = semantics.records_of(tower_module(event), nodes)
            (_, origin), = history.resolved((event,), contexts)
            top = next(r for r in records if r[:2] == ("Slab", "top"))
            far = [top[3].texels[c] for c in top[3].zones[mark] if math.dist(top[3].texels[c].point, origin) > core]
            self.assertTrue(far, mark)
            self.assertTrue(all(shape(t) for t in far), mark)


class AgeTest(unittest.TestCase):
    def test_an_old_event_happens_before_the_deposits_and_a_recent_one_after(self):
        self.assertEqual("past", history.moment_of(0.8))
        self.assertEqual("recent", history.moment_of(0.1))


if __name__ == "__main__":
    unittest.main()
