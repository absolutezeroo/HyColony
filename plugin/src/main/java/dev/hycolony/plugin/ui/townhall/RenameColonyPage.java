package dev.hycolony.plugin.ui.townhall;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.app.ui.TownHallView;
import dev.hycolony.plugin.ui.ColonyPage;
import javax.annotation.Nonnull;

/**
 * MC WindowTownHallNameEntry: a field with the colony's name, Done (renames, then the town hall shows again) and
 * Cancel (back to the town hall).
 */
final class RenameColonyPage extends ColonyPage {
    private final TownHallView view;

    RenameColonyPage(PlayerRef playerRef, TownHallView view, ColonyManager manager) {
        super(playerRef, manager);
        this.view = view;
    }

    @Override
    public void build(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull UICommandBuilder ui,
            @Nonnull UIEventBuilder events,
            @Nonnull Store<EntityStore> store) {
        ui.append("Pages/HyColony/RenameColony.ui");
        // Deviation from MC: the exact name; MC fills the field through a translation lookup that lowercases it.
        ui.set("#Name.Value", view.colonyName());
        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#DoneButton",
                EventData.of("Action", "done").append("@Name", "#Name.Value"),
                false);
        bind(events, "#CancelButton", "cancel");
    }

    /** Done renames (the core then shows the town hall again); a refused name or Cancel goes back to the town hall. */
    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull Act act) {
        switch (act.action()) {
            case "done" -> {
                if (!manager.administration().rename(player, view.colonyId(), act.name())) {
                    manager.windows().openTownHall(player, view.home().townHallPos());
                }
            }
            case "cancel" -> manager.windows().openTownHall(player, view.home().townHallPos());
            default -> {}
        }
    }
}
