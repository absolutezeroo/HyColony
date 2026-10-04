package dev.hycolony.core.kernel.item;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class FoodQualityTest {
    @Test
    void eachRankFeedsTheMedianOfOurFoodsOfItsQuality() {
        assertEquals(new FoodInfo(3, 0, false), FoodQuality.COMMON.food());
        assertEquals(new FoodInfo(8, 2, false), FoodQuality.UNCOMMON.food());
        assertEquals(new FoodInfo(12, 3, false), FoodQuality.RARE.food());
    }
}
