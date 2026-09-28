package dev.hycolony.core.crafting.recipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.item.ToolType;
import dev.hycolony.core.testing.FakeRecipeCatalog;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RecipeRegistryTest {
    private final List<String> warnings = new ArrayList<>();

    /** A registry read back from {@code saved}'s JSON text, as a colony load does. */
    private RecipeRegistry reload(RecipeRegistry saved, FakeRecipeCatalog catalog) {
        RecipeRegistry back = new RecipeRegistry();
        back.read(JsonParser.parseString(saved.write().toString()).getAsJsonObject(), catalog, warnings::add);
        return back;
    }

    @Test
    void sameRecipeGetsTheSameId() {
        RecipeRegistry reg = new RecipeRegistry();
        Recipe r = RecipeFixtures.at("Farmingbench", "Seeds", "Plant_Seeds_Wheat");
        assertEquals(reg.checkOrAdd(r), reg.checkOrAdd(r));
        assertEquals(new RecipeId("hytale:Plant_Seeds_Wheat"), reg.checkOrAdd(r));
        assertEquals(Optional.of(r), reg.get(new RecipeId("hytale:Plant_Seeds_Wheat")));
    }

    @Test
    void improvedRecipesGetIncreasingIds() {
        RecipeRegistry reg = new RecipeRegistry();
        RecipeId a = reg.checkOrAdd(RecipeFixtures.improved("A"));
        RecipeId b = reg.checkOrAdd(RecipeFixtures.improved("B"));
        assertEquals(new RecipeId("improved:1"), a);
        assertEquals(new RecipeId("improved:2"), b);
    }

    @Test
    void customRecipeGetsItsOwnIdEvenWithTheContentOfATaughtRecipe() {
        RecipeRegistry reg = new RecipeRegistry();
        Recipe taught = RecipeFixtures.at("Farmingbench", "Seeds", "Plant_Seeds_Wheat");
        Recipe custom = RecipeFixtures.from(taught, new RecipeSource.Custom("farmer_wheat_seeds"));

        RecipeId taughtId = reg.checkOrAdd(taught);
        RecipeId customId = reg.checkOrAdd(custom);

        assertEquals(new RecipeId("custom:farmer_wheat_seeds"), customId, "MC: recipeSource differs");
        assertNotEquals(taughtId, customId);
        assertEquals(Optional.of(customId), reg.idOf(custom));
        assertEquals(Optional.of(taughtId), reg.idOf(taught));
    }

    @Test
    void improvedRecipeEqualToATaughtOneSharesItsId() {
        RecipeRegistry reg = new RecipeRegistry();
        Recipe taught = RecipeFixtures.at("Farmingbench", "Seeds", "Plant_Seeds_Wheat");

        RecipeId taughtId = reg.checkOrAdd(taught);

        assertEquals(
                taughtId,
                reg.checkOrAdd(RecipeFixtures.from(taught, new RecipeSource.Improved())),
                "MC: neither a taught nor an improved recipe has a recipeSource");
    }

    @Test
    void unknownRecipeHasNoId() {
        RecipeRegistry reg = new RecipeRegistry();
        reg.checkOrAdd(RecipeFixtures.at("Farmingbench", "Seeds", "Plant_Seeds_Wheat"));
        assertTrue(reg.idOf(RecipeFixtures.at("Farmingbench", "Seeds", "Plant_Seeds_Corn"))
                .isEmpty());
        assertTrue(reg.get(new RecipeId("hytale:Plant_Seeds_Corn")).isEmpty());
    }

    @Test
    void changedCustomRecipeReplacesTheOldOneUnderItsId() {
        RecipeRegistry reg = new RecipeRegistry();
        RecipeSource source = new RecipeSource.Custom("farmer_seeds");
        Recipe before = RecipeFixtures.from(RecipeFixtures.at("Farmingbench", "Seeds", "Plant_Seeds_Wheat"), source);
        Recipe after = RecipeFixtures.from(RecipeFixtures.at("Farmingbench", "Seeds", "Plant_Seeds_Corn"), source);

        RecipeId id = reg.checkOrAdd(before);

        assertEquals(id, reg.checkOrAdd(after));
        assertEquals(Optional.of(after), reg.get(id));
        assertTrue(reg.idOf(before).isEmpty());
    }

    @Test
    void improvedRecipeSurvivesSaveAndLoad() {
        RecipeRegistry reg = new RecipeRegistry();
        Recipe improved = RecipeFixtures.improved("A");
        RecipeId id = reg.checkOrAdd(improved);

        RecipeRegistry back = reload(reg, new FakeRecipeCatalog());

        assertEquals(Optional.of(improved), back.get(id));
        assertEquals(Optional.of(id), back.idOf(improved));
        assertTrue(warnings.isEmpty(), warnings::toString);
    }

    @Test
    void customRecipeSurvivesSaveAndLoadWithEveryPart() {
        RecipeRegistry reg = new RecipeRegistry();
        Recipe custom = new Recipe(
                List.of(
                        new Ingredient.OfItem(RecipeFixtures.ESSENCE, 2),
                        new Ingredient.OfResourceType("Wood_Trunk", 4),
                        new Ingredient.OfTag("Fibre", 1)),
                new ItemAmount(new ItemKey("Bench_Farming"), 1),
                List.of(new ItemAmount(new ItemKey("Container_Bucket"), 1)),
                new BenchRequirement("Workbench", List.of("Benches", "Tools"), 2),
                Optional.of(ToolType.AXE),
                new RecipeSource.Custom("carpenter_bench"),
                true);
        RecipeId id = reg.checkOrAdd(custom);

        assertEquals(Optional.of(custom), reload(reg, new FakeRecipeCatalog()).get(id));
    }

    @Test
    void hytaleRecipeIsReadAgainFromTheCatalog() {
        RecipeRegistry reg = new RecipeRegistry();
        RecipeId id = reg.checkOrAdd(RecipeFixtures.at("Farmingbench", "Seeds", "Plant_Seeds_Wheat"));
        Recipe updated = RecipeFixtures.at("Farmingbench", "Crops", "Plant_Seeds_Wheat");

        RecipeRegistry back = reload(reg, new FakeRecipeCatalog().add(updated));

        assertEquals(Optional.of(updated), back.get(id), "the game's current version of the recipe");
        assertEquals(Optional.of(id), back.idOf(updated));
    }

    @Test
    void vanishedHytaleRecipeIsDroppedOnLoad() {
        RecipeRegistry reg = new RecipeRegistry();
        RecipeId id = reg.checkOrAdd(RecipeFixtures.at("Farmingbench", "Seeds", "Plant_Seeds_Gone"));

        RecipeRegistry back = reload(reg, new FakeRecipeCatalog());

        assertTrue(back.get(id).isEmpty());
        assertEquals(1, warnings.size());
    }

    @Test
    void malformedSavedRecipeIsSkippedWithOneWarningAndTheRestLoads() {
        RecipeRegistry reg = new RecipeRegistry();
        RecipeId kept = reg.checkOrAdd(RecipeFixtures.improved("A"));
        JsonObject saved = reg.write();
        JsonObject noItemId = JsonParser.parseString(
                        "{\"source\":\"improved\",\"inputs\":[{\"kind\":\"item\",\"amount\":1}],"
                                + "\"output\":{\"item\":\"B\",\"count\":1},"
                                + "\"bench\":{\"id\":\"Fieldcraft\",\"categories\":[],\"tier\":0}}")
                .getAsJsonObject();
        saved.getAsJsonObject("entries").add("improved:2", noItemId);
        saved.getAsJsonObject("entries").add("improved:3", JsonParser.parseString("42"));

        RecipeRegistry back = new RecipeRegistry();
        back.read(saved, new FakeRecipeCatalog(), warnings::add);

        assertTrue(back.get(kept).isPresent());
        assertTrue(back.get(new RecipeId("improved:2")).isEmpty());
        assertTrue(back.get(new RecipeId("improved:3")).isEmpty());
        assertEquals(2, warnings.size(), warnings::toString);
    }

    @Test
    void improvedIdsAreNeverReusedAfterALoad() {
        RecipeRegistry reg = new RecipeRegistry();
        reg.checkOrAdd(RecipeFixtures.improved("A"));
        reg.checkOrAdd(RecipeFixtures.improved("B"));
        JsonObject saved = reg.write();
        saved.remove("nextImproved");

        RecipeRegistry back = new RecipeRegistry();
        back.read(saved, new FakeRecipeCatalog(), warnings::add);

        assertEquals(new RecipeId("improved:3"), back.checkOrAdd(RecipeFixtures.improved("C")));
    }

    @Test
    void emptySaveReadsAsAnEmptyRegistry() {
        RecipeRegistry back = new RecipeRegistry();
        back.read(new JsonObject(), new FakeRecipeCatalog(), warnings::add);

        assertEquals(new RecipeId("improved:1"), back.checkOrAdd(RecipeFixtures.improved("A")));
        assertTrue(warnings.isEmpty(), warnings::toString);
    }
}
