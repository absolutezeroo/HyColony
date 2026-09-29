"""Tables de correspondances Minecraft -> Hytale.

C'est ici qu'on ajoute des blocs. Tout est rassemblé à un seul endroit, dans
une seule table : il n'y a plus d'ordre de priorité caché entre fichiers.

- SIMPLE      : blocs non directionnels -> un ID Hytale, rotation 0.
- FAMILY      : blocs directionnels -> adaptateur de famille (families.py).
- SKIP_*      : blocs volontairement retirés (pas de « non mappé » au rapport).
- Motifs      : règles par préfixe/suffixe (couleurs, vitres, panneaux…),
                voir converter.PATTERN_RULES.

Les règles contextuelles (qui regardent les voisins) et les assemblages
multi-blocs (lits, chaises, doubles portes…) sont dans converter.py et
detectors.py.
"""
from __future__ import annotations

from . import families as f
from .model import place

# ---------------------------------------------------------------------------
# Cases qui ne produisent jamais de bloc
# ---------------------------------------------------------------------------

PLACEHOLDERS = {
    "minecraft:air",
    "structurize:blocksubstitution",
    "structurize:blocksolidsubstitution",
    "structurize:blockfluidsubstitution",
    "structurize:blocktagsubstitution",
}
# Tout `minecolonies:blockhut*` est un marqueur de hutte (métadonnée) : géré
# par motif dans is_placeholder(), plus besoin d'ajouter chaque hutte.


def is_placeholder(name: str) -> bool:
    return name in PLACEHOLDERS or name.startswith("minecolonies:blockhut")


# Petits détails sans équivalent propre : on les retire plutôt que de les
# remplacer par un cube plein.
REMOVED = {
    "minecraft:stone_pressure_plate": "plaque de pression",
    "minecraft:oak_pressure_plate": "plaque de pression",
    "minecraft:heavy_weighted_pressure_plate": "plaque de pression",
    "minecraft:light_weighted_pressure_plate": "plaque de pression",
    "minecraft:lever": "levier",
    "minecraft:dropper": "dropper",
    "minecraft:tripwire_hook": "crochet de fil",
    "minecraft:stone_button": "bouton",
    "minecraft:oak_button": "bouton",
    "minecraft:bell": "cloche (pas d'équivalent 1x1 validé)",
    # Végétation fragile : dépend d'un support, on ne l'exporte pas.
    "minecraft:oak_leaves": "végétation",
    "minecraft:poppy": "végétation",
    "minecraft:azure_bluet": "végétation",
    "minecraft:red_tulip": "végétation",
}

# ---------------------------------------------------------------------------
# Blocs de support plein (source) : un torche murale n'est exportée que si
# l'un d'eux est derrière elle ; sert aussi à deviner le mur d'une étagère.
# ---------------------------------------------------------------------------

FULL_SUPPORT_SOURCE = {
    "minecraft:cobblestone", "minecraft:mossy_cobblestone",
    "minecraft:stone", "minecraft:andesite",
    "minecraft:stone_bricks", "minecraft:cracked_stone_bricks", "minecraft:mossy_stone_bricks",
    "minecraft:polished_andesite", "minecraft:polished_diorite", "minecraft:polished_granite",
    "minecraft:bricks",
    "minecraft:oak_planks", "minecraft:birch_planks", "minecraft:spruce_planks", "minecraft:dark_oak_planks",
    "minecraft:oak_log", "minecraft:birch_log", "minecraft:spruce_log", "minecraft:dark_oak_log",
    "minecraft:stripped_oak_log", "minecraft:stripped_oak_wood",
    "minecraft:red_terracotta", "minecraft:black_terracotta",
    "minecraft:red_concrete", "minecraft:yellow_concrete", "minecraft:gray_concrete",
    "minecraft:light_gray_concrete",
    "domum_ornamentum:plain", "domum_ornamentum:beige_bricks",
    "domum_ornamentum:one_crossed_lr", "domum_ornamentum:one_crossed_rl",
    "domum_ornamentum:double_crossed",
    "domum_ornamentum:side_framed", "domum_ornamentum:side_framed_horizontal",
}

# ---------------------------------------------------------------------------
# Blocs non directionnels
# ---------------------------------------------------------------------------

