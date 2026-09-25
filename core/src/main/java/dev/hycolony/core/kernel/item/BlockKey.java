package dev.hycolony.core.kernel.item;

import java.util.Objects;

/** Opaque block identifier, supplied by the adapters. */
public record BlockKey(String id) {
    public BlockKey {
        Objects.requireNonNull(id, "id");
    }
}
