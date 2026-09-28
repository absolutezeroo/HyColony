package dev.hycolony.core.farming.hut;

import com.google.gson.JsonObject;
import dev.hycolony.core.building.Building;
import dev.hycolony.core.building.PersistentModule;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.farming.field.FieldJson;
import dev.hycolony.core.logistics.pickup.KeepRule;
import dev.hycolony.core.logistics.pickup.KeepsItems;
import java.util.List;

/**
 * The farmer hut's settings (MC BuildingFarmer FARMER_SETTINGS): {@code fertilize}, "Request Fertilizer", on by
 * default. While it is on, one fertilizer stays with the farmer.
 */
public final class FarmerSettingsModule implements PersistentModule, KeepsItems {
    private boolean fertilize = true;

    /** MC FERTILIZE. */
    public boolean fertilize() {
        return fertilize;
    }

    public void setFertilize(boolean on) {
        fertilize = on;
    }

    /** Deviation from MC: one fertilizer tool (Hytale), where MC asks for compost or bone meal and keeps none. */
    @Override
    public List<KeepRule> keepRules(Colony colony, Building building) {
        return fertilize
                ? List.of(new KeepRule(colony.context().ports().farming().fertilizerItem()::equals, 1, true))
                : List.of();
    }

    @Override
    public void write(JsonObject out) {
        out.addProperty("fertilize", fertilize);
    }

    @Override
    public void read(JsonObject in) {
        fertilize = FieldJson.bool(in.get("fertilize")).orElse(true);
    }
}
