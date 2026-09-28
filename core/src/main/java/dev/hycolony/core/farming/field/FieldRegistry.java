package dev.hycolony.core.farming.field;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import dev.hycolony.core.kernel.BlockPos;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The colony's fields (MC RegisteredStructureManager building extensions of type farm field), keyed by field block
 * position, in registration order: the order MC's automatic claim walks them in.
 */
public final class FieldRegistry {
    private final Map<BlockPos, FarmField> fields = new LinkedHashMap<>();

    /** MC addBuildingExtension (putIfAbsent): true when {@code pos} was not a field yet. */
    public boolean add(BlockPos pos) {
        return fields.putIfAbsent(pos, new FarmField(pos)) == null;
    }

    public Optional<FarmField> get(BlockPos pos) {
        return Optional.ofNullable(fields.get(pos));
    }

    /** MC removeBuildingExtension: the field removed, empty if there was none. */
    public Optional<FarmField> remove(BlockPos pos) {
        return Optional.ofNullable(fields.remove(pos));
    }

    /** Every field, in registration order. */
    public List<FarmField> all() {
        return List.copyOf(fields.values());
    }

    /** MC getFreeExtensions: the fields no hut works, in registration order. */
    public List<FarmField> free() {
        return fields.values().stream().filter(f -> !f.isTaken()).toList();
    }

    /** MC getOwnedExtensions: the fields {@code hut} works. */
    public List<FarmField> ownedBy(BlockPos hut) {
        return fields.values().stream()
                .filter(f -> f.owner().filter(hut::equals).isPresent())
                .toList();
    }

    /**
     * MC cleanUpBuildings, every colony slow tick: a field whose position is loaded and is outside the colony, or whose
     * field block is gone, is removed. An unloaded field is kept, whatever the other tests say. True if a field was
     * removed.
     */
    public boolean cleanUp(Predicate<BlockPos> loaded, Predicate<BlockPos> inColony, Predicate<BlockPos> isFieldBlock) {
        return fields.values()
                .removeIf(f -> loaded.test(f.pos()) && (!inColony.test(f.pos()) || !isFieldBlock.test(f.pos())));
    }

    /**
     * MC colony load repair: an owner that is not one of {@code huts} (removed, or no longer a farmer) is reset.
     * True when a field changed.
     */
    public boolean freeOwnersNotIn(Set<BlockPos> huts) {
        boolean changed = false;
        for (FarmField f : fields.values()) {
            if (f.owner().filter(h -> !huts.contains(h)).isPresent()) {
                f.setOwner(Optional.empty());
                changed = true;
            }
        }
        return changed;
    }

    /** The fields, in order. */
    public JsonArray write() {
        JsonArray out = new JsonArray();
        fields.values().forEach(f -> out.add(f.write()));
        return out;
    }

    /** A registry of the saved fields (see {@link #load}). */
    public static FieldRegistry read(JsonArray in) {
        FieldRegistry r = new FieldRegistry();
        r.load(in);
        return r;
    }

    /** Replaces the fields by the saved ones; an unreadable entry is skipped, a second one at a position too. */
    public void load(JsonArray in) {
        fields.clear();
        for (JsonElement el : in) {
            if (el.isJsonObject()) {
                FarmField.read(el.getAsJsonObject()).ifPresent(f -> fields.putIfAbsent(f.pos(), f));
            }
        }
    }
}
