package dev.hycolony.core.citizen.happiness;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.hycolony.core.kernel.persist.SavedJson;

/**
 * A citizen's happiness in its save (MC CitizenHappinessHandler.write/read with persist): each modifier's id, the
 * last value of its function, its days and, for an expiring one, its weight, value and period. Reading is tolerant: an
 * unknown or invalid entry is skipped, a missing value takes its default (CLAUDE.md § 5).
 */
public final class HappinessJson {
    private HappinessJson() {}

    /** Every modifier of {@code happiness}, in its order. */
    public static JsonArray write(CitizenHappiness happiness) {
        JsonArray out = new JsonArray();
        for (HappinessModifier m : happiness.modifiers()) {
            JsonObject o = new JsonObject();
            o.addProperty("id", m.id());
            o.addProperty("days", m.days());
            switch (m) {
                case StaticModifier s -> o.addProperty("last", s.lastFactor());
                case TimeBasedModifier t -> o.addProperty("last", t.lastBase());
                case ExpirationModifier e -> {
                    o.addProperty("weight", e.weight());
                    o.addProperty("value", e.value());
                    o.addProperty("period", e.period());
                }
            }
            out.add(o);
        }
        return out;
    }

    /**
     * Restores {@code saved} into {@code happiness}: a modifier every citizen has gets its days and last value back; an
     * expiring one of a valid id is added (MC HappinessRegistry.loadFrom); anything else is skipped.
     */
    public static void read(JsonArray saved, CitizenHappiness happiness) {
        for (JsonElement el : saved) {
            if (!(el instanceof JsonObject o)) {
                continue;
            }
            String id = SavedJson.stringOr(o.get("id"), "");
            int days = SavedJson.intOr(o.get("days"), 0);
            HappinessModifier known = happiness.get(id).orElse(null);
            switch (known) {
                case StaticModifier s -> s.restoreLast(SavedJson.doubleOr(o.get("last"), 0));
                case TimeBasedModifier t -> t.restore(days, SavedJson.doubleOr(o.get("last"), 0));
                case ExpirationModifier e -> e.restoreDays(days);
                case null -> readExpiring(o, id, days, happiness);
            }
        }
    }

    /** An expiring modifier added by an event (damage, great food…), when its id is one MC knows. */
    private static void readExpiring(JsonObject o, String id, int days, CitizenHappiness happiness) {
        if (!HappinessIds.VALID.contains(id) || !o.has("period")) {
            return;
        }
        ExpirationModifier e = new ExpirationModifier(
                id,
                SavedJson.doubleOr(o.get("weight"), 1.0),
                SavedJson.doubleOr(o.get("value"), 1.0),
                Math.max(0, SavedJson.intOr(o.get("period"), 0)));
        e.restoreDays(days);
        happiness.add(e);
    }
}
