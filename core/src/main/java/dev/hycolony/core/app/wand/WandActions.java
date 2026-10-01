package dev.hycolony.core.app.wand;

import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.BlockKey;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.port.Msg;
import dev.hycolony.core.kernel.port.PreviewPort;
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

    /** ST AbstractBlueprintManipulationWindow: a click in the air puts the position this many blocks ahead. */
    private static final int AHEAD_BLOCKS = 10;

    private final ColonyManager manager;
    private final WandViews views;
    private final WandPacks packs;
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
        this.views = new WandViews(manager, hutItem);
        this.packs = new WandPacks(manager);
        this.placement = new WandPlacement(manager, hutItem, hutBlock);
        this.pastes = new PasteQueue(manager);
        this.paster = new WandPaste(manager, placement, pastes);
        this.preview = new WandPreview(manager, previews);
    }

    /**
     * ST ItemBuildTool.useOn/use and AbstractBlueprintManipulationWindow's constructor: the position is set only when
     * there is none (the clicked block, or 10 blocks ahead of the player for a click in the air); otherwise the ghost
     * stays where it is. Without a pack chosen, one is picked at random (ST StructurePacks.ensureSelectedPack); with
     * no pack at all the pack window opens (ST onOpened). The tip shows when the position is new. False, saying
     * {@code hycolony.wand.missingPos}, when no position can be found (the player's own is unknown).
     */
    public boolean open(UUID player, Optional<BlockPos> clicked) {
        WandSession s = sessions.get(player);
        boolean tip = s.anchor().isEmpty();
        if (s.anchor().isEmpty()) {
            Optional<BlockPos> at = clicked.or(() -> ahead(player));
            if (at.isEmpty()) {
                manager.context().notifier().send(player, Msg.of("hycolony.wand.missingPos"));
                return false;
            }
            s = s.withAnchor(at.get());
        }
        if (s.style().isEmpty()) {
            s = s.withStyle(packs.random().orElse(""));
        }
        s = s.withNav(s.nav().reopened(s.hasBuilding(), views.maxLevel(s) > 1));
        sessions.put(player, s);
        preview.refresh(player, s);
        if (s.style().isEmpty()) {
            packs.open(player, false);
        } else {
            manager.windows().ui().showWand(player, views.of(player, s, tip));
        }
        return true;
    }

    /** ST: {@code player.blockPosition().relative(player.getDirection(), 10)}; empty when the position is unknown. */
    private Optional<BlockPos> ahead(UUID player) {
        int facing = manager.context().players().facing(player);
        return manager.context().players().position(player).map(p -> WandMoves.ahead(p, facing, AHEAD_BLOCKS));
    }

    /**
     * ST WindowSwitchPack select: chooses a style and shows the build tool; a different one starts the window anew
     * and drops the chosen hut, its ghost and its rotation, as ST WindowExtendedBuildTool.init does on a pack change
     * (RenderingCache.removeBlueprint). False if unknown or the window was never opened.
     */
    public boolean selectStyle(UUID player, String style) {
        Optional<WandSession> s = opened(player);
        if (s.isEmpty() || !manager.context().ports().blueprints().styles().contains(style)) {
            return false;
        }
        WandSession next = s.get().withStyle(style);
        update(
                player,
                style.equals(s.get().style())
                        ? next
                        : next.withBuilding("").withRotation(0).withNav(WandNav.start()));
        return true;
    }

    /** ST switchPackClicked: the pack window, shuffled anew; false when the build tool was never opened. */
    public boolean switchPack(UUID player) {
        Optional<WandSession> s = opened(player);
        s.ifPresent(session -> packs.open(player, !session.style().isEmpty()));
        return s.isPresent();
    }

    /** ST WindowSwitchPack cancel: back to the build tool, or closed when no pack was ever chosen. */
    public boolean cancelPacks(UUID player) {
        Optional<WandSession> s = opened(player);
        if (s.isEmpty() || s.get().style().isEmpty()) {
            manager.windows().ui().close(player);
        } else {
            show(player, s.get());
        }
        return true;
    }

    /**
     * ST onButtonClicked on a category icon or a subfolder: opens {@code folder} ({@link WandNav#opened}); false when
     * it leads to no hut of the style. The chosen hut and its ghost stay.
     */
    public boolean openCategory(UUID player, String folder) {
        Optional<WandSession> s = opened(player);
        WandTree tree = s.map(x -> views.tree(x.style())).orElse(null);
        if (tree == null || !tree.contains(folder)) {
            return false;
        }
        boolean root = !folder.contains("/");
        update(
                player,
                s.get()
                        .withNav(s.get()
                                .nav()
                                .opened(folder, root, !tree.children(folder).isEmpty())));
        return true;
    }

    /** ST's back button ({@link WandNav#back}); false when the window shows none. */
    public boolean back(UUID player) {
        Optional<WandSession> s = opened(player).filter(x -> x.nav().canGoBack());
        s.ifPresent(x -> update(player, x.withNav(x.nav().back())));
        return s.isPresent();
    }

    /**
     * ST handleBlueprintCategory: chooses one of the style's huts at level 1 (setBlueprint(leveled.get(0))), locked
     * or not ({@link WandNav#chose}); false if the style has no plan for it.
     */
    public boolean selectBuilding(UUID player, String buildingTypeId) {
        Optional<WandSession> s =
                opened(player).filter(x -> views.tree(x.style()).hasHut(buildingTypeId));
        s.ifPresent(x -> {
            WandSession chosen = x.withBuilding(buildingTypeId).withLevel(1);
            update(player, chosen.withNav(x.nav().chose(views.maxLevel(chosen) > 1)));
        });
        return s.isPresent();
    }

    /** Chooses the previewed level, 1 to the hut's last with a plan; false before a hut is chosen or out of range. */
    public boolean selectLevel(UUID player, int level) {
        Optional<WandSession> s = manipulable(player);
        if (s.isEmpty() || level < 1 || level > views.maxLevel(s.get())) {
            return false;
        }
        update(player, s.get().withLevel(level).withNav(s.get().nav().leveled()));
        return true;
    }

    /**
     * ST confirmClicked in creative: the placement list (Constructed, Assign to Builder), the other lists hidden;
     * false outside creative or before a hut is chosen.
     */
    public boolean openPlacement(UUID player) {
        Optional<WandSession> s =
                manipulable(player).filter(x -> manager.context().players().isCreative(player));
        s.ifPresent(x -> update(player, x.withNav(x.nav().placement())));
        return s.isPresent();
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
            manager.windows().ui().close(player);
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
        manager.windows().ui().close(player);
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
        manager.windows().ui().showWand(player, views.of(player, s, false));
    }

    /** The session of a player who opened the window (it has an anchor). */
    private Optional<WandSession> opened(UUID player) {
        return Optional.of(sessions.get(player)).filter(s -> s.anchor().isPresent());
    }

    /** An opened session with a hut chosen: the manipulation buttons show. */
    private Optional<WandSession> manipulable(UUID player) {
        return opened(player).filter(WandSession::hasBuilding);
    }
}
