package dev.hycolony.core.construction.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.hycolony.core.construction.blueprint.BlueprintEntry;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.BlockItems;
import dev.hycolony.core.kernel.item.BlockKey;
import dev.hycolony.core.kernel.item.BlockState;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.testing.FakeCatalog;
import dev.hycolony.core.testing.crafting.FakeRecipeCatalog;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * The item a plain cell costs is the one that places its block (Structurize BlockUtils.getItemStackFromBlockState); the
 * Hytale facts are those of the pre.5 assets (docs/research/audit-monde-hytale.md A-15).
 */
class EntryCostTest {
    private static final BlockKey WALL_TORCH = new BlockKey("Wood_Torch_Wall");
    private static final BlockKey LARGE_CHEST = new BlockKey("Furniture_Crude_Chest_Large");
    private static final BlockKey LANTERN = new BlockKey("Deco_Lantern");
    private static final BlockKey CEILING_LANTERN = new BlockKey("Deco_Lantern_Ceiling");
    private static final BlockKey GRASS = new BlockKey("Soil_Grass");
    private static final BlockKey PATH = new BlockKey("Soil_Pathway");
    private static final BlockKey PLAIN_DIRT = new BlockKey("Soil_Dirt");
    /** Made up: a block nothing gives, that no item places and whose break gives no single stack. */
    private static final BlockKey STATUE = new BlockKey("Example_Unsourced_Statue");

    private static final BlockKey WATER = new BlockKey("~fluid:Water_Source");

    private static final ItemKey TORCH = new ItemKey("Furniture_Crude_Torch");
    private static final ItemKey SMALL_CHEST = new ItemKey("Furniture_Crude_Chest_Small");
    private static final ItemKey LANTERN_I = new ItemKey(LANTERN.id());
    private static final ItemKey CEILING_LANTERN_I = new ItemKey(CEILING_LANTERN.id());
    private static final ItemKey GRASS_I = new ItemKey(GRASS.id());
    private static final ItemKey DIRT = new ItemKey("Soil_Dirt");

    private final FakeCatalog items = new FakeCatalog();

    private List<ItemAmount> cost(BlockKey block) {
        BlueprintEntry e = new BlueprintEntry(new BlockPos(0, 0, 0), new BlockState(block, 0), false, Optional.empty());
        return EntryCost.of(e, items.plan(new FakeRecipeCatalog()));
    }

    private static Optional<ItemKey> own(BlockKey block) {
        return Optional.of(new ItemKey(block.id()));
    }

    /** Furniture_Crude_Torch.json: Wall override Wood_Torch_Wall, whose own item nothing gives. */
    @Test
    void wallTorchCostsTheTorchWhosePlacementMakesIt() {
        items.blockItems.put(
                WALL_TORCH,
                new BlockItems(own(WALL_TORCH), false, Optional.of(TORCH), Optional.of(new ItemAmount(TORCH, 1))));

        assertEquals(List.of(new ItemAmount(TORCH, 1)), cost(WALL_TORCH));
    }

    /** Furniture_Crude_Chest_Large.json: nothing gives its item, breaking it drops 2 Furniture_Crude_Chest_Small. */
    @Test
    void blockWhoseItemHasNoSourceCostsWhatBreakingItGives() {
        items.blockItems.put(
                LARGE_CHEST,
                new BlockItems(own(LARGE_CHEST), false, Optional.empty(), Optional.of(new ItemAmount(SMALL_CHEST, 2))));

        assertEquals(List.of(new ItemAmount(SMALL_CHEST, 2)), cost(LARGE_CHEST));
    }

    /** Deco_Lantern.json has a recipe; Deco_Lantern_Ceiling.json's Floor override names Deco_Lantern. */
    @Test
    void floorLanternCostsItsOwnCraftedItemNotTheCeilingOne() {
        items.blockItems.put(
                LANTERN, new BlockItems(own(LANTERN), true, Optional.of(CEILING_LANTERN_I), Optional.empty()));

        assertEquals(List.of(new ItemAmount(LANTERN_I, 1)), cost(LANTERN));
    }

