package dev.hycolony.core.kernel.port.body;

import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.port.BodyId;

/**
 * Citizen bodies sitting on seat blocks (MC SittingEntity: chairs, stools, benches of a dining hall). Never throws; an
 * unknown body or an unloaded seat answers false and a call on it does nothing.
 */
public interface BodySeats {
    /**
     * Sits the body on the seat block at {@code seat} (MC SittingEntity.sitDown); true once seated, also when it
     * already sits there. False when it is no loaded seat, the seat is taken or the body is unknown.
     */
    boolean sitOn(BodyId body, BlockPos seat);

    /** Whether every place of the seat at {@code seat} holds someone (MC isSittingPosOccupied); true if no seat. */
    boolean isSeatTaken(BlockPos seat);

    /** Gets the body off its seat; no effect on a body that does not sit or is unknown. */
    void standUp(BodyId body);
}
