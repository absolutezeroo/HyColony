"""Self-check of the Domum Ornamentum generator: python tools/domum/check.py (exits non-zero on failure). Tests on
real DO data call source.fetch(), which downloads DO's pinned commit once into build/domum-cache/ (network needed
on the first run only)."""

import tempfile
from pathlib import Path

import assemble
import convert
import faces
import pairs
import tags
import source
from convert import Converter, check_atlas
from families import FAMILIES, material
from PIL import Image

# timber_frame/framed_spec's centre box, one frame beam turned 22.5 degrees and a flat, double-sided pane.
MODEL = {
    "textures": {"frame": "block/oak_planks", "centre": "block/dark_oak_planks", "1": "#frame"},
    "elements": [
        {"from": [1, 0, 1], "to": [15, 16, 15], "faces": {
            d: {"uv": [1, 0, 15, 16], "texture": "#centre"} for d in ("north", "east", "south", "west", "up", "down")}},
        {"from": [2, 14, 15], "to": [14, 16, 16], "rotation": {"angle": 22.5, "axis": "z", "origin": [8, 8, 8]},
         "faces": {"north": {"uv": [16, 0, 0, 2], "rotation": 90, "texture": "#1"}, "up": {"texture": "#frame"}}},
        {"from": [0, 0, 8], "to": [16, 16, 8], "faces": {
            "north": {"uv": [0, 0, 16, 16], "texture": "#centre"}, "south": {"uv": [0, 0, 16, 16], "texture": "#1"}}},
    ],
}


def converter_matches_reference_geometry():
    """The converter's units, pivots, face correspondence and uv handling on a hand-built model (banc bd451ec)."""
    planks = {"dark": Image.new("RGBA", (32, 32), (60, 30, 20, 255)),
              "light": Image.new("RGBA", (32, 32), (200, 170, 140, 255))}
    nodes, atlas, _ = Converter(planks).convert(MODEL)
    assert len(nodes) == 3, nodes
    box, beam, pane = (n["shape"] for n in nodes)
    assert box["type"] == "box" and box["settings"]["size"] == {"x": 28, "y": 32, "z": 28}
    assert nodes[0]["position"] == {"x": 0, "y": 16, "z": 0} and box["offset"] == {"x": 0, "y": 0, "z": 0}
    assert len(box["textureLayout"]) == 6
    assert nodes[1]["position"] == {"x": 0, "y": 16, "z": 0}  # turned about its Minecraft origin
    assert beam["offset"] == {"x": 0, "y": 14, "z": 15} and set(beam["textureLayout"]) == {"back", "top"}
    assert abs(nodes[1]["orientation"]["z"] - 0.19509) < 1e-4
    assert pane["type"] == "quad" and pane["settings"] == {"size": {"x": 32, "y": 32}, "normal": "+Z"}
    assert pane["doubleSided"]
    assert convert.material(MODEL["textures"], "#1") == "dark"
    assert convert.material(MODEL["textures"], "#centre") == "light"
    check_atlas("check", nodes, atlas)
    assert atlas.width & (atlas.width - 1) == 0 and atlas.height & (atlas.height - 1) == 0


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


def material_follows_component_order():
    """The first DO component is Darkwood, another known component is Lightwood, matching the design's rule."""
    timber = next(f for f in FAMILIES if f.name == "TimberFrame")
    assert material(timber, timber.components[0]) == "dark"
    assert material(timber, timber.components[1]) == "light"
    assert material(timber, "unknown/tex") == "light"
    post = next(f for f in FAMILIES if f.name == "Post")
    assert len(post.components) == 1 and material(post, "anything/else") == "dark"


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
    """A facing=north state faces Hytale's front (-Z, Stairs.blockymodel's high side): every shingle slope's
    high edge sits on the -Z half, and a closed door is a thin panel across X, thin along Z (the vanilla Crude
    door's hitbox spans X). Doors' hinge side and their position in the block are Task 8's (recentred there)."""
    root = source.fetch()
    for family in FAMILIES:
        for block in family.blocks:
            if family.name == "Shingle":
                points = [p for e in assemble.state_model(root, family, block, {})["elements"]
                          for p in assemble.world_points(e)]
                top = max(p[1] for p in points)
                high_z = [p[2] for p in points if p[1] > top - 3]
                assert sum(high_z) / len(high_z) < 8, (block, sum(high_z) / len(high_z))
            elif family.mechanism == "door":
                points = [p for e in assemble.state_model(root, family, block, {})["elements"]
                          for p in assemble.world_points(e)]
                extent = [max(p[i] for p in points) - min(p[i] for p in points) for i in range(3)]
                assert extent[2] < 5 < extent[0], (block, extent)


def coplanar_overlap_is_removed():
    a = {"from": [0, 0, 0], "to": [16, 16, 1], "faces": {"north": {"uv": [0, 0, 16, 16], "texture": "#a"}}}
    b = {"from": [4, 4, 0], "to": [12, 12, 1], "faces": {"north": {"uv": [0, 0, 8, 8], "texture": "#b"}}}
    cleaned = faces.clean({"textures": {}, "elements": [a, b]})
    assert faces.overlapping_pairs(cleaned) == []
    # The small face is a detail on the big one (DO's timber beams): it stays in front, the backing moves back.
    assert "north" in cleaned["elements"][1]["faces"]
    assert cleaned["elements"][0]["from"][2] == faces.MIN_GAP_PX


