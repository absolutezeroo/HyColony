package dev.hycolony.core.app.requests;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.ColonyContext;
import dev.hycolony.core.colony.GamePorts;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.port.Msg;
import dev.hycolony.core.kernel.port.PlayerInventory;
import dev.hycolony.core.logistics.warehouse.RequesterLocation;
import dev.hycolony.core.request.Request;
import dev.hycolony.core.request.model.Deliverable;
import dev.hycolony.core.request.model.RequestState;
import dev.hycolony.core.request.model.RequestToken;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Hands an open item request what it asks for (MC's request window "Fulfill", RequestWindowCitizen.onFulfill and
 * TransferItemsToCitizenRequestMessage): from a player's inventory, or for free in creative mode. As MC, the request
 * is then overruled with the amount the window counted, whatever fitted.
 *
 * <p>Deviation from MC: what goes to no citizen (a hut's own request, a child request, a citizen gone) goes into the
 * hut that asks, or whose resolver asks, as the clipboard and the api have no citizen's window; MC hands it to the
 * window's citizen.
 *
 * <p>Deviation from MC: what the player wears is never taken; MC's transfer could take a worn stack identical to the
 * one handed over once the others ran out.
 */
public final class RequestFulfil {
    private static final System.Logger LOG = System.getLogger(RequestFulfil.class.getName());
    private final ColonyContext ctx;

    public RequestFulfil(ColonyContext ctx) {
        this.ctx = ctx;
    }

    /**
     * Fills {@code c}'s open item request {@code token}, then overrules it, and saves. From {@code payer}'s inventory
     * (MC: a player holding a matching item): its first matching stack and those identical, what fits moved and the
     * rest given back, the request closed with min(asked, all matching items owned). With no payer (creative mode, a
     * plugin): the request's first displayed item, as many as asked, for free, what does not fit lost, the request
     * closed with the amount asked; without a displayed item it is closed with no delivery, as MC. False, changing
     * nothing, for no such open request, or a payer holding none of it or only wearing it (told).
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
        Optional<List<ItemAmount>> closed =
                payer.isPresent() ? fromInventory(c, req, citizen, payer.get()) : forFree(c, req, citizen);
        if (closed.isEmpty()) {
            return false;
        }
        c.requests().overrule(token, closed.get(), citizen.isPresent());
        c.markDirty();
        return true;
    }

    /**
     * MC's window outside creative mode: the player's first carried stack the request accepts and those identical to
     * it (same item and wear, MC compareItemStacksIgnoreStackSize); the amount to overrule with counts every matching
     * item, worn ones too. Empty if the player holds none of it (a broken tool answers nothing: MC destroyed it), or
     * only wears it, then told so (MC cantTakeEquipped).
     */
    private Optional<List<ItemAmount>> fromInventory(
            Colony c, Request req, Optional<CitizenData> citizen, UUID player) {
        Deliverable wanted = req.deliverable().orElseThrow();
        PlayerInventory inv = ctx.ports().playerInventory();
        List<ItemAmount> carried = accepted(inv.stacks(player), wanted);
        int matching = count(carried) + count(accepted(inv.equipped(player), wanted));
        if (carried.isEmpty()) {
            if (matching > 0) {
                // MC names the window's citizen; with none, the hut that receives the items speaks.
                String who = citizen.map(CitizenData::name)
                        .orElseGet(() -> hut(c, req)
                                .map(Building::nameParam)
                                .orElse(req.requester().value()));
                ctx.notifier().send(player, Msg.of("hycolony.request.cantTakeEquipped", who));
            }
            return Optional.empty();
        }
        ItemAmount first = carried.getFirst();
        for (ItemAmount stack :
                inv.takeStacks(player, first.item(), wanted.count(), a -> a.damage() == first.damage())) {
            giveBack(player, deliver(c, req, citizen, stack));
        }
        return Optional.of(List.of(new ItemAmount(first.item(), Math.min(wanted.count(), matching))));
    }

    private List<ItemAmount> accepted(List<ItemAmount> stacks, Deliverable wanted) {
        return stacks.stream()
                .filter(s -> wanted.matches(s, ctx.ports().catalog()))
                .toList();
    }

    private static int count(List<ItemAmount> stacks) {
        return stacks.stream().mapToInt(ItemAmount::count).sum();
    }

    /**
     * MC creative mode: the request's first displayed item, as many as asked, stack by stack until one does not fit
     * (all lost with nowhere to go, as MC without the window's citizen); the amount asked to overrule with, none
     * without a displayed item.
     */
    private Optional<List<ItemAmount>> forFree(Colony c, Request req, Optional<CitizenData> citizen) {
        Deliverable wanted = req.deliverable().orElseThrow();
        Optional<ItemKey> item = wanted.displayed(ctx.ports().catalog());
        if (item.isEmpty()) {
            return Optional.of(List.of()); // MC overrules with an empty stack: closed, nothing delivered
        }
        int maxStack = Math.max(1, ctx.ports().catalog().maxStack(item.get()));
        for (int left = wanted.count(); left > 0; left -= maxStack) {
            if (deliver(c, req, citizen, new ItemAmount(item.get(), Math.min(maxStack, left))) != null) {
                break; // MC TransferItemsToCitizenRequestMessage stops at the first stack that does not fit
            }
        }
        return Optional.of(List.of(new ItemAmount(item.get(), wanted.count())));
    }

    /** Into the citizen's inventory, else the hut that asks; returns what did not fit (or null). */
    private @Nullable ItemAmount deliver(Colony c, Request req, Optional<CitizenData> citizen, ItemAmount stack) {
        GamePorts ports = ctx.ports();
        if (citizen.isPresent()) {
            return citizen.get().inventory().insert(stack, ports.catalog()::maxStack);
        }
        Optional<Building> hut = hut(c, req);
        return hut.isPresent() ? ports.containers().insert(hut.get().containers(), stack) : stack;
    }

    /** The hut that asks: the requester itself, else the hut whose resolver asks (a child request). */
    private static Optional<Building> hut(Colony c, Request req) {
        return c.buildings()
                .byRequester(req.requester())
                .or(() -> RequesterLocation.of(c, req.requester()).flatMap(c.buildings()::at));
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
