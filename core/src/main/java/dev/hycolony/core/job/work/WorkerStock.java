package dev.hycolony.core.job.work;

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
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.ToIntFunction;
import org.jspecify.annotations.Nullable;

/**
 * A worker's items: its inventory and its hut's containers (MC AbstractEntityAIBasic dumpInventory, keepX, pickup and
 * getMostEfficientTool helpers). Any job composes one around its citizen and hut.
 */
public final class WorkerStock {
    private static final System.Logger LOG = System.getLogger(WorkerStock.class.getName());

    /**
     * After a dump the full hut refused, the next full-inventory dump waits this many actions (drops meanwhile go to
     * the hut or are lost).
     */
    // ponytail: fixed retry, no hut capacity query in ContainerAccess; poll the hut's space if players complain.
    private static final int DUMP_RETRY_ACTIONS = 32;

    private final Colony colony;
    private final CitizenData citizen;
    private final Building hut;
    private final ItemCatalog catalog;
    private final ContainerAccess containers;
    private final ToIntFunction<ItemKey> maxStack;
    private final int actionsUntilDump;
    private int dumpRetryAt;

    /**
     * The stock of {@code citizen} working at {@code hut}, dumping every {@code actionsUntilDump} actions (MC
     * getActionsDoneUntilDumping: CitizenConstants.ACTIONS_UNTIL_DUMP, 32, unless the job overrides it).
     */
    public WorkerStock(Colony colony, CitizenData citizen, Building hut, int actionsUntilDump) {
        this.colony = colony;
        this.citizen = citizen;
        this.hut = hut;
        this.actionsUntilDump = actionsUntilDump;
        this.catalog = colony.context().ports().catalog();
        this.containers = colony.context().ports().containers();
        this.maxStack = catalog::maxStack;
    }

    public Inventory inventory() {
        return citizen.inventory();
    }

    /** How many of {@code item} the hut holds, a worn-out tool apart (it serves nothing). */
    public int hutCount(ItemKey item) {
        int total = 0;
        for (BlockPos container : hut.containers()) {
            for (ItemAmount a : containers.stacks(container)) {
                total += a.item().equals(item) && !catalog.wornOut(a) ? a.count() : 0;
            }
        }
        return total;
    }

    /**
     * Moves up to {@code max} from the hut to the inventory, each stack with its damage; a worn-out tool stays in the
     * hut, and what does not fit goes back there. Returns how many landed in the inventory.
     */
    public int take(ItemKey item, int max) {
        if (max <= 0) {
            return 0;
        }
        List<BlockPos> hc = hut.containers();
        int landed = 0;
        for (ItemAmount got : containers.extractStacks(hc, item, max, a -> !catalog.wornOut(a))) {
            ItemAmount rest = inventory().insert(got, maxStack);
            landed += got.count() - (rest == null ? 0 : rest.count());
            lose(rest == null ? null : containers.insert(hc, rest));
        }
        return landed;
    }

    /** A dump every {@code actionsUntilDump} actions, or once the inventory is full and the retry delay passed. */
    public boolean dumpDue(int actionsDone) {
        return actionsDone >= actionsUntilDump || (inventory().isFull() && actionsDone >= dumpRetryAt);
    }

    /** The next full-inventory dump happens at once, whatever the retry delay. */
    public void dumpNow() {
        dumpRetryAt = 0;
    }

    /**
     * Stores everything in the hut but (MC keepX) the {@code keep} amounts and one tool per type, not a worn-out one.
     * When the hut could not take it all (what could be stored is stored, the rest stays), or nothing was left to
     * store, the next full-inventory dump waits {@link #DUMP_RETRY_ACTIONS} instead of bouncing back at once. Then asks
     * a courier to empty the hut ({@link PickupRequests#afterDump}).
     */
    public void dump(Map<ItemKey, Integer> keep) {
        dump(keep, true);
    }

    /**
     * {@link #dump(Map)}; {@code pickupAllowed} false (MC isAfterDumpPickupAllowed) asks for no courier unless the hut
     * is full, e.g. while a crafter's task is under way.
     */
    public void dump(Map<ItemKey, Integer> keep, boolean pickupAllowed) {
        int before = carried();
        boolean stored = storeAll(keep);
        dumpRetryAt = stored && !inventory().isFull() ? 0 : DUMP_RETRY_ACTIONS;
        PickupRequests.afterDump(colony, hut, before - carried(), pickupAllowed);
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
            if (tool != null && !catalog.wornOut(a) && toolKept.add(tool.type())) {
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
    public void storeDrops(List<ItemAmount> drops) {
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
                    "Worker {0}: {1} x {2} lost, inventory and hut full",
                    citizen.name(),
                    rest.count(),
                    rest.item().id());
        }
    }

    /**
     * MC getMostEfficientTool: the slot of the lowest-level tool of {@code type} in the inventory within the hut's max
     * equipment level (the least powerful one that does the job), the first such slot; empty if none. The worker
     * holds and wears that slot (MC setHeldItem(hand, slot)).
     */
    public OptionalInt toolInInventory(ToolType type) {
        int best = -1;
        int bestLevel = Integer.MAX_VALUE;
        for (int i = 0; i < inventory().size(); i++) {
            ItemAmount a = inventory().slot(i).orElse(null);
            ToolInfo info = a == null ? null : usableTool(a, type);
            if (info != null && info.level() < bestLevel) {
                best = i;
                bestLevel = info.level();
            }
        }
        return best < 0 ? OptionalInt.empty() : OptionalInt.of(best);
    }

    /** A tool of {@code type} within the hut's max equipment level stored in the hut; empty if none. */
    public Optional<ItemKey> toolInHut(ToolType type) {
        for (BlockPos container : hut.containers()) {
            for (ItemAmount a : containers.stacks(container)) {
                if (usableTool(a, type) != null) {
                    return Optional.of(a.item());
                }
            }
        }
        return Optional.empty();
    }

    /** {@code a}'s tool info when it is a tool of {@code type} the hut allows and not worn out, else null. */
    private @Nullable ToolInfo usableTool(ItemAmount a, ToolType type) {
        ToolInfo info = catalog.tool(a.item()).orElse(null);
        return info != null && info.type() == type && info.level() <= hut.maxEquipmentLevel() && !catalog.wornOut(a)
                ? info
                : null;
    }

    /** The speed of {@code tool}; 1 for bare hands or an item that is no tool. */
    public float toolSpeed(@Nullable ItemKey tool) {
        return tool == null ? 1f : catalog.tool(tool).map(ToolInfo::speed).orElse(1f);
    }
}
