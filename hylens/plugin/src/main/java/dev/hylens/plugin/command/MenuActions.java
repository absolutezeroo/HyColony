package dev.hylens.plugin.command;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hycolony.api.ActionResult;
import dev.hycolony.api.Actor;
import dev.hycolony.api.ApiText;
import dev.hycolony.api.CitizenRef;
import dev.hycolony.api.ColonyWorld;
import dev.hycolony.api.Pos;
import dev.hycolony.api.debug.DebugAccess;
import dev.hylens.core.menu.ActionReport;
import java.util.Optional;
import java.util.UUID;
import org.joml.Vector3d;

/**
 * The menu's actions on a citizen (spec 2026-09-30, § 6.4), asked of HyColony in the operator's name: HyColony lets
 * an operator or a manager of the colony act, and refuses anyone else /hylens would have been granted to. World thread.
 */
final class MenuActions {
    private MenuActions() {}

    /**
     * Runs {@code action} ("leisure", "teleport" to the operator's cell {@code feet}, "respawn") on the chosen
     * {@code citizen} of {@code colonies} as {@code operator}; the text of its result, or asks to choose a citizen
     * first; empty for an unknown action.
     */
    static Optional<ApiText> run(
            String action, Optional<CitizenRef> citizen, Optional<ColonyWorld> colonies, UUID operator, Pos feet) {
        if (citizen.isEmpty() || colonies.isEmpty()) {
            return Optional.of(ApiText.of("hylens.action.noneChosen"));
        }
        DebugAccess debug = colonies.get().debug();
        CitizenRef c = citizen.get();
        Actor actor = new Actor.Player(operator);
        Optional<ActionResult> done = switch (action) {
            case "leisure" -> Optional.of(debug.forceLeisure(actor, c));
            case "teleport" -> Optional.of(debug.teleport(actor, c, feet));
            case "respawn" -> Optional.of(debug.respawnBody(actor, c));
            default -> Optional.empty();
        };
        return done.map(ActionReport::text);
    }

    /** The cell the operator stands in. */
    static Pos feet(Ref<EntityStore> ref, Store<EntityStore> store) {
        Vector3d p =
                store.getComponent(ref, TransformComponent.getComponentType()).getPosition();
        return new Pos((int) Math.floor(p.x), (int) Math.floor(p.y), (int) Math.floor(p.z));
    }
}
