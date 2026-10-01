package dev.hycolony.core.app.action;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.app.ui.BuildingView;
import dev.hycolony.core.building.Building;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.construction.hut.ConstructionBuildingTypes;
import dev.hycolony.core.farming.hut.FarmerHut;
import dev.hycolony.core.job.WorkerModule;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.Vec3;
import dev.hycolony.core.kernel.port.Msg;
import dev.hycolony.core.testing.TestContexts;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** MC's hut window buttons on the main page: Recall Worker, the name pencil and Inventory, all MANAGE_HUTS. */
class HutWindowActionsTest {
    private static final BlockPos HUT = new BlockPos(30, 64, 0);
    private final TestContexts t = new TestContexts();
    private final UUID alice = UUID.randomUUID();
    private final UUID stranger = UUID.randomUUID();
    private final ColonyManager manager = t.manager();
    private final HutWindowActions actions = manager.hutWindows();
    private final Colony colony;
    private final Building hut;

    HutWindowActionsTest() {
        manager.foundation().begin(alice, "Alice", new BlockPos(0, 64, 0), 0);
        colony = manager.foundation().confirm(alice, "A").orElseThrow();
        manager.huts().place(colony, ConstructionBuildingTypes.BUILDER.id(), HUT, 0, alice);
        hut = colony.buildings().at(HUT).orElseThrow();
    }

    private void hireAnn() {
        CitizenData ann = new CitizenData(1);
        ann.setName("Ann");
        colony.citizens().restore(ann);
        assertTrue(hut.module(WorkerModule.class).orElseThrow().hire(colony, hut, ann));
    }

    @Test
    void recallBringsEveryWorkerToTheHut() {
        hireAnn();
        colony.citizens().respawnBody(1);
        assertTrue(actions.recallWorkers(alice, HUT));
        assertEquals(List.of(Vec3.center(HUT)), t.bodies.teleports);
        assertTrue(t.ui.shown.get(alice) instanceof BuildingView, "the hut shows again");
    }

    @Test
    void recallSaysWhenABodyCannotAppear() {
        hireAnn();
        t.bodies.refuseSpawn = true;
        actions.recallWorkers(alice, HUT);
        assertEquals(
                Msg.of("hycolony.hut.recallFail"), t.notifier.sent.getLast().msg());
    }

    @Test
    void recallNeedsManageHuts() {
        hireAnn();
        assertFalse(actions.recallWorkers(stranger, HUT));
        assertTrue(colony.citizens().bodyOf(1).isEmpty());
        assertEquals(
                "hycolony.permission.toolDenied",
                t.notifier.sent.getLast().msg().key());
    }

    @Test
    void aNameOver15IsCutAndSaysSoAsMc() {
        assertTrue(actions.rename(alice, HUT, "Sixteen chars!!!"));
        assertEquals("Sixteen chars!!", hut.customName());
        assertEquals(
                Msg.of("hycolony.gui.name.toolong", "Sixteen chars!!"),
                t.notifier.sent.getLast().msg());
        assertTrue(t.ui.shown.get(alice) instanceof BuildingView, "the hut shows again");
    }

    @Test
    void renamingAndCyclingTheModeMarkTheColonyToSave() {
        colony.clearDirty();
        assertTrue(actions.rename(alice, HUT, "Mine"));
        assertTrue(colony.isDirty());
        colony.clearDirty();
        assertTrue(actions.cycleHiring(alice, HUT));
        assertTrue(colony.isDirty());
    }

    @Test
    void aNameOf15IsKeptAsTyped() {
        t.notifier.sent.clear();
        assertTrue(actions.rename(alice, HUT, " Fifteen chars "));
        assertEquals(" Fifteen chars ", hut.customName());
        assertTrue(t.notifier.sent.isEmpty());
    }

    @Test
    void anEmptyNameShowsTheTypeAgain() {
        hut.setCustomName("Bob's");
        assertTrue(actions.rename(alice, HUT, ""));
        assertEquals("", hut.customName());
    }

    @Test
    void renameNeedsManageHuts() {
        assertFalse(actions.rename(stranger, HUT, "Mine"));
        assertEquals("", hut.customName());
        assertEquals(
                "hycolony.permission.toolDenied",
                t.notifier.sent.getLast().msg().key());
    }

    @Test
    void theHutInventoryNeedsManageHutsAsMc() {
        assertTrue(actions.mayOpenInventory(alice, HUT));
        assertFalse(actions.mayOpenInventory(stranger, HUT));
        assertEquals(
                "hycolony.permission.toolDenied",
                t.notifier.sent.getLast().msg().key());
    }

    @Test
    void manageWorkersAtLevelZeroSaysSoAsMc() {
        assertTrue(actions.mayAssign(alice, HUT), "MC's builder hires at level 0, to build its own hut");
        BlockPos farm = new BlockPos(0, 64, 30);
        manager.huts().place(colony, FarmerHut.TYPE_ID, farm, 0, alice);
        assertFalse(actions.mayAssign(alice, farm));
        assertEquals(
                Msg.of("hycolony.hut.notBuiltYet"), t.notifier.sent.getLast().msg());
        Building b = colony.buildings().at(farm).orElseThrow();
        b.setLevel(1);
        b.setBuilt(true);
        assertTrue(actions.mayAssign(alice, farm));
    }

    @Test
    void manageHousingAtLevelZeroSaysSoAsMc() {
        BlockPos home = new BlockPos(0, 64, 30);
        manager.huts().place(colony, ConstructionBuildingTypes.RESIDENCE.id(), home, 0, alice);
        assertFalse(actions.mayAssign(alice, home));
        assertEquals(
                Msg.of("hycolony.hut.notBuiltYet"), t.notifier.sent.getLast().msg());
        colony.buildings().at(home).orElseThrow().setLevel(1);
        assertTrue(actions.mayAssign(alice, home));
    }

    @Test
    void aGoneHutRefusesEverything() {
        BlockPos nowhere = new BlockPos(20, 64, 20);
        assertFalse(actions.recallWorkers(alice, nowhere));
        assertFalse(actions.rename(alice, nowhere, "X"));
        assertFalse(actions.mayOpenInventory(alice, nowhere));
    }
}
