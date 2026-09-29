package dev.hycolony.core.farming.field;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import dev.hycolony.core.kernel.BlockPos;
import java.util.Optional;
import java.util.OptionalInt;
import org.jspecify.annotations.Nullable;

/** Tolerant reads of the farmer's saved values (CLAUDE.md § 5): a missing or unreadable value reads as empty. */
public final class FieldJson {
    private FieldJson() {}

    /** A whole number, empty if {@code el} is absent or not a number. */
    public static OptionalInt integer(@Nullable JsonElement el) {
        return el instanceof JsonPrimitive p && p.isNumber() ? OptionalInt.of(p.getAsInt()) : OptionalInt.empty();
    }

    /** A boolean, empty if {@code el} is absent or not a boolean. */
    public static Optional<Boolean> bool(@Nullable JsonElement el) {
        return el instanceof JsonPrimitive p && p.isBoolean() ? Optional.of(p.getAsBoolean()) : Optional.empty();
    }

    /** {@code p} saved as {@code [x, y, z]}. */
    public static JsonArray pos(BlockPos p) {
        JsonArray a = new JsonArray();
        a.add(p.x());
        a.add(p.y());
        a.add(p.z());
        return a;
    }

    /** A position saved as {@code [x, y, z]}, empty if absent or malformed. */
    public static Optional<BlockPos> pos(@Nullable JsonElement el) {
        if (!(el instanceof JsonArray a) || a.size() != 3) {
            return Optional.empty();
        }
        OptionalInt x = integer(a.get(0));
        OptionalInt y = integer(a.get(1));
        OptionalInt z = integer(a.get(2));
        return x.isPresent() && y.isPresent() && z.isPresent()
                ? Optional.of(new BlockPos(x.getAsInt(), y.getAsInt(), z.getAsInt()))
                : Optional.empty();
    }
}
