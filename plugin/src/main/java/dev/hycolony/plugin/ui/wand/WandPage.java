package dev.hycolony.plugin.ui.wand;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hycolony.core.building.BuildingType;
import dev.hycolony.core.colony.ColonyManager;
import dev.hycolony.core.colony.ui.WandView;
import dev.hycolony.core.construction.wand.WandActions;
import dev.hycolony.plugin.IdMap;
import dev.hycolony.plugin.ui.ColonyPage;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import javax.annotation.Nonnull;

/**
 * The build tool window (ST WindowExtendedBuildTool with AbstractBlueprintManipulationWindow's buttons): style, then
 * hut, then level, then the move/rotate/confirm/cancel buttons once a hut is chosen. Each button calls one
 * {@link WandActions} method, which re-shows the window. Closing it (Escape) does nothing: the ghost stays, as in ST.
 *
 * <p>Deviation from MC: no keyboard shortcuts (arrows, M, Enter), Hytale does not send keys to the server.
 */
public final class WandPage extends ColonyPage {
    /** The move buttons, their selector and direction; the event index is the direction's ordinal. */
    private static final List<Move> MOVES = List.of(
            new Move("#ForwardButton", WandActions.Dir.FORWARD),
            new Move("#BackButton", WandActions.Dir.BACK),
            new Move("#LeftButton", WandActions.Dir.LEFT),
            new Move("#RightButton", WandActions.Dir.RIGHT),
            new Move("#UpButton", WandActions.Dir.UP),
            new Move("#DownButton", WandActions.Dir.DOWN));

    private record Move(String selector, WandActions.Dir dir) {}

    /** A row of choice buttons: its list group and the action its buttons send. */
    private record Choices(String list, String action) {}

    private static final Choices STYLES = new Choices("#Styles", "style");
    private static final Choices LEVELS = new Choices("#Levels", "level");

    private final WandView view;
    private final WandActions wand;
    private final IdMap ids;

    public WandPage(PlayerRef playerRef, WandView view, ColonyManager manager, WandActions wand, IdMap ids) {
        super(playerRef, manager);
        this.view = view;
        this.wand = wand;
        this.ids = ids;
    }

    @Override
    public void build(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull UICommandBuilder ui,
            @Nonnull UIEventBuilder events,
            @Nonnull Store<EntityStore> store) {
        ui.append("Pages/HyColony/WandPage.ui");
        choices(ui, events, STYLES, view.styles(), view.styles().indexOf(view.style()));
        boolean styled = !view.style().isEmpty();
        ui.set("#ChooseStyle.Visible", !styled);
        ui.set("#HutSection.Visible", styled);
        ui.set("#NoHut.Visible", styled && view.buildingTypeIds().isEmpty());
        for (int i = 0; i < view.buildingTypeIds().size(); i++) {
            hutRow(ui, events, i, view.buildingTypeIds().get(i));
        }
        ui.set("#LevelSection.Visible", view.maxLevel() > 0);
        List<String> levels = IntStream.rangeClosed(1, view.maxLevel())
                .mapToObj(String::valueOf)
                .toList();
        choices(ui, events, LEVELS, levels, view.level() - 1);
        ui.set("#Manipulator.Visible", view.manipulate());
        if (view.manipulate()) {
            MOVES.forEach(m -> bind(events, m.selector(), "move", m.dir().ordinal()));
            bind(events, "#RotateRightButton", "rotateRight");
            bind(events, "#RotateLeftButton", "rotateLeft");
            bind(events, "#ConfirmButton", "confirm");
            bind(events, "#CancelButton", "cancel");
        }
    }

    /** A single-choice row of buttons (vanilla tab pattern), one per label; the chosen one is disabled. */
    private static void choices(
            UICommandBuilder ui, UIEventBuilder events, Choices row, List<String> labels, int chosen) {
        for (int i = 0; i < labels.size(); i++) {
            String button = row.list() + "[" + i + "]";
            ui.append(row.list(), "Pages/HyColony/TabButton.ui");
            // A raw Message on .Text disconnects the client ("couldn't set value"); a plain string is accepted.
            ui.set(button + ".Text", labels.get(i));
            ui.set(button + ".Disabled", i == chosen);
            bind(events, button, row.action(), i);
        }
    }

    /** Appends hut row {@code i}: its hut block icon, name and a choose button, disabled on the chosen hut. */
    private void hutRow(UICommandBuilder ui, UIEventBuilder events, int i, String typeId) {
        String row = "#Huts[" + i + "]";
        ui.append("#Huts", "Pages/HyColony/WandHutRow.ui");
        manager.context()
                .buildingTypes()
                .byId(typeId)
                .map(BuildingType::hutBlockKey)
                .ifPresent(key -> ui.set(row + " #Icon.ItemId", ids.itemId(key)));
        ui.set(row + " #Name.Text", buildingName(typeId));
        ui.set(row + " #ChooseButton.Disabled", typeId.equals(view.buildingTypeId()));
        bind(events, row + " #ChooseButton", "hut", i);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull Act act) {
        int i = act.index();
        switch (act.action()) {
            case "style" -> at(view.styles(), i).ifPresent(s -> wand.selectStyle(player, s));
            case "hut" -> at(view.buildingTypeIds(), i).ifPresent(t -> wand.selectBuilding(player, t));
            case "level" -> wand.selectLevel(player, i + 1);
            case "move" -> at(List.of(WandActions.Dir.values()), i).ifPresent(d -> wand.move(player, d));
            case "rotateRight" -> wand.rotate(player, true);
            case "rotateLeft" -> wand.rotate(player, false);
            case "confirm" -> wand.confirm(player, playerRef.getUsername());
            case "cancel" -> wand.cancel(player);
            default -> {
                // A forged or stale action: the window stays as it is.
            }
        }
    }

    private static <T> Optional<T> at(List<T> list, int i) {
        return i >= 0 && i < list.size() ? Optional.of(list.get(i)) : Optional.empty();
    }
}