_TIMBER = "Wood_Village_Wall_White_Full"
_CRATE = "Furniture_Village_Crate"

SIMPLE: dict[str, tuple[str, str | None]] = {
    # Bois
    "minecraft:oak_planks": ("Wood_Hardwood_Planks", None),
    "minecraft:dark_oak_planks": ("Wood_Hardwood_Planks", None),
    "minecraft:birch_planks": ("Wood_Lightwood_Planks", None),
    "minecraft:spruce_planks": ("Wood_Softwood_Planks", None),
    "minecraft:scaffolding": ("Wood_Hardwood_Beam", "échafaudage approximé en poutre"),
    # Pierre
    "minecraft:stone": ("Rock_Stone", None),
    "minecraft:cobblestone": ("Rock_Stone_Cobble", None),
    "minecraft:mossy_cobblestone": ("Rock_Stone_Cobble_Mossy", None),
    "minecraft:stone_bricks": ("Rock_Stone_Brick", None),
    "minecraft:cracked_stone_bricks": ("Rock_Stone_Brick", "fissures omises"),
    "minecraft:mossy_stone_bricks": ("Rock_Stone_Brick_Mossy", None),
    "minecraft:chiseled_stone_bricks": ("Rock_Stone_Brick_Decorative", None),
    "minecraft:andesite": ("Rock_Stone_Brick_Smooth", None),
    "minecraft:polished_andesite": ("Rock_Stone_Brick_Smooth", None),
    "minecraft:polished_diorite": ("Rock_Stone_Brick_Smooth", None),
    "minecraft:polished_granite": ("Rock_Stone_Brick_Smooth", None),
    "minecraft:bricks": ("Soil_Clay_Brick", None),
    "minecraft:sandstone": ("Rock_Sandstone", None),
    "minecraft:cut_sandstone": ("Rock_Sandstone_Brick", None),
    "minecraft:chiseled_sandstone": ("Rock_Sandstone_Brick", None),
    "minecraft:quartz_block": ("Rock_Lime_Brick", "quartz approximé en brique calcaire"),
    "minecraft:chiseled_quartz_block": ("Rock_Lime_Brick", "quartz approximé en brique calcaire"),
    "minecraft:quartz_pillar": ("Rock_Lime_Brick", "pilier de quartz : axe ignoré"),
    "minecraft:end_stone_bricks": ("Rock_Lime_Brick", None),
    "minecraft:prismarine": ("Rock_Aqua_Brick", None),
    "minecraft:prismarine_bricks": ("Rock_Aqua_Brick", None),
    "minecraft:dark_prismarine": ("Rock_Aqua_Brick", None),
    "minecraft:lapis_block": ("Rock_Aqua_Brick", None),
    "minecraft:purpur_block": ("Rock_Runic_Dark_Brick", None),
    "minecraft:purpur_pillar": ("Rock_Runic_Dark_Brick", None),
    **{f"minecraft:{n}": ("Rock_Volcanic", None) for n in (
        "netherrack", "blackstone", "chiseled_polished_blackstone", "nether_bricks",
        "red_nether_bricks", "magma_block", "crying_obsidian", "obsidian")},
    # Sol
    "minecraft:dirt": ("Soil_Dirt", None),
    "minecraft:dirt_path": ("Soil_Pathway", "chemin -> chemin de terre natif"),
    "minecraft:coarse_dirt": ("Soil_Dirt_Dry", "terre grossière -> terre sèche"),
    "minecraft:grass_block": ("Soil_Grass", None),
    "minecraft:grass": ("Plant_Grass_Lush", "herbe haute -> Plant_Grass_Lush"),
    "minecraft:short_grass": ("Plant_Grass_Lush", "herbe haute -> Plant_Grass_Lush"),  # nom depuis MC 1.20.3
    "minecraft:gravel": ("Soil_Gravel", None),
    "minecraft:sand": ("Soil_Sand", None),
    "minecraft:soul_sand": ("Soil_Sand", None),
    # Mobilier et utilitaires 1x1
    "minecraft:crafting_table": (_CRATE, "établi -> caisse 1x1"),
    "minecraft:cartography_table": (_CRATE, "table de cartographie -> caisse 1x1"),
    "minecraft:jukebox": (_CRATE, "jukebox -> caisse 1x1"),
    "minecraft:lectern": (_CRATE, "pupitre -> caisse 1x1 (orientation ignorée)"),
    "minecraft:barrel": ("Furniture_Tavern_Barrel", "tonneau natif (rotation aléatoire côté Hytale)"),
    "domum_ornamentum:blockbarreldeco_standing": ("Furniture_Tavern_Barrel", None),
    "domum_ornamentum:blockbarreldeco_onside": ("Furniture_Tavern_Barrel", "tonneau couché redressé"),
    "minecraft:composter": ("Furniture_Village_Planter", None),
    "minecraft:cauldron": ("Alchemy_Cauldron", None),
    "minecraft:water_cauldron": ("Alchemy_Cauldron", "niveau d'eau non conservé"),
    "minecraft:furnace": ("Rock_Stone_Brick", "four gardé en 1x1 (Bench_Furnace est multi-cases)"),
    "minecraft:smoker": ("Rock_Stone_Brick_Smooth", "fumoir gardé en 1x1"),
    "minecraft:torch": ("Furniture_Crude_Torch", None),
    "minecraft:glowstone": ("Deco_Lantern", "pierre lumineuse -> lanterne"),
    # Domum Ornamentum
    "domum_ornamentum:plain": ("Wood_Hardwood_Planks", None),
    "domum_ornamentum:beige_bricks": ("Rock_Lime_Brick", None),
    "domum_ornamentum:framed": (_TIMBER, None),
    "domum_ornamentum:horizontal_plain": (_TIMBER, None),
    "domum_ornamentum:side_framed": (_TIMBER, None),
    "domum_ornamentum:side_framed_horizontal": (_TIMBER, None),
    "domum_ornamentum:one_crossed_lr": (_TIMBER, None),
    "domum_ornamentum:one_crossed_rl": (_TIMBER, None),
    "domum_ornamentum:double_crossed": (_TIMBER, None),
    "domum_ornamentum:shingle_slab": ("Wood_Hardwood_Roof_Flat", "Roof_Flat ne tourne pas"),
}

