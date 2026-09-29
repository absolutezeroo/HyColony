package dev.hycolony.core.construction.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.hycolony.core.construction.blueprint.Blueprint;
import dev.hycolony.core.construction.blueprint.BlueprintEntry;
import dev.hycolony.core.construction.blueprint.StructurePlan;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.BlockKey;
import dev.hycolony.core.kernel.item.BlockState;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.item.Workstation;
import dev.hycolony.core.testing.FakeCatalog;
import dev.hycolony.core.testing.FakeWorldBlocks;
import dev.hycolony.core.testing.crafting.FakeRecipeCatalog;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** What a crafting bench of the plan costs the builder: its item, plus the Hytale upgrades up to its planned tier. */
class NeededResourcesWorkstationTest {
    private static final BlockPos HUT = new BlockPos(0, 64, 0);
    private static final String FARMING = "Farmingbench";
    private static final BlockKey BENCH = new BlockKey("Bench_Farming");
    private static final BlockKey STONE = new BlockKey("Rock_Stone");
    /** What the recipe catalog names as the bench's item. */
    private static final ItemKey BENCH_I = new ItemKey("Bench_Farming_Item");
    /** What the item catalog says places the bench block: only used when the bench's own item is unknown. */
    private static final ItemKey STONE_I = new ItemKey("Rock_Stone_Item");

    private static final ItemKey A = new ItemKey("Ingredient_A");
    private static final ItemKey B = new ItemKey("Ingredient_B");

    private final FakeCatalog items = new FakeCatalog();
    private final FakeRecipeCatalog recipes = new FakeRecipeCatalog();

    NeededResourcesWorkstationTest() {
        items.itemForBlock.put(BENCH, BENCH_I);
        items.itemForBlock.put(STONE, STONE_I);
        recipes.upgradeCosts.put(FARMING + ":2", List.of(new ItemAmount(A, 5)));
        recipes.upgradeCosts.put(FARMING + ":3", List.of(new ItemAmount(B, 8)));
    }

    private static BlueprintEntry bench(int tier) {
        return new BlueprintEntry(
                new BlockPos(1, 0, 0), new BlockState(BENCH, 0), false, Optional.of(new Workstation(FARMING, tier)));
    }

    private NeededResources needs(BlueprintEntry... entries) {
        Blueprint bp = new Blueprint("bench", List.of(entries), new BlockPos(0, 0, 0), new BlockPos(2, 0, 0));
        return NeededResources.compute(StructurePlan.build(bp, HUT, items), new FakeWorldBlocks(), items, recipes);
    }

    @Test
    void tierThreeBenchCostsTheBenchAndTwoUpgrades() {
        NeededResources n = needs(bench(3));

        assertEquals(Map.of(BENCH_I, 1, A, 5, B, 8), n.remaining());
        assertEquals(14, n.total());
    }

    @Test
    void tierOneBenchCostsOnlyTheBench() {
        assertEquals(Map.of(BENCH_I, 1), needs(bench(1)).remaining());
    }

    @Test
    void everyUnitOfABenchFillsTheBuilderBuckets() {
        NeededResources n = needs(bench(3), new BlueprintEntry(new BlockPos(2, 0, 0), new BlockState(STONE, 0), false));

        assertEquals(15, n.sequence().size());
        assertEquals(List.of(Map.of(STONE_I, 1, BENCH_I, 1, A, 5, B, 8)), Buckets.split(n.sequence(), items::maxStack));
    }

    @Test
    void plainBlockCostsItsOwnItem() {
        BlueprintEntry stone = new BlueprintEntry(new BlockPos(2, 0, 0), new BlockState(STONE, 0), false);

        assertEquals(List.of(new ItemAmount(STONE_I, 1)), EntryCost.of(stone, items, recipes));
    }

    @Test
    void benchWithoutAnyKnownItemCostsOnlyItsUpgrades() {
        items.itemForBlock.clear();

        assertEquals(List.of(new ItemAmount(A, 5)), EntryCost.of(bench(2), items, recipes));
    }

    @Test
    void anItemAskedByTheBenchAndItsUpgradesIsSummed() {
        recipes.upgradeCosts.put(FARMING + ":2", List.of(new ItemAmount(BENCH_I, 2), new ItemAmount(A, 5)));

        assertEquals(List.of(new ItemAmount(BENCH_I, 3), new ItemAmount(A, 5)), EntryCost.of(bench(2), items, recipes));
    }
}
