package dev.hycolony.core.app.action;

import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.app.requests.RequestFulfil;
import dev.hycolony.core.building.Building;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.ColonyAccess;
import dev.hycolony.core.colony.ColonyRefusal;
import dev.hycolony.core.colony.GamePorts;
import dev.hycolony.core.colony.permission.Action;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.request.Request;
import dev.hycolony.core.request.RequestManager;
import dev.hycolony.core.request.Resolver;
import dev.hycolony.core.request.model.RequestState;
import dev.hycolony.core.request.model.RequestToken;
import dev.hycolony.core.request.resolver.PlayerResolver;
import dev.hycolony.core.request.resolver.RetryingResolver;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * What players do for the colony's requests: hand over what one asks for ("Fournir"), stock a hut ("Ajouter"), or
 * change a hut container's content, which gives the building's stuck requests another chance.
 */
public final class RequestActions {
    private final ColonyManager manager;
    private final RequestFulfil fulfil;

    public RequestActions(ColonyManager manager) {
        this.manager = manager;
        this.fulfil = new RequestFulfil(manager.context());
    }

    /**
     * "Fournir" ({@link RequestFulfil}): from the player's inventory, or for free in creative mode (MC
     * RequestWindowCitizen), to the requesting citizen (or, with no citizen, the asking hut's containers), and
     * overrules the request. A partial amount still closes it; the requester asks again for the rest. False for an
     * unknown colony, a closed request or one for no items, a player holding none of it (a broken tool is none) or only
     * wearing it (told, as MC), or without MANAGE_HUTS (told).
     */
    public boolean fulfil(UUID player, int colonyId, RequestToken token) {
        Colony c = manager.byId(colonyId).orElse(null);
        if (c == null) {
            return false;
        }
        // MC TransferItemsToCitizenRequestMessage and UpdateRequestStateMessage: MANAGE_HUTS, refused aloud.
        if (!ColonyAccess.allows(c, player, Action.MANAGE_HUTS)) {
            ColonyRefusal.tellNoPermission(c, player);
            return false;
        }
        boolean creative = manager.context().players().isCreative(player);
        return fulfil.fulfil(c, token, creative ? Optional.empty() : Optional.of(player));
    }

    /**
     * MC RequestTreeWindowModule.cancel, sent as UpdateRequestStateMessage (CANCELLED) with MC's default MANAGE_HUTS:
     * the request is cancelled and the colony saved. False, changing nothing, for an unknown colony, a request no
     * longer open, or without the right (told, as MC). As MC's message, any open request may be cancelled; the windows
     * offer Cancel on their tree's roots only. Deviation from MC: a request already completed is left alone (MC's
     * message would cancel it in any state).
     */
    public boolean cancel(UUID player, int colonyId, RequestToken token) {
        Colony c = manager.byId(colonyId).orElse(null);
        if (c == null) {
            return false;
        }
        if (!ColonyAccess.allows(c, player, Action.MANAGE_HUTS)) {
            ColonyRefusal.tellNoPermission(c, player);
            return false;
        }
        boolean open = c.requests()
                .get(token)
                .filter(r -> r.state().isBefore(RequestState.COMPLETED))
                .isPresent();
        if (open) {
            c.requests().updateState(token, RequestState.CANCELLED);
            c.markDirty();
        }
        return open;
    }

    /**
     * "Ajouter": moves min(wanted, owned) from the player into the hut's containers, then overrules the first open
     * request of that building held by the player or retrying resolver for that item, with the stacks moved that are
     * not worn out (a broken tool closes nothing, MC destroyed it), and shows the hut's window again. Returns how many
     * moved; nothing happens (0) outside a colony, without the hut, without ACCESS_HUTS or for nothing wanted.
     */
    public int addToHut(UUID player, BlockPos hutPos, ItemKey item, int wanted) {
        Colony c = manager.colonyAt(hutPos).orElse(null);
        if (c == null || wanted <= 0 || !ColonyAccess.allows(c, player, Action.ACCESS_HUTS)) {
            return 0;
        }
        Building b = c.buildings().at(hutPos).orElse(null);
        if (b == null) {
            return 0;
        }
        GamePorts ports = manager.context().ports();
        int moved = 0;
        int usable = 0;
        for (ItemAmount stack : ports.playerInventory().takeStacks(player, item, wanted)) {
            int in = stack.count() - fulfil.giveBack(player, ports.containers().insert(b.containers(), stack));
            moved += in;
            usable += ports.catalog().wornOut(stack) ? 0 : in;
        }
        if (usable > 0) {
            overruleNextOpenRequestWithStack(c, b, new ItemAmount(item, usable));
        }
        if (moved > 0) {
            c.markDirty();
        }
        manager.windows().openBuilding(player, hutPos);
        return moved;
    }

    /**
     * A player changed a hut container's content: the building's stuck requests that its hut can now serve (enough
     * matching stock beyond what its other requests reserved) get another chance.
     */
    public void onContainerChanged(BlockPos containerPos) {
        manager.colonyAt(containerPos)
                .ifPresent(c -> c.buildings()
                        .owningContainer(containerPos)
                        .ifPresent(b -> c.requests()
                                .onColonyUpdate(r ->
                                        r.requester().equals(b.requesterId()) && b.stockCanServe(c.requests(), r))));
    }

    /** AbstractBuilding.overruleNextOpenRequestWithStack. */
    private void overruleNextOpenRequestWithStack(Colony c, Building b, ItemAmount stack) {
        RequestManager m = c.requests();
        for (Request r : m.byRequester(b.requesterId())) {
            String resolver = m.resolverOf(r.token()).map(Resolver::resolverId).orElse("");
            boolean stuck = resolver.equals(PlayerResolver.ID) || resolver.equals(RetryingResolver.ID);
            if (stuck
                    && r.state().isBefore(RequestState.COMPLETED)
                    && r.deliverable()
                            .filter(d ->
                                    d.matches(stack, manager.context().ports().catalog()))
                            .isPresent()) {
                m.overrule(r.token(), List.of(stack), false);
                return;
            }
        }
    }
}
