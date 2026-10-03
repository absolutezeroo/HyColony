package dev.hycolony.core.request.model;

import dev.hycolony.core.kernel.catalog.ItemCatalog;
import dev.hycolony.core.kernel.item.ItemKey;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * MC StackList: {@code count} items, any of {@code accepted}; asked for a recipe ingredient given by Hytale resource
 * type or tag. {@code description} names what is asked (MC description). MC's meta, NBT and ore dictionary matching,
 * result and left-over are not ported: a Hytale item is its id, and the type or tag is already listed out. Deviation
 * from MC: it accepts at least one item, as nobody could serve an empty one; MC does not check.
 */
public record StackList(List<ItemKey> accepted, String description, int count, int minCount) implements Deliverable {
    public StackList {
        accepted = List.copyOf(accepted);
        if (accepted.isEmpty()) {
            throw new IllegalArgumentException("a StackList accepts at least one item: " + description);
        }
        Objects.requireNonNull(description, "description");
    }

    @Override
    public boolean matches(ItemKey item, ItemCatalog catalog) {
        return accepted.contains(item);
    }

    /** Its first accepted item. */
    @Override
    public Optional<ItemKey> displayed(ItemCatalog catalog) {
        return Optional.of(accepted.getFirst());
    }

    @Override
    public StackList withCount(int newCount) {
        return new StackList(accepted, description, newCount, minCount);
    }

    /** MC IDeliverable.canBeResolvedByBuilding default: the hut's own stock may serve it. */
    @Override
    public boolean canBeResolvedByBuilding() {
        return true;
    }

    /** MC StackList.equals: the same accepted items, in any order, whatever the counts and description. */
    @Override
    public boolean equals(Object o) {
        return o instanceof StackList other && new HashSet<>(accepted).equals(new HashSet<>(other.accepted));
    }

    @Override
    public int hashCode() {
        return new HashSet<>(accepted).hashCode();
    }

    @Override
    public String describe() {
        return count + " x " + description;
    }
}
