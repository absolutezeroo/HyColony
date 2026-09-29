package dev.hycolony.core.farming.hut;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.hycolony.core.farming.field.FieldCells;
import dev.hycolony.core.farming.field.FieldJson;
import dev.hycolony.core.farming.field.FieldRadii;
import dev.hycolony.core.kernel.BlockPos;
import java.util.Optional;
import java.util.OptionalInt;
import org.jspecify.annotations.Nullable;

/**
 * Where the farmer is in its walk over the current field (MC BuildingFarmer cell, workingOffset and prevPos), saved
 * with the hut so a pass resumes after a reload.
 */
public final class FieldWalk {
    private int cell = -1;
    private int @Nullable [] offset;
    private @Nullable BlockPos prevPos;

    /** MC workingOffset: the {x, z} offset of the cell being worked, empty between passes. */
    public Optional<int[]> offset() {
        return Optional.ofNullable(offset).map(int[]::clone);
    }

    /**
     * MC nextValidCell: moves to the next cell of the spiral inside {@code radii}, restarting from the first one when
     * no cell is current; false (and no cell current) once the walk is over.
     */
    public boolean advance(FieldRadii radii) {
        if (offset == null) {
            cell = -1;
        }
        OptionalInt next = FieldCells.next(cell, radii);
        if (next.isEmpty()) {
            cell = FieldCells.LARGEST_CELL;
            offset = null;
            return false;
        }
        cell = next.getAsInt();
        offset = FieldCells.offset(cell);
        return true;
    }

    /** MC prevPos: the last cell worked in this pass, empty at its start. */
    public Optional<BlockPos> prevPos() {
        return Optional.ofNullable(prevPos);
    }

    public void setPrevPos(Optional<BlockPos> pos) {
        prevPos = pos.orElse(null);
    }

    /** Ends the pass: no cell current, no previous cell. */
    public void reset() {
        offset = null;
        prevPos = null;
        cell = -1;
    }

    void write(JsonObject out) {
        out.addProperty("cell", cell);
        if (offset != null) {
            JsonArray o = new JsonArray();
            o.add(offset[0]);
            o.add(offset[1]);
            out.add("workingOffset", o);
        }
        if (prevPos != null) {
            out.add("prevPos", FieldJson.pos(prevPos));
        }
    }

    void read(JsonObject in) {
        OptionalInt savedCell = FieldJson.integer(in.get("cell"));
        cell = savedCell.orElse(-1);
        if (cell < -1 || cell > FieldCells.LARGEST_CELL) {
            cell = -1; // corrupted: counting on from it would overflow the spiral
            savedCell = OptionalInt.empty();
        }
        offset = savedCell.isPresent() && in.get("workingOffset") instanceof JsonArray o && o.size() == 2
                ? readOffset(o)
                : null;
        prevPos = FieldJson.pos(in.get("prevPos")).orElse(null);
    }

    /** A saved {x, z} offset; null if either is not a number. */
    private static int @Nullable [] readOffset(JsonArray o) {
        OptionalInt x = FieldJson.integer(o.get(0));
        OptionalInt z = FieldJson.integer(o.get(1));
        return x.isPresent() && z.isPresent() ? new int[] {x.getAsInt(), z.getAsInt()} : null;
    }
}
