package dev.hycolony.core.crafting.recipe;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.kernel.item.ItemKey;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RecipeCatalogTest {
    @Test
    void noCatalogKnowsNothing() {
        RecipeCatalog none = RecipeCatalog.NONE;
        assertTrue(none.all().isEmpty());
        assertTrue(none.byHytaleId("Plant_Seeds_Wheat").isEmpty());
        assertTrue(none.itemsOf(new Ingredient.OfItem(new ItemKey("Rock_Stone"), 1))
                .isEmpty());
        assertTrue(none.benchUpgradeCost("Farmingbench", 1, 3).isEmpty());
        assertTrue(none.benchItem("Farmingbench").isEmpty());
        assertTrue(none.benchCategories("Farmingbench").isEmpty());
        assertFalse(none.playerKnows(new UUID(0, 1), "Plant_Seeds_Wheat"));
    }
}
