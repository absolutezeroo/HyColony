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
    private final ColonyManager manager;
    private final Function<String, ItemKey> hutItem;
    private final WandSessions sessions = new WandSessions();
    private final WandPlacement placement;
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
        this.preview = new WandPreview(manager, previews);
    }

    /**
     * ST ItemBuildTool.useOn/use: a clicked block becomes the anchor; a click in the air keeps the current one, or
     * says {@code hycolony.wand.missingPos} and opens nothing (false) when there is none. The first style is chosen
     * on first open.
     */
    public boolean open(UUID player, Optional<BlockPos> clicked) {
        WandSession s = sessions.get(player);
        if (clicked.isPresent()) {
            s = s.withAnchor(clicked.get());
        } else if (s.anchor().isEmpty()) {
            manager.context().notifier().send(player, Msg.of("hycolony.wand.missingPos"));
            return false;
        }
        List<String> styles = styles();
        if (s.style().isEmpty() && !styles.isEmpty()) {
            s = s.withStyle(styles.get(0));
        }
        update(player, s);
        return true;
    }

    /** Chooses one of the blueprint styles; false if unknown or the window was never opened. */
    public boolean selectStyle(UUID player, String style) {
        Optional<WandSession> s = opened(player);
        if (s.isEmpty() || !styles().contains(style)) {
            return false;
        }
        update(player, s.get().withStyle(style));
        return true;
    }

    /** Chooses one of the offered huts, capping the level at its maximum; false if it is not offered. */
    public boolean selectBuilding(UUID player, String buildingTypeId) {
        Optional<WandSession> s = opened(player);
        Optional<BuildingType> type = offered(player).stream()
                .filter(t -> t.id().equals(buildingTypeId))
                .findFirst();
        if (s.isEmpty() || type.isEmpty()) {
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
    public boolean move(UUID player, WandMoves.Dir dir) {
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
     * session, ghost and window (false); a success forgets them and closes the window.
     */
    public boolean confirm(UUID player, String playerName) {
        WandSession s = sessions.get(player);
        if (placement.confirm(player, playerName, s) instanceof WandPlacement.Refused refused) {
            manager.context().notifier().send(player, refused.reason());
            show(player, s);
            return false;
        }
        forget(player);
        manager.context().ui().close(player);
        return true;
    }

    /** ST cancel: hides the ghost, forgets the selection and anchor, and closes the window. Always true. */
    public boolean cancel(UUID player) {
        forget(player);
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
        List<String> huts = offered(player).stream().map(BuildingType::id).toList();
        WandView view = new WandView(
                styles(), huts, maxLevel(s), s.style(), s.buildingTypeId(), s.level(), s.rotation(), s.hasBuilding());
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

    /** Every hut in creative; in survival only those whose hut block the player carries (ST BLOCK_BLUEPRINT_REQUIREMENT). */
    private List<BuildingType> offered(UUID player) {
        List<BuildingType> all = manager.context().buildingTypes().all();
        if (manager.context().players().isCreative(player)) {
            return all;
        }
        return all.stream().filter(t -> carries(player, t)).toList();
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