# ---------------------------------------------------------------------------
# Blocs directionnels : nom -> adaptateur(props) -> Mapping
# ---------------------------------------------------------------------------

_HARDWOOD_STAIRS = f.stairs("Wood_Hardwood_Stairs")
_STONE_BRICK_STAIRS = f.stairs("Rock_Stone_Brick_Stairs")
_SMOOTH_HALF = f.slab("Rock_Stone_Brick_Smooth_Half")

FAMILY = {
    # Troncs, bois et bois écorcés : générés depuis WOOD_SPECIES (plus bas).
    # Escaliers
    "minecraft:oak_stairs": _HARDWOOD_STAIRS,
    "minecraft:spruce_stairs": _HARDWOOD_STAIRS,
    "minecraft:dark_oak_stairs": _HARDWOOD_STAIRS,
    "domum_ornamentum:vanilla_stairs_compat": _HARDWOOD_STAIRS,
    "minecraft:cobblestone_stairs": f.stairs("Rock_Stone_Cobble_Stairs"),
    "minecraft:stone_brick_stairs": _STONE_BRICK_STAIRS,
    "minecraft:stone_stairs": _STONE_BRICK_STAIRS,
    "minecraft:mossy_stone_brick_stairs": _STONE_BRICK_STAIRS,
    "minecraft:polished_andesite_stairs": _STONE_BRICK_STAIRS,
    "minecraft:polished_diorite_stairs": _STONE_BRICK_STAIRS,
    "minecraft:brick_stairs": f.stairs("Soil_Clay_Brick_Stairs"),
    "minecraft:nether_brick_stairs": f.stairs("Rock_Runic_Dark_Brick_Stairs"),
    "domum_ornamentum:shingle": f.stairs("Wood_Hardwood_Roof", "bardeau Domum -> toit Hardwood"),
    # Dalles
    "minecraft:oak_slab": f.slab("Wood_Hardwood_Planks_Half"),
    "minecraft:spruce_slab": f.slab("Wood_Hardwood_Planks_Half"),
    "minecraft:dark_oak_slab": f.slab("Wood_Hardwood_Planks_Half"),
    "minecraft:cobblestone_slab": f.slab("Rock_Stone_Cobble_Half"),
    "minecraft:mossy_cobblestone_slab": f.slab("Rock_Stone_Cobble_Mossy_Half"),
    "minecraft:stone_brick_slab": f.slab("Rock_Stone_Brick_Half"),
    "minecraft:brick_slab": f.slab("Soil_Clay_Brick_Half"),
    "minecraft:smooth_stone_slab": _SMOOTH_HALF,
    "minecraft:andesite_slab": _SMOOTH_HALF,
    "minecraft:polished_andesite_slab": _SMOOTH_HALF,
    "minecraft:polished_diorite_slab": _SMOOTH_HALF,
    # Murs et barrières
    "minecraft:cobblestone_wall": f.wall("Rock_Stone_Cobble_Wall"),
    "minecraft:stone_brick_wall": f.wall("Rock_Stone_Brick_Wall"),
    "minecraft:andesite_wall": f.wall("Rock_Stone_Brick_Wall", "mur d'andésite approximé en brique de pierre"),
    "minecraft:brick_wall": f.wall("Soil_Clay_Brick_Wall"),
    # minecraft:oak_fence : règle contextuelle (voisins réels), voir converter.fence_in_context.
    "minecraft:oak_fence_gate": f.fence_gate,
    "minecraft:iron_bars": f.iron_bars,
    # Adossés au mur
    "minecraft:ladder": f.wall_backed("Furniture_Village_Ladder"),
    # Orientés comme la source
    # Petit coffre : sa face avant (loquet) est en +Z à la rotation 0 comme le grand coffre (Chest_Small.blockymodel,
    # Chest_Open.blockyanim), d'où le demi-tour de families.CHEST_YAW_OFFSET. À confirmer en jeu à côté d'un grand.
    "minecraft:chest": f.chest("Furniture_Crude_Chest_Small", "coffre -> petit coffre natif"),
    "minecraft:ender_chest": f.chest("Furniture_Crude_Chest_Small", "coffre de l'Ender -> petit coffre natif"),
    "minecraft:hopper": f.facing("Metal_Iron_Pipe_Large_Mouthpiece", "entonnoir -> embout de tuyau"),
    "minecraft:campfire": f.facing("Bench_Campfire"),
    # Divers
    "minecraft:chain": f.chain("Deco_Iron_Chain_Small", "chaîne -> petite chaîne native"),
    "minecraft:lantern": f.lantern,
    "minecraft:rail": f.rail,
    "domum_ornamentum:panel": f.domum_panel,
    # Botte de foin : le cube de foin Hytale ne tourne pas (pas de VariantRotation), elle reste debout.
    "minecraft:hay_block": lambda p: place("Ingredient_Hay", 0, "botte de foin -> Ingredient_Hay (posée debout)",
                                           rule="simple"),
}

