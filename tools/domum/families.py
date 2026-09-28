"""Domum Ornamentum's family table: for every ported block family, its DO block ids, material components in
DO's own order and the Hytale mechanism that renders it.

Sources (pinned commit, source.COMMIT): registration ids from `block/ModBlocks.java`; component order from each
block's `COMPONENTS` list (`SimpleRetexturableComponent`, first argument = the model's placeholder texture);
cutter groups from each item's `getGroup()` (`item/decoration/*Item.java`, `item/vanilla/*Item.java`). No base
rotation is stored: assemble.state_model keeps each state's own blockstate rotation, and a facing=north state
already faces Hytale's front (-Z) as Minecraft renders it (check.py: oriented_families_face_minus_z). DO's datagen spells some placeholder
textures with an explicit `minecraft:` namespace and others without; both forms name the same vanilla texture, so
this table always uses the bare path.

Deviation from MC: no dynamic timber frame, framed lights, DO bricks/"extra" blocks, barrels or floating carpets
in DO-1 (see docs/superpowers/specs/2026-09-28-hycolony-domum-ornamentum-do1-design.md, "Hors DO-1"). MC's builder
already requests the dynamic timber frame as `framed` (DoBlockPlacementHandler), so leaving it out changes nothing
there.
"""

from dataclasses import dataclass


@dataclass(frozen=True)
class Family:
    """One Domum Ornamentum block family: its DO ids, material slots and Hytale mechanism."""

    name: str
    group: str
    blocks: tuple[str, ...]
    components: tuple[str, ...]
    mechanism: str
    slot_tags: tuple[str, ...] = ()  # DO tag of each component's slot, in component order (tags.TAG_GROUPS)
    # DO's default material of each component (SimpleRetexturableComponent's third argument) as the Hytale block
    # standing for it; the template draws with these.
    defaults: tuple[str, ...] = ()
    optional_second: bool = False  # DO lets the second slot stay empty: it then repeats the first material
    cutter_quantity: int = 1  # items per architect's cutter craft (A.3, DO-gen recipes/*.json)
    # Extra turn after assembly, degrees about y. A DO trapdoor facing=north hinges on +Z (its open state stands at
    # z 13..16), a vanilla Hytale trapdoor on -Z (Crude Trapdoor.blockymodel: hinge pivots at z -14): 180.
    turn_y: int = 0


