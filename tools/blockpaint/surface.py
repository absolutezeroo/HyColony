"""Paints the island of a layered material (spec 2026-10-03 blockpaint surfaces, the single run of operations):
substrate calmed to the part's detail budget (art.py), coats, the most useful of the effects the model's age,
condition, environment and the part's role call for, the events of its history, the composition, the part-wide macro
variation, then the dull film of a neglected condition."""

import colorsys
from collections import namedtuple

import art
import compat
import conditions
import degradations
import deposits
import effects
import history
import layers
import roles
import weathering

# Every planned effect: the first batch, the degradations and the deposits.
CATALOGUE = {**weathering.EFFECTS, **degradations.EFFECTS, **deposits.EFFECTS}
# Per step of a condition's show past a worn one's (a worn model wears, it is not dull yet), a model loses DULL of its
# saturation and is veiled VEIL of the way to FILM, a mid grey-brown of dust and grime (dulled): a light dust colour
# turned a dulled orange pastel pink rather than old.
DULL, VEIL, FILM = 0.2, 0.16, (132, 122, 104)
# What paint needs to paint layered materials: contexts (bake.survey's {texel: Texel}), the model's condition, age
# (None: the condition's), environment (None: neutral), roles ({part: role}), usage ({part: {map: amount}}), focus
# (parts), its seed (None: 0), its art (art.Art), its history (history.resolved: [(event, its source's point)]), the
# catalogue of effects ({name: effect}) and, for the critique (semantics.py), a list each painted island is recorded
# in ((part, side, rect, island, image)). catalog passes them from a module; the defaults serve tests and sheets that
# only name a condition.
Surface = namedtuple("Surface",
                     "contexts condition age environment roles usage focus seed art history catalogue record",
                     defaults=(conditions.USED, None, None, {}, {}, (), None, art.Art(), (), CATALOGUE, None))


def layered(material, surface, face, substrate):
    """The image of one island of material, face (part, side, its rect (u, v, w, h) in the texture): substrate (the
    brush's image) calmed to the part's detail budget unless it is a drawing (brushes.drawing: never calmed, only
    deposits planned on it, as on a plant or a liquid: compat.SETTLED_ONLY), coats laid, the most useful planned
    effects and the history's events run on the part (Blockbench's '--C<n>' left out of its name), composed, then
    swelled by the part-wide macro variation and dulled by the condition (dulled); recorded in surface.record when
    there is one, the island then keeping its base (its image before the effects, swelled and dulled likewise:
    illustration.py). Fails without a surface (a layered
    material needs a CONDITION other than DEFAULT)."""
    part, side, (u, v, w, h) = face
    if surface is None or surface.contexts is None:
        raise SystemExit(f"{part} {side}: a layered material needs a model with a CONDITION other than DEFAULT")
    texels = {(i, j): surface.contexts[u + i, v + j] for i in range(w) for j in range(h)}
    seed = surface.seed or 0
    detail = art.detail_of(part, surface.art, surface.focus)
    # A drawing (brushes.drawing) is no noise and no substrate: its every texel stays, and only what settles on it
    # (deposits) and the history's marks reach it, never wear or the ageing of a material; nor do they reach a living
    # plant or a liquid (compat.SETTLED_ONLY), calmed as any substrate.
    drawn = getattr(material.substrate, "drawn", False)
    settled_only = drawn or material.family in compat.SETTLED_ONLY
    island = layers.Island(substrate if drawn else art.calm(substrate, detail), texels, material, side)
    planned = merged(conditions.plan(surface, part), material.effects, part, surface.roles)
    how = effects.Pass(roles.declared(part, surface.roles, surface.usage, surface.focus), seed, surface.art.rest, True,
                       art.rest_floor(surface.condition.show))

    def reach(effect, degree, kept):
        return reachable(effect, degree, island, how, kept)

    uses = most_useful([use for use in planned if not settled_only or use[0].moment == "deposit"], material, detail,
                       reach, surface.condition.show)
    # The image before the effects, for the illustration pass to bring an effect back towards (illustration.py).
    base = layers.compose(island) if surface.record is not None else None
    effects.run(island, uses + history.uses(surface.history), how)
    show = surface.condition.show
    told = set().union(*island.zones.values())
    image = dulled(swelled(layers.compose(island), texels, seed), show, told)
    if surface.record is not None:
        island.base = dulled(swelled(base, texels, seed), show)
        surface.record.append((part, side, (u, v, w, h), island, image))
    return image


