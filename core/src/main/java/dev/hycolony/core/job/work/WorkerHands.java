package dev.hycolony.core.job.work;

import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.citizen.inventory.HeldItems;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.Vec3;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.item.ToolType;
import dev.hycolony.core.kernel.port.BodyAnimation;
import dev.hycolony.core.kernel.port.BodyId;
import dev.hycolony.core.kernel.port.CitizenBodies;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * What a worker visibly does with its body, for every job (MC CitizenItemUtils.setHeldItem, WorkerUtil.faceBlock and
 * swing): the item in its hand (kept as its inventory slot, {@link HeldItems}), the block it faces and the gesture it
 * plays.
 */
public final class WorkerHands {
    private final CitizenBodies bodies;
    private final BodyId body;
    private final CitizenData citizen;

    public WorkerHands(CitizenBodies bodies, BodyId body, CitizenData citizen) {
        this.bodies = bodies;
        this.body = body;
        this.citizen = citizen;
    }

    /** MC setHeldItem: {@code item} in the main hand (its first inventory slot), an empty hand when absent. */
    public void hold(Optional<ItemKey> item) {
        HeldItems.holdItem(citizen, bodies, body, item);
    }

    /**
     * MC equipTool, setHeldItem(hand, slot): the slot of the tool of {@code type} that {@code stock} would use in
     * hand, an empty hand without one.
     */
    public void holdTool(WorkerStock stock, ToolType type) {
        OptionalInt slot = stock.toolInInventory(type);
        if (slot.isPresent()) {
            holdSlot(slot.getAsInt());
        } else {
            hold(Optional.empty());
        }
    }

    /** MC setHeldItem(hand, slot): the main hand holds inventory slot {@code slot}, whose item the body shows. */
    public void holdSlot(int slot) {
        HeldItems.holdSlot(citizen, bodies, body, slot);
    }

    /** MC WorkerUtil.faceBlock, or hitBlockWithToolInHand's setLookAt: the body turns to {@code pos}. */
    public void face(BlockPos pos) {
        bodies.lookAt(body, Vec3.middle(pos));
    }

    /** MC swing: one stroke of {@code animation}. */
    public void swing(BodyAnimation animation) {
        bodies.playAnimation(body, animation);
    }
}
