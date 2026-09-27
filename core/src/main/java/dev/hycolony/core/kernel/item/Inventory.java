package dev.hycolony.core.kernel.item;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.ToIntFunction;
import org.jspecify.annotations.Nullable;

/**
 * A fixed number of slots, each holding at most one {@link ItemAmount}. Items are keys and counts only: item
 * metadata (tool durability, a hut's level) does not travel through a citizen's inventory (known limitation, backlog).
 */
public final class Inventory {
    private final @Nullable ItemAmount[] slots;
    private long changes;
    private Consumer<ItemKey> onGone = _ -> {};

    public Inventory(int slots) {
        this.slots = new @Nullable ItemAmount[slots];
    }

    public int size() {
        return slots.length;
    }

    /**
     * Merges into existing stacks of the same item first (up to {@code maxStack}), then fills empty slots.
     * Returns the remainder that did not fit, or {@code null} if everything was inserted.
     */
    public @Nullable ItemAmount insert(ItemAmount amount, ToIntFunction<ItemKey> maxStack) {
        Objects.requireNonNull(amount, "amount");
        int max = maxStack.applyAsInt(amount.item());
        int remaining = fillEmpty(amount.item(), merge(amount.item(), amount.count(), max), max);
        if (remaining != amount.count()) {
            changes++;
        }
        return remaining == 0 ? null : amount.withCount(remaining);
    }

    /** Tops up the stacks of {@code item} to {@code max}; returns what is left of {@code count}. */
    private int merge(ItemKey item, int count, int max) {
        int remaining = count;
        for (int i = 0; i < slots.length && remaining > 0; i++) {
            ItemAmount cur = slots[i];
            if (cur != null && cur.item().equals(item) && cur.count() < max) {
                int add = Math.min(max - cur.count(), remaining);
                slots[i] = cur.withCount(cur.count() + add);
                remaining -= add;
            }
        }
        return remaining;
    }

    /** Puts stacks of up to {@code max} in empty slots; returns what is left of {@code count}. */
    private int fillEmpty(ItemKey item, int count, int max) {
        int remaining = count;
        for (int i = 0; i < slots.length && remaining > 0; i++) {
            if (slots[i] == null) {
                int add = Math.min(max, remaining);
                slots[i] = new ItemAmount(item, add);
                remaining -= add;
            }
        }
        return remaining;
    }

    /** Removes up to {@code max}, from the last slots first. Returns how much was actually removed. */
    public int extract(ItemKey item, int max) {
        int removed = 0;
        for (int i = slots.length - 1; i >= 0 && removed < max; i--) {
            ItemAmount cur = slots[i];
            if (cur != null && cur.item().equals(item)) {
                int take = Math.min(cur.count(), max - removed);
                slots[i] = take == cur.count() ? null : cur.withCount(cur.count() - take);
                removed += take;
            }
        }
        if (removed > 0) {
            changes++;
            notifyIfGone(item);
        }
        return removed;
    }

    public int count(ItemKey item) {
        int total = 0;
        for (ItemAmount a : slots) {
            if (a != null && a.item().equals(item)) {
                total += a.count();
            }
        }
        return total;
    }

    public boolean isFull() {
        for (ItemAmount a : slots) {
            if (a == null) {
                return false;
            }
        }
        return true;
    }

    public int freeSlots() {
        int free = 0;
        for (ItemAmount a : slots) {
            if (a == null) {
                free++;
            }
        }
        return free;
    }

    /** Non-empty slots, in slot order. */
    public List<ItemAmount> contents() {
        List<ItemAmount> out = new ArrayList<>();
        for (ItemAmount a : slots) {
            if (a != null) {
                out.add(a);
            }
        }
        return out;
    }

    public Optional<ItemAmount> slot(int index) {
        return Optional.ofNullable(slots[index]);
    }

    /** Puts {@code amount} in {@code index}, or empties it; a player moving items in the citizen's window. */
    public void set(int index, Optional<ItemAmount> amount) {
        ItemAmount was = slots[index];
        slots[index] = amount.orElse(null);
        changes++;
        if (was != null) {
            notifyIfGone(was.item());
        }
    }

    /**
     * Calls {@code onGone} with an item each time the last of it leaves, by {@link #extract} or {@link #set}. Replaces
     * the previous listener; a {@link #copy} has none.
     */
    public void onGone(Consumer<ItemKey> onGone) {
        this.onGone = Objects.requireNonNull(onGone, "onGone");
    }

    private void notifyIfGone(ItemKey item) {
        if (count(item) == 0) {
            onGone.accept(item);
        }
    }

    /** Bumped by every change, so a view can tell its copy is stale without comparing slots. */
    public long changes() {
        return changes;
    }

    /** A detached copy of the slots, e.g. to compare before and after a player's move. */
    public Inventory copy() {
        Inventory out = new Inventory(slots.length);
        System.arraycopy(slots, 0, out.slots, 0, slots.length);
        return out;
    }

    public JsonArray write() {
        JsonArray out = new JsonArray();
        for (ItemAmount a : slots) {
            if (a == null) {
                out.add(JsonNull.INSTANCE);
            } else {
                JsonObject o = new JsonObject();
                o.addProperty("item", a.item().id());
                o.addProperty("count", a.count());
                out.add(o);
            }
        }
        return out;
    }

    public static Inventory read(JsonArray a, int slotCount) {
        Inventory inv = new Inventory(slotCount);
        for (int i = 0; i < a.size() && i < slotCount; i++) {
            var el = a.get(i);
            if (el == null || el.isJsonNull()) {
                continue;
            }
            JsonObject o = el.getAsJsonObject();
            inv.slots[i] = new ItemAmount(
                    new ItemKey(o.get("item").getAsString()), o.get("count").getAsInt());
        }
        return inv;
    }
}
