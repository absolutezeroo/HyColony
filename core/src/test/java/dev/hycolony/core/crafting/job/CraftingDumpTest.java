package dev.hycolony.core.crafting.job;

import static dev.hycolony.core.crafting.job.CrafterRig.AXE;
import static dev.hycolony.core.crafting.job.CrafterRig.BUCKET;
import static dev.hycolony.core.crafting.job.CrafterRig.ESSENCE;
import static dev.hycolony.core.crafting.job.CrafterRig.SEEDS;
import static dev.hycolony.core.crafting.job.CrafterRig.seeds;
import static dev.hycolony.core.crafting.job.CrafterRig.until;
import static dev.hycolony.core.crafting.job.CraftingStep.CRAFT;
import static dev.hycolony.core.crafting.job.CraftingStep.IDLE;
import static dev.hycolony.core.crafting.job.CraftingStep.INVENTORY_FULL;
import static dev.hycolony.core.crafting.job.CraftingStep.QUERY_ITEMS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.crafting.recipe.BenchRequirement;
import dev.hycolony.core.crafting.recipe.Ingredient;
import dev.hycolony.core.crafting.recipe.Recipe;
import dev.hycolony.core.crafting.recipe.RecipeSource;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.item.ToolInfo;
import dev.hycolony.core.kernel.item.ToolType;
import dev.hycolony.core.logistics.warehouse.WarehouseBuilding;
import dev.hycolony.core.request.Request;
import dev.hycolony.core.request.model.Delivery;
import dev.hycolony.core.request.model.Pickup;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * The crafter's dump (MC AbstractEntityAICrafting getNextCraftingState, dumpInventory and afterDump): when it dumps,
 * what it keeps, when it asks for a courier and where its secondary outputs go.
 */
class CraftingDumpTest {
    private final CrafterRig rig = new CrafterRig();

    @Test
    void crafterWithSomethingToDumpDoesNotGoIdle() {
        rig.job().incrementActions();

        assertFalse(rig.job().createAI(rig.h.colony, rig.body).canGoIdle(), "it dumps first");
    }

    @Test
    void inventoryFullOfOtherItemsIsDumpedOncePerRecipe() {
        for (String other : List.of("Rock_Stone", "Soil_Dirt", "Wood_Oak_Trunk", "Ingredient_Fibre")) {
            rig.crafter.inventory().insert(new ItemAmount(new ItemKey(other), 1), _ -> 64);
        }
        rig.stock(ESSENCE, 20);
        rig.task(rig.ask(10));

        assertEquals(INVENTORY_FULL, rig.toRecipe(), "4 slots of other items: more than 3");
        assertEquals(QUERY_ITEMS, rig.work.getRecipe(), "once per recipe, even if the dump left them");
    }

    /**
     * MC WorkerUtil.isPartOfRecipe knows the grid's tools and the secondary outputs, not the recipe's required tool.
     */
    @Test
    void toolTheRecipeRequiresCountsAsAnOtherItem() {
        CrafterRig axe = new CrafterRig(seeds(List.of(), Optional.of(ToolType.AXE)));
        axe.t.catalog.tools.put(AXE, new ToolInfo(ToolType.AXE, 1, 1f));
        for (String other : List.of("Rock_Stone", "Soil_Dirt", "Wood_Oak_Trunk")) {
            axe.crafter.inventory().insert(new ItemAmount(new ItemKey(other), 1), _ -> 64);
        }
        axe.crafter.inventory().insert(new ItemAmount(AXE, 1), _ -> 1);
        axe.stock(ESSENCE, 20);
        axe.task(axe.ask(10));

        assertEquals(INVENTORY_FULL, axe.toRecipe(), "3 slots of other items and the axe: more than 3");
    }

