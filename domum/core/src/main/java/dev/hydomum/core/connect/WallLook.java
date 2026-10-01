package dev.hydomum.core.connect;

import java.util.Set;

/** How a wall's top reads the block above it: its tall sides (MC WallSide.TALL) and whether its post is raised. */
public record WallLook(Set<Side> tall, boolean post) {
    public WallLook {
        tall = Set.copyOf(tall);
    }
}
