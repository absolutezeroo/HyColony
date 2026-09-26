package dev.hycolony.core.request.resolver;

import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.request.Deliverable;
import dev.hycolony.core.request.Request;
import dev.hycolony.core.request.RequestManager;
import dev.hycolony.core.request.RequestToken;
import dev.hycolony.core.request.RequesterId;
import dev.hycolony.core.request.Resolver;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** MineColonies StandardPlayerRequestResolver: the last resort, waits for a player to provide the items. */
public final class PlayerResolver implements Resolver {
    public static final String ID = "player";
    public static final int PRIORITY = 0;
    private static final RequesterId REQUESTER_ID = new RequesterId("resolver:" + ID);

    private final BlockPos location;
    /** Assigned and not finished, in the order they reached the player. */
    private final Map<RequestToken, Request> open = new LinkedHashMap<>();
    /** Requests already announced: a request coming back after a retry is not announced again. */
    private final Set<RequestToken> announced = new HashSet<>();

    private Consumer<Request> onNeedsPlayer = r -> {};

    public PlayerResolver(BlockPos location) {
        this.location = location;
    }

    /** Assigned & not finished, in the order they reached the player: the content of the requests window. */
    public List<Request> open() {
        return List.copyOf(open.values());
    }

    /** Called once per request, the first time it reaches the player (the colony tells its officers). */
    public void setOnNeedsPlayer(Consumer<Request> listener) {
        onNeedsPlayer = listener;
    }

    /** Persistence only. A restored request was announced before the save. */
    public void restore(Request request) {
        open.put(request.token(), request);
        announced.add(request.token());
    }

    @Override
    public String resolverId() {
        return ID;
    }

    @Override
    public int priority() {
        return PRIORITY;
    }

    @Override
    public boolean handles(Deliverable requestable) {
        return true;
    }

    @Override
    public boolean canResolve(RequestManager m, Request r) {
        return true;
    }

    @Override
    public Optional<List<Deliverable>> attemptResolve(RequestManager m, Request r) {
        return Optional.of(List.of());
    }

    @Override
    public void resolve(RequestManager m, Request r) {
        open.put(r.token(), r);
        announced.removeIf(t -> m.get(t).isEmpty()); // finished requests
        if (announced.add(r.token())) {
            onNeedsPlayer.accept(r);
        }
    }

    @Override
    public void onCancelling(RequestManager m, Request r) {
        open.remove(r.token());
    }

    @Override
    public double suitability(RequestManager m, Request r) {
        return 0;
    }

    /** Matching requests get another chance, with the player blacklisted. */
    @Override
    public void onColonyUpdate(RequestManager m, Predicate<Request> which) {
        for (Request r : new ArrayList<>(open.values())) {
            if (which.test(r) && r.children().isEmpty()) {
                m.reassign(r.token(), Set.of(ID));
            }
        }
    }

    @Override
    public RequesterId requesterId() {
        return REQUESTER_ID;
    }

    @Override
    public BlockPos location() {
        return location;
    }

    @Override
    public String displayName() {
        return "Player";
    }

    @Override
    public void onRequestComplete(RequestManager manager, Request request) {}

    @Override
    public void onRequestCancelled(RequestManager manager, Request request) {}
}
