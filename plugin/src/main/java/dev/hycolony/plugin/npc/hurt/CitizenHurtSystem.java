package dev.hycolony.plugin.npc.hurt;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.citizen.happiness.HappinessEvents;
import dev.hycolony.core.citizen.inventory.ArmorWear;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.kernel.port.BodyId;
import dev.hycolony.plugin.WorldRuntime;
import dev.hycolony.plugin.WorldRuntimes;
import dev.hycolony.plugin.npc.CitizenTag;
import dev.hycolony.plugin.npc.HyColonyComponents;
import java.util.Set;
import java.util.logging.Level;
import javax.annotation.Nonnull;
import org.jspecify.annotations.Nullable;

/**
 * A citizen took damage (MC EntityCitizen.handleDamagePerformed): hurt by an attacker (an entity or its projectile),
 * its citizen remembers it (its {@code hurtMemory}, no healing meanwhile); any damage but the id-map's ignored
 * causes (fire, lightning, as MC returns before) makes it unhappy for a day, and a cause that wears armour wears one
 * piece, as Hytale wears a player's ({@link ArmorWear}). In the inspect group, so only damage that was really applied
 * counts (sp4b-hytale-food § 5.c); {@link CitizenHitFilter} capped it before.
 */
public final class CitizenHurtSystem extends DamageEventSystem {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();

    private final WorldRuntimes runtimes;
    private final Set<String> ignoredCauses;

    public CitizenHurtSystem(WorldRuntimes runtimes, Set<String> ignoredCauses) {
        this.runtimes = runtimes;
        this.ignoredCauses = Set.copyOf(ignoredCauses);
    }

    @Override
    public Query<EntityStore> getQuery() {
        return HyColonyComponents.citizenTag();
    }

    @Nullable
    @Override
    public SystemGroup<EntityStore> getGroup() {
        return DamageModule.get().getInspectDamageGroup();
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
            boolean attacker = HurtSources.of(event, buffer).attacker();
            BodyId body = rt.bodies().refs().track(ref);
            rt.manager()
                    .byId(tag.colonyId())
                    .ifPresent(c -> c.citizens()
                            .get(tag.citizenId())
                            .ifPresent(d -> hurt(c, d, body, attacker, event.getCause())));
        } catch (RuntimeException e) {
            LOG.at(Level.SEVERE).withCause(e).log("HyColony citizen hurt failed");
        }
    }

    /** {@code d}, whose body is {@code body}, took a hit of {@code cause}, dealt by an entity when {@code attacker}. */
    private void hurt(Colony c, CitizenData d, BodyId body, boolean attacker, @Nullable DamageCause cause) {
        if (attacker) { // MC setLastHurtByMob, before the fire and lightning return
            d.vitals().hurtMemory().attacked(c.context().clock().currentTick());
        }
        if (cause != null && ignoredCauses.contains(cause.getId())) {
            return;
        }
        HappinessEvents.hurt(c, body);
        // Hytale's DamageArmor wears armour only for a cause that loses durability (DamageCause.isDurabilityLoss).
        if (cause != null && cause.isDurabilityLoss()) {
            ArmorWear.onHurt(c, d);
        }
    }
}
