package dev.hycolony.core.farming.job;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.citizen.inventory.CitizenEquipment;
import dev.hycolony.core.farming.CropState;
import dev.hycolony.core.farming.field.FarmField;
import dev.hycolony.core.farming.field.FieldStage;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.Vec3;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.item.ToolInfo;
import dev.hycolony.core.kernel.item.ToolType;
import dev.hycolony.core.kernel.port.BodyAnimation;
import dev.hycolony.core.request.model.ToolRequest;
import dev.hycolony.core.testing.FakeBodies;
import dev.hycolony.core.testing.farming.FakeFarming;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** MC EntityAIWorkFarmer.workAtField, hoeIfAble, tryToPlant and harvestIfAble, plus the Hytale fertilizer. */
class FieldPassTest extends FarmerTestBase {
    @Test
    void hoePassTillsEveryTillableCellAndWearsTheHoe() {
        FarmField f = field(true);
        give(HOE, 1);
        settings().setFertilize(false);

        assertEquals(FarmerState.IDLE, pass(FarmerState.FARMER_HOE));

        assertTrue(t.farming.tilled.containsAll(cells()));
        assertEquals(8, citizen.inventory().slot(0).map(ItemAmount::damage).orElse(-1));
        assertEquals(FieldStage.HOED, f.stage());
        assertEquals(0.02 * cells().size(), citizen.hunger().pending(), 1e-9); // MC: each tilled cell
    }

    @Test
    void missingHoeMidPassSkipsTheCell() {
        field(true);
        settings().setFertilize(false);
        give(HOE, 1);
        assertEquals(FarmerState.FARMER_HOE, work.prepare());
        citizen.inventory().set(0, java.util.Optional.empty()); // the hoe is gone once the pass started

        for (int i = 0; i < 100 && work.workAtField(FarmerState.FARMER_HOE) == FarmerState.FARMER_HOE; i++) {
            // the whole pass
        }

        assertTrue(t.farming.tilled.isEmpty());
        assertTrue(colony.requests().byRequester(hut.requesterId()).stream()
                .anyMatch(r -> r.requestable() instanceof ToolRequest));
    }

    @Test
    void plantPassPlantsTheFieldSeed() {
        FarmField f = tilledField();
        give(SEEDS, 8);

        assertEquals(FarmerState.IDLE, pass(FarmerState.FARMER_PLANT));

        for (BlockPos cell : cells()) {
            assertEquals(SEEDS, t.farming.crops.get(cell.offset(0, 1, 0)));
        }
        assertEquals(0, carried(SEEDS));
        assertEquals(FieldStage.PLANTED, f.stage());
        assertEquals(0.02 * cells().size(), citizen.hunger().pending(), 1e-9); // MC plantCrop: each crop placed
    }

    @Test
    void plantPassWithoutSeedGoesBackToPreparing() {
        tilledField();
        give(SEEDS, 1);

        FarmerState state = pass(FarmerState.FARMER_PLANT);

        assertEquals(FarmerState.PREPARING, state);
        assertEquals(1, t.farming.crops.size());
        assertEquals(HOE, t.bodies.bodies.get(body).held, "no seed bag shown once the seeds ran out");
    }

    @Test
    void harvestPassPutsDropsInTheInventoryAndEmptiesNormalCrops() {
        FarmField f = plantedField(FakeFarming.WHEAT_SEEDS);

        assertEquals(FarmerState.IDLE, pass(FarmerState.FARMER_HARVEST));

        assertTrue(t.farming.crops.isEmpty());
        assertEquals(8, carried(FakeFarming.WHEAT));
        assertEquals(24, carried(FakeFarming.ESSENCE));
        assertEquals(8, job.actionsDone());
        assertEquals(FieldStage.EMPTY, f.stage());
    }

    @Test
    void eternalCropsStayAfterHarvest() {
        plantedField(new dev.hycolony.core.kernel.item.ItemKey("Plant_Seeds_Wheat_Eternal"));

        pass(FarmerState.FARMER_HARVEST);

        assertEquals(8, t.farming.crops.size());
        assertTrue(t.farming.cropState.values().stream().allMatch(s -> s == CropState.GROWING));
    }

    @Test
    void fertilizerIsUsedOncePerUnfertilizedCell() {
        field(true);
        give(HOE, 1);
        give(FERTILIZER, 1); // 5 uses: 5 of the 8 cells

        pass(FarmerState.FARMER_HOE);

        assertEquals(5, t.farming.fertilized.size());
        assertEquals(0, carried(FERTILIZER), "a fertilizer worn to its 5 uses breaks");
    }

    @Test
    void noFertilizerUsedWhenTheSettingIsOff() {
        field(true);
        give(HOE, 1);
        give(FERTILIZER, 1);
        settings().setFertilize(false);

        pass(FarmerState.FARMER_HOE);

        assertTrue(t.farming.fertilized.isEmpty());
    }

