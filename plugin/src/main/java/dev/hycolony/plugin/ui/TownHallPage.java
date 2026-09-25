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
import dev.hycolony.core.colony.ui.CitizenRow;
import dev.hycolony.core.colony.ui.TownHallView;
import java.util.List;
import java.util.function.Consumer;
import javax.annotation.Nonnull;

public final class TownHallPage extends InteractiveCustomUIPage<TownHallPage.Data> {
    public static final class Data {
        static final BuilderCodec<Data> CODEC = BuilderCodec.builder(Data.class, Data::new)
                .append(new KeyedCodec<>("@Name", Codec.STRING), (d, v) -> d.name = v, d -> d.name).add()
                .build();
        String name;
    }

    private final TownHallView view;
    private final Consumer<String> rename;

    public TownHallPage(PlayerRef player, TownHallView view, Consumer<String> rename) {
        super(player, CustomPageLifetime.CanDismiss, Data.CODEC);
        this.view = view;
        this.rename = rename;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder ui, @Nonnull UIEventBuilder events,
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
            ui.set(row + " #Status.Text", Message.translation("hycolony.status." + rows.get(i).status()));
        }
        if (view.canRename()) {
            events.addEventBinding(CustomUIEventBindingType.Activating, "#RenameButton",
                    new EventData().append("@Name", "#RenameInput.Value"));
        } else {
            ui.set("#RenameButton.Visible", false);
            ui.set("#RenameInput.Visible", false);
        }
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull Data data) {
        rename.accept(data.name); // core re-shows an updated TownHallView on success
        sendUpdate(new UICommandBuilder(), false);
    }
}
