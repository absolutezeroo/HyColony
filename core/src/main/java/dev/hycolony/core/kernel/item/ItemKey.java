package dev.hycolony.core.kernel.item;

import java.util.Objects;

/** Opaque item identifier, supplied by the adapters. */
public record ItemKey(String id) {
    public ItemKey {
        Objects.requireNonNull(id, "id");
    }
}
