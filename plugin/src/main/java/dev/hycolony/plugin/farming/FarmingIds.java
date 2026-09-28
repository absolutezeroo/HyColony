package dev.hycolony.plugin.farming;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * The {@code farming} section of the id-map (sp3b-hytale-farming § 2-3): crop seed → crop block, the soils a hoe tills,
 * the tilled soil, the fertilizer tool, the hoes with their tool level, the blocks that keep a cell out of a field, and
 * the till sound.
 * Absent from an older id-map: nothing farms.
 */
public record FarmingIds(
        @Nullable Map<String, String> seeds,
        @Nullable List<String> tillable,
        @Nullable String tilled,
        @Nullable String fertilizer,
        @Nullable Map<String, Integer> hoes,
        @Nullable List<String> fieldBarriers,
        @Nullable String tillSound) {
    /** An id-map without farming. */
    public static final FarmingIds NONE = new FarmingIds(null, null, null, null, null, null, null);

    public Map<String, String> seedCrops() {
        return Objects.requireNonNullElse(seeds, Map.of());
    }

    public List<String> tillableSoils() {
        return Objects.requireNonNullElse(tillable, List.of());
    }

    public String tilledSoil() {
        return Objects.requireNonNullElse(tilled, "Soil_Dirt_Tilled");
    }

    public String fertilizerTool() {
        return Objects.requireNonNullElse(fertilizer, "Tool_Fertilizer");
    }

    /** Hoe item → tool level (0 for the crudest). */
    public Map<String, Integer> hoeLevels() {
        return Objects.requireNonNullElse(hoes, Map.of());
    }

    /** The sound event of a hoe tilling (vanilla Hoe_Till's WorldSoundEventId). */
    public String tillSoundEvent() {
        return Objects.requireNonNullElse(tillSound, "SFX_Hoe_T1_Till");
    }

    public List<String> barriers() {
        return Objects.requireNonNullElse(fieldBarriers, List.of());
    }
}
