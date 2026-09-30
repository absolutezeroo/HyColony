package dev.hycolony.core.colony;

import dev.hycolony.core.kernel.event.EventBus;
import dev.hycolony.core.request.Request;
import dev.hycolony.core.request.model.RequestState;
import dev.hycolony.core.request.model.RequestStateChanged;
import java.util.function.BiConsumer;

/**
 * Posts each state change of a colony's requests on the world's bus, only while someone listens: most requests change
 * state several times in a tick.
 */
final class RequestStatePoster implements BiConsumer<Request, RequestState> {
    private final EventBus bus;
    private final int colonyId;

    RequestStatePoster(EventBus bus, int colonyId) {
        this.bus = bus;
        this.colonyId = colonyId;
    }

    @Override
    public void accept(Request request, RequestState from) {
        if (bus.hasListeners(RequestStateChanged.class)) {
            bus.post(new RequestStateChanged(colonyId, request.token(), from, request.state()));
        }
    }
}
