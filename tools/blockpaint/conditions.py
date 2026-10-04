"""How old a model is, what state it is in and where it lived (spec 2026-10-03 blockpaint surfaces § 3.3, age,
condition, environment): the age sets the effects of time (rust, fading, patina, cracks), the condition those of use
and of care (wear, chips, scratches, dirt, grime, dust) and how far care holds time and place back, the environment
what is plausible there and what the place itself lays (snow, sand, moss, soot). Together with the part's role they
give each effect its degree."""

from collections import namedtuple

import roles

# A condition: the degree of each effect of use and neglect, how much of time's and the place's effects it lets
# through (care: 0 keeps rust and moss away, 1 lets it all), the bake's own edge chips and foot grime (bake.survey's
# wear and grime: 1, 1 is the render of models that declare nothing), the age it implies when the model declares
# none, and how many more effects each island may show (art.VISIBLE's, plus show): a neglected object shows more of
# its story than a used one, which the effects of use and neglect would otherwise crowd out; show also raises its rest
# zones' floor (art.rest_floor) and past a worn one dulls it all over (surface.dulled).
Condition = namedtuple("Condition", "name degrees care wear grime age show", defaults=(0,))
# An age: the degree of each effect of time.
Age = namedtuple("Age", "name degrees")
# An environment: the weight it puts on each effect (1 when missing, but 0 for an effect of PLACED) and the degree of
# the effects the place lays itself.
Environment = namedtuple("Environment", "name weights degrees", defaults=({},))
# Effects that need a place: none of them shows unless the environment weighs or lays it (moss needs damp, snow cold,
# soot fire, grease machines). A model's material may still declare one as its own (effects.material).
PLACED = frozenset({"mud", "grease", "oil", "soot", "ash", "sand", "snow", "moss", "mold"})

NEW = Age("new", {})
MATURE = Age("mature", {"rust": 0.3, "fading": 0.2, "oxidation": 0.2, "verdigris": 0.2, "chalking": 0.1})
OLD = Age("old", {"rust": 0.6, "fading": 0.4, "chalking": 0.3, "craquelure": 0.3, "peeling": 0.3, "cracks": 0.2,
                  "oxidation": 0.4, "verdigris": 0.4, "rust_spots": 0.3, "rust_streaks": 0.3, "moss": 0.2,
                  "mold": 0.1})
ANCIENT = Age("ancient", {"rust": 0.9, "fading": 0.6, "chalking": 0.5, "craquelure": 0.5, "peeling": 0.6,
                          "flaking": 0.5, "blistering": 0.3, "cracks": 0.5, "oxidation": 0.6, "verdigris": 0.7,
                          "rust_spots": 0.5, "deep_rust": 0.6, "rust_pits": 0.5, "rust_streaks": 0.6, "moss": 0.4,
                          "mold": 0.3})

# The bake's light of today: the render of every model that declares nothing.
DEFAULT = Condition("default", {}, 0.0, 1.0, 1.0, NEW)
PRISTINE = Condition("pristine", {}, 0.0, 0.0, 0.0, NEW)
MAINTAINED = Condition("maintained", {"edge_wear": 0.15, "chips": 0.05, "grime": 0.1, "dust": 0.05,
                                      "micro_scratches": 0.1}, 0.3, 0.0, 0.0, MATURE)
USED = Condition("used", {"edge_wear": 0.3, "chips": 0.15, "dirt": 0.25, "grime": 0.3, "dust": 0.15, "scuffs": 0.2,
                          "scratches": 0.15, "micro_scratches": 0.2, "fraying": 0.1}, 0.7, 0.0, 0.0, MATURE)
WORN = Condition("worn", {"edge_wear": 0.55, "chips": 0.4, "dirt": 0.5, "grime": 0.5, "dust": 0.3, "scuffs": 0.4,
                          "scratches": 0.35, "micro_scratches": 0.3, "fraying": 0.3}, 1.0, 0.0, 0.0, MATURE, 1)
NEGLECTED = Condition("neglected", {"edge_wear": 0.6, "chips": 0.55, "dirt": 0.7, "grime": 0.8, "dust": 0.8,
                                    "scuffs": 0.45, "scratches": 0.4, "fraying": 0.45}, 1.0, 0.0, 0.0, OLD, 2)
