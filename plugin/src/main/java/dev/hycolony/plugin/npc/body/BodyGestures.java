package dev.hycolony.plugin.npc.body;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.AnimationSlot;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.entity.AnimationUtils;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.physics.util.PhysicsMath;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.util.InventoryHelper;
import dev.hycolony.core.kernel.Vec3;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.port.BodyAnimation;
import java.util.List;
import java.util.Optional;
import org.joml.Vector3d;

/**
 * What a citizen body shows: the item in its hand, the armour it wears, an animation, where it looks. World thread
 * only.
 */
public final class BodyGestures {
    private BodyGestures() {}

    /** Hotbar slot 0 of the NPC (the role's default hotbar has 3 slots); empty clears it. */
    public static void hold(Ref<EntityStore> ref, Optional<ItemKey> item, Store<EntityStore> store) {
        if (item.isEmpty()) {
            InventoryHelper.clearItemInHand(ref, (byte) 0, store);
        } else if (InventoryHelper.setHotbarItem(ref, item.get().id(), (byte) 0, store)) {
            InventoryHelper.setHotbarSlot(ref, (byte) 0, store);
        }
    }

    /**
     * Puts {@code pieces} in the body's armour (InventoryComponent.Armor, ItemArmorSlot order), as a role's Armor key
     * does (InventoryHelper.useArmor); SyncEquipmentSystem then shows them to the players around. A bare slot, or an
     * item the game lacks, empties it. A copy for display: Hytale wears no NPC armour out
     * (ItemUtils.canDecreaseItemStackDurability: players only).
     */
    public static void wear(Ref<EntityStore> ref, List<Optional<ItemKey>> pieces, Store<EntityStore> store) {
        InventoryComponent.Armor armor = store.getComponent(ref, InventoryComponent.Armor.getComponentType());
        if (armor == null) {
            return;
        }
        ItemContainer worn = armor.getInventory();
        for (short slot = 0; slot < Math.min(pieces.size(), worn.getCapacity()); slot++) {
            Optional<ItemKey> piece =
                    pieces.get(slot).filter(k -> Item.getAssetMap().getAsset(k.id()) != null);
            if (piece.isPresent()) {
                worn.setItemStackForSlot(slot, new ItemStack(piece.get().id(), 1));
            } else {
                worn.removeItemStackFromSlot(slot);
            }
        }
    }

    /**
     * Plays an item animation on the Action slot (the model has no work animations of its own).
     * Fallback if the client shows nothing: {@code "Default", "SwingRight"}.
     */
    public static void animate(Ref<EntityStore> ref, BodyAnimation animation, Store<EntityStore> store) {
        switch (animation) {
            case BUILD -> AnimationUtils.playAnimation(ref, AnimationSlot.Action, "Block", "Build", store);
            case MINE -> AnimationUtils.playAnimation(ref, AnimationSlot.Action, "Pickaxe", "Mine", store);
            // The hoe's own animation set (Server/Item/Animations/Hoe.json), as Hoe_Till plays it for a player
            case TILL -> AnimationUtils.playAnimation(ref, AnimationSlot.Action, "Hoe", "Till", store);
            // The seeds' animation set (Template_Seeds PlayerAnimationsId), as Seed_Place plays it for a player
            case PLANT -> AnimationUtils.playAnimation(ref, AnimationSlot.Action, "Item", "Interact", store);
            // The foods' animation set (Template_Food PlayerAnimationsId "Item"), as eating plays it for a player
            case EAT -> AnimationUtils.playAnimation(ref, AnimationSlot.Action, "Item", "Consume", store);
        }
    }

    /**
     * MC WorkerUtil.faceBlock: body yaw toward the target (TransformComponent rotation) and head yaw and pitch from
     * the eyes (HeadRotation), with PhysicsMath's heading/pitch as NPC motions use. The caller ends the walk first:
     * with no Seek target the role has no body steering, so MotionControllerBase keeps the yaw it reads from the
     * transform each tick, and the head, without head steering, turns toward that body yaw.
     */
    public static void lookAt(Ref<EntityStore> ref, Vec3 target, Store<EntityStore> store) {
        TransformComponent t = store.getComponent(ref, TransformComponent.getComponentType());
        HeadRotation head = store.getComponent(ref, HeadRotation.getComponentType());
        if (t == null) {
            return;
        }
        Vector3d p = t.getPosition();
        double dx = target.x() - p.x, dz = target.z() - p.z;
        double dy = target.y() - (p.y + ModelComponent.getEyeHeight(ref, store));
        float yaw = PhysicsMath.normalizeTurnAngle(PhysicsMath.headingFromDirection(dx, dz));
        t.getRotation().setYaw(yaw);
        if (head != null) {
            head.getRotation().setYaw(yaw);
            head.getRotation().setPitch(PhysicsMath.pitchFromDirection(dx, dy, dz));
        }
    }
}
