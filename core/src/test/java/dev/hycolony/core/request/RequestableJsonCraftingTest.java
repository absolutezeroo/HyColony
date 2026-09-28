package dev.hycolony.core.request;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.request.model.Crafting;
import dev.hycolony.core.request.model.Requestable;
import dev.hycolony.core.request.model.StackList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** The crafting task and the "one of these items" request survive a save (SP3b-1). */
class RequestableJsonCraftingTest {
    private static Optional<Requestable> roundTrip(Requestable r) {
        return RequestableJson.read(
                JsonParser.parseString(RequestableJson.write(r).toString()).getAsJsonObject());
    }

    @Test
    void craftingTaskSurvivesSaveAndLoad() {
        Crafting task = new Crafting(new ItemKey("Plant_Seeds_Wheat"), 3, 1, "hytale:Plant_Seeds_Wheat", true);

        Crafting back = (Crafting) roundTrip(task).orElseThrow();

        assertEquals(task.stack(), back.stack());
        assertEquals(3, back.count());
        assertEquals(1, back.minCount(), "MC PublicCrafting.serialize drops it; kept here");
        assertEquals("hytale:Plant_Seeds_Wheat", back.recipeId());
        assertTrue(back.isPublic());
    }

    @Test
    void privateCraftingTaskStaysPrivate() {
        Crafting task = new Crafting(new ItemKey("Plant_Seeds_Wheat"), 2, 2, "custom:farmer_seeds", false);

        Crafting back = (Crafting) roundTrip(task).orElseThrow();

        assertFalse(back.isPublic());
        assertEquals("custom:farmer_seeds", back.recipeId());
    }

    @Test
    void stackListSurvivesSaveAndLoad() {
        StackList trunks = new StackList(
                List.of(new ItemKey("Wood_Oak_Trunk"), new ItemKey("Wood_Birch_Trunk")), "type:Wood_Trunk", 8, 4);

        StackList back = (StackList) roundTrip(trunks).orElseThrow();

        assertEquals(trunks.accepted(), back.accepted());
        assertEquals("type:Wood_Trunk", back.description());
        assertEquals(8, back.count());
        assertEquals(4, back.minCount());
    }

    @Test
    void stackListWithoutItsItemsIsNotRead() {
        JsonObject saved = RequestableJson.write(new StackList(List.of(new ItemKey("A")), "x", 1, 1));
        saved.remove("accepted");

        assertTrue(RequestableJson.read(saved).isEmpty());
    }

    @Test
    void craftingTaskWithoutItsRecipeIsNotRead() {
        JsonObject saved = RequestableJson.write(new Crafting(new ItemKey("A"), 1, 1, "hytale:A", true));
        saved.remove("recipe");

        assertTrue(RequestableJson.read(saved).isEmpty());
    }
}
