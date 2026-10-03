"""Deposits beyond the first batch (spec 2026-10-03 blockpaint surfaces § 3.5.2): mud, grease, oil, soot, ash, sand,
snow, moss and mold, each where it gathers: mud low and splashed, grease and oil where hands and axles work, soot in
hollows high up, ash and snow on what faces the sky, sand low and in hollows, moss and mold where water stays and
light does not reach. Grease writes sticky (dust clings to it); moss and mold read water_retention (cracks hold
water)."""

import maps
from effects import Effect, clamp, laying

MUD, GREASE, OIL, SOOT = (84, 62, 40), (42, 36, 28), (34, 30, 26), (34, 30, 28)
ASH, SAND, SNOW, MOSS, MOLD = (170, 166, 160), (214, 190, 136), (238, 242, 248), (84, 118, 52), (64, 74, 54)


def wet(island, i, j):
    """Whether a crack holds water beside (i, j) (the water_retention signal)."""
    held = island.signal("water_retention")
    return any((i + di, j + dj) in held for di in (-1, 0, 1) for dj in (-1, 0, 1))


def damp(t, island, i, j):
    """Where water stays and light does not reach: hollows, upturned surfaces, beside a crack."""
    return clamp(0.5 * t.occlusion + 0.6 * maps.water(t) + 0.3 * maps.up(t) + (0.4 if wet(island, i, j) else 0.0)
                 - 0.2 * max(0.0, t.light))


EFFECTS = {
    "mud": Effect("mud", "deposit", lambda t, d, isl, i, j: clamp(1.2 * maps.ground(t) + 0.8 * d.ground
                                                                  * (1 - t.height)),
                  laying(MUD, 0.8), scale=1.1, spread=0.8, soft=True),
    "grease": Effect("grease", "deposit", lambda t, d, isl, i, j: clamp(max(d.contact, 0.5 * d.abrasion)
                                                                        * (0.6 + t.occlusion)),
                     laying(GREASE, 0.5), writes=("sticky",), scale=0.7, spread=0.6, soft=True),
    "oil": Effect("oil", "deposit", lambda t, d, isl, i, j: clamp(0.6 * d.contact + 0.6 * maps.ground(t)),
                  laying(OIL, 0.45), scale=0.9, spread=0.8),
    "soot": Effect("soot", "deposit", lambda t, d, isl, i, j: clamp(t.occlusion * 1.5 + 0.5 * t.height - 0.2),
                   laying(SOOT, 0.5), scale=0.6, spread=0.5, soft=True),
    "ash": Effect("ash", "deposit", lambda t, d, isl, i, j: maps.sky(t), laying(ASH, 0.5), scale=0.7, spread=0.7,
                  soft=True),
    "sand": Effect("sand", "deposit", lambda t, d, isl, i, j: clamp(max(maps.ground(t), maps.water(t) * 2)),
                   laying(SAND, 0.6), scale=0.8, spread=0.6, soft=True),
    "snow": Effect("snow", "deposit", lambda t, d, isl, i, j: maps.sky(t), laying(SNOW, 0.9), scale=0.4,
                   spread=0.4, soft=True),
    "moss": Effect("moss", "deposit", lambda t, d, isl, i, j: damp(t, isl, i, j), laying(MOSS, 0.85),
                   reads=("water_retention",), scale=0.7, spread=0.8),
    "mold": Effect("mold", "deposit", lambda t, d, isl, i, j: damp(t, isl, i, j), laying(MOLD, 0.6),
                   reads=("water_retention",), scale=1.6, spread=1.0),
}
