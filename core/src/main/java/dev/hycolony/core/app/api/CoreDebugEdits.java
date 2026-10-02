package dev.hycolony.core.app.api;

import dev.hycolony.api.ActionResult;
import dev.hycolony.api.Actor;
import dev.hycolony.api.ApiText;
import dev.hycolony.api.CitizenRef;
import dev.hycolony.api.ColonyRef;
import dev.hycolony.api.debug.SaturationChange;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.ColonyState;
import java.util.Optional;

/**
 * The api's debug actions that change a colony's citizens, as MC's citizen commands (spec 2026-10-02 lot 2, § 3):
 * spawnNew, an operator command, and modify saturation, an officer command that a non-operator may run only if the
 * server allows it, and a player only in creative mode.
 *
 * <p>Deviation from MC: an action answers an {@link ActionResult}, without MC's success text (the new citizen's name,
 * the saturation set): the caller reads them back.
 */
final class CoreDebugEdits {
    private final CoreColonyWorld world;

    CoreDebugEdits(CoreColonyWorld world) {
        this.world = world;
    }

    /**
     * MC CommandCitizenSpawnNew: refused to a player who is not an operator (IMCOPCommand, checked before the colony),
     * then a citizen arrives by force ({@code CitizenManager.spawnForced}). Deviation from MC: unavailable when none
     * could arrive, where MC's command fails on a null citizen; and the api's CitizenSpawned event carries no source,
     * where MC posts its CitizenAddedModEvent with COMMANDS (a component more would break the api's record).
     */
    ActionResult spawnCitizen(Actor actor, ColonyRef ref) {
        world.checkThread();
        Optional<ApiText> refusal = operatorRefusal(actor);
        if (refusal.isPresent()) {
            return new ActionResult.Refused(refusal.get());
        }
        return world.find(ref)
                .<ActionResult>map(
                        c -> c.citizens().spawnForced() ? new ActionResult.Done() : new ActionResult.Unavailable())
                .orElseGet(ActionResult.NotFound::new);
    }

    /**
     * MC CommandCitizenModify saturation: refuses a value outside 0 to the maximum (its argument's bounds), then a
     * player neither operator nor manager, a manager who is not an operator unless the server allows it, and a player
     * not in creative mode, all before the citizen is looked up; then changes it ({@link #change}) and saves. An
     * operator passes MC's officer check without its colony, so one out of creative mode is refused before the colony
     * is looked up.
     */
    ActionResult modifySaturation(Actor actor, CitizenRef ref, SaturationChange change, double value) {
        world.checkThread();
        if (!(value >= 0 && value <= CitizenData.MAX_SATURATION)) { // also NaN
            return new ActionResult.Refused(
                    ApiText.of("hycolony.debug.refused.value", "0", String.valueOf((int) CitizenData.MAX_SATURATION)));
        }
        if (actor instanceof Actor.Player p && world.isOperator(p.id()) && !world.isCreative(p.id())) {
            return new ActionResult.Refused(ApiText.of("hycolony.debug.refused.creative"));
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
                    change(c, d, change, value);
                    // Deviation from MC: marks the colony to save, as HyColony rewrites only the colonies marked.
                    c.markDirty();
                    return new ActionResult.Done();
                })
                .orElseGet(ActionResult.NotFound::new);
    }

    /**
     * MC CitizenData setSaturation, increaseSaturation and decreaseSaturation, which does nothing to an inactive
     * colony (MC Colony.isActive).
     */
    private static void change(Colony c, CitizenData d, SaturationChange change, double value) {
        switch (change) {
            case SET -> d.setSaturation(value);
            case INCREASE -> d.hunger().increase(value);
            case DECREASE -> {
                if (c.state() != ColonyState.INACTIVE) {
                    d.hunger().decrease(value, c.context().config().gameplay().foodModifier());
                }
            }
        }
    }

    /** Why {@code actor} may not run an operator command (MC IMCOPCommand, COMMAND_REQUIRES_OP); a plugin may. */
    private Optional<ApiText> operatorRefusal(Actor actor) {
        return switch (actor) {
            case Actor.Colony _ -> Optional.of(ApiText.of("hycolony.debug.refused.colony"));
            case Actor.Plugin _ -> Optional.empty();
            case Actor.Player p ->
                world.isOperator(p.id())
                        ? Optional.empty()
                        : Optional.of(ApiText.of("hycolony.debug.refused.operator"));
        };
    }

    /**
     * {@link CoreDebugActions#refusal}, then MC CommandCitizenModify's canPlayerUseModifyCitizensCommand for a player
     * who is not an operator, then its creative mode for any player (a plugin passes, as MC's server console).
     */
    private static Optional<ApiText> modifyRefusal(Actor actor, Colony c) {
        Optional<ApiText> officer = CoreDebugActions.refusal(actor, c);
        if (officer.isPresent() || !(actor instanceof Actor.Player p)) {
            return officer;
        }
        if (!c.context().players().isOperator(p.id())
                && !c.context().config().commands().canPlayerUseModifyCitizensCommand()) {
            return Optional.of(ApiText.of("hycolony.debug.refused.config"));
        }
        return c.context().players().isCreative(p.id())
                ? Optional.empty()
                : Optional.of(ApiText.of("hycolony.debug.refused.creative"));
    }
}
