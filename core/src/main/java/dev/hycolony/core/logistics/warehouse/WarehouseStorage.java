package dev.hycolony.core.logistics.warehouse;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.building.module.BuildingModule;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.permission.Action;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.Inventory;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.port.ContainerAccess;
import dev.hycolony.core.kernel.port.Msg;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Stores a courier's inventory into the warehouse racks (MC {@code TileEntityWareHouse.dumpInventoryIntoWareHouse}).
 * Holds only the time of the last "warehouse full" message, not saved: MC keeps it in the {@code TileEntityWareHouse}
 * field {@code lastNotification}, which is never written to NBT.
 */
public final class WarehouseStorage implements BuildingModule {
    /** MC Constants.TICKS_FIVE_MIN: the least time between two "warehouse full" messages. */
    public static final int TICKS_FIVE_MIN = 6000;

    private boolean notified;
    private long lastNotificationTick;

    /**
     * Moves each non-empty slot of {@code courier} into the rack chosen by {@link #rackFor}; what does not fit stays in
     * the slot. When no rack is left, tells the colony (at most every {@link #TICKS_FIVE_MIN}) and stops.
     */
    public void store(Colony colony, Building warehouse, Inventory courier) {
        ContainerAccess access = colony.context().ports().containers();
        for (int i = 0; i < courier.size(); i++) {
            ItemAmount stack = courier.slot(i).orElse(null);
            if (stack == null) {
                continue;
            }
            Optional<BlockPos> rack = rackFor(access, warehouse, stack.item());
            if (rack.isEmpty()) {
                notifyFull(colony, warehouse);
                return;
            }
            courier.set(i, Optional.ofNullable(access.insert(List.of(rack.get()), stack)));
        }
    }

    /**
     * MC getRackForStack: the first rack with a free slot that already holds {@code item}, else the first empty rack,
     * else the one with the most free slots; empty when every rack is full. Deviation from MC: MC's second choice (a
     * rack holding a "similar" item, same creative tab) is skipped, Hytale items have no creative tab in the core.
     */
    private static Optional<BlockPos> rackFor(ContainerAccess access, Building warehouse, ItemKey item) {
        List<BlockPos> racks = warehouse.containers();
        for (BlockPos rack : racks) {
            if (access.freeSlots(rack) > 0 && access.count(List.of(rack), item) > 0) {
                return Optional.of(rack);
            }
        }
        BlockPos emptiest = null;
        int mostFree = 0;
        for (BlockPos rack : racks) {
            int free = access.freeSlots(rack);
            if (free > 0 && access.contents(List.of(rack)).isEmpty()) {
                return Optional.of(rack);
            }
            if (free > mostFree) {
                mostFree = free;
                emptiest = rack;
            }
        }
        return Optional.ofNullable(emptiest);
    }

    /**
     * MC's full-warehouse message to the players receiving colony messages. Deviation from MC: storage upgrades are not
     * ported, so a max-level warehouse always gets MC's "max upgrade" text, never the "pay to upgrade the racks" one;
     * and the first message is never delayed. MC's is delayed only in the first {@link #TICKS_FIVE_MIN} ticks of a new
     * world: its unsaved {@code lastNotification} starts at 0 against a saved game time, so after a restart it also
     * sends at once.
     */
    private void notifyFull(Colony colony, Building warehouse) {
        long now = colony.context().clock().currentTick();
        if (notified && now - lastNotificationTick <= TICKS_FIVE_MIN) {
            return;
        }
        notified = true;
        lastNotificationTick = now;
        Msg full = Msg.of(
                warehouse.level() >= warehouse.type().maxLevel()
                        ? "hycolony.warehouse.fullMax"
                        : "hycolony.warehouse.full");
        for (UUID member : colony.permissions().members().keySet()) {
            if (colony.permissions().hasPermission(member, Action.RECEIVE_MESSAGES)) {
                colony.context().notifier().send(member, full);
            }
        }
    }
}
