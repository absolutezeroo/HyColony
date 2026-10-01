package dev.hycolony.core.app.view;

import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.app.ui.BuildingView;
import dev.hycolony.core.app.ui.CitizenView;
import dev.hycolony.core.app.ui.RequestsView;
import dev.hycolony.core.app.ui.TownHallView;
import dev.hycolony.core.app.ui.UiPort;
import dev.hycolony.core.app.ui.WindowKey;
import dev.hycolony.core.building.Building;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.ColonyAccess;
import dev.hycolony.core.colony.ColonyContext;
import dev.hycolony.core.colony.ColonyRefusal;
import dev.hycolony.core.colony.permission.Action;
import dev.hycolony.core.kernel.BlockPos;
import java.util.Optional;
import java.util.UUID;

/**
 * Shows the colony's windows (MC's Window* classes) through the UI port. Opening one needs ACCESS_HUTS, else the
 * player is told; the {@code show*} methods re-show a window after an action that already checked its permission. The
 * hut, town hall, citizen, clipboard and field windows shown stay live ({@link OpenWindows}).
 */
public final class ColonyWindows {
    private final ColonyManager manager;
    private final ColonyContext ctx;
    private final TownHallViews townHall;
    private final BuildingViews buildings;
    private final RequestViews requests;
    private final CitizenViews citizens;
    private final FieldViews fields;
    private final OpenWindows open;
    private final UiPort ui;

    public ColonyWindows(ColonyManager manager, UiPort ui) {
        this.manager = manager;
        this.ui = ui;
        this.ctx = manager.context();
        this.townHall = new TownHallViews(ctx);
        this.buildings = new BuildingViews(ctx);
        this.requests = new RequestViews(ctx);
        this.citizens = new CitizenViews(ctx, townHall, requests);
        this.fields = new FieldViews(ctx);
        this.open = new OpenWindows(ui);
    }

    /** The port the players' windows open through. */
    public UiPort ui() {
        return ui;
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
        ui.showCitizen(player, view);
        open.watch(
                player,
                new OpenWindows.Shown<>(new WindowKey.Citizen(c.id(), citizenId), view),
                () -> watchable(c.id(), player)
                        .flatMap(col -> col.citizens().get(citizenId).map(cd -> citizens.of(col, cd, player))),
                ui::refreshCitizen);
    }

    /** Any hut's window; false, showing nothing, for a missing hut or a viewer without access. */
    public boolean openBuilding(UUID player, BlockPos hutPos) {
        Colony c = manager.colonyAt(hutPos).orElse(null);
        Building b = c == null ? null : c.buildings().at(hutPos).orElse(null);
        if (c == null || b == null || !canAccess(c, player)) {
            return false;
        }
        showBuilding(c, b, player);
        return true;
    }

    /**
     * A hut's build options (MC WindowBuildBuilding, opened by the "build" button); needs ACCESS_HUTS, a missing hut is
     * a silent no-op.
     */
    public void openBuildOptions(UUID player, BlockPos hutPos) {
        Colony c = manager.colonyAt(hutPos).orElse(null);
        Building b = c == null ? null : c.buildings().at(hutPos).orElse(null);
        if (c != null && b != null && canAccess(c, player)) {
            ui.showBuildOptions(player, BuildOptionsViews.of(c, b, buildings.of(c, b, player)));
        }
    }

    /** {@link #showBuildingGui} for a player who may see the colony's huts (ACCESS_HUTS); a missing hut is a no-op. */
    public void openBuildingGui(UUID player, BlockPos hutPos) {
        Colony c = manager.colonyAt(hutPos).orElse(null);
        Building b = c == null ? null : c.buildings().at(hutPos).orElse(null);
        if (c != null && b != null && canAccess(c, player)) {
            showBuildingGui(c, b, player);
        }
    }

