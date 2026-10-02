package dev.hycolony.core.app.requests;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.ColonyContext;
import dev.hycolony.core.colony.GamePorts;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.request.Request;
import dev.hycolony.core.request.model.Deliverable;
import dev.hycolony.core.request.model.RequestState;
import dev.hycolony.core.request.model.RequestToken;
import dev.hycolony.core.request.model.StackRequest;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Hands an open item request what it asks for (MC's request window "Fulfill", RequestWindowCitizen.onFulfill and
 * TransferItemsToCitizenRequestMessage): from a player's inventory, or for free in creative mode.
 */
public final class RequestFulfil {
    private static final System.Logger LOG = System.getLogger(RequestFulfil.class.getName());
    private final ColonyContext ctx;

    public RequestFulfil(ColonyContext ctx) {
        this.ctx = ctx;
    }

    /**
     * Fills {@code c}'s open item request {@code token}, then overrules it, and saves. From {@code payer}'s inventory:
     * min(asked, owned), each stack with its damage, what does not fit given back, and the request closed with what
     * moved. With no payer (creative mode, a plugin): the request's first displayed item, as many as asked, for free,
     * what does not fit lost, and the request closed with the amount asked, as MC. False, changing nothing, for no
     * such open request or nothing to hand over.
     */
    public boolean fulfil(Colony c, RequestToken token, Optional<UUID> payer) {
        Request req = c.requests()
                .get(token)
                .filter(r -> r.state().isBefore(RequestState.COMPLETED)
                        && r.deliverable().isPresent())
                .orElse(null);
        if (req == null) {
            return false;
        }
        Optional<CitizenData> citizen = req.citizenId() == Request.NO_CITIZEN
                ? Optional.empty()
                : c.citizens().get(req.citizenId());
        Optional<ItemAmount> moved =
                payer.isPresent() ? fromInventory(c, req, citizen, payer.get()) : forFree(c, req, citizen);
        if (moved.isEmpty()) {
            return false;
        }
        c.requests().overrule(token, List.of(moved.get()), citizen.isPresent());
        c.markDirty();
        return true;
    }

    /** min(asked, owned) of the request's item from {@code player}; what moved, empty if nothing did. */
    private Optional<ItemAmount> fromInventory(Colony c, Request req, Optional<CitizenData> citizen, UUID player) {
        Deliverable wanted = req.deliverable().orElseThrow();
        GamePorts ports = ctx.ports();
        Optional<ItemKey> item = wanted instanceof StackRequest s
                ? Optional.of(s.item())
                : ports.playerInventory().contents(player).keySet().stream()
                        .filter(k -> wanted.matches(k, ports.catalog()))
                        .findFirst();
        if (item.isEmpty()) {
            return Optional.empty();
        }
        // A broken tool answers no request (MC destroyed it): the player keeps it, a good one goes.
        List<ItemAmount> taken = ports.playerInventory()
                .takeStacks(player, item.get(), wanted.count(), a -> wanted.matches(a, ports.catalog()));
        int moved = 0;
        for (ItemAmount stack : taken) {
            moved += stack.count() - giveBack(player, deliver(c, req, citizen, stack));
        }
        return moved > 0 ? Optional.of(new ItemAmount(item.get(), moved)) : Optional.empty();
    }

    /**
     * MC creative mode: the request's first displayed item, as many as asked, stack by stack until one does not fit;
     * the amount asked, empty without a displayed item.
     */
    private Optional<ItemAmount> forFree(Colony c, Request req, Optional<CitizenData> citizen) {
        Deliverable wanted = req.deliverable().orElseThrow();
        Optional<ItemKey> item = wanted.displayed(ctx.ports().catalog());
        if (item.isEmpty()) {
            return Optional.empty();
        }
        int maxStack = Math.max(1, ctx.ports().catalog().maxStack(item.get()));
        for (int left = wanted.count(); left > 0; left -= maxStack) {
            if (deliver(c, req, citizen, new ItemAmount(item.get(), Math.min(maxStack, left))) != null) {
                break; // MC TransferItemsToCitizenRequestMessage stops at the first stack that does not fit
            }
        }
        return Optional.of(new ItemAmount(item.get(), wanted.count()));
    }

    /** Into the citizen's inventory, else the requesting hut's containers; returns what did not fit (or null). */
    private @Nullable ItemAmount deliver(Colony c, Request req, Optional<CitizenData> citizen, ItemAmount stack) {
        GamePorts ports = ctx.ports();
        if (citizen.isPresent()) {
            return citizen.get().inventory().insert(stack, ports.catalog()::maxStack);
        }
        Optional<Building> hut = c.buildings().byRequester(req.requester());
        return hut.isPresent() ? ports.containers().insert(hut.get().containers(), stack) : stack;
    }

    /** Returns {@code rest} to {@code player}; returns its count (0 if none). A full inventory loses it, logged. */
    public int giveBack(UUID player, @Nullable ItemAmount rest) {
        if (rest == null) {
            return 0;
        }
        ItemAmount lost = ctx.ports().playerInventory().give(player, rest);
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
