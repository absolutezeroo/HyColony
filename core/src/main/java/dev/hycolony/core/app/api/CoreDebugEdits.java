package dev.hycolony.core.app.api;

import dev.hycolony.api.ActionResult;
import dev.hycolony.api.Actor;
import dev.hycolony.api.ApiText;
import dev.hycolony.api.CitizenRef;
import dev.hycolony.api.ColonyRef;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.colony.Colony;
import java.util.Optional;

/**
 * The api's debug actions that change a colony's citizens, as MC's citizen commands (spec 2026-10-02 lot 2, § 3):
 * spawnNew, an operator command, and modify saturation, an officer command that a non-operator may run only if the
 * server allows it.
 */
final class CoreDebugEdits {
    private final CoreColonyWorld world;

    CoreDebugEdits(CoreColonyWorld world) {
        this.world = world;
    }

    /**
     * MC CommandCitizenSpawnNew (an IMCOPCommand): a citizen arrives by force ({@code CitizenManager.spawnForced});
     * unavailable when none could, not found without the colony.
     */
    ActionResult spawnCitizen(Actor actor, ColonyRef ref) {
        world.checkThread();
        Colony c = world.find(ref).orElse(null);
        if (c == null) {
            return new ActionResult.NotFound();
        }
        Optional<ApiText> refusal = operatorRefusal(actor, c);
        if (refusal.isPresent()) {
            return new ActionResult.Refused(refusal.get());
        }
        return c.citizens().spawnForced() ? new ActionResult.Done() : new ActionResult.Unavailable();
    }

    /**
     * MC CommandCitizenModify saturation: sets it, kept between 0 and the maximum, and saves; refused for a value that
     * is not a number, and for a manager who is not an operator unless the server allows it, before the citizen is
     * looked up, as MC.
     */
    ActionResult setSaturation(Actor actor, CitizenRef ref, double value) {
        world.checkThread();
        if (Double.isNaN(value)) {
            return new ActionResult.Refused(ApiText.of("hycolony.debug.refused.value"));
        }
        Colony c = world.find(ref.colony()).orElse(null);
        if (c == null) {
            return new ActionResult.NotFound();
        }
        Optional<ApiText> refusal = modifyRefusal(actor, c);
        if (refusal.isPresent()) {
            return new ActionResult.Refused(refusal.get());
        }
        return c.citizens()
                .get(ref.citizenId())
                .<ActionResult>map(d -> {
                    d.setSaturation(Math.clamp(value, 0, CitizenData.MAX_SATURATION));
                    c.markDirty();
                    return new ActionResult.Done();
                })
                .orElseGet(ActionResult.NotFound::new);
    }

    /** Why {@code actor} may not act as an operator on {@code c} (MC IMCOPCommand); a plugin may. */
    private static Optional<ApiText> operatorRefusal(Actor actor, Colony c) {
        return switch (actor) {
            case Actor.Colony _ -> Optional.of(ApiText.of("hycolony.debug.refused.colony"));
            case Actor.Plugin _ -> Optional.empty();
            case Actor.Player p ->
                c.context().players().isOperator(p.id())
                        ? Optional.empty()
                        : Optional.of(ApiText.of("hycolony.permission.denied", c.name()));
        };
    }

    /**
     * {@link CoreDebugActions#refusal}, then MC CommandCitizenModify's canPlayerUseModifyCitizensCommand for a player
     * who is not an operator.
     */
    private static Optional<ApiText> modifyRefusal(Actor actor, Colony c) {
        return CoreDebugActions.refusal(actor, c)
                .or(() -> actor instanceof Actor.Player p
                                && !c.context().players().isOperator(p.id())
                                && !c.context().config().commands().canPlayerUseModifyCitizensCommand()
                        ? Optional.of(ApiText.of("hycolony.debug.refused.config"))
                        : Optional.empty());
    }
}
