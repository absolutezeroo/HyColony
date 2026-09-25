package dev.hycolony.core.request;

import dev.hycolony.core.kernel.BlockPos;

public interface Requester {
    RequesterId requesterId();

    BlockPos location();

    String displayName();

    void onRequestComplete(RequestManager manager, Request request);

    void onRequestCancelled(RequestManager manager, Request request);
}
