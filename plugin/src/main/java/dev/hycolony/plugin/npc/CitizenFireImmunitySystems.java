package dev.hycolony.plugin.npc;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.entity.effect.ActiveEntityEffect;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.logging.Level;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Fire never hurts a HyColony citizen, and never shows on them either.
 *
 * <p>Deviation from MC: citizens are immune to fire (user request), independent of invulnerability. MC lets a
 * citizen burn like any other living entity; here neither {@code Grant} nor {@code Guard} depend on the
 * {@code Invulnerable} component the role currently carries, so the immunity survives a future change that makes
 * citizens mortal.
 */
public final class CitizenFireImmunitySystems {
    private CitizenFireImmunitySystems() {}

    /**
     * Grants every HyColony citizen the Hytale entity effect {@code Immunity_Fire} once, permanently.
     *
     * <p>{@code Burn} and {@code Lava_Burn} (Deco_Fire, the lit Furniture_Crude_Brazier, lava) both inherit
     * {@code Burn_Template}, whose {@code ApplyConditions} refuse to (re)apply the effect while the target already
     * has {@code Immunity_Fire}: neither the tick damage nor the burning visual (screen tint, flame particles) ever
     * starts.
     */
    public static final class Grant extends RefSystem<EntityStore> {
        private static final String IMMUNITY_FIRE_EFFECT_ID = "Immunity_Fire";
        private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();

        private EntityEffect immunityFire;
        private boolean missingEffectWarned;

        @Override
        public Query<EntityStore> getQuery() {
            return HyColonyComponents.citizenTag();
        }

        @Override
        public void onEntityAdded(
                @Nonnull Ref<EntityStore> ref,
                @Nonnull AddReason reason,
                @Nonnull Store<EntityStore> store,
                @Nonnull CommandBuffer<EntityStore> buffer) {
            try {
                EntityEffect effect = resolveImmunityFire();
                if (effect == null) {
                    return;
                }
                EffectControllerComponent effects =
                        buffer.getComponent(ref, EffectControllerComponent.getComponentType());
                if (effects != null) {
                    effects.addEffect(ref, effect, buffer);
                }
            } catch (RuntimeException e) {
                LOG.at(Level.SEVERE).withCause(e).log("HyColony citizen fire immunity grant failed");
            }
        }

        @Override
        public void onEntityRemove(
                @Nonnull Ref<EntityStore> ref,
                @Nonnull RemoveReason reason,
                @Nonnull Store<EntityStore> store,
                @Nonnull CommandBuffer<EntityStore> buffer) {
            // The effect is saved with the entity itself; nothing to release here.
        }

        private EntityEffect resolveImmunityFire() {
            if (immunityFire == null) {
                immunityFire = EntityEffect.getAssetMap().getAsset(IMMUNITY_FIRE_EFFECT_ID);
                if (immunityFire == null) {
                    LOG.at(missingEffectWarned ? Level.FINE : Level.WARNING).log(
                            "HyColony: entity effect '%s' not found; citizens can still burn", IMMUNITY_FIRE_EFFECT_ID);
                    missingEffectWarned = true;
                }
            }
            return immunityFire;
        }
    }

    /**
     * Cancels the one fire-adjacent hazard that {@code Immunity_Fire} does not cover: the extinguished campfire's
     * embers (Deco_Campfire_Off) deal contact damage through a nameless {@code Physical}-cause entity effect
     * ({@code Server/Item/Interactions/Block/Block_Damage.json}), not through {@code Burn}, so it carries no
     * {@code ApplyConditions} to gate on.
     */
    public static final class Guard extends DamageEventSystem {
        private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();
        private static final String PHYSICAL_CAUSE_ID = "Physical";

        /**
         * {@code EntityEffect.Locale} of Block_Damage's anonymous effect: the only Physical-cause, effect-driven
         * damage that shares it is Environmental_Block_Damage (cactus, brambles), whose cause is Environmental, not
         * Physical, so the pair (cause, locale) is unique to campfire embers. Survival_Trap_Spike_* and
         * Survival_Trap_Snapjaw are also anonymous Physical-cause effects, but their locale is "spikes"/"snapjaw":
         * traps must keep hurting citizens.
         */
        private static final String BLOCK_DAMAGE_LOCALE = "block";

        private int physicalCauseIndex = Integer.MIN_VALUE;
        private boolean causeResolved;

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
        public void handle(
                int index,
                @Nonnull ArchetypeChunk<EntityStore> chunk,
                @Nonnull Store<EntityStore> store,
                @Nonnull CommandBuffer<EntityStore> buffer,
                @Nonnull Damage event) {
            try {
                if (isEmberContactDamage(event)) {
                    event.setCancelled(true);
                }
            } catch (RuntimeException e) {
                LOG.at(Level.SEVERE).withCause(e).log("HyColony citizen fire contact guard failed");
            }
        }

        /**
         * Combat damage always carries an {@code EntitySource}/{@code ProjectileSource}; a DoT tick from an entity
         * effect (Burn, Lava_Burn, embers, spike traps...) carries the {@link ActiveEntityEffect} itself as source.
         * Burn and Lava_Burn use the {@code Fire} cause and never reach here (see {@link Grant}). Among the
         * remaining Physical-cause effect ticks, only Block_Damage's locale identifies the embers (see
         * {@link #BLOCK_DAMAGE_LOCALE}); spike traps and the snapjaw must still hurt citizens.
         */
        private boolean isEmberContactDamage(Damage event) {
            if (event.getDamageCauseIndex() != resolvePhysicalCauseIndex()
                    || !(event.getSource() instanceof ActiveEntityEffect activeEffect)) {
                return false;
            }
            EntityEffect effect = EntityEffect.getAssetMap().getAsset(activeEffect.getEntityEffectIndex());
            return effect != null && BLOCK_DAMAGE_LOCALE.equals(effect.getLocale());
        }

        /** Resolved once (this cause never disappears mid-run): a repeated failure would just repeat the same miss. */
        private int resolvePhysicalCauseIndex() {
            if (!causeResolved) {
                physicalCauseIndex = DamageCause.getAssetMap().getIndex(PHYSICAL_CAUSE_ID);
                causeResolved = true;
                if (physicalCauseIndex == Integer.MIN_VALUE) {
                    LOG.at(Level.WARNING).log(
                            "HyColony: damage cause '%s' not found; campfire embers can still hurt citizens",
                            PHYSICAL_CAUSE_ID);
                }
            }
            return physicalCauseIndex;
        }
    }
}
