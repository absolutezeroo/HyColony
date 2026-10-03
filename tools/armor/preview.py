"""Dresses Hytale's player model in the plate armor for Blockbench (spec 2026-10-02 plate armor, § 3): each piece's bone
nodes snapped to the player's bones of the same name (the likeliest rule of the client, a few units off at most), the
body's boxes kept in grey, and one texture for all (the pieces' textures stacked, then the grey): a Blockbench project
in a Hytale format shows only part of several textures. Reads the generated pieces; writes nothing in the repository.

    python tools/armor/preview.py <out dir>   (then open <out dir>/Preview.blockymodel, its texture Preview.png)
"""

import copy
import json
import sys
from pathlib import Path

from PIL import Image

TOOLS = Path(__file__).resolve().parents[1]
sys.path.append(str(TOOLS / "blockpaint"))
from generate import FOLDER, RESOURCES  # noqa: E402
from models import add, empty_shape, multiply, node, placed, rotate, walk  # noqa: E402
from pack import GRADLE_ASSETS, Assets, rounded  # noqa: E402
from pieces import PIECES  # noqa: E402

BODY = (138, 131, 120, 255)


def worn(nodes, bones, piece, origin=(0, 0, 0), rotation=(0, 0, 0, 1)):
    """The piece's boxes in world space, flat, named <piece>__<name>: a bone node snapped to the player's bone of its
    name, any other node placed as Hytale places children (its shape offset applying to its children)."""
    out = []
    for n in nodes:
        p, o, off = n["position"], n["orientation"], n["shape"]["offset"]
        at = add(origin, rotate(rotation, (p["x"], p["y"], p["z"])))
        rot = multiply(rotation, (o["x"], o["y"], o["z"], o["w"]))
        if n["shape"]["type"] == "none":
            at, rot = bones.get(n["name"], (at, rot))
        else:
            out.append(dict(n, name=f"{piece}__{n['name']}", position=dict(zip("xyz", at)),
                            orientation=dict(zip("xyzw", rot)), children=[], shape=copy.deepcopy(n["shape"])))
        out += worn(n.get("children", []), bones, piece, add(at, rotate(rot, (off["x"], off["y"], off["z"]))), rot)
    return out


def main(out):
    assets = Assets(GRADLE_ASSETS)
    player = assets.json("Common/Characters/Player.blockymodel")["nodes"]
    bones = {n["name"]: (pos, rot) for n, pos, rot in placed(player)}
    boxes, images, bands, y = [], {}, {}, 0
    for piece in PIECES:
        model = RESOURCES / "Common" / (FOLDER + piece + ".blockymodel")
        images[piece] = Image.open(model.with_suffix(".png"))
        bands[piece], y = y, y + images[piece].height
        boxes += worn(json.loads(model.read_text(encoding="utf-8"))["nodes"], bones, piece)
    bands["Body"] = y
    boxes += [dict(n, name=f"Body__{n['name']}", position=dict(zip("xyz", pos)), orientation=dict(zip("xyzw", rot)),
                   children=[], shape=copy.deepcopy(n["shape"]))
              for n, pos, rot in placed(player) if n["shape"]["type"] == "box"]
    for box in boxes:
        for face in box["shape"].get("textureLayout", {}).values():
            face["offset"] = {"x": face["offset"]["x"], "y": face["offset"]["y"] + bands[box["name"].split("__")[0]]}
    atlas = Image.new("RGBA", (288, y + 128), (0, 0, 0, 0))
    for piece, image in images.items():
        atlas.paste(image, (0, bands[piece]))
    atlas.paste(BODY, (0, y, 288, y + 128))
    out.mkdir(parents=True, exist_ok=True)
    atlas.save(out / "Preview.png")
    root = node("Preview", (0, 0, 0), empty_shape(), boxes)
    for number, n in enumerate(walk([root]), 1):
        n["id"] = str(number)
    (out / "Preview.blockymodel").write_text(json.dumps(rounded({"format": "character", "lod": "auto",
                                                                  "nodes": [root]})), encoding="utf-8")
    print("Preview.png", atlas.size)


if __name__ == "__main__":
    main(Path(sys.argv[1]))
