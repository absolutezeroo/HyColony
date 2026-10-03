"""Which effects a material can take (spec 2026-10-03 blockpaint surfaces, compatibility): for each effect that does
not suit every material, the families where it is compatible (full weight) and possible (reduced weight); every
other family is impossible there (iron rusts, copper does not; cloth frays, stone does not). An effect missing here
suits every family."""

# The families of substrates and of coat films.
FAMILIES = ("ferrous", "cuprous", "noble", "wood", "textile", "leather", "hair", "bone", "stone", "ceramic", "glass",
            "wax", "rubber", "paper", "paint_film", "varnish_film", "metal_film")
# The weight of an effect where it is only possible.
POSSIBLE = 0.4
METALS = {"ferrous", "cuprous", "noble", "metal_film"}
FILMS = {"paint_film", "varnish_film"}
# {effect: (compatible families, possible families)}. Wear, chips, scratches, the marks of history and most deposits
# (snow, sand, dust, soot…) lie on anything and are not listed; moss and mold grow only on some.
MATRIX = {
    "rust": ({"ferrous"}, set()),
    "rust_spots": ({"ferrous"}, set()),
    "deep_rust": ({"ferrous"}, set()),
    "rust_pits": ({"ferrous"}, set()),
    "verdigris": ({"cuprous"}, set()),
    # Iron darkens, copper browns, silver tarnishes; gold hardly does.
    "oxidation": ({"ferrous", "cuprous", "metal_film"}, {"noble"}),
    "fraying": ({"textile"}, {"leather", "hair", "paper"}),
    "cracks": ({"stone", "ceramic"} | FILMS, {"wood", "bone", "leather", "glass", "wax"}),
    # The ageing of a film: only paint chalks; a glaze crazes (ceramic), stone spalls.
    "craquelure": (FILMS, {"ceramic"}),
    "chalking": ({"paint_film"}, set()),
    "peeling": ({"paint_film"}, {"varnish_film", "metal_film"}),
    "blistering": ({"paint_film"}, {"varnish_film"}),
    "flaking": (FILMS | {"metal_film"}, {"stone", "ceramic"}),
    # The sun bleaches dyes, paint and wood; it hardly touches metal, stone or glass.
    "fading": ({"paint_film", "textile", "wood", "leather", "paper"}, {"varnish_film", "hair", "bone", "rubber"}),
    "micro_scratches": (METALS | FILMS | {"glass", "ceramic"}, {"wood", "stone", "leather", "bone", "rubber", "wax"}),
    "burn": ({"wood", "textile", "paper", "leather", "hair"},
             FILMS | {"bone", "wax", "rubber", "stone", "ceramic", "ferrous"}),
    "moss": ({"stone", "wood", "ceramic"}, {"glass", "textile"}),
    "mold": ({"wood", "textile", "leather", "paper"}, {"stone", "ceramic", "paint_film", "varnish_film"}),
}


def weight(effect, family):
    """The weight of effect on family: 1 compatible, POSSIBLE possible, 0 impossible; 1 for an effect not listed or a
    layer without family."""
    if effect not in MATRIX or family is None:
        return 1.0
    compatible, possible = MATRIX[effect]
    return 1.0 if family in compatible else POSSIBLE if family in possible else 0.0
