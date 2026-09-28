package dev.hycolony.core.farming.hut;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.hycolony.core.farming.field.FarmField;
import dev.hycolony.core.farming.field.FieldJson;
import dev.hycolony.core.farming.field.FieldRegistry;
import dev.hycolony.core.kernel.BlockPos;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import org.jspecify.annotations.Nullable;

/**
 * Which of its fields a farmer hut works next (MC BuildingExtensionsModule.getExtensionToWorkOn): the current one while
 * it exists, else a field never worked, else the one worked the longest ago before today. A field that got a pass is
 * left until the next colony day.
 */
final class FieldChoice {
    private @Nullable BlockPos current;
    /** The colony day each field last got a pass (MC checkedExtensions). */
    private final Map<BlockPos, Integer> checked = new HashMap<>();

    /** MC getExtensionToWorkOn; it makes the chosen field current. Empty when every field is done for today. */
    Optional<FarmField> pick(FieldRegistry fields, BlockPos hut, int today) {
        List<FarmField> owned = fields.ownedBy(hut);
        Optional<FarmField> kept =
                owned.stream().filter(f -> f.pos().equals(current)).findFirst();
        if (kept.isPresent()) {
            return kept;
        }
        Optional<FarmField> next = owned.stream()
                .filter(f -> !checked.containsKey(f.pos()))
                .findFirst()
                .or(() -> owned.stream().filter(f -> day(f) < today).min((a, b) -> Integer.compare(day(a), day(b))));
        current = next.map(FarmField::pos).orElse(null);
        return next;
    }

    /** The day {@code f} last got a pass; only called on checked fields. */
    private int day(FarmField f) {
        return checked.getOrDefault(f.pos(), Integer.MAX_VALUE);
    }

    /** The current field, if it still exists among the hut's fields. */
    Optional<FarmField> current(FieldRegistry fields, BlockPos hut) {
        return fields.ownedBy(hut).stream().filter(f -> f.pos().equals(current)).findFirst();
    }

    /** MC resetCurrentExtension: the current field is done for {@code today}; no field is current any more. */
    void reset(int today) {
        if (current != null) {
            checked.put(current, today);
        }
        current = null;
    }

    /**
     * Saves the current field and the checked days. Deviation from MC: they come back after a reload, where MC writes
     * and reads them under different keys and loses them.
     */
    void write(JsonObject out) {
        if (current != null) {
            out.add("current", pos(current));
        }
        JsonArray list = new JsonArray();
        checked.forEach((pos, day) -> {
            JsonObject e = new JsonObject();
            e.add("pos", pos(pos));
            e.addProperty("day", day);
            list.add(e);
        });
        out.add("checked", list);
    }

    void read(JsonObject in) {
        current = FieldJson.pos(in.get("current")).orElse(null);
        checked.clear();
        if (in.get("checked") instanceof JsonArray list) {
            for (JsonElement el : list) {
                if (el instanceof JsonObject e) {
                    OptionalInt day = FieldJson.integer(e.get("day"));
                    FieldJson.pos(e.get("pos"))
                            .filter(p -> day.isPresent())
                            .ifPresent(p -> checked.put(p, day.getAsInt()));
                }
            }
        }
    }

    private static JsonArray pos(BlockPos p) {
        JsonArray a = new JsonArray();
        a.add(p.x());
        a.add(p.y());
        a.add(p.z());
        return a;
    }
}
