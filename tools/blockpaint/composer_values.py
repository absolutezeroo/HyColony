"""The composer's values (spec 2026-10-04 blockpaint composer § 3): each unit pulled to its value group so the model
spreads over its own range, the background stepping back, the important units stepping forward, two merging forms
parted, two alike materials parted on a single axis. Each operation changes the texture's values only and returns its
report lines."""

import bisect
import colorsys

from composer_units import (colour_gap, contrast_unit, lightness, mean_colour, mean_light, neighbours, saturate_unit,
                            scale_unit, shifted)

# The value groups and the centile of the model's own lightness each aims at.
GROUP_CENTILES = {"deep": 0.03, "dark": 0.12, "mid": 0.5, "light": 0.85, "accent": 0.97}
# A unit is pulled VALUE_PULL of the way to its group's target, its value scaled within PULL_LIMIT: further, it reads
# as another material (the large quarry's light ground stone, halved, read as coal).
VALUE_PULL, PULL_LIMIT = 0.85, (0.75, 4 / 3)
# A normal unit's own centile in the model is pushed SPREAD times further from the middle: what is already darker
# than most goes darker, what is lighter goes lighter.
SPREAD = 1.6
# The background (importance 0) loses BACKGROUND_CONTRAST of its contrast and BACKGROUND_SATURATION of its
# saturation; an important unit gains FOCAL_CONTRAST[level] of contrast, a major accent FOCAL_SATURATION too.
BACKGROUND_CONTRAST, BACKGROUND_SATURATION = 0.45, 0.25
FOCAL_CONTRAST, FOCAL_SATURATION = {2: 0.25, 3: 0.5}, 0.2
# Two neighbouring units merge when their mean lightness lie within MERGED; the more important lighter and the other
# darker by ROUTE each.
MERGED, ROUTE = 18, 0.1
# Two neighbouring units of different families stay told apart when their mean colours differ by DISTINCT_COLOUR
# (summed over r, g, b; semantics checks the same); parted on one axis by at most SEPARATE (value, saturation) or
# SEPARATE_HUE degrees (temperature), tried in SEPARATE_STEPS steps; the hue parts only colours at least
# HUE_SATURATION saturated.
DISTINCT_COLOUR, SEPARATE, SEPARATE_HUE, SEPARATE_STEPS, HUE_SATURATION = 30, 0.2, 12.0, 20, 0.15
# A unit more than LARGER times the texels of its partner is the setting the small one parts from: it never moves (a
# candle holder never turns a whole worktop pink).
LARGER = 4


def group_of(unit, named):
    """A unit's value group: named by the composer, else by its importance (paper at least light); None for a normal
    unit, which keeps its own rank spread away from the middle (groups)."""
    if unit.part in named:
        return named[unit.part]
    if unit.level == 3:
        return "accent"
    if unit.family == "paper" or unit.level == 2:
        return "light"
    return "dark" if unit.level == 0 else None


def centile_of(value, values):
    """The share of values (sorted) under value."""
    return bisect.bisect_left(values, value) / max(len(values) - 1, 1)


def width(pixels, cells):
    """The width of the lightness of cells, from its 10th to its 90th centile."""
    values = sorted(lightness(pixels[c]) for c in cells)
    return values[int(0.9 * (len(values) - 1))] - values[int(0.1 * (len(values) - 1))]


def factor(group, mean, target):
    """The value factor pulling a unit's mean VALUE_PULL towards target, within PULL_LIMIT, and one way only for a
    group: a light or accent unit never darkens, a dark or deep one never lightens (a white sheet already lighter than
    its target stays white)."""
    k = min(PULL_LIMIT[1], max(PULL_LIMIT[0], (mean + VALUE_PULL * (target - mean)) / mean))
    if group in ("light", "accent"):
        return max(1.0, k)
    return min(1.0, k) if group in ("dark", "deep") else k


