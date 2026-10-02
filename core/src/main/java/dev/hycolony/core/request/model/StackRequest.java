package dev.hycolony.core.request.model;

import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.port.ItemCatalog;
import java.util.Objects;
import java.util.Optional;

public record StackRequest(ItemKey item, int count, int minCount, boolean canBeResolvedByBuilding)
        implements Deliverable {
    public StackRequest {
        Objects.requireNonNull(item, "item");
    }

    @Override
    public boolean matches(ItemKey other, ItemCatalog catalog) {
        return item.equals(other);
    }

    /** Its item. */
    @Override
    public Optional<ItemKey> displayed(ItemCatalog catalog) {
        return Optional.of(item);
    }

    @Override
    public StackRequest withCount(int newCount) {
        return new StackRequest(item, newCount, minCount, canBeResolvedByBuilding);
    }

    @Override
    public String describe() {
        return count + " x " + item.id();
    }
}
