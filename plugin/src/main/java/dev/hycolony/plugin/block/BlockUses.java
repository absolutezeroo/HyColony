package dev.hycolony.plugin.block;

import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.event.events.ecs.UseBlockEvent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.modules.block.components.ItemContainerBlock;
import com.hypixel.hytale.server.core.universe.world.World;
import dev.hycolony.core.colony.permission.BlockUse;
import dev.hycolony.plugin.IdMap;
import java.util.Set;
import org.joml.Vector3i;
import org.jspecify.annotations.Nullable;

/** Describes a Hytale block use as the core {@link BlockUse} rule sees it. World thread only. */
final class BlockUses {
    private final Set<String> toggleables;
    private final Set<String> potions;

    BlockUses(IdMap ids) {
        this.toggleables = ids.toggleableUseInteractions();
        this.potions = ids.potions();
    }

    /** Door or gate (its Use interaction), container, block entity, and what the player holds. */
    BlockUse of(World world, UseBlockEvent.Pre event) {
        BlockType type = event.getBlockType();
        Vector3i target = event.getTargetBlock();
        String useInteraction = type.getInteractions().get(InteractionType.Use);
        return new BlockUse(
                useInteraction != null && toggleables.contains(useInteraction),
                BlockModule.getComponent(ItemContainerBlock.getComponentType(), world, target.x, target.y, target.z)
                        != null,
                type.getBlockEntity() != null,
                held(event.getContext().getHeldItem()));
    }

    private BlockUse.Held held(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return BlockUse.Held.NOTHING;
        }
        if (potions.contains(stack.getItemId())) {
            return BlockUse.Held.POTION;
        }
        return stack.getItem().isConsumable() ? BlockUse.Held.FOOD : BlockUse.Held.OTHER;
    }
}
