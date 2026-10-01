package dev.hycolony.plugin.ui.wand;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.ui.Anchor;
import com.hypixel.hytale.server.core.ui.Value;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.app.ui.WandView;
import dev.hycolony.core.app.wand.WandActions;
import dev.hycolony.core.kernel.port.Msg;
import dev.hycolony.plugin.adapter.HytaleNotifier;
import dev.hycolony.plugin.ui.ColonyPage;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nonnull;

/**
 * The build tool window as Structurize's (windowbuildtool.xml with layoutmanipulation.xml, WindowExtendedBuildTool):
 * Switch Pack and the tree; the pack's category icons; the open folder's back button and subfolders or blueprints,
 * three per row; the chosen hut's levels; the manipulator, cancel and confirm. Each button calls one
 * {@link WandActions} method, which shows the window again. In creative, confirm opens the placement list first (ST
 * updatePlacementOptions). Closing it (Escape) does nothing: the ghost stays, as in ST.
 *
 * <p>Deviation from Structurize: no keyboard shortcuts (Hytale sends no keys to the server); no settings button (its
 * settings are the client renderer's); the mirror button stays disabled (no mirrored huts); the category icons have
 * no hover tint; the tip goes at the next action, where ST hides it after 10 seconds.
 */
public final class WandPage extends ColonyPage {
    private static final String DIR = "Pages/HyColony/Structurize/";
    private static final String ICONS = "UI/Custom/Pages/HyColony/Structurize/category_";
    /** The pack folders whose MineColonies icon HyColony ships; any other one gets Structurize's default icon. */
    private static final Set<String> ICON_FOLDERS = Set.of(
            "agriculture",
            "craftsmanship",
            "decorations",
            "education",
            "fundamentals",
            "infrastructure",
            "military",
            "mystic",
            "walls");

    /** The move buttons and their direction; the event index is the direction's ordinal. */
    private static final List<Move> MOVES = List.of(
            new Move("#Forward", WandActions.Dir.FORWARD),
            new Move("#Backward", WandActions.Dir.BACK),
            new Move("#Left", WandActions.Dir.LEFT),
            new Move("#Right", WandActions.Dir.RIGHT),
            new Move("#Up", WandActions.Dir.UP),
            new Move("#Down", WandActions.Dir.DOWN));

    private record Move(String selector, WandActions.Dir dir) {}

    /**
     * ST updateFolders/updateBlueprints, doubled: rows 40 px high, the list ending at y 400 px from x 200 px, 540 px
     * wide, 1 to 3 rows tall (more scroll), three buttons a row.
     */
    private static final int ROW_HEIGHT_PX = 40;

    private static final int GRID_BOTTOM_PX = 400;
    private static final int GRID_LEFT_PX = 200;
    private static final int GRID_WIDTH_PX = 540;
    private static final int GRID_MAX_ROWS = 3;
    private static final int PER_ROW = 3;
    private static final String CORE_PREFIX = "hycolony:";

    private final WandView view;
    private final WandActions wand;

    public WandPage(PlayerRef playerRef, WandView view, ColonyManager manager, WandActions wand) {
        super(playerRef, manager);
        this.view = view;
        this.wand = wand;
    }

    @Override
    public void build(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull UICommandBuilder ui,
            @Nonnull UIEventBuilder events,
            @Nonnull Store<EntityStore> store) {
        ui.append(DIR + "BuildTool.ui");
        bind(events, "#Switch", "switch");
        ui.set("#Tree.TextSpans", WandTexts.tree(view));
        categories(ui, events);
        if (view.panel().placing()) {
            placement(ui, events);
        }
        grid(ui, events);
        if (view.panel().levels()) {
            levels(ui, events);
        }
        ui.set("#Tip.Visible", view.tip());
        ui.set("#Manipulator.Visible", view.manipulate());
        ui.set("#Confirm.Visible", view.canConfirm());
        bind(events, "#Cancel", "cancel");
        if (view.manipulate()) {
            MOVES.forEach(m -> bind(events, m.selector(), "move", m.dir().ordinal()));
            bind(events, "#RotateRight", "rotate", 1); // index 1: clockwise
            bind(events, "#RotateLeft", "rotate", 0);
        }
        if (view.canConfirm()) {
            bind(events, "#Confirm", "confirm");
        }
    }

    /** ST onUpdate: one icon per top folder; the one last clicked disabled, with its disabled icon. */
    private void categories(UICommandBuilder ui, UIEventBuilder events) {
        String open = view.panel().disabledCategory();
        for (int i = 0; i < view.categories().size(); i++) {
            String folder = view.categories().get(i);
            String sel = "#Categories[" + i + "]";
            ui.append("#Categories", DIR + "CategoryIcon.ui");
            boolean isOpen = folder.equals(open);
            String icon = ICON_FOLDERS.contains(folder)
                    ? ICONS + folder + (isOpen ? "_disabled" : "") + ".png"
                    : ICONS + "default" + (isOpen ? "_dim" : "") + ".png";
            ui.set(sel + " #Icon.AssetPath", icon);
            ui.set(sel + " #Button.TooltipText", WandTexts.folder(playerRef, folder));
            ui.set(sel + " #Button.Disabled", isOpen);
            bind(events, sel + " #Button", "category", i);
        }
    }

