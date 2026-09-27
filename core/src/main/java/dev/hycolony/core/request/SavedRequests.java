package dev.hycolony.core.request;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.hycolony.core.request.model.RequestToken;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Tolerant read of the saved request list: a request this build cannot read (unknown type or state) is skipped, then
 * every request whose parent or one of whose children is missing is dropped too, until each remaining family is whole.
 * No request points to a missing one and no parent waits on a child that will never come; the requesters ask again.
 */
final class SavedRequests {
    private static final System.Logger LOG = System.getLogger(SavedRequests.class.getName());

    private SavedRequests() {}

    /** The loadable requests by token, in save order; one log line per request left out. */
    static Map<RequestToken, Request> read(JsonArray requests, Function<JsonObject, Optional<Request>> reader) {
        Map<RequestToken, Request> loaded = new LinkedHashMap<>();
        for (JsonElement el : requests) {
            JsonObject saved = el.getAsJsonObject();
            reader.apply(saved)
                    .ifPresentOrElse(
                            r -> loaded.put(r.token(), r),
                            () -> LOG.log(
                                    System.Logger.Level.WARNING,
                                    "Saved request {0} skipped: unknown type or state ({1}, {2})",
                                    saved.get("token").getAsString(),
                                    saved.getAsJsonObject("requestable").get("type"),
                                    saved.get("state")));
        }
        // A dropped request breaks its own parent and children; each pass drops one or more, or is the last.
        boolean dropped = true;
        while (dropped) {
            dropped = loaded.values().removeIf(r -> isIncomplete(r, loaded));
        }
        return loaded;
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
