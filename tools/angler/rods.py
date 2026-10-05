"""HyAngler's eight fishing rods, one per Hytale tool tier (the user asked for Adamantite, Mithril and Onyxium besides
the spec's five, 2026-10-04), and its bobber (spec 2026-10-04-hyangler-design § 7.1, plan task 13): their geometry,
held as Hytale's own unused rod is (Common/Items/Tools/Fishing_Rod/FishingRod.blockymodel: an R-Attachment root, the
rod turned 180 degrees about y), each tier's design (rod_designs) on a common hinged skeleton, and their painting by
tools/blockpaint: woods, leathers, cloths, metals, crystals and enamels in the colours of Hytale's icons and of the
tier's bow.

    python tools/angler/rods.py <output folder> [<icons folder>]

writes Rod_<Tier>.blockymodel, Rod_<Tier>.png, Bobber.blockymodel and Bobber.png there, and each rod's icon
Rod_<Tier>.png into the icons folder when given; for Blockbench first, then the plugin's pack (plan task 13):
angler/plugin/src/main/resources/Common/Items/HyAngler and Common/Icons/Items/HyAngler. Python 3.10+ and Pillow.
"""

import json
import math
import sys
from pathlib import Path
from types import SimpleNamespace

from PIL import Image

TOOLS = Path(__file__).resolve().parents[1]
sys.path.append(str(TOOLS / "blockpaint"))
from brushes import cloth, coloured, crystal, family, jitter, painted, wood  # noqa: E402
from catalog import model_texture  # noqa: E402
from composer import Composer  # noqa: E402
from conditions import MAINTAINED, TEMPERATE_OUTDOOR  # noqa: E402
from icons import ICON_SIZE, draw_model, frame, turned  # noqa: E402
from illustration import Illustration  # noqa: E402
from metals import copper, gold, worked  # noqa: E402
from metalwork import metal  # noqa: E402
from models import add, box_shape, corners, empty_shape, node, placed, rotate, unwrap, walk  # noqa: E402
from organic import bone, feathers, leather  # noqa: E402
from pack import rounded, save_png  # noqa: E402
from rod_designs import design  # noqa: E402

SIDES = ("front", "back", "left", "right", "top", "bottom")
TIERS = ("Crude", "Copper", "Iron", "Thorium", "Cobalt", "Adamantite", "Mithril", "Onyxium")
# Turned half a turn about y, as the vanilla rod's Handle is.
HALF_TURN_Y = {"x": 0, "y": 1, "z": 0, "w": 0}
# A rod's icon: laid diagonally, as Hytale's tool icons and HyColony's knight sword (tools/armor WEAPON_VIEWS).
ICON_VIEW = turned(90, 0, -45)


@family("textile")
def binding(rgb):
    """Plant fibre wound tight round the wood: turns across the long side, each lighter on its crown, a dark groove
    between two."""
    def rule(x, y, w, h, side):
        turn = (y if h >= w else x) % 2
        return coloured(rgb, (1.1, 0.82)[turn] + 0.05 * jitter(x * 17 + y * 5, 61))
    return painted(rule)


@family("textile")
def spooled(rgb):
    """Line wound on a spool: fine turns across it, faintly uneven."""
    def rule(x, y, w, h, side):
        return coloured(rgb, (1.06, 0.9)[y % 2] + 0.04 * jitter(x * 11 + y * 3, 62))
    return painted(rule)


@family("noble")
def thorium():
    """Hytale's thorium: deep green, fresh green highlights, blue-green shade (Ingredient_Bar_Thorium's icon)."""
    return worked((73, 154, 79), (33, 91, 64), (16, 50, 56))


@family("noble")
def cobalt():
    """Hytale's cobalt: deep blue, teal highlights, ink shade (Ingredient_Bar_Cobalt's icon)."""
    return worked((49, 109, 141), (33, 54, 90), (23, 30, 63))