def groups(pixels, model, composer):
    """Pulls each unit towards its group's target (a centile of the model's own lightness; factor); a normal unit (no
    group) aims at its own centile pushed SPREAD times further from the middle, so the model spreads over its range
    instead of a band of mid values. Undone, and reported, when the model's width would narrow all the same."""
    values = sorted(lightness(pixels[c]) for c in model.texels)
    before, kept = width(pixels, model.texels), {c: pixels[c] for c in model.texels}
    counts = {}
    for unit in model.units:
        mean = mean_light(pixels, unit.texels)
        group = group_of(unit, composer.groups)
        counts[group or "rang"] = counts.get(group or "rang", 0) + 1
        centile = GROUP_CENTILES[group] if group else 0.5 + SPREAD * (centile_of(mean, values) - 0.5)
        if mean:
            target = values[int(min(0.97, max(0.03, centile)) * (len(values) - 1))]
            scale_unit(pixels, unit.texels, factor(group, mean, target))
    after = width(pixels, model.texels)
    named = ", ".join(f"{counts[g]} {g}" for g in (*GROUP_CENTILES, "rang") if g in counts)
    if after < before:
        for cell, pixel in kept.items():
            pixels[cell] = pixel
        return [f"groupes de valeur ({named}) annulés : l'étendue aurait rétréci ({before:.1f} -> {after:.1f})"]
    return [f"groupes de valeur : {named} ; étendue des valeurs (10e à 90e centile) {round(before)} -> {round(after)}"]


def background(pixels, model, composer):
    """The background steps back: each calm unit's contrast and saturation lowered."""
    calm = [u for u in model.units if u.level == 0]
    for unit in calm:
        contrast_unit(pixels, unit.texels, 1 - BACKGROUND_CONTRAST)
        saturate_unit(pixels, unit.texels, 1 - BACKGROUND_SATURATION)
    parts = ", ".join(sorted({u.part for u in calm}))
    return [f"fond en retrait ({parts}) : contraste -{round(BACKGROUND_CONTRAST * 100)} %, saturation "
            f"-{round(BACKGROUND_SATURATION * 100)} %"] if calm else []


def focal(pixels, model, composer):
    """The important units step forward: their contrast raised, a major accent's saturation too."""
    lines = []
    for unit in (u for u in model.units if u.level >= 2):
        contrast_unit(pixels, unit.texels, 1 + FOCAL_CONTRAST[unit.level])
        if unit.level == 3:
            saturate_unit(pixels, unit.texels, 1 + FOCAL_SATURATION)
        lines.append(f"{unit.part} ({unit.family}) en avant : contraste +{round(FOCAL_CONTRAST[unit.level] * 100)} %")
    return lines


def routing(pixels, model, composer):
    """Parts the neighbouring units whose values merge (within MERGED, measured once before any move) and whose
    importance differs: each unit moves once, by ROUTE, lighter when it is ahead in more such pairs than behind,
    darker when behind in more, not at all when even, so a unit between many neighbours is not pushed again and
    again."""
    lines, pull = [], {}
    light = {(u.part, u.family): mean_light(pixels, u.texels) for u in model.units}
    for a, b in neighbours(model.units):
        if a.level == b.level or abs(light[a.part, a.family] - light[b.part, b.family]) >= MERGED:
            continue
        ahead, behind = (a, b) if a.level > b.level else (b, a)
        for unit, sign in ((ahead, 1), (behind, -1)):
            pull[unit.part, unit.family] = (unit, pull.get((unit.part, unit.family), (unit, 0))[1] + sign)
        lines.append(f"{ahead.part} et {behind.part} se confondaient : {ahead.part} avance, {behind.part} recule")
    for unit, sign in pull.values():
        if sign:
            scale_unit(pixels, unit.texels, 1 + ROUTE * (1 if sign > 0 else -1))
    return lines


