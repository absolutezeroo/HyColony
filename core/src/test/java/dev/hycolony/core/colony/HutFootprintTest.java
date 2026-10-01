package dev.hycolony.core.colony;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.construction.blueprint.Blueprint;
import dev.hycolony.core.construction.blueprint.BlueprintSource;
import dev.hycolony.core.construction.hut.ConstructionBuildingTypes;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.testing.TestContexts;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class HutFootprintTest {
    private final TestContexts t = new TestContexts();
    private int askedLevel;

    private GamePorts ports() {
        t.blueprints = new BlueprintSource() {
            @Override
            public Optional<Blueprint> load(String style, String type, int level, int rotation) {
                askedLevel = level;
                return Optional.of(new Blueprint("k", List.of(), new BlockPos(-2, -1, -3), new BlockPos(4, 6, 2)));
            }

            @Override
            public List<String> styles() {
                return List.of("s");
            }
        };
        return t.context().ports();
    }

    private static Building hut(int level, String style) {
        Building b = Building.create(ConstructionBuildingTypes.RESIDENCE, new BlockPos(100, 64, 100), 0);
        b.setLevel(level);
        b.setStyle(style);
        return b;
    }

    @Test
    void cornersOfThePlanWidenedByOneBlock() {
        GamePorts p = ports();
        Building b = hut(2, "s");

        assertTrue(HutFootprint.isInBuilding(p, b, new BlockPos(97, 62, 96))); // min corner - 1
        assertTrue(HutFootprint.isInBuilding(p, b, new BlockPos(105, 71, 103))); // max corner + 1
        assertFalse(HutFootprint.isInBuilding(p, b, new BlockPos(106, 64, 100)));
        assertEquals(2, askedLevel);
    }

    @Test
    void levelZeroUsesThePlanOfLevelOne() {
        GamePorts p = ports();

        HutFootprint.of(p, hut(0, "s"));

        assertEquals(1, askedLevel);
    }

    @Test
    void withoutPlanOnlyTheHutAndItsNeighbours() {
        GamePorts p = t.context().ports(); // TestContexts' source loads nothing

        assertTrue(HutFootprint.isInBuilding(p, hut(1, ""), new BlockPos(101, 65, 99)));
        assertFalse(HutFootprint.isInBuilding(p, hut(1, ""), new BlockPos(102, 64, 100)));
        assertFalse(HutFootprint.isInBuilding(p, hut(1, "s"), new BlockPos(102, 64, 100))); // style, no plan
    }
}
