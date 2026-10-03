package dev.hycolony.plugin.item;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.type.blockhitbox.BlockBoundingBoxes;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.fluid.Fluid;
import com.hypixel.hytale.server.core.asset.type.fluid.FluidTicker;
import com.hypixel.hytale.server.core.universe.world.connectedblocks.CustomConnectedBlockTemplateAsset;
import com.hypixel.hytale.server.core.universe.world.connectedblocks.CustomTemplateConnectedBlockRuleSet;
import dev.hycolony.core.construction.blueprint.PlacementRules;
import dev.hycolony.core.kernel.item.BlockKey;
import dev.hycolony.plugin.block.HytaleBlockStates;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;

/**
 * The facts of a block that the builder's plan matching and costing read, from its Hytale assets and the id-map's
 * {@link BlockFamilies}, cached per key (docs/research/domaine1-suite.md). An unknown or unreadable block has none of
 * the asset facts (the id-map lists still name it); the first failure is logged, the next ones at FINE. World thread
 * only.
 */
public final class HytaleBlockTraits implements PlacementRules {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();
    /** The block group of every tree leaf ({@code Plant_Leaves_*}); the Leaves block set misses them. */
    static final String LEAVES_GROUP = "Leaves";

    private static final Traits NONE = new Traits(false, false, Optional.empty());

    private record Traits(boolean goodFloor, boolean fluidSource, Optional<String> shapeFamily) {}

    private final Set<String> dirt;
    private final Set<String> dirtCells;
    private final Set<String> dirtPaths;
    private final Optional<BlockKey> plainDirt;
    private final Set<String> freeShapeTemplates;
    private final Map<BlockKey, Traits> cache = new HashMap<>();
    private boolean warned;

    public HytaleBlockTraits(BlockFamilies families) {
        this.dirt = families.dirtBlocks();
        this.dirtCells = families.dirtCellBlocks();
        this.dirtPaths = families.dirtPathBlocks();
        this.plainDirt = families.plainDirtBlock().map(BlockKey::new);
        this.freeShapeTemplates = families.freeShapeTemplateIds();
    }

    /**
     * Whether a placeholder fill cell may keep this block (Structurize BlockUtils.isGoodFloorBlock): a full block,
     * leaves excluded (Structurize {@code unsuitable_solid_for_placeholder}). A state variant is judged by its own
     * block type (a slab's {@code Full} state is a cube).
     *
     * <p>Deviation from MC (Hytale world): Structurize's full collision shape → Hytale's own test of a full block,
     * solid and drawn as a cube (FluidTicker.isFullySolid), with the full hitbox ({@code BlockBoundingBoxes.DEFAULT}),
     * so that mud (seven eighths high, as MC's) is none.
     */
    @Override
    public boolean isGoodFloor(BlockKey block) {
        return traits(block).goodFloor();
    }

    /** Whether this block is a fluid source: its fluid's maximum level is 1, where a flowing fluid's is 8. */
    @Override
    public boolean isFluidSource(BlockKey block) {
        return traits(block).fluidSource();
    }

    /** Whether the id-map lists this block in MC's dirt tag. */
    @Override
    public boolean isDirt(BlockKey block) {
        return dirt.contains(block.id());
    }

    /** Whether the id-map lists this block among the plan blocks any dirt answers (grass and dirt). */
    @Override
    public boolean takesAnyDirt(BlockKey block) {
        return dirtCells.contains(block.id());
    }

    /** Whether the id-map lists this block among the dirt paths. */
    @Override
    public boolean isDirtPath(BlockKey block) {
        return dirtPaths.contains(block.id());
    }

    /** The id-map's plain dirt block, MC's {@code Blocks.DIRT}; empty if it has none. */
    @Override
    public Optional<BlockKey> plainDirt() {
        return plainDirt;
    }

    /**
     * The family of a block whose connection template is a free-shape one in the id-map: the template and its first
     * shape's block pattern, the same for every shape of the family; empty for any other block.
     */
    @Override
    public Optional<String> shapeFamily(BlockKey block) {
        return traits(block).shapeFamily();
    }

    private Traits traits(BlockKey block) {
        Traits t = cache.get(block);
        if (t == null) {
            t = read(block.id());
            cache.put(block, t);
        }
        return t;
    }

    private Traits read(String id) {
        try {
            if (id.startsWith(HytaleBlockStates.FLUID_PREFIX)) {
                Fluid fluid = Fluid.getAssetMap().getAsset(id.substring(HytaleBlockStates.FLUID_PREFIX.length()));
                return new Traits(false, fluid != null && fluid.getMaxFluidLevel() == 1, Optional.empty());
            }
            BlockType type = BlockType.getAssetMap().getAsset(id);
            if (type == null || type.isUnknown()) {
                return NONE;
            }
            boolean leaves = LEAVES_GROUP.equals(type.getGroup());
            boolean full = FluidTicker.isFullySolid(type) && BlockBoundingBoxes.DEFAULT.equals(type.getHitboxType());
            return new Traits(full && !leaves, false, family(type));
        } catch (RuntimeException e) {
            LOG.at(warned ? Level.FINE : Level.WARNING).withCause(e).log("Block facts unreadable for %s", id);
            warned = true;
            return NONE;
        }
    }

    private Optional<String> family(BlockType type) {
        if (!(type.getConnectedBlockRuleSet() instanceof CustomTemplateConnectedBlockRuleSet rules)) {
            return Optional.empty();
        }
        CustomConnectedBlockTemplateAsset template = rules.getShapeTemplateAsset();
        if (template == null || !freeShapeTemplates.contains(template.getId())) {
            return Optional.empty();
        }
        return rules.getShapeNameToBlockPatternMap().entrySet().stream()
                .min(Map.Entry.comparingByKey())
                .map(e -> template.getId() + ":" + e.getValue());
    }
}