# ---------------------------------------------------------------------------
# Essences de bois : une ligne par essence, les 4 formes en sont déduites.
#   <essence>_log            -> tronc Hytale (axe conservé)
#   <essence>_wood           -> même tronc en version _Full (écorce sur 6 faces)
#   stripped_<essence>_log   -> Wood_<type>_Decorative, dans le même type de bois
#   stripped_<essence>_wood     que les planches de l'essence (reste assorti)
# Tous marqués support=15 (posés par un joueur : pas d'abattage comme un arbre).
# ---------------------------------------------------------------------------

# essence -> (tronc Hytale, type de bois des planches, note)
WOOD_SPECIES: dict[str, tuple[str, str, str | None]] = {
    "oak": ("Wood_Oak_Trunk", "Hardwood", None),
    "spruce": ("Wood_Cedar_Trunk", "Softwood", None),
    "birch": ("Wood_Birch_Trunk", "Lightwood", None),
    "dark_oak": ("Wood_Oak_Trunk", "Hardwood", "chêne noir approximé en chêne"),
    "jungle": ("Wood_Jungle_Trunk", "Tropicalwood", None),
    "acacia": ("Wood_Beech_Trunk", "Tropicalwood", "acacia approximé en hêtre (choix HytalesHub)"),
    "mangrove": ("Wood_Banyan_Trunk", "Tropicalwood", "palétuvier approximé en banian (choix HytalesHub)"),
}
# Pas encore d'équivalent choisi : cherry, pale_oak, crimson/warped (stems).
# Ils restent « non mappés » dans le rapport.
# (Wood_Stripped_Deco existe mais il est blanc : écarté.)


