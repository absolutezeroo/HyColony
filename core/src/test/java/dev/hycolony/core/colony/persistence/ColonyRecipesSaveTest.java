package dev.hycolony.core.colony.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.ColonyManager;
import dev.hycolony.core.crafting.recipe.Recipe;
import dev.hycolony.core.crafting.recipe.RecipeFixtures;
import dev.hycolony.core.crafting.recipe.RecipeId;
import dev.hycolony.core.kernel.persist.FileColonyStorage;
import dev.hycolony.core.kernel.persist.MigrationChain;
import dev.hycolony.core.testing.TestContexts;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The colony's recipe registry (MC StandardRecipeManager) is saved with the colony. */
class ColonyRecipesSaveTest {
    private static final Recipe WHEAT = RecipeFixtures.at("Farmingbench", "Seeds", "Plant_Seeds_Wheat");

    @TempDir
    Path dir;

    private ColonyManager load(TestContexts t) {
        ColonyManager m = new ColonyManager(t.context());
        m.persistence().setStorage(new FileColonyStorage(dir), MigrationChain.sp3b());
        m.persistence().loadAll();
        return m;
    }

    @Test
    void colonyRecipesSurviveSaveAndLoad() throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/fixtures/colony-v3-crafting.json")) {
            Files.write(dir.resolve("colony-1.json"), in.readAllBytes());
        }
        TestContexts first = new TestContexts();
        first.recipes.add(WHEAT);
        ColonyManager m = load(first);
        Colony c = m.byId(1).orElseThrow();
        RecipeId wheat = c.recipes().checkOrAdd(WHEAT);
        RecipeId improved = c.recipes().checkOrAdd(RecipeFixtures.improved("Plant_Seeds_Corn"));
        m.persistence().saveAll();

        TestContexts second = new TestContexts();
        second.recipes.add(WHEAT);
        Colony r = load(second).byId(1).orElseThrow();

        assertEquals(Optional.of(WHEAT), r.recipes().get(wheat));
        assertEquals(
                Optional.of(RecipeFixtures.improved("Plant_Seeds_Corn")),
                r.recipes().get(improved));
    }

    @Test
    void v3ColonyLoadsWithAnEmptyRegistry() throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/fixtures/colony-v3-crafting.json")) {
            Files.write(dir.resolve("colony-1.json"), in.readAllBytes());
        }

        Colony c = load(new TestContexts()).byId(1).orElseThrow();

        assertTrue(c.recipes().idOf(WHEAT).isEmpty());
        assertEquals(new RecipeId("improved:1"), c.recipes().checkOrAdd(RecipeFixtures.improved("A")));
    }
}
