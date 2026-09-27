package dev.hycolony.core.testing;

import dev.hycolony.core.building.BuildingRegistry;
import dev.hycolony.core.building.BuildingTypes;
import dev.hycolony.core.citizen.CitizenNames;
import dev.hycolony.core.colony.ColonyContext;
import dev.hycolony.core.colony.ConstructionPorts;
import dev.hycolony.core.construction.blueprint.Blueprint;
import dev.hycolony.core.construction.blueprint.BlueprintSource;
import dev.hycolony.core.construction.hut.ConstructionBuildingTypes;
import dev.hycolony.core.job.JobRegistry;
import dev.hycolony.core.kernel.WorldKey;
import dev.hycolony.core.kernel.config.ColonyConfig;
import dev.hycolony.core.kernel.event.EventBus;
import dev.hycolony.core.logistics.courier.DeliverymanHut;
import dev.hycolony.core.logistics.warehouse.WarehouseBuilding;
import java.util.List;
import java.util.Optional;
import java.util.Random;

/** A fully faked colony context. Fields are public so tests can steer the fakes. */
public final class TestContexts {
    public final FakeClock clock = new FakeClock();
    public final FakeBodies bodies = new FakeBodies();
    public final FakeWorld world = new FakeWorld();
    public final FakeNotifier notifier = new FakeNotifier();
    public final FakePlayers players = new FakePlayers();
    public final FakeUi ui = new FakeUi();
    public final EventBus bus = new EventBus();
    public final FakeCatalog catalog = new FakeCatalog();
    public final FakeWorldBlocks blocks = new FakeWorldBlocks();
    public final FakeContainers containers = new FakeContainers();
    public final FakePlayerInventory playerInventory = new FakePlayerInventory();
    public final FakeWorldEffects effects = new FakeWorldEffects();
    public BlueprintSource blueprints = new BlueprintSource() {
        @Override
        public Optional<Blueprint> load(String style, String buildingTypeId, int level, int rotation) {
            return Optional.empty();
        }

        @Override
        public List<String> styles() {
            return List.of();
        }
    };
    public ColonyConfig config = ColonyConfig.defaults();
    public JobRegistry jobs = jobs();

    private static BuildingRegistry buildings() {
        BuildingRegistry r = BuildingTypes.defaults();
        ConstructionBuildingTypes.register(r);
        WarehouseBuilding.register(r);
        DeliverymanHut.register(r);
        return r;
    }

    private static JobRegistry jobs() {
        JobRegistry r = JobRegistry.defaults();
        ConstructionBuildingTypes.register(r);
        DeliverymanHut.register(r);
        return r;
    }

    public ColonyContext context() {
        return new ColonyContext(
                new WorldKey("world"),
                config,
                clock,
                bodies,
                world,
                notifier,
                ui,
                players,
                buildings(),
                jobs,
                CitizenNames.loadDefault(),
                new Random(1234),
                bus,
                new ConstructionPorts(catalog, blocks, containers, playerInventory, blueprints, effects));
    }
}
