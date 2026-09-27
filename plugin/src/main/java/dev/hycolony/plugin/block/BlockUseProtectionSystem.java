package dev.hycolony.plugin.block;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.event.events.ecs.UseBlockEvent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.modules.block.components.ItemContainerBlock;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hycolony.core.colony.ColonyManager;
import dev.hycolony.core.colony.permission.Action;
import dev.hycolony.core.colony.permission.BlockUse;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.port.Msg;
import dev.hycolony.plugin.IdMap;
import dev.hycolony.plugin.WorldRuntime;
import dev.hycolony.plugin.WorldRuntimes;
import dev.hycolony.plugin.adapter.HytaleNotifier;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nonnull;
import org.joml.Vector3i;
import org.jspecify.annotations.Nullable;

/**
 * Using any block inside a colony (MC ColonyPermissionEventHandler.on(PlayerInteractEvent)): describes the use for the
 * core {@link BlockUse} rule and cancels it when the player's rank lacks the action, with MC's denial message. A
 * cancelled use makes UseBlock fail: the held item's fallback (placing a block, breaking it) then meets the place and
 * break protection. Hut blocks are left to HutBlockSystems.
 */
public final class BlockUseProtectionSystem extends EntityEventSystem<EntityStore, UseBlockEvent.Pre> {
    private final WorldRuntimes runtimes;
    private final Set<String> hutBlockIds;
    private final Set<String> toggleables;
    private final Set<String> potionCategories;

    public BlockUseProtectionSystem(WorldRuntimes runtimes, IdMap ids) {
        super(UseBlockEvent.Pre.class);
        this.runtimes = runtimes;
        this.hutBlockIds = HutBlockSystems.byBlockId(runtimes.setup()).keySet();
        this.toggleables = ids.toggleableUseInteractions();
        this.potionCategories = ids.potionCategories();
    }

    @Override
    public Query<EntityStore> getQuery() {
        return PlayerRef.getComponentType();
    }

    @Override
    public void handle(
            int index,
            @Nonnull ArchetypeChunk<EntityStore> chunk,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> buffer,
            @Nonnull UseBlockEvent.Pre event) {
        BlockType type = event.getBlockType();
        if (event.isCancelled() || hutBlockIds.contains(type.getId())) {
            return;
        }
        World world = store.getExternalData().getWorld();
        WorldRuntime rt = runtimes.of(world);
        PlayerRef player = HutBlockSystems.player(index, chunk, store);
        if (rt == null || !rt.enabled() || player == null) {
            return;
        }
        Vector3i target = event.getTargetBlock();
        BlockUse use = new BlockUse(
                toggleables.contains(type.getInteractions().get(InteractionType.Use)),
                BlockModule.getComponent(ItemContainerBlock.getComponentType(), world, target.x, target.y, target.z)
                        != null,
                type.getBlockEntity() != null,
                held(event.getContext().getHeldItem()));
        BlockPos pos = HutBlockSystems.pos(target);
        ColonyManager manager = rt.manager();
        Optional<Action> refused =
                use.refused(action -> manager.isAllowed(player.getUuid(), pos, action), manager.protectionEnabled());
        if (refused.isPresent()) {
            event.setCancelled(true);
            player.sendMessage(HytaleNotifier.toMessage(Msg.of(
                    "hycolony.permission.denied", manager.colonyAt(pos).get().name())));
        }
    }

    private BlockUse.Held held(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return BlockUse.Held.NOTHING;
        }
        String[] categories = stack.getItem().getCategories();
        if (categories != null && Arrays.stream(categories).anyMatch(potionCategories::contains)) {
            return BlockUse.Held.POTION;
        }
        return stack.getItem().isConsumable() ? BlockUse.Held.FOOD : BlockUse.Held.OTHER;
    }
}
