package dev.hycolony.core.construction.builder;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.Inventory;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.item.ToolInfo;
import dev.hycolony.core.kernel.item.ToolType;
import dev.hycolony.core.kernel.port.ContainerAccess;
import dev.hycolony.core.kernel.port.ItemCatalog;
import dev.hycolony.core.logistics.pickup.PickupRequests;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.ToIntFunction;
import org.jspecify.annotations.Nullable;

/**
 * The builder's items: its inventory and its hut's containers (MC AbstractEntityAIBasic dump, pickup and tool
 * helpers). Its requests are {@link BuilderRequests}'.
 */
final class BuilderStock {
    private static final System.Logger LOG = System.getLogger(BuilderStock.class.getName());

    /** MC EntityAIStructureBuilder.ACTIONS_UNTIL_DUMP (the builder's own, not CitizenConstants' 32 for others). */
    static final int ACTIONS_UNTIL_DUMP = 4096;
    /**
     * After a dump the full hut refused, the next full-inventory dump waits this many actions (drops meanwhile go to
     * the hut or are lost).
     */
    // ponytail: fixed retry, no hut capacity query in ContainerAccess; poll the hut's space if players complain.
    static final int DUMP_RETRY_ACTIONS = 32;

    private final Colony colony;
    private final CitizenData citizen;
    private final Building hut;
    private final ItemCatalog catalog;
    private final ContainerAccess containers;
    private final ToIntFunction<ItemKey> maxStack;
    private int dumpRetryAt;

    BuilderStock(Colony colony, CitizenData citizen, Building hut) {
        this.colony = colony;
        this.citizen = citizen;
        this.hut = hut;
        this.catalog = colony.context().ports().catalog();
        this.containers = colony.context().ports().containers();
        this.maxStack = catalog::maxStack;
    }

    Inventory inventory() {
        return citizen.inventory();
    }

    int hutCount(ItemKey item) {
        return containers.count(hut.containers(), item);
    }

    /**
     * Moves up to {@code max} from the hut to the inventory, each stack with its damage; what does not fit goes back to
     * the hut. Returns how many landed in the inventory.
     */
    int take(ItemKey item, int max) {
        if (max <= 0) {
            return 0;
        }
        List<BlockPos> hc = hut.containers();
        int landed = 0;
        for (ItemAmount got : containers.extractStacks(hc, item, max)) {
            ItemAmount rest = inventory().insert(got, maxStack);
            landed += got.count() - (rest == null ? 0 : rest.count());
            lose(rest == null ? null : containers.insert(hc, rest));
        }
        return landed;
    }

    /** Tops the inventory up to each amount of {@code bucket} from the hut. */
    void takeBucket(Map<ItemKey, Integer> bucket) {
        bucket.forEach((item, n) -> take(item, n - inventory().count(item)));
    }

    /** A dump every {@link #ACTIONS_UNTIL_DUMP} actions, or once the inventory is full and the retry delay passed. */
    boolean dumpDue(int actionsDone) {
        return actionsDone >= ACTIONS_UNTIL_DUMP || (inventory().isFull() && actionsDone >= dumpRetryAt);
    }

    /** The next full-inventory dump happens at once, whatever the retry delay. */
    void dumpNow() {
        dumpRetryAt = 0;
    }

    /**
     * Stores everything in the hut but (MC keepX) the {@code keep} amounts and one tool per type. When the hut could
     * not take it all (what could be stored is stored, the rest stays), or nothing was left to store, the next
     * full-inventory dump waits {@link #DUMP_RETRY_ACTIONS} instead of bouncing back at once. Then asks a courier to
     * empty the hut ({@link PickupRequests#afterDump}).
     */
    void dump(Map<ItemKey, Integer> keep) {
        int before = carried();
        boolean stored = storeAll(keep);
        dumpRetryAt = stored && !inventory().isFull() ? 0 : DUMP_RETRY_ACTIONS;
        PickupRequests.afterDump(colony, hut, before - carried());
    }

