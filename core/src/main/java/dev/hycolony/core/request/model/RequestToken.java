package dev.hycolony.core.request.model;

import java.util.Objects;
import java.util.UUID;

public record RequestToken(UUID id) {
    public RequestToken {
        Objects.requireNonNull(id, "id");
    }

    public static RequestToken random() {
        return new RequestToken(UUID.randomUUID());
    }
}
