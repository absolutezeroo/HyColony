package dev.hyangler.plugin.cast;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.ItemUtils;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hyangler.api.Angler;
import dev.hyangler.api.Catch;
import dev.hyangler.api.FishingContext;
import dev.hyangler.api.Pos;
import dev.hyangler.api.Rarity;
import dev.hyangler.api.event.CatchLanded;
import dev.hyangler.core.FishingService;
import java.util.List;
import java.util.Optional;
import java.util.random.RandomGenerator;
import org.joml.Vector3d;

/**
 * Gives a reeled-in catch and wears the rod (spec § 5, § 6.3; fishing-hytale.md § 5.5): the catch rolled at the
 * bobber, given as Hytale gives a picked-up item (it flies from the bobber to the angler) and published; the rod in
 * hand losing durability unless the game mode spares it.
 */
final class CatchGiver {
    /** The rarities with a state, from the rarest down: a rolled one the item lacks falls to the next it has. */
    private static final List<Rarity> STATES_DOWN =
            List.of(Rarity.LEGENDARY, Rarity.EPIC, Rarity.RARE, Rarity.UNCOMMON);

    private final FishingService service;

    CatchGiver(FishingService service) {
        this.service = service;
    }

    /**
     * Rolls a catch at the bobber with the open water the cast kept (vanilla), gives it to the angler and publishes
     * CatchLanded; empty when nothing bites there or a hook cancels it, or the bobber has no position.
     */
    Optional<Catch> land(ActiveCast cast, Ref<EntityStore> bobber, CommandBuffer<EntityStore> buffer) {
        TransformComponent transform = buffer.getComponent(bobber, TransformComponent.getComponentType());
        if (transform == null) {
            return Optional.empty();
        }
        Vector3d at = new Vector3d(transform.getPosition());
        int x = (int) Math.floor(at.x);
        int y = (int) Math.floor(at.y);
        int z = (int) Math.floor(at.z);
        FishingContext context = cast.world.at(x, y, z, cast.rod.tackle(), cast.session.openWater());
        Angler angler = new Angler.Player(cast.player);
        Optional<Catch> landed = service.land(angler, context, RandomGenerator.of("L64X128MixRandom"));
        landed.ifPresent(c -> {
            ItemUtils.interactivelyPickupItem(cast.angler, new ItemStack(stateOf(c), c.count()), at, buffer);
            service.publish(new CatchLanded(angler, c, new Pos(x, y, z)));
        });
        return landed;
    }

    /** Takes wear from the rod in hand, unless the angler's game mode spares it (creative). */
    void wear(Ref<EntityStore> angler, InteractionContext ctx, int wear, CommandBuffer<EntityStore> buffer) {
        ItemStack rod = ctx.getHeldItem();
        ItemContainer hand = ctx.getHeldItemContainer();
        if (rod != null && hand != null && ItemUtils.canDecreaseItemStackDurability(angler, buffer)) {
            ItemUtils.updateItemStackDurability(angler, rod, hand, ctx.getHeldItemSlot(), -wear, buffer);
        }
    }

    /**
     * The item to give: the catch's rarity state, or the nearest lower one the item has (not every fish has all four),
     * else the base item (spec § 6.3, Item.getItemIdForState).
     */
    private static String stateOf(Catch c) {
        Item item = Item.getAssetMap().getAsset(c.itemId());
        Rarity rarity = c.rarity().orElse(Rarity.COMMON);
        for (Rarity r : STATES_DOWN) {
            String state = item == null || r.compareTo(rarity) > 0 ? null : item.getItemIdForState(r.hytaleName());
            if (state != null) {
                return state;
            }
        }
        return c.itemId();
    }
}
