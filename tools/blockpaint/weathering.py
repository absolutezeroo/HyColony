"""Effects of the first batch (spec 2026-10-03 blockpaint surfaces, degradations and deposits): edge wear, chips and
rust that degrade, dirt, dust and grime that lie on top. Each builds its mask from the maps (maps.py, the part's
declared maps), never from chance. Rust writes rust (its pits and streaks read it), dust reads sticky (it clings to
grease)."""

import maps
from effects import Effect, clamp, laying, whole

# Rust's colour, which the other forms of rust and the marks of history share.
RUST, DIRT, DUST, GRIME = (142, 70, 34), (92, 72, 50), (190, 180, 160), (60, 48, 40)
# How much of each deposit's colour covers what is under it where it lies whole.
DIRT_COVER, DUST_COVER, GRIME_COVER = 0.7, 0.4, 0.45
# How much a worn edge lightens a substrate without coats (polished by use), and rust's cover of bare iron.
POLISH, RUST_COVER = 0.08, 0.75
# How far a chip digs into the substrate past every coat, how much more rust takes beside damage and dust beside
# grease.
CHIP_BITE, DAMAGE_RUST, STICKY_DUST = 0.4, 0.3, 0.6


def wear_edges(island, amounts):
    """Wears the edges: through every coat to the substrate, or, without coats, polishes the substrate lighter."""
    full = whole(amounts)
    if island.coats:
        island.dig(dict.fromkeys(full, island.total()))
    else:
        island.shade(full, 1 + POLISH)


def chip(island, amounts):
    """Chips off every coat and bites a little into the substrate."""
    island.dig(dict.fromkeys(whole(amounts), island.total() + CHIP_BITE))


def rust_over(island, amounts):
    """Rusts the bare iron."""
    island.tint(whole(amounts), RUST, RUST_COVER)


def rust_mask(t, island, i, j):
    """Where rust takes: bare iron only, the more so where exposed and where chips or scratches damaged it (the
    damage signal, Rust Around Damage)."""
    if not island.bare("ferrous", i, j):
        return 0.0
    damaged = island.signal("damage")
    near = any((i + di, j + dj) in damaged for di in (-1, 0, 1) for dj in (-1, 0, 1))
    return clamp(0.5 + 0.5 * maps.exposure(t) + (DAMAGE_RUST if near else 0.0))


def dust_mask(t, d, island, i, j):
    """Where dust settles: on what faces up, open to the sky or under cover (a worktop under shelves gathers it too),
    less where hands brush it off, and it clings to grease (the sticky signal) whichever way the face turns."""
    sticky = island.signal("sticky")
    clings = any((i + di, j + dj) in sticky for di in (-1, 0, 1) for dj in (-1, 0, 1))
    return clamp(maps.up(t) * (1 - 0.7 * d.contact) + (STICKY_DUST if clings else 0.0))


EFFECTS = {
    "edge_wear": Effect(
        "edge_wear", "degrade",
        lambda t, d, isl, i, j: clamp(max(0.0, 1 - t.rim / 2) * (0.7 + 0.3 * t.height) * (1 + d.contact + d.abrasion)),
        wear_edges, writes=("bare",), scale=1.3, spread=0.95),
    "chips": Effect(
        "chips", "degrade",
        lambda t, d, isl, i, j: clamp(0.45 + 0.55 * max(1 - t.rim / maps.RIM_REACH, d.impact)), chip,
        writes=("bare", "damage"), scale=0.35, spread=1.0),
    "rust": Effect(
        "rust", "degrade", lambda t, d, isl, i, j: rust_mask(t, isl, i, j), rust_over, reads=("bare", "damage"),
        writes=("rust",), scale=0.6, spread=0.8),
    "grime": Effect(
        "grime", "deposit", lambda t, d, isl, i, j: clamp(t.occlusion * 2.2), laying(GRIME, GRIME_COVER),
        scale=0.7, spread=0.4, soft=True),
    "dirt": Effect(
        "dirt", "deposit", lambda t, d, isl, i, j: clamp(max(maps.ground(t), d.ground * (1 - t.height))),
        laying(DIRT, DIRT_COVER), scale=0.5, spread=0.5, soft=True),
    "dust": Effect(
        "dust", "deposit", dust_mask, laying(DUST, DUST_COVER), reads=("sticky",), scale=0.8, spread=0.8,
        soft=True),
}
