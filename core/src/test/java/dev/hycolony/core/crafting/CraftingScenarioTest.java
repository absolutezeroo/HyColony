package dev.hycolony.core.crafting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import dev.hycolony.core.building.Building;
import dev.hycolony.core.building.BuildingType;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.permission.Permissions;
import dev.hycolony.core.colony.territory.TerritoryIndex;
import dev.hycolony.core.construction.hut.ConstructionBuildingTypes;
import dev.hycolony.core.crafting.module.CraftingHut;
import dev.hycolony.core.crafting.module.CraftingModule;
import dev.hycolony.core.crafting.recipe.CraftingRules;
import dev.hycolony.core.crafting.recipe.RecipeFixtures;
import dev.hycolony.core.crafting.recipe.RecipeId;
import dev.hycolony.core.job.JobAI;
import dev.hycolony.core.job.WorkerModule;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.Vec3;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.item.Workstation;
import dev.hycolony.core.logistics.courier.DeliverymanHut;
import dev.hycolony.core.logistics.warehouse.WarehouseBuilding;
import dev.hycolony.core.request.Request;
import dev.hycolony.core.request.model.Crafting;
import dev.hycolony.core.request.model.Delivery;
import dev.hycolony.core.request.model.RequestState;
import dev.hycolony.core.request.model.RequestToken;
import dev.hycolony.core.request.model.StackRequest;
import dev.hycolony.core.testing.TestContexts;
import dev.hycolony.core.testing.crafting.TestCrafters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * The spec's scenario, end to end with the real resolvers and AIs: a residence asks for seeds, the crafter hut's
 * crafter makes them from the essence in its chest, and the courier brings them to the residence. The colony is the
 * courier tests' one (CourierAITestBase), with the crafter hut added.
 */
class CraftingScenarioTest {
    private static final ItemKey SEEDS = new ItemKey("Plant_Seeds_Wheat");
    private static final ItemKey ESSENCE = RecipeFixtures.ESSENCE;
    private static final BlockPos RACK = new BlockPos(2, 64, 0);
    private static final int MAX_TICKS = 20_000;

    private final TestContexts t = new TestContexts();
    private final UUID owner = UUID.randomUUID();
    private final Colony colony;
    private final List<JobAI> ais = new ArrayList<>();
    private final List<Request> made = new ArrayList<>();

    CraftingScenarioTest() {
        t.craftingRules =
                CraftingRules.parse(JsonParser.parseString(CraftingHut.RULES).getAsJsonObject(), w -> {});
        t.bodies.instant = true;
        t.players.online.put(owner, new BlockPos(0, 64, 0)); // keeps the colony active: its requests tick
        colony = new Colony(
                t.context(),
                new TerritoryIndex(),
                new Colony.Founding(1, "T", new BlockPos(0, 64, 0), Permissions.createDefault(owner, "Owner")));
        colony.requests().setCreationListener(made::add);
    }

    @Test
    void playerRequestIsCraftedAndDelivered() {
        Building warehouse = building(WarehouseBuilding.TYPE, new BlockPos(0, 64, 0), 1);
        warehouse.registeredBlocks().addContainer(RACK);
        Building courierHut = building(DeliverymanHut.TYPE, new BlockPos(-20, 64, 0), 5);
        Building residence = building(ConstructionBuildingTypes.RESIDENCE, new BlockPos(10, 64, 0), 1);
        Building crafterHut = building(TestCrafters.HUT, new BlockPos(30, 64, 0), 1);
        crafterHut.registeredBlocks().addWorkstation(new BlockPos(31, 64, 0), new Workstation("Farmingbench", 1));
        RecipeId seeds = colony.recipes().checkOrAdd(RecipeFixtures.at("Farmingbench", "Seeds", SEEDS.id()));
        assertTrue(crafterHut.module(CraftingModule.class).orElseThrow().learn(colony, crafterHut, seeds, owner));
        hire(courierHut, 1);
        colony.buildings().onColonyTick(colony); // attaches the courier to the warehouse
        hire(crafterHut, 2);
        t.containers.containers.put(crafterHut.position(), new LinkedHashMap<>(Map.of(ESSENCE, 20)));

        RequestToken asked = colony.requests().createAndAssign(residence, new StackRequest(SEEDS, 10, 10, true), -1);
        for (int tick = 0; tick < MAX_TICKS && !completed(asked); tick++) {
            t.clock.tick++;
            colony.tick();
            ais.forEach(JobAI::tick);
        }

        assertFalse(colony.isSuspended(), "the colony threw");
        assertTrue(
                completed(asked),
                () -> "not served; requests " + colony.requests().all());
        assertTrue(made.stream().anyMatch(r -> r.requestable() instanceof Crafting), "crafted, not found elsewhere");
        assertEquals(
                List.of(new ItemAmount(SEEDS, 10)),
                made.stream()
                        .filter(r -> r.requestable() instanceof Delivery)
                        .map(r -> ((Delivery) r.requestable()).stack())
                        .toList(),
                "the batch goes to the residence as one stack");
        assertEquals(10, t.containers.count(residence.containers(), SEEDS));
        assertEquals(0, everywhere(ESSENCE), "all the essence went into the seeds");
        assertEquals(10, everywhere(SEEDS), "no seed made twice or lost");
    }

    private boolean completed(RequestToken token) {
        return colony.requests().get(token).map(Request::state).orElse(null) == RequestState.COMPLETED;
    }

    private Building building(BuildingType type, BlockPos pos, int level) {
        Building b = Building.create(type, pos, 0);
        b.setLevel(level);
        b.setBuilt(true);
        colony.buildings().add(b);
        return b;
    }

    /** Hires citizen {@code id} at {@code hut}, whose job AI starts at the hut; a starving citizen earns no XP. */
    private void hire(Building hut, int id) {
        CitizenData citizen = new CitizenData(id);
        citizen.setSaturation(CitizenData.MAX_SATURATION);
        colony.citizens().restore(citizen);
        assertTrue(hut.module(WorkerModule.class).orElseThrow().hire(colony, hut, citizen));
        ais.add(citizen.job().orElseThrow().createAI(colony, t.bodies.existing(1, id, Vec3.center(hut.position()))));
    }

    /** Every {@code item} in the colony: containers and citizens' inventories. */
    private int everywhere(ItemKey item) {
        int n = 0;
        for (Map<ItemKey, Integer> c : t.containers.containers.values()) {
            n += c.getOrDefault(item, 0);
        }
        for (CitizenData c : colony.citizens().all()) {
            n += c.inventory().count(item);
        }
        return n;
    }
}
