"""The semantic critique of a layered model (spec 2026-10-03 blockpaint surfaces § 3.9): repaints it keeping each
island and the zones of its effects, then asks whether each effect sits where it makes sense: dirt low, moss where
damp, touched parts the most worn, rust on bare iron only and rather where exposed, the grain along each part's
declared axis, the focus the most detailed, effects still readable from afar, neighbouring materials told apart, each
event's marks within its reach.

    python tools/blockpaint/semantics.py tools/huts/warehouse.py [path/to/Assets.zip]
"""

import importlib.util
import json
import sys
from collections import namedtuple
from pathlib import Path

import catalog
import conditions
import critique
import deposits
import history
import maps
import roles
import surface
from pack import ROOT, Assets
from paint import grain_of

# An answer: the question, True or False (None: the model gives nothing to ask it on), and what was measured.
Answer = namedtuple("Answer", "question ok detail")
# From afar a model shows at a quarter of its size: each FAR x FAR block of texels becomes one, and shows a zone when
# at least READABLE of its texels are in it (half the block, the box filter's threshold).
FAR, READABLE = 2, 2
# Two neighbouring materials are told apart when their mean colours differ by this much (summed over r, g, b), or
# their grains by this much (mean step).
DISTINCT_COLOUR, DISTINCT_STEP = 30, 3.0
# Two parts are neighbours when their texels come within this many world units.
TOUCH = 0.6
# Where each mod keeps its models: a module's MODEL is under one of them.
COMMONS = tuple(ROOT / m / "src/main/resources/Common" for m in ("plugin", "vanilla/plugin", "domum/plugin"))


def records_of(module, nodes, assets=None):
    """[(part, side, rect, island, image)] of every layered island of the module's model, as catalog paints it."""
    return painted(module, nodes, assets)[0]


def painted(module, nodes, assets=None):
    """(records_of's records, the history resolved on the whole model) of a module, its model baked once; ([], [])
    for a module that declares no CONDITION or DEFAULT (nothing layered)."""
    condition = getattr(module, "CONDITION", None)
    # As catalog.model_texture tells them: DEFAULT itself paints as before.
    if condition is None or condition is conditions.DEFAULT:
        return [], []
    values, contexts = catalog.surveyed(module, nodes)
    surface = catalog.module_surface(module, contexts, [])
    catalog.module_texture(module, nodes, assets, values, surface)
    return surface.record, surface.history


def cells(records, zone=None):
    """[(island, cell)] of every texel of records, or of those in the zone named zone."""
    return [(r[3], c) for r in records for c in (r[3].zones.get(zone, ()) if zone else r[3].texels)]


def mean(values, default=0.0):
    values = list(values)
    return sum(values) / len(values) if values else default


def dirt_low(records):
    """Dirt (and mud) lies lower than the model on average."""
    dirty = cells(records, "dirt") + cells(records, "mud")
    if not dirty:
        return Answer("terre en bas", None, "pas de terre")
    low, every = mean(i.texels[c].height for i, c in dirty), mean(i.texels[c].height for i, c in cells(records))
    return Answer("terre en bas", low < every, f"hauteur moyenne {low:.2f} contre {every:.2f}")


def moss_damp(records):
    """Moss and mold grow where it is damper than the model on average (deposits.damp: hollows, tops, beside a
    crack, out of the light)."""
    grown = cells(records, "moss") + cells(records, "mold")
    if not grown:
        return Answer("mousse à l'humidité", None, "pas de mousse")
    damp = mean(deposits.damp(i.texels[c], i, *c) for i, c in grown)
    every = mean(deposits.damp(i.texels[c], i, *c) for i, c in cells(records))
    return Answer("mousse à l'humidité", damp > every, f"humidité moyenne {damp:.2f} contre {every:.2f}")


def touched_worn(records, declared):
    """Parts touched by hands (contact of at least a half) are worn more than the others (edge wear and chips)."""
    def share(group):
        worn = sum(len(r[3].zones.get("edge_wear", set()) | r[3].zones.get("chips", set())) for r in group)
        return worn / max(1, sum(len(r[3].texels) for r in group))

    touched = [r for r in records if declared(r[0]).contact >= 0.5]
    others = [r for r in records if declared(r[0]).contact < 0.5]
    if not touched or not others:
        return Answer("pièces touchées plus usées", None, "pas de pièce touchée")
    a, b = share(touched), share(others)
    return Answer("pièces touchées plus usées", a >= b, f"{a:.0%} contre {b:.0%}")


