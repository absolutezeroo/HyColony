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
 * (SpectateCommand.enterAndWatch), and keeps the citizen's history while watched (spec 2026-09-30, § 6.1).
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
     * not when its body is not loaded in their world, or dying, or the watch game mode cannot be entered.
     */
    void start(PlayerRef player, Store<EntityStore> store, Ref<EntityStore> ref, CitizenRef citizen, String name) {
        HyColonyApi api = HyColonyApi.get();
        // A dying body is refused as /spectate refuses it: FollowTarget would drop it at once (SpectateCommand:94).
        Optional<Ref<EntityStore>> body = api.bodyOf(citizen)
                .filter(b -> b.isValid()
                        && store.equals(b.getStore())
                        && !store.getArchetype(b).contains(DeathComponent.getComponentType()));
        if (body.isEmpty()) {
            Chat.tell(player, "hylens.watch.noBody", name);
            return;
        }
        Optional<Subscription> tracking = api.track(owner, citizen);
        if (tracking.isEmpty()) {
            Chat.tell(player, "hylens.watch.notFound", name);
            return;
        }
        Optional<String> mode = ids.watchGameMode().filter(GameModeTypes::isValidType);
        if (mode.isEmpty() || !Spectating.isSpectating(ref, store) && !GameModeTypes.enter(ref, store, mode.get())) {
            tracking.get().close();
            Chat.tell(player, "hylens.watch.cantEnter");
            return;
        }
        store.putComponent(ref, Spectating.getComponentType(), new Spectating(body.get()));
        watches.start(player.getUuid(), citizen, tracking.get());
        Chat.tell(player, "hylens.watch.started", name);
    }
}
