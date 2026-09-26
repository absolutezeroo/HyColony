package dev.hycolony.plugin.ui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hycolony.core.colony.ColonyManager;
import dev.hycolony.core.colony.ui.CitizenRow;
import dev.hycolony.core.colony.ui.TownHallView;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nonnull;

public final class TownHallPage extends InteractiveCustomUIPage<TownHallPage.Data> {
    public static final class Data {
        static final BuilderCodec<Data> CODEC = BuilderCodec.builder(Data.class, Data::new)
                .append(new KeyedCodec<>("Action", Codec.STRING), (d, v) -> d.action = v, d -> d.action)
                .add()
                .append(new KeyedCodec<>("@Name", Codec.STRING), (d, v) -> d.name = v, d -> d.name)
                .add()
                .build();
        String action;
        String name;
    }

    private final TownHallView view;
    private final ColonyManager manager;
    private final UUID player;

    public TownHallPage(PlayerRef player, TownHallView view, ColonyManager manager) {
        super(player, CustomPageLifetime.CanDismiss, Data.CODEC);
        this.view = view;
        this.manager = manager;
        this.player = player.getUuid();
    }

    @Override
    public void build(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull UICommandBuilder ui,
            @Nonnull UIEventBuilder events,
            @Nonnull Store<EntityStore> store) {
        ui.append("Pages/HyColony/TownHall.ui");
        ui.set("#ColonyName.Text", view.colonyName());
        ui.set("#Owner.Text", Message.translation("hycolony.ui.townhall.owner").param("p0", view.ownerName()));
        ui.set("#Day.Text", Message.translation("hycolony.ui.townhall.day").param("p0", String.valueOf(view.day())));
        ui.set("#RenameInput.Value", view.colonyName());
        List<CitizenRow> rows = view.citizens();
        for (int i = 0; i < rows.size(); i++) {
            String row = "#CitizenList[" + i + "]";
            ui.append("#CitizenList", "Pages/HyColony/CitizenRow.ui");
            ui.set(row + " #Name.Text", rows.get(i).name());
            ui.set(
                    row + " #Status.Text",
                    Message.translation("hycolony.status." + rows.get(i).status()));
        }
        // Navigation opens another page: no interface lock, so nothing to unlock.
        for (String action : new String[] {"building", "workOrders", "requests"}) {
            String button = "#" + Character.toUpperCase(action.charAt(0)) + action.substring(1) + "Button";
            events.addEventBinding(CustomUIEventBindingType.Activating, button, EventData.of("Action", action), false);
        }
        if (view.canRename()) {
            events.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    "#RenameButton",
                    new EventData().append("@Name", "#RenameInput.Value"));
        } else {
            ui.set("#RenameButton.Visible", false);
            ui.set("#RenameInput.Visible", false);
        }
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull Data data) {
        if (data.action != null) {
            switch (data.action) {
                // The town hall stands at the colony's center.
                case "building" ->
                    manager.byId(view.colonyId())
                            .ifPresent(c -> manager.windows().openBuilding(player, c.center()));
                case "workOrders" -> manager.windows().openWorkOrders(player, view.colonyId());
                case "requests" -> manager.windows().openRequests(player, view.colonyId());
                default -> {}
            }
            return;
        }
        manager.administration()
                .rename(player, view.colonyId(), data.name); // core re-shows an updated TownHallView on success
        sendUpdate(new UICommandBuilder(), false);
    }
}
