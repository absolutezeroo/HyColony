package dev.hycolony.plugin.npc.hurt;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.system.StoreSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.type.attitude.Attitude;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.blackboard.Blackboard;
import com.hypixel.hytale.server.npc.blackboard.view.attitude.AttitudeView;
import com.hypixel.hytale.server.npc.systems.BlackboardSystems;
import dev.hycolony.plugin.WorldRuntime;
import dev.hycolony.plugin.WorldRuntimes;
import dev.hycolony.plugin.npc.HyColonyComponents;
import dev.hycolony.plugin.npc.spawn.HostileGroup;
import java.util.Set;
import java.util.logging.Level;
import javax.annotation.Nonnull;
import org.jspecify.annotations.Nullable;

/**
 * MC's monsters attack citizens (MC mobattackcitizens, default on): a {@link HostileGroup} NPC is hostile to a citizen
 * body while the config's {@code MobAttackCitizens} holds. Hytale reads attitudes through each world's AttitudeView;
 * our provider asks after the per-NPC overrides and before the vanilla attitude groups (citizen-death.md § 2.6).
 */
public final class CitizenAttitudeSystem extends StoreSystem<EntityStore> {
    /** After the NPC overrides (0) and the lineage (100), before the attitude groups (200). */
    private static final int PRIORITY = 150;

    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();
    private static final Set<Dependency<EntityStore>> DEPENDENCIES =
            Set.of(new SystemDependency<>(Order.AFTER, BlackboardSystems.InitSystem.class));

    private final WorldRuntimes runtimes;
    private final HostileGroup hostile;
    private boolean failed;

    public CitizenAttitudeSystem(WorldRuntimes runtimes, HostileGroup hostile) {
        this.runtimes = runtimes;
        this.hostile = hostile;
    }

    @Override
    public Set<Dependency<EntityStore>> getDependencies() {
        return DEPENDENCIES;
    }

    @Override
    public void onSystemAddedToStore(@Nonnull Store<EntityStore> store) {
        World world = store.getExternalData().getWorld();
        store.getResource(Blackboard.getResourceType())
                .forEachView(
                        AttitudeView.class,
                        view -> view.registerProvider(
                                PRIORITY, (source, role, target, accessor) -> attitude(world, role, target, accessor)));
    }

    @Override
    public void onSystemRemovedFromStore(@Nonnull Store<EntityStore> store) {
        // The view goes with the store.
    }

    /** HOSTILE for a hostile NPC (role {@code role}) facing a citizen body; null (no say) otherwise. */
    private @Nullable Attitude attitude(
            World world, int role, Ref<EntityStore> target, ComponentAccessor<EntityStore> accessor) {
        try {
            if (accessor.getComponent(target, HyColonyComponents.citizenTag()) == null) {
                return null;
            }
            WorldRuntime rt = runtimes.of(world);
            boolean attack = rt != null
                    && rt.enabled()
                    && rt.manager().context().config().combat().mobAttackCitizens()
                    && hostile.contains(role);
            return attack ? Attitude.HOSTILE : null;
        } catch (RuntimeException e) {
            LOG.at(failed ? Level.FINE : Level.SEVERE).withCause(e).log("HyColony citizen attitude failed");
            failed = true;
            return null;
        }
    }
}
