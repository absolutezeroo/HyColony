package dev.hycolony.core.request;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.hycolony.core.request.model.RequestToken;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Tolerant read of the saved request list: a request this build cannot read (unknown type or state) is skipped, then
 * every request whose parent or one of whose children is missing is dropped too, until each remaining family is whole.
 * No request points to a missing one and no parent waits on a child that will never come; the requesters ask again. A
 * corrupted save is repaired the same way: a request whose parent chain comes back to itself, or whose parent and
 * child links do not match both ways, is dropped with its family.
 */
final class SavedRequests {
    private static final System.Logger LOG = System.getLogger(SavedRequests.class.getName());

    private SavedRequests() {}

    /**
     * The loadable requests by token, in save order.
     *
     * @param repaired whether broken parent or child links were dropped, so the save must be rewritten; requests left
     *     out for an unknown type or state do not count, but the next save drops them anyway: their requesters ask
     *     again once their pack is back
     */
    record Loaded(Map<RequestToken, Request> requests, boolean repaired) {}

    /** The loadable requests and whether links were repaired; one log line per request left out. */
    static Loaded read(JsonArray requests, Function<JsonObject, Optional<Request>> reader) {
        Map<RequestToken, Request> loaded = new LinkedHashMap<>();
        for (JsonElement el : requests) {
            JsonObject saved = el instanceof JsonObject o ? o : new JsonObject();
            reader.apply(saved)
                    .ifPresentOrElse(
                            r -> loaded.put(r.token(), r),
                            () -> LOG.log(
                                    System.Logger.Level.WARNING,
                                    "Saved request {0} skipped: unreadable, or of an unknown type or state ({1}, {2})",
                                    saved.get("token"),
                                    saved.get("requestable"),
                                    saved.get("state")));
        }
        // A dropped request breaks its own parent and children; each pass drops one or more, or is the last.
        boolean repaired = false;
        boolean dropped = true;
        while (dropped) {
            dropped = loaded.values().removeIf(r -> isIncomplete(r, loaded));
            boolean broken = loaded.values().removeIf(r -> hasBrokenLinks(r, loaded));
            repaired |= broken;
            dropped |= broken;
        }
        return new Loaded(loaded, repaired);
    }

    /** A parent chain that loops back to {@code r}, or a parent or child link the other side does not share. */
    private static boolean hasBrokenLinks(Request r, Map<RequestToken, Request> loaded) {
        Optional<RequestToken> self = Optional.of(r.token());
        boolean broken = isInParentCycle(r, loaded)
                || r.parent()
                        .map(loaded::get)
                        .map(p -> !p.children().contains(r.token()))
                        .orElse(false)
                || r.children().stream()
                        .map(loaded::get)
                        .anyMatch(c -> c != null && !c.parent().equals(self));
        if (broken) {
            LOG.log(
                    System.Logger.Level.WARNING,
                    "Saved request {0} skipped: its parent and child links are broken or loop",
                    r.token());
        }
        return broken;
    }

    /** Whether climbing the parents from {@code r} comes back to it; a loop above {@code r} is left to its members. */
    private static boolean isInParentCycle(Request r, Map<RequestToken, Request> loaded) {
        Set<RequestToken> climbed = new HashSet<>();
        Optional<RequestToken> up = r.parent();
        while (up.isPresent() && climbed.add(up.get())) {
            if (up.get().equals(r.token())) {
                return true;
            }
            up = Optional.ofNullable(loaded.get(up.get())).flatMap(Request::parent);
        }
        return false;
    }

    private static boolean isIncomplete(Request r, Map<RequestToken, Request> loaded) {
        boolean incomplete = r.parent().map(p -> !loaded.containsKey(p)).orElse(false)
                || !loaded.keySet().containsAll(r.children());
        if (incomplete) {
            LOG.log(
                    System.Logger.Level.WARNING,
                    "Saved request {0} skipped: part of its family did not load",
                    r.token());
        }
        return incomplete;
    }
}
