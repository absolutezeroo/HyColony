package dev.hycolony.core.request;

import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.request.model.RequesterId;

public interface Requester {
    RequesterId requesterId();

    BlockPos location();

    String displayName();

    void onRequestComplete(RequestManager manager, Request request);

    void onRequestCancelled(RequestManager manager, Request request);
}
