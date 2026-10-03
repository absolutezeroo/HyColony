"""Paints the island of a layered material (spec 2026-10-03 blockpaint surfaces, the single run of operations):
substrate calmed to the part's detail budget (art.py), coats, the most useful of the effects the model's age,
condition, environment and the part's role call for, the events of its history, the composition, then the part-wide
macro variation."""

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
    brush's image) calmed to the part's detail budget, coats laid, the most useful planned effects and the history's
    events run on the part (Blockbench's '--C<n>' left out of its name), composed, then swelled by the part-wide
    macro variation; recorded in surface.record when there is one. Fails without a surface (a layered material needs
    a CONDITION other than DEFAULT)."""
    part, side, (u, v, w, h) = face
    if surface is None or surface.contexts is None:
        raise SystemExit(f"{part} {side}: a layered material needs a model with a CONDITION other than DEFAULT")
    texels = {(i, j): surface.contexts[u + i, v + j] for i in range(w) for j in range(h)}
    seed = surface.seed or 0
    detail = art.detail_of(part, surface.art, surface.focus)
    island = layers.Island(art.calm(substrate, detail), texels, material, side)
    uses = most_useful(merged(conditions.plan(surface, part), material.effects, part, surface.roles), material, detail)
    how = effects.Pass(roles.declared(part, surface.roles, surface.usage, surface.focus), seed, surface.art.rest, True)
    effects.run(island, uses + history.uses(surface.history), how)
    image = layers.compose(island)
    # The macro variation swells the whole part, coats and deposits included, so its faces join up.
    pixels = image.load()
    for (i, j), t in texels.items():
        if pixels[i, j][3]:
            pixels[i, j] = (*layers.swell(pixels[i, j][:3], t.point, seed), pixels[i, j][3])
    if surface.record is not None:
        surface.record.append((part, side, (u, v, w, h), island, image))
    return image


def merged(planned, own, part, role_of):
    """The planned effects (their degrees weighed by the part's role already) with the material's own ((effect,
    degree)) added, weighed by the role likewise; an effect in both takes the larger degree."""
    degrees = {effect.name: (effect, degree) for effect, degree in planned}
    for effect, degree in own:
        degree = min(1.0, degree * roles.weight(part, role_of, effect.name))
        degrees[effect.name] = (effect, max(degree, degrees.get(effect.name, (effect, 0.0))[1]))
    return list(degrees.values())


def most_useful(uses, material, detail):
    """At most art.VISIBLE[detail] of uses ((effect, degree)), the most useful first: degree (the role's weight in
    it, merged), times the material's own weight for the effect, times the effect's best weight on its substrate or a
    coat (compat). An effect worth nothing is left out. Ties keep their order."""
    families = (material.family, *(coat.family for coat in material.coats))

    def useful(use):
        effect, degree = use
        own = material.weights.get(effect.name, 1.0)
        return degree * own * max(compat.weight(effect.name, f) for f in families)

    ranked = sorted(uses, key=useful, reverse=True)
    return [use for use in ranked if useful(use) > 0][:art.VISIBLE[detail]]
