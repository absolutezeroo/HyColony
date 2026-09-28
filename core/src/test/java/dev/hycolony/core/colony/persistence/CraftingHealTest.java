package dev.hycolony.core.colony.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.ColonyManager;
import dev.hycolony.core.colony.territory.TerritoryIndex;
import dev.hycolony.core.crafting.job.Crafter;
import dev.hycolony.core.crafting.job.CraftingTasks;
import dev.hycolony.core.crafting.module.CraftingHut;
import dev.hycolony.core.crafting.module.CraftingModule;
import dev.hycolony.core.crafting.recipe.Recipe;
import dev.hycolony.core.crafting.recipe.RecipeFixtures;
import dev.hycolony.core.crafting.recipe.RecipeId;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.persist.FileColonyStorage;
import dev.hycolony.core.kernel.persist.MigrationChain;
import dev.hycolony.core.request.model.RequestToken;
import dev.hycolony.core.request.model.StackRequest;
import dev.hycolony.core.testing.TestContexts;
import dev.hycolony.core.testing.crafting.TestCrafters;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The load repairs the crafting state a save left dangling, and marks the colony to rewrite (CLAUDE.md § 5). */
class CraftingHealTest {
    private static final Recipe WHEAT = RecipeFixtures.fieldcraft("Seeds", "Plant_Seeds_Wheat");
    private static final Recipe BERRY = RecipeFixtures.fieldcraft("Seeds", "Plant_Seeds_Berry");

    private final CraftingHut h = new CraftingHut(contexts(), CraftingHut.RULES, TestCrafters.HUT);

    @TempDir
    Path dir;

    CraftingHealTest() {
        h.t.recipes.add(WHEAT).add(BERRY);
    }

    /** A context that loads the test crafter hut, whose catalog has the game's recipes {@code recipes}. */
    private static TestContexts contexts(Recipe... recipes) {
        TestContexts t = new TestContexts();
        t.extraBuildingTypes.add(TestCrafters.HUT);
        for (Recipe r : recipes) {
            t.recipes.add(r);
        }
        return t;
    }

    private static Colony load(JsonObject saved, TestContexts t) {
        return ColonySerializer.read(saved, t.context(), new TerritoryIndex());
    }

    private CraftingModule module(Colony c) {
        return c.buildings()
                .at(h.hut.position())
                .orElseThrow()
                .module(CraftingModule.class)
                .orElseThrow();
    }

    private static CraftingTasks tasks(Colony c, int citizenId) {
        return ((Crafter) c.citizens().get(citizenId).orElseThrow().job().orElseThrow()).craftingTasks();
    }

    @Test
    void learnedRecipeMissingFromTheRegistryIsRemoved() {
        RecipeId wheat = h.teach(WHEAT);
        RecipeId berry = h.teach(BERRY);
        h.module.toggle(h.colony, 0);
        JsonObject saved = ColonySerializer.write(h.colony);

        Colony loaded = load(saved, contexts(BERRY)); // a game update took the wheat recipe away

        assertEquals(List.of(berry), module(loaded).recipes());
        assertFalse(module(loaded).isDisabled(wheat));
        assertTrue(loaded.isDirty(), "the healed state is written at the next save");
        JsonObject rewritten = ColonySerializer.write(loaded);
        assertEquals(new JsonArray(), savedModule(rewritten).getAsJsonArray("disabled"), "its mark goes too");
    }

    @Test
    void learnedRecipeEditedOutOfTheSavedRegistryIsRemoved() {
        RecipeId improved = h.register(RecipeFixtures.improved("Plant_Seeds_Corn"));
        h.module.learn(h.colony, h.hut, improved, h.owner);
        RecipeId berry = h.teach(BERRY);
        JsonObject saved = ColonySerializer.write(h.colony);
        saved.getAsJsonObject("recipes").getAsJsonObject("entries").remove(improved.value());

        Colony loaded = load(saved, contexts(WHEAT, BERRY));

        assertEquals(List.of(berry), module(loaded).recipes());
        assertTrue(loaded.isDirty());
    }

    @Test
    void tasksOfUnknownRequestsAreDropped() {
        CitizenData crafter = h.hire();
        RequestToken live =
                h.colony.requests().createAndAssign(h.hut, new StackRequest(new ItemKey("Rock_Stone"), 1, 1, true), -1);
        RequestToken queuedGone = RequestToken.random();
        RequestToken assignedGone = RequestToken.random();
        CraftingTasks tasks = tasks(h.colony, crafter.id());
        tasks.onTaskBeingResolved(queuedGone);
        tasks.onTaskBeingResolved(live);
        tasks.onTaskBeingScheduled(assignedGone);

        Colony loaded = load(ColonySerializer.write(h.colony), contexts(WHEAT, BERRY));

        CraftingTasks healed = tasks(loaded, crafter.id());
        assertEquals(List.of(live), healed.taskQueue());
        assertEquals(List.of(), healed.assignedTasks(), "MC never cleans the scheduled tasks otherwise");
        assertTrue(loaded.isDirty());
    }

    @Test
    void soundCraftingSaveLoadsWithoutRewrite() {
        h.teach(WHEAT);
        CitizenData crafter = h.hire();
        RequestToken live =
                h.colony.requests().createAndAssign(h.hut, new StackRequest(new ItemKey("Rock_Stone"), 1, 1, true), -1);
        tasks(h.colony, crafter.id()).onTaskBeingScheduled(live);

        Colony loaded = load(ColonySerializer.write(h.colony), contexts(WHEAT, BERRY));

        assertEquals(1, module(loaded).recipes().size());
        assertEquals(List.of(live), tasks(loaded, crafter.id()).assignedTasks());
        assertFalse(loaded.isDirty());
    }

    /** Simulation: the autosave never wrote the healed state, so every load healed the same save again. */
    @Test
    void colonyHealedByTheStorageLoadIsRewrittenAtTheNextSave() throws IOException {
        h.teach(WHEAT);
        store(h.colony);

        Colony loaded = loadAll(contexts(BERRY)); // a game update took the wheat recipe away

        assertEquals(List.of(), module(loaded).recipes());
        assertTrue(loaded.isDirty(), "the healed state is written at the next save");
    }

    @Test
    void soundColonyLoadedFromTheStorageIsNotRewritten() throws IOException {
        h.teach(WHEAT);
        store(h.colony);

        Colony loaded = loadAll(contexts(WHEAT, BERRY));

        assertEquals(1, module(loaded).recipes().size());
        assertFalse(loaded.isDirty());
    }

    private void store(Colony c) throws IOException {
        new FileColonyStorage(dir).save(c.id(), ColonySerializer.write(c).toString());
    }

    /** The colony read back by the real load of a world ({@link ColonyManager}'s persistence). */
    private Colony loadAll(TestContexts t) {
        ColonyManager m = new ColonyManager(t.context());
        m.persistence().setStorage(new FileColonyStorage(dir), MigrationChain.sp3b());
        m.persistence().loadAll();
        return m.byId(h.colony.id()).orElseThrow();
    }

    private static JsonObject savedModule(JsonObject colony) {
        for (JsonElement b : colony.getAsJsonArray("buildings")) {
            JsonObject building = b.getAsJsonObject();
            if (building.get("type").getAsString().equals(TestCrafters.ID)) {
                return building.getAsJsonObject("modules").getAsJsonObject("crafting");
            }
        }
        throw new AssertionError("no crafter hut saved");
    }
}
