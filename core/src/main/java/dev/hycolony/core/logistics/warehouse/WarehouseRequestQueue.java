package dev.hycolony.core.logistics.warehouse;

import com.google.gson.JsonObject;
import dev.hycolony.core.building.Building;
import dev.hycolony.core.building.module.ModuleTab;
import dev.hycolony.core.building.module.PersistentModule;
import dev.hycolony.core.building.module.ProvidesTab;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.request.model.RequestToken;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** The deliveries and pickups waiting for a courier at this warehouse (MC {@code WarehouseRequestQueueModule}). */
public final class WarehouseRequestQueue implements PersistentModule, ProvidesTab {
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
        out.add("requests", RequestToken.toJson(tokens));
    }

    /** See {@link RequestToken#fromJson}: a token that is not a UUID is dropped. */
    @Override
    public void read(JsonObject in) {
        tokens.clear();
        tokens.addAll(RequestToken.fromJson(in.get("requests")));
    }

    /** The warehouse's Tasks tab (MC WarehouseRequestTaskModuleView). */
    @Override
    public ModuleTab tab(Colony colony, Building building, UUID viewer) {
        return new WarehouseTasksView(TaskRows.of(colony, tokens));
    }
}
