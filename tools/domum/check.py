"""Self-check of the converter and the Domum Ornamentum family table, offline: python tools/domum/check.py (exits
non-zero on failure)."""

import tempfile
from pathlib import Path

import assemble
import convert
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


def main():
    converter_matches_reference_geometry()
    families_cover_the_spec()
    material_follows_component_order()
    rotate_y_turns_north_face_to_east()
    multipart_keeps_matching_parts_only()
    with tempfile.TemporaryDirectory() as tmp:
        partial_cache_is_refetched(Path(tmp))
    print("tools/domum check: OK")


if __name__ == "__main__":
    main()
