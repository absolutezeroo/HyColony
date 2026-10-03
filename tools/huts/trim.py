"""Drops the hidden faces of HyColony's hut blocks (tools/common/cull.py) and lays their faces out again (models.unwrap,
a glinting hut's crystals in a band of their own for glint.py's frames), rewriting each hut's <MODEL>.blockymodel in
generate.py's JSON layout. Run it after building or editing a hut, then generate.py to paint the new layout. Nodes
the hut animates keep their faces.

It rewrites the hut's source model and never adds a face back: a dropped face is gone from the model Blockbench
reopens too, so an edit that uncovers one enables it again on its box in Blockbench before saving.

    python tools/huts/trim.py
"""

import json
import sys
from pathlib import Path

TOOLS = Path(__file__).resolve().parents[1]
sys.path.append(str(TOOLS / "common"))
import glint  # noqa: E402
from cull import cull  # noqa: E402
from generate import MODELS, RESOURCES  # noqa: E402
from models import MAX_SIDE, WIDTHS, face_rects, unwrap  # noqa: E402
from pack import write_json  # noqa: E402

# The blockyanim tracks that move a node; a UV offset (a glint) does not.
MOTION = ("position", "orientation", "shapeStretch", "shapeVisible")


def moving(module, nodes):
    """The names of the nodes the hut's animation moves, computed from its module as generate.py writes it (a glint,
    breathing unless BREATHE is False, or animation(nodes)), never read from a blockyanim that may be stale."""
    if hasattr(module, "GLINT"):
        tracks = glint.animation(nodes, module.GLINT, 0, getattr(module, "BREATHE", True))["nodeAnimations"]
    elif hasattr(module, "animation"):
        tracks = module.animation(nodes)["nodeAnimations"]
    else:
        return frozenset()
    return frozenset(name for name, track in tracks.items() if any(track.get(key) for key in MOTION))


def laid_out(nodes, prefix):
    """Lays the model's faces out (models.unwrap); a glinting hut's crystals (nodes named prefix*) in a band of their
    own, at the width whose texture is smallest once glint.frames has added its frames, within MAX_SIDE if it can.
    Returns the texture size, frames included."""
    if prefix is None:
        return unwrap(nodes)

    def apart(name):
        return name.startswith(prefix)

    def grown(width):
        return glint.grown_size(nodes, prefix, unwrap(nodes, (width,), apart))

    widest = max(u1 - u0 for _, u0, _, u1, _ in face_rects(nodes))
    sizes = [(grown(width), width) for width in WIDTHS if width >= widest]
    _, width = min(sizes, key=lambda s: (max(*s[0], MAX_SIDE), s[0][0] * s[0][1]))
    return grown(width)


def main():
    for module in MODELS:
        if not module.MODEL.startswith("Blocks/"):
            continue
        model = RESOURCES / "Common" / (module.MODEL + ".blockymodel")
        data = json.loads(model.read_text(encoding="utf-8"))
        dropped = cull(data["nodes"], moving(module, data["nodes"]))
        size = laid_out(data["nodes"], getattr(module, "GLINT", None))
        write_json(model, data)
        print(f"{model.stem}: {len(dropped)} hidden faces dropped, {size[0]} x {size[1]}")


if __name__ == "__main__":
    main()