@family("noble")
def adamantite():
    """Hytale's adamantite: blood red, crimson highlights, wine shade (Ingredient_Bar_Adamantite's icon)."""
    return worked((151, 20, 30), (82, 5, 25), (52, 0, 24))


@family("noble")
def onyxium():
    """Hytale's onyxium: violet black, dusk-violet highlights, ink shade (Ingredient_Bar_Onyxium's icon)."""
    return worked((73, 55, 97), (30, 19, 43), (13, 5, 21))


LINE = spooled((214, 208, 180))

# The rod in its own frame (y up its length, the reel on its -z side), as a tree a held-item animation bends
# (fishing-hytale.md § 7.5, the bow's Handle > Bow-Top > Bow-Top2): the grip and reel stay fixed, the crank turns about
# the reel's axle (x), and four hinged sections, each a child of the one below, bend a little more each. This skeleton
# is every tier's (the animations and the line's tip tracks rely on it); the rest is the tier's design (rod_designs).
# Every part's name starts with Rod_ so that no animation of the player or of another item (Handle, Chain…) can reach
# it; the root keeps the hand's bone name, R-Attachment, which hangs the model on the hand.
# The hinged sections, from the butt up, each reaching the next one's hinge: (hinge name, hinge height, size, guides
# on it (height, ring)). The tip carries the tip top and the tip's ring, where the line leaves.
SECTIONS = [
    ("Rod_Blank", 16, (4, 33, 4), [(26, 5), (44, 5)]),
    ("Rod_Mid", 49, (3, 34, 3), [(62, 4), (80, 4)]),
    ("Rod_Upper", 83, (2, 17, 2), [(98, 4)]),
    ("Rod_Tip", 100, (2, 17, 2), [(112, 3)]),
]


def rod(tier):
    """The rod of a tier as Hytale nodes, unwrapped, and each part's material by name: an R-Attachment root (isPiece,
    as Hytale's tools: it hangs the model on the hand), the rod turned as the vanilla one, its sections hinged one into
    the next, dressed as the tier's design says."""
    d = design(tier)
    mats = {}

    def part(name, at, size, mat, turn=None):
        mats[name] = mat
        placed = _placed(name, at, at, box_shape(size, SIDES, shading="flat"), [])
        placed["turn"] = turn
        return placed

    parts = [part(*p) for p in d["fixed"]]
    crank = [part(*p) for p in d["crank"]]
    parts.append(_placed("Rod_Reel_Crank", d["crank_pivot"], d["crank_pivot"], empty_shape(), crank))
    chain = None
    for name, hinge, size, guides in reversed(SECTIONS):
        mats[name] = "blank"
        centre = (0, hinge + size[1] / 2, 0)
        children = [p for i, (y, r) in enumerate(guides) for p in _guide(f"{name}_Guide{i}", y, size[0] / 2, r)]
        children += [part(*p) for p in d["deco"].get(name, [])]
        ferrule = d["ferrules"].get(name)
        if ferrule is not None:
            children.append(part(f"{name}_Ferrule", (0, hinge + size[1], 0), *ferrule))
        if name == "Rod_Tip":
            children.append(part("Rod_Tip_Top", (0, hinge + size[1] + 1, 0), *d["tip_top"]))
            children += _ring("Rod_Tip_Ring", hinge + size[1] + 2.5, -2.5, 3)
        if chain is not None:
            children.append(chain)
        chain = _placed(name, (0, hinge, 0), centre, box_shape(size, SIDES, shading="flat"), children)
    parts.append(chain)
    holder = node("Rod", (0, -6, 3), empty_shape(), [_built(p, (0, 0, 0)) for p in parts])
    holder["orientation"] = dict(HALF_TURN_Y)
    root_shape = empty_shape()
    root_shape["settings"]["isPiece"] = True
    root = node("R-Attachment", (-3, 14, -6), root_shape, [holder])
    nodes = [root]
    _check_attached(tier, nodes)
    unwrap(nodes)
    return nodes, mats


