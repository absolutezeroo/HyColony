package dev.hycolony.plugin.ui.wand;

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
import dev.hyblockui.api.PageEvents;
import dev.hycolony.core.app.ui.SuggestBuildToolView;
import dev.hycolony.core.app.wand.HutHandPlacement;
import javax.annotation.Nonnull;
import org.jspecify.annotations.Nullable;

/**
 * MC WindowSuggestBuildTool: shown when a hut block is placed by hand. "Use build tool" asks the core to swap the hut
 * with the build tool and open it there (which replaces this page), or to say the tool is missing; the cross closes.
 */
public final class SuggestBuildToolPage extends InteractiveCustomUIPage<SuggestBuildToolPage.Data> {
    public static final class Data {
        static final BuilderCodec<Data> CODEC = BuilderCodec.builder(Data.class, Data::new)
                .append(new KeyedCodec<>("Action", Codec.STRING), (d, v) -> d.action = v, d -> d.action)
                .add()
                .build();

        @Nullable
        String action;
    }

    private final SuggestBuildToolView view;
    private final HutHandPlacement hand;

    public SuggestBuildToolPage(PlayerRef player, SuggestBuildToolView view, HutHandPlacement hand) {
        super(player, CustomPageLifetime.CanDismiss, Data.CODEC);
        this.view = view;
        this.hand = hand;
    }

    @Override
    public void build(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull UICommandBuilder ui,
            @Nonnull UIEventBuilder events,
            @Nonnull Store<EntityStore> store) {
        ui.append("Pages/HyColony/SuggestBuildTool.ui");
        events.addEventBinding(
                CustomUIEventBindingType.Activating, "#BuildTool", new EventData().append("Action", "buildTool"));
        events.addEventBinding(
                CustomUIEventBindingType.Activating, "#Cancel", new EventData().append("Action", "cancel"));
    }

    /** As FoundColonyPage: a failure is logged, and the update always sent, since the page locks the client. */
    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, String rawData) {
        PageEvents.guard(getClass(), () -> {
            try {
                super.handleDataEvent(ref, store, rawData);
            } finally {
                sendUpdate(new UICommandBuilder(), false);
            }
        });
    }

    /** MC buildToolClicked: the build tool opens in place of this page; without one, MC closes it. */
    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull Data data) {
        if ("buildTool".equals(data.action) && hand.useBuildTool(playerRef.getUuid(), view.pos(), view.hut())) {
            return;
        }
        close();
    }
}
