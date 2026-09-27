package dev.hycolony.core.construction.wand;

import dev.hycolony.core.building.BuildingType;
import dev.hycolony.core.colony.ColonyManager;
import dev.hycolony.core.colony.ui.WandView;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.BlockKey;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.port.Msg;
import dev.hycolony.core.kernel.port.PreviewPort;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * The build tool buttons (ST ItemBuildTool, WindowExtendedBuildTool, AbstractBlueprintManipulationWindow): each
 * updates the player's session, redraws the ghost and re-shows the window, and returns false on an invalid input
 * (nothing changes then). Sessions live in memory only.
 */
public final class WandActions {
    /** The six move buttons of Structurize's manipulation window (ST AbstractBlueprintManipulationWindow). */
    public enum Dir {
        FORWARD,
        BACK,
        LEFT,
        RIGHT,
        UP,
        DOWN
    }

    private final ColonyManager manager;
    private final Function<String, ItemKey> hutItem;
    private final WandSessions sessions = new WandSessions();
    private final WandPlacement placement;
    private final PasteQueue pastes;
    private final WandPaste paster;
    private final WandPreview preview;

    /**
     * {@code hutItem} and {@code hutBlock} turn a hut's logical {@code hutBlockKey} into its item and block keys,
     * which only the plugin's id map knows.
     */
    public WandActions(
            ColonyManager manager,
            PreviewPort previews,
            Function<String, ItemKey> hutItem,
            Function<String, BlockKey> hutBlock) {
        this.manager = manager;
        this.hutItem = hutItem;
        this.placement = new WandPlacement(manager, hutItem, hutBlock);
        this.pastes = new PasteQueue(manager);
        this.paster = new WandPaste(manager, placement, pastes);
        this.preview = new WandPreview(manager, previews);
    }

    /**
     * ST ItemBuildTool.useOn/use: a clicked block becomes the anchor; a click in the air keeps the current one, or
     * says {@code hycolony.wand.missingPos} and opens nothing (false) when there is none. No style is preselected:
     * like ST WindowExtendedBuildTool.onOpened, which sends a player without a pack to WindowSwitchPack, the window
     * offers no hut until one is chosen.
     */
    public boolean open(UUID player, Optional<BlockPos> clicked) {
        WandSession s = sessions.get(player);
        if (clicked.isPresent()) {
            s = s.withAnchor(clicked.get());
        } else if (s.anchor().isEmpty()) {
            manager.context().notifier().send(player, Msg.of("hycolony.wand.missingPos"));
            return false;
        }
        update(player, s);
        return true;
    }

    /**
     * Chooses one of the blueprint styles; a different one drops the chosen hut and its ghost, as ST
     * WindowExtendedBuildTool.init does on a pack change. False if unknown or the window was never opened.
     */
    public boolean selectStyle(UUID player, String style) {
        Optional<WandSession> s = opened(player);
        if (s.isEmpty() || !styles().contains(style)) {
            return false;
        }
        WandSession next = s.get().withStyle(style);
        update(player, style.equals(s.get().style()) ? next : next.withBuilding(""));
        return true;
    }

    /** Chooses one of the offered huts, capping the level at its maximum; false if it is not offered. */
    public boolean selectBuilding(UUID player, String buildingTypeId) {
        Optional<WandSession> s = opened(player);
        Optional<BuildingType> type = s.flatMap(session -> offered(player, session.style()).stream()
                .filter(t -> t.id().equals(buildingTypeId))
                .findFirst());
        if (type.isEmpty()) {
            return false;
        }
        int level = Math.min(s.get().level(), type.get().maxLevel());
        update(player, s.get().withBuilding(buildingTypeId).withLevel(level));
        return true;
    }

    /** Chooses the previewed level, 1 to the hut's maximum; false before a hut is chosen or out of range. */
    public boolean selectLevel(UUID player, int level) {
        Optional<WandSession> s = manipulable(player);
        if (s.isEmpty() || level < 1 || level > maxLevel(s.get())) {
            return false;
        }
        update(player, s.get().withLevel(level));
        return true;
    }

    /** Moves the anchor one block, relative to where the player faces now; false before a hut is chosen. */
    public boolean move(UUID player, Dir dir) {
        Optional<WandSession> s = manipulable(player);
        if (s.isEmpty()) {
            return false;
        }
        int facing = manager.context().players().facing(player);
        update(player, s.get().withAnchor(WandMoves.move(s.get().anchor().orElseThrow(), dir, facing)));
        return true;
    }

    /** Turns the plan a quarter turn; false before a hut is chosen. */
    public boolean rotate(UUID player, boolean clockwise) {
        Optional<WandSession> s = manipulable(player);
        if (s.isEmpty()) {
            return false;
        }
        update(player, s.get().withRotation(WandMoves.rotate(s.get().rotation(), clockwise)));
        return true;
    }

