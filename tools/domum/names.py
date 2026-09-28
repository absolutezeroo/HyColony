"""Item ids and English/French names of the test bench's shapes, after Domum Ornamentum's en_us.json."""

# Model folder -> (id part, English, French, prefix dropped from the model's file name).
FAMILIES = {
    "": ("Cutter", "Architect's cutter", "Établi de l'architecte", ""),
    "allbrick": ("AllBrick", "All-brick", "Tout-brique", ""),
    "barrel_dec": ("Barrel", "Barrel", "Tonneau", "barreldeco_"),
    "door": ("Door", "Door", "Porte", "door_"),
    "door/fancy": ("FancyDoor", "Fancy door", "Porte ouvragée", "door_"),
    "framed_light": ("FramedLight", "Framed light", "Lumière encadrée", ""),
    "panel": ("Panel", "Panel", "Panneau", "panel_"),
    "paperwall": ("PaperWall", "Framed pane", "Vitre encadrée", "blockpaperwall_"),
    "tiledpaperwall": ("TiledPaperWall", "Tiled pane", "Vitre carrelée", "blockpaperwall_"),
    "pillar": ("Pillar", "Pillar", "Pilier", ""),
    "post": ("Post", "Post", "Poteau", "post_"),
    "shingle": ("Shingle", "Shingle", "Bardeau", ""),
    "shingle_slab": ("ShingleSlab", "Shingle slab", "Demi-bardeau", "shingle_slab_"),
    "timber_frame": ("TimberFrame", "Timber frame", "Colombage", ""),
    "trapdoor": ("Trapdoor", "Trapdoor", "Trappe", "trapdoor_"),
    "trapdoor/fancy": ("FancyTrapdoor", "Fancy trapdoor", "Trappe ouvragée", "trapdoor_"),
}

