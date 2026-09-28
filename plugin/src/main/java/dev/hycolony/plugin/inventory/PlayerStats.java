package dev.hycolony.plugin.inventory;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.ItemUtils;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageSystems;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * The stats Hytale's character panel shows, as its texts: health, stamina and mana as "current/max", and defense as
 * the armor's physical damage reduction in percent (DamageSystems.ArmorDamageReduction, the reduction damage gets).
 */
record PlayerStats(String health, String stamina, String mana, String defense) {
    /** player's stats now; "0/0" for a stat they lack. */
    static PlayerStats of(Store<EntityStore> store, Ref<EntityStore> player) {
        EntityStatMap stats = store.getComponent(player, EntityStatMap.getComponentType());
        return new PlayerStats(
                pair(stats, DefaultEntityStatTypes.getHealth()),
                pair(stats, DefaultEntityStatTypes.getStamina()),
                pair(stats, DefaultEntityStatTypes.getMana()),
                defense(store, player) + "%");
    }

    private static String pair(EntityStatMap stats, int index) {
        EntityStatValue value = stats == null ? null : stats.get(index);
        return value == null ? "0/0" : Math.round(value.get()) + "/" + Math.round(value.getMax());
    }

    /** The armor's multiplier against physical damage, in percent, as ArmorDamageReduction applies it. */
    private static int defense(Store<EntityStore> store, Ref<EntityStore> player) {
        var modifiers = DamageSystems.ArmorDamageReduction.getResistanceModifiers(
                        store.getExternalData().getWorld(),
                        PlayerSection.ARMOR.container(store, player),
                        ItemUtils.canApplyItemStackPenalties(player, store),
                        store.getComponent(player, EffectControllerComponent.getComponentType()))
                .get(DamageCause.PHYSICAL);
        return modifiers == null ? 0 : Math.round(modifiers.multiplierModifier * 100);
    }
}
