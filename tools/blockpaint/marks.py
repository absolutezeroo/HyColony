"""The marks events leave (spec 2026-10-03 blockpaint surfaces § 3.6): dents, scratches, scorch, smoke soot, water
stains, blood, rust streaks, chemical discolouring, a magic glow and a repair's patch. Each is an effects.Effect whose
mask the event weighs by its own reach (history.py); the mask here only keeps the mark to where it can be (scratches
in lines, blood in drops round a blot, a glow in veins). Their names are their own, none the plan's
(surface.CATALOGUE), so that the critique tells an event's marks apart (semantics.history_in_place)."""

import math

from bake import LIGHT, value_noise
from effects import Effect, laying, tinting, whole
from vectors import add, dot, scale
from weathering import RUST

SCORCH, SOOT, WATER, BLOOD = (58, 38, 24), (34, 30, 28), (68, 58, 46), (108, 22, 18)
CHEMICAL, GLOW, PATCH = (150, 158, 64), (176, 96, 232), (210, 176, 128)
# How far a dent digs (a fraction of the coats); how its hollow, its wall in shadow and its lip in the light scale
# what shows; how far a wall must turn to or from the light (the in-plane share of bake.LIGHT) to count as either.
DENT_DIG, DENT_SHADE, DENT_WALL, DENT_LIP, DENT_TURN = 0.5, 0.86, 0.68, 1.22, 0.15
# Scratches: parallel lines this many world units apart, this wide, across the event's zone.
SCRATCH_SPACING, SCRATCH_WIDTH = 2.6, 0.45
# A water stain is darker along its border (the tide mark) than inside it.
WATER_RIM, WATER_INSIDE = 0.45, 0.15
# A patch's tint and the shade of its seam.
PATCH_TINT, SEAM_SHADE = 0.35, 0.78
# Blood: drops where a world noise (DROP_SCALE times finer than bake's) passes DROP_LEVEL; the blot's weight elsewhere.
DROP_SCALE, DROP_LEVEL, SPATTER_CORE = 1.4, 0.3, 0.45
# Magic: veins where a world noise is within VEIN_WIDTH of 0; the wash's weight between them.
VEIN_SCALE, VEIN_WIDTH, VEIN_WASH = 0.7, 0.1, 0.5


def dent(island, amounts):
    """A dent: a shallow dig into the coats and a hollow pressed into the surface, whatever shows there: darker, its
    wall turned from the light (bake.LIGHT) in shadow, its wall turned to it catching the light, as a hand-painted dent
    reads."""
    cells = set(whole(amounts))
    island.dig({cell: island.total() * DENT_DIG for cell in cells})
    for i, j in cells:
        t = island.texels[i, j]
        # Each wall at the hollow's rim faces into it, away from the rim's side.
        lit = sum(-dot(add(scale(t.u_dir, di), scale(t.v_dir, dj)), LIGHT)
                  for di, dj in ((1, 0), (-1, 0), (0, 1), (0, -1)) if (i + di, j + dj) not in cells)
        island.press([(i, j)], DENT_LIP if lit > DENT_TURN else DENT_WALL if lit < -DENT_TURN else DENT_SHADE)


def scratch(island, amounts):
    """A scratch: through every coat, down to the substrate."""
    island.dig(dict.fromkeys(whole(amounts), island.total() + 0.05))


def scratch_lines(t, declared, island, i, j):
    """Where a scratch can be: on parallel slanting lines across the surface (world space)."""
    s = (t.point[0] * 0.8 + t.point[1] * 0.6 + t.point[2] * 0.5) / SCRATCH_SPACING
    return 1.0 if abs(s - math.floor(s) - 0.5) < SCRATCH_WIDTH / SCRATCH_SPACING else 0.0


def water_stain(island, amounts):
    """A water stain: a tide mark darker along its border than inside."""
    cells = set(whole(amounts))
    rim = [(i, j) for i, j in cells if {(i + 1, j), (i - 1, j), (i, j + 1), (i, j - 1)} - cells]
    island.tint(rim, WATER, WATER_RIM, layer="visible")
    island.tint(cells - set(rim), WATER, WATER_INSIDE, layer="visible")


def patch(island, amounts):
    """A repair's patch: fresher material in its own shade, a darker seam round it pressed into whatever shows."""
    cells = set(whole(amounts))
    seam = [(i, j) for i, j in cells if {(i + 1, j), (i - 1, j), (i, j + 1), (i, j - 1)} - cells]
    island.tint(cells - set(seam), PATCH, PATCH_TINT, layer="visible")
    island.press(seam, SEAM_SHADE)


def spatter(t, declared, island, i, j):
    """Where blood can be: a blot (SPATTER_CORE: only near the event's source, where its reach is strong) and drops
    flung round it (whole, in blobs of world noise)."""
    return 1.0 if value_noise(scale(t.point, DROP_SCALE)) > DROP_LEVEL else SPATTER_CORE


def veins(t, declared, island, i, j):
    """Where a magic glow can be: veins along the middle lines of a world noise, and a faint wash between them
    (VEIN_WASH: only near the source)."""
    return 1.0 if abs(value_noise(scale(t.point, VEIN_SCALE))) < VEIN_WIDTH else VEIN_WASH


def anywhere(*args):
    return 1.0


MARKS = {
    "dent": Effect("dent", "recent", anywhere, dent, writes=("damage",), scale=1.2, spread=0.5),
    "scratch": Effect("scratch", "recent", scratch_lines, scratch, writes=("damage", "bare"), scale=0.9, spread=0.6),
    "scorch": Effect("scorch", "recent", anywhere, tinting(SCORCH, 0.6), scale=0.7, spread=0.5),
    "smoke_soot": Effect("smoke_soot", "recent", anywhere, laying(SOOT, 0.6), scale=0.6, spread=0.5, soft=True),
    "water_stain": Effect("water_stain", "recent", anywhere, water_stain, scale=0.5, spread=0.4),
    "blood": Effect("blood", "recent", spatter, laying(BLOOD, 0.75), scale=1.6, spread=0.3),
    "rust_streak": Effect("rust_streak", "recent", anywhere, tinting(RUST, 0.5), scale=0.8, spread=0.4),
    "discolour": Effect("discolour", "recent", anywhere, tinting(CHEMICAL, 0.4), scale=0.6, spread=0.6),
    "glow": Effect("glow", "recent", veins, tinting(GLOW, 0.5), scale=0.5, spread=0.3),
    "patch": Effect("patch", "recent", anywhere, patch, scale=0.3, spread=0.0),
}
