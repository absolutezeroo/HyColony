package dev.hycolony.core.construction.wand;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.ColonyManager;
import dev.hycolony.core.construction.blueprint.Blueprint;
import dev.hycolony.core.construction.blueprint.BlueprintEntry;
import dev.hycolony.core.construction.blueprint.StructurePlan;
import dev.hycolony.core.construction.hut.ConstructionBuildingTypes;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.config.ColonyConfig;
import dev.hycolony.core.testing.FakeBlueprints;
import dev.hycolony.core.testing.TestContexts;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PasteQueueTest {
    private static final BlockPos HUT = new BlockPos(10, 64, 10);
    private static final int HUT_BLOCKS = FakeBlueprints.hut(false).entries().size();

    private final TestContexts t = contexts();
    private final ColonyManager manager = new ColonyManager(t.context());
    private final PasteQueue queue = new PasteQueue(manager);

    private static TestContexts contexts() {
        TestContexts t = new TestContexts();
        FakeBlueprints.registerBlocks(t.catalog);
        ColonyConfig d = ColonyConfig.defaults();
        t.config = new ColonyConfig(
                d.gameplay(),
                d.claims(),
                d.permissions(),
                d.commands(),
                d.client(),
                d.hycolony(),
                new ColonyConfig.Structurize(5));
        return t;
    }

    private void paste(Blueprint bp) {
        queue.add(StructurePlan.build(bp, HUT, t.catalog));
    }

    @Test
    void placesAtMostMaxOperationsPerTick() {
        paste(FakeBlueprints.hut(false));
        queue.tick();
        assertEquals(5, t.blocks.placed.size());
        for (int i = 0; i < HUT_BLOCKS; i++) {
            queue.tick();
        }
        assertEquals(HUT_BLOCKS, t.blocks.placed.size());
        assertTrue(queue.isEmpty());
    }

    /**
     * Pastes the hut plan without its (1, 2, 1) corner, where the world has dirt, to its end; returns every changed
     * position in order.
     */
    private List<BlockPos> pasteOverAStrayBlock() {
        t.blocks.blocks.put(HUT.offset(1, 2, 1), FakeBlueprints.state(FakeBlueprints.DIRT));
        Blueprint hut = FakeBlueprints.hut(false);
        List<BlueprintEntry> entries = new ArrayList<>(hut.entries());
        entries.removeIf(e -> e.offset().equals(new BlockPos(1, 2, 1)));
        List<BlockPos> changes = new ArrayList<>();
        t.blocks.beforeChange = changes::add;
        paste(new Blueprint("gap", entries, hut.min(), hut.max()));
        for (int i = 0; i < HUT_BLOCKS; i++) {
            queue.tick();
        }
        return changes;
    }

    @Test
    void clearsWhatThePlanDoesNotWantBeforePlacingSolidsThenDecorations() {
        BlockPos stray = HUT.offset(1, 2, 1);
        List<BlockPos> changes = pasteOverAStrayBlock();
        assertEquals(stray, changes.get(0));
        assertFalse(t.blocks.blocks.containsKey(stray));
        // The torch (NON_SOLID) comes after every solid block.
        int torch = changes.indexOf(HUT.offset(0, 1, 0));
        assertEquals(changes.size() - 1, torch);
    }

    @Test
    void everyBlockIsChangedQuietlyWithoutParticlesNorSound() {
        List<BlockPos> changes = pasteOverAStrayBlock();
        assertEquals(HUT_BLOCKS, changes.size());
        assertEquals(changes, t.blocks.quiet);
    }

    @Test
    void registersPlacedContainersWithTheHutsBuilding() {
        UUID alice = UUID.randomUUID();
        manager.foundation().begin(alice, "Alice", new BlockPos(0, 64, 0), 0);
        Colony colony = manager.foundation().confirm(alice, "Rivendell").orElseThrow();
        manager.huts().place(colony, ConstructionBuildingTypes.BUILDER.id(), HUT, 0);
        paste(FakeBlueprints.hut(false));
        for (int i = 0; i < HUT_BLOCKS; i++) {
            queue.tick();
        }
        Building b = colony.buildings().at(HUT).orElseThrow();
        assertTrue(b.registeredContainers().contains(HUT.offset(0, 2, 0)));
    }

    @Test
    void aBlockThatCannotBePlacedIsSkippedAndThePasteEnds() {
        t.blocks.refusePlace = true;
        paste(FakeBlueprints.hut(false));
        for (int i = 0; i < HUT_BLOCKS; i++) {
            queue.tick();
        }
        assertTrue(queue.isEmpty());
    }
}
