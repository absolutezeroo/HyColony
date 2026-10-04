"""The composer's budgets (spec 2026-10-04 blockpaint composer § 3): how many accents a model may hold, which few
texels take its deepest darks and its strongest lights, and whether each accent still reads at a quarter of its
size. Each operation changes the texture's values only and returns its report lines."""

from composer_units import TOUCH, lightness, mean_colour, mean_light, neighbours
from illustration import lit_edge, scaled
from vectors import near

# Accents a model may hold by its size in blocks: up to SMALL blocks, up to MEDIUM, more.
SMALL, MEDIUM = 1.0, 4.0
ACCENTS = {"small": 2, "medium": 4, "large": 7}
# The deepest darks: the DARK_SHARE most enclosed texels of the model (at least DARK_OCCLUSION enclosed: under a
# shelf, at a junction, deep in a hollow), darker by DEEP, never under FLOOR in lightness.
DARK_SHARE, DARK_OCCLUSION, DEEP, FLOOR = 0.05, 0.45, 0.45, 24
# Lights on the lit edges (illustration.lit_edge): a major accent's PRIMARY lighter, an important unit's SECONDARY;
# a calm unit's edge lights pulled DIMMED of the way back to its mean.
PRIMARY, SECONDARY, DIMMED = 0.35, 0.15, 0.6
# From afar (a quarter of the side, each AFAR x AFAR block one texel: semantics.FAR halves the side instead), an
# accent reads when its mean lightness stands MERGED from its neighbours' and it fills at least one whole block.
AFAR, MERGED = 4, 12


def size_of(model):
    """small, medium or large, from the model's size in blocks."""
    return "small" if model.blocks <= SMALL else "medium" if model.blocks <= MEDIUM else "large"


def accents(model):
    """(model with its accents within budget, report lines): the units of importance 2 and 3 joined into accents (the
    units of one part, a ledger's leather and pages; touching units of one family, a crate's lid and body), the most
    important (then the largest) first; past the model's budget (ACCENTS), the others fall to importance 1."""
    groups = []
    for unit in (u for u in model.units if u.level >= 2):
        joined = [g for g in groups if any(o.part == unit.part or o.family == unit.family and near(
            o.points, unit.points, TOUCH) for o in g)]
        groups = [g for g in groups if g not in joined] + [[unit] + [u for g in joined for u in g]]
    groups.sort(key=lambda g: (-max(u.level for u in g), -sum(len(u.texels) for u in g)))
    budget = ACCENTS[size_of(model)]
    dropped = {(u.part, u.family) for g in groups[budget:] for u in g}
    kept = {(u.part, u.family): n for n, g in enumerate(groups[:budget]) for u in g}
    units = [u._replace(level=1) if (u.part, u.family) in dropped else u._replace(accent=kept.get((u.part, u.family)))
             for u in model.units]
    lines = [f"budget d'accents ({size_of(model)}, {budget}) : {min(len(groups), budget)} accents gardés"]
    if dropped:
        lines.append(f"budget dépassé : {', '.join(sorted({p for p, _ in dropped}))} ramenés au rang normal")
    return model._replace(units=units), lines


def darks(pixels, model, composer=None):
    """Darkens the DARK_SHARE most enclosed texels of the model by DEEP, never under FLOOR."""
    enclosed = sorted((c for c, t in model.texels.items() if t.occlusion >= DARK_OCCLUSION),
                      key=lambda c: -model.texels[c].occlusion)
    chosen = enclosed[:int(DARK_SHARE * len(model.texels))]
    for cell in chosen:
        light = lightness(pixels[cell])
        pixels[cell] = scaled(pixels[cell], max(1 - DEEP, min(1.0, FLOOR / light)) if light else 1.0)
    share = round(100 * len(chosen) / max(len(model.texels), 1), 1)
    return [f"sombres profonds sur {len(chosen)} texels ({share} % du modèle), aux jonctions et sous les étagères"]


def lights(pixels, model, composer=None):
    """Primary edge lights on major accents, secondary on important units, none on calm ones (their edge lights
    pulled back towards their mean)."""
    counts = {"primary": 0, "secondary": 0, "dimmed": 0}
    for unit in model.units:
        edge = [c for c, t in unit.texels.items() if lit_edge(t)]
        if unit.level >= 2:
            k = 1 + (PRIMARY if unit.level == 3 else SECONDARY)
            for cell in edge:
                pixels[cell] = scaled(pixels[cell], k)
            counts["primary" if unit.level == 3 else "secondary"] += len(edge)
        elif unit.level == 0 and edge:
            mean = mean_colour(pixels, unit.texels)
            for cell in [c for c in edge if lightness(pixels[c]) > lightness(mean)]:
                here = pixels[cell]
                pixels[cell] = (*(round(here[k] + (mean[k] - here[k]) * DIMMED) for k in range(3)), here[3])
                counts["dimmed"] += 1
    return [f"clairs : {counts['primary']} texels primaires, {counts['secondary']} secondaires, "
            f"{counts['dimmed']} éteints sur le fond"]


def distance(pixels, model, composer=None):
    """Whether each accent (its units together) reads at a quarter of the size: its mean lightness MERGED from that of
    the units around it outside the accent, and at least one AFAR x AFAR block of its texels whole."""
    lines = []
    pairs = neighbours(model.units)
    for accent in sorted({u.accent for u in model.units if u.accent is not None}):
        units = [u for u in model.units if u.accent == accent]
        texels = {c for u in units for c in u.texels}
        around = {c for a, b in pairs for here, there in ((a, b), (b, a))
                  if here.accent == accent and there.accent != accent for c in there.texels}
        gap = abs(mean_light(pixels, texels) - mean_light(pixels, around)) if around else MERGED
        blocks = {(x // AFAR, y // AFAR) for x, y in texels}
        whole = any(all((bx * AFAR + i, by * AFAR + j) in texels for i in range(AFAR) for j in range(AFAR))
                    for bx, by in blocks)
        verdict = "se lit" if gap >= MERGED and whole else "ne se lit plus"
        names = ", ".join(sorted({u.part for u in units}))
        lines.append(f"à 25 % : l'accent {names} {verdict} (écart {round(gap)} avec ce qui l'entoure)")
    return lines
