package dev.hycolony.plugin.block;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.WorldEventSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.event.events.ecs.DamageBlockEvent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hycolony.plugin.WorldRuntime;
import dev.hycolony.plugin.WorldRuntimes;
import java.util.logging.Level;
import javax.annotation.Nonnull;

/**
 * Explosions leave colony blocks intact (config Permissions.TurnOffExplosionsInColonies; the core decides).
 *
 * <p>The world-level DamageBlockEvent is only fired for explosions: BlockHarvestUtils invokes it without an entity
 * only when the damage has no instigator, and ExplosionUtils.processTargetBlocks is the only such caller (0.6.8).
 * Cancelling it keeps the block and stops the blast there, like a block the explosion cannot break.
 *
 * <p>Deviation from MC: only the block part of the setting is honoured. DAMAGE_PLAYERS and DAMAGE_NOTHING also
 * shield entities in MC, but Hytale's explosion damage to entities cannot be told apart from projectile damage.
 */
public final class ExplosionProtectionSystem extends WorldEventSystem<EntityStore, DamageBlockEvent> {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();

    private final WorldRuntimes runtimes;

    public ExplosionProtectionSystem(WorldRuntimes runtimes) {
        super(DamageBlockEvent.class);
        this.runtimes = runtimes;
    }

    @Override
    public void handle(
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> buffer,
            @Nonnull DamageBlockEvent event) {
        try {
            WorldRuntime rt = runtimes.of(store.getExternalData().getWorld());
            if (rt != null
                    && rt.enabled()
                    && rt.manager().protection().explosionSparesBlock(HutBlockSystems.pos(event.getTargetBlock()))) {
                event.setCancelled(true);
            }
        } catch (RuntimeException e) {
            event.setCancelled(true);
            LOG.at(Level.SEVERE).withCause(e).log("HyColony explosion check failed at %s", event.getTargetBlock());
        }
    }
}