def _check_attached(tier, nodes):
    """Stops on a name used twice, or on boxes not all held together face to face (an edge or a corner is not enough):
    a part, or a group of parts, floating free, or a slit of air, shows close to the camera. Turned boxes count by
    their bounding box."""
    boxes = {}
    for n, at, turn in placed(nodes):
        shape = n["shape"]
        if shape["type"] != "box":
            continue
        if n["name"] in boxes:
            raise SystemExit(f"{tier}: {n['name']} used twice")
        offset = tuple(shape["offset"][a] for a in "xyz")
        points = [add(at, rotate(turn, add(offset, c))) for c in corners(shape)]
        boxes[n["name"]] = [(min(p[i] for p in points), max(p[i] for p in points)) for i in range(3)]

    def faces(a, b):
        gaps = [max(a[i][0], b[i][0]) - min(a[i][1], b[i][1]) for i in range(3)]
        return all(g <= 1e-6 for g in gaps) and sum(g < -1e-6 for g in gaps) >= 2

    held, todo = {"Rod_Grip"}, ["Rod_Grip"]
    while todo:
        a = boxes[todo.pop()]
        for name, b in boxes.items():
            if name not in held and faces(a, b):
                held.add(name)
                todo.append(name)
    loose = sorted(set(boxes) - held)
    if loose:
        raise SystemExit(f"{tier}: parts not held to the grip face to face: {', '.join(loose)}")


def _placed(name, pivot, centre, shape, children):
    """A part in the rod's frame: its hinge (pivot), its shape's centre and its children, all in rod coordinates."""
    return {"name": name, "pivot": pivot, "centre": centre, "shape": shape, "children": children}


def _built(part, base):
    """A Hytale node of a part: its position counts from base (its parent's pivot plus shape offset, the rule of
    models.placed), its shape offset from its pivot to its centre; no section is turned at rest."""
    offset = tuple(c - p for c, p in zip(part["centre"], part["pivot"]))
    part["shape"]["offset"] = {"x": offset[0], "y": offset[1], "z": offset[2]}
    position = tuple(p - b for p, b in zip(part["pivot"], base))
    built = node(part["name"], position, part["shape"], [_built(c, part["centre"]) for c in part["children"]])
    if part.get("turn"):
        built["orientation"] = _quaternion(*part["turn"])
    return built


def _quaternion(x, y, z):
    """The orientation of Euler angles in degrees, turned about x, then y, then z: an ornament set at a slant (a fin, a
    shard); only parts no animation targets are turned (a section's ornament still bends with its section)."""
    hx, hy, hz = (math.radians(a) / 2 for a in (x, y, z))
    cx, sx, cy, sy, cz, sz = math.cos(hx), math.sin(hx), math.cos(hy), math.sin(hy), math.cos(hz), math.sin(hz)
    return {"x": sx * cy * cz - cx * sy * sz, "y": cx * sy * cz + sx * cy * sz, "z": cx * cy * sz - sx * sy * cz,
            "w": cx * cy * cz + sx * sy * sz}


def _guide(name, y, blank_half, ring_size):
    """A line guide on the -z side at height y: a short foot against the blank, then a hollow ring right after it,
    flat across the rod, the line running up through its hole."""
    foot = (0, y, -blank_half - 0.5)
    return [_placed(f"{name}_Foot", foot, foot, box_shape((1, 1, 1), SIDES, shading="flat"), [])] + _ring(
        name, y, -blank_half - 1 - ring_size / 2, ring_size)


