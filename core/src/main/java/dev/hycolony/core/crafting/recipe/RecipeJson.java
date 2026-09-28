package dev.hycolony.core.crafting.recipe;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.item.ToolType;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * One {@link Recipe} to and from its saved JSON (MC RecipeStorageFactory.serialize and deserialize). Reading a
 * malformed entry throws {@link IllegalArgumentException}, or Gson's {@link IllegalStateException} or {@link
 * UnsupportedOperationException}; {@link RecipeRegistry} then skips it.
 */
final class RecipeJson {
    /** Source prefix of a Hytale recipe, followed by its Hytale id. */
    static final String HYTALE = "hytale:";
    /** Source prefix of a custom recipe, followed by its {@code crafting.json} id. */
    static final String CUSTOM = "custom:";
    /** Source of an improved recipe. */
    static final String IMPROVED = "improved";

    private static final String SOURCE = "source";

    private RecipeJson() {}

    /** The whole recipe: inputs as taught (not cleaned), outputs, bench, tool when one is needed, source, knowledge. */
    static JsonObject write(Recipe r) {
        JsonObject o = sourceOnly(r.source());
        JsonArray inputs = new JsonArray();
        r.inputs().forEach(in -> inputs.add(ingredient(in)));
        o.add("inputs", inputs);
        o.add("output", stack(r.primaryOutput()));
        JsonArray secondary = new JsonArray();
        r.secondaryOutputs().forEach(s -> secondary.add(stack(s)));
        o.add("secondary", secondary);
        o.add("bench", bench(r.bench()));
        r.requiredTool().ifPresent(t -> o.addProperty("tool", t.name()));
        o.addProperty("knowledge", r.knowledgeRequired());
        return o;
    }

    /** Only the source: what a recipe the game keeps (a Hytale one) needs to be found again. */
    static JsonObject sourceOnly(RecipeSource source) {
        JsonObject o = new JsonObject();
        o.addProperty(
                SOURCE,
                switch (source) {
                    case RecipeSource.Hytale(String id) -> HYTALE + id;
                    case RecipeSource.Custom(String id) -> CUSTOM + id;
                    case RecipeSource.Improved() -> IMPROVED;
                });
        return o;
    }

    /** The saved source; throws for one this build does not know. */
    static RecipeSource source(JsonObject o) {
        String source = required(o, SOURCE).getAsString();
        if (source.startsWith(HYTALE)) {
            return new RecipeSource.Hytale(source.substring(HYTALE.length()));
        }
        if (source.startsWith(CUSTOM)) {
            return new RecipeSource.Custom(source.substring(CUSTOM.length()));
        }
        if (IMPROVED.equals(source)) {
            return new RecipeSource.Improved();
        }
        throw new IllegalArgumentException("unknown recipe source " + source);
    }

    /**
     * The saved recipe; throws when a part is missing or malformed, and, as MC StandardRecipeManager.read skips one,
     * for a recipe without input. Missing secondary outputs, tool or knowledge read as none.
     */
    static Recipe read(JsonObject o) {
        List<Ingredient> inputs = new ArrayList<>();
        for (JsonElement in : required(o, "inputs").getAsJsonArray()) {
            inputs.add(ingredient(in.getAsJsonObject()));
        }
        if (inputs.isEmpty()) {
            throw new IllegalArgumentException("recipe without input");
        }
        List<ItemAmount> secondary = new ArrayList<>();
        if (o.get("secondary") instanceof JsonArray saved) {
            for (JsonElement s : saved) {
                secondary.add(stack(s.getAsJsonObject()));
            }
        }
        Optional<ToolType> tool = o.get("tool") instanceof JsonPrimitive t
                ? Optional.of(ToolType.valueOf(t.getAsString()))
                : Optional.empty();
        return new Recipe(
                inputs,
                stack(required(o, "output").getAsJsonObject()),
                secondary,
                bench(required(o, "bench").getAsJsonObject()),
                tool,
                source(o),
                o.get("knowledge") instanceof JsonPrimitive k && k.getAsBoolean());
    }

    private static JsonObject bench(BenchRequirement b) {
        JsonObject o = new JsonObject();
        o.addProperty("id", b.benchId());
        JsonArray categories = new JsonArray();
        b.categories().forEach(categories::add);
        o.add("categories", categories);
        o.addProperty("tier", b.requiredTier());
        return o;
    }

    private static BenchRequirement bench(JsonObject o) {
        List<String> categories = new ArrayList<>();
        for (JsonElement c : required(o, "categories").getAsJsonArray()) {
            categories.add(c.getAsString());
        }
        return new BenchRequirement(
                required(o, "id").getAsString(), categories, required(o, "tier").getAsInt());
    }

    private static JsonObject ingredient(Ingredient in) {
        JsonObject o = new JsonObject();
        switch (in) {
            case Ingredient.OfItem i -> {
                o.addProperty("kind", "item");
                o.addProperty("id", i.item().id());
            }
            case Ingredient.OfResourceType t -> {
                o.addProperty("kind", "type");
                o.addProperty("id", t.id());
            }
            case Ingredient.OfTag t -> {
                o.addProperty("kind", "tag");
                o.addProperty("id", t.id());
            }
        }
        o.addProperty("amount", in.amount());
        return o;
    }

    private static Ingredient ingredient(JsonObject o) {
        String id = required(o, "id").getAsString();
        int amount = required(o, "amount").getAsInt();
        return switch (required(o, "kind").getAsString()) {
            case "item" -> new Ingredient.OfItem(new ItemKey(id), amount);
            case "type" -> new Ingredient.OfResourceType(id, amount);
            case "tag" -> new Ingredient.OfTag(id, amount);
            default -> throw new IllegalArgumentException("unknown ingredient kind in " + o);
        };
    }

    private static JsonObject stack(ItemAmount a) {
        JsonObject o = new JsonObject();
        o.addProperty("item", a.item().id());
        o.addProperty("count", a.count());
        return o;
    }

    private static ItemAmount stack(JsonObject o) {
        return new ItemAmount(
                new ItemKey(required(o, "item").getAsString()),
                required(o, "count").getAsInt());
    }

    /** The value under {@code key}; throws when it is absent or null. */
    private static JsonElement required(JsonObject o, String key) {
        JsonElement e = o.get(key);
        if (e == null || e.isJsonNull()) {
            throw new IllegalArgumentException("missing " + key);
        }
        return e;
    }
}
