package dev.hycolony.core.app.hut;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.app.ui.BuildingView;
import dev.hycolony.core.app.ui.CitizenRow;
import dev.hycolony.core.building.Building;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.citizen.Skill;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.construction.hut.ConstructionBuildingTypes;
import dev.hycolony.core.job.HiringMode;
import dev.hycolony.core.job.WorkerModule;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.testing.TestContexts;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/** MC WindowHireWorker: who is listed, in which order, with which button, skills and mode. */
class HireWorkerViewTest {
    private static final BlockPos HUT = new BlockPos(30, 64, 0);
    private static final BlockPos OTHER = new BlockPos(0, 64, 30);
    private final TestContexts t = new TestContexts();
    private final UUID alice = UUID.randomUUID();
    private final ColonyManager manager = t.manager();
    private final Colony colony;
    private final Building hut;
    private final Building other;

    HireWorkerViewTest() {
        manager.foundation().begin(alice, "Alice", new BlockPos(0, 64, 0), 0);
        colony = manager.foundation().confirm(alice, "A").orElseThrow();
        manager.huts().place(colony, ConstructionBuildingTypes.BUILDER.id(), HUT, 0, alice);
        manager.huts().place(colony, ConstructionBuildingTypes.BUILDER.id(), OTHER, 0, alice);
        hut = colony.buildings().at(HUT).orElseThrow();
        other = colony.buildings().at(OTHER).orElseThrow();
    }

    private CitizenData citizen(int id, String name, @Nullable BlockPos home) {
        CitizenData d = new CitizenData(id);
        d.setName(name);
        d.setHomeBuilding(home);
        colony.citizens().restore(d);
        return d;
    }

    private void hire(Building b, CitizenData d) {
        assertTrue(b.module(WorkerModule.class).orElseThrow().hire(colony, b, d));
    }

    private HireView view() {
        manager.windows().openBuilding(alice, HUT);
        return ((BuildingView) t.ui.shown.get(alice)).hire().orElseThrow();
    }

    private static List<String> names(List<HireView.Candidate> rows) {
        return rows.stream().map(HireView.Candidate::name).toList();
    }

    @Test
    void employedHereComeFirstThenUnemployedByHomeDistanceThenName() {
        citizen(1, "Zed", null);
        citizen(2, "Abe", null);
        citizen(3, "Far", new BlockPos(100, 64, 0)); // 70 blocks: 80 once rounded
        citizen(4, "Near", new BlockPos(60, 64, 0)); // 30 blocks: 40 once rounded
        hire(hut, citizen(5, "Mine", null));
        assertEquals(List.of("Mine", "Near", "Far", "Abe", "Zed"), names(view().listed(false)));
    }

    @Test
    void homesInTheSame40BlockBucketSortByName() {
        citizen(1, "Zed", new BlockPos(55, 64, 0)); // 25 blocks: 40 once rounded
        citizen(2, "Abe", new BlockPos(65, 64, 0)); // 35 blocks: 40 once rounded
        assertEquals(List.of("Abe", "Zed"), names(view().listed(false)));
    }

    @Test
    void aHutThatMayNotAssignYetIsFullAsMc() {
        BlockPos farmPos = new BlockPos(-30, 64, 0);
        manager.huts().place(colony, dev.hycolony.core.farming.hut.FarmerHut.TYPE_ID, farmPos, 0, alice);
        citizen(1, "Ann", null);
        manager.windows().openBuilding(alice, farmPos);
        HireView v = ((BuildingView) t.ui.shown.get(alice)).hire().orElseThrow();
        assertTrue(v.full(), "MC isFull: !allowsAssignment() || full");
        assertEquals(HireView.Button.NONE, v.button(v.all().getFirst(), false));
    }

    @Test
    void workersOfOtherHutsShowOnlyWithShowEmployedAndLast() {
        citizen(1, "Free", null);
        hire(other, citizen(2, "Busy", null));
        citizen(3, "Kid", null).setChild(true);
        HireView v = view();
        assertEquals(List.of("Free"), names(v.listed(false)));
        assertEquals(List.of("Free", "Busy"), names(v.listed(true)), "children are never listed");
    }

    @Test
    void buttonsFollowMc() {
        CitizenData free = citizen(1, "Free", null);
        CitizenData busy = citizen(2, "Busy", null);
        hire(other, busy);
        HireView v = view();
        HireView.Candidate freeRow = v.all().stream()
                .filter(c -> c.citizenId() == free.id())
                .findFirst()
                .orElseThrow();
        HireView.Candidate busyRow = v.all().stream()
                .filter(c -> c.citizenId() == busy.id())
                .findFirst()
                .orElseThrow();
        assertEquals(HireView.Button.HIRE, v.button(freeRow, false));
        assertEquals(HireView.Button.HIRE, v.button(busyRow, true), "show employed lets the hut take them");
        hire(hut, free);
        HireView full = view();
        assertTrue(full.full(), "a builder's hut takes one worker");
        HireView.Candidate mine = full.all().getFirst();
        assertEquals(HireView.Button.FIRE, full.button(mine, false));
        HireView.Candidate stillBusy = full.all().stream()
                .filter(c -> c.citizenId() == busy.id())
                .findFirst()
                .orElseThrow();
        assertEquals(HireView.Button.NONE, full.button(stillBusy, true));
    }

    @Test
    void skillsListTheJobsPrimaryThenSecondaryFirst() {
        citizen(1, "Ann", null);
        HireView v = view();
        WorkerModule w = hut.module(WorkerModule.class).orElseThrow();
        List<Skill> order = v.all().getFirst().skills().stream()
                .map(CitizenRow.SkillLevel::skill)
                .toList();
        assertEquals(w.primary(), order.get(0));
        assertEquals(w.secondary(), order.get(1));
        assertEquals(Skill.values().length, order.size());
        assertEquals(Optional.of(w.primary()), v.primary());
    }

    @Test
    void theModeAndJobAreTheHuts() {
        HireView v = view();
        assertEquals(HiringMode.DEFAULT, v.mode());
        assertEquals("hycolony:builder", v.jobId());
        assertFalse(v.full());
    }
}
