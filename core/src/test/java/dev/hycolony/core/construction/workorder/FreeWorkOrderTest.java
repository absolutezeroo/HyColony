package dev.hycolony.core.construction.workorder;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.building.Building;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.construction.blueprint.Blueprint;
import dev.hycolony.core.construction.blueprint.BlueprintEntry;
import dev.hycolony.core.construction.blueprint.BlueprintSource;
import dev.hycolony.core.construction.hut.ConstructionBuildingTypes;
import dev.hycolony.core.job.WorkerModule;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.Either;
import dev.hycolony.core.kernel.config.ColonyConfig;
import dev.hycolony.core.kernel.item.BlockKey;
import dev.hycolony.core.kernel.item.BlockState;
import dev.hycolony.core.testing.TestContexts;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Which orders are free: creative operators (configurable) or every order under builderInfiniteResources. */
class FreeWorkOrderTest {
    private static final ColonyConfig D = ColonyConfig.defaults();
    private final TestContexts t = new TestContexts();
    private final UUID alice = UUID.randomUUID();
    private Colony colony;
    private ColonyManager manager;

    FreeWorkOrderTest() {
        t.blueprints = new BlueprintSource() {
            @Override
            public Optional<Blueprint> load(String style, String buildingTypeId, int level, int rotation) {
                BlockPos o = new BlockPos(1, 0, 0);
                return Optional.of(new Blueprint(
                        "bp", List.of(new BlueprintEntry(o, new BlockState(new BlockKey("Stone"), 0), false)), o, o));
            }

            @Override
            public List<String> styles() {
                return List.of("medieval");
            }
        };
    }

    private static ColonyConfig config(boolean infinite, boolean creativeOps) {
        return new ColonyConfig(
                D.gameplay(),
                D.claims(),
                D.permissions(),
                D.commands(),
                D.client(),
                new ColonyConfig.HyColony(D.hycolony().autosaveIntervalMinutes(), infinite, creativeOps),
                D.structurize());
    }

    private void start(ColonyConfig config) {
        t.config = config;
        manager = t.manager();
        manager.foundation().begin(alice, "Alice", new BlockPos(0, 64, 0), 0);
        colony = manager.foundation().confirm(alice, "A").orElseThrow();
        Building builder = hut(ConstructionBuildingTypes.BUILDER, new BlockPos(10, 64, 0), 5);
        CitizenData c = new CitizenData(1);
        colony.citizens().restore(c);
        assertTrue(builder.module(WorkerModule.class).orElseThrow().hire(colony, builder, c));
    }

    private Building hut(dev.hycolony.core.building.BuildingType type, BlockPos pos, int level) {
        manager.huts().place(colony, type.id(), pos, 0, UUID.randomUUID());
        Building b = colony.buildings().at(pos).orElseThrow();
        b.setLevel(level);
        return b;
    }

    private WorkOrder order(int x, int level, WorkOrderType type) {
        Building b = hut(ConstructionBuildingTypes.RESIDENCE, new BlockPos(x, 64, 0), level);
        var r = colony.work().request(alice, b.position(), type, "", Optional.empty());
        assertTrue(r instanceof Either.Left, () -> "refused: " + r);
        return ((Either.Left<WorkOrder, WorkOrderRefusal>) r).value();
    }

    @Test
    void creativeOperatorGetsFreeOrdersButNeverRemove() {
        t.players.creativeOperators.add(alice);
        start(D);
        assertTrue(order(20, 0, WorkOrderType.BUILD).free());
        assertTrue(order(30, 1, WorkOrderType.UPGRADE).free());
        assertTrue(order(40, 1, WorkOrderType.REPAIR).free());
        assertFalse(order(50, 1, WorkOrderType.REMOVE).free());
    }

    @Test
    void otherPlayersGetNormalOrders() {
        start(D);
        assertFalse(order(20, 0, WorkOrderType.BUILD).free());
    }

    @Test
    void creativeOperatorsPayWhenTheSettingIsOff() {
        t.players.creativeOperators.add(alice);
        start(config(false, false));
        assertFalse(order(20, 0, WorkOrderType.BUILD).free());
    }

    @Test
    void infiniteResourcesMakesEveryOrderFreeButRemove() {
        start(config(true, false));
        assertTrue(order(20, 0, WorkOrderType.BUILD).free());
        assertTrue(order(30, 1, WorkOrderType.UPGRADE).free());
        assertFalse(order(40, 1, WorkOrderType.REMOVE).free());
    }

    @Test
    void freeSurvivesSaveAndLoadAndDefaultsToFalse() {
        t.players.creativeOperators.add(alice);
        start(D);
        JsonObject saved = order(20, 0, WorkOrderType.BUILD).write();
        assertTrue(WorkOrder.read(saved).orElseThrow().free());
        saved.remove("free"); // a save from before the flag
        assertFalse(WorkOrder.read(saved).orElseThrow().free());
    }
}
