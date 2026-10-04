package dev.hylens.core.watch;

import dev.hycolony.api.CitizenRef;
import dev.hycolony.api.ColonyWorld;
import java.util.Optional;

/**
 * When a watch has lost its citizen for good (spec 2026-09-30, § 6.1): HyColony no longer knows it, dead or its colony
 * deleted. A watch of another world's citizen is kept: its operator may come back.
 */
public final class WatchLoss {
    private WatchLoss() {}

    /**
     * Whether {@code citizen} is gone from {@code colonies}, HyColony in the world named {@code world}; false for
     * another world's citizen, or where HyColony does not run.
     */
    public static boolean gone(String world, Optional<ColonyWorld> colonies, CitizenRef citizen) {
        return citizen.colony().world().equals(world)
                && colonies.map(w -> w.citizen(citizen).isEmpty()).orElse(false);
    }
}
