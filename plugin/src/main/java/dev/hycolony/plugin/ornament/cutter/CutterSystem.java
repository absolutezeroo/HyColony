package dev.hycolony.plugin.ornament.cutter;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.event.events.ecs.UseBlockEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hycolony.plugin.block.BlockUseProtectionSystem;
import java.util.Set;
import java.util.logging.Level;
import javax.annotation.Nonnull;

/**
 * A player using the architect's cutter (MC DO ArchitectsCutterBlock.use): opens its window. The use is not cancelled
 * on success (the block's own Use is a no-op); a failure cancels it and is logged.
 */
public final class CutterSystem extends EntityEventSystem<EntityStore, UseBlockEvent.Pre> {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();
    /** The cutter block's key (tools/domum/blocks/cutter.py IDENT). */
    static final String CUTTER = "HyColony_DO_ArchitectsCutter";

    /** After the colony protection, so that a refused use is already cancelled here. */
    private final Set<Dependency<EntityStore>> dependencies =
            Set.of(new SystemDependency<>(Order.AFTER, BlockUseProtectionSystem.class));

    private final CutterSettings settings;

    /** Opens windows with {@code settings}: the ornaments, the remembered groups, the craft time, the sounds. */
    public CutterSystem(CutterSettings settings) {
        super(UseBlockEvent.Pre.class);
        this.settings = settings;
    }

    @Override
    public Set<Dependency<EntityStore>> getDependencies() {
        return dependencies;
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
        if (event.isCancelled() || !CUTTER.equals(event.getBlockType().getId())) {
            return;
        }
        try {
            CutterOpener.open(store.getExternalData().getWorld(), chunk.getReferenceTo(index), settings);
        } catch (RuntimeException e) {
            event.setCancelled(true);
            LOG.at(Level.SEVERE).withCause(e).log("HyColony cutter use failed at %s", event.getTargetBlock());
        }
    }
}
