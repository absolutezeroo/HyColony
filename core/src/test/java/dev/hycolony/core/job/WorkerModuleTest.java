package dev.hycolony.core.job;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import dev.hycolony.core.building.Building;
import dev.hycolony.core.building.BuildingType;
import dev.hycolony.core.building.ModuleProducer;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.citizen.Skill;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.Permissions;
import dev.hycolony.core.colony.TerritoryIndex;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.testing.TestContexts;
import dev.hycolony.core.testing.TestJobs;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WorkerModuleTest {
    private final TestContexts t = new TestContexts();

    private Colony colony() {
        return new Colony(t.context(), new TerritoryIndex(), 1, "T", new BlockPos(0, 64, 0),
                Permissions.createDefault(UUID.randomUUID(), "A"));
    }

    private WorkerModule module() {
        return new WorkerModule(TestJobs.TYPE, Skill.Athletics, Skill.Strength, 2, false);
    }

    private Building buildingWith(WorkerModule module, int level, boolean built) {
        BuildingType type = new BuildingType("test:worker-hut", "hut.worker", 5,
                List.of(new ModuleProducer("worker", () -> module)));
        Building b = Building.create(type, new BlockPos(1, 64, 1), 0);
        b.setLevel(level);
        b.setBuilt(built);
        return b;
    }

    @Test
    void autoHiresFirstJoblessByIdEverySlowTick() {
        Colony c = colony();
        c.citizens().restore(new CitizenData(1));
        CitizenData child = new CitizenData(2);
        child.setChild(true);
        c.citizens().restore(child);
        c.citizens().restore(new CitizenData(3));

        WorkerModule module = module();
        Building b = buildingWith(module, 1, true);
        c.buildings().add(b);

        c.buildings().onColonyTick(c);
        assertEquals(List.of(1), module.workers());
        assertTrue(c.citizens().get(1).orElseThrow().job().isPresent());
        assertEquals(b.position(), c.citizens().get(1).orElseThrow().workBuilding());

        c.buildings().onColonyTick(c); // citizen 2 is a child: skipped, citizen 3 hired next
        assertEquals(List.of(1, 3), module.workers());

        c.buildings().onColonyTick(c); // full: no more hires
        assertEquals(List.of(1, 3), module.workers());
    }

    @Test
    void manualAndLockedNeverAutoHire() {
        for (HiringMode mode : List.of(HiringMode.MANUAL, HiringMode.LOCKED)) {
            Colony c = colony();
            c.citizens().restore(new CitizenData(1));
            WorkerModule module = module();
            module.setHiringMode(mode);
            Building b = buildingWith(module, 1, true);
            c.buildings().add(b);
            c.buildings().onColonyTick(c);
            assertTrue(module.workers().isEmpty(), mode.toString());
        }
    }

    @Test
    void defaultHiresOnlyWhenColonyAutoHiring() {
        Colony c = colony();
        c.citizens().restore(new CitizenData(1));
        c.settings().setAutoHiring(false);
        WorkerModule module = module();
        Building b = buildingWith(module, 1, true);
        c.buildings().add(b);
        c.buildings().onColonyTick(c);
        assertTrue(module.workers().isEmpty());

        c.settings().setAutoHiring(true);
        c.buildings().onColonyTick(c);
        assertEquals(List.of(1), module.workers());
    }

    @Test
    void level0BuildingCannotHireUnlessAssignableAtLevel0() {
        Colony c = colony();
        c.citizens().restore(new CitizenData(1));
        WorkerModule blocked = module();
        Building notBuilt = buildingWith(blocked, 0, false);
        c.buildings().add(notBuilt);
        c.buildings().onColonyTick(c);
        assertTrue(blocked.workers().isEmpty());

        Colony c2 = colony();
        c2.citizens().restore(new CitizenData(1));
        WorkerModule allowed = new WorkerModule(TestJobs.TYPE, Skill.Athletics, Skill.Strength, 2, true);
        Building stillLevel0 = buildingWith(allowed, 0, false);
        c2.buildings().add(stillLevel0);
        c2.buildings().onColonyTick(c2);
        assertEquals(List.of(1), allowed.workers());
    }

    @Test
    void workerModulePersists() {
        Colony c = colony();
        c.citizens().restore(new CitizenData(1));
        c.citizens().restore(new CitizenData(2));
        WorkerModule module = module();
        module.setHiringMode(HiringMode.AUTO);
        Building b = buildingWith(module, 1, true);
        c.buildings().add(b);
        assertTrue(module.hire(c, b, c.citizens().get(1).orElseThrow()));
        assertTrue(module.hire(c, b, c.citizens().get(2).orElseThrow()));

        JsonObject json = new JsonObject();
        module.write(json);

        WorkerModule restored = module();
        restored.read(json);
        assertEquals(module.workers(), restored.workers());
        assertEquals(HiringMode.AUTO, restored.hiringMode());
    }
}
