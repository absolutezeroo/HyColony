"""Surface effects and the single run of operations on a face (spec 2026-10-03 blockpaint surfaces, effects): each
effect builds its mask from the maps, never from chance; a degree reaches the texels in the order of that mask (degree,
never opacity: where an effect is, it is whole); effects run by moment (what degrades, then what lies on top), and
within a moment after the effects whose signals they read; the zones they write are kept on the island as signals
for the effects after them (anchor points: rust around damage)."""

import zlib
from collections import namedtuple

import art
import compat
from bake import value_noise

# An effect: its name (compat.py, role weights), its moment (MOMENTS), its mask (t, declared, island, i, j) -> 0..1,
# its action on the island (island, {(i, j): amount}), the signals it reads and writes (island.signals), the
# frequency of its clusters (a noise cell spans bake.NOISE_CELL / scale world units), how much the clusters cut into
# its mask (spread) and whether its border fades (soft: deposits).
Effect = namedtuple("Effect", "name moment mask act reads writes scale spread soft",
                    defaults=((), (), 0.6, 0.6, False))
# What degrades, old events (history.PAST_AGE), what lies on top, recent events.
MOMENTS = ("degrade", "past", "deposit", "recent")
EVENT_MOMENTS = ("past", "recent")
# The signals of the catalogue, which an effect may read or write without another of the run writing or reading it:
# bare (a substrate dug bare), damage (chips, scratches), rust, water_retention (cracks hold water), sticky (grease).
# Any other signal must be both written and read in the run: a misspelt one stops the paint.
SIGNALS = frozenset({"bare", "damage", "rust", "water_retention", "sticky"})
NEIGHBOURS = ((1, 0), (-1, 0), (0, 1), (0, -1), (1, 1), (1, -1), (-1, 1), (-1, -1))
# The fewest touching texels a zone keeps (a 2 x 2 patch): fewer is a speck that vanishes from afar.
MIN_PATCH = 4
# A soft effect fades to this amount on the texels next to its zone whose score falls short by less than SOFT_BAND.
SOFT_AMOUNT, SOFT_BAND = 0.5, 0.15
# A material: its substrate brush, family, coats (layers.Coat), own effects ((effect, degree)) and own weights
# ({effect: weight}, multiplying compat's: 0 forbids an effect on it, as a stainless steel forbids rust).
Material = namedtuple("Material", "substrate family coats effects weights")
# How a run goes on a face: the part's declared maps (roles.Declared), the seed of its clusters, whether rest zones
# damp the planned effects (art.rest), whether a deposit over a more useful one is left out (run) and the rest zones'
# floor (art.rest_floor: a neglected model rests less).
Pass = namedtuple("Pass", "declared seed rest fight floor", defaults=(0, False, False, art.REST))


def clamp(v):
    """v held within [0, 1]."""
    return min(1.0, max(0.0, v))


def whole(amounts):
    """The cells amounts ({(i, j): amount}, reached) holds whole, leaving out a soft border."""
    return [cell for cell, amount in amounts.items() if amount == 1.0]


def laying(rgb, cover):
    """An action laying a deposit of colour rgb, cover of it where an effect lies whole (less on its soft border)."""
    def act(island, amounts):
        island.deposit(rgb, {cell: amount * cover for cell, amount in amounts.items()})
    return act


def tinting(rgb, amount, layer="visible"):
    """An action turning layer (layers.Island.tint) towards rgb by amount where an effect lies whole."""
    def act(island, amounts):
        island.tint(whole(amounts), rgb, amount, layer=layer)
    return act


def material(substrate, family=None, coats=(), effects=(), weights=None):
    """A material for paint's tiles: substrate (a brush), its family (else the brush's own, brush.family), coats from
    the bottom up, its own effects ((effect, degree)) and weights ({effect: weight}). Fails on an unknown family and
    on an effect impossible on the substrate and on every coat."""
    family = family or getattr(substrate, "family", None)
    for f in (family, *(coat.family for coat in coats)):
        if f is not None and f not in compat.FAMILIES:
            raise SystemExit(f"unknown family {f} (families: {', '.join(compat.FAMILIES)})")
    weights = dict(weights or {})
    for effect, _ in effects:
        if weights.get(effect.name, 1.0) == 0.0 or all(compat.weight(effect.name, f) == 0.0
                                                        for f in (family, *(coat.family for coat in coats))):
            raise SystemExit(f"{effect.name} is impossible on {family} and its coats")
    return Material(substrate, family, tuple(coats), tuple(effects), weights)


def cluster(point, scale, seed, name):
    """Smooth clusters in [0, 1] over world space (faces side by side join up), two octaves so their borders are
    organic, each effect and seed its own."""
    salt = zlib.crc32(f"{name}/{seed}".encode()) % 997
    p = tuple(c * scale + salt * 1.37 for c in point)
    q = tuple(c * 2.3 + 5.1 for c in p)
    return min(1.0, max(0.0, (0.8 * value_noise(p) + 0.3 * value_noise(q) + 1) / 2))


