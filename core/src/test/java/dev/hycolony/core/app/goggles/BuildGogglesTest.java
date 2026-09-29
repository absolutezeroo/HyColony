package dev.hycolony.core.app.goggles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.building.Building;
import dev.hycolony.core.building.BuildingType;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.construction.hut.ConstructionBuildingTypes;
import dev.hycolony.core.construction.workorder.WorkOrder;
import dev.hycolony.core.construction.workorder.WorkOrderRefusal;
import dev.hycolony.core.construction.workorder.WorkOrderType;
import dev.hycolony.core.job.WorkerModule;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.Either;
import dev.hycolony.core.kernel.config.ColonyConfig;
import dev.hycolony.core.kernel.port.PreviewPort;
import dev.hycolony.core.testing.FakeBlueprints;
import dev.hycolony.core.testing.FakePreviews;
import dev.hycolony.core.testing.TestContexts;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BuildGogglesTest {
    private static final BlockPos BUILDER_HUT = new BlockPos(10, 64, 0);
    private static final BlockPos HOUSE = new BlockPos(20, 64, 0);
    /** 8 floor and 16 wall planks, a torch and a chest ({@link FakeBlueprints#hut}). */
    private static final int HUT_BLOCKS = 26;

    private final TestContexts t = new TestContexts();
    private final FakePreviews previews = new FakePreviews();
    private final UUID alice = UUID.randomUUID();
    private ColonyManager manager;
    private Colony colony;
    private BuildGoggles goggles;

    BuildGogglesTest() {
        start(ColonyConfig.defaults());
    }

    /** A fresh colony under {@code config}. */
    private void start(ColonyConfig config) {
        t.config = config;
        FakeBlueprints.registerBlocks(t.catalog);
        FakeBlueprints bps = new FakeBlueprints();
        for (int level = 1; level <= 5; level++) {
            bps.put(ConstructionBuildingTypes.BUILDER.id(), level, FakeBlueprints.hut(false));
            bps.put(ConstructionBuildingTypes.RESIDENCE.id(), level, FakeBlueprints.hut(false));
        }
        t.blueprints = bps;
        manager = t.manager();
        manager.foundation().begin(alice, "Alice", new BlockPos(0, 64, 0), 0);
        colony = manager.foundation().confirm(alice, "A").orElseThrow();
        goggles = new BuildGoggles(manager, previews);
        t.players.online.put(alice, HOUSE.offset(5, 0, 0));
    }

    private Building hut(BuildingType type, BlockPos pos, int level) {
        manager.huts().place(colony, type.id(), pos, 0);
        Building b = colony.buildings().at(pos).orElseThrow();
        b.setLevel(level);
        return b;
    }

    private void employedBuilder() {
        Building b = hut(ConstructionBuildingTypes.BUILDER, BUILDER_HUT, 1);
        CitizenData citizen = new CitizenData(1);
        colony.citizens().restore(citizen);
        assertTrue(b.module(WorkerModule.class).orElseThrow().hire(colony, b, citizen));
    }

    private WorkOrder order(WorkOrderType type, int houseLevel) {
        hut(ConstructionBuildingTypes.RESIDENCE, HOUSE, houseLevel);
        Either<WorkOrder, WorkOrderRefusal> r = colony.work().request(alice, HOUSE, type, "", Optional.empty());
        assertTrue(r instanceof Either.Left, () -> "refused: " + r);
        return ((Either.Left<WorkOrder, WorkOrderRefusal>) r).value();
    }

    private WorkOrder claimedBuild() {
        employedBuilder();
        WorkOrder o = order(WorkOrderType.BUILD, 0);
        colony.work().tick();
        assertTrue(o.claimedBy().isPresent());
        return o;
    }

    private void place(int dx, int dy, int dz) {
        t.blocks.blocks.put(HOUSE.offset(dx, dy, dz), FakeBlueprints.state(FakeBlueprints.PLANKS));
    }

    private void ticks(int n) {
        for (int i = 0; i < n; i++) {
            goggles.tick();
        }
    }

    private List<PreviewPort.Block> shownBlocks() {
        return previews.of(alice).values().iterator().next().blocks();
    }

    @Test
    void wearerNearAClaimedOrderSeesOnlyTheBlocksStillToPlace() {
        claimedBuild();
        place(-1, 0, -1);
        place(1, 2, 1);

        goggles.equip(alice);

        assertEquals(1, previews.of(alice).size());
        assertEquals(HOUSE, previews.of(alice).values().iterator().next().origin());
        assertEquals(HUT_BLOCKS - 2, shownBlocks().size());
        assertTrue(shownBlocks().stream().noneMatch(b -> b.offset().equals(new BlockPos(-1, 0, -1))));
    }

    @Test
    void unclaimedOrderIsNotShown() {
        employedBuilder();
        order(WorkOrderType.BUILD, 0); // not claimed until the work manager ticks

        goggles.equip(alice);

        assertTrue(previews.of(alice).isEmpty());
    }

    @Test
    void orderFartherThanFiftyBlocksIsNotShownUntilThePlayerComesWithinRange() {
        claimedBuild();
        t.players.online.put(alice, HOUSE.offset(51, 0, 0));

        goggles.equip(alice);
        assertTrue(previews.of(alice).isEmpty());

        t.players.online.put(alice, HOUSE.offset(50, 0, 0));
        ticks(BuildGoggles.CHECK_INTERVAL_TICKS);
        assertEquals(1, previews.of(alice).size());
    }

    @Test
    void configuredGoggleRangeReplacesTheFiftyBlockDefault() {
        ColonyConfig d = ColonyConfig.defaults();
        start(new ColonyConfig(
                d.gameplay(),
                d.claims(),
                d.permissions(),
                d.commands(),
                new ColonyConfig.Client(10),
                d.hycolony(),
                d.structurize()));
        claimedBuild();
        t.players.online.put(alice, HOUSE.offset(11, 0, 0));

        goggles.equip(alice);
        assertTrue(previews.of(alice).isEmpty());

        t.players.online.put(alice, HOUSE.offset(10, 0, 0));
        ticks(BuildGoggles.CHECK_INTERVAL_TICKS);
        assertEquals(1, previews.of(alice).size());
    }

    @Test
    void removeOrderShowsTheBlocksStillToRemove() {
        employedBuilder();
        place(-1, 0, -1);
        place(0, 1, 1);
        order(WorkOrderType.REMOVE, 1);
        colony.work().tick();

        goggles.equip(alice);

        assertEquals(2, shownBlocks().size());
    }

    @Test
    void previewIsRecreatedEveryHundredTicksOnlyWhenBlocksWerePlaced() {
        claimedBuild();
        goggles.equip(alice);
        ticks(BuildGoggles.REFRESH_TICKS);
        assertEquals(1, previews.shows.size()); // nothing placed: kept as is

        place(-1, 0, -1);
        ticks(BuildGoggles.REFRESH_TICKS - 1);
        assertEquals(1, previews.shows.size()); // never at every block
        ticks(1);
        assertEquals(2, previews.shows.size());
        assertEquals(HUT_BLOCKS - 1, shownBlocks().size());
    }

    @Test
    void equippingGogglesAlreadyWornRecreatesNothing() {
        claimedBuild();
        goggles.equip(alice);

        goggles.equip(alice); // another armour slot changed

        assertEquals(1, previews.shows.size());
    }

    @Test
    void cancelledOrderDisappearsAtTheNextCheck() {
        WorkOrder o = claimedBuild();
        goggles.equip(alice);

        colony.work().cancel(o.id());
        ticks(BuildGoggles.CHECK_INTERVAL_TICKS);

        assertTrue(previews.of(alice).isEmpty());
    }

    @Test
    void takingTheGogglesOffClearsEveryPreviewAndStopsTheUpdates() {
        claimedBuild();
        goggles.equip(alice);

        goggles.unequip(alice);
        place(-1, 0, -1);
        ticks(BuildGoggles.REFRESH_TICKS);

        assertTrue(previews.of(alice).isEmpty());
        assertEquals(1, previews.shows.size());
    }

    @Test
    void wearerWhoLeftTheWorldIsForgotten() {
        claimedBuild();
        goggles.equip(alice);

        t.players.online.remove(alice);
        ticks(BuildGoggles.CHECK_INTERVAL_TICKS);
        t.players.online.put(alice, HOUSE);
        ticks(BuildGoggles.REFRESH_TICKS);

        assertTrue(previews.of(alice).isEmpty());
    }
}
