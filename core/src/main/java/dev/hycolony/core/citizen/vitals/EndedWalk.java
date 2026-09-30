package dev.hycolony.core.citizen.vitals;

import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.Vec3;
import dev.hycolony.core.kernel.nav.WalkEnd;
import dev.hycolony.core.kernel.port.NavStatus;

/**
 * How a citizen's last walk ended: what it went to, how it ended, where the body stood, how far from its goal, the
 * nav's status then ({@code MOVING} when it ended close without the nav) and the tick. Made once per walk end.
 */
public record EndedWalk(BlockPos target, WalkEnd how, Vec3 at, double distance, NavStatus nav, long tick) {}
