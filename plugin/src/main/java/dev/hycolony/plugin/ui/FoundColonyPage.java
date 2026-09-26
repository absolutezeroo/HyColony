package dev.hycolony.plugin.ui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hycolony.core.colony.ui.FoundColonyView;
import javax.annotation.Nonnull;

public final class FoundColonyPage extends InteractiveCustomUIPage<FoundColonyPage.Data> {
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

    public interface Handler {
        /** True once the colony exists; false keeps the window answerable (e.g. invalid name). */
        boolean confirm(String name);

        /** {@code windowClosing}: called from onDismiss, while Hytale is already closing the page. */
        void cancel(boolean windowClosing);
    }

    private final FoundColonyView view;
    private final Handler handler;
    private boolean answered;

    public FoundColonyPage(PlayerRef player, FoundColonyView view, Handler handler) {
        super(player, CustomPageLifetime.CanDismiss, Data.CODEC);
        this.view = view;
        this.handler = handler;
    }

    @Override
    public void build(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull UICommandBuilder ui,
            @Nonnull UIEventBuilder events,
            @Nonnull Store<EntityStore> store) {
        ui.append("Pages/HyColony/FoundColony.ui");
        ui.set("#NameInput.Value", view.suggestedName());
        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#ConfirmButton",
                new EventData().append("Action", "confirm").append("@Name", "#NameInput.Value"));
        events.addEventBinding(
                CustomUIEventBindingType.Activating, "#CancelButton", new EventData().append("Action", "cancel"));
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull Data data) {
        if ("confirm".equals(data.action)) {
            answered = true; // set first: a successful confirm closes the page, which calls onDismiss
            answered = handler.confirm(data.name); // an invalid name keeps the foundation pending
        } else {
            answered = true;
            handler.cancel(false);
        }
        sendUpdate(new UICommandBuilder(), false); // required: otherwise the client stays in a loading state
    }

    @Override
    public void onDismiss(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        if (!answered) {
            answered = true; // set first: cancelling closes the page, which calls onDismiss again
            handler.cancel(true); // closing the window = cancel (spec § 4.2)
        }
    }
}
