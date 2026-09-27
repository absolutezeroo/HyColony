package dev.hycolony.core.construction.resources;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.building.ProvidesTab;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.ui.tab.ModuleTab;
import dev.hycolony.core.construction.workorder.Stage;
import dev.hycolony.core.construction.workorder.WorkOrder;
import dev.hycolony.core.kernel.item.Inventory;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.port.ItemCatalog;
import dev.hycolony.core.logistics.pickup.KeepRule;
import dev.hycolony.core.logistics.pickup.KeepsItems;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.ToIntFunction;
import org.jspecify.annotations.Nullable;

/**
 * The builder hut's current order: its needs split into buckets (recomputed on every start, as MC recomputes them on
 * load). Progress lives in the {@link WorkOrder}, which persists it; this module only writes through to it, so it
 * has nothing to save. Port of MC's BuildingResourcesModule.
 */
public final class BuildingResourcesModule implements KeepsItems, ProvidesTab {
    private @Nullable WorkOrder order;
    private NeededResources needs = NeededResources.empty();
    private List<Map<ItemKey, Integer>> buckets = List.of();

    /** Takes the order's stage and index as they are, and recomputes the buckets from {@code needs}. */
    public void start(WorkOrder o, NeededResources needs) {
        this.order = o;
        this.needs = needs;
        this.buckets = Buckets.split(needs.sequence(), needs::maxStack);
    }

    /** Forgets the order (completed, cancelled or lost): back to the state before any start. */
    public void reset() {
        this.order = null;
        this.needs = NeededResources.empty();
        this.buckets = List.of();
    }

    public Optional<Map<ItemKey, Integer>> currentBucket() {
        return bucket(0);
    }

    public Optional<Map<ItemKey, Integer>> nextBucket() {
        return bucket(1);
    }

    private Optional<Map<ItemKey, Integer>> bucket(int i) {
        return i < buckets.size() ? Optional.of(Collections.unmodifiableMap(buckets.get(i))) : Optional.empty();
    }

    /**
     * One block placed with {@code item}: reduces the needs and the first bucket holding it (placement order), and
     * drops that bucket once empty (MC's reduceNeededResource).
     */
    public void onPlaced(ItemKey item) {
        needs.reduce(item, 1);
        for (int i = 0; i < buckets.size(); i++) {
            Map<ItemKey, Integer> b = buckets.get(i);
            Integer n = b.get(item);
            if (n != null) {
                if (n > 1) {
                    b.put(item, n - 1);
                } else {
                    b.remove(item);
                    if (b.isEmpty()) {
                        buckets.remove(i);
                    }
                }
                return;
            }
        }
    }

    /**
     * Every item the order still needs, in its remaining amount, stays in the hut and the builder's inventory (MC
     * {@code AbstractBuildingStructureBuilder.getRequiredItemsAndAmount}).
     */
    @Override
    public List<KeepRule> keepRules(Building building, ItemCatalog catalog) {
        List<KeepRule> out = new ArrayList<>(needs.remaining().size());
        needs.remaining().forEach((item, n) -> out.add(new KeepRule(item::equals, n, true)));
        return out;
    }

    public NeededResources needs() {
        return needs;
    }

    /** Per item of the current and next bucket together: need minus (inventory + hut), strictly positive only. */
    public Map<ItemKey, Integer> missingForCurrentAndNext(Inventory builderInv, ToIntFunction<ItemKey> hutCount) {
        Map<ItemKey, Integer> out = new LinkedHashMap<>();
        for (int i = 0; i < 2 && i < buckets.size(); i++) {
            buckets.get(i).forEach((k, v) -> out.merge(k, v, Integer::sum));
        }
        out.replaceAll((k, v) -> v - builderInv.count(k) - hutCount.applyAsInt(k));
        out.values().removeIf(v -> v <= 0);
        return out;
    }

    /** 0 before any start (work order ids start at 1). */
    public int orderId() {
        return order == null ? 0 : order.id();
    }

    /** DONE before any start. */
    public Stage stage() {
        return order == null ? Stage.DONE : order.stage();
    }

    public int progressIndex() {
        return order == null ? 0 : order.progressIndex();
    }

    /** Writes through to the order, the single owner of progress. */
    public void progress(Stage s, int index) {
        if (order == null) {
            throw new IllegalStateException("no order started");
        }
        order.progress(s, index);
    }

    /**
     * The builder hut's Resources, Settings and Work orders tabs (MC BUILDING_RESOURCES, BUILDER_SETTINGS and
     * WORKORDER_VIEW module views).
     */
    @Override
    public ModuleTab tab(Colony colony, Building building, UUID viewer) {
        return BuilderTabsViews.of(colony, building, this, viewer);
    }
}