    /**
     * MC IBuildingView.openGui: the town hall's window for the town hall, the hut's window for any other hut. Checks
     * no permission, like the other {@code show*} methods.
     */
    public void showBuildingGui(Colony c, Building b, UUID viewer) {
        if (c.buildings().townHall().filter(b::equals).isPresent()) {
            showTownHall(c, viewer);
        } else {
            showBuilding(c, b, viewer);
        }
    }

    /**
     * The clipboard (MC WindowClipBoard): root requests held by the player or retrying resolver, the async ones only
     * with {@code showImportant}, then watched as MC's RequestTreeWindowModule.onUpdate refreshes it; nothing for an
     * unknown colony or a viewer without access.
     */
    public void openRequests(UUID player, int colonyId, boolean showImportant) {
        Colony c = manager.byId(colonyId).orElse(null);
        if (c == null || !canAccess(c, player)) {
            return;
        }
        RequestsView view = requests.of(c, player, showImportant);
        ui.showRequests(player, view);
        open.watch(
                player,
                new OpenWindows.Shown<>(new WindowKey.Clipboard(c.id()), view),
                () -> watchable(c.id(), player).map(col -> requests.of(col, player, showImportant)),
                ui::refreshRequests);
    }

    /**
     * A field block's window (MC WindowField), watched as MC redraws it every tick: the farmer, seed and radii, and
     * each side seen from where the viewer looks now. Checks no permission (MC opens it for anyone); nothing for a
     * position that is not one of the colony's fields.
     */
    public void showField(Colony c, BlockPos pos, UUID player) {
        fields.of(c, pos, player).ifPresent(view -> {
            ui.showField(player, view);
            open.watch(
                    player,
                    new OpenWindows.Shown<>(new WindowKey.Field(pos), view),
                    () -> manager.byId(c.id()).flatMap(col -> fields.of(col, pos, player)),
                    ui::refreshField);
        });
    }

    /**
     * Re-shows the window after an action. Checks no permission: the caller has checked that {@code viewer} may
     * see it (ACCESS_HUTS, or the right its own action requires). Public for the colony actions.
     */
    public void showTownHall(Colony c, UUID viewer) {
        TownHallView view = townHall.of(c, viewer);
        ui.showTownHall(viewer, view);
        open.watch(
                viewer,
                new OpenWindows.Shown<>(new WindowKey.TownHall(c.id()), view),
                () -> watchable(c.id(), viewer).map(col -> townHall.of(col, viewer)),
                ui::refreshTownHall);
    }

    /**
     * Re-shows the window after an action. Checks no permission: the caller has checked that {@code viewer} may
     * see it (ACCESS_HUTS, or the right its own action requires). Public for the colony actions.
     */
    public void showBuilding(Colony c, Building b, UUID viewer) {
        BuildingView view = buildings.of(c, b, viewer);
        ui.showBuilding(viewer, view);
        BlockPos pos = b.position();
        open.watch(
                viewer,
                new OpenWindows.Shown<>(new WindowKey.Hut(pos), view),
                () -> watchable(c.id(), viewer)
                        .flatMap(col -> col.buildings().at(pos).map(hut -> buildings.of(col, hut, viewer))),
                ui::refreshBuilding);
    }

    /** MC's subscriber update: redraws each open colony window whose view changed. Called every tick. */
    public void tick() {
        open.tick();
    }

    /** The colony, while it exists and {@code viewer} may still see its windows (ACCESS_HUTS); silent. */
    private Optional<Colony> watchable(int colonyId, UUID viewer) {
        return manager.byId(colonyId).filter(c -> ColonyAccess.allows(c, viewer, Action.ACCESS_HUTS));
    }

    /** ACCESS_HUTS, else the player is told. */
    private boolean canAccess(Colony c, UUID player) {
        if (ColonyAccess.allows(c, player, Action.ACCESS_HUTS)) {
            return true;
        }
        ColonyRefusal.tell(c, player);
        return false;
    }
}
