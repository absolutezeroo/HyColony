package dev.hycolony.core.kernel.item;

import java.util.Objects;

public record BlockState(BlockKey key, int rotation) {
    public BlockState {
        Objects.requireNonNull(key, "key");
    }
}
