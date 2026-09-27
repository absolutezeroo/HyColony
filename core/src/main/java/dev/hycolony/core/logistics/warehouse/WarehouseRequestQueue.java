package dev.hycolony.core.logistics.warehouse;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.hycolony.core.building.PersistentModule;
import dev.hycolony.core.request.model.RequestToken;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** The deliveries and pickups waiting for a courier at this warehouse (MC {@code WarehouseRequestQueueModule}). */
public final class WarehouseRequestQueue implements PersistentModule {
    private final List<RequestToken> tokens = new ArrayList<>();

    /** MC addRequest: appends {@code token} at the end of the queue. */
    public void add(RequestToken token) {
        tokens.add(token);
    }

    /** The queue itself, mutable, oldest first (MC getMutableRequestList). */
    public List<RequestToken> tokens() {
        return tokens;
    }

    @Override
    public void write(JsonObject out) {
        JsonArray arr = new JsonArray();
        tokens.forEach(t -> arr.add(t.id().toString()));
        out.add("requests", arr);
    }

    /** A token that is not a UUID is dropped: the request it named cannot be found anyway. */
    @Override
    public void read(JsonObject in) {
        tokens.clear();
        if (!in.has("requests")) {
            return;
        }
        for (JsonElement e : in.getAsJsonArray("requests")) {
            try {
                tokens.add(new RequestToken(UUID.fromString(e.getAsString())));
            } catch (IllegalArgumentException | UnsupportedOperationException | IllegalStateException _) {
                // tolerant read (CLAUDE.md § 5)
            }
        }
    }
}
