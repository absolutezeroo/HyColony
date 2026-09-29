package dev.hycolony.core.job.work;

import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.Vec3;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.item.ToolType;
import dev.hycolony.core.kernel.port.BodyAnimation;
import dev.hycolony.core.kernel.port.BodyId;
import dev.hycolony.core.kernel.port.CitizenBodies;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * What a worker visibly does with its body, for every job (MC CitizenItemUtils.setHeldItem, WorkerUtil.faceBlock and
 * swing): the item in its hand, the block it faces and the gesture it plays.
 */
public final class WorkerHands {
    private final CitizenBodies bodies;
    private final BodyId body;

    public WorkerHands(CitizenBodies bodies, BodyId body) {
        this.bodies = bodies;
        this.body = body;
    }

    /** MC setHeldItem: {@code item} in the main hand, an empty hand when absent. */
    public void hold(Optional<ItemKey> item) {
        bodies.setHeldItem(body, item);
    }

    /** MC equipTool: the tool of {@code type} that {@code stock} would use in hand, an empty hand without one. */
    public void holdTool(WorkerStock stock, ToolType type) {
        OptionalInt slot = stock.toolInInventory(type);
        hold(
                slot.isEmpty()
                        ? Optional.empty()
                        : stock.inventory().slot(slot.getAsInt()).map(ItemAmount::item));
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
