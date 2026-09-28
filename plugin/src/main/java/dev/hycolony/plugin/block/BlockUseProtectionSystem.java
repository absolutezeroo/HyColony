package dev.hycolony.plugin.block;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.event.events.ecs.UseBlockEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.ColonyManager;
import dev.hycolony.core.colony.ColonyRefusal;
import dev.hycolony.core.colony.permission.BlockUse;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.plugin.IdMap;
import dev.hycolony.plugin.WorldRuntime;
import dev.hycolony.plugin.WorldRuntimes;
import dev.hydomum.plugin.api.HyDomumSystems;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import javax.annotation.Nonnull;

/**
 * Using any block inside a colony (MC ColonyPermissionEventHandler.on(PlayerInteractEvent)): describes the use for the
 * core {@link BlockUse} rule and cancels it when the player's rank lacks the action, with MC's denial message. A
 * cancelled use makes UseBlock fail: the held item's fallback (placing a block, breaking it) then meets the place and
 * break protection. Hut blocks are left to HutBlockSystems.
 */
public final class BlockUseProtectionSystem extends EntityEventSystem<EntityStore, UseBlockEvent.Pre> {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();
    /** Before HyDomum's cutter, so that the cutter sees a refused use already cancelled. */
    private static final Set<Dependency<EntityStore>> DEPENDENCIES =
            Set.of(new SystemDependency<>(Order.BEFORE, HyDomumSystems.cutterUse()));

    private final WorldRuntimes runtimes;
    private final Set<String> hutBlockIds;
    private final BlockUses uses;

    public BlockUseProtectionSystem(WorldRuntimes runtimes, IdMap ids) {
        super(UseBlockEvent.Pre.class);
        this.runtimes = runtimes;
        this.hutBlockIds = HutBlockSystems.byBlockId(runtimes.setup()).keySet();
        this.uses = new BlockUses(ids);
    }

    @Override
    public Set<Dependency<EntityStore>> getDependencies() {
        return DEPENDENCIES;
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
        try {
            BlockType type = event.getBlockType();
            if (event.isCancelled() || hutBlockIds.contains(type.getId())) {
                return;
            }
            World world = store.getExternalData().getWorld();
            WorldRuntime rt = runtimes.of(world);
            PlayerRef player = HutBlockSystems.player(index, chunk, store);
            BlockPos pos = HutBlockSystems.pos(event.getTargetBlock());
            if (rt == null || !rt.enabled() || player == null) {
                return;
            }
            ColonyManager manager = rt.manager();
            Optional<Colony> colony = manager.colonyAt(pos);
            if (colony.isEmpty()) {
                return;
            }
            BlockUse use = uses.of(world, event);
            if (use.refused(action -> manager.isAllowed(player.getUuid(), pos, action), manager.protectionEnabled())
                    .isPresent()) {
                event.setCancelled(true);
                ColonyRefusal.tell(colony.get(), player.getUuid());
            }
        } catch (RuntimeException e) {
            // A failing check must not let the use through.
            event.setCancelled(true);
            LOG.at(Level.SEVERE).withCause(e).log("HyColony block use check failed at %s", event.getTargetBlock());
        }
    }
}
