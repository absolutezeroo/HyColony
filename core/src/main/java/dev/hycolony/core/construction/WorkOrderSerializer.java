package dev.hycolony.core.construction;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import java.util.ArrayList;
import java.util.List;

/** A colony's work orders to and from JSON (the top id is saved beside them, see {@link WorkManager#topId()}). */
public final class WorkOrderSerializer {
    private WorkOrderSerializer() {}

    public static JsonArray write(WorkManager work) {
        JsonArray arr = new JsonArray();
        work.all().forEach(o -> arr.add(o.write()));
        return arr;
    }

    /** Replaces the manager's orders with the saved ones. */
    public static void read(JsonArray arr, WorkManager work) {
        List<WorkOrder> orders = new ArrayList<>();
        for (JsonElement el : arr) {
            orders.add(WorkOrder.read(el.getAsJsonObject()));
        }
        work.restore(orders);
    }
}
