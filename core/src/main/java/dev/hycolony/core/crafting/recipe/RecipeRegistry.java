package dev.hycolony.core.crafting.recipe;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * The recipes a colony knows, by id (MC StandardRecipeManager, global in MC, one per colony here and saved with it).
 *
 * <p>A recipe is found again by its content and its MC {@code recipeSource}, as MC RecipeStorage.equals compares both.
 * MC's recipeSource of a {@link RecipeSource.Custom} recipe is its {@code crafting.json} id (MC CustomRecipe); a
 * {@link RecipeSource.Hytale} recipe, taught by a player, and a {@link RecipeSource.Improved} one have none (MC
 * AddRemoveRecipeMessage sets none, improveRecipe sets it to null). So a custom recipe never shares the id of a taught
 * recipe of the same content, which MC checkForWorkerSpecificRecipes relies on, while a taught and an improved one do.
 *
 * <p>Deviation from MC: ids are names ({@code hytale:<Hytale id>}, {@code custom:<id>}, {@code improved:<n>}) rather
 * than random tokens, so a Hytale recipe is saved by its id and read again from the game. A recipe whose name is taken
 * by another content of the same source (the game or {@code crafting.json} changed it) replaces it under that id,
 * where MC adds it under a new token and checkForWorkerSpecificRecipes swaps it in. Every recipe is saved, where MC
 * saves only those used since the server started.
 */
public final class RecipeRegistry {
    private static final String ENTRIES = "entries";
    private static final String NEXT_IMPROVED = "nextImproved";
    private static final String IMPROVED_ID = RecipeJson.IMPROVED + ":";

    private final Map<RecipeId, Recipe> recipes = new LinkedHashMap<>();
    private final Map<Key, RecipeId> ids = new HashMap<>();
    private int nextImproved = 1;

    /** What MC RecipeStorage.equals compares: the content, and the custom recipe id standing for MC's source. */
    private record Key(Recipe.Content content, Optional<String> mcSource) {
        static Key of(Recipe r) {
            Optional<String> mcSource =
                    r.source() instanceof RecipeSource.Custom(String id) ? Optional.of(id) : Optional.empty();
            return new Key(r.content(), mcSource);
        }
    }

    /**
     * MC StandardRecipeManager.checkOrAddRecipe: the id of the known recipe equal to {@code recipe}; otherwise adds it
     * under a new id: {@code hytale:<Hytale id>}, {@code custom:<id>}, or the next {@code improved:<n>}.
     */
    public RecipeId checkOrAdd(Recipe recipe) {
        RecipeId known = ids.get(Key.of(recipe));
        if (known != null) {
            return known;
        }
        RecipeId id = switch (recipe.source()) {
            case RecipeSource.Hytale(String hytaleId) -> new RecipeId(RecipeJson.HYTALE + hytaleId);
            case RecipeSource.Custom(String customId) -> new RecipeId(RecipeJson.CUSTOM + customId);
            case RecipeSource.Improved() -> new RecipeId(IMPROVED_ID + nextImproved++);
        };
        put(id, recipe);
        return id;
    }

    /** MC StandardRecipeManager.getRecipe: the recipe known under {@code id}; empty if none. */
    public Optional<Recipe> get(RecipeId id) {
        return Optional.ofNullable(recipes.get(id));
    }

    /** MC StandardRecipeManager.getRecipeId: the id of the known recipe equal to {@code recipe}; empty if none. */
    public Optional<RecipeId> idOf(Recipe recipe) {
        return Optional.ofNullable(ids.get(Key.of(recipe)));
    }

    /** MC StandardRecipeManager.write: a Hytale recipe by its source alone, any other one in full. */
    public JsonObject write() {
        JsonObject entries = new JsonObject();
        recipes.forEach((id, r) -> entries.add(
                id.value(),
                r.source() instanceof RecipeSource.Hytale ? RecipeJson.sourceOnly(r.source()) : RecipeJson.write(r)));
        JsonObject o = new JsonObject();
        o.addProperty(NEXT_IMPROVED, nextImproved);
        o.add(ENTRIES, entries);
        return o;
    }

    /**
     * MC StandardRecipeManager.read: replaces this registry's recipes with the saved ones; a Hytale recipe is read again
     * from {@code catalog}. A recipe the game no longer has, or a malformed one, is dropped with one {@code warn}.
     */
    public void read(JsonObject in, RecipeCatalog catalog, Consumer<String> warn) {
        recipes.clear();
        ids.clear();
        nextImproved = in.get(NEXT_IMPROVED) instanceof JsonPrimitive p && p.isNumber() ? Math.max(1, p.getAsInt()) : 1;
        if (!(in.get(ENTRIES) instanceof JsonObject entries)) {
            return;
        }
        for (String id : entries.keySet()) {
            readEntry(id, entries.get(id), catalog, warn).ifPresent(r -> put(new RecipeId(id), r));
            if (id.startsWith(IMPROVED_ID)) {
                nextImproved = Math.max(nextImproved, improvedNumber(id) + 1);
            }
        }
    }

    /** One saved recipe; empty, after one warning, when the game no longer has it or it is malformed. */
    private static Optional<Recipe> readEntry(
            String id, JsonElement saved, RecipeCatalog catalog, Consumer<String> warn) {
        try {
            JsonObject o = saved.getAsJsonObject();
            if (RecipeJson.source(o) instanceof RecipeSource.Hytale(String hytaleId)) {
                Optional<Recipe> current = catalog.byHytaleId(hytaleId);
                if (current.isEmpty()) {
                    warn.accept("Recipe " + id + " dropped: the game no longer has the Hytale recipe " + hytaleId);
                }
                return current;
            }
            return Optional.of(RecipeJson.read(o));
        } catch (IllegalArgumentException | IllegalStateException | UnsupportedOperationException e) {
            warn.accept("Recipe " + id + " dropped: malformed save (" + e.getMessage() + ")");
            return Optional.empty();
        }
    }

    /** The {@code n} of {@code improved:<n>}; 0 when it is not a number. */
    private static int improvedNumber(String id) {
        try {
            return Integer.parseInt(id.substring(IMPROVED_ID.length()));
        } catch (NumberFormatException _) {
            return 0;
        }
    }

    /** Keeps {@code recipe} under {@code id}, in place of what was there; the first id of a content stays its id. */
    private void put(RecipeId id, Recipe recipe) {
        Recipe replaced = recipes.put(id, recipe);
        if (replaced != null) {
            ids.remove(Key.of(replaced), id);
        }
        ids.putIfAbsent(Key.of(recipe), id);
    }
}
