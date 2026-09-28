package dev.hycolony.core.testing;

import dev.hycolony.core.CoreFeatures;
import dev.hycolony.core.building.BuildingRegistry;
import dev.hycolony.core.building.BuildingType;
import dev.hycolony.core.citizen.CitizenNames;
import dev.hycolony.core.colony.ColonyContext;
import dev.hycolony.core.colony.ConstructionPorts;
import dev.hycolony.core.construction.blueprint.Blueprint;
import dev.hycolony.core.construction.blueprint.BlueprintSource;
import dev.hycolony.core.crafting.recipe.CraftingRules;
import dev.hycolony.core.crafting.recipe.CraftingSetup;
import dev.hycolony.core.job.JobRegistry;
import dev.hycolony.core.kernel.WorldKey;
import dev.hycolony.core.kernel.config.ColonyConfig;
import dev.hycolony.core.kernel.event.EventBus;
import dev.hycolony.core.testing.crafting.FakeRecipeCatalog;
import dev.hycolony.core.testing.crafting.TestCrafters;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.function.Supplier;
import java.util.random.RandomGenerator;

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
    public final FakeRecipeCatalog recipes = new FakeRecipeCatalog();
    public CraftingRules craftingRules = CraftingRules.EMPTY;
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
    /** The random source of each context; seeded, so a run replays the same draws. */
    public Supplier<RandomGenerator> random = () -> new Random(1234);

    /** Building types a test adds on top of the defaults, as a pack would. */
    public final List<BuildingType> extraBuildingTypes = new ArrayList<>();

    private BuildingRegistry buildings() {
        BuildingRegistry r = new BuildingRegistry();
        CoreFeatures.register(r, new JobRegistry());
        extraBuildingTypes.forEach(r::register);
        return r;
    }

    private static JobRegistry jobs() {
        JobRegistry r = new JobRegistry();
        CoreFeatures.register(new BuildingRegistry(), r);
        r.register(TestCrafters.JOB);
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
                random.get(),
                bus,
                new ConstructionPorts(
                        catalog,
                        blocks,
                        containers,
                        playerInventory,
                        blueprints,
                        effects,
                        new CraftingSetup(recipes, craftingRules)));
    }
}
