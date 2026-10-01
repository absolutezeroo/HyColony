package dev.hycolony.core.citizen.home;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.hycolony.core.building.Building;
import dev.hycolony.core.building.module.BuildingEventsModule;
import dev.hycolony.core.building.module.PersistentModule;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.BlockKey;
import dev.hycolony.core.kernel.persist.SavedJson;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * The beds of a residence, in the order they were placed (MC BedHandlingModule). Only a bed's base block is kept,
 * Hytale's equivalent of MC's bed head.
 *
 * <p>Deviation from MC: MC keeps a HashSet, so its bed order (and each resident's bed) may change between sessions;
 * here it is the placing order. MC's onWakeUp clears each bed's OCCUPIED flag; Hytale keeps a bed's occupancy itself.
 */
public final class BedModule implements PersistentModule, BuildingEventsModule {
    private final List<BlockPos> beds = new ArrayList<>();

    public List<BlockPos> beds() {
        return Collections.unmodifiableList(beds);
    }

    /** The bed of the resident of rank {@code rank} (MC EntityAISleep: by index); empty past the list. */
    public Optional<BlockPos> bed(int rank) {
        return rank >= 0 && rank < beds.size() ? Optional.of(beds.get(rank)) : Optional.empty();
    }

    public void addBed(BlockPos pos) {
        if (!beds.contains(pos)) {
            beds.add(pos);
        }
    }

    /** MC removeBed: forgets a position that is no bed any more. */
    public void removeBed(BlockPos pos) {
        beds.remove(pos);
    }

    /** MC onBlockPlacedInBuilding: a bed placed in the building joins its list. */
    @Override
    public void onBlockPlacedInBuilding(Colony colony, Building building, BlockPos pos, BlockKey block) {
        if (colony.context().ports().catalog().isBed(block)) {
            addBed(pos);
        }
    }

    @Override
    public void write(JsonObject out) {
        JsonArray arr = new JsonArray();
        beds.forEach(p -> arr.add(SavedJson.pos(p)));
        out.add("beds", arr);
    }

    /** Tolerant (CLAUDE.md § 5): a malformed position is skipped. */
    @Override
    public void read(JsonObject in) {
        beds.clear();
        for (JsonElement e : SavedJson.arrayOr(in.get("beds"))) {
            SavedJson.tryPos(e).ifPresent(this::addBed);
        }
    }
}
