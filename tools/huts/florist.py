"""The flower shop's hut block (spec 2026-10-02 hut models, § quarries, plantation and florist; decoration until the
florist's job is ported), built by a one-off script: a two-tier stand of potted tulips, daisies, lavender and roses, a
pair of shears on its low shelf, a bucket of cut flowers and an open sack of compost (MineColonies' florist grows its
flowers with compost), its rim rolled in a ring, a trowel stuck in it. Each flower is a stem and one or two boxes, the
detail painted on them; the flowers sway in a light breeze (animation)."""

import math

from brushes import cloth, coloured, metal, painted, stone, terracotta, wood
from conditions import TEMPERATE_OUTDOOR, USED
from materials import BUCKET_INSIDE, BUCKET_WOOD, leaf, staves
from models import walk
from motion import blockyanim, leaning, track, wave

MODEL = "Blocks/HyColony/Huts/Florist"
ICON = "Hut_Florist"
PICTURES = frozenset()
# Painted in layers (spec 2026-10-03 blockpaint surfaces): a flower stand in use, outdoors.
CONDITION, ENVIRONMENT, SEED = USED, TEMPERATE_OUTDOOR, 11
# The breeze: each flower (a node Stem_*, pivoting at its base, its head its child) nods to and fro NOD_DEGREES and
# sways side to side SWAY_DEGREES over the loop of LOOP_TICKS (1/60 s), all at the same pace. The flowers of a pot (or
# of the bucket) move nearly as one, a hair apart, so that they never swing into each other; the pots are out of step.
# The cut flowers, packed in the bucket, move BUCKET times as far.
LOOP_TICKS, NOD_DEGREES, SWAY_DEGREES = 360, 2.5, 3.5
POT_PHASE, FLOWER_PHASE, BUCKET = 0.29, 0.03, 0.5
PETALS = {"Red": (204, 62, 58), "Yellow": (242, 202, 64), "Blue": (86, 124, 222), "Purple": (152, 92, 204),
          "White": (238, 234, 226), "Pink": (238, 142, 182), "Orange": (242, 142, 62)}
FAMILY = {"grip": "paint_film", "lavender": "plant",
          **{f"{kind}_{c.lower()}": "plant" for kind in ("tulip", "daisy", "rose") for c in PETALS}}
# Node name prefix -> material, the first that matches.
PREFIXES = (("Leg", "stand"), ("Tier", "stand"), ("Pot", "pot"), ("Stem", "leaf"), ("Leaf", "leaf"),
            ("Spike", "lavender"), ("Shears_Blade", "iron"), ("Shears_Grip", "grip"), ("Bucket", "bucket"),
            ("Band", "iron"), ("Sack", "sack"), ("Trowel_Blade", "iron"), ("Trowel", "handle"))


def material(name, side):
    """Soil in the pots' tops, the bucket's inside, compost in the sack's mouth, a flower's kind and colour from its
    name (Tulip_Red_…), then by name."""
    if name.startswith("Pot") and side == "top":
        return "soil"
    if (name, side) in BUCKET_INSIDE:
        return "inside"
    if name == "Sack" and side == "top":
        return "compost"
    if name.startswith(("Tulip", "Daisy", "Rose")):
        kind, colour = name.split("_")[:2]
        return f"{kind.lower()}_{colour.lower()}"
    return next(m for prefix, m in PREFIXES if name.startswith(prefix))


def animation(nodes):
    """The looping blockyanim: every flower sways in a light breeze, three nods and two sways a loop; a pot's flowers
    (Stem_H11, Stem_H12: pot H1; Stem_C*: the bucket) move nearly with their pot's phase, FLOWER_PHASE apart."""
    stems = [n["name"] for n in walk(nodes) if n["name"].startswith("Stem") and n.get("children")]
    pots = list(dict.fromkeys(pot(name) for name in stems))
    tracks = {}
    for i, name in enumerate(stems):
        phase = pots.index(pot(name)) * POT_PHASE + i * FLOWER_PHASE
        reach = BUCKET if pot(name) == "C" else 1
        tracks[name] = track(leaning(wave(NOD_DEGREES * reach, 3, LOOP_TICKS, phase), lambda t: 0,
                                     wave(SWAY_DEGREES * reach, 2, LOOP_TICKS, phase)), LOOP_TICKS)
    return blockyanim(tracks, LOOP_TICKS)


def pot(stem):
    """The pot a flower stands in, from its stem's name: Stem_H12 -> H1, Stem_C3 -> C (the bucket)."""
    tag = stem.split("_")[1]
    return "C" if tag.startswith("C") else tag[:2]


def tiles(assets):
    """Material -> its 32 px tile or brush."""
    return {
        "stand": wood((134, 96, 60), plank=6), "pot": terracotta((178, 98, 62)),
        "soil": stone((80, 58, 40), chunk=(2, 2)),
        "leaf": leaf((86, 148, 58)), "bucket": staves(BUCKET_WOOD), "inside": staves((70, 48, 30)),
        "iron": metal((150, 154, 162)), "grip": painted(lambda x, y, w, h, side: coloured((176, 52, 48), 1)),
        "sack": cloth((196, 170, 122)), "compost": stone((72, 52, 36), chunk=(2, 2)),
        "handle": wood((150, 108, 66), plank=99),
        "lavender": painted(lambda x, y, w, h, side: coloured(PETALS["Purple"], 0.72 if y % 2 else 1.08)),
        **{f"tulip_{c.lower()}": tulip(rgb) for c, rgb in PETALS.items()},
        **{f"daisy_{c.lower()}": daisy(rgb) for c, rgb in PETALS.items()},
        **{f"rose_{c.lower()}": rose(rgb) for c, rgb in PETALS.items()},
    }


def tulip(rgb):
    """A tulip's cup: petal tips along the top row of its sides (light and dark in turn), a dark heart in its top."""
    def rule(x, y, w, h, side):
        if side == "top":
            return coloured(rgb, 0.45 if (x, y) == (w // 2, h // 2) else 0.95)
        if y == 0:
            return coloured(rgb, 1.12 if x % 2 == 0 else 0.8)
        return coloured(rgb, 1.02 - 0.06 * y)
    return painted(rule)


def daisy(rgb):
    """A daisy's petals: lighter towards their tips, a yellow heart in the middle of the top, a darker underside."""
    def rule(x, y, w, h, side):
        if side == "top" and abs(x - (w - 1) / 2) < 1 and abs(y - (h - 1) / 2) < 1:
            return coloured((222, 172, 46), 1.05 if (x + y) % 2 else 0.9)
        if side == "bottom":
            return coloured(rgb, 0.78)
        return coloured(rgb, 0.92 + 0.12 * math.hypot(x - (w - 1) / 2, y - (h - 1) / 2) / max(w, h))
    return painted(rule)


def rose(rgb):
    """A rose: petal edges in darker arcs, a dark swirl at the middle of the top."""
    def rule(x, y, w, h, side):
        if side == "top":
            r = math.hypot(x - (w - 1) / 2, y - (h - 1) / 2)
            return coloured(rgb, 0.62 if r < 0.8 else 0.85 if round(r * 2) % 2 else 1.05)
        return coloured(rgb, 1.08 if y == 0 else 0.88 if (x + y) % 3 == 0 else 1)
    return painted(rule)
