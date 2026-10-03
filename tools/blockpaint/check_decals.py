"""Checks of quads and decals (spec 2026-10-03 blockpaint decals): python tools/blockpaint/check_decals.py (no assets
needed). An AssertionError names the case."""

import math
import unittest
from types import SimpleNamespace

from PIL import Image

import bake
import brushes
import catalog
import conditions
import decals
import effects
import paint
import surface
from icons import ICON_SIZE, draw_model, frame
from models import FACE_NORMALS, box_shape, node, placed, quad_shape, unwrap
from vectors import add, rotate, scale

SIDES = ("front", "back", "left", "right", "top", "bottom")
WOOD, DIAL = (150, 104, 62), (232, 222, 196)


def ring(w, h, side):
    """A dial drawn on a clear ground: opaque on its border, transparent within."""
    image = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    for x in range(w):
        for y in range(h):
            if x in (0, w - 1) or y in (0, h - 1):
                image.putpixel((x, y), (*DIAL, 255))
    return image


def block_with_quad():
    """An 8 unit block and a 4 x 4 quad 0.1 in front of its front face, both laid out."""
    nodes = [node("Block", (0, 4, 0), box_shape((8, 8, 8), SIDES)),
             node("Decal", (0, 4, 4.1), quad_shape((4, 4)))]
    size = unwrap(nodes)
    return nodes, size


def rect_of(nodes, name):
    return next(r[2:] for r in paint.islands(nodes) if r[0] == name)


class QuadTest(unittest.TestCase):
    def test_a_quad_unwraps_bakes_without_edges_and_paints_keeping_its_transparency(self):
        nodes, size = block_with_quad()
        u, v, w, h = rect_of(nodes, "Decal")
        self.assertEqual((4, 4), (w, h))
        values, contexts = bake.survey(nodes)
        decal = [contexts[u + i, v + j] for i in range(w) for j in range(h)]
        self.assertTrue(all(t.normal == (0.0, 0.0, 1.0) and t.edge is None and t.rim == bake.RIM_CAP for t in decal))
        look = paint.Look({"wood": brushes.wood(WOOD), "dial": ring}, lambda n, s: "dial" if n == "Decal" else "wood")
        image = paint.texture(nodes, size, look, values)
        self.assertEqual(0, image.getpixel((u + 2, v + 2))[3])
        self.assertEqual(255, image.getpixel((u, v))[3])

    def test_a_decal_takes_about_the_light_of_the_face_beneath(self):
        nodes, _ = block_with_quad()
        values = bake.light_map(nodes)
        du, dv, _, _ = rect_of(nodes, "Decal")
        bu, bv, _, _ = next(r[2:] for r in paint.islands(nodes) if r[:2] == ("Block", "front"))
        # The decal's middle lies over the front's middle: texels (1, 1) of the decal and (3, 3) of the face.
        self.assertAlmostEqual(values[bu + 3, bv + 3], values[du + 1, dv + 1], delta=0.05)

    def test_an_icon_draws_a_quad_over_its_box(self):
        nodes, size = block_with_quad()
        look = paint.Look({"wood": brushes.wood(WOOD), "dial": ring}, lambda n, s: "dial" if n == "Decal" else "wood")
        texture = paint.texture(nodes, size, look, bake.light_map(nodes))
        icon = Image.new("RGBA", (ICON_SIZE, ICON_SIZE), (0, 0, 0, 0))
        draw_model(icon, nodes, texture, *frame(nodes))
        lit = {p[:3] for p in icon.get_flattened_data() if p[3]}
        self.assertTrue(any(abs(c[0] - c[2]) < 50 and c[0] > 150 for c in lit), "the dial shows")

    def test_a_quad_facing_another_way_is_refused(self):
        shape = quad_shape((4, 4))
        shape["settings"]["normal"] = "-X"
        with self.assertRaises(SystemExit):
            unwrap([node("Decal", (0, 0, 0), shape)])


def turned_part():
    """A box offset from its node and turned 30 degrees about y, the way a hut's parts sit."""
    part = node("Part", (3, 4, -2), box_shape((8, 6, 4), SIDES, offset=(1, 0.5, 0)))
    half = math.radians(30) / 2
    part["orientation"] = {"x": 0, "y": math.sin(half), "z": 0, "w": math.cos(half)}
    return part


