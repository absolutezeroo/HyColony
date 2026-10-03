package dev.hycolony.plugin.npc.body;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.ItemUtils;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageSystems;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * A body's defense, as Hytale's armour reduction counts it (DamageSystems.ArmorDamageReduction.getResistanceModifiers):
 * the share of physical damage its worn armour and active effects take off, broken pieces counted as Hytale does, from
 * the percent resistances only: flat ones (BaseDamageResistance, only QA and debug pieces have it) are left out. World
 * thread.
 */
public final class BodyDefense {
    private BodyDefense() {}

    /** The physical damage reduction of the body at {@code ref}, in percent rounded down, 0 to 100; 0 unarmoured. */
    public static int percent(World world, Store<EntityStore> store, Ref<EntityStore> ref) {
        InventoryComponent.Armor armor = store.getComponent(ref, InventoryComponent.Armor.getComponentType());
        DamageCause physical = DamageCause.getAssetMap().getAsset("Physical");
        if (armor == null || physical == null) {
            return 0;
        }
        DamageSystems.ArmorDamageReduction.ArmorResistanceModifiers mods =
                DamageSystems.ArmorDamageReduction.getResistanceModifiers(
                                world,
                                armor.getInventory(),
                                ItemUtils.canApplyItemStackPenalties(ref, store),
                                store.getComponent(ref, EffectControllerComponent.getComponentType()))
                        .get(physical);
        return mods == null ? 0 : (int) Math.clamp(mods.multiplierModifier * 100, 0, 100);
    }
}
