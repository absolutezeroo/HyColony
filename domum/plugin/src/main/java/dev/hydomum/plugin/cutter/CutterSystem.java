package dev.hydomum.plugin.cutter;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.event.events.ecs.UseBlockEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.logging.Level;
import javax.annotation.Nonnull;

/**
 * A player using the architect's cutter (MC DO ArchitectsCutterBlock.use): opens its window. The use is not cancelled
 * on success (the block's own Use is a no-op); a failure cancels it and is logged.
 */
public final class CutterSystem extends EntityEventSystem<EntityStore, UseBlockEvent.Pre> {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();
    /** The cutter block's key (tools/domum/blocks/cutter.py IDENT). */
    static final String CUTTER = "HyDomum_ArchitectsCutter";

    private final CutterSettings settings;

    /** Opens windows with {@code settings}: the ornaments, the remembered groups, the craft time, the sounds. */
    public CutterSystem(CutterSettings settings) {
        super(UseBlockEvent.Pre.class);
        this.settings = settings;
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
            LOG.at(Level.SEVERE).withCause(e).log("HyDomum cutter use failed at %s", event.getTargetBlock());
        }
    }
}
