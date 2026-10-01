package dev.hycolony.core.app.view;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.app.hut.HireView.Candidate;
import dev.hycolony.core.app.hut.HireView.HomeLine;
import dev.hycolony.core.app.ui.BuildingView;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.construction.hut.ConstructionBuildingTypes;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.testing.TestContexts;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** MC WindowHireWorker: candidates by home distance to the hut, rounded to 40 blocks, and where each one lives. */
class HireListTest {
    private static final BlockPos HUT = new BlockPos(30, 64, 0);
    private final TestContexts t = new TestContexts();
    private final UUID alice = UUID.randomUUID();
    private final ColonyManager manager = t.manager();
    private final Colony colony = found();

    private Colony found() {
        manager.foundation().begin(alice, "Alice", new BlockPos(0, 64, 0), 0);
        Colony c = manager.foundation().confirm(alice, "A").orElseThrow();
        manager.huts().place(c, ConstructionBuildingTypes.BUILDER.id(), HUT, 0, UUID.randomUUID());
        return c;
    }

    private CitizenData citizen(int id, String name, BlockPos home) {
        CitizenData d = new CitizenData(id);
        d.setName(name);
        d.setHomeBuilding(home);
        colony.citizens().restore(d);
        return d;
    }

    private BuildingView view() {
        manager.windows().openBuilding(alice, HUT);
        return (BuildingView) t.ui.shown.get(alice);
    }

    @Test
    void hireListSortsByHomeDistanceThenName() {
        citizen(1, "Zed", null);
        citizen(2, "Abe", null);
        citizen(3, "Far", new BlockPos(100, 64, 0)); // 70 blocks: 80 once rounded
        citizen(4, "Near", new BlockPos(60, 64, 0)); // 30 blocks: 40 once rounded

        List<String> names = rows().stream().map(Candidate::name).toList();

        assertEquals(List.of("Near", "Far", "Abe", "Zed"), names); // the homeless count as 100
    }

    @Test
    void hireRowsSayWhereTheyLive() {
        citizen(1, "Home", null);
        citizen(2, "Here", HUT);
        citizen(3, "Away", new BlockPos(60, 64, 0));

        List<Candidate> rows = rows();

        assertEquals(List.of(2, 3, 1), rows.stream().map(Candidate::citizenId).toList());
        assertEquals(HomeLine.LIVES_HERE, rows.get(0).home());
        assertEquals(HomeLine.DISTANCE, rows.get(1).home());
        assertEquals(30, rows.get(1).homeDistance());
        assertEquals(HomeLine.HOMELESS, rows.get(2).home());
    }

    private List<Candidate> rows() {
        return view().hire().orElseThrow().listed(false);
    }
}
