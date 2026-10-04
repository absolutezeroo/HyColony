package dev.hycolony.plugin.npc.body;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.modules.entity.component.HealthRegenState;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.LongSupplier;
import org.jspecify.annotations.Nullable;

/**
 * A citizen body's health (its Health stat, sp4b-hytale-food § 5.b) and when it was last hurt. Our role has MaxHealth
 * 100, Hytale's scale (CitizenData.MAX_HEALTH). Hytale regenerates an NPC's health by itself (Health.json, +5 %
 * every 0.5 s after 15 s unhurt); that is switched off at each read (the healing reads every body every 100 ticks), as
 * MC citizens heal from their saturation only [in-game]. World thread only.
 */
public final class BodyVitals {
    /** MC LivingEntity.getLastHurtByMob: an attacker is remembered for 100 ticks (the core's, 20 per second). */
    private static final long HURT_MEMORY_TICKS = 100;

    private final World world;
    private final LongSupplier coreTicks;
    /** Core tick of each body's last hurt by an attacker; weak, so a removed entity drops out. */
    private final Map<Ref<EntityStore>, Long> lastHurt = new WeakHashMap<>();

    /** {@code coreTicks}: the core's clock, at 20 ticks per second where Hytale's world runs at 30. */
    public BodyVitals(World world, LongSupplier coreTicks) {
        this.world = world;
        this.coreTicks = coreTicks;
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

    /** Notes that an attacker hurt the body now (from the damage system). */
    public void hurt(Ref<EntityStore> ref) {
        lastHurt.put(ref, coreTicks.getAsLong());
    }

    /** Whether the body took damage less than {@link #HURT_MEMORY_TICKS} ago. */
    public boolean recentlyHurt(Ref<EntityStore> ref) {
        Long tick = lastHurt.get(ref);
        return tick != null && coreTicks.getAsLong() - tick < HURT_MEMORY_TICKS;
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