class PlaceTest(unittest.TestCase):
    def test_a_decal_lies_over_its_rect_a_gap_out_on_every_side_of_a_turned_part_static_or_not(self):
        for side, static in ((side, static) for side in SIDES for static in (False, True)):
            part = turned_part()
            part["shape"]["settings"]["isStaticBox"] = static
            group = node("Group", (-2, 1, 5), box_shape((2, 2, 2), SIDES), [part])
            half = math.radians(20) / 2
            group["orientation"] = {"x": math.sin(half), "y": 0, "z": 0, "w": math.cos(half)}
            nodes = [group]
            decals.place(nodes, {"dial": decals.Decal("Part", side, (1, 1, 2, 2), ring)})
            # A static box keeps no children (Blockbench drops them): its decal lies beside it, a static box too.
            holder = group if static else part
            self.assertEqual(["Part_Decal_dial"], [c["name"] for c in holder["children"] if "Decal" in c["name"]])
            self.assertEqual(static, bool(holder["children"][-1]["shape"]["settings"].get("isStaticBox")))
            unwrap(nodes)
            where = {n["name"]: (n, position, rotation) for n, position, rotation in placed(nodes)}
            part, decal = where["Part"], where["Part_Decal_dial"]
            on_face, _, _ = bake.face_frame(part[0]["shape"], side, part[1], part[2])
            on_decal, _, _ = bake.face_frame(decal[0]["shape"], "front", decal[1], decal[2])
            out = scale(rotate(part[2], FACE_NORMALS[side]), decals.DECAL_GAP)
            for i, j in ((0, 0), (1, 0), (0, 1), (1, 1)):
                expected = add(on_face(1 + i + 0.5, 1 + j + 0.5), out)
                for a, b in zip(expected, on_decal(i + 0.5, j + 0.5)):
                    self.assertAlmostEqual(a, b, places=6, msg=side)

    def test_placing_again_replaces_the_former_decals(self):
        nodes = [turned_part()]
        decals.place(nodes, {"dial": decals.Decal("Part", "front", (1, 1, 2, 2), ring)})
        decals.place(nodes, {"hand": decals.Decal("Part", "top", (0, 0, 3, 1), ring)})
        self.assertEqual(["Part_Decal_hand"], [c["name"] for c in nodes[0]["children"]])
        decals.place(nodes, {})
        self.assertEqual([], nodes[0]["children"])
        bare = {"name": "Part", "shape": turned_part()["shape"]}
        decals.place([bare], {})
        self.assertNotIn("children", bare, "a model without decals is written as before")

    def test_placing_again_replaces_the_decal_of_a_static_box_at_the_top_and_in_a_group(self):
        # The residence's clock: a static box at the model's top, its dial beside it at the top too.
        for nested in (False, True):
            part = turned_part()
            part["shape"]["settings"]["isStaticBox"] = True
            nodes = [node("Group", (0, 0, 0), box_shape((2, 2, 2), SIDES), [part])] if nested else [part]
            holder = nodes[0]["children"] if nested else nodes
            for _ in range(2):
                decals.place(nodes, {"dial": decals.Decal("Part", "front", (1, 1, 2, 2), ring)})
            self.assertEqual(["Part", "Part_Decal_dial"], [n["name"] for n in holder], nested)
            decals.place(nodes, {})
            self.assertEqual(["Part"], [n["name"] for n in holder], nested)

    def test_a_decal_is_seen_from_both_sides_and_shaded_as_its_part(self):
        for shading in ("standard", "flat"):
            part = turned_part()
            part["shape"]["shadingMode"] = shading
            decals.place([part], {"dial": decals.Decal("Part", "front", (1, 1, 2, 2), ring)})
            shape = part["children"][0]["shape"]
            self.assertTrue(shape["doubleSided"])
            self.assertEqual(shading, shape["shadingMode"])

    def test_a_decal_too_small_or_between_texels_is_refused(self):
        for rect in ((0, 0, 0, 2), (0, 0, 2, 1.5), (0.5, 0, 2, 2)):
            with self.assertRaises(SystemExit, msg=rect):
                decals.place([turned_part()], {"dial": decals.Decal("Part", "front", rect, ring)})

    def test_catalog_paints_a_decal_as_a_drawing_whether_the_model_is_layered_or_not(self):
        for condition in (None, conditions.WORN):
            nodes = [node("Block", (0, 4, 0), box_shape((8, 8, 8), SIDES))]
            module = SimpleNamespace(MODEL="Blocks/Test/Clock", PICTURES=frozenset(), material=lambda n, s: "wood",
                                     tiles=lambda a: {"wood": effects.material(brushes.wood(WOOD))
                                                      if condition else brushes.wood(WOOD)},
                                     DECALS={"dial": decals.Decal("Block", "front", (2, 2, 4, 4), ring)},
                                     **({"CONDITION": condition} if condition else {}))
            decals.place(nodes, module.DECALS)
            unwrap(nodes)
            image = catalog.model_texture(module, nodes, None)
            u, v, w, h = rect_of(nodes, "Block_Decal_dial")
            self.assertEqual(0, image.getpixel((u + 2, v + 2))[3], condition)
            ring_texel = image.getpixel((u, v))
            self.assertLess(abs(ring_texel[0] - ring_texel[2]), 60, condition)
        # Layered, the dial is a drawing: worn as the model is, no wear nor ageing even runs on it (run records each
        # effect it runs in zones, reached or not).
        record = []
        values, contexts = catalog.surveyed(module, nodes)
        catalog.module_texture(module, nodes, None, values, catalog.module_surface(module, contexts, record))
        dial = next(island for part, _, _, island, _ in record if part == "Block_Decal_dial")
        degrading = {name for name, effect in surface.CATALOGUE.items() if effect.moment == "degrade"}
        self.assertFalse(set(dial.zones) & degrading, dial.zones)
        self.assertEqual("wood", dial.family, "a decal is of its part's family: what settles on the part settles")

    def test_a_layered_model_paints_every_tile_in_layers_brush_or_image(self):
        nodes = [node("Block", (0, 4, 0), box_shape((8, 8, 8), SIDES))]
        unwrap(nodes)
        record = []
        module = SimpleNamespace(MODEL="Blocks/Test/Crate", PICTURES=frozenset({"label"}),
                                 material=lambda n, s: {"top": "label", "front": "image"}.get(s, "wood"),
                                 tiles=lambda a: {"wood": brushes.wood(WOOD),
                                                  "image": brushes.as_tile(brushes.wood(WOOD)),
                                                  "label": Image.new("RGBA", (32, 32), (*DIAL, 255))},
                                 FAMILY={"image": "wood", "label": "paper"}, CONDITION=conditions.USED)
        values, contexts = catalog.surveyed(module, nodes)
        catalog.module_texture(module, nodes, None, values, catalog.module_surface(module, contexts, record))
        painted = {side: island for _, side, _, island, _ in record}
        self.assertEqual(set(SIDES), set(painted), "every face layered, image tiles included")
        self.assertEqual(("wood", "wood", "paper"), (painted["right"].family, painted["front"].family,
                                                     painted["top"].family), "the brush's family, else FAMILY's")
        self.assertTrue(painted["top"].substrate.getpixel((0, 0))[:3] == DIAL, "a picture laid as drawn")

    def test_decals_on_an_item_model_are_refused_before_anything_is_read(self):
        module = SimpleNamespace(MODEL="Items/Test/Clock", DECALS={})
        with self.assertRaises(SystemExit):
            catalog.paint([module], None, None, None)

    def test_a_missing_or_stretched_part_is_refused(self):
        with self.assertRaises(SystemExit):
            decals.place([turned_part()], {"dial": decals.Decal("Lid", "front", (0, 0, 2, 2), ring)})
        stretched = turned_part()
        stretched["shape"]["stretch"]["x"] = -1
        with self.assertRaises(SystemExit):
            decals.place([stretched], {"dial": decals.Decal("Part", "front", (0, 0, 2, 2), ring)})

    def test_a_decal_on_a_face_the_part_does_not_show_is_refused(self):
        part = node("Part", (0, 4, 0), box_shape((8, 8, 8), ("front", "top")))
        with self.assertRaises(SystemExit):
            decals.place([part], {"dial": decals.Decal("Part", "back", (0, 0, 2, 2), ring)})

    def test_painting_leaves_the_module_s_own_tiles_as_they_were(self):
        shared = {"wood": brushes.wood(WOOD)}
        module = SimpleNamespace(MODEL="Blocks/Test/Clock", PICTURES=frozenset(), material=lambda n, s: "wood",
                                 tiles=lambda a: shared,
                                 DECALS={"dial": decals.Decal("Block", "front", (2, 2, 4, 4), ring)})
        nodes = [node("Block", (0, 4, 0), box_shape((8, 8, 8), SIDES))]
        decals.place(nodes, module.DECALS)
        unwrap(nodes)
        catalog.model_texture(module, nodes, None)
        self.assertEqual({"wood"}, set(shared), "a module-wide dict gains no decal")

    def test_a_decal_on_a_face_of_a_material_the_module_has_not_is_refused_by_name(self):
        module = SimpleNamespace(PICTURES=frozenset(), material=lambda n, s: "glass", tiles=lambda a: {},
                                 DECALS={"dial": decals.Decal("Block", "front", (2, 2, 4, 4), ring)})
        with self.assertRaisesRegex(SystemExit, "decal dial"):
            catalog.decal_tiles(module, {"wood": effects.material(brushes.wood(WOOD))}, True)


if __name__ == "__main__":
    unittest.main()
