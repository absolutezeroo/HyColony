package dev.hycolony.core.request.resolver;

import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.request.Request;
import dev.hycolony.core.request.RequestManager;
import dev.hycolony.core.request.Resolver;
import dev.hycolony.core.request.model.RequestToken;
import dev.hycolony.core.request.model.Requestable;
import dev.hycolony.core.request.model.RequesterId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;

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

    private @Nullable Consumer<Request> onNeedsPlayer;

    public PlayerResolver(BlockPos location) {
        this.location = location;
    }

    /** Assigned & not finished, in the order they reached the player: the content of the requests window. */
    public List<Request> open() {
        return List.copyOf(open.values());
    }

    /**
     * Called once per request, the first time it reaches the player (the colony tells its officers). A request that
     * reached the player before anyone listened (a colony still loading) is announced now.
     */
    public void setOnNeedsPlayer(Consumer<Request> listener) {
        onNeedsPlayer = listener;
        List.copyOf(open.values()).forEach(this::announce);
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

    /**
     * Everything (MC getRequestType = {@code IRequestable}): a courier delivery or pickup that no warehouse can take
     * waits here too, until a courier comes back to work and the colony update reassigns it.
     */
    @Override
    public boolean handles(Requestable requestable) {
        return true;
    }

    @Override
    public boolean canResolve(RequestManager m, Request r) {
        return true;
    }

    @Override
    public Optional<List<Requestable>> attemptResolve(RequestManager m, Request r) {
        return Optional.of(List.of());
    }

    @Override
    public void resolve(RequestManager m, Request r) {
        open.put(r.token(), r);
        announced.removeIf(t -> m.get(t).isEmpty()); // finished requests
        announce(r);
    }

    /** Tells the listener about {@code r} once; not before a listener is set. */
    private void announce(Request r) {
        Consumer<Request> listener = onNeedsPlayer;
        if (listener != null && announced.add(r.token())) {
            listener.accept(r);
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

    /**
     * MC onColonyUpdate: matching requests get another chance, with the player blacklisted; for one that does not
     * match, the first matching ancestor does ({@link Ancestors}).
     */
    @Override
    public void onColonyUpdate(RequestManager m, Predicate<Request> which) {
        for (Request r : new ArrayList<>(open.values())) {
            if (!which.test(r)) {
                Ancestors.reassignMatching(m, r, which);
            } else if (r.children().isEmpty()) {
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
