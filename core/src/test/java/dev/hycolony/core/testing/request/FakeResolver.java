package dev.hycolony.core.testing.request;

import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.request.Request;
import dev.hycolony.core.request.RequestManager;
import dev.hycolony.core.request.Resolver;
import dev.hycolony.core.request.model.RequesterId;

/**
 * A resolver double standing at the origin, as good as any other ({@code suitability} 0), told nothing about the
 * children it asks for; a test says what it handles, takes and does.
 */
public abstract class FakeResolver implements Resolver {
    private final String id;
    private final int priority;

    protected FakeResolver(String id, int priority) {
        this.id = id;
        this.priority = priority;
    }

    @Override
    public String resolverId() {
        return id;
    }

    @Override
    public int priority() {
        return priority;
    }

    @Override
    public double suitability(RequestManager m, Request r) {
        return 0;
    }

    @Override
    public RequesterId requesterId() {
        return new RequesterId("resolver:" + id);
    }

    @Override
    public BlockPos location() {
        return new BlockPos(0, 64, 0);
    }

    @Override
    public String displayName() {
        return id;
    }

    @Override
    public void onRequestComplete(RequestManager manager, Request request) {}

    @Override
    public void onRequestCancelled(RequestManager manager, Request request) {}
}
