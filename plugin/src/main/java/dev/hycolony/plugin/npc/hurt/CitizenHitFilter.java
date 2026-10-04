package dev.hycolony.plugin.npc.hurt;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageSystems;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hycolony.core.citizen.hurt.CitizenHurt;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.plugin.WorldRuntime;
import dev.hycolony.plugin.WorldRuntimes;
import dev.hycolony.plugin.npc.CitizenTag;
import dev.hycolony.plugin.npc.HyColonyComponents;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import javax.annotation.Nonnull;
import org.jspecify.annotations.Nullable;

/**
 * Who may hurt a citizen and how much a hit takes ({@link CitizenHurt#allowed}, MC EntityCitizen.hurt): in the filter
 * group, after the attacker's own effects scale the damage and before armour and a raised shield reduce it, as MC caps
 * the hit before its armour. A hit the core refuses is cancelled.
 */
public final class CitizenHitFilter extends DamageEventSystem {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();
    private static final Set<Dependency<EntityStore>> DEPENDENCIES = Set.of(
            new SystemDependency<>(Order.AFTER, DamageSystems.ScaleOutgoingDamageFromEntityEffects.class),
            new SystemDependency<>(Order.BEFORE, DamageSystems.ArmorDamageReduction.class),
            new SystemDependency<>(Order.BEFORE, DamageSystems.WieldingDamageReduction.class));

    private final WorldRuntimes runtimes;

    public CitizenHitFilter(WorldRuntimes runtimes) {
        this.runtimes = runtimes;
    }

    @Override
    public Query<EntityStore> getQuery() {
        return HyColonyComponents.citizenTag();
    }

    @Nullable
    @Override
    public SystemGroup<EntityStore> getGroup() {
        return DamageModule.get().getFilterDamageGroup();
    }

    @Override
    public Set<Dependency<EntityStore>> getDependencies() {
        return DEPENDENCIES;
    }

    @Override
    public void handle(
            int index,
            @Nonnull ArchetypeChunk<EntityStore> chunk,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> buffer,
            @Nonnull Damage event) {
        try {
            if (event.getAmount() <= 0) {
                return;
            }
            Ref<EntityStore> ref = chunk.getReferenceTo(index);
            CitizenTag tag = store.getComponent(ref, HyColonyComponents.citizenTag());
            WorldRuntime rt = runtimes.of(store.getExternalData().getWorld());
            if (tag == null || rt == null || !rt.enabled()) {
                return;
            }
            Optional<Colony> colony = rt.manager().byId(tag.colonyId());
            if (colony.isEmpty()) {
                return;
            }
            double allowed = CitizenHurt.allowed(
                    colony.get(), rt.bodies().refs().track(ref), event.getAmount(), HurtSources.of(event, buffer));
            if (allowed <= 0) {
                event.setCancelled(true);
            } else {
                event.setAmount((float) allowed);
            }
        } catch (RuntimeException e) {
            event.setCancelled(true); // CLAUDE.md § 4: a failed check cancels
            LOG.at(Level.SEVERE).withCause(e).log("HyColony citizen hit filter failed");
        }
    }
}
