"""Degradations beyond the first batch (spec 2026-10-03 blockpaint surfaces § 3.5.2): scuffs, scratches, cracks and
craquelure, the ageing of paint (fading, chalking, peeling, flaking, blistering), oxidation and verdigris, burn and
fraying, and the forms of rust (spots, deep rust, pits, streaks). Each builds its mask from the maps and the part's
declared maps; signals tie them: rust writes rust, which its streaks read; cracks write water_retention."""

import math

import maps
from brushes import smooth2
from effects import Effect, clamp, tinting, whole
from marks import scratch, scratch_lines
from weathering import RUST

DEEP_RUST, PIT = (92, 44, 26), (52, 30, 22)
FADED, CHALK, OXIDE, VERDIGRIS = (196, 192, 186), (232, 228, 222), (88, 80, 70), (92, 164, 134)
BURNT, FRAYED = (34, 26, 22), (232, 224, 206)
# How far a streak of rust runs down below its rust (texels), and how much it stains at its start.
STREAK_RUN, STREAK_STAIN = 5, 0.4


def digging(fraction, extra=0.0):
    """An action digging every whole texel to fraction of the coats, plus extra into the substrate."""
    def act(island, amounts):
        island.dig(dict.fromkeys(whole(amounts), island.total() * fraction + extra))
    return act


def fine_lines(t, declared, island, i, j):
    """Micro scratches: fine lines, closer together than scratches."""
    s = (t.point[0] * 0.5 - t.point[1] * 0.9 + t.point[2] * 0.3) / 1.6
    return 1.0 if abs(s - math.floor(s) - 0.5) < 0.18 else 0.0


def crack_lines(t, scale, width):
    """Cracks: along the middle lines of a slow noise, wandering, one texel wide."""
    p = t.point
    return 1.0 if abs(smooth2((p[0] + p[2]) / scale, p[1] / scale, 101)) < width else 0.0


def craquelure_net(t):
    """Craquelure: a fine net of cells about three units across."""
    p = t.point
    u, v = (p[0] + p[2]) / 3.0, p[1] / 3.0
    return 1.0 if min(u - math.floor(u), v - math.floor(v)) < 0.18 else 0.0


def peel(island, amounts):
    """Peeling: patches of coat lifted off down to the substrate, their rims darker where the curls cast a shadow."""
    cells = set(whole(amounts))
    island.dig(dict.fromkeys(cells, island.total()))
    rim = [(i, j) for i, j in cells if {(i + 1, j), (i - 1, j), (i, j + 1), (i, j - 1)} - cells]
    island.shade(rim, 0.82)


def blister(island, amounts):
    """Blisters: small swellings of the paint, lighter in their middle, a shade under them."""
    cells = set(whole(amounts))
    island.tint(cells, (255, 250, 240), 0.12, layer="visible")
    below = [(i, j + 1) for i, j in cells if (i, j + 1) in island.texels and (i, j + 1) not in cells]
    island.tint(below, (40, 34, 30), 0.25, layer="visible")


def streak(island, amounts):
    """Rust streaks: rust run down below each rusty texel, fading as it goes."""
    rusty = island.signal("rust")
    for i, j in whole(amounts):
        for k in range(1, STREAK_RUN + 1):
            if (i, j - k) in rusty:
                island.tint([(i, j)], RUST, STREAK_STAIN * (1 - k / (STREAK_RUN + 1)), layer="visible")
                break


def break_through(island, amounts):
    """Rust spots: rust breaking through every coat of iron in small colonies."""
    cells = whole(amounts)
    island.dig(dict.fromkeys(cells, island.total()))
    island.tint(cells, RUST, 0.8)


def below_rust(t, declared, island, i, j):
    """Where rust can run: on side faces (v down), under rust within STREAK_RUN texels."""
    if abs(t.normal[1]) > 0.5:
        return 0.0
    rusty = island.signal("rust")
    return 1.0 if any((i, j - k) in rusty for k in range(1, STREAK_RUN + 1)) else 0.0


def bare_ferrous(t, declared, island, i, j):
    return 1.0 if island.bare("ferrous", i, j) else 0.0


