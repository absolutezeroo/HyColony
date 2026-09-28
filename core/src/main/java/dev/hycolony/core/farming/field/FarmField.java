package dev.hycolony.core.farming.field;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.ItemKey;
import java.util.Objects;
import java.util.Optional;

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
        o.add("pos", pos(pos));
        owner.ifPresent(h -> o.add("owner", pos(h)));
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
        Optional<BlockPos> pos = readPos(in.get("pos"));
        if (pos.isEmpty()) {
            return Optional.empty();
        }
        FarmField f = new FarmField(pos.get());
        f.owner = readPos(in.get("owner"));
        if (in.has("seed") && in.get("seed").isJsonPrimitive()) {
            f.seed = Optional.of(new ItemKey(in.get("seed").getAsString()));
        }
        if (in.get("radii") instanceof JsonArray r && r.size() == 4) {
            f.radii = new FieldRadii(
                    r.get(0).getAsInt(),
                    r.get(1).getAsInt(),
                    r.get(2).getAsInt(),
                    r.get(3).getAsInt());
        }
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

    private static JsonArray pos(BlockPos p) {
        JsonArray a = new JsonArray();
        a.add(p.x());
        a.add(p.y());
        a.add(p.z());
        return a;
    }

    private static Optional<BlockPos> readPos(JsonElement el) {
        return el instanceof JsonArray a && a.size() == 3
                ? Optional.of(new BlockPos(
                        a.get(0).getAsInt(), a.get(1).getAsInt(), a.get(2).getAsInt()))
                : Optional.empty();
    }
}
