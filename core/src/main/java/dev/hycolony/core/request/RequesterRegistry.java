package dev.hycolony.core.request;

import java.util.Optional;

public interface RequesterRegistry {
    Optional<Requester> find(RequesterId id);
}