def rust_on_bare_iron(records):
    """Rust only on bare iron, and rather where exposed than on bare iron at large."""
    rusty = cells(records, "rust")
    if not rusty:
        return Answer("rouille sur le fer nu", None, "pas de rouille")
    off = [c for i, c in rusty if not i.bare("ferrous", *c)]
    bare = [(i, c) for i, c in cells(records) if i.bare("ferrous", *c)]
    exposed = mean(maps.exposure(i.texels[c]) for i, c in rusty)
    every = mean(maps.exposure(i.texels[c]) for i, c in bare)
    return Answer("rouille sur le fer nu", not off and exposed >= every - 0.05,
                  f"{len(off)} texels hors du fer nu ; exposition {exposed:.2f} contre {every:.2f}")


def grain_along_axes(records, axes):
    """On each face of a part with a declared axis that runs across the face, the colour changes less along the grain
    than across it."""
    wrong, checked = [], 0
    for part, side, _, island, _ in records:
        # The grain is the substrate's: a coat of paint over it hides it, as it should.
        image = island.substrate
        direction = grain_of(side, axes.get(part))
        if direction is None or min(image.size) < 2:
            continue
        checked += 1
        along_u, along_v = steps(image, 1, 0), steps(image, 0, 1)
        if (along_u >= along_v) if direction == "u" else (along_v >= along_u):
            wrong.append(f"{part} {side}")
    if not checked:
        return Answer("fibres selon l'axe", None, "pas d'axe déclaré")
    return Answer("fibres selon l'axe", not wrong, ", ".join(wrong) or f"{checked} faces")


def focus_dominant(records, focus):
    """The focal parts are at least as detailed (mean step) as the others."""
    def detail(group):
        measured = [critique.measure(r[4]) for r in group if min(r[4].size) >= critique.MIN_SIDE]
        return mean(m.step for m in measured if m)

    focal = [r for r in records if r[0] in focus]
    if not focal:
        return Answer("point focal dominant", None, "pas de point focal")
    a, b = detail(focal), detail([r for r in records if r[0] not in focus])
    return Answer("point focal dominant", a >= b, f"détail {a:.1f} contre {b:.1f}")


def readable(records):
    """Every effect zone survives a quarter of the size (half the side): once each FAR x FAR block of its island is
    one texel, at least one block still holds READABLE of the zone's texels."""
    tiny = [f"{r[0]} {r[1]} {name}" for r in records for name, zone in r[3].zones.items() if zone and not seen(zone)]
    return Answer("lisible de loin", not tiny, ", ".join(tiny[:6]) or "toutes les zones tiennent")


