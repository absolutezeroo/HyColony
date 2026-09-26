package dev.hycolony.core.colony.view;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.ColonyContext;
import dev.hycolony.core.colony.ColonyManager;
import dev.hycolony.core.colony.permission.Action;
import dev.hycolony.core.construction.resources.BuildingResourcesModule;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.port.Msg;
import java.util.UUID;

/**
 * Shows the colony's windows (MC's Window* classes) through the UI port. Opening one needs ACCESS_HUTS, else the
 * player is told; the {@code show*} methods re-show a window after an action that already checked its permission.
 */
public final class ColonyWindows {
    private final ColonyManager manager;
    private final ColonyContext ctx;
    private final TownHallViews townHall;
    private final BuildingViews buildings;
    private final RequestViews requests;
    private final CitizenViews citizens;
    private final BuilderResourcesViews builderResources;

    public ColonyWindows(ColonyManager manager) {
        this.manager = manager;
        this.ctx = manager.context();
        this.townHall = new TownHallViews(ctx);
        this.builderResources = new BuilderResourcesViews(ctx);
        this.buildings = new BuildingViews(ctx, new BuilderTabsViews(builderResources));
        this.requests = new RequestViews(ctx);
        this.citizens = new CitizenViews(ctx, townHall, requests);
    }

    public void openTownHall(UUID player, BlockPos hutPos) {
        manager.colonyAt(hutPos).filter(c -> canAccess(c, player)).ifPresent(c -> showTownHall(c, player));
    }

    /** Right-click on a citizen (MC WindowCitizen). */
    public void openCitizen(UUID player, int colonyId, int citizenId) {
        Colony c = manager.byId(colonyId).orElse(null);
        CitizenData d = c == null ? null : c.citizens().get(citizenId).orElse(null);
        if (d == null || !canAccess(c, player)) {
            return;
        }
        ctx.ui().showCitizen(player, citizens.of(c, d, player));
    }

    /** Any hut's window; the town hall's window reaches it through its "building" action. */
    public void openBuilding(UUID player, BlockPos hutPos) {
        Colony c = manager.colonyAt(hutPos).orElse(null);
        Building b = c == null ? null : c.buildings().at(hutPos).orElse(null);
        if (b != null && canAccess(c, player)) {
            showBuilding(c, b, player);
        }
    }

    public void openWorkOrders(UUID player, int colonyId) {
        manager.byId(colonyId).filter(c -> canAccess(c, player)).ifPresent(c -> showWorkOrders(c, player));
    }

    /** The builder hut's resources tab. */
    public void openBuilderResources(UUID player, BlockPos hutPos) {
        Colony c = manager.colonyAt(hutPos).orElse(null);
        Building hut = c == null ? null : c.buildings().at(hutPos).orElse(null);
        BuildingResourcesModule m =
                hut == null ? null : hut.module(BuildingResourcesModule.class).orElse(null);
        if (m == null || !canAccess(c, player)) {
            return;
        }
        ctx.ui().showBuilderResources(player, builderResources.of(c, hut, m, player));
    }

    /** The clipboard (MC WindowClipBoard): root requests held by the player or retrying resolver. */
    public void openRequests(UUID player, int colonyId) {
        Colony c = manager.byId(colonyId).orElse(null);
        if (c == null || !canAccess(c, player)) {
            return;
        }
        ctx.ui().showRequests(player, requests.of(c, player));
    }

    /**
     * Re-shows the window after an action. Checks no permission: the caller has checked that {@code viewer} may
     * see it (ACCESS_HUTS, or the right its own action requires). Public for the colony actions.
     */
    public void showTownHall(Colony c, UUID viewer) {
        ctx.ui().showTownHall(viewer, townHall.of(c, viewer));
    }

    /**
     * Re-shows the window after an action. Checks no permission: the caller has checked that {@code viewer} may
     * see it (ACCESS_HUTS, or the right its own action requires). Public for the colony actions.
     */
    public void showBuilding(Colony c, Building b, UUID viewer) {
        ctx.ui().showBuilding(viewer, buildings.of(c, b, viewer));
    }

    /**
     * Re-shows the window after an action. Checks no permission: the caller has checked that {@code viewer} may
     * see it (ACCESS_HUTS, or the right its own action requires). Public for the colony actions.
     */
    public void showWorkOrders(Colony c, UUID viewer) {
        ctx.ui().showWorkOrders(viewer, WorkOrderViews.of(c, viewer));
    }

    /** ACCESS_HUTS, else the player is told. */
    private boolean canAccess(Colony c, UUID player) {
        if (c.permissions().hasPermission(player, Action.ACCESS_HUTS)) {
            return true;
        }
        ctx.notifier().send(player, Msg.of("hycolony.permission.denied", c.name()));
        return false;
    }
}
