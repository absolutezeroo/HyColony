package dev.hycolony.core.building;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.hycolony.core.app.persistence.ColonySerializer;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.permission.Permissions;
import dev.hycolony.core.colony.territory.TerritoryIndex;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.Workstation;
import dev.hycolony.core.testing.TestContexts;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The crafting benches (and containers) a hut registered from its plan (MC AbstractBuilding.registerBlockPosition). */
class BuildingWorkstationsTest {
    private static final BlockPos CENTER = new BlockPos(0, 64, 0);
    private static final BlockPos HUT = new BlockPos(8, 64, 0);
    private static final BlockPos BENCH = new BlockPos(9, 64, 1);
    private static final BlockPos OTHER_BENCH = new BlockPos(10, 64, 1);
    private static final BuildingType CRAFTER_HUT = new BuildingType("test:crafter", "hut.test", 5, List.of());

    private static Building hut() {
        return Building.create(CRAFTER_HUT, HUT, 0);
    }

    @Test
    void registeredWorkstationKeepsItsBenchAndTier() {
        RegisteredBlocks blocks = hut().registeredBlocks();
        blocks.addWorkstation(BENCH, new Workstation("Farmingbench", 2));
        assertEquals(Map.of(BENCH, new Workstation("Farmingbench", 2)), blocks.workstations());
    }

    @Test
    void benchPlacedAgainAtTheSamePositionReplacesTheOldOne() {
        RegisteredBlocks blocks = hut().registeredBlocks();
        blocks.addWorkstation(BENCH, new Workstation("Farmingbench", 1));
        blocks.addWorkstation(BENCH, new Workstation("Farmingbench", 3));
        assertEquals(Map.of(BENCH, new Workstation("Farmingbench", 3)), blocks.workstations());
    }

    @Test
    void brokenWorkstationIsForgotten() {
        RegisteredBlocks blocks = hut().registeredBlocks();
        blocks.addWorkstation(BENCH, new Workstation("Farmingbench", 1));
        blocks.removeWorkstation(BENCH);
        assertTrue(blocks.workstations().isEmpty());
    }

    @Test
    void hutBlockIsNeverARegisteredContainer() {
        Building b = hut();
        b.registeredBlocks().addContainer(HUT);
        b.registeredBlocks().addContainer(BENCH);
        assertEquals(Set.of(BENCH), b.registeredBlocks().containers());
        assertEquals(List.of(BENCH, HUT), b.containers()); // MC AbstractBuildingContainer.getContainers: racks first
    }

    @Test
    void workstationBelowTierOneIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new Workstation("Farmingbench", 0));
    }

    @Test
    void workstationsSurviveSaveAndLoadInTheirOrder() {
        TestContexts t = new TestContexts();
        Colony c = colony(t);
        Building b = hut();
        b.registeredBlocks().addWorkstation(OTHER_BENCH, new Workstation("Workbench", 1));
        b.registeredBlocks().addWorkstation(BENCH, new Workstation("Farmingbench", 3));
        c.buildings().add(b);

        Colony back = ColonySerializer.read(ColonySerializer.write(c), t.context(), new TerritoryIndex());

        Map<BlockPos, Workstation> loaded =
                back.buildings().at(HUT).orElseThrow().registeredBlocks().workstations();
        assertEquals(b.registeredBlocks().workstations(), loaded);
        assertEquals(List.of(OTHER_BENCH, BENCH), List.copyOf(loaded.keySet()));
    }

    @Test
    void malformedWorkstationEntriesAreSkippedOnLoad() {
        TestContexts t = new TestContexts();
        Colony c = colony(t);
        Building b = hut();
        b.registeredBlocks().addWorkstation(BENCH, new Workstation("Farmingbench", 2));
        c.buildings().add(b);
        JsonObject json = ColonySerializer.write(c);
        JsonArray saved = hutJson(json).getAsJsonArray("workstations");
        for (String bad : List.of(
                "{\"pos\":{\"x\":1,\"y\":64,\"z\":1},\"tier\":2}",
                "{\"pos\":{\"x\":2,\"y\":64,\"z\":1},\"bench\":\"Workbench\",\"tier\":0}",
                "{\"pos\":\"garbage\",\"bench\":\"Workbench\",\"tier\":1}",
                "{\"pos\":{\"x\":3,\"y\":64,\"z\":1},\"bench\":\"Workbench\",\"tier\":\"high\"}",
                "\"not an object\"")) {
            saved.add(JsonParser.parseString(bad));
        }

        Colony back = ColonySerializer.read(json, t.context(), new TerritoryIndex());

        assertEquals(
                Map.of(BENCH, new Workstation("Farmingbench", 2)),
                back.buildings().at(HUT).orElseThrow().registeredBlocks().workstations());
    }

    private static Colony colony(TestContexts t) {
        t.extraBuildingTypes.add(CRAFTER_HUT);
        UUID owner = new UUID(0, 1);
        return new Colony(
                t.context(),
                new TerritoryIndex(),
                new Colony.Founding(1, "T", CENTER, Permissions.createDefault(owner, "Owner")));
    }

    private static JsonObject hutJson(JsonObject colony) {
        for (var el : colony.getAsJsonArray("buildings")) {
            if (CRAFTER_HUT.id().equals(el.getAsJsonObject().get("type").getAsString())) {
                return el.getAsJsonObject();
            }
        }
        throw new AssertionError("hut not saved");
    }
}