def _wood_rules() -> dict:
    rules = {}
    for sp, (trunk, planks, note) in WOOD_SPECIES.items():
        rules[f"minecraft:{sp}_log"] = f.trunk(trunk, note)
        rules[f"minecraft:{sp}_wood"] = f.trunk(f"{trunk}_Full", "bois (écorce sur 6 faces) -> tronc _Full")
        for form in ("log", "wood"):
            rules[f"minecraft:stripped_{sp}_{form}"] = f.trunk(
                f"Wood_{planks}_Decorative", f"bois écorcé -> Wood_{planks}_Decorative")
    return rules


FAMILY.update(_wood_rules())
# Toutes les formes de bois sont des blocs pleins : elles portent une torche murale.
FULL_SUPPORT_SOURCE.update(_wood_rules())

# ---------------------------------------------------------------------------
# Couleurs : béton / laine / terre cuite -> argile Hytale
# ---------------------------------------------------------------------------

# Couleurs d'argile qui existent vraiment dans Hytale (vérifié contre la liste
# d'IDs). `None` = argile de base (marron naturel).
DYE_TO_CLAY = {
    "black": "Black", "blue": "Blue", "cyan": "Cyan", "light_blue": "Cyan",
    "gray": "Grey", "grey": "Grey", "green": "Green", "lime": "Lime",
    "magenta": "Purple", "purple": "Purple", "pink": "Pink", "red": "Red",
    "white": "White", "yellow": "Yellow", "brown": None,
}
_CLAY_SUFFIXES = [  # (suffixe, lisse ?) — l'ordre compte
    ("_concrete_powder", False), ("_glazed_terracotta", True),
    ("_terracotta", False), ("_concrete", True), ("_wool", True),
]


def colored_clay(name: str):
    """Renvoie (cible, note) pour un bloc coloré Minecraft, sinon None.

    Béton / laine / terre cuite émaillée -> Soil_Clay_Smooth_<couleur>
    Terre cuite / poudre de béton        -> Soil_Clay_<couleur>
    """
    if not name.startswith("minecraft:"):
        return None
    block = name.split(":", 1)[1]
    if block == "terracotta":
        return "Soil_Clay", "terre cuite -> argile de base"
    for suffix, smooth in _CLAY_SUFFIXES:
        if block.endswith(suffix):
            color = block[: -len(suffix)]
            break
    else:
        return None
    # Soil_Clay_Grey2 n'existe pas : le gris clair utilise l'argile grise
    # ordinaire (pas lisse) pour rester distinct du gris foncé lisse.
    if color == "light_gray":
        return "Soil_Clay_Grey", f"{block} -> Soil_Clay_Grey (Grey2 n'existe pas)"
    # Soil_Clay_Yellow2 n'existe pas non plus : l'orange utilise l'argile
    # orange ordinaire.  À VÉRIFIER VISUELLEMENT en jeu.
    if color == "orange":
        return "Soil_Clay_Orange", f"{block} -> Soil_Clay_Orange (Yellow2 n'existe pas)"
    mapped = DYE_TO_CLAY.get(color)
    if mapped is None:  # marron ou couleur inconnue
        if smooth:
            return "Soil_Clay_Smooth_Orange", f"{block} -> argile lisse marron (nommée Orange côté Hytale)"
        return "Soil_Clay", f"{block} -> argile de base marron"
    target = ("Soil_Clay_Smooth_" if smooth else "Soil_Clay_") + mapped
    return target, f"{block} -> {target}"


# Blocs colorés d'autres mods (même suffixes) : approximation en brique d'argile.
MODDED_COLORED_SUFFIXES = ("_wool", "_carpet", "_terracotta", "_concrete", "_concrete_powder")


def simple_rule(name: str):
    target, note = SIMPLE[name]
    return lambda p: place(target, 0, note, rule="simple")
