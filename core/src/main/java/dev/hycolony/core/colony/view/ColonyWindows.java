package dev.hycolony.core.colony.view;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.ColonyContext;
import dev.hycolony.core.colony.ColonyManager;
import dev.hycolony.core.colony.permission.Action;
import dev.hycolony.core.colony.ui.BuildingView;
import dev.hycolony.core.colony.ui.CitizenView;
import dev.hycolony.core.colony.ui.TownHallView;
import dev.hycolony.core.colony.ui.WindowKey;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.port.Msg;
import java.util.Optional;
import java.util.UUID;

/**
 * Shows the colony's windows (MC's Window* classes) through the UI port. Opening one needs ACCESS_HUTS, else the
 * player is told; the {@code show*} methods re-show a window after an action that already checked its permission. The
 * hut, town hall and citizen windows shown stay live ({@link OpenWindows}).
 */
public final class ColonyWindows {
    private final ColonyManager manager;
    private final ColonyContext ctx;
    private final TownHallViews townHall;
    private final BuildingViews buildings;
    private final RequestViews requests;
    private final CitizenViews citizens;
    private final OpenWindows open;

    public ColonyWindows(ColonyManager manager) {
        this.manager = manager;
        this.ctx = manager.context();
        this.townHall = new TownHallViews(ctx);
        this.buildings = new BuildingViews(ctx, new BuilderTabsViews(new BuilderResourcesViews(ctx)));
        this.requests = new RequestViews(ctx);
        this.citizens = new CitizenViews(ctx, townHall, requests);
        this.open = new OpenWindows(ctx.ui());
    }

    public void openTownHall(UUID player, BlockPos hutPos) {
        manager.colonyAt(hutPos).filter(c -> canAccess(c, player)).ifPresent(c -> showTownHall(c, player));
    }

    /** Right-click on a citizen (MC WindowCitizen). */
    public void openCitizen(UUID player, int colonyId, int citizenId) {
        Colony c = manager.byId(colonyId).orElse(null);
        CitizenData d = c == null ? null : c.citizens().get(citizenId).orElse(null);
        if (c == null || d == null || !canAccess(c, player)) {
            return;
        }
        CitizenView view = citizens.of(c, d, player);
        ctx.ui().showCitizen(player, view);
        open.watch(
                player,
                new OpenWindows.Shown<>(new WindowKey.Citizen(c.id(), citizenId), view),
                () -> watchable(c.id(), player)
                        .flatMap(col -> col.citizens().get(citizenId).map(cd -> citizens.of(col, cd, player))),
                ctx.ui()::refreshCitizen);
    }

    /** Any hut's window; the town hall's window reaches it through its "building" action. */
    public void openBuilding(UUID player, BlockPos hutPos) {
        Colony c = manager.colonyAt(hutPos).orElse(null);
        Building b = c == null ? null : c.buildings().at(hutPos).orElse(null);
        if (c != null && b != null && canAccess(c, player)) {
            showBuilding(c, b, player);
        }
    }

    /**
     * The town hall's own hut window (MC "build" button on the Home tab): looked up on {@code colonyId} directly, not
     * through a hut position, since the caller already knows which colony it is showing. A missing colony or town
     * hall building is a silent no-op, like {@link #openBuilding}.
     */
    public void openTownHallBuilding(UUID player, int colonyId) {
        manager.byId(colonyId)
                .ifPresent(c -> c.buildings().townHall().ifPresent(b -> {
                    if (canAccess(c, player)) {
                        showBuilding(c, b, player);
                    }
                }));
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
        TownHallView view = townHall.of(c, viewer);
        ctx.ui().showTownHall(viewer, view);
        open.watch(
                viewer,
                new OpenWindows.Shown<>(new WindowKey.TownHall(c.id()), view),
                () -> watchable(c.id(), viewer).map(col -> townHall.of(col, viewer)),
                ctx.ui()::refreshTownHall);
    }

    /**
     * Re-shows the window after an action. Checks no permission: the caller has checked that {@code viewer} may
     * see it (ACCESS_HUTS, or the right its own action requires). Public for the colony actions.
     */
    public void showBuilding(Colony c, Building b, UUID viewer) {
        BuildingView view = buildings.of(c, b, viewer);
        ctx.ui().showBuilding(viewer, view);
        BlockPos pos = b.position();
        open.watch(
                viewer,
                new OpenWindows.Shown<>(new WindowKey.Hut(pos), view),
                () -> watchable(c.id(), viewer)
                        .flatMap(col -> col.buildings().at(pos).map(hut -> buildings.of(col, hut, viewer))),
                ctx.ui()::refreshBuilding);
    }

    /** MC's subscriber update: redraws each open colony window whose view changed. Called every tick. */
    public void tick() {
        open.tick();
    }

    /** The colony, while it exists and {@code viewer} may still see its windows (ACCESS_HUTS); silent. */
    private Optional<Colony> watchable(int colonyId, UUID viewer) {
        return manager.byId(colonyId).filter(c -> c.permissions().hasPermission(viewer, Action.ACCESS_HUTS));
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
