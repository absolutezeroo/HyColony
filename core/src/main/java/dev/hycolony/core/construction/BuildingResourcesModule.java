package dev.hycolony.core.construction;

import com.google.gson.JsonObject;
import dev.hycolony.core.building.PersistentModule;
import dev.hycolony.core.kernel.item.Inventory;
import dev.hycolony.core.kernel.item.ItemKey;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.ToIntFunction;

/**
 * The builder hut's current order: its progress (persisted) and its needs split into buckets (recomputed on load,
 * as in MineColonies). Port of MC's BuildingResourcesModule.
 */
public final class BuildingResourcesModule implements PersistentModule {
    private int orderId; // 0 = none; work order ids start at 1
    private Stage stage = Stage.DONE;
    private int progressIndex;
    private NeededResources needs = NeededResources.empty();
    private List<Map<ItemKey, Integer>> buckets = List.of();
    private int current;

    /** Recomputes the buckets. Progress is kept when resuming the same order, else taken from the order. */
    public void start(WorkOrder o, NeededResources needs) {
        if (o.id() != orderId) {
            orderId = o.id();
            stage = o.stage();
            progressIndex = o.progressIndex();
        }
        this.needs = needs;
        this.buckets = Buckets.split(needs.remaining(), needs::maxStack);
        this.current = 0;
    }

    public Optional<Map<ItemKey, Integer>> currentBucket() { return bucket(current); }

    public Optional<Map<ItemKey, Integer>> nextBucket() { return bucket(current + 1); }

    private Optional<Map<ItemKey, Integer>> bucket(int i) {
        return i < buckets.size() ? Optional.of(buckets.get(i)) : Optional.empty();
    }

    /**
     * Moves on once the current bucket's blocks are all placed (MC drops a bucket when its map empties). Merely
     * holding the items is not enough: they would then also be counted against the next bucket's request.
     * Inventory and hut are part of the contract but not needed for that test.
     */
    public void advanceBucketIfSatisfied(Inventory builderInv, ToIntFunction<ItemKey> hutCount) {
        while (current < buckets.size() && outstanding(buckets.get(current), current + 1).isEmpty()) {
            current++;
        }
    }

    public NeededResources needs() { return needs; }

    /** Per item of the current and next bucket: what is still to place there, minus inventory and hut; > 0 only. */
    public Map<ItemKey, Integer> missingForCurrentAndNext(Inventory builderInv, ToIntFunction<ItemKey> hutCount) {
        Map<ItemKey, Integer> want = new LinkedHashMap<>();
        currentBucket().ifPresent(b -> b.forEach((k, v) -> want.merge(k, v, Integer::sum)));
        nextBucket().ifPresent(b -> b.forEach((k, v) -> want.merge(k, v, Integer::sum)));
        Map<ItemKey, Integer> out = outstanding(want, current + 2);
        out.replaceAll((k, v) -> v - builderInv.count(k) - hutCount.applyAsInt(k));
        out.values().removeIf(v -> v <= 0);
        return out;
    }

    /**
     * What of {@code items} is not placed yet. Placement consumes buckets front to back, so an item's share in
     * these buckets is what remains minus what the buckets from {@code after} on still hold.
     */
    private Map<ItemKey, Integer> outstanding(Map<ItemKey, Integer> items, int after) {
        Map<ItemKey, Integer> out = new LinkedHashMap<>();
        for (Map.Entry<ItemKey, Integer> e : items.entrySet()) {
            int later = 0;
            for (int j = after; j < buckets.size(); j++) {
                later += buckets.get(j).getOrDefault(e.getKey(), 0);
            }
            int left = Math.min(e.getValue(), needs.remaining().getOrDefault(e.getKey(), 0) - later);
            if (left > 0) {
                out.put(e.getKey(), left);
            }
        }
        return out;
    }

    public int orderId() { return orderId; }

    public Stage stage() { return stage; }

    public int progressIndex() { return progressIndex; }

    public void progress(Stage s, int index) {
        this.stage = s;
        this.progressIndex = index;
    }

    @Override
    public void write(JsonObject out) {
        out.addProperty("orderId", orderId);
        out.addProperty("stage", stage.name());
        out.addProperty("progressIndex", progressIndex);
    }

    @Override
    public void read(JsonObject in) {
        orderId = in.has("orderId") ? in.get("orderId").getAsInt() : 0;
        progressIndex = in.has("progressIndex") ? in.get("progressIndex").getAsInt() : 0;
        stage = Stage.DONE;
        if (in.has("stage")) {
            try {
                stage = Stage.valueOf(in.get("stage").getAsString());
            } catch (IllegalArgumentException unknown) {
                // stays DONE
            }
        }
        needs = NeededResources.empty();
        buckets = List.of();
        current = 0;
    }
}
