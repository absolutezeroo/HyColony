package dev.hycolony.core.colony.action;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.ColonyManager;
import dev.hycolony.core.colony.permission.Action;
import dev.hycolony.core.colony.view.ColonyWindows;
import dev.hycolony.core.construction.workorder.WorkOrder;
import dev.hycolony.core.construction.workorder.WorkOrderRefusal;
import dev.hycolony.core.construction.workorder.WorkOrderType;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.Either;
import dev.hycolony.core.kernel.port.Msg;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * What players do to work orders: order one from a hut's window, cancel it there, and reorder or delete orders from
 * the town hall's list (MANAGE_HUTS). Each re-shows the window it came from.
 */
public final class WorkOrderActions {
    private final ColonyManager manager;
    private final ColonyWindows windows;

    public WorkOrderActions(ColonyManager manager, ColonyWindows windows) {
        this.manager = manager;
        this.windows = windows;
    }

    /**
     * The hut window's Build/Upgrade/Repair/Deconstruct button: empty on success (the window is re-shown), else the
     * refusal, which the player is also told.
     */
    public Optional<WorkOrderRefusal> order(UUID player, BlockPos hutPos, WorkOrderType type, String style) {
        Colony c = manager.colonyAt(hutPos).orElse(null);
        Building b = c == null ? null : c.buildings().at(hutPos).orElse(null);
        if (b == null) {
            return Optional.of(WorkOrderRefusal.INVALID_TYPE); // the hut is gone
        }
        Either<WorkOrder, WorkOrderRefusal> r = c.work().request(player, hutPos, type, style, Optional.empty());
        if (r instanceof Either.Right<WorkOrder, WorkOrderRefusal> refused) {
            manager.context()
                    .notifier()
                    .send(
                            player,
                            Msg.of("hycolony.workorder.refused."
                                    + refused.value().name().toLowerCase(Locale.ROOT)));
            return Optional.of(refused.value());
        }
        windows.showBuilding(c, b, player);
        return Optional.empty();
    }

    /** The hut window's Cancel button (MANAGE_HUTS). */
    public boolean cancel(UUID player, BlockPos hutPos) {
        ManagedHut h = ManagedHut.find(manager, player, hutPos).orElse(null);
        Optional<WorkOrder> order =
                h == null ? Optional.empty() : h.colony().work().byBuilding(hutPos);
        if (order.isEmpty()) {
            return false;
        }
        h.colony().work().cancel(order.get().id());
        windows.showBuilding(h.colony(), h.building(), player);
        return true;
    }

    /** Town hall info tab up/down arrows: {@code delta > 0} moves the order up. */
    public boolean move(UUID player, int colonyId, int orderId, int delta) {
        Colony c = managedColony(player, colonyId, orderId);
        if (c == null) {
            return false;
        }
        c.work().move(orderId, delta);
        windows.showWorkOrders(c, player);
        return true;
    }

    public boolean delete(UUID player, int colonyId, int orderId) {
        Colony c = managedColony(player, colonyId, orderId);
        if (c == null) {
            return false;
        }
        c.work().cancel(orderId);
        windows.showWorkOrders(c, player);
        return true;
    }

    /** The colony if it holds the order and the player may manage its huts, else null. */
    private Colony managedColony(UUID player, int colonyId, int orderId) {
        return manager.byId(colonyId)
                .filter(c -> c.permissions().hasPermission(player, Action.MANAGE_HUTS)
                        && c.work().byId(orderId).isPresent())
                .orElse(null);
    }
}