def reached(effect, island, degree, how):
    """{(i, j): amount} of the texels effect reaches at degree in the pass how (Pass): those whose score (mask,
    weighed by compat for the layer showing there and by the material's weights, by the rest zones when how.rest
    (art.rest) unless the effect is an event's (EVENT_MOMENTS), cut by clusters) is strictly above 1 - degree, in a
    patch of at least MIN_PATCH touching texels (patches), get 1; a soft effect fades to SOFT_AMOUNT on the texels
    beside them that fall short by less than SOFT_BAND. Degree 0 reaches nothing; a texel outside the mask never."""
    if degree <= 0:
        return {}
    source = island.substrate.load()
    own = island.weights.get(effect.name, 1.0)
    scores = {}
    for (i, j), t in island.texels.items():
        if not source[i, j][3]:
            continue
        mask = effect.mask(t, how.declared, island, i, j) * compat.weight(effect.name, island.family_at(i, j)) * own
        # An event marks where it happened: rest zones only damp the planned effects.
        if how.rest and effect.moment not in EVENT_MOMENTS:
            mask *= art.rest(t, how.declared, how.floor)
        if mask > 0:
            clusters = cluster(t.point, effect.scale, how.seed, effect.name)
            scores[i, j] = mask * (1 - effect.spread + effect.spread * clusters)
    full = patches({cell for cell, score in scores.items() if score > 1 - degree})
    amounts = dict.fromkeys(full, 1.0)
    if effect.soft:
        for (i, j), score in scores.items():
            if (i, j) not in full and score > 1 - degree - SOFT_BAND and {(i + 1, j), (i - 1, j), (i, j + 1),
                                                                         (i, j - 1)} & full:
                amounts[i, j] = SOFT_AMOUNT
    return amounts


def patches(cells):
    """The cells of cells that belong to a patch of at least MIN_PATCH touching cells (8-connected): smaller ones are
    specks of noise, not zones, and would vanish from afar (a quarter of the size)."""
    kept, seen = set(), set()
    for start in cells:
        if start in seen:
            continue
        patch, todo = {start}, [start]
        seen.add(start)
        while todo:
            i, j = todo.pop()
            for di, dj in NEIGHBOURS:
                cell = (i + di, j + dj)
                if cell in cells and cell not in seen:
                    seen.add(cell)
                    patch.add(cell)
                    todo.append(cell)
        if len(patch) >= MIN_PATCH:
            kept |= patch
    return kept


def run(island, uses, how):
    """Runs the effects of uses ((effect, degree), the more useful first) on island in order (ordered) in the pass
    how (Pass), each acting on the texels it reaches; its zone is added to island.zones (an effect that comes twice
    keeps both) and to the signals it writes (island.signals). With how.fight, a deposit whose zone lies over more
    than art.FIGHT of its texels under an earlier deposit's is not laid (two deposits fighting over one place: the
    earlier, more useful one stays). An effect may come twice (planned and from an event), each with its own degree.
    Fails on a signal read that no effect of the run writes, or written that none reads, unless it is one of
    SIGNALS."""
    written = SIGNALS | {name for effect, _ in uses for name in effect.writes}
    read = SIGNALS | {name for effect, _ in uses for name in effect.reads}
    for effect, _ in uses:
        unknown = (set(effect.reads) - written) | (set(effect.writes) - read)
        if unknown:
            raise SystemExit(f"{effect.name}: unknown signals {', '.join(sorted(unknown))}")
    degrees = {id(effect): degree for effect, degree in uses}
    deposited = set()
    for effect in ordered([effect for effect, _ in uses]):
        amounts = reached(effect, island, degrees[id(effect)], how)
        zone = set(whole(amounts))
        if how.fight and effect.moment == "deposit" and zone and len(zone & deposited) > art.FIGHT * len(zone):
            continue
        if effect.moment == "deposit":
            deposited |= zone
        island.zones.setdefault(effect.name, set()).update(zone)
        for name in effect.writes:
            island.signals.setdefault(name, set()).update(zone)
        effect.act(island, amounts)


def ordered(effects):
    """effects by moment, and within a moment after every effect writing a signal they read (kept in their given
    order otherwise). Fails on a cycle of signals."""
    result = []
    for moment in MOMENTS:
        waiting = [e for e in effects if e.moment == moment]
        while waiting:
            ready = [e for e in waiting if not any(set(e.reads) & set(o.writes) for o in waiting if o is not e)]
            if not ready:
                raise SystemExit(f"effects read each other's signals in a cycle: {', '.join(e.name for e in waiting)}")
            result.append(ready[0])
            waiting.remove(ready[0])
    return result
