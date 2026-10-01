package dev.hycolony.plugin.ui.townhall;

import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.ui.DropdownEntryInfo;
import com.hypixel.hytale.server.core.ui.LocalizableString;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.app.ui.TownHallView;
import dev.hycolony.plugin.ui.ColonyPage;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * The town hall's Home tab (MC WindowMainPage, layoutactions.xml): the colony name and its rename pencil, the build
 * button (build options, or cancel the running order) and the colony pack; citizen style and name pack are shown
 * disabled at "default", as MC shows them without Patreon. Requests are the clipboard item's, as in MC.
 */
final class TownHallActionsTab implements TownHallTab {
    private final ColonyManager manager;
    private final UUID player;
    private final TownHallView view;

    TownHallActionsTab(ColonyManager manager, UUID player, TownHallView view) {
        this.manager = manager;
        this.player = player;
        this.view = view;
    }

    @Override
    public void render(UICommandBuilder ui, UIEventBuilder events, String root) {
        ui.set(root + " #ColonyName.Text", view.colonyName());
        // As MC, every button shows for every viewer: the core refuses and says so without the right.
        ColonyPage.bind(events, root + " #RenameButton", TownHallPage.RENAME);
        buildButton(ui, events, root);
        colonyPack(ui, events, root);
    }

    /**
     * MC AbstractBuildingMainWindow.updateButtonBuild: "Build options" opens the build options window; with an order
     * it reads "Cancel Build / Upgrade / Repair / Removal" and cancels it.
     */
    private void buildButton(UICommandBuilder ui, UIEventBuilder events, String root) {
        String button = root + " #BuildButton";
        if (view.home().order().isEmpty()) {
            ui.set(button + ".Text", Message.translation("hycolony.ui.building.buildOptions"));
            ColonyPage.bind(events, button, "build");
            return;
        }
        String type = view.home().order().get().name().toLowerCase(Locale.ROOT);
        ui.set(button + ".Text", Message.translation("hycolony.ui.building.cancel." + type));
        ColonyPage.bind(events, button, "cancel");
    }

    /**
     * MC colonyStylePicker: the blueprint styles, the colony's chosen.
     *
     * <p>Deviation from MC: a dropdown, as MC opens Structurize's pack window, which HyColony does not port.
     */
    private void colonyPack(UICommandBuilder ui, UIEventBuilder events, String root) {
        String dropdown = root + " #ColonyPack";
        List<DropdownEntryInfo> entries = new ArrayList<>();
        view.home().styles().forEach(s -> entries.add(new DropdownEntryInfo(LocalizableString.fromString(s), s)));
        ui.set(dropdown + ".Entries", entries);
        ui.set(dropdown + ".Value", view.home().style());
        events.addEventBinding(
                CustomUIEventBindingType.ValueChanged,
                dropdown,
                EventData.of("Action", "colonyPack").append("@Name", dropdown + ".Value"),
                false);
    }

    /** Each button goes to the core, which checks the right and shows the next window; the pencil is the page's. */
    @Override
    public Outcome handle(ColonyPage.Act act) {
        switch (act.action()) {
            case "build" ->
                manager.windows().openBuildOptions(player, view.home().townHallPos());
            case "cancel" -> manager.workOrders().cancel(player, view.home().townHallPos());
            case "colonyPack" -> manager.administration().setStyle(player, view.colonyId(), act.name());
            default -> {}
        }
        return Outcome.NONE;
    }
}