def swelled(image, texels, seed):
    """image under the part-wide macro variation (layers.swell), coats and deposits included, so a part's faces join
    up; changed in place and returned."""
    pixels = image.load()
    for (i, j), t in texels.items():
        if pixels[i, j][3]:
            pixels[i, j] = (*layers.swell(pixels[i, j][:3], t.point, seed), pixels[i, j][3])
    return image


def dulled(image, show, told=frozenset()):
    """image greyed by the film of age and dust a model whose condition shows show more effects
    (conditions.Condition.show) carries all over: per step of show past a worn model's, DULL of its saturation gone
    and VEIL of the way to FILM, as a neglected piece's palette goes dull at a glance; unchanged up to worn. The film
    ages the finish, not the story laid on it: the texels of told (effects' zones: rust, wear, deposits) keep their
    colour, so rust still parts from the wood. Changed in place and returned."""
    steps = show - conditions.WORN.show
    if steps <= 0:
        return image
    pixels = image.load()
    width, height = image.size
    for x in range(width):
        for y in range(height):
            if pixels[x, y][3] and (x, y) not in told:
                h, s, v = colorsys.rgb_to_hsv(*(c / 255 for c in pixels[x, y][:3]))
                grey = colorsys.hsv_to_rgb(h, s * max(0.0, 1 - DULL * steps), v)
                veil = min(1.0, VEIL * steps)
                pixels[x, y] = (*(round(255 * c + (d - 255 * c) * veil) for c, d in zip(grey, FILM)),
                                pixels[x, y][3])
    return image


def merged(planned, own, part, role_of):
    """The planned effects (their degrees weighed by the part's role already) with the material's own ((effect,
    degree)) added, weighed by the role likewise; an effect in both takes the larger degree."""
    degrees = {effect.name: (effect, degree) for effect, degree in planned}
    for effect, degree in own:
        degree = min(1.0, degree * roles.weight(part, role_of, effect.name))
        degrees[effect.name] = (effect, max(degree, degrees.get(effect.name, (effect, 0.0))[1]))
    return list(degrees.values())


def most_useful(uses, material, detail, reach=None, extra=0):
    """At most art.VISIBLE[detail] + extra of uses ((effect, degree)), the most useful first: degree (the role's
    weight in it, merged), times the material's own weight for the effect, times the effect's best weight on its
    substrate or a coat (compat). An effect worth nothing is left out, and so is one reach(effect, degree, kept so far)
    says cannot appear on the island (dirt high off the ground): its place goes to the next. Ties keep their order."""
    families = (material.family, *(coat.family for coat in material.coats))

    def useful(use):
        effect, degree = use
        own = material.weights.get(effect.name, 1.0)
        return degree * own * max(compat.weight(effect.name, f) for f in families)

    kept = []
    for use in sorted(uses, key=useful, reverse=True):
        if len(kept) == art.VISIBLE[detail] + extra:
            break
        if useful(use) > 0 and (reach is None or reach(*use, kept)):
            kept.append(use)
    return kept


def reachable(effect, degree, island, how, kept):
    """Whether effect at degree can appear on island in the pass how (effects.Pass): it reaches some texels already,
    or it reads a signal an effect of kept writes (rust on the metal that wear will lay bare)."""
    if any(set(effect.reads) & set(other.writes) for other, _ in kept):
        return True
    return bool(effects.reached(effect, island, degree, how))
