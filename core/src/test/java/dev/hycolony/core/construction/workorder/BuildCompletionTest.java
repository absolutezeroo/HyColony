package dev.hycolony.core.construction.workorder;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.ColonyManager;
import dev.hycolony.core.construction.hut.ConstructionBuildingTypes;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.port.Msg;
import dev.hycolony.core.testing.TestContexts;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BuildCompletionTest {
    private static final BlockPos RES = new BlockPos(20, 64, 0);
    private final TestContexts t = new TestContexts();
    private final ColonyManager manager = new ColonyManager(t.context());
    private final UUID alice = UUID.randomUUID();
    private final Colony colony;

    BuildCompletionTest() {
        manager.foundation().begin(alice, "Alice", new BlockPos(0, 64, 0), 0);
        colony = manager.foundation().confirm(alice, "A").orElseThrow();
    }

    private Building residence(int level) {
        manager.huts().place(colony, ConstructionBuildingTypes.RESIDENCE.id(), RES, 0);
        Building b = colony.buildings().at(RES).orElseThrow();
        b.setLevel(level);
        t.notifier.sent.clear();
        return b;
    }

    private void complete(Building b, WorkOrderType type, int target) {
        BuildCompletion.apply(
                colony, new WorkOrder(1, type, RES, target, new WorkOrder.Layout("medieval", target, 0)), b);
    }

    private Msg onlyMessage() {
        assertEquals(1, t.notifier.sent.size());
        return t.notifier.sent.get(0).msg();
    }

    @Test
    void buildCelebratesAtTheHutAndNamesItTranslatedWithItsLevel() {
        complete(residence(0), WorkOrderType.BUILD, 1);

        assertEquals(List.of(RES), t.effects.celebrated);
        assertEquals(Msg.of("hycolony.build.complete", "%hycolony.ui.building.type.residence", "1"), onlyMessage());
    }

    @Test
    void upgradeCelebratesOnce() {
        complete(residence(2), WorkOrderType.UPGRADE, 3);

        assertEquals(List.of(RES), t.effects.celebrated);
        assertEquals(Msg.of("hycolony.build.complete", "%hycolony.ui.building.type.residence", "3"), onlyMessage());
    }

    @Test
    void repairKeepsTheLevelSoNoFireworks() {
        complete(residence(2), WorkOrderType.REPAIR, 2);

        assertEquals(List.of(), t.effects.celebrated);
        assertEquals(
                Msg.of("hycolony.build.repairComplete", "%hycolony.ui.building.type.residence", "2"), onlyMessage());
    }

    @Test
    void removeNeverCelebrates() {
        complete(residence(2), WorkOrderType.REMOVE, 0);

        assertEquals(List.of(), t.effects.celebrated);
        assertEquals(Msg.of("hycolony.build.removeComplete", "%hycolony.ui.building.type.residence"), onlyMessage());
    }

    @Test
    void aCustomNameIsShownAsIs() {
        Building b = residence(0);
        b.setCustomName("Maison");

        complete(b, WorkOrderType.BUILD, 1);

        assertEquals(Msg.of("hycolony.build.complete", "Maison", "1"), onlyMessage());
    }
}
