package dev.hycolony.core.app.action;

import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.building.Building;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.GamePorts;
import dev.hycolony.core.colony.permission.Action;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.request.Request;
import dev.hycolony.core.request.RequestManager;
import dev.hycolony.core.request.Resolver;
import dev.hycolony.core.request.model.Deliverable;
import dev.hycolony.core.request.model.RequestState;
import dev.hycolony.core.request.model.RequestToken;
import dev.hycolony.core.request.model.StackRequest;
import dev.hycolony.core.request.resolver.PlayerResolver;
import dev.hycolony.core.request.resolver.RetryingResolver;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * What players do for the colony's requests: hand over what one asks for ("Fournir"), stock a hut ("Ajouter"), or
 * change a hut container's content, which gives the building's stuck requests another chance.
 */
public final class RequestActions {
    private static final System.Logger LOG = System.getLogger(ColonyManager.class.getName());

    private final ColonyManager manager;

    public RequestActions(ColonyManager manager) {
        this.manager = manager;
    }

    /**
     * "Fournir": moves min(requested, owned) from the player to the requesting citizen (or, for the building
     * itself, its hut containers), each stack with its damage, and overrules the request. A partial amount still
     * closes it; the requester asks again for the rest. False if nothing was moved.
     */
    public boolean fulfil(UUID player, int colonyId, RequestToken token) {
        Colony c = manager.byId(colonyId).orElse(null);
        if (c == null || !c.permissions().hasPermission(player, Action.ACCESS_HUTS)) {
            return false;
        }
        Request req = openItemRequest(c, token).orElse(null);
        if (req == null) {
            return false;
        }
        Deliverable wanted = req.deliverable().orElseThrow();
        GamePorts ports = manager.context().ports();
        Optional<ItemKey> item = itemFor(player, wanted);
        if (item.isEmpty()) {
            return false;
        }
        // A broken tool answers no request (MC destroyed it): the player keeps it, a good one goes.
        List<ItemAmount> taken = ports.playerInventory()
                .takeStacks(player, item.get(), wanted.count(), a -> wanted.matches(a, ports.catalog()));
        Optional<CitizenData> citizen = req.citizenId() == Request.NO_CITIZEN
                ? Optional.empty()
                : c.citizens().get(req.citizenId());
        int moved = 0;
        for (ItemAmount stack : taken) {
            moved += stack.count() - giveBack(player, deliver(c, req, citizen, stack));
        }
        if (moved <= 0) {
            return false;
        }
        c.requests().overrule(token, List.of(new ItemAmount(item.get(), moved)), citizen.isPresent());
        c.markDirty();
        return true;
    }

    /** The request if it is still open and asks for items; empty otherwise (a player only provides items). */
    private static Optional<Request> openItemRequest(Colony c, RequestToken token) {
        return c.requests()
                .get(token)
                .filter(r -> r.state().isBefore(RequestState.COMPLETED)
                        && r.deliverable().isPresent());
    }

    /** The item a stack request names, else the first of the player's items that matches. */
    private Optional<ItemKey> itemFor(UUID player, Deliverable wanted) {
        GamePorts ports = manager.context().ports();
        return wanted instanceof StackRequest s
                ? Optional.of(s.item())
                : ports.playerInventory().contents(player).keySet().stream()
                        .filter(k -> wanted.matches(k, ports.catalog()))
                        .findFirst();
    }

    /** Into the citizen's inventory, else the requesting hut's containers; returns what did not fit (or null). */
    private @Nullable ItemAmount deliver(Colony c, Request req, Optional<CitizenData> citizen, ItemAmount taken) {
        GamePorts ports = manager.context().ports();
        if (citizen.isPresent()) {
            return citizen.get().inventory().insert(taken, ports.catalog()::maxStack);
        }
        Optional<Building> hut = c.buildings().byRequester(req.requester());
        return hut.isPresent() ? ports.containers().insert(hut.get().containers(), taken) : taken;
    }

    /**
     * "Ajouter": moves min(wanted, owned) from the player into the hut's containers, then overrules the first open
     * request of that building held by the player or retrying resolver for that item, with the stacks moved that are
     * not worn out (a broken tool closes nothing, MC destroyed it), and shows the hut's window again. Returns how many
     * moved; nothing happens (0) outside a colony, without the hut or without ACCESS_HUTS.
     */
    public int addToHut(UUID player, BlockPos hutPos, ItemKey item, int wanted) {
        Colony c = manager.colonyAt(hutPos).orElse(null);
        if (c == null || wanted <= 0 || !c.permissions().hasPermission(player, Action.ACCESS_HUTS)) {
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
            int in = stack.count() - giveBack(player, ports.containers().insert(b.containers(), stack));
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
                                .onColonyUpdate(r -> r.requester().equals(b.requesterId())
                                        && b.resolvers().stream().anyMatch(res -> res.canResolve(c.requests(), r)))));
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
                m.overrule(r.token(), List.of(stack));
                return;
            }
        }
    }

    /** Returns {@code rest} to the player; returns its count (0 if none). */
    private int giveBack(UUID player, @Nullable ItemAmount rest) {
        if (rest == null) {
            return 0;
        }
        ItemAmount lost = manager.context().ports().playerInventory().give(player, rest);
        if (lost != null) {
            LOG.log(
                    System.Logger.Level.WARNING,
                    "Player {0} inventory full: {1} x {2} lost",
                    player,
                    lost.count(),
                    lost.item().id());
        }
        return rest.count();
    }
}