    /**
     * Places the hut (MC SurvivalHandler, see {@link WandPlacement}). A refusal is sent to the player and keeps the
     * session, ghost and window (false); a success forgets them and closes the window, except when a town hall
     * began founding a colony: the founding window has replaced ours and closing it would cancel the foundation.
     */
    public boolean confirm(UUID player, String playerName) {
        WandSession s = sessions.get(player);
        return finish(player, s, placement.confirm(player, playerName, s));
    }

    /**
     * The creative "Pretty" paste ({@link WandPaste}). False, with nothing changed, if the player is not in creative
     * mode: ST only offers the button then, and our window lives on the server, so the check is made again here. A
     * refusal is handled like {@link #confirm}'s; a paste keeps the session, ghost and window, as ST closes its window
     * after a survival placement only, except for a town hall whose founding window has replaced ours.
     */
    public boolean paste(UUID player, String playerName) {
        if (!manager.context().players().isCreative(player)) {
            return false;
        }
        WandSession s = sessions.get(player);
        WandPlacement.Result result = paster.paste(player, playerName, s);
        if (result instanceof WandPlacement.Placed) {
            show(player, s);
            return true;
        }
        return finish(player, s, result);
    }

    /** One core tick: the pastes in progress place their next blocks (ST Manager.onWorldTick). */
    public void tick() {
        pastes.tick();
    }

    private boolean finish(UUID player, WandSession s, WandPlacement.Result result) {
        if (result instanceof WandPlacement.Refused(var reason)) {
            manager.context().notifier().send(player, reason);
            show(player, s);
            return false;
        }
        forget(player);
        sessions.put(player, WandSession.empty().withStyle(s.style()));
        if (!(result instanceof WandPlacement.FoundColony)) {
            manager.context().ui().close(player);
        }
        return true;
    }

    /**
     * ST cancel: hides the ghost, forgets the anchor and the hut, and closes the window. Always true. The style is
     * kept, like ST's selected pack, which outlives the window.
     */
    public boolean cancel(UUID player) {
        String style = sessions.get(player).style();
        forget(player);
        sessions.put(player, WandSession.empty().withStyle(style));
        manager.context().ui().close(player);
        return true;
    }

    /** The player left: their ghost and session go, without touching a window they no longer have. */
    public void disconnect(UUID player) {
        forget(player);
    }

    private void forget(UUID player) {
        preview.hide(player);
        sessions.clear(player);
    }

    private void update(UUID player, WandSession s) {
        sessions.put(player, s);
        preview.refresh(player, s);
        show(player, s);
    }

    private void show(UUID player, WandSession s) {
        List<String> huts =
                offered(player, s.style()).stream().map(BuildingType::id).toList();
        WandView view = new WandView(
                styles(),
                huts,
                maxLevel(s),
                s.style(),
                s.buildingTypeId(),
                s.level(),
                s.rotation(),
                s.hasBuilding(),
                manager.context().players().isCreative(player));
        manager.context().ui().showWand(player, view);
    }

    /** The session of a player who opened the window (it has an anchor). */
    private Optional<WandSession> opened(UUID player) {
        return Optional.of(sessions.get(player)).filter(s -> s.anchor().isPresent());
    }

    /** An opened session with a hut chosen: the manipulation buttons show. */
    private Optional<WandSession> manipulable(UUID player) {
        return opened(player).filter(WandSession::hasBuilding);
    }

    /**
     * The huts with a plan in {@code style} (ST lists the blueprints of the pack); in survival, only those whose hut
     * block the player carries too (ST BLOCK_BLUEPRINT_REQUIREMENT). Empty before a style is chosen.
     */
    private List<BuildingType> offered(UUID player, String style) {
        boolean creative = manager.context().players().isCreative(player);
        return manager.context().buildingTypes().all().stream()
                .filter(t -> hasPlan(style, t) && (creative || carries(player, t)))
                .toList();
    }

    private boolean hasPlan(String style, BuildingType type) {
        return !style.isEmpty()
                && manager.context()
                        .ports()
                        .blueprints()
                        .load(style, type.id(), 1, 0)
                        .isPresent();
    }

    private boolean carries(UUID player, BuildingType type) {
        return manager.context().ports().playerInventory().count(player, hutItem.apply(type.hutBlockKey())) > 0;
    }

    private int maxLevel(WandSession s) {
        return manager.context()
                .buildingTypes()
                .byId(s.buildingTypeId())
                .map(BuildingType::maxLevel)
                .orElse(0);
    }

    private List<String> styles() {
        return manager.context().ports().blueprints().styles();
    }
}
