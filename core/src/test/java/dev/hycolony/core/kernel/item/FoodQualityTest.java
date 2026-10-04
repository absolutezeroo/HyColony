package dev.hycolony.core.kernel.item;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class FoodQualityTest {
    @Test
    void eachRankFeedsTheMedianNutritionOfOurFoodsOfItsQuality() {
        assertEquals(3, FoodQuality.COMMON.food().nutrition());
        assertEquals(8, FoodQuality.UNCOMMON.food().nutrition());
        assertEquals(12, FoodQuality.RARE.food().nutrition());
    }

    @Test
    void aFoodWithoutAFileIsNeverAMineColoniesDishLikeMcForAnotherModsFood() {
        for (FoodQuality rank : FoodQuality.values()) {
            assertEquals(new FoodInfo(rank.food().nutrition(), 0, false), rank.food(), rank.name());
        }
    }
}
