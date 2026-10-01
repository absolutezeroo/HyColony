package dev.hycolony.core.app.action;

import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.app.view.ColonyWindows;
import dev.hycolony.core.building.Building;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.ColonyAccess;
import dev.hycolony.core.colony.permission.Action;
import dev.hycolony.core.construction.workorder.ManualSelection;
import dev.hycolony.core.construction.workorder.WorkOrder;
import dev.hycolony.core.construction.workorder.WorkOrderRefusal;
import dev.hycolony.core.construction.workorder.WorkOrderType;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.Either;
import dev.hycolony.core.kernel.port.Msg;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * What players do to work orders: order one from a hut's window, cancel it there, select or cancel one from a builder
 * hut's Work orders tab, and reorder or delete orders from the town hall's list (MANAGE_HUTS). Each re-shows the window
 * it came from.
 */
public final class WorkOrderActions {
    private final ColonyManager manager;
    private final ColonyWindows windows;

    public WorkOrderActions(ColonyManager manager, ColonyWindows windows) {
        this.manager = manager;
        this.windows = windows;
    }

    /** {@link #order(UUID, BlockPos, WorkOrderType, String, Optional)} for any builder. */
    public Optional<WorkOrderRefusal> order(UUID player, BlockPos hutPos, WorkOrderType type, String style) {
        return order(player, hutPos, type, style, Optional.empty());
    }

    /**
     * The build options' Build/Upgrade/Repair/Deconstruct button (MC BuildRequestMessage): empty on success, the
     * order then reserved to {@code builder} if one is chosen; else the refusal, which the player is also told. Either
     * way the building's own window shows again to a player who may see the colony's huts (MC WindowBuildBuilding
     * closes with openGui); nothing for a hut gone.
     */
    public Optional<WorkOrderRefusal> order(
            UUID player, BlockPos hutPos, WorkOrderType type, String style, Optional<BlockPos> builder) {
        Colony c = manager.colonyAt(hutPos).orElse(null);
        Building b = c == null ? null : c.buildings().at(hutPos).orElse(null);
        if (c == null || b == null) {
            return Optional.of(WorkOrderRefusal.INVALID_TYPE); // the hut is gone
        }
        Either<WorkOrder, WorkOrderRefusal> r = c.work().request(player, hutPos, type, style, builder);
        if (r instanceof Either.Right(var refusal)) {
            manager.context()
                    .notifier()
                    .send(
                            player,
                            Msg.of("hycolony.workorder.refused."
                                    + refusal.name().toLowerCase(Locale.ROOT)));
            if (ColonyAccess.allows(c, player, Action.ACCESS_HUTS)) {
                windows.showBuildingGui(c, b, player);
            }
            return Optional.of(refusal);
        }
        windows.showBuildingGui(c, b, player);
        return Optional.empty();
    }

    /** The hut window's Cancel button (MANAGE_HUTS). */
    public boolean cancel(UUID player, BlockPos hutPos) {
        ManagedHut h = ManagedHut.find(manager, player, hutPos).orElse(null);
        if (h == null) {
            return false;
        }
        Optional<WorkOrder> order = h.colony().work().byBuilding(hutPos);
        if (order.isEmpty()) {
            return false;
        }
        h.colony().work().cancel(order.get().id());
        windows.showBuildingGui(h.colony(), h.building(), player);
        return true;
    }

    /**
     * The builder hut's Work orders tab, Select (MANAGE_HUTS): the hut claims the order (MC
     * BuildingBuilder.setWorkOrder). True once claimed (the hut window is re-shown); a refusal is told to the player.
     */
    public boolean select(UUID player, BlockPos builderHut, int orderId) {
        ManagedHut h = ManagedHut.find(manager, player, builderHut).orElse(null);
        if (h == null) {
            return false;
        }
        Optional<ManualSelection.Refusal> refused = ManualSelection.select(h.colony(), h.building(), orderId);
        if (refused.isPresent()) {
            manager.context().notifier().send(player, Msg.of(selectRefusalKey(refused.get())));
            return false;
        }
        windows.showBuilding(h.colony(), h.building(), player);
        return true;
    }

    /** MC's MESSAGE_WARNING_* of each refusal. */
    public static String selectRefusalKey(ManualSelection.Refusal r) {
        return switch (r) {
            case NO_WORKER -> "hycolony.workorder.select.noWorker";
            case NOT_FOR_BUILDER -> "hycolony.workorder.select.notForBuilder";
            case ALREADY_CLAIMED -> "hycolony.workorder.select.alreadyClaimed";
            case CANNOT_BUILD -> "hycolony.workorder.select.cannotBuild";
        };
    }

    /**
     * The builder hut's Work orders tab, Cancel (MANAGE_HUTS): removes any colony order by id, claimed by this hut or
     * not, as MC WorkOrderChangeMessage does. Returns false for an unknown hut, order or permission.
     */
    public boolean cancelFromBuilder(UUID player, BlockPos builderHut, int orderId) {
        ManagedHut h = ManagedHut.find(manager, player, builderHut).orElse(null);
        if (h == null || h.colony().work().byId(orderId).isEmpty()) {
            return false;
        }
        h.colony().work().cancel(orderId);
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
        windows.showTownHall(c, player);
        return true;
    }

    public boolean delete(UUID player, int colonyId, int orderId) {
        Colony c = managedColony(player, colonyId, orderId);
        if (c == null) {
            return false;
        }
        c.work().cancel(orderId);
        windows.showTownHall(c, player);
        return true;
    }

    /** The colony if it holds the order and the player may manage its huts, else null. */
    private @Nullable Colony managedColony(UUID player, int colonyId, int orderId) {
        return manager.byId(colonyId)
                .filter(c -> ColonyAccess.allows(c, player, Action.MANAGE_HUTS)
                        && c.work().byId(orderId).isPresent())
                .orElse(null);
    }
}
