"""The illustration pass (spec 2026-10-04 blockpaint illustration): after the layers and before the composer and the
light, what a hand-painted artist does to each form rather than stack effects: rest areas cleared of secondary detail,
the form modelled at the part's scale, each face framed, junctions pressed, a few chosen edges lit. It reads the
layered islands (surface.layered's records) and never changes a layer: only the texture's values, each decision
reported. The model-wide decisions (materials told apart, accents) are the composer's (composer.py)."""

from collections import namedtuple

from bake import BEVEL_RINGS, LIGHT, value_noise
from vectors import dot, scale

# The operations, in the order they run: each reads the texture as the one before left it; rest first, as it brings
# texels back towards the image before the effects.
STEPS = ("rest", "form", "planes", "contact", "edges")
# A model's illustration: a list the decisions are reported in (None: not reported) and the operations run. The
# parts' visual importance is the model's (paint.Painting.importance, importance_of).
Illustration = namedtuple("Illustration", "report steps", defaults=(None, STEPS))
# The effects that read only up close; rest areas lose part of them.
SECONDARY = frozenset({"micro_scratches", "scratches", "scuffs", "dust", "craquelure", "chalking"})
# A rest area: at least REST_RIM texels from an open border and out of contact; there a normal part loses REST_CUT of
# its secondary effects, a calm one twice that.
REST_RIM, REST_CUT = 3, 0.45
# Contact: texels this enclosed (bake.Texel.occlusion) lie at a junction, darkened by up to CONTACT.
CONTACT_OCCLUSION, CONTACT = 0.45, 0.2
# Form: a part's top lighter and its foot darker by FORM each, broken by a world noise of cells FORM_CELL times
# bake.NOISE_CELL wide, so that it is no plain vertical gradient.
FORM, FORM_CELL, FORM_BREAK = 0.14, 3.0, 0.35
# Planes: each face framed as Hytale paints its panels (docs/research/blockpaint-surfaces.md § 8), darker by up to
# FRAME on its open border, fading over FRAME_RINGS texels inwards; a face turned to the light framed FRAME_LIT less.
FRAME, FRAME_RINGS, FRAME_LIT = 0.18, 3, 0.5
# Edges: the outer ring of an edge turned at least FACING to the light takes up to HIGHLIGHT, on the runs where a
# world noise passes the part's threshold (a calm part none, a major accent most).
FACING, HIGHLIGHT = 0.2, 0.25
RUNS = {1: 0.3, 2: 0.1, 3: -0.1}


def illustrate(image, records, illustration, level, show=0):
    """image (the unlit texture of records, surface.layered's) illustrated by each of illustration's steps in turn,
    level(part) giving each part's importance (importance_of), show the model's condition's (conditions.Condition: a
    neglected model's rest areas keep more of their story); changed in place and returned, the decisions appended to
    illustration.report when it is a list."""
    pixels = image.load()
    for step in illustration.steps:
        lines = rest(pixels, records, level, show) if step == "rest" else OPERATIONS[step](pixels, records, level)
        if illustration.report is not None:
            illustration.report.extend(lines)
    return image


def importance_of(part, importance, focus):
    """A part's visual importance: as importance ({part: 0..3}) names it, else 2 for a FOCUS part (important: the eye
    goes there, the major accent is a choice of its own), else 1."""
    return importance.get(part, 2 if part in focus else 1)


def scaled(pixel, k):
    return (*(max(0, min(255, round(c * k))) for c in pixel[:3]), pixel[3])


def rest(pixels, records, level, show=0):
    """Brings the secondary effects (SECONDARY) of a calm or normal part back towards the island's base (its image
    before the effects) in its rest areas: by REST_CUT on a normal part, twice that on a calm one, a texel in two such
    zones twice over, all of it divided by 1 + show (a neglected model's dust and scuffs are its story). A texel any
    other effect reached (rust, chips, a mark of the history) keeps all of them: the base holds none, and those tell
    the model's story."""
    lines = []
    for part, side, (u, v, _, _), island, _ in records:
        importance = level(part)
        if importance > 1 or island.base is None:
            continue
        told = {cell for name, zone in island.zones.items() if name not in SECONDARY for cell in zone}
        cut, base, keep, names = min(0.9, REST_CUT * (2 - importance)) / (1 + show), island.base.load(), {}, []
        for name in sorted(SECONDARY & set(island.zones)):
            cells = [c for c in island.zones[name] if c not in told and island.texels[c].rim >= REST_RIM
                     and island.texels[c].occlusion < CONTACT_OCCLUSION]
            for cell in cells:
                keep[cell] = keep.get(cell, 1.0) * (1 - cut)
            if cells:
                names.append(name)
        for (i, j), k in keep.items():
            here, under = pixels[u + i, v + j], base[i, j]
            pixels[u + i, v + j] = (*(round(under[c] + (here[c] - under[c]) * k) for c in range(3)), here[3])
        if names:
            lines.append(f"{part} {side} : {', '.join(names)} réduits de {round(cut * 100)} % en zone de repos "
                         f"({len(keep)} texels)")
    return lines