RUINED = Condition("ruined", {"edge_wear": 0.85, "chips": 0.85, "dirt": 0.85, "grime": 0.9, "dust": 0.9,
                              "scuffs": 0.7, "scratches": 0.6, "fraying": 0.7}, 1.0, 0.0, 0.0, ANCIENT, 3)

# Indoors and dry: dust settles, nothing grows, little rusts, the sun does not fade.
DRY_INTERIOR = Environment("dry_interior", {"dirt": 0.3, "rust": 0.5, "dust": 1.3, "fading": 0.3})
# A cellar, a bath house: damp air, mould, faster rust.
HUMID_INTERIOR = Environment("humid_interior", {"rust": 1.3, "dust": 0.8, "fading": 0.3, "mold": 1.0},
                             {"mold": 0.4})
TEMPERATE_OUTDOOR = Environment("temperate_outdoor", {"dirt": 1.0, "rust": 1.2, "dust": 0.6, "fading": 1.2,
                                                      "moss": 1.0, "mud": 1.0}, {"mud": 0.25})
# Shade and damp under trees: moss first.
FOREST = Environment("forest", {"rust": 1.1, "fading": 0.6, "dust": 0.4, "moss": 1.3, "mold": 1.0, "mud": 1.0},
                     {"moss": 0.35, "mud": 0.25})
SWAMP = Environment("swamp", {"rust": 1.5, "verdigris": 1.4, "dust": 0.2, "moss": 1.3, "mold": 1.3, "mud": 1.0},
                    {"mud": 0.6, "moss": 0.4, "mold": 0.4})
# Sun and wind: paint fades and chalks, wood cracks, sand drifts, little rusts.
DESERT = Environment("desert", {"rust": 0.3, "verdigris": 0.5, "fading": 1.6, "chalking": 1.5, "cracks": 1.4,
                                "dust": 1.2, "sand": 1.0}, {"sand": 0.5})
# Salt air: metals corrode fastest and paint blisters (ISO 9223 rates the seaside C4 to C5, a dry interior C1).
COASTAL = Environment("coastal", {"rust": 1.6, "verdigris": 1.6, "fading": 1.3, "peeling": 1.3, "blistering": 1.3,
                                  "sand": 1.0}, {"sand": 0.3})
# Frost splits what holds water; snow lies on whatever faces the sky.
SNOW = Environment("snow", {"rust": 0.8, "fading": 0.8, "dust": 0.2, "cracks": 1.3, "snow": 1.0}, {"snow": 0.6})
# Forges and workshops: soot, ash, grease on what works.
INDUSTRIAL = Environment("industrial", {"dirt": 1.2, "grime": 1.4, "rust": 1.3, "soot": 1.0, "ash": 1.0,
                                        "grease": 1.0, "oil": 1.0}, {"soot": 0.5, "ash": 0.25, "grease": 0.35,
                                                                     "oil": 0.25})
ENVIRONMENTS = {e.name: e for e in (DRY_INTERIOR, HUMID_INTERIOR, TEMPERATE_OUTDOOR, FOREST, SWAMP, DESERT, COASTAL,
                                    SNOW, INDUSTRIAL)}


def plan(surface, part=None):
    """[(effect, degree)] of the effects of surface's catalogue ({name: effect}) on a part, from surface's condition,
    age (the condition's own when None), environment and roles (surface.Surface): the condition's degree plus the
    age's and the environment's, both held back by care, weighed by the environment (an effect of PLACED only where
    it names it) and by the part's role, at most 1; effects at degree 0 are left out."""
    condition, environment = surface.condition, surface.environment
    age = surface.age or condition.age
    weights, laid = (environment.weights, environment.degrees) if environment else ({}, {})
    uses = []
    for name, effect in surface.catalogue.items():
        degree = condition.degrees.get(name, 0.0) + (age.degrees.get(name, 0.0) + laid.get(name, 0.0)) * condition.care
        degree *= weights.get(name, 0.0 if name in PLACED else 1.0)
        degree *= roles.weight(part, surface.roles, name)
        if degree > 0:
            uses.append((effect, min(1.0, degree)))
    return uses
