"""Conversion to the material layout checks of the Domum Ornamentum generator (run by check.py)."""

import math

import assemble
import convert
import faces
import source
from families import FAMILIES


def _faces_read(blockymodel):
    from models import face_rects
    return list(face_rects(blockymodel["nodes"]))


def second_component_reads_the_right_tile():
    root = source.fetch()
    timber = next(f for f in FAMILIES if f.name == "TimberFrame")
    model = convert.to_blockymodel(assemble.state_model(root, timber, "framed", {}), timber)
    lefts = [r[1] for r in _faces_read(model)]
    assert any(x >= 32 for x in lefts) and any(x < 32 for x in lefts)


def single_material_stays_in_one_tile():
    root = source.fetch()
    post = next(f for f in FAMILIES if f.name == "Post")
    model = convert.to_blockymodel(assemble.state_model(root, post, "post", {}), post)
    assert all(r[3] <= 32 for r in _faces_read(model))


def reversed_uv_is_mirrored_in_place():
    family = next(f for f in FAMILIES if f.name == "Post")
    element = {"from": [0, 0, 0], "to": [8, 16, 1], "faces": {"north": {"uv": [16, 0, 8, 16], "texture": "#a"}}}
    model = convert.to_blockymodel({"textures": {"a": "block/oak_planks"}, "elements": [element]}, family)
    face = model["nodes"][0]["children"][0]["shape"]["textureLayout"]["back"]
    assert face["mirror"]["x"] and not face["mirror"]["y"]
    assert _faces_read(model)[0][1:] == (16, 0, 32, 32)  # uv 8..16 -> texels 16..32, read mirrored


def every_do_face_reads_inside_one_tile():
    """No face of any DO-1 default state reads outside its layout or across two material tiles (it would show the
    other material); oversized faces are stretched instead."""
    root = source.fetch()
    for family in FAMILIES:
        if family.mechanism == "vanilla" or family.name == "AllBrickStair":
            continue
        width, height = convert.layout_size(family)
        for block in family.blocks:
            model = convert.to_blockymodel(faces.clean(assemble.state_model(root, family, block, {})), family)
            for name, u0, v0, u1, v1 in _faces_read(model):
                eps = 1e-6
                assert -eps <= u0 and u1 <= width + eps and -eps <= v0 and v1 <= height + eps, (block, name, u0, u1)
                assert (u0 + eps) // 32 == (u1 - eps) // 32, (block, name, u0, u1)


def _node(model, name):
    return next(n for n in model["nodes"][0]["children"] if n["name"] == name)


def turned_face_reads_its_rotated_rectangle():
    """A face turned 90 degrees reads the rectangle the vanilla rule gives: an 8x16 px face (16x32 units) turned by
    90 reads a 32x16 texel area. Worked by hand: corners of (0..16, 0..32) turned (x, y) -> (-y, x) span x -32..0,
    so the offset (pivot) is 32 to read x 0..32; a sign error in the turn moves it to (0, 16)."""
    family = next(f for f in FAMILIES if f.name == "Post")
    element = {"from": [0, 0, 0], "to": [8, 16, 1], "faces": {"north": {"uv": [0, 0, 16, 8], "rotation": 90,
                                                                          "texture": "#a"}}}
    model = convert.to_blockymodel({"textures": {"a": "block/oak_planks"}, "elements": [element]}, family)
    face = _node(model, "E0")["shape"]["textureLayout"]["back"]
    assert face["angle"] == 90 and face["offset"] == {"x": 32, "y": 0}, face
    assert _faces_read(model)[0][1:] == (0, 0, 32, 16)


def oversized_box_is_stretched_back():
    """A box wider than one tile is built at 32 units on that axis and stretched to its real size."""
    family = next(f for f in FAMILIES if f.name == "Post")
    element = {"from": [0, 0, 0], "to": [24, 16, 16], "faces": {"north": {"texture": "#a"}, "up": {"texture": "#a"}}}
    model = convert.to_blockymodel({"textures": {"a": "block/oak_planks"}, "elements": [element]}, family)
    shape = _node(model, "E0")["shape"]
    assert shape["settings"]["size"] == {"x": 32, "y": 32, "z": 32}, shape["settings"]
    assert shape["stretch"] == {"x": 1.5, "y": 1.0, "z": 1.0}, shape["stretch"]
    assert all(u1 <= 32 and v1 <= 32 for _, _, _, u1, v1 in _faces_read(model))


def geometry_matches_blockbench_units():
    """Positions, pivots and shapes follow Blockbench's Hytale exporter: 2 units per Minecraft pixel, x and z centred
    on the block; a tilted element turns about its origin; a flat element with both sides textured is a double-sided
    quad facing +Z."""
    family = next(f for f in FAMILIES if f.name == "Post")
    elements = [
        {"from": [4, 0, 4], "to": [12, 8, 12], "faces": {"up": {"texture": "#a"}}},
        {"from": [0, 0, 8], "to": [16, 16, 8], "faces": {"north": {"texture": "#a"}, "south": {"texture": "#a"}}},
        {"from": [6, 0, 6], "to": [10, 16, 10], "rotation": {"angle": 22.5, "axis": "y", "origin": [8, 8, 8]},
         "faces": {"north": {"texture": "#a"}}},
    ]
    model = convert.to_blockymodel({"textures": {"a": "block/oak_planks"}, "elements": elements}, family)
    box, quad, tilted = (_node(model, name) for name in ("E0", "E1", "E2"))
    assert box["position"] == {"x": 0, "y": 8, "z": 0} and box["shape"]["type"] == "box"
    assert box["shape"]["settings"]["size"] == {"x": 16, "y": 16, "z": 16}
    assert quad["shape"]["type"] == "quad" and quad["shape"]["doubleSided"]
    assert quad["shape"]["settings"]["normal"] == "+Z"
    assert tilted["position"] == {"x": 0, "y": 16, "z": 0}
    assert abs(tilted["orientation"]["y"] - math.sin(math.radians(11.25))) < 1e-9, tilted["orientation"]


def run():
    """Runs this module's checks; an AssertionError names the failing case."""
    second_component_reads_the_right_tile()
    single_material_stays_in_one_tile()
    reversed_uv_is_mirrored_in_place()
    every_do_face_reads_inside_one_tile()
    turned_face_reads_its_rotated_rectangle()
    oversized_box_is_stretched_back()
    geometry_matches_blockbench_units()