    @Test
    void cellDelayFollowsStamina() {
        field(true);
        give(HOE, 1);
        settings().setFertilize(false);
        work.prepare();

        for (int i = 0; i < 10 && work.delay().remaining() == 0; i++) {
            work.workAtField(FarmerState.FARMER_HOE); // walks to the cell, then works it
        }

        assertEquals(35, work.delay().remaining()); // max(1, 40 - 10 / 2)
    }

    @Test
    void finishedPassAdvancesTheStageAndAsksForADump() {
        FarmField f = field(true);
        give(HOE, 1);
        settings().setFertilize(false);

        pass(FarmerState.FARMER_HOE);

        assertEquals(FieldStage.HOED, f.stage());
        assertTrue(work.consumeDumpRequest());
        assertFalse(work.consumeDumpRequest());
        assertTrue(fields().fieldToWorkOn(colony, hut).isEmpty(), "a pass that worked leaves the field for today");
    }

    @Test
    void fieldBrokenMidPassIsDropped() {
        field(true);
        give(HOE, 1);
        settings().setFertilize(false);
        work.prepare();
        work.workAtField(FarmerState.FARMER_HOE);

        colony.registries().fields().remove(FIELD);

        assertEquals(FarmerState.IDLE, work.workAtField(FarmerState.FARMER_HOE));
        fields().fieldToWorkOn(colony, hut);
        assertTrue(fields().walk().offset().isEmpty(), "the next field starts from its first cell");
    }

    @Test
    void fieldFreedMidPassResetsTheWalk() {
        FarmField f = field(true);
        give(HOE, 1);
        settings().setFertilize(false);
        work.prepare();
        work.workAtField(FarmerState.FARMER_HOE);
        work.workAtField(FarmerState.FARMER_HOE);

        fields().free(colony, hut, f);
        fields().fieldToWorkOn(colony, hut);

        assertTrue(fields().walk().offset().isEmpty(), "the next field starts from its first cell");
    }

    /** Prepares, then runs {@code state}'s pass to its end (or to PREPARING); returns the state it ended on. */
    private FarmerState pass(FarmerState state) {
        FarmerState prepared = work.prepare();
        assertEquals(state, prepared);
        FarmerState now = state;
        for (int i = 0; i < 100 && now == state; i++) {
            now = work.workAtField(state);
        }
        return now;
    }

    private FarmField tilledField() {
        FarmField f = field(true);
        give(HOE, 1);
        settings().setFertilize(false);
        for (BlockPos cell : cells()) {
            t.farming.tillable.remove(cell);
            t.farming.tilled.add(cell);
        }
        f.nextStage();
        return f;
    }

    private FarmField plantedField(dev.hycolony.core.kernel.item.ItemKey seed) {
        FarmField f = tilledField();
        f.nextStage();
        for (BlockPos cell : cells()) {
            t.farming.plant(cell.offset(0, 1, 0), seed);
            t.farming.cropState.put(cell.offset(0, 1, 0), CropState.MATURE);
        }
        return f;
    }

    @Test
    void cellWithinFourBlocksIsWorkedWithoutWalking() {
        field(true);
        give(HOE, 1);
        settings().setFertilize(false);
        t.bodies.frozen = true;
        t.bodies.bodies.get(body).position = Vec3.center(FIELD.offset(-2, 0, 1));
        assertEquals(FarmerState.FARMER_HOE, work.prepare());

        work.workAtField(FarmerState.FARMER_HOE);

        assertTrue(t.farming.tilled.contains(cells().get(0)), "MC walkToSafePos: within 4 blocks");
        assertTrue(t.bodies.moves.isEmpty());
    }

    @Test
    void harvestWithoutDropsStillCounts() {
        plantedField(FakeFarming.WHEAT_SEEDS);
        t.farming.harvestDrops = List.of();

        pass(FarmerState.FARMER_HARVEST);

        assertTrue(t.farming.crops.isEmpty());
        assertEquals(8, job.actionsDone(), "MC mineBlock: the block broke");
    }

    @Test
    void farmerTillsWithTheHoeInHandLikeAPlayer() {
        field(true);
        give(HOE, 1);
        settings().setFertilize(false);

        pass(FarmerState.FARMER_HOE);

        FakeBodies.Body b = t.bodies.bodies.get(body);
        assertEquals(HOE, b.held, "MC workAtField equips the hoe");
        assertEquals(BodyAnimation.TILL, b.lastAnimation, "MC hoeIfAble swings");
        assertEquals(8, b.animations);
        assertEquals(cells(), t.effects.tilled, "the till sound of each cell");
        assertEquals(cells().stream().map(Vec3::middle).toList(), t.bodies.looks, "faces each soil it tills");
    }

