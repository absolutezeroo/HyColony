"""Blockstate assembly and orientation checks of the Domum Ornamentum generator (run by check.py)."""

import tempfile
from pathlib import Path

import assemble
import source
from families import FAMILIES


def families_cover_the_spec():
    """DO-1's family table matches the design's family list, and leaves out what it explicitly excludes."""
    names = {f.name for f in FAMILIES}
    assert names == {"TimberFrame", "Shingle", "ShingleSlab", "Pillar", "Post", "Panel", "Door", "FancyDoor",
                      "Trapdoor", "FancyTrapdoor", "PaperWall", "Fence", "FenceGate", "Wall", "Stairs", "Slab",
                      "AllBrick", "AllBrickStair"}, names
    # Framed Light block ids all end with "_light" (out of DO-1 scope); AllBrick's "light_brick" and
    # AllBrickStair's "light_brick_stair" start with "light" instead, so endswith (not a plain substring test)
    # tells them apart.
    assert not any("dynamic" in b or b.endswith("light") for f in FAMILIES for b in f.blocks)


def partial_cache_is_refetched(tmp):
    """A cache missing its .complete marker (Review Focus 4: an interrupted download) is never trusted."""
    (tmp / "models").mkdir(parents=True)
    assert not source.is_complete(tmp)


def rotate_y_turns_north_face_to_east():
    """assemble.rotate_y matches Minecraft's y rotation direction: clockwise from above, north -> east."""
    model = {
        "textures": {},
        "elements": [
            {"from": [0, 0, 0], "to": [16, 16, 2], "faces": {"north": {"uv": [0, 0, 16, 16], "texture": "#a"}}}
        ],
    }
    element = assemble.rotate_y(model, 90)["elements"][0]
    assert element["from"] == [14, 0, 0] and element["to"] == [16, 16, 16], element
    assert set(element["faces"]) == {"east"}


def multipart_keeps_matching_parts_only():
    """assemble.parts keeps every unconditional part plus the parts whose "when" matches props."""
    state = {
        "multipart": [
            {"apply": {"model": "p/post"}},
            {"when": {"north": "true"}, "apply": {"model": "p/side"}},
            {"when": {"north": "false|none"}, "apply": {"model": "p/side_off"}},
        ]
    }
    assert [p["model"] for p in assemble.parts(state, {"north": "false"})] == ["p/post", "p/side_off"]


def multipart_or_condition_matches_any_branch():
    state = {"multipart": [{"when": {"OR": [{"north": "true"}, {"south": "true"}]}, "apply": {"model": "p/side"}}]}
    assert [p["model"] for p in assemble.parts(state, {"north": "false", "south": "true"})] == ["p/side"]
    assert assemble.parts(state, {"north": "false", "south": "false"}) == []


def inner_tilt_follows_a_model_turn():
    """rotate_y carries an element's own tilt: a 22.5 degree tilt about z becomes -22.5 about x after a
    90 degree turn (Minecraft's z axis maps onto -x), its origin turned with the element."""
    model = {"textures": {}, "elements": [{"from": [2, 14, 15], "to": [14, 16, 16], "faces": {},
             "rotation": {"angle": 22.5, "axis": "z", "origin": [8, 8, 10]}}]}
    rotation = assemble.rotate_y(model, 90)["elements"][0]["rotation"]
    assert rotation["axis"] == "x" and rotation["angle"] == -22.5, rotation
    assert rotation["origin"] == [6, 8, 8], rotation


def uvlock_recomputes_uv_from_rotated_bounds():
    """rotate_y with uvlock replaces a rotated face's uv by Minecraft's default uv of its new bounds, but
    leaves it untouched when degrees is 0 (no rotation to compensate for)."""
    model = {
        "textures": {},
        "elements": [{"from": [2, 14, 4], "to": [10, 16, 12], "faces": {"up": {"uv": [0, 0, 16, 16], "texture": "#a"}}}],
    }
    turned = assemble.rotate_y(model, 90, uvlock=True)["elements"][0]
    assert turned["from"] == [4, 14, 2] and turned["to"] == [12, 16, 10], turned
    assert turned["faces"]["up"]["uv"] == [4, 2, 12, 10], turned["faces"]["up"]

    still = assemble.rotate_y(model, 0, uvlock=True)["elements"][0]
    assert still["faces"]["up"]["uv"] == [0, 0, 16, 16], still["faces"]["up"]


