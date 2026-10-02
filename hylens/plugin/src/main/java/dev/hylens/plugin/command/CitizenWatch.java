package dev.hylens.plugin.command;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.modules.entity.component.Spectating;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.entity.gamemode.GameModeTypes;
import com.hypixel.hytale.server.core.plugin.PluginBase;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hycolony.api.CitizenRef;
import dev.hycolony.api.Subscription;
import dev.hycolony.plugin.api.HyColonyApi;
import dev.hylens.core.watch.Watches;
import dev.hylens.plugin.HyLensIds;
import java.util.Optional;

/**
 * Puts an operator's camera on a citizen's body, in the watch game mode, as /spectate target does
 * (SpectateCommand.enterAndWatch), and keeps the citizen's history while watched (spec 2026-09-30, § 6.1); frees the
 * camera and puts it back while the watch goes on (spec 2026-10-02, § 3.3).
 */
final class CitizenWatch {
    private final PluginBase owner;
    private final Watches watches;
    private final HyLensIds ids;

    CitizenWatch(PluginBase owner, Watches watches, HyLensIds ids) {
        this.owner = owner;
        this.watches = watches;
        this.ids = ids;
    }

    /**
     * On the world's thread: the operator at {@code ref} watches {@code citizen}, named {@code name}; tells them why
     * not when its body is not loaded in their world, or dying, HyColony no longer knows it, or the watch game mode
     * cannot be entered.
     */
    void start(PlayerRef player, Store<EntityStore> store, Ref<EntityStore> ref, CitizenRef citizen, String name) {
        Optional<Ref<EntityStore>> body = body(store, citizen);
        if (body.isEmpty()) {
            Chat.tell(player, "hylens.watch.noBody", name);
            return;
        }
        Optional<Subscription> tracking = HyColonyApi.get().track(owner, citizen);
        if (tracking.isEmpty()) {
            Chat.tell(player, "hylens.watch.notFound", name);
            return;
        }
        Optional<String> mode = ids.watchGameMode().filter(GameModeTypes::isValidType);
        if (mode.isEmpty() || (!Spectating.isSpectating(ref, store) && !GameModeTypes.enter(ref, store, mode.get()))) {
            tracking.get().close();
            Chat.tell(player, "hylens.watch.cantEnter");
            return;
        }
        store.putComponent(ref, Spectating.getComponentType(), new Spectating(body.get()));
        watches.start(player.getUuid(), citizen, tracking.get());
        Chat.tell(player, "hylens.watch.started", name);
    }

    /**
     * On the world's thread: the operator at {@code ref}'s camera leaves the body watched, as Hytale's SpectateControl
     * Detach (SpectateControlInteraction:49-52); they stay in the spectator mode, and the watch, its history, HUD and
     * drawings go on. False, and they are told, when they are not spectating.
     */
    boolean free(PlayerRef player, Store<EntityStore> store, Ref<EntityStore> ref) {
        if (!Spectating.isSpectating(ref, store)) {
            Chat.tell(player, "hylens.watch.notWatching");
            return false;
        }
        store.putComponent(ref, Spectating.getComponentType(), new Spectating());
        Chat.tell(player, "hylens.watch.freed");
        return true;
    }

    /**
     * On the world's thread: the operator at {@code ref}'s camera follows {@code citizen}'s body again, named
     * {@code name}, its watch and history kept; refused, and they are told, when not spectating or when the body is
     * refused as {@link #start} refuses it. True once following.
     */
    boolean follow(PlayerRef player, Store<EntityStore> store, Ref<EntityStore> ref, CitizenRef citizen, String name) {
        if (!Spectating.isSpectating(ref, store)) {
            Chat.tell(player, "hylens.watch.notWatching");
            return false;
        }
        Optional<Ref<EntityStore>> body = body(store, citizen);
        if (body.isEmpty()) {
            Chat.tell(player, "hylens.watch.noBody", name);
            return false;
        }
        store.putComponent(ref, Spectating.getComponentType(), new Spectating(body.get()));
        Chat.tell(player, "hylens.watch.followed", name);
        return true;
    }

    /**
     * Stops {@code player}'s watch and its history and leaves the spectator mode, even with no watch held, since the
     * mode is saved with the player and outlives a server restart; tells them which.
     */
    void stop(PlayerRef player, Store<EntityStore> store, Ref<EntityStore> ref) {
        boolean watched = watches.stop(player.getUuid()).isPresent();
        boolean left = Spectating.isSpectating(ref, store) && GameModeTypes.exit(ref, store);
        Chat.tell(player, watched || left ? "hylens.watch.stopped" : "hylens.watch.notWatching");
    }

    /** Whether the operator at {@code ref} spectates with a camera free of any body. */
    boolean cameraFree(Store<EntityStore> store, Ref<EntityStore> ref) {
        Spectating s = store.getComponent(ref, Spectating.getComponentType());
        return s != null && s.getTargetRef() == null;
    }

    /**
     * {@code citizen}'s body when loaded in {@code store}; a dying body is refused as /spectate refuses it, since
     * FollowTarget would drop it at once (SpectateCommand:94).
     */
    private static Optional<Ref<EntityStore>> body(Store<EntityStore> store, CitizenRef citizen) {
        return HyColonyApi.get()
                .bodyOf(citizen)
                .filter(b -> b.isValid()
                        && store.equals(b.getStore())
                        && !store.getArchetype(b).contains(DeathComponent.getComponentType()));
    }
}