def seen(zone):
    """Whether a block of FAR x FAR texels holds READABLE of zone's texels."""
    blocks = {}
    for i, j in zone:
        blocks[i // FAR, j // FAR] = blocks.get((i // FAR, j // FAR), 0) + 1
    return max(blocks.values()) >= READABLE


def materials_distinct(records, material_of):
    """Neighbouring parts of different substances (families) differ in mean colour or in grain (a part of two
    materials, one on some faces and one on others, counts as two). Two materials of one family are one substance
    painted twice (a barrel's staves and lid), and a part too small to read from afar (readable) is not judged."""
    parts = {}
    for part, side, _, island, image in records:
        entry = parts.setdefault((part, material_of(part, side)),
                                 {"points": [], "colours": [], "steps": [], "family": island.family})
        entry["points"] += [t.point for t in island.texels.values()]
        entry["colours"] += [p[:3] for p in image.get_flattened_data() if p[3]]
        m = critique.measure(image) if min(image.size) >= 2 else None
        if m:
            entry["steps"].append(m.step)
    alike = []
    keys = sorted(parts)
    for k, a in enumerate(keys):
        for b in keys[k + 1:]:
            pa, pb = parts[a], parts[b]
            small = min(len(pa["points"]), len(pb["points"])) < FAR * FAR * READABLE
            if a[1] == b[1] or pa["family"] == pb["family"] or small or not near(pa["points"], pb["points"]):
                continue
            colour = sum(abs(mean(c[n] for c in pa["colours"]) - mean(c[n] for c in pb["colours"])) for n in range(3))
            if colour < DISTINCT_COLOUR and abs(mean(pa["steps"]) - mean(pb["steps"])) < DISTINCT_STEP:
                alike.append(f"{a[0]}/{b[0]}")
    return Answer("matériaux voisins distincts", not alike, ", ".join(alike) or "tous distincts")


def history_in_place(records, events):
    """Every mark an event laid (an effect of its own, which the plan never lays: marks.py) lies within the reach of an
    event that lays it (history.reach_of): a fire's soot above its hearth, a stain below its edge. The chips an event
    shares with the plan are not checked: their zone holds the planned chips too, which lie anywhere."""
    laid = {}
    for event, origin in events:
        for effect, _ in event.effects:
            if effect.name not in surface.CATALOGUE:
                laid.setdefault(effect.name, []).append((event, origin))
    if not laid:
        return Answer("histoire à sa place", None, "pas d'histoire")
    stray = [f"{r[0]} {r[1]} {name}" for r in records for name, zone in r[3].zones.items() if name in laid
             for c in zone if all(history.reach_of(e, r[3].texels[c], o) <= 0 for e, o in laid[name])]
    return Answer("histoire à sa place", not stray, ", ".join(sorted(set(stray))[:6]) or "chaque marque à sa place")


def near(points_a, points_b):
    """Whether two sets of world points come within TOUCH of each other (bounding boxes, widened by TOUCH)."""
    lo_a, hi_a = [min(p[k] for p in points_a) for k in range(3)], [max(p[k] for p in points_a) for k in range(3)]
    lo_b, hi_b = [min(p[k] for p in points_b) for k in range(3)], [max(p[k] for p in points_b) for k in range(3)]
    return all(lo_a[k] - TOUCH <= hi_b[k] and lo_b[k] - TOUCH <= hi_a[k] for k in range(3))


def steps(image, dx, dy):
    """Mean colour step between each texel and its neighbour (dx, dy) away."""
    w, h = image.size
    px = image.load()
    pairs = [(px[x, y], px[x + dx, y + dy]) for x in range(w - dx) for y in range(h - dy)]
    return mean(sum(abs(a[k] - b[k]) for k in range(3)) for a, b in pairs)


def answers(module, nodes, assets=None):
    """The semantic answers for a module's model."""
    # The history is resolved on the whole model, as catalog paints it: an event may stand on a part not layered.
    records, events = painted(module, nodes, assets)
    role_of, usage, focus = getattr(module, "ROLES", {}), getattr(module, "USAGE", {}), getattr(module, "FOCUS", ())
    return [dirt_low(records), moss_damp(records),
            touched_worn(records, lambda part: roles.declared(part, role_of, usage, focus)),
            rust_on_bare_iron(records), grain_along_axes(records, getattr(module, "AXES", {})),
            focus_dominant(records, focus), readable(records), materials_distinct(records, catalog.material_of(module)),
            history_in_place(records, events)]


def main(args):
    path = Path(args[0]).resolve()
    sys.path.insert(0, str(path.parent))
    spec = importlib.util.spec_from_file_location(path.stem, path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    found = [r / (module.MODEL + ".blockymodel") for r in COMMONS if (r / (module.MODEL + ".blockymodel")).exists()]
    if not found:
        raise SystemExit(f"{module.MODEL}.blockymodel is in none of {', '.join(str(r) for r in COMMONS)}")
    nodes = json.loads(found[0].read_text(encoding="utf-8"))["nodes"]
    assets = Assets(args[1]) if len(args) > 1 else None
    for a in answers(module, nodes, assets):
        print(f"{'—' if a.ok is None else 'oui' if a.ok else 'NON'}  {a.question} : {a.detail}")


if __name__ == "__main__":
    main(sys.argv[1:])
