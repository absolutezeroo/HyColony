package dev.hycolony.core.request;

import dev.hycolony.core.request.model.RequesterId;
import java.util.Optional;

public interface RequesterRegistry {
    Optional<Requester> find(RequesterId id);
}