def _ring(name, y, z, size):
    """A hollow square ring of outer side size, one unit thick, lying flat across the rod at height y, centred at z:
    four bars round a hole of size - 2 the line runs through."""
    half = (size - 1) / 2
    bars = [(f"{name}_A", (0, y, z - half), (size, 1, 1)), (f"{name}_B", (0, y, z + half), (size, 1, 1)),
            (f"{name}_C", (-half, y, z), (1, 1, size - 2)), (f"{name}_D", (half, y, z), (1, 1, size - 2))]
    return [_placed(n, at, at, box_shape(dims, SIDES, shading="flat"), []) for n, at, dims in bars]


def material_of(name, mats):
    """The material of a part by node name: its design's, else a guide's (its foot a fitting)."""
    if name in mats:
        return mats[name]
    if "_Guide" in name or name.startswith("Rod_Tip_Ring"):
        return "fitting" if name.endswith("_Foot") else "guide"
    raise SystemExit(f"no material for {name}")


def look(tiles, seed, mats):
    """A tool that has served, outdoors (spec 2026-10-03 blockpaint surfaces), illustrated and composed."""
    return SimpleNamespace(GROUNDED=False, PICTURES=frozenset(), CONDITION=MAINTAINED, ENVIRONMENT=TEMPERATE_OUTDOOR,
                           SEED=seed, FAMILY={}, material=lambda name, side: material_of(name.split("--")[0], mats),
                           tiles=lambda assets: tiles, ILLUSTRATION=Illustration(), COMPOSER=Composer())


@family("paint_film")
def enamel(rgb):
    """A float's glossy paint: even, a soft highlight band a third down its sides."""
    def rule(x, y, w, h, side):
        k = 1 + 0.03 * jitter(x * 7 + y * 13, 63)
        if side not in ("top", "bottom") and h > 2:
            k += 0.12 * max(0.0, 1 - abs(y / (h - 1) - 0.3) * 4)
        return coloured(rgb, k)
    return painted(rule)


# The bobber: a classic float, white below and red above the waterline, a red cap and a slim antenna with a white tip,
# a brass eye below where the line ties on. About a third of a block tall, to read on the water at 20 blocks.
BOBBER_PARTS = [
    ("Body", (0, 0, 0), (8, 4, 8), "white"),
    ("Top", (0, 4, 0), (8, 4, 8), "red"),
    ("Cap", (0, 7, 0), (5, 2, 5), "red"),
    ("Antenna", (0, 10.5, 0), (2, 5, 2), "red"),
    ("Antenna_Tip", (0, 14, 0), (2, 2, 2), "white"),
    ("Eye", (0, -3.5, 0), (2, 3, 2), "brass"),
]
BOBBER_TILES = {"white": enamel((232, 226, 210)), "red": enamel((196, 52, 40)), "brass": metal("brass")}


