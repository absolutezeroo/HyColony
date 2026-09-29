package dev.hycolony.core.construction.workorder;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** A colony's work orders to and from JSON (the top id is saved beside them, see {@link WorkManager#topId()}). */
public final class WorkOrderSerializer {
    private static final System.Logger LOG = System.getLogger(WorkOrderSerializer.class.getName());

    private WorkOrderSerializer() {}

    public static JsonArray write(WorkManager work) {
        JsonArray arr = new JsonArray();
        work.all().forEach(o -> arr.add(o.write()));
        return arr;
    }

    /**
     * Replaces the manager's orders with the saved ones; an order that cannot be read is left out and logged (§ 5).
     * Returns true when one was, so that the colony is saved again without it.
     */
    public static boolean read(JsonArray arr, WorkManager work) {
        List<WorkOrder> orders = new ArrayList<>();
        boolean skipped = false;
        for (JsonElement el : arr) {
            Optional<WorkOrder> order = el instanceof JsonObject o ? WorkOrder.read(o) : Optional.empty();
            order.ifPresent(orders::add);
            if (order.isEmpty()) {
                LOG.log(System.Logger.Level.WARNING, "Saved work order skipped, unreadable: {0}", el);
                skipped = true;
            }
        }
        work.restore(orders);
        return skipped;
    }
}
