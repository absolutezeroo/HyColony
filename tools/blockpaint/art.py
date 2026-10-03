"""The art pass (spec 2026-10-03 blockpaint surfaces, art): what makes a layered surface read like Hytale's rather
than procedural noise. A model's detail budget (low, medium, high: the 50th, 75th and 90th centile of Hytale's
furniture, docs/research/blockpaint-surfaces.md § 2.1), raised on its focal parts and lowered elsewhere; the calm of
its substrates, pulled towards their local mean until they keep that budget; its rest zones, where effects die down;
how many effects each island shows."""

from collections import namedtuple

from PIL import Image

import critique

# A model's art: its detail budget and whether its effects die down away from borders, contact, impact and focus.
Art = namedtuple("Art", "detail rest", defaults=("medium", True))
DETAILS = ("low", "medium", "high")
# What an island may show at each detail budget, per kind: mean step and share of jumps (the share of micro is
# critique.py's to tell: calming hardly moves it).
Target = namedtuple("Target", "step jumps")
TARGETS = {
    "low": {"warm": Target(6.1, 0.157), "grey": Target(15.3, 0.45), "colour": Target(9.2, 0.255)},
    "medium": {"warm": Target(9.3, 0.283), "grey": Target(20.0, 0.524), "colour": Target(12.8, 0.394)},
    "high": {"warm": Target(15.3, 0.416), "grey": Target(21.9, 0.563), "colour": Target(20.3, 0.528)},
}
# Each calming step pulls every texel this far towards the mean of its look-alike neighbours (art.alike), at most
# CALM_STEPS times; a texel with fewer than ALIKE look-alike neighbours is a speck of noise (the end of a seam has one).
CALM_PULL, CALM_STEPS, ALIKE = 0.35, 16, 1
# Two texels look alike when they differ by at most this, summed over r, g, b: across a plank of brushes.wood the grain
# differs by 20 (median) to 42 (90th centile), across its seam by 42 (10th centile) to 84 (median). Under the seam's
# least: a little grain stays rather than seams fading.
ALIKE_STEP = 30
# Away from borders, contact, impact and focus, an effect's mask is worth this much (rest zones).
REST = 0.5
# How many effects an island shows at each detail budget, and how much of a deposit's zone may lie under an earlier,
# more useful deposit before it is left out (two deposits fighting over one place).
VISIBLE = {"low": 2, "medium": 3, "high": 4}
FIGHT = 0.6


def detail_of(part, art, focus):
    """The detail budget of part: the model's, one step up on a focal part and one step down elsewhere when the model
    has focal parts."""
    k = DETAILS.index(art.detail)
    if focus:
        k += 1 if part in focus else -1
    return DETAILS[min(len(DETAILS) - 1, max(0, k))]


def calm(image, detail):
    """image pulled towards the mean of its look-alike neighbours (alike), CALM_PULL at a time, until it keeps the
    target of its kind at detail (TARGETS) or CALM_STEPS have passed; unchanged when it already does, when any texel is
    transparent or when a side is under critique.MIN_SIDE texels. Alpha stays."""
    if min(image.size) < critique.MIN_SIDE:
        return image
    groups = alike(image)
    for _ in range(CALM_STEPS):
        m = critique.measure(image)
        if m is None:
            return image
        target = TARGETS[detail][m.kind]
        # Evening out lowers the step and the jumps; the share of micro hardly moves (noise has no meso nor macro to
        # gain): that is for the macro variation and the brush's structure, and critique.py tells it.
        if m.step <= target.step and m.jumps <= target.jumps:
            return image
        image = Image.blend(image, averaged(image, groups), CALM_PULL)
    return image


def alike(image):
    """{(x, y): (the texels of its 3 x 3 box it may be averaged with, whether it is a speck)}, read once from image:
    those that look like it (within ALIKE_STEP summed over r, g, b), so that grain evens out while a plank's seam, a
    stave or a hoop, far from its sides but alike along its line (a line's texel has two look-alike neighbours),
    keeps its edge however many times the image is calmed. A texel with fewer than ALIKE look-alike neighbours is a
    speck: it takes the median of its whole box (averaged), which wipes it out without pulling a plank towards the
    seam beside it."""
    w, h = image.size
    source = image.load()
    groups = {}
    for x in range(w):
        for y in range(h):
            here = source[x, y]
            cells = [(i, j) for i in (x - 1, x, x + 1) for j in (y - 1, y, y + 1) if 0 <= i < w and 0 <= j < h]
            near = [c for c in cells if sum(abs(source[c][k] - here[k]) for k in range(3)) <= ALIKE_STEP]
            speck = len(near) - 1 < ALIKE
            groups[x, y] = (cells if speck else near, speck)
    return groups


def averaged(image, groups):
    """image with each texel the mean of its group (alike), or its median for a speck; alpha kept."""
    source = image.load()
    out = image.copy()
    pixels = out.load()
    for (x, y), (group, speck) in groups.items():
        if speck:
            colour = tuple(sorted(source[c][k] for c in group)[len(group) // 2] for k in range(3))
        else:
            colour = tuple(round(sum(source[c][k] for c in group) / len(group)) for k in range(3))
        pixels[x, y] = (*colour, source[x, y][3])
    return out


def rest(t, declared):
    """The weight of an effect's mask at texel t: whole near an open border, contact, impact or focus, REST far from
    them all."""
    near = max(0.0, 1.0 - t.rim / 3, declared.contact, declared.impact, declared.focus)
    return REST + (1 - REST) * min(1.0, near)
