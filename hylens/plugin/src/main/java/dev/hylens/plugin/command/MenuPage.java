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
import dev.hycolony.api.ApiText;
import dev.hycolony.api.ColonyWorld;
import dev.hylens.core.menu.MenuView;
import dev.hylens.core.menu.MenuViews;
import dev.hylens.core.menu.Menus;
import dev.hylens.core.watch.Watches;
import dev.hylens.plugin.HyColonyAccess;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nonnull;
import org.jspecify.annotations.Nullable;

/**
 * The HyLens menu (spec 2026-09-30, § 6.4): choose a colony and a citizen, watch it, act on it, turn layers on or off,
 * send it walking, pause, step and resume the colonies, check them now or every few seconds. Each click redraws the
 * page from what HyColony tells now; actions are {@link MenuActions}', the clock {@link MenuClock}'s, the checks
 * {@link MenuChecks}', "send here" {@link MenuSend}'s.
 */
final class MenuPage extends InteractiveCustomUIPage<MenuPage.Data> {
    /** One click: its action, the row or layer it was on, and the "send here" cell typed. */
    static final class Data {
        static final BuilderCodec<Data> CODEC = BuilderCodec.builder(Data.class, Data::new)
                .append(new KeyedCodec<>("Action", Codec.STRING), (d, v) -> d.action = v, d -> d.action)
                .add()
                .append(new KeyedCodec<>("Index", Codec.STRING), (d, v) -> d.index = v, d -> d.index)
                .add()
                .append(new KeyedCodec<>("@X", Codec.STRING), (d, v) -> d.x = v, d -> d.x)
                .add()
                .append(new KeyedCodec<>("@Y", Codec.STRING), (d, v) -> d.y = v, d -> d.y)
                .add()
                .append(new KeyedCodec<>("@Z", Codec.STRING), (d, v) -> d.z = v, d -> d.z)
                .add()
                .build();

        @Nullable
        String action;

        @Nullable
        String index;

        /** "Send here"'s cell, as typed. */
        @Nullable
        String x;

        @Nullable
        String y;

        @Nullable
        String z;
    }

    private final Menus menus;
    private final Watches watches;
    private final CitizenWatch watch;
    private final MenuClock clock;
    private final MenuChecks checks;
    private final MenuSend send;
    private final MenuEvents events = new MenuEvents();
    private Optional<ApiText> result = Optional.empty();

    MenuPage(PlayerRef player, LensParts parts, CitizenWatch watch, MenuSend send) {
        super(player, CustomPageLifetime.CanDismiss, Data.CODEC);
        this.menus = parts.menus();
        this.watches = parts.watches();
        this.watch = watch;
        this.clock = parts.clock();
        this.checks = new MenuChecks(parts.menus(), parts.alerts());
        this.send = send;
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
                        v -> MenuRender.render(ui, events, v, result, send.fields(MenuActions.feet(ref, store))),
                        () -> MenuRender.only(ui, ApiText.of("hylens.notRunning")));
    }

    /**
     * A failure is logged, never thrown into Hytale's PageManager; the page always gets an update back. A LinkageError
     * (HyLens stopped, its classes gone) is caught too ({@link MenuEvents}).
     */
    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, String rawData) {
        events.guard(getClass(), () -> {
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
        send.remember(data);
        if (closing(data.action, v.get(), ref, store)) {
            return;
        }
        if ("send".equals(data.action)) {
            result = Optional.of(send.toCell(playerRef, store.getExternalData().getWorld()));
        } else if (!"watch".equals(data.action)) {
            dispatch(data.action, Objects.requireNonNullElse(data.index, ""), v.get(), ref, store);
        }
        rebuild();
    }

    /**
     * Handles the clicks that may close the page: "watch" closes it once the watch started, "sendMap" arms the map
     * and closes the page so the operator can open the map. True once closed.
     */
    private boolean closing(String action, MenuView v, Ref<EntityStore> ref, Store<EntityStore> store) {
        if ("sendMap".equals(action)) {
            send.byMap(playerRef);
            close();
            return true;
        }
        return "watch".equals(action) && startWatch(v, ref, store);
    }

    /**
     * Records the operator's choice {@code action} (a colony, a citizen, a layer, a smaller or larger step) named by
     * {@code index}; choosing a colony or a citizen clears the last result.
     */
    private void choose(String action, String index, MenuView v, UUID operator) {
        switch (action) {
            case "colony" -> {
                MenuClicks.colony(v, index).ifPresent(c -> menus.update(operator, s -> s.withColony(c)));
                result = Optional.empty();
            }
            case "citizen" -> {
                MenuClicks.citizen(v, index).ifPresent(c -> menus.update(operator, s -> s.withCitizen(c)));
                result = Optional.empty();
            }
            case "layer" -> MenuClicks.layer(index).ifPresent(l -> menus.update(operator, s -> s.toggle(l)));
            case "stepLess" -> menus.update(operator, s -> s.withStep(s.step() - 1));
            default -> menus.update(operator, s -> s.withStep(s.step() + 1));
        }
    }

    /** Handles every click but "watch": a choice, the clock, the checks, or an action on the chosen citizen. */
    private void dispatch(String action, String index, MenuView v, Ref<EntityStore> ref, Store<EntityStore> store) {
        UUID operator = playerRef.getUuid();
        if (MenuClicks.CHOICES.contains(action)) {
            choose(action, index, v, operator);
        } else if (MenuClicks.CLOCK.contains(action)) {
            result = clock.run(
                    action,
                    store.getExternalData().getWorld(),
                    operator,
                    menus.state(operator).step());
        } else if (MenuClicks.CHECKS.contains(action)) {
            result = Optional.of(checks.run(action, playerRef, colonies(store)));
        } else {
            act(action, v, ref, store);
        }
    }

    /** Runs the chosen citizen's action {@code action}; its result shows under the buttons. */
    private void act(String action, MenuView v, Ref<EntityStore> ref, Store<EntityStore> store) {
        MenuActions.run(action, v.citizen(), colonies(store), playerRef.getUuid(), MenuActions.feet(ref, store))
                .ifPresent(r -> result = Optional.of(r));
    }

    private Optional<MenuView> view(Store<EntityStore> store) {
        UUID operator = playerRef.getUuid();
        boolean paused = MenuClock.paused(store.getExternalData().getWorld());
        return colonies(store).map(w -> MenuViews.of(w, paused, menus.state(operator), watches.watched(operator)));
    }

    private static Optional<ColonyWorld> colonies(Store<EntityStore> store) {
        return HyColonyAccess.world(store.getExternalData().getWorld());
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
}
