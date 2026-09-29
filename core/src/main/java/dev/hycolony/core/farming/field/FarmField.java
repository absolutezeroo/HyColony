package dev.hycolony.core.farming.field;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.ItemKey;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import org.jspecify.annotations.Nullable;

/**
 * One field of the colony (MC FarmField): the field block's position, the farmer hut that owns it, the seed it grows,
 * its radii and its stage. Only the colony's field registry and the farmer's modules change it.
 */
public final class FarmField {
    private final BlockPos pos;
    private Optional<BlockPos> owner = Optional.empty();
    private Optional<ItemKey> seed = Optional.empty();
    private FieldRadii radii = FieldRadii.defaults();
    private FieldStage stage = FieldStage.EMPTY;

    public FarmField(BlockPos pos) {
        this.pos = Objects.requireNonNull(pos, "pos");
    }

    public BlockPos pos() {
        return pos;
    }

    /** The farmer hut working this field, empty when it is free (MC getBuildingId). */
    public Optional<BlockPos> owner() {
        return owner;
    }

    public void setOwner(Optional<BlockPos> hut) {
        owner = Objects.requireNonNull(hut, "hut");
    }

    /** MC isTaken. */
    public boolean isTaken() {
        return owner.isPresent();
    }

    public Optional<ItemKey> seed() {
        return seed;
    }

    public void setSeed(Optional<ItemKey> newSeed) {
        seed = Objects.requireNonNull(newSeed, "newSeed");
    }

    public FieldRadii radii() {
        return radii;
    }

    public void setRadii(FieldRadii r) {
        radii = Objects.requireNonNull(r, "r");
    }

    public FieldStage stage() {
        return stage;
    }

    /** MC nextState. */
    public void nextStage() {
        stage = stage.next();
    }

    /** MC serializeNBT: position, owner, seed, radii (S, W, N, E) and stage. */
    public JsonObject write() {
        JsonObject o = new JsonObject();
        o.add("pos", FieldJson.pos(pos));
        owner.ifPresent(h -> o.add("owner", FieldJson.pos(h)));
        seed.ifPresent(s -> o.addProperty("seed", s.id()));
        JsonArray r = new JsonArray();
        r.add(radii.south());
        r.add(radii.west());
        r.add(radii.north());
        r.add(radii.east());
        o.add("radii", r);
        o.addProperty("stage", stage.name());
        return o;
    }

    /** The saved field; empty without a readable position. Other keys fall back to a new field's values. */
    public static Optional<FarmField> read(JsonObject in) {
        Optional<BlockPos> pos = FieldJson.pos(in.get("pos"));
        if (pos.isEmpty()) {
            return Optional.empty();
        }
        FarmField f = new FarmField(pos.get());
        f.owner = FieldJson.pos(in.get("owner"));
        if (in.has("seed") && in.get("seed").isJsonPrimitive()) {
            f.seed = Optional.of(new ItemKey(in.get("seed").getAsString()));
        }
        f.radii = readRadii(in.get("radii"));
        f.stage = readStage(in.get("stage"));
        return Optional.of(f);
    }

    private static FieldStage readStage(JsonElement el) {
        if (el == null || !el.isJsonPrimitive()) {
            return FieldStage.EMPTY;
        }
        try {
            return FieldStage.valueOf(el.getAsString());
        } catch (IllegalArgumentException e) {
            return FieldStage.EMPTY;
        }
    }

    /** Four numbers, none negative, within the budget of {@link FieldRadii#MAX_RANGE}; else the defaults. */
    private static FieldRadii readRadii(@Nullable JsonElement el) {
        if (!(el instanceof JsonArray r) || r.size() != 4) {
            return FieldRadii.defaults();
        }
        int[] sides = new int[4];
        int sum = 0;
        for (int i = 0; i < 4; i++) {
            OptionalInt side = FieldJson.integer(r.get(i));
            if (side.isEmpty() || side.getAsInt() < 0) {
                return FieldRadii.defaults();
            }
            sides[i] = side.getAsInt();
            sum += sides[i];
        }
        return sum > FieldRadii.MAX_RANGE
                ? FieldRadii.defaults()
                : new FieldRadii(sides[0], sides[1], sides[2], sides[3]);
    }
}
