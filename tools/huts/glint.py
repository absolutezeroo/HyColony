"""Living crystals (spec 2026-10-02 hut models, § build tool): the gems of a held model breathe (shapeStretch, smooth,
as Hytale's Blocks/Animations/Candle/Candle_Burn) and a glint sweeps across them now and then (a shapeUvOffset
flipbook, as Hytale's VFX/Fire/Fire: frames painted below the texture; an offset of -d reads the island d pixels
lower). Times are in 1/60 s (Blockbench's Hytale plugin exports blockyanim keyframes at 60 per second)."""

import math

from PIL import Image

from brushes import mix
from models import walk
from paint import islands

DURATION = 180
# The breath's peak stretch: at most 1.06, as a 4 x 4 gem turned 45 degrees reaches 2.83 of the 3 half width of the
# build tool's bezel (3 / 2.83 = 1.0607): any more and its edges pass through it.
PULSE = 1.06
GLINT_FRAMES = 3
# The glint passes from GLINT_START, each frame held GLINT_HOLD then left in one more tick: shown 6/60 s by a client
# that steps from key to key (as Hytale's fire and torch suggest), sliding at most one tick if it interpolates.
GLINT_START, GLINT_HOLD = 120, 5
GLINT_LIGHT = (236, 252, 255)
# Half width of the glint's band across a face, in diagonal units (corner to corner is 2).
GLINT_BAND = 0.45


def frames(image, nodes, prefix):
    """(image, step): image grown with GLINT_FRAMES copies of the islands of the nodes named prefix*, frame k
    step * k pixels below the islands and its glint k / (GLINT_FRAMES + 1) of the way across each face."""
    rects = [(u, v, w, h) for name, _, u, v, w, h in islands(nodes) if name.startswith(prefix)]
    if not rects:
        raise SystemExit(f"glint: no node named {prefix}*")
    top, bottom = min(r[1] for r in rects), max(r[1] + r[3] for r in rects)
    step = max(image.height + 1 - top, bottom - top + 2)
    out = Image.new("RGBA", (image.width, 32 * math.ceil((bottom + 1 + GLINT_FRAMES * step) / 32)), (0, 0, 0, 0))
    out.paste(image)
    for k in range(1, GLINT_FRAMES + 1):
        for u, v, w, h in rects:
            # The island with its bled border, then the glint on the island itself.
            out.paste(image.crop((u - 1, v - 1, u + w + 1, v + h + 1)), (u - 1, v - 1 + k * step))
            glint(out, (u, v + k * step, w, h), k / (GLINT_FRAMES + 1))
    return out, step


def glint(image, rect, t):
    """Lightens the face at rect along a band across its diagonal, t (0..1) of the way from its top left."""
    u, v, w, h = rect
    pixels = image.load()
    for x in range(w):
        for y in range(h):
            distance = abs((x + 0.5) / w + (y + 0.5) / h - 2 * t)
            if distance < GLINT_BAND:
                r, g, b, a = pixels[u + x, v + y]
                pixels[u + x, v + y] = (*mix((r, g, b), GLINT_LIGHT, 0.8 * (1 - distance / GLINT_BAND)), a)


def animation(nodes, prefix, step):
    """The blockyanim of the nodes named prefix*: a breath every DURATION, the glint frames step pixels apart."""
    names = sorted(n["name"] for n in walk(nodes) if n["name"].startswith(prefix))
    return {"formatVersion": 1, "duration": DURATION, "holdLastKeyframe": False, "nodeAnimations": {
        name: {"position": [], "orientation": [], "shapeStretch": breath(), "shapeVisible": [],
               "shapeUvOffset": flipbook(step)} for name in names}}


def breath():
    """Stretch keyframes: 1, PULSE halfway, back to 1."""
    def key(time, k):
        return {"time": time, "delta": {"x": k, "y": k, "z": k}, "interpolationType": "smooth"}

    return [key(0, 1), key(DURATION // 2, PULSE), key(DURATION, 1)]


def flipbook(step):
    """UV offset keyframes: the plain islands until GLINT_START, each glint frame held GLINT_HOLD, the plain islands
    again until DURATION. Every frame has a key at both ends, so an interpolating client slides only in the tick
    between two frames."""
    offsets = [0] + [-k * step for k in range(1, GLINT_FRAMES + 1)] + [0]
    keys = [(0, 0), (GLINT_START - 1, 0)]
    for k, offset in enumerate(offsets[1:]):
        start = GLINT_START + k * (GLINT_HOLD + 1)
        keys += [(start, offset), (start + GLINT_HOLD, offset)]
    keys.append((DURATION, 0))
    return [{"time": time, "delta": {"x": 0, "y": offset}} for time, offset in keys]
