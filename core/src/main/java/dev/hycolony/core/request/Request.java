package dev.hycolony.core.request;

import dev.hycolony.core.kernel.item.ItemAmount;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** A request tracked by the {@link RequestManager}; only the manager mutates it. */
public final class Request {
    private final RequestToken token;
    private final RequesterId requester;
    private final Deliverable requestable;
    private final int citizenId;
    private RequestState state = RequestState.CREATED;
    private RequestToken parent;
    private final List<RequestToken> children = new ArrayList<>();
    private final List<ItemAmount> deliveries = new ArrayList<>();
    private Set<String> blacklist = Set.of();

    Request(RequestToken token, RequesterId requester, Deliverable requestable, int citizenId) {
        this.token = Objects.requireNonNull(token, "token");
        this.requester = Objects.requireNonNull(requester, "requester");
        this.requestable = Objects.requireNonNull(requestable, "requestable");
        this.citizenId = citizenId;
    }

    public RequestToken token() { return token; }

    public RequesterId requester() { return requester; }

    public Deliverable requestable() { return requestable; }

    public RequestState state() { return state; }

    public Optional<RequestToken> parent() { return Optional.ofNullable(parent); }

    public List<RequestToken> children() { return Collections.unmodifiableList(children); }

    public List<ItemAmount> deliveries() { return Collections.unmodifiableList(deliveries); }

    /** -1 = the building itself. */
    public int citizenId() { return citizenId; }

    // --- package-private mutators, used by RequestManager ---

    void setState(RequestState s) { state = s; }

    void setParent(RequestToken p) { parent = p; }

    void addChild(RequestToken c) { children.add(c); }

    void removeChild(RequestToken c) { children.remove(c); }

    void addDelivery(ItemAmount a) { deliveries.add(a); }

    void setDeliveries(List<ItemAmount> d) {
        deliveries.clear();
        deliveries.addAll(d);
    }

    /** The resolver blacklist this request was last assigned with; its children inherit it. */
    Set<String> blacklist() { return blacklist; }

    void setBlacklist(Set<String> b) { blacklist = Set.copyOf(b); }

    @Override
    public String toString() {
        return "Request[" + token.id() + " " + requestable.describe() + " " + state + "]";
    }
}
