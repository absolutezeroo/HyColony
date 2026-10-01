package dev.hycolony.core.citizen.home;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import dev.hycolony.core.building.Building;
import dev.hycolony.core.building.module.BuildingEventsModule;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.permission.Permissions;
import dev.hycolony.core.colony.territory.TerritoryIndex;
import dev.hycolony.core.construction.hut.ConstructionBuildingTypes;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.BlockKey;
import dev.hycolony.core.testing.TestContexts;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BedModuleTest {
    private static final BlockKey BED = new BlockKey("Furniture_Village_Bed");
    private static final BlockKey CHAIR = new BlockKey("Furniture_Village_Chair");
    private final TestContexts t = new TestContexts();

    @Test
    void placedBedsJoinInOrderWithoutDuplicatesAndOtherBlocksDoNot() {
        t.catalog.beds.add(BED);
        Colony c = new Colony(
                t.context(),
                new TerritoryIndex(),
                new Colony.Founding(1, "T", new BlockPos(0, 64, 0), Permissions.createDefault(UUID.randomUUID(), "A")));
        Building b = Building.create(ConstructionBuildingTypes.RESIDENCE, new BlockPos(8, 64, 0), 0);

        BuildingEventsModule.blockPlaced(c, b, new BlockPos(10, 64, 0), BED);
        BuildingEventsModule.blockPlaced(c, b, new BlockPos(9, 64, 0), BED);
        BuildingEventsModule.blockPlaced(c, b, new BlockPos(10, 64, 0), BED);
        BuildingEventsModule.blockPlaced(c, b, new BlockPos(11, 64, 0), CHAIR);

        BedModule beds = b.module(BedModule.class).orElseThrow();
        assertEquals(List.of(new BlockPos(10, 64, 0), new BlockPos(9, 64, 0)), beds.beds());
        assertEquals(Optional.of(new BlockPos(9, 64, 0)), beds.bed(1));
        assertTrue(beds.bed(2).isEmpty());
        assertTrue(beds.bed(-1).isEmpty());
    }

    @Test
    void removedBedAndRoundTrip() {
        BedModule beds = new BedModule();
        beds.addBed(new BlockPos(1, 2, 3));
        beds.addBed(new BlockPos(4, 5, 6));
        beds.removeBed(new BlockPos(1, 2, 3));
        JsonObject saved = new JsonObject();
        beds.write(saved);

        BedModule read = new BedModule();
        read.read(saved);

        assertEquals(List.of(new BlockPos(4, 5, 6)), read.beds());
    }
}
