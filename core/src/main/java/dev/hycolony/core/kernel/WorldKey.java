package dev.hycolony.core.kernel;

import java.util.Objects;

/** Identifies a game world by its name. */
public record WorldKey(String name) {
    public WorldKey {
        Objects.requireNonNull(name, "name");
    }
}
