package dev.hycolony.core.crafting.module;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.hycolony.core.crafting.recipe.RecipeId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;

/**
 * The ordered recipe ids of a crafting module and the disabled ones (MC AbstractCraftingBuildingModule.recipes and
 * disabledRecipes), saved as {@code {"recipes": [..], "disabled": [..]}}. Never lists an id twice.
 */
final class RecipeList {
    private static final String RECIPES = "recipes";
    private static final String DISABLED = "disabled";

    private final List<RecipeId> ids = new ArrayList<>();
    private final Set<RecipeId> disabled = new LinkedHashSet<>();

    /** The ids in order; read-only. */
    List<RecipeId> ids() {
        return Collections.unmodifiableList(ids);
    }

    boolean isDisabled(RecipeId id) {
        return disabled.contains(id);
    }

    /** Whether {@code index} is a position of the list. */
    boolean inRange(int index) {
        return index >= 0 && index < ids.size();
    }

    /** MC addRecipeToList: adds the id first or last; false, changing nothing, if already listed. */
    boolean add(RecipeId id, boolean atTop) {
        if (ids.contains(id)) {
            return false;
        }
        ids.add(atTop ? 0 : ids.size(), id);
        return true;
    }

    /** Removes the id and its disabled mark; false if it was not listed. */
    boolean remove(RecipeId id) {
        if (!ids.remove(id)) {
            return false;
        }
        disabled.remove(id);
        return true;
    }

    /** Disables the id, or enables it again; returns whether it is now enabled. */
    boolean toggle(RecipeId id) {
        if (disabled.remove(id)) {
            return true;
        }
        disabled.add(id);
        return false;
    }

    /**
     * MC switchOrder: a full move sends the id at {@code i} first if {@code i > j}, else last; otherwise swaps
     * {@code i} and {@code j}. Returns false, changing nothing, for an index outside the list.
     */
    boolean move(int i, int j, boolean fullMove) {
        if (fullMove && inRange(i)) {
            RecipeId moved = ids.remove(i);
            ids.add(i > j ? 0 : ids.size(), moved);
            return true;
        }
        if (!fullMove && inRange(i) && inRange(j)) {
            Collections.swap(ids, i, j);
            return true;
        }
        return false;
    }

    /**
     * MC replaceRecipe: {@code newId} takes the place of {@code oldId}, or, if already listed, {@code oldId} is only
     * removed; false if {@code oldId} is not listed.
     */
    boolean replace(RecipeId oldId, RecipeId newId) {
        int index = ids.indexOf(oldId);
        if (index < 0) {
            return false;
        }
        if (ids.contains(newId)) {
            ids.remove(index);
        } else {
            ids.set(index, newId);
        }
        return true;
    }

    /** Keeps the ids {@code keep} accepts and the disabled marks of the ids still listed; returns whether any went. */
    boolean retain(Predicate<RecipeId> keep) {
        boolean changed = ids.removeIf(keep.negate());
        return disabled.removeIf(id -> !ids.contains(id)) || changed;
    }

    void write(JsonObject out) {
        out.add(RECIPES, array(ids));
        out.add(DISABLED, array(disabled));
    }

    /** MC deserializeNBT: a missing list reads as empty; an entry that is not a string, or listed twice, is skipped. */
    void read(JsonObject in) {
        ids.clear();
        disabled.clear();
        readInto(in.get(RECIPES), ids);
        readInto(in.get(DISABLED), disabled);
    }

    private static JsonArray array(Collection<RecipeId> saved) {
        JsonArray out = new JsonArray();
        saved.forEach(id -> out.add(id.value()));
        return out;
    }

    private static void readInto(@Nullable JsonElement saved, Collection<RecipeId> into) {
        if (!(saved instanceof JsonArray array)) {
            return;
        }
        for (JsonElement e : array) {
            if (e instanceof JsonPrimitive p && p.isString() && !into.contains(new RecipeId(p.getAsString()))) {
                into.add(new RecipeId(p.getAsString()));
            }
        }
    }
}