def tiles_of(tier):
    """A tier's tiles, by the materials its design names: blank (the rod), grip, fitting (butt, collars, seat,
    ferrules), guide, reel, knob, and its accents. Colours: Hytale's icons (Ingredient_Stick, _Fibre, _Leather_*,
    _Fabric_Scrap_*, _Bar_*) and the tier's bow (Common/Items/Weapons/Bow/<Tier>_Texture.png)."""
    knotty = wood((90, 58, 36), plank=99)
    tiles = {
        "Crude": {"blank": wood((104, 70, 44), plank=99), "grip": binding((126, 142, 58)),
                  "fitting": binding((126, 142, 58)), "guide": bone(), "reel": wood((126, 88, 56), plank=99),
                  "knob": knotty, "leaf": binding((92, 120, 48))},
        "Copper": {"blank": wood((188, 146, 90), plank=99), "grip": leather((134, 86, 43)), "fitting": copper(),
                   "guide": copper(), "reel": copper(), "knob": wood((110, 72, 44), plank=99)},
        "Iron": {"blank": wood((150, 104, 64), plank=99), "grip": leather((110, 71, 33)), "fitting": metal("iron"),
                 "guide": metal("iron"), "reel": metal("iron"), "knob": leather((85, 49, 32)),
                 "rivet": metal("steel")},
        "Thorium": {"blank": crystal((120, 210, 110), (72, 150, 78), (31, 89, 63)), "grip": cloth((96, 52, 120)),
                    "fitting": thorium(), "guide": bone(), "reel": thorium(), "knob": bone(), "bone": bone(),
                    "gem": crystal((170, 240, 140), (90, 190, 90), (40, 110, 60))},
        "Cobalt": {"blank": cobalt(), "grip": cloth((40, 38, 52)), "fitting": metal("steel"),
                   "guide": metal("steel"), "reel": cobalt(), "knob": cloth((40, 38, 52)),
                   "chevron": enamel((232, 232, 226)), "feather": feathers((40, 44, 70))},
        "Adamantite": {"blank": adamantite(), "grip": leather((70, 40, 30)), "fitting": metal("iron"),
                       "guide": metal("iron"), "reel": adamantite(), "knob": leather((70, 40, 30)),
                       "flame": enamel((236, 128, 40)), "ember": enamel((250, 196, 80)), "rivet": metal("steel")},
        "Mithril": {"blank": metal("silver", "polished"), "grip": cloth((236, 232, 222)), "fitting": gold(),
                    "guide": gold(), "reel": metal("silver", "polished"), "knob": gold(), "gold": gold()},
        "Onyxium": {"blank": crystal((73, 55, 97), (30, 19, 43), (13, 5, 21)), "grip": cloth((48, 110, 70)),
                    "fitting": onyxium(), "guide": crystal((222, 190, 255), (160, 100, 230), (90, 46, 150)),
                    "reel": onyxium(), "knob": crystal((222, 190, 255), (160, 100, 230), (90, 46, 150)),
                    "shard": crystal((110, 88, 140), (48, 32, 70), (16, 8, 26)),
                    "glow": crystal((236, 214, 255), (180, 130, 245), (110, 60, 180))},
    }[tier]
    return dict(tiles, line=LINE)


def bobber():
    """The bobber as Hytale nodes, unwrapped: a root at its waterline, and each part's material by name."""
    parts = [node(name, at, box_shape(size, SIDES, shading="flat")) for name, at, size, _ in BOBBER_PARTS]
    nodes = [node("Bobber", (0, 0, 0), empty_shape(), parts)]
    unwrap(nodes)
    return nodes, {name: mat for name, _, _, mat in BOBBER_PARTS}


def write(folder, name, model, tiles, seed, icons=None):
    """Paints a model (nodes, materials) and writes <name>.blockymodel and <name>.png into folder, and its icon
    <name>.png into icons when given; returns the texture's size."""
    nodes, mats = model
    image = model_texture(look(tiles, seed, mats), nodes, None)
    folder.mkdir(parents=True, exist_ok=True)
    (folder / f"{name}.blockymodel").write_text(
        json.dumps(rounded({"lod": "auto", "nodes": nodes}), separators=(",", ":")) + "\n", encoding="utf-8",
        newline="\n")
    save_png(image, folder / f"{name}.png")
    if icons is not None:
        icon = Image.new("RGBA", (ICON_SIZE, ICON_SIZE), (0, 0, 0, 0))
        draw_model(icon, nodes, image, *frame(nodes, ICON_VIEW), ICON_VIEW)
        icons.mkdir(parents=True, exist_ok=True)
        save_png(icon, icons / f"{name}.png")
    return image.size


def main():
    folder = Path(sys.argv[1])
    icons = Path(sys.argv[2]) if len(sys.argv) > 2 else None
    for i, tier in enumerate(TIERS):
        tiles = tiles_of(tier)
        model = rod(tier)
        size = write(folder, f"Rod_{tier}", model, tiles, 40 + i, icons)
        print(f"Rod_{tier}: {size}, {sum(1 for _ in walk(model[0]))} nodes")
    print("Bobber:", write(folder, "Bobber", bobber(), BOBBER_TILES, 50))


if __name__ == "__main__":
    main()
