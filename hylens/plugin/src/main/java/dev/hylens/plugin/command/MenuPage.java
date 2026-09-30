package dev.hylens.plugin.command;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hyblockui.api.PageEvents;
import dev.hycolony.api.ApiText;
import dev.hycolony.api.CitizenRef;
import dev.hycolony.api.ColonyRef;
import dev.hycolony.api.ColonyWorld;
import dev.hylens.core.draw.Layers;
import dev.hylens.core.menu.MenuView;
import dev.hylens.core.menu.MenuViews;
import dev.hylens.core.menu.Menus;
import dev.hylens.core.watch.Watches;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nonnull;
import org.jspecify.annotations.Nullable;

/**
 * The HyLens menu (spec 2026-09-30, § 6.4): choose a colony and a citizen, watch it, act on it, turn layers on or off.
 * Each click redraws the page from what HyColony tells now; actions are {@link MenuActions}'.
 */
final class MenuPage extends InteractiveCustomUIPage<MenuPage.Data> {
    /** One click: its action, and the row or layer it was on. */
    static final class Data {
        static final BuilderCodec<Data> CODEC = BuilderCodec.builder(Data.class, Data::new)
                .append(new KeyedCodec<>("Action", Codec.STRING), (d, v) -> d.action = v, d -> d.action)
                .add()
                .append(new KeyedCodec<>("Index", Codec.STRING), (d, v) -> d.index = v, d -> d.index)
                .add()
                .build();

        @Nullable
        String action;

        @Nullable
        String index;
    }

    private final Menus menus;
    private final Watches watches;
    private final CitizenWatch watch;
    private Optional<ApiText> result = Optional.empty();

    MenuPage(PlayerRef player, Menus menus, Watches watches, CitizenWatch watch) {
        super(player, CustomPageLifetime.CanDismiss, Data.CODEC);
        this.menus = menus;
        this.watches = watches;
        this.watch = watch;
    }

    @Override
    public void build(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull UICommandBuilder ui,
            @Nonnull UIEventBuilder events,
            @Nonnull Store<EntityStore> store) {
        ui.append(MenuRender.PAGE);
        view(store)
                .ifPresentOrElse(
                        v -> MenuRender.render(ui, events, v, result),
                        () -> MenuRender.only(ui, ApiText.of("hylens.notRunning")));
    }

    /** A failure is logged, never thrown into Hytale's PageManager; the page always gets an update back. */
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

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull Data data) {
        Optional<MenuView> v = view(store);
        if (v.isEmpty() || data.action == null) {
            return;
        }
        UUID operator = playerRef.getUuid();
        String index = data.index == null ? "" : data.index;
        switch (data.action) {
            case "colony" -> {
                colonyRow(v.get(), index).ifPresent(c -> menus.update(operator, s -> s.withColony(c)));
                result = Optional.empty();
            }
            case "citizen" -> {
                citizenRow(v.get(), index).ifPresent(c -> menus.update(operator, s -> s.withCitizen(c)));
                result = Optional.empty();
            }
            case "layer" -> layer(index).ifPresent(l -> menus.update(operator, s -> s.toggle(l)));
            case "watch" -> {
                if (startWatch(v.get(), ref, store)) {
                    return;
                }
            }
            default -> act(data.action, v.get(), ref, store);
        }
        rebuild();
    }

    /** Runs the chosen citizen's action {@code action}; its result shows under the buttons. */
    private void act(String action, MenuView v, Ref<EntityStore> ref, Store<EntityStore> store) {
        Optional<CitizenRef> citizen = v.citizen();
        Optional<ColonyWorld> world = colonies(store);
        if (citizen.isEmpty() || world.isEmpty()) {
            result = Optional.of(ApiText.of("hylens.action.noneChosen"));
            return;
        }
        MenuActions.run(action, world.get().debug(), citizen.get(), playerRef.getUuid(), MenuActions.feet(ref, store))
                .ifPresent(r -> result = Optional.of(r));
    }

    private Optional<MenuView> view(Store<EntityStore> store) {
        UUID operator = playerRef.getUuid();
        return colonies(store).map(w -> MenuViews.of(w, menus.state(operator), watches.watched(operator)));
    }

    private static Optional<ColonyWorld> colonies(Store<EntityStore> store) {
        return WatchCommand.worldOf(store.getExternalData().getWorld());
    }

    /**
     * Watches the chosen citizen, then closes the page once the watch started; else the page stays open, the reason in
     * the chat, or asks to choose a citizen first. True once closed.
     */
    private boolean startWatch(MenuView v, Ref<EntityStore> ref, Store<EntityStore> store) {
        Optional<MenuView.CitizenRow> chosen =
                v.citizens().stream().filter(MenuView.CitizenRow::chosen).findFirst();
        if (chosen.isEmpty()) {
            result = Optional.of(ApiText.of("hylens.action.noneChosen"));
            return false;
        }
        watch.start(playerRef, store, ref, chosen.get().ref(), chosen.get().name());
        if (watches.watched(playerRef.getUuid()).equals(Optional.of(chosen.get().ref()))) {
            close();
            return true;
        }
        return false;
    }

    /** The colony whose id is {@code index}, if the page still lists it. */
    private static Optional<ColonyRef> colonyRow(MenuView v, String index) {
        return id(index)
                .flatMap(id -> v.colonies().stream()
                        .map(MenuView.ColonyRow::ref)
                        .filter(c -> c.colonyId() == id)
                        .findFirst());
    }

    /** The citizen of the chosen colony whose id is {@code index}, if the page still lists it. */
    private static Optional<CitizenRef> citizenRow(MenuView v, String index) {
        return id(index)
                .flatMap(id -> v.citizens().stream()
                        .map(MenuView.CitizenRow::ref)
                        .filter(c -> c.citizenId() == id)
                        .findFirst());
    }

    private static Optional<Integer> id(String index) {
        try {
            return Optional.of(Integer.parseInt(index));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private static Optional<Layers.Layer> layer(String name) {
        try {
            return Optional.of(Layers.Layer.valueOf(name));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
