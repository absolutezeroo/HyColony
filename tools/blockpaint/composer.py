"""The value and focus composer (spec 2026-10-04 blockpaint composer): after the illustration pass and before the
light, what an illustrator decides for the whole model: what steps forward and what steps back, where the strongest
contrasts go, which few texels take the deepest darks and the strongest lights, how many accents the model holds, and
which forms and materials must part. It reasons on the whole model (composer_units: units of a part and a family),
adds no material, brush nor effect, and never changes a layer: only the texture's values, each decision reported."""

from collections import namedtuple

import composer_budgets
import composer_values
from composer_units import model_of

# The operations after the accent budget, in the order they run: each reads the texture as the one before left it.
STEPS = ("groups", "background", "focal", "darks", "lights", "routing", "materials", "distance")
# A model's composer: value groups it names ({part: "deep", "dark", "mid", "light" or "accent"}; the others follow
# composer_values.group_of), a list the decisions are reported in (None: not reported) and the operations run.
Composer = namedtuple("Composer", "groups report steps", defaults=({}, None, STEPS))
OPERATIONS = {
    "groups": composer_values.groups, "background": composer_values.background, "focal": composer_values.focal,
    "routing": composer_values.routing, "materials": composer_values.materials, "darks": composer_budgets.darks,
    "lights": composer_budgets.lights, "distance": composer_budgets.distance,
}


def compose(image, records, composer, level):
    """image (the unlit texture of records, surface.layered's, after the illustration pass) composed: the accent budget
    first (composer_budgets.accents), then each of composer's steps; level(part) gives each part's importance
    (illustration.importance_of). Changed in place and returned, the decisions appended to composer.report when it is
    a list."""
    pixels = image.load()
    model, lines = composer_budgets.accents(model_of(pixels, records, level))
    for step in composer.steps:
        lines += OPERATIONS[step](pixels, model, composer)
    if composer.report is not None:
        composer.report.extend(lines)
    return image