    /**
     * ST updateFolders/updateBlueprints: back, then the subfolders or the blueprints, three per row; back alone once a
     * hut with levels is chosen; nothing when the window shows no back button.
     */
    private void grid(UICommandBuilder ui, UIEventBuilder events) {
        if (!view.panel().back()) {
            return;
        }
        boolean folders = !view.folders().isEmpty();
        int cells = 1 + (folders ? view.folders().size() : view.huts().size());
        int rows = (cells + PER_ROW - 1) / PER_ROW;
        int shown = Math.min(rows, GRID_MAX_ROWS);
        Anchor anchor = new Anchor();
        anchor.setLeft(Value.of(GRID_LEFT_PX));
        anchor.setTop(Value.of(GRID_BOTTOM_PX - shown * ROW_HEIGHT_PX));
        anchor.setWidth(Value.of(GRID_WIDTH_PX));
        anchor.setHeight(Value.of(shown * ROW_HEIGHT_PX));
        ui.setObject("#Grid.Anchor", anchor);
        for (int r = 0; r < rows; r++) {
            ui.append("#Grid", DIR + "GridRow.ui");
        }
        for (int c = 0; c < cells; c++) {
            String slot = "#Grid[" + c / PER_ROW + "] #S" + c % PER_ROW + " ";
            if (c == 0) {
                ui.set(slot + "#Back.Visible", true);
                bind(events, slot + "#Back", "back");
            } else if (folders) {
                ui.set(slot + "#Folder.Visible", true);
                ui.set(
                        slot + "#Folder.Text",
                        WandTexts.folder(playerRef, view.folders().get(c - 1)));
                bind(events, slot + "#Folder", "folder", c - 1);
            } else {
                hut(ui, events, slot, c - 1);
            }
        }
    }

    /**
     * ST handleBlueprint: the hut's name, its selected or locked texture, and as tooltip its name and description
     * (AbstractBlockHut.getDesc, HyColony huts only) then its requirements in red.
     */
    private void hut(UICommandBuilder ui, UIEventBuilder events, String slot, int i) {
        WandView.Hut hut = view.huts().get(i);
        String id = hut.buildingTypeId();
        String button = slot + (hut.selected() ? "#Selected" : hut.locked() ? "#Locked" : "#Hut");
        ui.set(button + ".Visible", true);
        ui.set(button + ".Text", buildingName(id));
        Message tip = buildingName(id);
        if (id.startsWith(CORE_PREFIX)) {
            tip = Message.join(
                    tip,
                    Message.raw("\n"),
                    Message.translation("hycolony.ui.building.type." + id.substring(CORE_PREFIX.length()) + ".desc"));
        }
        for (Msg m : hut.requirements()) {
            tip = Message.join(
                    tip, Message.raw("\n"), HytaleNotifier.toMessage(m).color("#ff5555"));
        }
        ui.set(button + ".TooltipTextSpans", tip);
        bind(events, button, "hut", i);
    }

    /** ST updateLevels: "Level: n" for each level of the chosen hut. */
    private void levels(UICommandBuilder ui, UIEventBuilder events) {
        for (int i = 0; i < view.maxLevel(); i++) {
            String row = "#Levels[" + i + "]";
            ui.append("#Levels", DIR + "LevelRow.ui");
            ui.set(
                    row + " #Button.Text",
                    Message.translation("hycolony.ui.wand.levelN").param("p0", String.valueOf(i + 1)));
            bind(events, row + " #Button", "level", i);
        }
    }

    /** ST updatePlacementOptions, creative only: Constructed (the paste), then MC's Assign to Builder. */
    private void placement(UICommandBuilder ui, UIEventBuilder events) {
        ui.set("#Placement.Visible", true);
        String[][] options = {{"hycolony.ui.wand.pretty", "paste"}, {"hycolony.ui.wand.assign", "place"}};
        for (int i = 0; i < options.length; i++) {
            String row = "#Placement[" + i + "]";
            ui.append("#Placement", DIR + "LevelRow.ui");
            ui.set(row + " #Button.Text", Message.translation(options[i][0]));
            bind(events, row + " #Button", options[i][1]);
        }
    }

    /** A navigation button, else a placement one; a forged or stale action leaves the window as it is. */
    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull Act act) {
        if (!navigate(act)) {
            place(act.action());
        }
    }

    /** Switch Pack, a folder, back, a hut, a level, a move or a turn; false for any other action. */
    private boolean navigate(Act act) {
        int i = act.index();
        switch (act.action()) {
            case "switch" -> wand.switchPack(player);
            case "category" -> at(view.categories(), i).ifPresent(f -> wand.openCategory(player, f));
            case "folder" -> at(view.folders(), i).ifPresent(f -> wand.openCategory(player, f));
            case "back" -> wand.back(player);
            case "hut" -> at(view.hutIds(), i).ifPresent(t -> wand.selectBuilding(player, t));
            case "level" -> wand.selectLevel(player, i + 1);
            case "move" -> at(List.of(WandActions.Dir.values()), i).ifPresent(d -> wand.move(player, d));
            case "rotate" -> wand.rotate(player, i == 1);
            default -> {
                return false;
            }
        }
        return true;
    }

    /** Confirm, a placement option or cancel. */
    private void place(String action) {
        switch (action) {
            case "confirm" -> confirm();
            case "place" -> wand.confirm(player, playerRef.getUsername());
            case "paste" -> wand.paste(player, playerRef.getUsername());
            case "cancel" -> wand.cancel(player);
            default -> {
                // A forged or stale action: the window stays as it is.
            }
        }
    }

    /**
     * ST confirmClicked: nothing before a blueprint is chosen; survival places at once (MC's one handler); creative
     * shows the placement list.
     */
    private void confirm() {
        if (!view.manipulate()) {
            return;
        }
        if (view.creative()) {
            wand.openPlacement(player);
        } else {
            wand.confirm(player, playerRef.getUsername());
        }
    }

    private static <T> Optional<T> at(List<T> list, int i) {
        return i >= 0 && i < list.size() ? Optional.of(list.get(i)) : Optional.empty();
    }
}
