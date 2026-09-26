package dev.hycolony.core.request.model;

import java.util.Objects;

public record RequesterId(String value) {
    public RequesterId {
        Objects.requireNonNull(value, "value");
    }
}
