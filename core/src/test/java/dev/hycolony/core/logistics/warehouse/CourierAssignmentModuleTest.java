package dev.hycolony.core.logistics.warehouse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import dev.hycolony.core.app.persistence.ColonySerializer;
import dev.hycolony.core.building.Building;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.permission.Permissions;
import dev.hycolony.core.colony.territory.TerritoryIndex;
import dev.hycolony.core.job.HiringMode;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.request.model.RequestToken;
import dev.hycolony.core.testing.TestContexts;
import dev.hycolony.core.testing.TestJobs;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CourierAssignmentModuleTest {
    private final TestContexts t = new TestContexts();
    private final Colony colony = new Colony(
            t.context(),
            new TerritoryIndex(),
            new Colony.Founding(1, "T", new BlockPos(0, 64, 0), Permissions.createDefault(UUID.randomUUID(), "A")));

    private Building warehouse(BlockPos pos, int level) {
        Building b = Building.create(WarehouseBuilding.TYPE, pos, 0);
        b.setLevel(level);
        b.setBuilt(level > 0);
        colony.buildings().add(b);
        return b;
    }

    private CitizenData courier(int id) {
        CitizenData c = new CitizenData(id);
        c.setJob(TestJobs.COURIER.factory().apply(c));
        colony.citizens().restore(c);
        return c;
    }

    private static List<Integer> couriers(Building warehouse) {
        return warehouse.module(CourierAssignmentModule.class).orElseThrow().couriers();
    }

    @Test
    void attachesUpToTwoCouriersPerLevel() {
        Building w = warehouse(new BlockPos(10, 64, 10), 1);
        courier(1);
        courier(2);
        courier(3);
        colony.citizens().restore(new CitizenData(4)); // jobless: never attached

        colony.buildings().onColonyTick(colony);
        assertEquals(List.of(1, 2), couriers(w));

        w.setLevel(2);
        colony.buildings().onColonyTick(colony);
        assertEquals(List.of(1, 2, 3), couriers(w));
    }

    @Test
    void levelZeroAttachesNone() {
        Building w = warehouse(new BlockPos(10, 64, 10), 0);
        courier(1);

        colony.buildings().onColonyTick(colony);

        assertTrue(couriers(w).isEmpty());
    }

    @Test
    void aCourierBelongsToOneWarehouseOnly() {
        Building first = warehouse(new BlockPos(10, 64, 10), 1);
        Building second = warehouse(new BlockPos(20, 64, 20), 1);
        courier(1);

        colony.buildings().onColonyTick(colony);

        assertEquals(List.of(1), couriers(first));
        assertTrue(couriers(second).isEmpty());
        assertEquals(first, CourierAssignmentModule.warehouseOf(colony, 1).orElseThrow());
    }

    @Test
    void detachesACitizenNoLongerCourier() {
        Building w = warehouse(new BlockPos(10, 64, 10), 1);
        CitizenData c = courier(1);
        colony.buildings().onColonyTick(colony);
        assertEquals(List.of(1), couriers(w));

        c.setJob(null);
        colony.buildings().onColonyTick(colony);

        assertTrue(couriers(w).isEmpty());
        assertTrue(CourierAssignmentModule.warehouseOf(colony, 1).isEmpty());
    }

    @Test
    void queueSurvivesSaveAndLoad() {
        Building w = warehouse(new BlockPos(10, 64, 10), 1);
        courier(1);
        colony.buildings().onColonyTick(colony);
        RequestToken first = RequestToken.random();
        RequestToken second = RequestToken.random();
        w.module(WarehouseRequestQueue.class).orElseThrow().add(first);
        w.module(WarehouseRequestQueue.class).orElseThrow().add(second);

        Colony loaded = ColonySerializer.read(ColonySerializer.write(colony), t.context(), new TerritoryIndex());

        Building back = loaded.buildings().at(w.position()).orElseThrow();
        assertEquals(
                List.of(first, second),
                back.module(WarehouseRequestQueue.class).orElseThrow().tokens());
        assertEquals(List.of(1), couriers(back));
    }

    /** CLAUDE.md § 5: a malformed courier entry or mode never locks the colony. */
    @Test
    void unreadableSavedValuesFallBackInsteadOfThrowing() {
        CourierAssignmentModule m = warehouse(new BlockPos(10, 64, 10), 1)
                .module(CourierAssignmentModule.class)
                .orElseThrow();
        m.read(JsonParser.parseString("{\"couriers\":[4,\"a\",5],\"hiringMode\":{}}")
                .getAsJsonObject());
        assertEquals(List.of(4, 5), m.couriers());
        assertEquals(HiringMode.DEFAULT, m.hiringMode());

        m.read(JsonParser.parseString("{\"couriers\":{}}").getAsJsonObject());
        assertTrue(m.couriers().isEmpty());
    }
}
