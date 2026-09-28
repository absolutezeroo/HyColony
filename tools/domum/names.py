"""Template ids and English/French names of the Domum Ornamentum shapes, after DO's en_us.json."""

PREFIX = "HyColony_DO_"

# Family -> (English, French).
FAMILY_NAMES = {
    "TimberFrame": ("Timber frame", "Colombage"),
    "Shingle": ("Shingle", "Bardeau"),
    "ShingleSlab": ("Shingle slab", "Demi-bardeau"),
    "Pillar": ("Pillar", "Pilier"),
    "Post": ("Post", "Poteau"),
    "Panel": ("Panel", "Panneau"),
    "Door": ("Door", "Porte"),
    "FancyDoor": ("Fancy door", "Porte ouvragée"),
    "Trapdoor": ("Trapdoor", "Trappe"),
    "FancyTrapdoor": ("Fancy trapdoor", "Trappe ouvragée"),
    "PaperWall": ("Framed pane", "Vitre encadrée"),
    "Fence": ("Fence", "Clôture"),
    "FenceGate": ("Fence gate", "Portillon"),
    "Wall": ("Wall", "Muret"),
    "Stairs": ("Stairs", "Escalier"),
    "Slab": ("Slab", "Dalle"),
    "AllBrick": ("All-brick", "Tout-brique"),
    "AllBrickStair": ("All-brick stairs", "Escalier tout-brique"),
}

# DO block id or type value -> (English, French).
VARIANTS = {
    "plain": ("plain", "simple"),
    "double_crossed": ("double crossed", "double croix"),
    "framed": ("framed", "encadré"),
    "side_framed": ("side", "côté"),
    "up_gated": ("up gate", "porte haute"),
    "down_gated": ("down gate", "porte basse"),
    "one_crossed_lr": ("left right crossed", "croix gauche-droite"),
    "one_crossed_rl": ("right left crossed", "croix droite-gauche"),
    "horizontal_plain": ("plain horizontal", "simple horizontal"),
    "side_framed_horizontal": ("side horizontal", "côté horizontal"),
    "light_brick": ("light", "clair"),
    "dark_brick": ("dark", "sombre"),
    "light_brick_stair": ("light", "clair"),
    "dark_brick_stair": ("dark", "sombre"),
    "double": ("double", "double"),
    "heavy": ("heavy", "massif"),
    "pinched": ("pinched", "pincé"),
    "quad": ("quad", "quadruple"),
    "turned": ("turned", "tourné"),
    "full": ("full", "plein"),
    "creeper": ("creeper", "creeper"),
    "port_manteau": ("port manteau", "portemanteau"),
    "vertically_striped": ("vertically striped", "rayé verticalement"),
    "horizontally_striped": ("horizontally striped", "rayé horizontalement"),
    "vertically_squiggly_striped": ("vertical squiggles", "ondulé verticalement"),
    "horizontally_squiggly_striped": ("horizontal squiggles", "ondulé horizontalement"),
    "vertical_bars": ("vertical bars", "barreaux verticaux"),
    "horizontal_bars": ("horizontal bars", "barreaux horizontaux"),
    "waffle": ("waffle", "gaufré"),
    "boss": ("boss", "bossage"),
    "coffer": ("coffer", "caisson"),
    "moulding": ("moulding", "moulure"),
    "porthole": ("porthole", "hublot"),
    "roundel": ("roundel", "médaillon"),
    "slot": ("slot", "fente"),
    "shingle": ("", ""),
    "shingle_flat": ("flat", "plat"),
    "shingle_flat_lower": ("flat lower", "plat bas"),
    "shingle_steep": ("steep", "raide"),
    "shingle_steep_lower": ("steep lower", "raide bas"),
    "blockpillar": ("round", "rond"),
    "blockypillar": ("voxel", "voxel"),
    "squarepillar": ("square", "carré"),
    "blockpaperwall": ("", ""),
    "blocktiledpaperwall": ("tiled", "carrelée"),
}


def camel(word):
    """DO snake_case -> CamelCase (framed -> Framed, one_crossed_lr -> OneCrossedLr)."""
    return "".join(part.capitalize() for part in word.split("_"))


def template_id(family, parts):
    """HyColony_DO_<Family>[_<Part>...]: parts are the DO block id and/or type value that name the variant, those
    with an empty English name (the family's plain block) left out."""
    named = [camel(p) for p in parts if VARIANTS[p][0]]
    return PREFIX + "_".join([family.name] + named)


def lang_key(ident):
    """The item's translation key inside hycolony.lang (the item refers to it prefixed with hycolony.)."""
    return "item.do." + ident[len(PREFIX):].lower() + ".name"


def names(family, parts):
    """(English, French): 'Family (variant, variant)', or the family alone."""
    words = [VARIANTS[p] for p in parts if VARIANTS[p][0]]
    result = []
    for i in (0, 1):
        base = FAMILY_NAMES[family.name][i]
        result.append(f"{base} ({', '.join(w[i] for w in words)})" if words else base)
    return tuple(result)
