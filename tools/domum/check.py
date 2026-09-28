"""Self-check of the converter, offline: python tools/domum/check.py (exits non-zero on failure)."""

from PIL import Image

from convert import Converter, check_atlas, material

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


def main():
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
    assert material(MODEL["textures"], "#1") == "dark" and material(MODEL["textures"], "#centre") == "light"
    check_atlas("check", nodes, atlas)
    assert atlas.width & (atlas.width - 1) == 0 and atlas.height & (atlas.height - 1) == 0
    print("tools/domum check: OK")


if __name__ == "__main__":
    main()