def state_model_assembles_real_do_data():
    """state_model resolves both DO model roots (source.fetch(): hand-authored "_spec" models and the
    datagen-generated thin wrappers a blockstate's own "model" field references) for families whose
    default state needs no uvlock."""
    root = source.fetch()
    for family_name, block_id in (("TimberFrame", "plain"), ("Shingle", "shingle"), ("Door", "vanilla_doors_compat")):
        family = next(f for f in FAMILIES if f.name == family_name)
        model = assemble.state_model(root, family, block_id, {})
        assert model["elements"], family_name


def uvlock_families_assemble_without_error():
    """Trapdoor, Panel (their default state) and Pillar's column shape all select a uvlock DO part in
    real data; state_model recomputes their uv instead of raising."""
    root = source.fetch()
    cases = (
        ("Trapdoor", "vanilla_trapdoors_compat", {}),
        ("Panel", "panel", {}),
        ("Pillar", "blockpillar", {"column": "pillar_column"}),
    )
    for family_name, block_id, props in cases:
        family = next(f for f in FAMILIES if f.name == family_name)
        model = assemble.state_model(root, family, block_id, props)
        assert model["elements"], family_name


def default_props_pick_the_blocks_own_default_state():
    """A property missing from props resolves to the block's own default (facing=north, half=bottom for
    the stair-style spelling): state_model with props={} matches calling with those defaults spelled out."""
    root = source.fetch()
    shingle = next(f for f in FAMILIES if f.name == "Shingle")
    implicit = assemble.state_model(root, shingle, "shingle", {})
    explicit = assemble.state_model(
        root, shingle, "shingle", {"facing": "north", "half": "bottom", "shape": "straight"}
    )
    assert implicit == explicit


def oriented_families_face_minus_z():
    """A facing=north state faces Hytale's front: every shingle slope's high edge sits well inside the -Z half
    (Stairs.blockymodel); a closed door is thin along Z and swings open on its west (-X) hinge, the Crude door's;
    an open trapdoor or panel stands on its -Z hinge, the Crude trapdoor's. A 90 or 180 degree error fails."""
    root = source.fetch()

    def points(family, block, props):
        return [p for e in assemble.state_model(root, family, block, props)["elements"]
                for p in assemble.world_points(e)]

    def extent(ps, axis):
        return min(p[axis] for p in ps), max(p[axis] for p in ps)

    for family in FAMILIES:
        for block in family.blocks:
            if family.name == "Shingle":
                ps = points(family, block, {})
                top = max(p[1] for p in ps)
                high_z = [p[2] for p in ps if p[1] > top - 3]
                assert sum(high_z) / len(high_z) < 7.7, (block, sum(high_z) / len(high_z))
            elif family.mechanism == "door":
                closed = points(family, block, {})
                assert extent(closed, 2)[1] - extent(closed, 2)[0] < 5 < extent(closed, 0)[1] - extent(closed, 0)[0]
                assert extent(points(family, block, {"open": "true"}), 0)[1] < 8, block
            elif family.turn_y:
                assert extent(points(family, block, {"open": "true"}), 2)[1] < 8, block


def run():
    """Runs this module's checks; an AssertionError names the failing case."""
    families_cover_the_spec()
    rotate_y_turns_north_face_to_east()
    multipart_keeps_matching_parts_only()
    multipart_or_condition_matches_any_branch()
    inner_tilt_follows_a_model_turn()
    uvlock_recomputes_uv_from_rotated_bounds()
    state_model_assembles_real_do_data()
    uvlock_families_assemble_without_error()
    default_props_pick_the_blocks_own_default_state()
    oriented_families_face_minus_z()
    with tempfile.TemporaryDirectory() as tmp:
        partial_cache_is_refetched(Path(tmp))
