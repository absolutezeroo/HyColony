package dev.hycolony.core.request;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.item.ToolType;
import dev.hycolony.core.request.resolver.PlayerResolver;
import dev.hycolony.core.request.resolver.RetryingResolver;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * RequestManager <-> JSON: requests, assignments (resolverId -> tokens), retrying and player state. Read it after
 * the providers (buildings) re-registered; a request whose resolver is gone is reassigned instead of dropped.
 */
public final class RequestSerializer {
    private RequestSerializer() {}

    public static JsonObject write(RequestManager m) {
        JsonObject o = new JsonObject();
        JsonArray requests = new JsonArray();
        m.all().forEach(r -> requests.add(request(r)));
        o.add("requests", requests);

        JsonObject assignments = new JsonObject();
        m.assignments().forEach((id, tokens) -> assignments.add(id, tokens(tokens)));
        o.add("assignments", assignments);

        retrying(m).ifPresent(r -> {
            JsonObject ro = new JsonObject();
            ro.add("delays", counts(r.delays(), r.delays().keySet()));
            ro.add("tries", counts(r.tries(), r.delays().keySet())); // stale tries are pruned at the next tick
            o.add("retrying", ro);
        });
        player(m).ifPresent(p -> o.add("player", tokens(p.open().stream().map(Request::token).toList())));
        return o;
    }

    public static void read(JsonObject o, RequestManager m) {
        if (!o.has("requests")) {
            return; // schema v1 placeholder
        }
        for (JsonElement el : o.getAsJsonArray("requests")) {
            m.restore(readRequest(el.getAsJsonObject()));
        }

        List<RequestToken> orphans = new ArrayList<>();
        JsonObject assignments = o.getAsJsonObject("assignments");
        for (String resolverId : assignments.keySet()) {
            Optional<Resolver> resolver = m.resolver(resolverId);
            for (RequestToken t : readTokens(assignments.getAsJsonArray(resolverId))) {
                Optional<Request> req = m.get(t);
                if (req.isEmpty()) {
                    continue;
                }
                if (resolver.isPresent()) {
                    m.restoreAssignment(t, resolver.get());
                } else if (req.get().state().ordinal() < RequestState.COMPLETED.ordinal()) {
                    orphans.add(t); // a finished one just waits for pickup
                }
            }
        }

        // Membership comes from the assignments; the saved resolver state only supplies the numbers.
        JsonObject ro = o.has("retrying") ? o.getAsJsonObject("retrying") : new JsonObject();
        Map<RequestToken, Integer> savedDelays = readCounts(ro.getAsJsonObject("delays"));
        Map<RequestToken, Integer> savedTries = readCounts(ro.getAsJsonObject("tries"));
        retrying(m).ifPresent(r -> {
            Map<RequestToken, Integer> delays = new LinkedHashMap<>();
            Map<RequestToken, Integer> tries = new LinkedHashMap<>();
            for (Request req : m.assignedTo(RetryingResolver.ID)) {
                delays.put(req.token(), savedDelays.getOrDefault(req.token(), RetryingResolver.DELAY_TICKS));
                tries.put(req.token(), savedTries.getOrDefault(req.token(), 1));
            }
            r.restore(delays, tries);
        });
        player(m).ifPresent(p -> m.assignedTo(PlayerResolver.ID).forEach(p::restore));

        orphans.forEach(m::reassignLoaded);
    }

    private static Optional<RetryingResolver> retrying(RequestManager m) {
        return m.resolver(RetryingResolver.ID).map(RetryingResolver.class::cast);
    }

    private static Optional<PlayerResolver> player(RequestManager m) {
        return m.resolver(PlayerResolver.ID).map(PlayerResolver.class::cast);
    }

    // ---- requests ----