    private int carried() {
        int total = 0;
        for (ItemAmount a : inventory().contents()) {
            total += a.count();
        }
        return total;
    }

    /** Stores slot by slot, so each stack goes with its own damage. */
    private boolean storeAll(Map<ItemKey, Integer> keep) {
        List<BlockPos> hc = hut.containers();
        Map<ItemKey, Integer> keepLeft = new HashMap<>(keep);
        Set<ToolType> toolKept = EnumSet.noneOf(ToolType.class);
        for (int i = 0; i < inventory().size(); i++) {
            ItemAmount a = inventory().slot(i).orElse(null);
            if (a == null) {
                continue;
            }
            ToolInfo tool = catalog.tool(a.item()).orElse(null);
            if (tool != null && toolKept.add(tool.type())) {
                continue;
            }
            int kept = Math.min(a.count(), keepLeft.getOrDefault(a.item(), 0));
            keepLeft.computeIfPresent(a.item(), (_, n) -> n - kept);
            if (kept < a.count() && !store(i, a, kept, hc)) {
                return false;
            }
        }
        return true;
    }

    /** Stores all but {@code kept} of the stack {@code a} in {@code slot}; false when the hut could not take it all. */
    private boolean store(int slot, ItemAmount a, int kept, List<BlockPos> hc) {
        ItemAmount rest = containers.insert(hc, a.withCount(a.count() - kept));
        int stored = a.count() - kept - (rest == null ? 0 : rest.count());
        if (stored > 0) {
            inventory()
                    .set(slot, stored == a.count() ? Optional.empty() : Optional.of(a.withCount(a.count() - stored)));
        }
        return rest == null;
    }

    /** Drops go to the inventory, then the hut; what fits in neither is lost (logged). */
    void storeDrops(List<ItemAmount> drops) {
        for (ItemAmount d : drops) {
            ItemAmount rest = inventory().insert(d, maxStack);
            if (rest != null) {
                lose(containers.insert(hut.containers(), rest));
            }
        }
    }

    /** Items that fit neither in the inventory nor in the hut: logged as {@code debrisLost}. */
    private void lose(@Nullable ItemAmount rest) {
        if (rest != null) {
            colony.log().add("debrisLost", colony.day(), rest.item().id(), String.valueOf(rest.count()));
            LOG.log(
                    System.Logger.Level.DEBUG,
                    "Builder {0}: {1} x {2} lost, inventory and hut full",
                    citizen.name(),
                    rest.count(),
                    rest.item().id());
        }
    }

    /**
     * MC getMostEfficientTool: the lowest-level tool of {@code type} in the inventory within the hut's max equipment
     * level (the least powerful one that does the job). Null if none.
     */
    @Nullable
    ItemKey toolInInventory(ToolType type) {
        ItemKey best = null;
        int bestLevel = Integer.MAX_VALUE;
        for (ItemAmount a : inventory().contents()) {
            ToolInfo info = catalog.tool(a.item()).orElse(null);
            if (info != null
                    && info.type() == type
                    && info.level() <= hut.maxEquipmentLevel()
                    && info.level() < bestLevel) {
                best = a.item();
                bestLevel = info.level();
            }
        }
        return best;
    }

    /** A tool of {@code type} within the hut's max equipment level stored in the hut, or null. */
    @Nullable
    ItemKey toolInHut(ToolType type) {
        for (ItemKey item : containers.contents(hut.containers()).keySet()) {
            ToolInfo info = catalog.tool(item).orElse(null);
            if (info != null && info.type() == type && info.level() <= hut.maxEquipmentLevel()) {
                return item;
            }
        }
        return null;
    }

    float toolSpeed(@Nullable ItemKey tool) {
        return tool == null ? 1f : catalog.tool(tool).map(ToolInfo::speed).orElse(1f);
    }
}