# Variant (file name without the family prefix and _spec) -> (English, French).
VARIANTS = {
    "architectscutter": ("", ""),
    "block": ("base block", "bloc de base"),
    "dark_brick": ("dark", "sombre"),
    "dark_brick_stair": ("dark, stairs", "sombre, escalier"),
    "dark_brick_stair_inner": ("dark, inner stairs", "sombre, escalier intérieur"),
    "dark_brick_stair_outer": ("dark, outer stairs", "sombre, escalier extérieur"),
    "light_brick": ("light", "clair"),
    "light_brick_stair": ("light, stairs", "clair, escalier"),
    "light_brick_stair_inner": ("light, inner stairs", "clair, escalier intérieur"),
    "light_brick_stair_outer": ("light, outer stairs", "clair, escalier extérieur"),
    "onside": ("laying", "couché"),
    "standing": ("standing", "debout"),
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
    "center_light": ("center", "centre"),
    "crossed_light": ("crossed", "croisée"),
    "fancy_light": ("fancy", "ouvragée"),
    "four_light": ("four panes", "quatre carreaux"),
    "framed_light": ("framed", "encadrée"),
    "horizontal_light": ("horizontal", "horizontale"),
    "vertical_light": ("vertical", "verticale"),
    "post": ("post", "poteau"),
    "side_north": ("side, north", "côté, nord"),
    "side_south": ("side, south", "côté, sud"),
    "side_east": ("side, east", "côté, est"),
    "side_west": ("side, west", "côté, ouest"),
    "side_off_north": ("open side, north", "côté ouvert, nord"),
    "side_off_south": ("open side, south", "côté ouvert, sud"),
    "side_off_east": ("open side, east", "côté ouvert, est"),
    "side_off_west": ("open side, west", "côté ouvert, ouest"),
    "blockpillar_full_pillar": ("round, full", "rond, complet"),
    "blockpillar_pillar_base": ("round, base", "rond, base"),
    "blockpillar_pillar_capital": ("round, capital", "rond, chapiteau"),
    "blockpillar_pillar_column": ("round, column", "rond, fût"),
    "blockypillar_full_pillar": ("voxel, full", "voxel, complet"),
    "blockypillar_pillar_base": ("voxel, base", "voxel, base"),
    "blockypillar_pillar_capital": ("voxel, capital", "voxel, chapiteau"),
    "blockypillar_pillar_column": ("voxel, column", "voxel, fût"),
    "squarepillar_full_pillar": ("square, full", "carré, complet"),
    "squarepillar_pillar_base": ("square, base", "carré, base"),
    "squarepillar_pillar_capital": ("square, capital", "carré, chapiteau"),
    "squarepillar_pillar_column": ("square, column", "carré, fût"),
    "double": ("double", "double"),
    "heavy": ("heavy", "massif"),
    "pinched": ("pinched", "pincé"),
    "plain": ("plain", "simple"),
    "quad": ("quad", "quadruple"),
    "turned": ("turned", "tourné"),
    "straight": ("straight", "droit"),
    "concave": ("concave", "concave"),
    "convex": ("convex", "convexe"),
    "flat_straight": ("flat, straight", "plat, droit"),
    "flat_concave": ("flat, concave", "plat, concave"),
    "flat_convex": ("flat, convex", "plat, convexe"),
    "flat_lower_straight": ("flat lower, straight", "plat bas, droit"),
    "flat_lower_concave": ("flat lower, concave", "plat bas, concave"),
    "flat_lower_convex": ("flat lower, convex", "plat bas, convexe"),
    "steep_straight": ("steep, straight", "raide, droit"),
    "steep_concave": ("steep, concave", "raide, concave"),
    "steep_convex": ("steep, convex", "raide, convexe"),
    "steep_lower_straight": ("steep lower, straight", "raide bas, droit"),
    "steep_lower_concave": ("steep lower, concave", "raide bas, concave"),
    "steep_lower_convex": ("steep lower, convex", "raide bas, convexe"),
    "top": ("top", "sommet"),
    "curved": ("curved", "courbe"),
    "one_way": ("one way", "une voie"),
    "two_way": ("two way", "deux voies"),
    "three_way": ("three way", "trois voies"),
    "four_way": ("four way", "quatre voies"),
    "double_crossed": ("double crossed", "double croix"),
    "down_gated": ("down gate", "porte basse"),
    "up_gated": ("up gate", "porte haute"),
    "dynamic_timberframe": ("dynamic", "dynamique"),
    "dynamic_timberframe_item": ("dynamic, item", "dynamique, objet"),
    "framed": ("framed", "encadré"),
    "horizontal_plain": ("plain horizontal", "simple horizontal"),
    "one_crossed_lr": ("left right crossed", "croix gauche-droite"),
    "one_crossed_rl": ("right left crossed", "croix droite-gauche"),
    "side_framed": ("side", "côté"),
    "side_framed_horizontal": ("side horizontal", "côté horizontal"),
}


def variant_key(folder, stem):
    prefix = FAMILIES[folder][3]
    stem = stem.removesuffix("_spec")
    return stem[len(prefix):] if prefix and stem.startswith(prefix) else stem


def item_id(folder, stem):
    """HyColony_DO_<Family>[_<Variant>], the variant's words capitalised."""
    variant = variant_key(folder, stem)
    words = "_".join(w.capitalize() for w in variant.split("_")) if VARIANTS[variant][0] else ""
    return "HyColony_DO_" + FAMILIES[folder][0] + ("_" + words if words else "")


def lang_key(folder, stem):
    return "item.do." + item_id(folder, stem)[len("HyColony_DO_"):].lower() + ".name"


def names(folder, stem):
    """(English, French): 'Family (variant)', or the family alone."""
    family = FAMILIES[folder]
    variant = VARIANTS[variant_key(folder, stem)]
    return tuple(f"{family[i]} ({variant[i - 1]})" if variant[i - 1] else family[i] for i in (1, 2))
