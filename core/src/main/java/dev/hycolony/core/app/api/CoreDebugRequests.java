package dev.hycolony.core.app.api;

import dev.hycolony.api.ActionResult;
import dev.hycolony.api.Actor;
import dev.hycolony.api.ApiText;
import dev.hycolony.api.ColonyRef;
import dev.hycolony.core.app.requests.RequestFulfil;
import dev.hycolony.core.app.requests.RequestSystemReset;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.ColonyAccess;
import dev.hycolony.core.colony.permission.Action;
import dev.hycolony.core.request.model.RequestState;
import dev.hycolony.core.request.model.RequestToken;
import java.util.Optional;
import java.util.UUID;

/**
 * The api's debug actions on a colony's requests (spec 2026-10-02 lot 3, § 5): MC's request window "Fulfill"
 * ({@link RequestFulfil}) and /mc colony requestsystem-reset ({@link RequestSystemReset}).
 *
 * <p>Deviation from MC: both answer an {@link ActionResult}, without MC's texts (the reset's "restarted in 1.618
 * seconds" is a made-up duration); and a plugin fulfils for free, as MC's console would, having no inventory.
 */
final class CoreDebugRequests {
    private final CoreColonyWorld world;

    CoreDebugRequests(CoreColonyWorld world) {
        this.world = world;
    }

    /**
     * MC RequestWindowCitizen.onFulfill, then TransferItemsToCitizenRequestMessage and UpdateRequestStateMessage
     * (MANAGE_HUTS): not found without the colony or an open item request {@code requestId}; refused to a player
     * without the right; then free for a plugin or a player in creative mode, else from the player's inventory.
     */
    ActionResult fulfilRequest(Actor actor, ColonyRef ref, String requestId) {
        world.checkThread();
        Colony c = world.find(ref).orElse(null);
        Optional<RequestToken> token = token(requestId).filter(t -> c != null && isOpenItemRequest(c, t));
        if (c == null || token.isEmpty()) {
            return new ActionResult.NotFound();
        }
        Optional<UUID> payer;
        switch (actor) {
            case Actor.Colony _ -> {
                return new ActionResult.Refused(ApiText.of("hycolony.debug.refused.colony"));
            }
            case Actor.Plugin _ -> payer = Optional.empty();
            case Actor.Player p -> {
                if (!ColonyAccess.allows(c, p.id(), Action.MANAGE_HUTS)) {
                    return new ActionResult.Refused(ApiText.of("hycolony.permission.denied", c.name()));
                }
                payer = world.isCreative(p.id()) ? Optional.empty() : Optional.of(p.id());
            }
        }
        return new RequestFulfil(c.context()).fulfil(c, token.get(), payer)
                ? new ActionResult.Done()
                : new ActionResult.Unavailable();
    }

    /**
     * MC CommandRSReset (an IMCCommand: any player): not found without the colony; refused to the colony, and to a
     * player who is not an operator unless the server's canPlayerUseResetCommand allows it; then the reset.
     */
    ActionResult resetRequests(Actor actor, ColonyRef ref) {
        world.checkThread();
        Colony c = world.find(ref).orElse(null);
        if (c == null) {
            return new ActionResult.NotFound();
        }
        Optional<ApiText> refusal = switch (actor) {
            case Actor.Colony _ -> Optional.of(ApiText.of("hycolony.debug.refused.colony"));
            case Actor.Plugin _ -> Optional.empty();
            case Actor.Player p ->
                world.isOperator(p.id()) || c.context().config().commands().canPlayerUseResetCommand()
                        ? Optional.empty()
                        : Optional.of(ApiText.of("hycolony.debug.refused.config"));
        };
        if (refusal.isPresent()) {
            return new ActionResult.Refused(refusal.get());
        }
        RequestSystemReset.reset(c);
        return new ActionResult.Done();
    }

    /** The request {@code id} names, if it is a token's UUID string. */
    private static Optional<RequestToken> token(String id) {
        try {
            return Optional.of(new RequestToken(UUID.fromString(id)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /** Whether {@code token} is one of {@code c}'s requests still open that asks for items. */
    private static boolean isOpenItemRequest(Colony c, RequestToken token) {
        return c.requests()
                .get(token)
                .filter(r -> r.state().isBefore(RequestState.COMPLETED)
                        && r.deliverable().isPresent())
                .isPresent();
    }
}