def form(pixels, records, level):
    """Models each part at its own scale: its top lighter and its foot darker by FORM, the gradient broken by a slow
    world noise (FORM_CELL)."""
    spans = {}
    for part, _, _, island, _ in records:
        ys = [t.point[1] for t in island.texels.values()]
        low, high = spans.get(part, (min(ys), max(ys)))
        spans[part] = (min(low, *ys), max(high, *ys))
    for part, _, (u, v, _, _), island, _ in records:
        low, high = spans[part]
        if high <= low:
            continue
        for (i, j), t in island.texels.items():
            place = (t.point[1] - low) / (high - low) - 0.5
            broken = 1 + FORM_BREAK * value_noise(scale(t.point, 1 / FORM_CELL))
            pixels[u + i, v + j] = scaled(pixels[u + i, v + j], 1 + 2 * FORM * place * broken)
    return [f"modelé de {len(spans)} pièces : le haut éclairci et le pied assombri de {round(FORM * 100)} %"]


def planes(pixels, records, level):
    """Frames each face: darker by its open border (bake.Texel.rim; a seam between two boxes is none), by up to FRAME
    fading over FRAME_RINGS texels, FRAME_LIT less on a face turned to the light, so that neighbouring planes part."""
    count = 0
    for _, _, (u, v, _, _), island, _ in records:
        for (i, j), t in island.texels.items():
            if t.rim < FRAME_RINGS:
                turned = 1 - FRAME_LIT * max(0.0, dot(t.normal, LIGHT))
                pixels[u + i, v + j] = scaled(pixels[u + i, v + j], 1 - FRAME * (1 - t.rim / FRAME_RINGS) * turned)
                count += 1
    return [f"cadre des faces : bords assombris de {round(FRAME * 100)} % au plus ({count} texels)"] if count else []


def contact(pixels, records, level):
    """Darkens the junctions between parts (texels at least CONTACT_OCCLUSION enclosed) by up to CONTACT, the more
    enclosed the darker."""
    count = 0
    for _, _, (u, v, _, _), island, _ in records:
        for (i, j), t in island.texels.items():
            if t.occlusion >= CONTACT_OCCLUSION:
                deep = (t.occlusion - CONTACT_OCCLUSION) / (1 - CONTACT_OCCLUSION)
                pixels[u + i, v + j] = scaled(pixels[u + i, v + j], 1 - CONTACT * (0.5 + 0.5 * deep))
                count += 1
    return [f"jonctions appuyées sur {count} texels"] if count else []


def lit_edge(t):
    """Whether a texel lies on the outer ring of an edge turned at least FACING to the light."""
    return t.edge is not None and t.edge[1] == BEVEL_RINGS[0] and dot(t.edge[0], LIGHT) >= FACING


def edges(pixels, records, level):
    """Lights the outer ring of the edges turned to the light (lit_edge), on runs only: where a world noise passes the
    part's threshold (RUNS), a calm part none; one report line, the parts lit the most first."""
    lit, seen = {}, {}
    for part, _, (u, v, _, _), island, _ in records:
        threshold = RUNS.get(level(part))
        for (i, j), t in island.texels.items():
            if not lit_edge(t):
                continue
            seen[part] = seen.get(part, 0) + 1
            if threshold is not None and value_noise(t.point) > threshold:
                pixels[u + i, v + j] = scaled(pixels[u + i, v + j], 1 + HIGHLIGHT * min(1.0, dot(t.edge[0], LIGHT)))
                lit[part] = lit.get(part, 0) + 1
    if not lit:
        return []
    shares = sorted(((round(100 * lit[p] / seen[p]), p) for p in lit), reverse=True)
    return [f"reflets sélectifs sur {len(lit)} pièces ({round(100 * sum(lit.values()) / sum(seen.values()))} % des "
            f"arêtes éclairées) ; les plus longs : {', '.join(f'{p} {s} %' for s, p in shares[:3])}"]


OPERATIONS = {"rest": rest, "form": form, "planes": planes, "contact": contact, "edges": edges}
