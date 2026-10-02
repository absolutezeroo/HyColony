package dev.hycolony.plugin.item;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.BlockMaterial;
import com.hypixel.hytale.protocol.DrawType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import dev.hycolony.core.kernel.item.BlockKey;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

/**
 * Whether a placeholder fill cell may keep a block (Structurize BlockUtils.isGoodFloorBlock), read from its Hytale
 * block type and cached per key. World thread only.
 */
public final class HytaleGoodFloor {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();

    private final Map<BlockKey, Boolean> cache = new HashMap<>();
    private boolean warned;

    /**
     * A solid block drawn as a full cube ({@code Cube}, or {@code CubeWithModel} as ores are), leaves excluded
     * (Structurize {@code unsuitable_solid_for_placeholder}): vanilla leaves are models of group {@code Leaves}, and
     * tilled soil is a cube ({@code Template_Soil}), as Structurize's {@code good_solid_for_placeholder} wants. A state
     * variant is judged by its own block type (a slab's {@code Full} state is a cube); an unknown block, a fluid or an
     * unreadable one is no good floor; the first failure is logged, the next ones at FINE.
     *
     * <p>Deviation from MC: Structurize tests the collision shape ({@code isGoodFullBlock}); Hytale has no such shape
     * on the server, so the draw type and material stand for it.
     */
    public boolean test(BlockKey block) {
        return cache.computeIfAbsent(block, k -> {
            try {
                BlockType type = BlockType.getAssetMap().getAsset(k.id());
                return type != null
                        && !type.isUnknown()
                        && type.getMaterial() == BlockMaterial.Solid
                        && (type.getDrawType() == DrawType.Cube || type.getDrawType() == DrawType.CubeWithModel)
                        && !"Leaves".equals(type.getGroup());
            } catch (RuntimeException e) {
                LOG.at(warned ? Level.FINE : Level.WARNING).withCause(e).log("Good floor unreadable for %s", k.id());
                warned = true;
                return false;
            }
        });
    }
}
