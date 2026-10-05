package dev.hyangler.core.catalog;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.hyangler.api.CatchCategory;
import dev.hyangler.api.RodStats;
import dev.hyangler.core.condition.ConditionRegistry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads the data files into a catalog, tolerantly (CLAUDE.md § 5): an absent key takes its default, an unknown key is
 * ignored, an invalid file is left out with its reason, and never stops the others.
 */
public final class CatalogReader {
    private CatalogReader() {}

    /**
     * The catalog of files; rarityItems names the items that carry rarity states (only they roll one), defaultLine is a
     * rod's line when its file gives none.
     */
    public static Catalog read(
            List<RawFile> files, ConditionRegistry registry, Set<String> rarityItems, int defaultLine) {
        List<Entry> fish = new ArrayList<>();
        List<Entry> junk = new ArrayList<>();
        List<Entry> treasure = new ArrayList<>();
        Map<String, RodStats> rods = new HashMap<>();
        List<Rejection> rejections = new ArrayList<>();
        for (RawFile file : files) {
            try {
                JsonObject json = JsonParser.parseString(file.json()).getAsJsonObject();
                switch (file.kind()) {
                    case FISH -> fish.add(entry(file.id(), CatchCategory.FISH, json, registry, rarityItems));
                    case CATCH -> {
                        Entry e = entry(file.id(), category(json), json, registry, Set.of());
                        (e.category() == CatchCategory.JUNK ? junk : treasure).add(e);
                    }
                    case ROD -> rods.put(file.id(), rod(json, defaultLine));
                }
            } catch (RuntimeException e) { // a malformed file must never stop the others (CLAUDE.md § 5)
                rejections.add(new Rejection(file.kind(), file.id(), String.valueOf(e.getMessage())));
            }
        }
        return new Catalog(
                List.copyOf(fish), List.copyOf(junk), List.copyOf(treasure), Map.copyOf(rods), List.copyOf(rejections));
    }

    private static Entry entry(
            String id, CatchCategory category, JsonObject json, ConditionRegistry registry, Set<String> rarityItems) {
        int weight = integer(json, "Weight", 0);
        if (weight < 1) {
            throw new IllegalArgumentException("Weight must be at least 1");
        }
        int[] count = count(json);
        // Rarities can only turn the states off: an item without them has no state to give.
        boolean rarities = rarityItems.contains(id)
                && (!json.has("Rarities") || json.get("Rarities").getAsBoolean());
        return new Entry(
                id,
                category,
                weight,
                integer(json, "Quality", 0),
                count[0],
                count[1],
                rarities,
                registry.parseAll(json.get("Conditions")),
                modifiers(json, registry));
    }

    private static CatchCategory category(JsonObject json) {
        String category = json.has("Category") ? json.get("Category").getAsString() : "";
        return switch (category) {
            case "Junk" -> CatchCategory.JUNK;
            case "Treasure" -> CatchCategory.TREASURE;
            default -> throw new IllegalArgumentException("Category is Junk or Treasure, not '" + category + "'");
        };
    }

    private static int[] count(JsonObject json) {
        JsonElement e = json.get("Count");
        if (e == null) {
            return new int[] {1, 1};
        }
        JsonArray a = e.getAsJsonArray();
        int min = a.get(0).getAsInt();
        int max = a.size() > 1 ? a.get(1).getAsInt() : min;
        if (min < 1 || max < min) {
            throw new IllegalArgumentException("Count needs 1 <= min <= max");
        }
        return new int[] {min, max};
    }

    private static List<Modifier> modifiers(JsonObject json, ConditionRegistry registry) {
        JsonElement e = json.get("Modifiers");
        if (e == null) {
            return List.of();
        }
        List<Modifier> out = new ArrayList<>();
        for (JsonElement m : e.getAsJsonArray()) {
            JsonObject o = m.getAsJsonObject();
            double multiplier = o.get("Multiplier").getAsDouble();
            if (!Double.isFinite(multiplier) || multiplier < 0) {
                throw new IllegalArgumentException("Multiplier must be a finite number, 0 or more");
            }
            out.add(new Modifier(registry.parse(o.getAsJsonObject("If")), multiplier));
        }
        return List.copyOf(out);
    }

    private static RodStats rod(JsonObject json, int defaultLine) {
        RodStats rod = new RodStats(
                integer(json, "Tier", 0),
                integer(json, "Lure", 0),
                integer(json, "Luck", 0),
                integer(json, "MaxLine", defaultLine));
        rod.tackle(); // validates lure, luck and line (throws IllegalArgumentException)
        return rod;
    }

    private static int integer(JsonObject json, String key, int fallback) {
        return json.has(key) ? json.get(key).getAsInt() : fallback;
    }
}
