package dev.hycolony.plugin.ui.logistics;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import dev.hycolony.core.colony.ColonyManager;
import dev.hycolony.core.colony.ui.BuildingView;
import dev.hycolony.plugin.ui.ColonyPage;
import java.util.UUID;

/**
 * The Main tab's pickup row of a worker hut (MC AbstractWindowWorkerModuleBuilding): "Pickup priority: 5/10" or
 * "never", its - and + buttons, and "Force pickup". Hidden on a hut without a pickup priority.
 */
public final class PickupPanel {
    private static final String[][] BUTTONS = {
        {"#PickupDownButton", "pickupDown"}, {"#PickupUpButton", "pickupUp"}, {"#ForcePickupButton", "forcePickup"}
    };

    private final ColonyManager manager;
    private final UUID player;
    private final BuildingView view;

    public PickupPanel(ColonyManager manager, UUID player, BuildingView view) {
        this.manager = manager;
        this.player = player;
        this.view = view;
    }

    /** Fills the row, its buttons disabled without MANAGE_HUTS. */
    public void render(UICommandBuilder ui, UIEventBuilder events) {
        if (view.pickupPriority().isEmpty()) {
            ui.set("#PickupSection.Visible", false);
            return;
        }
        int priority = view.pickupPriority().getAsInt();
        ui.set(
                "#PickupPriority.Text",
                priority == 0
                        ? Message.translation("hycolony.ui.pickup.never")
                        : Message.translation("hycolony.ui.pickup.priority").param("p0", String.valueOf(priority)));
        for (String[] b : BUTTONS) {
            if (view.canManage()) {
                ColonyPage.bind(events, b[0], b[1]);
            } else {
                ui.set(b[0] + ".Disabled", true);
            }
        }
    }

    /** The core checks MANAGE_HUTS and shows the window again; returns whether {@code act} was one of these buttons. */
    public boolean handle(ColonyPage.Act act) {
        switch (act.action()) {
            case "pickupDown" -> manager.logistics().alterPickupPriority(player, view.pos(), false);
            case "pickupUp" -> manager.logistics().alterPickupPriority(player, view.pos(), true);
            case "forcePickup" -> manager.logistics().forcePickup(player, view.pos());
            default -> {
                return false;
            }
        }
        return true;
    }
}
