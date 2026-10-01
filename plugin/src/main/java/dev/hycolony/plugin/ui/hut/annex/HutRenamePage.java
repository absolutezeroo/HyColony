package dev.hycolony.plugin.ui.hut.annex;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.plugin.ui.ColonyPage;
import javax.annotation.Nonnull;

/**
 * MC WindowHutNameEntry: a field with the hut's name, Done (the core renames, cutting at 15 characters, then shows the
 * hut again) and Cancel (back to the hut).
 */
public final class HutRenamePage extends ColonyPage {
    private final BlockPos hut;
    private final String name;

    public HutRenamePage(PlayerRef playerRef, ColonyManager manager, BlockPos hut, String name) {
        super(playerRef, manager);
        this.hut = hut;
        this.name = name;
    }

    @Override
    public void build(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull UICommandBuilder ui,
            @Nonnull UIEventBuilder events,
            @Nonnull Store<EntityStore> store) {
        ui.append("Pages/HyColony/HutRename.ui");
        // Deviation from MC: the exact name; MC fills the field with the name lowercased.
        ui.set("#Name.Value", name);
        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#DoneButton",
                EventData.of("Action", "done").append("@Name", "#Name.Value"),
                false);
        bind(events, "#CancelButton", "cancel");
    }

    /** Done renames (the core then shows the hut again); a refusal or Cancel goes back to the hut. */
    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull Act act) {
        switch (act.action()) {
            case "done" -> {
                if (!manager.hutWindows().rename(player, hut, act.name())) {
                    back();
                }
            }
            case "cancel" -> back();
            default -> {}
        }
    }

    /** Shows the hut again; closes this window when the hut is gone or no longer visible to the player. */
    private void back() {
        if (!manager.windows().openBuilding(player, hut)) {
            close();
        }
    }
}
