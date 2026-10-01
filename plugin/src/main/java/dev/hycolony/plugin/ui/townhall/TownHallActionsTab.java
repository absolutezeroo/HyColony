package dev.hycolony.plugin.ui.townhall;

import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.app.ui.TownHallView;
import dev.hycolony.plugin.ui.ColonyPage;
import java.util.UUID;

/**
 * The town hall's Actions tab (MC WindowMainPage, layoutactions.xml): the colony name with rename, and the town hall's
 * hut window (MC "build" button).
 *
 * <p>Deviation from MC: the owner and the colony day are shown, and the Requests button stands in for the clipboard
 * item, which HyColony does not have yet. No map, mercenaries, banner, colours nor styles (no such systems).
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
        ui.set(
                root + " #Owner.Text",
                Message.translation("hycolony.ui.townhall.owner").param("p0", view.ownerName()));
        ui.set(
                root + " #Day.Text",
                Message.translation("hycolony.ui.townhall.day").param("p0", String.valueOf(view.day())));
        ui.set(root + " #RenameInput.Value", view.colonyName());
        ColonyPage.bind(events, root + " #BuildingButton", "building");
        ColonyPage.bind(events, root + " #RequestsButton", "requests");
        if (view.canRename()) {
            // The rename field shows the name, as MC's name label beside its edit button.
            ui.set(root + " #ColonyName.Visible", false);
            events.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    root + " #RenameButton",
                    EventData.of("Action", "rename").append("@Name", root + " #RenameInput.Value"),
                    false);
        } else {
            ui.set(root + " #RenameButton.Visible", false);
            ui.set(root + " #RenameInput.Visible", false);
        }
    }

    /** Navigation opens another window; rename goes to the core, which re-shows the town hall on success. */
    @Override
    public void handle(ColonyPage.Act act) {
        switch (act.action()) {
            case "building" -> manager.windows().openTownHallBuilding(player, view.colonyId());
            case "requests" -> manager.windows().openRequests(player, view.colonyId());
            case "rename" -> manager.administration().rename(player, view.colonyId(), act.name());
            default -> {}
        }
    }
}
