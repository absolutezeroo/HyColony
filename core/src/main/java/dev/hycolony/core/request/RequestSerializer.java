package dev.hycolony.core.request;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.persist.SavedJson;
import dev.hycolony.core.request.model.RequestState;
import dev.hycolony.core.request.model.RequestToken;
import dev.hycolony.core.request.model.Requestable;
import dev.hycolony.core.request.model.RequesterId;
import dev.hycolony.core.request.resolver.PlayerResolver;
import dev.hycolony.core.request.resolver.RetryingResolver;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * RequestManager <-> JSON: requests, assignments (resolverId -> tokens), retrying and player state. Read it after
 * the providers (buildings) re-registered; a request whose resolver is gone is reassigned instead of dropped. A request
 * of an unknown type or state is skipped with its whole family, so a disabled pack never locks the colony.
 */
public final class RequestSerializer {
    private RequestSerializer() {}

    public static JsonObject write(RequestManager m) {
        JsonObject o = new JsonObject();
        JsonArray requests = new JsonArray();
        m.all().forEach(r -> requests.add(request(r)));
        o.add("requests", requests);

        JsonObject assignments = new JsonObject();
        m.store.assignments().forEach((id, tokens) -> assignments.add(id, RequestToken.toJson(tokens)));
        o.add("assignments", assignments);

        retrying(m).ifPresent(r -> {
            JsonObject ro = new JsonObject();
            ro.add("delays", counts(r.delays(), r.delays().keySet()));
            ro.add("tries", counts(r.tries(), r.delays().keySet())); // stale tries are pruned at the next tick
            o.add("retrying", ro);
        });
        player(m)
                .ifPresent(p -> o.add(
                        "player",
                        RequestToken.toJson(
                                p.open().stream().map(Request::token).toList())));
        return o;
    }

    /** Restores the saved requests into {@code m}; true when broken request links were dropped (rewrite the save). */
    public static boolean read(JsonObject o, RequestManager m) {
        if (!o.has("requests")) {
            return false; // schema v1 placeholder
        }
        SavedRequests.Loaded loaded =
                SavedRequests.read(SavedJson.arrayOr(o.get("requests")), RequestSerializer::readRequest);
        loaded.requests().values().forEach(m.store::restore);

        List<RequestToken> orphans = readAssignments(SavedJson.objectOr(o.get("assignments")), m);
        boolean unassigned = addUnassigned(m, orphans);

        // Membership comes from the assignments; the saved resolver state only supplies the numbers.
        JsonObject ro = SavedJson.objectOr(o.get("retrying"));
        Map<RequestToken, Integer> savedDelays = readCounts(SavedJson.objectOr(ro.get("delays")));
        Map<RequestToken, Integer> savedTries = readCounts(SavedJson.objectOr(ro.get("tries")));
        retrying(m).ifPresent(r -> {
            Map<RequestToken, Integer> delays = new LinkedHashMap<>();
            Map<RequestToken, Integer> tries = new LinkedHashMap<>();
            for (Request req : m.assignedTo(RetryingResolver.ID)) {
                delays.put(req.token(), savedDelays.getOrDefault(req.token(), RetryingResolver.DELAY_UPDATES));
                tries.put(req.token(), savedTries.getOrDefault(req.token(), 1));
            }
            r.restore(delays, tries);
        });
        player(m).ifPresent(p -> m.assignedTo(PlayerResolver.ID).forEach(p::restore));

        orphans.forEach(m::reassignLoaded);
        return loaded.repaired() || unassigned;
    }

    /**
     * Adds to {@code orphans} the open requests that no saved assignment names, which would otherwise wait forever
     * without a resolver. True when one had been assigned (an unreadable or missing entry, § 5, repaired): a request
     * still REPORTED is legitimately unassigned (RequestAssigner) and only tried again.
     */
    private static boolean addUnassigned(RequestManager m, List<RequestToken> orphans) {
        boolean lost = false;
        for (Request r : m.all()) {
            if (r.state().isBefore(RequestState.COMPLETED)
                    && !m.store.isAssigned(r.token())
                    && !orphans.contains(r.token())) {
                orphans.add(r.token());
                lost |= !r.state().isBefore(RequestState.ASSIGNED);
            }
        }
        return lost;
    }

    /** Restores the assignments whose resolver still exists; returns the open requests whose resolver is gone. */
    private static List<RequestToken> readAssignments(JsonObject assignments, RequestManager m) {
        List<RequestToken> orphans = new ArrayList<>();
        for (String resolverId : assignments.keySet()) {
            Optional<Resolver> resolver = m.resolver(resolverId);
            for (RequestToken t : RequestToken.fromJson(assignments.get(resolverId))) {
                Optional<Request> req = m.get(t);
                if (req.isEmpty()) {
                    continue;
                }
                if (resolver.isPresent()) {
                    m.store.restoreAssignment(t, resolver.get());
                } else if (req.get().state().isBefore(RequestState.COMPLETED)) {
                    orphans.add(t); // a finished one just waits for pickup
                }
            }
        }
        return orphans;
    }

