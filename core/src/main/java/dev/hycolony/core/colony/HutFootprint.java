package dev.hycolony.core.colony;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.kernel.BlockPos;

/**
 * A hut's footprint: the corners of its plan at its level (at least 1), around the hut block. Port of MC
 * AbstractSchematicProvider.getCorners and isInBuilding; without a plan, MC's default corners are the hut block.
 */
public final class HutFootprint {
    private HutFootprint() {}

    /** The plan's corners, inclusive, in world positions. */
    public record Box(BlockPos min, BlockPos max) {
        public boolean contains(BlockPos p) {
            return p.x() >= min.x()
                    && p.x() <= max.x()
                    && p.y() >= min.y()
                    && p.y() <= max.y()
                    && p.z() >= min.z()
                    && p.z() <= max.z();
        }
    }

    /** {@code b}'s corners from the plan of its level (at least 1) and rotation; the hut block alone without one. */
    public static Box of(GamePorts ports, Building b) {
        BlockPos at = b.position();
        if (b.style().isEmpty()) {
            return new Box(at, at);
        }
        return ports.blueprints()
                .load(b.style(), b.type().id(), Math.max(1, b.level()), b.rotation())
                .map(bp -> new Box(
                        at.offset(bp.min().x(), bp.min().y(), bp.min().z()),
                        at.offset(bp.max().x(), bp.max().y(), bp.max().z())))
                .orElse(new Box(at, at));
    }

    /** MC isInBuilding: {@code pos} within the corners widened by one block on every axis. */
    public static boolean isInBuilding(GamePorts ports, Building b, BlockPos pos) {
        Box box = of(ports, b);
        return new Box(box.min().offset(-1, -1, -1), box.max().offset(1, 1, 1)).contains(pos);
    }
}