FAMILIES = (
    Family(
        name="TimberFrame",
        group="btimberframe",
        # 10 TimberFrameType blocks (block/types/TimberFrameType.java), each its own registration.
        blocks=("plain", "double_crossed", "framed", "side_framed", "up_gated", "down_gated", "one_crossed_lr",
                "one_crossed_rl", "horizontal_plain", "side_framed_horizontal"),
        # TimberFrameBlock.COMPONENTS: frame (timber_frames_frame), centre (timber_frames_center).
        components=("block/oak_planks", "block/dark_oak_planks"),
        mechanism="static",
        slot_tags=("timber_frames_frame", "timber_frames_center"), cutter_quantity=4,
        # DO default: OAK_PLANKS + WHITE_TERRACOTTA.
        defaults=("Wood_Hardwood_Planks", "Soil_Clay_Smooth_White"),
    ),
    Family(
        name="Shingle",
        group="cshingle",
        blocks=("shingle", "shingle_flat", "shingle_flat_lower", "shingle_steep", "shingle_steep_lower"),
        # ShingleBlock.COMPONENTS: roof (shingles_roof), support (shingles_support).
        components=("block/clay", "block/oak_planks"),
        mechanism="roof",
        slot_tags=("shingles_roof", "shingles_support"), cutter_quantity=4,
        # DO default: brick_extra (DO's base brick) + OAK_PLANKS.
        defaults=("Soil_Clay_Brick", "Wood_Hardwood_Planks"),
    ),
    Family(
        name="ShingleSlab",
        group="cshingle",
        blocks=("shingle_slab",),
        # ShingleSlabBlock.COMPONENTS: roof (shingles_roof), support (shingles_support).
        components=("block/oak_planks", "block/dark_oak_planks"),
        mechanism="shingle_slab",
        slot_tags=("shingles_roof", "shingles_support"), cutter_quantity=4,
        # DO default: brick_extra + OAK_PLANKS.
        defaults=("Soil_Clay_Brick", "Wood_Hardwood_Planks"),
    ),
    Family(
        name="Pillar",
        group="gpillar",
        blocks=("blockpillar", "blockypillar", "squarepillar"),
        # PillarBlock.COMPONENTS: single slot (pillar_materials); shared by all three registrations.
        components=("block/oak_planks",),
        mechanism="pillar",
        slot_tags=("pillar_materials",),
        # DO default: STONE_BRICKS.
        defaults=("Rock_Stone_Brick",),
    ),
    Family(
        name="Post",
        group="kpost",
        blocks=("post",),
        # PostBlock.COMPONENTS: single slot (post_materials).
        components=("block/oak_planks",),
        mechanism="static",
        slot_tags=("post_materials",),
        # DO default: OAK_PLANKS.
        defaults=("Wood_Hardwood_Planks",),
    ),
    Family(
        name="Panel",
        group="fpanel",
        blocks=("panel",),
        # PanelBlock.COMPONENTS: single slot (trapdoors_materials).
        components=("block/oak_planks",),
        mechanism="static",
        slot_tags=("trapdoors_materials",), cutter_quantity=4,
        # DO default: STRIPPED_OAK_WOOD.
        defaults=("Wood_Stripped_Deco",),
        turn_y=180,
    ),
    Family(
        name="Door",
        group="ddoor",
        blocks=("vanilla_doors_compat",),
        # block/vanilla/DoorBlock.COMPONENTS: single slot (doors_materials).
        components=("block/oak_planks",),
        mechanism="door",
        slot_tags=("doors_materials",),
        # DO default: STRIPPED_OAK_WOOD.
        defaults=("Wood_Stripped_Deco",),
    ),
    Family(
        name="FancyDoor",
        group="ddoor",
        blocks=("fancy_door",),
        # FancyDoorBlock.COMPONENTS: main slot and an optional second slot, both fancy_doors_materials.
        components=("block/oak_planks", "block/acacia_planks"),
        mechanism="door",
        slot_tags=("fancy_doors_materials", "fancy_doors_materials"), optional_second=True, cutter_quantity=2,
        # DO default: SPRUCE_PLANKS + COPPER_BLOCK.
        defaults=("Wood_Softwood_Planks", "Metal_Copper"),
    ),
    Family(
        name="Trapdoor",
        group="etrapdoor",
        blocks=("vanilla_trapdoors_compat",),
        # block/vanilla/TrapdoorBlock.COMPONENTS: single slot (trapdoors_materials).
        components=("block/oak_planks",),
        mechanism="trapdoor",
        slot_tags=("trapdoors_materials",),
        # DO default: STRIPPED_JUNGLE_WOOD.
        defaults=("Wood_Stripped_Deco",),
        turn_y=180,
    ),
    Family(
        name="FancyTrapdoor",
        group="etrapdoor",
        blocks=("fancy_trapdoors",),
        # FancyTrapdoorBlock.COMPONENTS: two slots (fancy_trapdoors_materials): frame then centre.
        components=("block/oak_planks", "block/acacia_planks"),
        mechanism="trapdoor",
        slot_tags=("fancy_trapdoors_materials", "fancy_trapdoors_materials"), cutter_quantity=2,
        # DO default: STRIPPED_OAK_WOOD + QUARTZ_BLOCK.
        defaults=("Wood_Stripped_Deco", "Rock_Quartzite"),
        turn_y=180,
    ),
    Family(
        name="PaperWall",
        group="hpaperwall",
        blocks=("blockpaperwall", "blocktiledpaperwall"),
        # PaperWallBlock.COMPONENTS: frame (paperwall_frame), centre (paperwall_center).
        components=("block/oak_planks", "block/dark_oak_planks"),
        mechanism="pane",
        slot_tags=("paper_wall_frame", "paper_wall_center"), cutter_quantity=6,
        # DO default: STRIPPED_SPRUCE_WOOD + GLASS. Deviation from MC: Hytale 0.6.8 has no glass block, so the
        # centre defaults to white wool (paper-like) and no pane takes glass.
        defaults=("Wood_Stripped_Deco", "Cloth_Block_Wool_White"),
    ),
    Family(
        name="Fence",
        group="avanilla",
        blocks=("vanilla_fence_compat",),
        # block/vanilla/FenceBlock.COMPONENTS: single slot (fence_materials).
        components=("block/oak_planks",),
        mechanism="vanilla",
        slot_tags=("fence_materials",),
        # DO default: STRIPPED_OAK_WOOD.
        defaults=("Wood_Stripped_Deco",),
    ),
    Family(
        name="FenceGate",
        group="avanilla",
        blocks=("vanilla_fence_gate_compat",),
        # block/vanilla/FenceGateBlock.COMPONENTS: single slot (fence_gate_materials).
        components=("block/oak_planks",),
        mechanism="vanilla",
        slot_tags=("fence_gate_materials",),
        # DO default: STRIPPED_OAK_WOOD.
        defaults=("Wood_Stripped_Deco",),
    ),
    Family(
        name="Wall",
        group="avanilla",
        blocks=("vanilla_wall_compat",),
        # block/vanilla/WallBlock.COMPONENTS: single slot (wall_materials).
        components=("block/oak_planks",),
        mechanism="vanilla",
        slot_tags=("wall_materials",),
        # DO default: STRIPPED_OAK_WOOD.
        defaults=("Wood_Stripped_Deco",),
    ),
    Family(
        name="Stairs",
        group="avanilla",
        blocks=("vanilla_stairs_compat",),
        # block/vanilla/StairBlock.COMPONENTS: single slot (stairs_materials).
        components=("block/oak_planks",),
        mechanism="vanilla",
        slot_tags=("stairs_materials",),
        # DO default: STRIPPED_OAK_WOOD.
        defaults=("Wood_Stripped_Deco",),
    ),
    Family(
        name="Slab",
        group="avanilla",
        blocks=("vanilla_slab_compat",),
        # block/vanilla/SlabBlock.COMPONENTS: single slot (slab_materials).
        components=("block/oak_planks",),
        mechanism="vanilla",
        slot_tags=("slab_materials",), cutter_quantity=2,
        # DO default: STRIPPED_OAK_WOOD.
        defaults=("Wood_Stripped_Deco",),
    ),
    Family(
        name="AllBrick",
        group="jbrick",
        blocks=("light_brick", "dark_brick"),
        # AllBrickBlock.COMPONENTS: single slot (all_brick_materials).
        components=("block/oak_planks",),
        mechanism="static",
        slot_tags=("all_brick_materials",),
        # DO default: POLISHED_ANDESITE (no andesite in 0.6.8: stone bricks).
        defaults=("Rock_Stone_Brick",),
    ),
    Family(
        name="AllBrickStair",
        group="jbrick",
        blocks=("light_brick_stair", "dark_brick_stair"),
        # AllBrickStairBlock.COMPONENTS: single slot (all_brick_materials), same as AllBrick.
        components=("block/oak_planks",),
        mechanism="static",
        slot_tags=("all_brick_materials",),
        # DO default: POLISHED_ANDESITE (no andesite in 0.6.8: stone bricks).
        defaults=("Rock_Stone_Brick",),
    ),
)

