package dev.hylens.plugin.command;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hycolony.api.ActionResult;
import dev.hycolony.api.Actor;
import dev.hycolony.api.ApiText;
import dev.hycolony.api.CitizenRef;
import dev.hycolony.api.ColonyRef;
import dev.hycolony.api.ColonyWorld;
import dev.hycolony.api.Pos;
import dev.hycolony.api.debug.DebugAccess;
import dev.hylens.core.menu.ActionReport;
import dev.hylens.core.menu.MenuView;
import java.util.Optional;
import java.util.UUID;
import org.joml.Vector3d;

/**
 * The menu's actions on a citizen (spec 2026-09-30, § 6.4) and its edits of a colony's citizens (spec 2026-10-02 lot
 * 2, § 5), asked of HyColony in the operator's name: HyColony decides who may act, as MC's commands. World thread.
 */
final class MenuActions {
    private MenuActions() {}

    /**
     * Runs the edit {@code action}: on {@code v}'s chosen colony ({@link #onColony}), or change the chosen citizen's
     * "saturation" by the step named {@code index} (its maximum read from HyColony now); as {@code operator}. The text
     * of its result, or asks to choose first; empty for an unknown step or an unread saturation.
     */
    static Optional<ApiText> edit(
            String action, String index, MenuView v, Optional<ColonyWorld> colonies, UUID operator) {
        Actor actor = new Actor.Player(operator);
        if (!"saturation".equals(action)) {
            return Optional.of(onColony(action, v, colonies, actor));
        }
        if (v.citizen().isEmpty() || colonies.isEmpty()) {
            return Optional.of(ApiText.of("hylens.action.noneChosen"));
        }
        CitizenRef c = v.citizen().get();
        ColonyWorld w = colonies.get();
        return MenuClicks.saturation(index)
                .flatMap(step -> w.wellbeing(c)
                        .map(now -> ActionReport.text(
                                w.debug().modifySaturation(actor, c, step.change(), step.value(now.maxSaturation())))));
    }

    /**
     * Runs {@code action} on {@code v}'s chosen colony as {@code actor}: "spawn" a citizen, "resetRequests", or
     * "fulfil" the chosen request; the text of its result, or asks to choose the colony or the request first.
     */
    private static ApiText onColony(String action, MenuView v, Optional<ColonyWorld> colonies, Actor actor) {
        if (colonies.isEmpty() || v.colony().isEmpty()) {
            return ApiText.of("hylens.action.noColony");
        }
        DebugAccess debug = colonies.get().debug();
        ColonyRef c = v.colony().get();
        return switch (action) {
            case "spawn" -> ActionReport.spawned(debug.spawnCitizen(actor, c));
            case "resetRequests" -> ActionReport.reset(debug.resetRequests(actor, c));
            default ->
                v.requests().stream()
                        .filter(MenuView.RequestRow::chosen)
                        .findFirst()
                        .map(r -> ActionReport.fulfilled(debug.fulfilRequest(actor, c, r.id())))
                        .orElse(ApiText.of("hylens.action.noRequest"));
        };
    }

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