EFFECTS = {
    "scuffs": Effect("scuffs", "degrade", lambda t, d, isl, i, j: clamp(max(d.abrasion, 0.7 * d.contact,
                                                                              0.6 * maps.ground(t),
                                                                              0.4 * maps.exposure(t))),
                     digging(0.4), scale=0.5, spread=0.7),
    "scratches": Effect("scratches", "degrade", lambda t, d, isl, i, j: scratch_lines(t, d, isl, i, j)
                        * clamp(max(d.contact, d.impact, 0.6 * maps.exposure(t))), scratch,
                        writes=("damage", "bare"), scale=0.9, spread=0.6),
    "micro_scratches": Effect("micro_scratches", "degrade", lambda t, d, isl, i, j: fine_lines(t, d, isl, i, j)
                              * clamp(max(d.contact, d.abrasion, 0.5)), digging(0.3), scale=0.7, spread=0.6),
    "cracks": Effect("cracks", "degrade", lambda t, d, isl, i, j: crack_lines(t, 9.0, 0.05)
                     * clamp(0.4 + 0.6 * maps.exposure(t)), tinting((40, 34, 30), 0.6),
                     writes=("water_retention",), scale=0.6, spread=0.5),
    "craquelure": Effect("craquelure", "degrade", lambda t, d, isl, i, j: craquelure_net(t),
                         tinting((60, 52, 46), 0.35), scale=0.4, spread=0.6),
    "fading": Effect("fading", "degrade", lambda t, d, isl, i, j: clamp(maps.sky(t) + 0.4 * maps.exposure(t)),
                     tinting(FADED, 0.35), scale=0.3, spread=0.4),
    "chalking": Effect("chalking", "degrade", lambda t, d, isl, i, j: clamp(maps.sky(t) + 0.3),
                       tinting(CHALK, 0.25), scale=0.4, spread=0.5),
    "peeling": Effect("peeling", "degrade", lambda t, d, isl, i, j: clamp(0.5 + 0.5 * maps.exposure(t)), peel,
                      writes=("bare",), scale=0.45, spread=1.0),
    "flaking": Effect("flaking", "degrade", lambda t, d, isl, i, j: clamp(0.4 + 0.6 * maps.exposure(t)),
                      digging(1.0), writes=("bare",), scale=1.4, spread=1.0),
    "blistering": Effect("blistering", "degrade", lambda t, d, isl, i, j: clamp(0.5 + 0.5 * maps.sky(t)), blister,
                         scale=1.6, spread=1.0),
    "oxidation": Effect("oxidation", "degrade", lambda t, d, isl, i, j: clamp(0.4 + 0.6 * t.occlusion),
                        tinting(OXIDE, 0.3), scale=0.4, spread=0.5),
    "verdigris": Effect("verdigris", "degrade", lambda t, d, isl, i, j: clamp(maps.water(t) * 2 + 0.6 * t.occlusion
                                                                              + 0.3 * maps.sky(t)),
                        tinting(VERDIGRIS, 0.6), scale=0.6, spread=0.7),
    "burn": Effect("burn", "degrade", lambda t, d, isl, i, j: clamp(0.3 + 0.7 * (1 - t.height)),
                   tinting(BURNT, 0.7), scale=0.5, spread=0.8),
    "fraying": Effect("fraying", "degrade", lambda t, d, isl, i, j: clamp(1 - t.rim / 2), tinting(FRAYED, 0.35),
                      scale=1.2, spread=0.8),
    "rust_spots": Effect("rust_spots", "degrade", lambda t, d, isl, i, j: (isl.family == "ferrous")
                         * clamp(0.5 + 0.5 * maps.exposure(t)), break_through, writes=("rust", "bare"), scale=1.6,
                         spread=1.0),
    "deep_rust": Effect("deep_rust", "degrade", lambda t, d, isl, i, j: bare_ferrous(t, d, isl, i, j)
                        * clamp(0.3 + 0.7 * t.occlusion + 0.4 * maps.water(t)), tinting(DEEP_RUST, 0.8, "substrate"),
                        reads=("bare",), writes=("rust",), scale=0.5, spread=0.7),
    "rust_pits": Effect("rust_pits", "degrade", lambda t, d, isl, i, j: 1.0 if (i, j) in isl.signal("rust") else 0.0,
                        tinting(PIT, 0.7, "substrate"), reads=("rust",), scale=1.8, spread=1.0),
    "rust_streaks": Effect("rust_streaks", "degrade", below_rust, streak, reads=("rust",), scale=0.6, spread=0.3),
}
