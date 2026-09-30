package dev.hycolony.core.app.api;

import dev.hycolony.api.ActionResult;
import dev.hycolony.api.Actor;
import dev.hycolony.api.ApiText;
import dev.hycolony.api.CitizenRef;
import dev.hycolony.api.Pos;
import dev.hycolony.core.citizen.CitizenAI;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.ColonyAccess;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.Vec3;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Consumer;

/**
 * The api's debug actions on one world's citizens (spec 2026-09-30, § 4.1, § 5). A player acts if an operator or a
 * manager of the colony (MC IMCColonyOfficerCommand.checkPreCondition); a plugin answers for itself; the colony is
 * only a cause, never one who asks.
 */
final class CoreDebugActions {
    private final CoreColonyWorld world;

    CoreDebugActions(CoreColonyWorld world) {
        this.world = world;
    }

    /** MC CommandCitizenTriggerWalkTo: sends the citizen there ({@code CitizenAI.walkTo}); its body must live. */
    ActionResult walkTo(Actor actor, CitizenRef ref, Pos target) {
        return onLivingAi(actor, ref, ai -> ai.walkTo(block(target)));
    }

    /** Starts a leisure break now, as long as MC's ({@link CitizenData#LEISURE_TICKS}). */
    ActionResult forceLeisure(Actor actor, CitizenRef ref) {
        return act(actor, ref, (c, d) -> {
            d.setLeisureTime(CitizenData.LEISURE_TICKS);
            return new ActionResult.Done();
        });
    }

    /** Teleports the citizen's living body onto {@code target} ({@code CitizenAI.teleport}). */
    ActionResult teleport(Actor actor, CitizenRef ref, Pos target) {
        return onLivingAi(actor, ref, ai -> ai.teleport(Vec3.center(block(target))));
    }

    /** Gives the citizen a new body where the respawn check would; unavailable when none could appear. */
    ActionResult respawnBody(Actor actor, CitizenRef ref) {
        return act(
                actor,
                ref,
                (c, d) -> c.citizens().respawnBody(d.id()) ? new ActionResult.Done() : new ActionResult.Unavailable());
    }

    /** Runs {@code action} on the AI of the citizen's living body; unavailable while it has none. */
    private ActionResult onLivingAi(Actor actor, CitizenRef ref, Consumer<CitizenAI> action) {
        return act(actor, ref, (c, d) -> {
            Optional<CitizenAI> ai = c.citizens()
                    .bodyOf(d.id())
                    .filter(c.context().bodies()::isAlive)
                    .flatMap(_ -> c.citizens().ai(d.id()));
            ai.ifPresent(action);
            return ai.isPresent() ? new ActionResult.Done() : new ActionResult.Unavailable();
        });
    }

    /**
     * Runs {@code action} for {@code actor} on the citizen {@code ref}: not found without its colony, refused without
     * the right (checked first, as MC's commands), then not found without the citizen.
     */
    private ActionResult act(Actor actor, CitizenRef ref, BiFunction<Colony, CitizenData, ActionResult> action) {
        world.checkThread();
        Colony c = world.find(ref.colony()).orElse(null);
        if (c == null) {
            return new ActionResult.NotFound();
        }
        Optional<ApiText> refusal = refusal(actor, c);
        if (refusal.isPresent()) {
            return new ActionResult.Refused(refusal.get());
        }
        return c.citizens().get(ref.citizenId()).map(d -> action.apply(c, d)).orElseGet(ActionResult.NotFound::new);
    }

    /** Why {@code actor} may not act on {@code c}; empty when it may. */
    private static Optional<ApiText> refusal(Actor actor, Colony c) {
        return switch (actor) {
            case Actor.Colony _ -> Optional.of(ApiText.of("hycolony.debug.refused.colony"));
            case Actor.Plugin _ -> Optional.empty();
            case Actor.Player p ->
                ColonyAccess.isOfficer(c, p.id())
                        ? Optional.empty()
                        : Optional.of(ApiText.of("hycolony.permission.denied", c.name()));
        };
    }

    private static BlockPos block(Pos p) {
        return new BlockPos(p.x(), p.y(), p.z());
    }
}