    @Test
    void farmerSowsWithTheSeedInHandLikeAPlayer() {
        tilledField();
        give(SEEDS, 8);

        pass(FarmerState.FARMER_PLANT);

        FakeBodies.Body b = t.bodies.bodies.get(body);
        List<BlockPos> crops = cells().stream().map(c -> c.offset(0, 1, 0)).toList();
        assertEquals(SEEDS, b.held, "Hytale's seed bag in hand");
        assertEquals(BodyAnimation.PLANT, b.lastAnimation);
        assertEquals(8, b.animations);
        assertEquals(crops.stream().map(Vec3::middle).toList(), t.bodies.looks, "faces each crop it sows");
        assertEquals(crops, t.effects.placed, "the placing sound of each crop");
    }

    @Test
    void plantingRefusedByTheWorldKeepsTheSeedAndMovesOn() {
        tilledField();
        give(SEEDS, 8);
        t.farming.refusePlant = true;

        assertEquals(FarmerState.IDLE, pass(FarmerState.FARMER_PLANT));

        assertEquals(8, carried(SEEDS));
        assertTrue(t.effects.placed.isEmpty());
        assertEquals(0, t.bodies.bodies.get(body).animations);
    }

    @Test
    void harvestIsAStrokeOnTheCrop() {
        plantedField(FakeFarming.WHEAT_SEEDS);
        give(HOE, 1);

        pass(FarmerState.FARMER_HARVEST);

        FakeBodies.Body b = t.bodies.bodies.get(body);
        assertEquals(HOE, b.held);
        assertEquals(BodyAnimation.MINE, b.lastAnimation, "MC mineBlock with the hoe");
        List<BlockPos> crops = cells().stream().map(c -> c.offset(0, 1, 0)).toList();
        assertEquals(crops, t.effects.hits);
        assertEquals(crops.stream().map(Vec3::middle).toList(), t.bodies.looks, "MC hitBlockWithToolInHand looks");
    }

    @Test
    void farmerHoldsTheSlotOfTheHoeItWears() {
        field(true);
        settings().setFertilize(false);
        int uses = t.catalog.durability(HOE);
        citizen.inventory().set(0, Optional.of(new ItemAmount(HOE, 1, uses))); // worn out: never used
        citizen.inventory().set(2, Optional.of(new ItemAmount(HOE, 1)));

        pass(FarmerState.FARMER_HOE);

        assertEquals(2, citizen.equipment().held(CitizenEquipment.Hand.MAIN), "MC equipHoe: the hoe it wears");
        assertEquals(8, citizen.inventory().slot(2).orElseThrow().damage());
    }

    @Test
    void farmerUsesTheFirstHoeItCarriesNotTheLowest() {
        field(true);
        settings().setFertilize(false);
        hut.setLevel(3);
        ItemKey copper = new ItemKey("Tool_Hoe_Copper");
        t.catalog.tools.put(copper, new ToolInfo(ToolType.HOE, 1, 1f));
        t.catalog.durability.put(copper, 100);
        citizen.inventory().set(1, Optional.of(new ItemAmount(copper, 1)));
        citizen.inventory().set(4, Optional.of(new ItemAmount(HOE, 1)));

        pass(FarmerState.FARMER_HOE);

        assertEquals(1, citizen.equipment().held(CitizenEquipment.Hand.MAIN), "MC getHoeSlot: the first one");
        assertEquals(8, citizen.inventory().slot(1).orElseThrow().damage());
    }

    @Test
    void aFarmerLeftWithoutAHoeHoldsNoSlot() {
        field(true);
        settings().setFertilize(false);
        int uses = t.catalog.durability(HOE);
        citizen.inventory().set(0, Optional.of(new ItemAmount(HOE, 1, uses - 1))); // breaks on the first cell

        pass(FarmerState.FARMER_HOE);

        assertEquals(CitizenEquipment.NO_SLOT, citizen.equipment().held(CitizenEquipment.Hand.MAIN), "MC equipHoe: -1");
    }

    @Test
    void hoeBrokenOnTheLastCellEmptiesTheHandThoughAnotherHoeIsCarried() {
        field(true);
        settings().setFertilize(false);
        int uses = t.catalog.durability(HOE);
        citizen.inventory().set(0, Optional.of(new ItemAmount(HOE, 1, uses - 8))); // 8 uses left
        citizen.inventory().set(3, Optional.of(new ItemAmount(HOE, 1)));

        pass(FarmerState.FARMER_HOE);

        assertEquals(null, t.bodies.bodies.get(body).held, "MC damageItemInHand; equipHoe only at the next cell");
        assertEquals(0, citizen.equipment().held(CitizenEquipment.Hand.MAIN), "MC keeps the held slot");
    }

    @Test
    void hoeBrokenOnTheLastCellLeavesTheHandEmpty() {
        field(true);
        give(HOE, 1);
        settings().setFertilize(false);
        int uses = t.catalog.durability(HOE);
        citizen.inventory().damage(0, uses - 8, uses); // the hoe, alone in slot 0: 8 uses left

        pass(FarmerState.FARMER_HOE);

        assertEquals(0, carried(HOE), "worn out on the eighth cell");
        assertEquals(null, t.bodies.bodies.get(body).held);
    }
}
