package dev.hycolony.plugin.npc.body;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.modules.entity.component.HealthRegenState;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageSystems;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hycolony.plugin.npc.hurt.CauseIndex;
import org.jspecify.annotations.Nullable;

/**
 * A citizen body's health (its Health stat, sp4b-hytale-food § 5.b); the core remembers its attackers. Our role has
 * MaxHealth 100, Hytale's scale (CitizenData.MAX_HEALTH). Hytale regenerates an NPC's health by itself (Health.json,
 * +5 % every 0.5 s after 15 s unhurt); that is switched off at each read (the healing reads every body every 100
 * ticks), as MC citizens heal from their saturation only [in-game]. World thread only.
 */
public final class BodyVitals {
    private final World world;
    private final CauseIndex crush = new CauseIndex("Crush");

    public BodyVitals(World world) {
        this.world = world;
    }

    private Store<EntityStore> store() {
        return world.getEntityStore().getStore();
    }

    /** The Health stat in percent of its range; 0 without one. */
    public int percent(Ref<EntityStore> ref) {
        EntityStatValue health = health(ref);
        return health == null ? 0 : (int) (health.asPercentage() * 100);
    }

    /** The current health; 0 without a Health stat. */
    public double current(Ref<EntityStore> ref) {
        EntityStatValue health = health(ref);
        return health == null ? 0 : health.get();
    }

    /** The maximum health; 0 without a Health stat. */
    public double max(Ref<EntityStore> ref) {
        EntityStatValue health = health(ref);
        return health == null ? 0 : health.getMax();
    }

    /** Adds {@code amount} to the Health stat, which caps it at its maximum. */
    public void heal(Ref<EntityStore> ref, double amount) {
        EntityStatMap stats = store().getComponent(ref, EntityStatMap.getComponentType());
        if (stats != null) {
            stats.addStatValue(DefaultEntityStatTypes.getHealth(), (float) amount);
        }
    }

    /**
     * Deals {@code amount} of {@code Crush} damage, without a source, through Hytale's damage systems (armour, our
     * filters, death). Deviation from MC (Hytale world): MC's STUCK_DAMAGE → Crush, the closest cause, which armour
     * reduces as MC's (citizen-death.md § 3). Deferred to world.execute, as a caller may run while the store processes.
     */
    public void damage(Ref<EntityStore> ref, double amount) {
        int cause = crush.get();
        if (cause == CauseIndex.MISSING) {
            return;
        }
        world.execute(() -> {
            if (ref.isValid()) {
                DamageSystems.executeDamage(ref, store(), new Damage(Damage.NULL_SOURCE, cause, (float) amount));
            }
        });
    }

    private @Nullable EntityStatValue health(Ref<EntityStore> ref) {
        Store<EntityStore> st = store();
        stopNaturalRegen(st, ref);
        EntityStatMap stats = st.getComponent(ref, EntityStatMap.getComponentType());
        return stats == null ? null : stats.get(DefaultEntityStatTypes.getHealth());
    }

    /**
     * HealthRegenState is not saved with the entity, and every NPC gets one (Role.createAndAttach): switched off at
     * each read, a read never adds a component (it may run while a store processes).
     */
    private static void stopNaturalRegen(Store<EntityStore> st, Ref<EntityStore> ref) {
        HealthRegenState regen = st.getComponent(ref, HealthRegenState.getComponentType());
        if (regen != null) {
            regen.setRegenEnabled(false);
        }
    }
}
