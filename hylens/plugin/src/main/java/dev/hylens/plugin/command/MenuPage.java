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
import dev.hycolony.api.ColonyWorld;
import dev.hylens.core.check.AutoCheck;
import dev.hylens.core.check.NewAlerts;
import dev.hylens.core.menu.MenuView;
import dev.hylens.core.menu.MenuViews;
import dev.hylens.core.menu.Menus;
import dev.hylens.core.watch.Watches;
import dev.hylens.plugin.HyColonyAccess;
import dev.hylens.plugin.check.ColonyChecks;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nonnull;
import org.jspecify.annotations.Nullable;

/**
 * The HyLens menu (spec 2026-09-30, § 6.4): choose a colony and a citizen, watch it, act on it, turn layers on or off,
 * pause, step and resume the colonies, check them now or every few seconds. Each click redraws the page from what
 * HyColony tells now; actions are {@link MenuActions}', the clock {@link MenuClock}'s.
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

    /** The clicks that record a choice. */
    private static final Set<String> CHOICES = Set.of("colony", "citizen", "layer", "stepLess", "stepMore");
    /** The clicks on the colony clock. */
    private static final Set<String> CLOCK = Set.of("pause", "step", "resume");
    /** The clicks on the checks. */
    private static final Set<String> CHECKS = Set.of("checkNow", "autoCheck");

    private final Menus menus;
    private final Watches watches;
    private final CitizenWatch watch;
    private final MenuClock clock;
    private final NewAlerts alerts;
    private Optional<ApiText> result = Optional.empty();

    MenuPage(PlayerRef player, LensParts parts, CitizenWatch watch) {
        super(player, CustomPageLifetime.CanDismiss, Data.CODEC);
        this.menus = parts.menus();
        this.watches = parts.watches();
        this.watch = watch;
        this.clock = parts.clock();
        this.alerts = parts.alerts();
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
        String action = data.action;
        if ("watch".equals(action)) {
            if (startWatch(v.get(), ref, store)) {
                return;
            }
        } else {
            dispatch(action, Objects.requireNonNullElse(data.index, ""), v.get(), ref, store);
        }
        rebuild();
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
        if (CHOICES.contains(action)) {
            choose(action, index, v, operator);
        } else if (CLOCK.contains(action)) {
            result = clock.run(
                    action,
                    store.getExternalData().getWorld(),
                    operator,
                    menus.state(operator).step());
        } else if (CHECKS.contains(action)) {
            check(action, store, operator);
        } else {
            act(action, v, ref, store);
        }
    }

    /**
     * Checks the world's colonies now, the result told in the chat ("checkNow"), or turns the operator's automatic
     * check on or off ("autoCheck").
     */
    private void check(String action, Store<EntityStore> store, UUID operator) {
        if ("checkNow".equals(action)) {
            colonies(store).ifPresent(w -> ColonyChecks.tell(playerRef, w));
            result = Optional.of(ApiText.of("hylens.check.inChat"));
        } else {
            boolean on = AutoCheck.toggle(menus, alerts, operator);
            result = Optional.of(ApiText.of(on ? "hylens.check.autoOn" : "hylens.check.autoOff"));
        }
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