    @Test
    void crafterDumpsTheToolItsRecipeMakes() {
        Recipe hatchet = new Recipe(
                List.of(new Ingredient.OfItem(ESSENCE, 2)),
                new ItemAmount(AXE, 1),
                List.of(),
                new BenchRequirement("Farmingbench", List.of("Seeds"), 1),
                Optional.empty(),
                new RecipeSource.Hytale(AXE.id()),
                false);
        CrafterRig smith = new CrafterRig(hatchet);
        smith.t.catalog.tools.put(AXE, new ToolInfo(ToolType.AXE, 1, 1f));
        smith.stock(ESSENCE, 2);
        Request task = smith.task(smith.ask(AXE, 1));
        assertEquals(CRAFT, smith.toCraft());
        assertEquals(INVENTORY_FULL, until(smith.work::craft, CRAFT));
        assertEquals(1, smith.carried(AXE));

        assertEquals(IDLE, until(smith.work::dump, INVENTORY_FULL));

        assertEquals(0, smith.carried(AXE), "MC keepX: the test crafter's hut keeps no tool in the inventory");
        assertEquals(1, smith.inHut(AXE), "in the hut, for the courier to deliver");
        assertEquals(List.of(new ItemAmount(AXE, 1)), task.deliveries());
    }

    @Test
    void dumpAsksNoPickupWhileATaskIsUnderWay() {
        rig.stock(ESSENCE, 4);
        rig.task(rig.ask(2));
        assertEquals(CRAFT, rig.toCraft());
        rig.job().incrementActions(); // a dump due mid-task

        assertEquals(IDLE, until(rig.work::dump, INVENTORY_FULL));

        assertEquals(List.of(), pickups());
        assertEquals(4, rig.inHut(ESSENCE), "what it carried went back to the hut");
    }

    @Test
    void dumpWithoutTaskAsksForAPickup() {
        rig.crafter.inventory().insert(new ItemAmount(SEEDS, 3), _ -> 64);
        rig.job().incrementActions();

        assertEquals(IDLE, until(rig.work::dump, INVENTORY_FULL));

        assertEquals(1, pickups().size());
    }

    @Test
    void secondaryOutputsGoToTheNearestWarehouse() {
        CrafterRig bucket = new CrafterRig(seeds(List.of(new ItemAmount(BUCKET, 1)), Optional.empty()));
        Building near = warehouse(bucket, new BlockPos(20, 64, 0), 1);
        Building far = warehouse(bucket, new BlockPos(60, 64, 0), 1);
        warehouse(bucket, new BlockPos(12, 64, 0), 0); // nearer, but not built yet
        bucket.stock(ESSENCE, 2);
        Request task = bucket.task(bucket.ask(1));
        assertEquals(CRAFT, bucket.toCraft());
        assertEquals(INVENTORY_FULL, until(bucket.work::craft, CRAFT));
        assertEquals(List.of(new ItemAmount(SEEDS, 1)), task.deliveries(), "only the output is the task's");
        bucket.tasks().secondaryOutputs().merge(BUCKET, 69, Integer::sum); // 70 in all: more than a stack

        assertEquals(IDLE, until(bucket.work::dump, INVENTORY_FULL));

        List<Delivery> sent = bucket.m().byRequester(near.requesterId()).stream()
                .map(Request::requestable)
                .filter(Delivery.class::isInstance)
                .map(Delivery.class::cast)
                .toList();
        assertEquals(
                List.of(new ItemAmount(BUCKET, 64), new ItemAmount(BUCKET, 6)),
                sent.stream().map(Delivery::stack).toList());
        for (Delivery d : sent) {
            assertEquals(bucket.h.hut.position(), d.start());
            assertEquals(near.requesterId(), d.target());
            assertEquals(Pickup.MAX_BUILDING_PRIORITY, d.priority());
        }
        assertEquals(List.of(), bucket.m().byRequester(far.requesterId()));
        assertTrue(bucket.tasks().secondaryOutputs().isEmpty());
    }

    private List<Request> pickups() {
        return rig.m().byRequester(rig.h.hut.requesterId()).stream()
                .filter(r -> r.requestable() instanceof Pickup)
                .toList();
    }

    private static Building warehouse(CrafterRig r, BlockPos pos, int level) {
        Building w = Building.create(WarehouseBuilding.TYPE, pos, 0);
        w.setLevel(level);
        w.setBuilt(level > 0);
        r.h.colony.buildings().add(w);
        return w;
    }
}