    private static JsonObject request(Request r) {
        JsonObject o = new JsonObject();
        o.addProperty("token", r.token().id().toString());
        o.addProperty("requester", r.requester().value());
        o.add("requestable", requestable(r.requestable()));
        o.addProperty("state", r.state().name());
        o.add("parent", r.parent().<JsonElement>map(p -> new JsonPrimitive(p.id().toString()))
                .orElse(JsonNull.INSTANCE));
        o.add("children", tokens(r.children()));
        JsonArray deliveries = new JsonArray();
        for (ItemAmount a : r.deliveries()) {
            JsonObject d = new JsonObject();
            d.addProperty("item", a.item().id());
            d.addProperty("count", a.count());
            deliveries.add(d);
        }
        o.add("deliveries", deliveries);
        o.addProperty("citizenId", r.citizenId());
        JsonArray blacklist = new JsonArray();
        r.blacklist().stream().sorted().forEach(blacklist::add);
        o.add("blacklist", blacklist);
        return o;
    }

    private static Request readRequest(JsonObject o) {
        Request r = new Request(token(o.get("token").getAsString()), new RequesterId(o.get("requester").getAsString()),
                readRequestable(o.getAsJsonObject("requestable")), o.get("citizenId").getAsInt());
        r.setState(RequestState.valueOf(o.get("state").getAsString()));
        JsonElement parent = o.get("parent");
        if (parent != null && !parent.isJsonNull()) {
            r.setParent(token(parent.getAsString()));
        }
        readTokens(o.getAsJsonArray("children")).forEach(r::addChild);
        for (JsonElement el : o.getAsJsonArray("deliveries")) {
            JsonObject d = el.getAsJsonObject();
            r.addDelivery(new ItemAmount(new ItemKey(d.get("item").getAsString()), d.get("count").getAsInt()));
        }
        Set<String> blacklist = new HashSet<>();
        for (JsonElement el : o.getAsJsonArray("blacklist")) {
            blacklist.add(el.getAsString());
        }
        r.setBlacklist(blacklist);
        return r;
    }

    private static JsonObject requestable(Deliverable d) {
        JsonObject o = new JsonObject();
        switch (d) {
            case StackRequest s -> {
                o.addProperty("type", "stack");
                o.addProperty("item", s.item().id());
                o.addProperty("count", s.count());
                o.addProperty("minCount", s.minCount());
                o.addProperty("canBeResolvedByBuilding", s.canBeResolvedByBuilding());
            }
            case ToolRequest t -> {
                o.addProperty("type", "tool");
                o.addProperty("tool", t.type().name());
                o.addProperty("minLevel", t.minLevel());
                o.addProperty("maxLevel", t.maxLevel());
            }
        }
        return o;
    }

    private static Deliverable readRequestable(JsonObject o) {
        String type = o.get("type").getAsString();
        return switch (type) {
            case "stack" -> new StackRequest(new ItemKey(o.get("item").getAsString()), o.get("count").getAsInt(),
                    o.get("minCount").getAsInt(), o.get("canBeResolvedByBuilding").getAsBoolean());
            case "tool" -> new ToolRequest(ToolType.valueOf(o.get("tool").getAsString()), o.get("minLevel").getAsInt(),
                    o.get("maxLevel").getAsInt());
            default -> throw new IllegalArgumentException("Unknown requestable type: " + type);
        };
    }

    // ---- tokens ----

    private static RequestToken token(String s) {
        return new RequestToken(UUID.fromString(s));
    }

    private static JsonArray tokens(Collection<RequestToken> tokens) {
        JsonArray a = new JsonArray();
        tokens.forEach(t -> a.add(t.id().toString()));
        return a;
    }

    private static List<RequestToken> readTokens(JsonArray a) {
        List<RequestToken> out = new ArrayList<>(a.size());
        for (JsonElement el : a) {
            out.add(token(el.getAsString()));
        }
        return out;
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

    private static Map<RequestToken, Integer> readCounts(JsonObject o) {
        Map<RequestToken, Integer> out = new LinkedHashMap<>();
        if (o != null) {
            for (String key : o.keySet()) {
                out.put(token(key), o.get(key).getAsInt());
            }
        }
        return out;
    }
}
