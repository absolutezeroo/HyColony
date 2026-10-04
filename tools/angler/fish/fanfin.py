"""Fan fins painted the way Hytale's fish fins are: rays fanning out from near the root, each a soft light ridge with
a darker groove beside it, a scalloped edge (a rounded lobe at each ray's tip, a notch between two), and a smooth
dark-root to light-edge gradient with no noise."""

import math
from collections import namedtuple

# root: (x, y) in texels of the quad (may lie outside it); a0, a1: the angular span of the rays in degrees (0 points
# up the texture, 90 to its right, -90 to its left, 180 down); reach(n) -> ray length in texels from the root for n in
# 0..1 across the span; start: the length where the fin leaves the body (its root edge); count: rays; scallop: notch
# depth in texels; dark, mid, light: the three tones; tips: darken each ray's tip (Bluegill, Pike); extra(x, y, n, a):
# an optional colour pass (spots, blotch) given the ray position n and the distance a from root (0) to edge (1).
# sharp: the lobe exponent (1.5 rounded, below 1 pointed spines); bend: how far rays curve back over 30 texels, in
# fractions of the span.
Fan = namedtuple("Fan", "root a0 a1 reach start count scallop dark mid light tips extra sharp bend",
                 defaults=(False, None, 1.5, 0.0))


def mix(a, b, t):
    t = max(0.0, min(1.0, t))
    return tuple(x + (y - x) * t for x, y in zip(a, b))


def fan(fin, s, t, w, h):
    """(colour, alpha) of the fan fin fin at quad coordinates (s, t) in 0..1 of a w x h quad; None outside it."""
    x, y = s * w, t * h
    dx, dy = x - fin.root[0], y - fin.root[1]
    r = math.hypot(dx, dy)
    angle = math.degrees(math.atan2(dx, -dy))
    lo, hi = min(fin.a0, fin.a1), max(fin.a0, fin.a1)
    if not lo <= angle <= hi:
        return None
    n = (angle - fin.a0) / (fin.a1 - fin.a0)
    # rays bend a little backwards as they run out, like Hytale's
    k = (n + fin.bend * r / 30) * fin.count
    frac = k % 1.0
    lobe = (1 - math.sin(math.pi * frac)) ** fin.sharp
    length = fin.reach(n) - fin.scallop * lobe
    if r > length or r < fin.start * 0.6:
        return None
    along = max(0.0, (r - fin.start) / max(1e-6, length - fin.start))
    ridge = math.sin(math.pi * frac) ** 2
    groove = 1 - math.sin(math.pi * frac) ** 0.4
    c = mix(fin.dark, fin.mid, along / 0.55) if along < 0.55 else mix(fin.mid, fin.light, (along - 0.55) / 0.45)
    c = mix(c, fin.light, 0.42 * ridge * (0.35 + 0.65 * along))
    c = mix(c, fin.dark, 0.55 * groove)
    if fin.tips and r > length - 1.6:
        c = mix(c, fin.dark, 0.45)
    if fin.extra:
        c = fin.extra(c, x, y, n, along)
    return tuple(int(round(v)) for v in c), 255