    /** Deco_Lantern.json: Ceiling override Deco_Lantern_Ceiling, which nothing gives, breaking into a lantern. */
    @Test
    void ceilingLanternCostsTheLanternWhosePlacementMakesIt() {
        items.blockItems.put(
                CEILING_LANTERN,
                new BlockItems(
                        own(CEILING_LANTERN),
                        false,
                        Optional.of(LANTERN_I),
                        Optional.of(new ItemAmount(LANTERN_I, 1))));

        assertEquals(List.of(new ItemAmount(LANTERN_I, 1)), cost(CEILING_LANTERN));
    }

    /** No pre.5 variant breaks into another item than the one placing it: this fixture only pins the order. */
    @Test
    void placingItemComesBeforeWhatBreakingTheVariantGives() {
        items.blockItems.put(
                CEILING_LANTERN,
                new BlockItems(
                        own(CEILING_LANTERN), false, Optional.of(LANTERN_I), Optional.of(new ItemAmount(TORCH, 3))));

        assertEquals(List.of(new ItemAmount(LANTERN_I, 1)), cost(CEILING_LANTERN));
    }

    /**
     * Soil_Grass.json has a Farmingbench recipe and breaks into Soil_Dirt: outside a grass cell (takesAnyDirt, below)
     * its own item is asked.
     */
    @Test
    void blockWhoseItemHasASourceCostsItsOwnItemWhateverItDrops() {
        items.blockItems.put(
                GRASS, new BlockItems(own(GRASS), true, Optional.empty(), Optional.of(new ItemAmount(DIRT, 1))));

        assertEquals(List.of(new ItemAmount(GRASS_I, 1)), cost(GRASS));
    }

    @Test
    void blockWithNothingElseToAskCostsItsOwnItemEvenWithoutSource() {
        items.blockItems.put(STATUE, new BlockItems(own(STATUE), false, Optional.empty(), Optional.empty()));

        assertEquals(List.of(new ItemAmount(new ItemKey(STATUE.id()), 1)), cost(STATUE));
    }

    private List<ItemAmount> costOn(BlockKey block, BlockKey world) {
        BlueprintEntry e = new BlueprintEntry(new BlockPos(0, 0, 0), new BlockState(block, 0), false, Optional.empty());
        return EntryCost.of(e, new BlockState(world, 0), items.plan(new FakeRecipeCatalog()));
    }

    private void plainDirt() {
        items.plainDirt = Optional.of(PLAIN_DIRT);
        items.itemForBlock.put(PLAIN_DIRT, DIRT);
    }

    /** Structurize GrassPlacementHandler.getRequiredItems: a grass or dirt cell costs dirt, as Hytale grass breaks. */
    @Test
    void grassCellCostsDirt() {
        plainDirt();
        items.takesAnyDirt.add(GRASS);

        assertEquals(List.of(new ItemAmount(DIRT, 1)), cost(GRASS));
    }

    /** Structurize BlockGrassPathPlacementHandler.getRequiredItems: a path costs dirt, nothing on dirt already. */
    @Test
    void pathCellCostsDirtButNothingOnDirt() {
        plainDirt();
        items.dirtPaths.add(PATH);

        assertEquals(List.of(new ItemAmount(DIRT, 1)), costOn(PATH, GRASS));
        assertEquals(List.of(), costOn(PATH, PLAIN_DIRT));
    }

    /** MC BuildingStructureHandler.isStackFree: leaves cost nothing (Hytale: the Leaves block group). */
    @Test
    void leavesAreFree() {
        BlockKey leaves = new BlockKey("Plant_Leaves_Oak");
        items.itemForBlock.put(leaves, new ItemKey(leaves.id()));
        items.leaves.add(leaves);

        assertEquals(List.of(), cost(leaves));
    }

    @Test
    void blockWithoutItemIsFree() {
        items.blockItems.put(WATER, BlockItems.NONE);

        assertEquals(List.of(), cost(WATER));
    }
}
