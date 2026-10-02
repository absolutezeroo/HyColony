package dev.hycolony.plugin.npc;

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
import dev.hycolony.core.citizen.happiness.HappinessEvents;
import dev.hycolony.core.citizen.inventory.ArmorWear;
import dev.hycolony.plugin.WorldRuntime;
import dev.hycolony.plugin.WorldRuntimes;
import java.util.Set;
import java.util.logging.Level;
import javax.annotation.Nonnull;
import org.jspecify.annotations.Nullable;

/**
 * A citizen took damage (MC EntityCitizen.hurt): hurt by an attacker (an entity or its projectile), its body remembers
 * it for 100 ticks (no healing meanwhile, MC getLastHurtByMob); any damage makes its citizen unhappy for a day and
 * wears its armour (MC CitizenItemUtils.damageArmor, {@link ArmorWear}). In
 * the inspect group, so only damage that was really applied counts
 * (sp4b-hytale-food § 5.c). The id-map's ignored causes (fire, lightning) do not count, as MC returns before. The
 * citizen role is Invulnerable today: nothing reaches here until citizens can be hurt (citizen-death.md).
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
            DamageCause cause = event.getCause();
            if (event.getAmount() <= 0 || (cause != null && ignoredCauses.contains(cause.getId()))) {
                return;
            }
            Ref<EntityStore> ref = chunk.getReferenceTo(index);
            CitizenTag tag = store.getComponent(ref, HyColonyComponents.citizenTag());
            WorldRuntime rt = runtimes.of(store.getExternalData().getWorld());
            if (tag == null || rt == null || !rt.enabled()) {
                return;
            }
            if (event.getSource() instanceof Damage.EntitySource) {
                rt.bodies().health().hurt(ref); // MC getLastHurtByMob: only an attacker stops the healing
            }
            double percent = rt.bodies().health().damagePercent(ref, event.getAmount());
            rt.manager().byId(tag.colonyId()).ifPresent(c -> {
                HappinessEvents.hurt(c, rt.bodies().track(ref));
                c.citizens().get(tag.citizenId()).ifPresent(d -> ArmorWear.onHurt(c, d, percent));
            });
        } catch (RuntimeException e) {
            LOG.at(Level.SEVERE).withCause(e).log("HyColony citizen hurt failed");
        }
    }
}