def near_coplanar_backing_moves_behind_its_detail():
    beam = {"from": [0, 0, 0.01], "to": [16, 2, 1], "faces": {"north": {"texture": "#frame"}}}
    centre = {"from": [1, 0, 0.02], "to": [15, 16, 15], "faces": {"north": {"texture": "#centre"}}}
    cleaned = faces.clean({"textures": {}, "elements": [beam, centre]})
    assert faces.overlapping_pairs(cleaned) == []
    assert abs(cleaned["elements"][1]["from"][2] - (0.01 + faces.MIN_GAP_PX)) < 1e-9
    assert cleaned["elements"][0]["from"][2] == 0.01


def fully_hidden_face_is_dropped():
    front = {"from": [0, 0, 0], "to": [16, 16, 1], "faces": {"north": {"texture": "#a"}}}
    back = {"from": [4, 4, 0.02], "to": [12, 12, 1], "faces": {"north": {"texture": "#b"}}}
    cleaned = faces.clean({"textures": {}, "elements": [front, back]})
    assert "north" not in cleaned["elements"][1]["faces"]


def pillar_column_is_capped():
    root = source.fetch()
    pillar = next(f for f in FAMILIES if f.name == "Pillar")
    for block in pillar.blocks:
        model = faces.cap_ends(assemble.state_model(root, pillar, block, {"column": "pillar_column"}))
        tops = [e for e in model["elements"] if e["to"][1] == 16 and "up" in e["faces"]]
        bottoms = [e for e in model["elements"] if e["from"][1] == 0 and "down" in e["faces"]]
        assert tops and bottoms, block
        # blockpillar's eight overlapping blades get one lid each way, never eight coplanar caps.
        assert faces.overlapping_pairs(model) == [] or block != "blockpillar", faces.overlapping_pairs(model)


def clean_resolves_every_do_default_state():
    """faces.clean leaves no z-fighting pair in any DO-1 block's default state (compat families, whose geometry
    comes from Minecraft's vanilla templates, are Task 10's)."""
    root = source.fetch()
    for family in FAMILIES:
        if family.mechanism == "vanilla" or family.name == "AllBrickStair":
            continue
        for block in family.blocks:
            model = faces.clean(faces.cap_ends(assemble.state_model(root, family, block, {})))
            assert faces.overlapping_pairs(model) == [], block


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


def every_tag_lists_real_cubes():
    """Every DO tag gets Hytale blocks, each a plain textured cube (the runtime reads its side texture)."""
    assets = tags.open_assets()
    built = tags.build(assets)
    assert set(built) == set(tags.TAG_GROUPS)
    for tag, blocks in built.items():
        assert blocks, tag
        for block in blocks:
            assert tags.texture(assets, block).startswith("BlockTextures/"), (tag, block)
    assert "Wood_Hardwood_Planks" in built["shingles_support"] and "Soil_Dirt" not in built["shingles_support"]


def every_slot_has_a_tag_and_its_default():
    """Each family's slots name a DO tag, and DO's default material of each component belongs to that tag."""
    built = tags.build(tags.open_assets())
    for family in FAMILIES:
        assert 1 <= len(family.slot_tags) <= 2 and len(family.slot_tags) == len(family.components), family.name
        for tag, component in zip(family.slot_tags, family.components):
            assert tags.default_material(tag, component, built) in built[tag], (family.name, tag, component)


def pair_texture_is_both_materials_side_by_side(tmp):
    assets = tags.open_assets()
    path = pairs.write(tmp, assets, "Wood_Hardwood_Planks", "Rock_Stone_Brick")
    assert path == "Blocks/HyColony/DO/Pairs/Wood_Hardwood_Planks__Rock_Stone_Brick.png"
    image = Image.open(tmp / "Common" / path)
    assert image.size == (64, 32)
    for x0, block in ((0, "Wood_Hardwood_Planks"), (32, "Rock_Stone_Brick")):
        face = assets.image("Common/" + tags.texture(assets, block)).resize((32, 32), Image.NEAREST)
        assert image.getpixel((x0 + 5, 7)) == face.getpixel((5, 7)), block


def main():
    converter_matches_reference_geometry()
    families_cover_the_spec()
    material_follows_component_order()
    rotate_y_turns_north_face_to_east()
    multipart_keeps_matching_parts_only()
    multipart_or_condition_matches_any_branch()
    inner_tilt_follows_a_model_turn()
    uvlock_recomputes_uv_from_rotated_bounds()
    state_model_assembles_real_do_data()
    uvlock_families_assemble_without_error()
    default_props_pick_the_blocks_own_default_state()
    oriented_families_face_minus_z()
    coplanar_overlap_is_removed()
    near_coplanar_backing_moves_behind_its_detail()
    fully_hidden_face_is_dropped()
    pillar_column_is_capped()
    clean_resolves_every_do_default_state()
    every_tag_lists_real_cubes()
    every_slot_has_a_tag_and_its_default()
    with tempfile.TemporaryDirectory() as tmp:
        partial_cache_is_refetched(Path(tmp))
    with tempfile.TemporaryDirectory() as tmp:
        pair_texture_is_both_materials_side_by_side(Path(tmp))
    print("tools/domum check: OK")


if __name__ == "__main__":
    main()
