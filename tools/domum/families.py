"""Domum Ornamentum's family table: for every ported block family, its DO block ids, material components in
DO's own order, base orientation and the Hytale mechanism that renders it.

Sources (pinned commit, source.COMMIT): registration ids from `block/ModBlocks.java`; component order from each
block's `COMPONENTS` list (`SimpleRetexturableComponent`, first argument = the model's placeholder texture);
cutter groups from each item's `getGroup()` (`item/decoration/*Item.java`, `item/vanilla/*Item.java`); `base_y`
measured on the generated blockstate's `facing=north` variant
(`src/datagen/generated/.../assets/domum_ornamentum/blockstates/*.json`). DO's datagen spells some placeholder
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
    """One Domum Ornamentum block family: its DO ids, material slots, base rotation and Hytale mechanism."""

    name: str
    group: str
    blocks: tuple[str, ...]
    components: tuple[str, ...]
    base_y: int
    mechanism: str


FAMILIES = (
    Family(
        name="TimberFrame",
        group="btimberframe",
        # 10 TimberFrameType blocks (block/types/TimberFrameType.java), each its own registration.
        blocks=("plain", "double_crossed", "framed", "side_framed", "up_gated", "down_gated", "one_crossed_lr",
                "one_crossed_rl", "horizontal_plain", "side_framed_horizontal"),
        # TimberFrameBlock.COMPONENTS: frame (timber_frames_frame), centre (timber_frames_center).
        components=("block/oak_planks", "block/dark_oak_planks"),
        base_y=0,  # DO bs: plain.json, facing=north carries no rotation (a symmetric multipart)
        mechanism="static",
    ),
    Family(
        name="Shingle",
        group="cshingle",
        blocks=("shingle", "shingle_flat", "shingle_flat_lower", "shingle_steep", "shingle_steep_lower"),
        # ShingleBlock.COMPONENTS: roof (shingles_roof), support (shingles_support).
        components=("block/clay", "block/oak_planks"),
        base_y=270,  # DO bs: shingle.json, shape=straight half=bottom facing=north
        mechanism="roof",
    ),
    Family(
        name="ShingleSlab",
        group="cshingle",
        blocks=("shingle_slab",),
        # ShingleSlabBlock.COMPONENTS: roof (shingles_roof), support (shingles_support).
        components=("block/oak_planks", "block/dark_oak_planks"),
        base_y=0,  # DO bs: shingle_slab.json, facing=north carries no rotation
        mechanism="shingle_slab",
    ),
    Family(
        name="Pillar",
        group="gpillar",
        blocks=("blockpillar", "blockypillar", "squarepillar"),
        # PillarBlock.COMPONENTS: single slot (pillar_materials); shared by all three registrations.
        components=("block/oak_planks",),
        base_y=0,  # DO bs: blockpillar.json has no facing property (vertical connection only)
        mechanism="pillar",
    ),
    Family(
        name="Post",
        group="kpost",
        blocks=("post",),
        # PostBlock.COMPONENTS: single slot (post_materials).
        components=("block/oak_planks",),
        base_y=0,  # DO bs: post.json, type=plain facing=north
        mechanism="static",
    ),
    Family(
        name="Panel",
        group="fpanel",
        blocks=("panel",),
        # PanelBlock.COMPONENTS: single slot (trapdoors_materials).
        components=("block/oak_planks",),
        base_y=180,  # DO bs: panel.json, type=full half=bottom open=false facing=north
        mechanism="static",
    ),
    Family(
        name="Door",
        group="ddoor",
        blocks=("vanilla_doors_compat",),
        # block/vanilla/DoorBlock.COMPONENTS: single slot (doors_materials).
        components=("block/oak_planks",),
        base_y=270,  # DO bs: vanilla_doors_compat.json, type=full half=lower hinge=left open=false facing=north
        mechanism="door",
    ),
    Family(
        name="FancyDoor",
        group="ddoor",
        blocks=("fancy_door",),
        # FancyDoorBlock.COMPONENTS: main slot and an optional second slot, both fancy_doors_materials.
        components=("block/oak_planks", "block/acacia_planks"),
        base_y=270,  # DO bs: fancy_door.json, type=full half=lower hinge=left open=false facing=north
        mechanism="door",
    ),
    Family(
        name="Trapdoor",
        group="etrapdoor",
        blocks=("vanilla_trapdoors_compat",),
        # block/vanilla/TrapdoorBlock.COMPONENTS: single slot (trapdoors_materials).
        components=("block/oak_planks",),
        base_y=180,  # DO bs: vanilla_trapdoors_compat.json, type=full half=bottom open=false facing=north
        mechanism="trapdoor",
    ),
    Family(
        name="FancyTrapdoor",
        group="etrapdoor",
        blocks=("fancy_trapdoors",),
        # FancyTrapdoorBlock.COMPONENTS: two slots (fancy_trapdoors_materials): frame then centre.
        components=("block/oak_planks", "block/acacia_planks"),
        base_y=180,  # DO bs: fancy_trapdoors.json, type=full half=bottom open=false facing=north
        mechanism="trapdoor",
    ),
    Family(
        name="PaperWall",
        group="hpaperwall",
        blocks=("blockpaperwall", "blocktiledpaperwall"),
        # PaperWallBlock.COMPONENTS: frame (paperwall_frame), centre (paperwall_center).
        components=("block/oak_planks", "block/dark_oak_planks"),
        base_y=0,  # DO bs: blockpaperwall.json; each direction has its own piece, never rotated
        mechanism="pane",
    ),
    Family(
        name="Fence",
        group="avanilla",
        blocks=("vanilla_fence_compat",),
        # block/vanilla/FenceBlock.COMPONENTS: single slot (fence_materials).
        components=("block/oak_planks",),
        base_y=0,  # DO bs: vanilla_fence_compat.json, north=true carries no rotation
        mechanism="vanilla",
    ),
    Family(
        name="FenceGate",
        group="avanilla",
        blocks=("vanilla_fence_gate_compat",),
        # block/vanilla/FenceGateBlock.COMPONENTS: single slot (fence_gate_materials).
        components=("block/oak_planks",),
        base_y=0,  # DO bs: vanilla_fence_gate_compat.json, in_wall=false open=false facing=north
        mechanism="vanilla",
    ),
    Family(
        name="Wall",
        group="avanilla",
        blocks=("vanilla_wall_compat",),
        # block/vanilla/WallBlock.COMPONENTS: single slot (wall_materials).
        components=("block/oak_planks",),
        base_y=0,  # DO bs: vanilla_wall_compat.json, north=low carries no rotation
        mechanism="vanilla",
    ),
    Family(
        name="Stairs",
        group="avanilla",
        blocks=("vanilla_stairs_compat",),
        # block/vanilla/StairBlock.COMPONENTS: single slot (stairs_materials).
        components=("block/oak_planks",),
        base_y=270,  # DO bs: vanilla_stairs_compat.json, shape=straight half=bottom facing=north
        mechanism="vanilla",
    ),
    Family(
        name="Slab",
        group="avanilla",
        blocks=("vanilla_slab_compat",),
        # block/vanilla/SlabBlock.COMPONENTS: single slot (slab_materials).
        components=("block/oak_planks",),
        base_y=0,  # DO bs: vanilla_slab_compat.json has no facing property (type=top/bottom/double)
        mechanism="vanilla",
    ),
    Family(
        name="AllBrick",
        group="jbrick",
        blocks=("light_brick", "dark_brick", "light_brick_stair", "dark_brick_stair"),
        # AllBrickBlock.COMPONENTS / AllBrickStairBlock.COMPONENTS: single slot (all_brick_materials).
        components=("block/oak_planks",),
        base_y=0,  # the plain block has no facing property; its stair follows the same convention as Stairs
        mechanism="static",
    ),
)


def material(family, texture):
    """The material slot of a component's placeholder texture: 'dark' for the family's first (frame) component,
    'light' for another of its known components, and 'dark' for an unknown texture unless the family has more
    than one component, in which case it is 'light'.

    MC/DO: `MateriallyTexturedBakedModel` retextures each placeholder sprite by component (A.2); this mirrors it
    for a fixed Darkwood (1st component) / Lightwood (rest) pair instead of a player-chosen material.
    """
    if texture == family.components[0]:
        return "dark"
    if texture in family.components:
        return "light"
    return "dark" if len(family.components) == 1 else "light"