    private static Optional<RetryingResolver> retrying(RequestManager m) {
        return m.resolver(RetryingResolver.ID).map(RetryingResolver.class::cast);
    }

    private static Optional<PlayerResolver> player(RequestManager m) {
        return m.resolver(PlayerResolver.ID).map(PlayerResolver.class::cast);
    }

    private static JsonObject request(Request r) {
        JsonObject o = new JsonObject();
        o.addProperty("token", r.token().id().toString());
        o.addProperty("requester", r.requester().value());
        o.add("requestable", RequestableJson.write(r.requestable()));
        o.addProperty("state", r.state().name());
        o.add(
                "parent",
                r.parent()
                        .<JsonElement>map(p -> new JsonPrimitive(p.id().toString()))
                        .orElse(JsonNull.INSTANCE));
        o.add("children", RequestToken.toJson(r.children()));
        JsonArray deliveries = new JsonArray();
        for (ItemAmount a : r.deliveries()) {
            JsonObject d = new JsonObject();
            d.addProperty("item", a.item().id());
            d.addProperty("count", a.count());
            deliveries.add(d);
        }
        o.add("deliveries", deliveries);
        o.addProperty("citizenId", r.citizenId());
        if (r.deliveredToCitizen()) {
            o.addProperty("deliveredToCitizen", true);
        }
        if (r.async()) {
            o.addProperty("async", true);
        }
        JsonArray blacklist = new JsonArray();
        r.blacklist().stream().sorted().forEach(blacklist::add);
        o.add("blacklist", blacklist);
        return o;
    }

    /**
     * The saved request; empty when its token, what it asks for or its state cannot be read, or is unknown to this
     * build. A missing optional value takes its default (§ 5); a malformed parent or child link is repaired by
     * {@link SavedRequests}.
     */
    private static Optional<Request> readRequest(JsonObject o) {
        Optional<RequestToken> token = RequestToken.parse(o.get("token"));
        Optional<Requestable> requestable =
                o.get("requestable") instanceof JsonObject saved ? RequestableJson.read(saved) : Optional.empty();
        Optional<RequestState> state = SavedJson.enumOf(RequestState.class, o.get("state"));
        if (token.isEmpty() || requestable.isEmpty() || state.isEmpty()) {
            return Optional.empty();
        }
        Request r = new Request(
                token.get(),
                new RequesterId(SavedJson.stringOr(o.get("requester"), "")),
                requestable.get(),
                SavedJson.intOr(o.get("citizenId"), Request.NO_CITIZEN));
        r.setState(state.get());
        RequestToken.parse(o.get("parent")).ifPresent(r::setParent);
        RequestToken.fromJson(o.get("children")).forEach(r::addChild);
        readDeliveries(SavedJson.arrayOr(o.get("deliveries")), r);
        r.setDeliveredToCitizen(SavedJson.boolOr(o.get("deliveredToCitizen"), false));
        r.setAsync(SavedJson.boolOr(o.get("async"), false));
        r.setBlacklist(readBlacklist(SavedJson.arrayOr(o.get("blacklist"))));
        return Optional.of(r);
    }

    /** Adds the saved deliveries to {@code r}; one without an item or a positive count is dropped. */
    private static void readDeliveries(JsonArray saved, Request r) {
        for (JsonElement el : saved) {
            JsonObject d = SavedJson.objectOr(el);
            String item = SavedJson.stringOr(d.get("item"), "");
            int count = SavedJson.intOr(d.get("count"), 0);
            if (!item.isEmpty() && count > 0) {
                r.addDelivery(new ItemAmount(new ItemKey(item), count));
            }
        }
    }

    /** The saved resolver ids; an entry that is not a string is dropped. */
    private static Set<String> readBlacklist(JsonArray saved) {
        Set<String> blacklist = new HashSet<>();
        for (JsonElement el : saved) {
            String resolver = SavedJson.stringOr(el, "");
            if (!resolver.isEmpty()) {
                blacklist.add(resolver);
            }
        }
        return blacklist;
    }

    private static JsonObject counts(Map<RequestToken, Integer> map, Set<RequestToken> keys) {
        JsonObject o = new JsonObject();
        map.forEach((t, n) -> {
            if (keys.contains(t)) {
                o.addProperty(t.id().toString(), n);
            }
        });
        return o;
    }

    /** Saved counts by token; an entry whose key is not a token or whose value is not a number is dropped. */
    private static Map<RequestToken, Integer> readCounts(JsonObject o) {
        Map<RequestToken, Integer> out = new LinkedHashMap<>();
        for (String key : o.keySet()) {
            Optional<RequestToken> token = RequestToken.parse(new JsonPrimitive(key));
            if (token.isPresent() && o.get(key) instanceof JsonPrimitive n && n.isNumber()) {
                out.put(token.get(), n.getAsInt());
            }
        }
        return out;
    }
}
