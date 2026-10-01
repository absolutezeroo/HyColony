package dev.hycolony.core.crafting.furnace;

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

/**
 * The cooking stations a hut's worker fills, in the order they came (MC FurnaceUserModule): the stations its plan
 * places. Deviation from MC: a station a player places in the footprint joins too, since no Hytale plan has one
 * (spec SP4b § 10). The worker drops a position that is no station any more when it finds it.
 */
public final class FurnaceUserModule implements PersistentModule, BuildingEventsModule {
    private final List<BlockPos> stations = new ArrayList<>();

    public List<BlockPos> stations() {
        return Collections.unmodifiableList(stations);
    }

    /** Adds the station at {@code pos}, once. */
    public void addStation(BlockPos pos) {
        if (!stations.contains(pos)) {
            stations.add(pos);
        }
    }

    /** MC removeFromFurnaces: forgets a position that is no station any more. */
    public void removeStation(BlockPos pos) {
        stations.remove(pos);
    }

    /** MC onBlockPlacedInBuilding: a station of the plan joins the list. */
    @Override
    public void onBlockPlacedInBuilding(Colony colony, Building building, BlockPos pos, BlockKey block) {
        if (colony.context().ports().cooking().catalog().isStation(block)) {
            addStation(pos);
        }
    }

    /** A station a player placed in the footprint joins the list (see the class: a deviation); marks the colony. */
    @Override
    public void onBlockPlacedByPlayer(Colony colony, Building building, BlockPos pos, BlockKey block) {
        if (colony.context().ports().cooking().catalog().isStation(block) && !stations.contains(pos)) {
            stations.add(pos);
            colony.markDirty();
        }
    }

    @Override
    public void write(JsonObject out) {
        JsonArray arr = new JsonArray();
        stations.forEach(p -> arr.add(SavedJson.pos(p)));
        out.add("furnaces", arr);
    }

    /** Tolerant (CLAUDE.md § 5): a malformed position is skipped. */
    @Override
    public void read(JsonObject in) {
        stations.clear();
        for (JsonElement e : SavedJson.arrayOr(in.get("furnaces"))) {
            SavedJson.tryPos(e).ifPresent(this::addStation);
        }
    }
}
