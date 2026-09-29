package dev.hycolony.core.request.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** A request's identity. Saved as its UUID string. */
public record RequestToken(UUID id) {
    public RequestToken {
        Objects.requireNonNull(id, "id");
    }

    public static RequestToken random() {
        return new RequestToken(UUID.randomUUID());
    }

    /** {@code tokens} as a JSON array of UUID strings, in order. */
    public static JsonArray toJson(Collection<RequestToken> tokens) {
        JsonArray out = new JsonArray();
        tokens.forEach(t -> out.add(t.id().toString()));
        return out;
    }

    /** The token saved as {@code saved}; empty unless it is a UUID string (CLAUDE.md § 5). */
    public static Optional<RequestToken> parse(@Nullable JsonElement saved) {
        if (saved instanceof JsonPrimitive p && p.isString()) {
            try {
                return Optional.of(new RequestToken(UUID.fromString(p.getAsString())));
            } catch (IllegalArgumentException _) {
                // tolerant read (CLAUDE.md § 5)
            }
        }
        return Optional.empty();
    }

    /**
     * The tokens of a saved array, in order; an entry that is not a UUID string is dropped (the request it named
     * cannot be found anyway), and anything but an array reads as none (CLAUDE.md § 5).
     */
    public static List<RequestToken> fromJson(@Nullable JsonElement saved) {
        List<RequestToken> out = new ArrayList<>();
        if (!(saved instanceof JsonArray array)) {
            return out;
        }
        for (JsonElement e : array) {
            parse(e).ifPresent(out::add);
        }
        return out;
    }
}