def materials(pixels, model, composer):
    """Parts each two neighbouring units of different families whose colours lie closer than DISTINCT_COLOUR, on the
    one axis they already differ on the most (axis_of), just enough. Each unit moves once: where a pair's unit has
    already moved, or is more than LARGER times its partner, its partner moves alone; where neither may, the pair is
    reported as left."""
    lines, moved = [], set()
    for a, b in neighbours(model.units):
        if a.family == b.family or colour_gap(*means(pixels, a, b)) >= DISTINCT_COLOUR:
            continue
        free = [u for u in (a, b) if key(u) not in moved and len(u.texels) <= LARGER * len(partner(u, a, b).texels)]
        if free:
            lines.append(parted(pixels, a, b, free))
            moved |= {key(u) for u in free}
        else:
            lines.append(f"{a.part} et {b.part} trop proches : ni l'une ni l'autre ne bouge (déjà déplacée ou "
                         "décor), laissées")
    return lines


def key(unit):
    return unit.part, unit.family


def partner(unit, a, b):
    """The other unit of the pair (a, b)."""
    return b if key(unit) == key(a) else a


def means(pixels, a, b):
    return mean_colour(pixels, a.texels), mean_colour(pixels, b.texels)


def axes_of(pixels, a, b):
    """[(axis, whether a goes up on it)], the axes a and b already differ on the most first: value, saturation and,
    when both are coloured (HUE_SATURATION), temperature (on a grey the hue is noise and cannot part them)."""
    (ha, sa, va), (hb, sb, vb) = (colorsys.rgb_to_hsv(*(c / 255 for c in m)) for m in means(pixels, a, b))
    gaps = {"valeur": va - vb, "saturation": sa - sb}
    if min(sa, sb) >= HUE_SATURATION:
        # The signed shorter way from b's hue to a's, in half turns, to weigh against value and saturation.
        gaps["température"] = 2 * ((ha - hb + 0.5) % 1.0 - 0.5)
    return [(axis, gaps[axis] >= 0) for axis in sorted(gaps, key=lambda name: -abs(gaps[name]))]


def parted(pixels, a, b, free):
    """Parts a and b on a single axis, moving only the free units, each time from their own colours, by n
    SEPARATE_STEPS-ths of the most allowed until DISTINCT_COLOUR: the axis they differ on the most (axes_of), else
    the next that gets there; when none does, the widest of them at the most allowed. Its report line says which."""
    before = {cell: pixels[cell] for unit in free for cell in unit.texels}
    names = f"{a.part} ({a.family}) et {b.part} ({b.family}) trop proches"
    best = None
    for axis, a_up in axes_of(pixels, a, b):
        signed = [(unit, (1 if a_up else -1) * (1 if key(unit) == key(a) else -1)) for unit in free]
        for n in range(1, SEPARATE_STEPS + 1):
            moved(pixels, signed, before, axis, n / SEPARATE_STEPS)
            gap = colour_gap(*means(pixels, a, b))
            if gap >= DISTINCT_COLOUR:
                return f"{names} : séparés par la {axis} ({round(100 * n / SEPARATE_STEPS)} % du plus permis)"
        best = max(best or (gap, axis, signed), (gap, axis, signed), key=lambda tried: tried[0])
    moved(pixels, best[2], before, best[1], 1.0)
    return f"{names} : aucun axe ne suffit, écartés par la {best[1]} au plus permis sans atteindre le seuil"


def moved(pixels, signed, before, axis, amount):
    """Sets each texel of the units of signed ([(unit, 1 or -1)]) to its colour before (before) nudged along axis by
    amount, up or down by its unit's sign."""
    for unit, sign in signed:
        for cell in unit.texels:
            pixels[cell] = nudged(before[cell], axis, sign * amount)


def nudged(pixel, axis, amount):
    """pixel moved along axis by amount (from -1 to 1) of the most allowed: SEPARATE of its value or saturation,
    SEPARATE_HUE degrees of hue (turned further on for temperature)."""
    if axis == "valeur":
        return (*(max(0, min(255, round(c * (1 + amount * SEPARATE)))) for c in pixel[:3]), pixel[3])
    if axis == "température":
        return shifted(pixel, amount * SEPARATE_HUE, 1.0)
    return shifted(pixel, 0.0, 1 + amount * SEPARATE)
