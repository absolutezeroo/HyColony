package dev.hycolony.core.citizen.home;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.building.BuildingType;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.permission.Permissions;
import dev.hycolony.core.colony.territory.TerritoryIndex;
import dev.hycolony.core.construction.hut.ConstructionBuildingTypes;
import dev.hycolony.core.job.WorkerModule;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.testing.TestContexts;
import java.util.List;
import java.util.OptionalInt;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ResidentsViewsTest {
    private static final BlockPos HOUSE = new BlockPos(100, 64, 0);
    private final TestContexts t = new TestContexts();
    private final UUID owner = UUID.randomUUID();
    private final Colony c = new Colony(
            t.context(),
            new TerritoryIndex(),
            new Colony.Founding(1, "T", new BlockPos(0, 64, 0), Permissions.createDefault(owner, "A")));
    private final Building house = hut(ConstructionBuildingTypes.RESIDENCE, HOUSE, 1);

    private Building hut(BuildingType type, BlockPos at, int level) {
        Building b = Building.create(type, at, 0);
        b.setLevel(level);
        b.setBuilt(true);
        c.buildings().add(b);
        return b;
    }

    private CitizenData citizen(int id) {
        CitizenData d = new CitizenData(id);
        d.setName("C" + id);
        c.citizens().restore(d);
        return d;
    }

    /** Hires {@code d} at a new builder hut at {@code at}: its workplace. */
    private void worksAt(CitizenData d, BlockPos at) {
        Building work = hut(ConstructionBuildingTypes.BUILDER, at, 1);
        assertTrue(work.module(WorkerModule.class).orElseThrow().hire(c, work, d));
    }

    /** Makes {@code d} live in a new residence at {@code at}. */
    private void livesAt(CitizenData d, BlockPos at) {
        Building home = hut(ConstructionBuildingTypes.RESIDENCE, at, 1);
        assertTrue(home.module(LivingModule.class).orElseThrow().assign(c, home, d));
    }

    private ResidentsView view(UUID viewer) {
        return ResidentsViews.of(c, house, house.module(LivingModule.class).orElseThrow(), viewer);
    }

    @Test
    void candidatesHomelessFirstThenByWorkDistance() {
        citizen(1);
        worksAt(citizen(2), new BlockPos(130, 64, 0));
        CitizenData housedNear = citizen(3);
        worksAt(housedNear, new BlockPos(110, 64, 0));
        livesAt(housedNear, new BlockPos(100, 64, 50));
        livesAt(citizen(4), new BlockPos(100, 64, 80));

        List<Integer> order = view(owner).candidates().stream()
                .map(ResidentsView.Candidate::citizenId)
                .toList();

        assertEquals(List.of(1, 2, 3, 4), order);
        assertEquals(OptionalInt.of(30), view(owner).candidates().get(1).workDistance());
    }

    @Test
    void candidateLinesSayCloserAndFar() {
        CitizenData d = citizen(1);
        worksAt(d, new BlockPos(105, 64, 0));
        livesAt(d, new BlockPos(505, 64, 0));

        ResidentsView.Candidate line = view(owner).candidates().getFirst();

        assertEquals(OptionalInt.of(5), line.workDistance());
        assertTrue(line.closer());
        assertFalse(line.home().homeless());
        assertEquals(OptionalInt.of(400), line.home().currentDistance());
        assertTrue(line.home().far());
    }

    @Test
    void residentsAndWorkFromHomeAreNoCandidates() {
        CitizenData resident = citizen(1);
        house.module(LivingModule.class).orElseThrow().assign(c, house, resident);
        worksAt(resident, new BlockPos(500, 64, 0));
        CitizenData atWork = citizen(2);
        worksAt(atWork, new BlockPos(150, 64, 0));
        atWork.setHomeBuilding(atWork.workBuilding());

        ResidentsView v = view(owner);

        assertTrue(v.candidates().isEmpty());
        assertEquals(1, v.assigned());
        assertEquals(1, v.max());
        ResidentsView.Resident line = v.residents().getFirst();
        assertEquals(OptionalInt.of(400), line.workDistance());
        assertTrue(line.far());
    }

    @Test
    void viewerWithoutManageHutsCannotManage() {
        assertTrue(view(owner).canManage());
        assertFalse(view(UUID.randomUUID()).canManage());
        assertFalse(view(owner).manual()); // DEFAULT with the colony's auto-housing on
    }
}
