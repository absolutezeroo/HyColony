"""Conversion to the material layout checks of the Domum Ornamentum generator (run by check.py)."""

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


def run():
    """Runs this module's checks; an AssertionError names the failing case."""
    second_component_reads_the_right_tile()
    single_material_stays_in_one_tile()
    reversed_uv_is_mirrored_in_place()
    every_do_face_reads_inside_one_tile()
