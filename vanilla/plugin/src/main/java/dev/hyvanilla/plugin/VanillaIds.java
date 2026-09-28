package dev.hyvanilla.plugin;

import com.google.gson.Gson;
import com.hypixel.hytale.logger.HytaleLogger;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import org.jspecify.annotations.Nullable;

/** HyVanilla's asset ids, read from its own hyvanilla/id-map.json (written by tools/vanilla): the flower pots. */
final class VanillaIds {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();
    private static final String FILE = "/hyvanilla/id-map.json";

    private record Data(@Nullable Map<String, Map<String, String>> flowerPots) {}

    private VanillaIds() {}

    /**
     * Empty pot block -> plant item -> that pot's block holding it, from the id-map on the mod's classpath; empty, with
     * a warning, when the file is missing, unreadable or malformed (a null entry).
     */
    static Map<String, Map<String, String>> flowerPots() {
        try (InputStream in = VanillaIds.class.getResourceAsStream(FILE)) {
            if (in == null) {
                LOG.at(Level.WARNING).log("HyVanilla: %s is missing", FILE);
                return Map.of();
            }
            Data data = new Gson().fromJson(new String(in.readAllBytes(), StandardCharsets.UTF_8), Data.class);
            Map<String, Map<String, String>> pots =
                    data == null ? Map.of() : Objects.requireNonNullElse(data.flowerPots(), Map.of());
            // Deep copy: a null entry throws here rather than in the system, and never stops the mod loading.
            Map<String, Map<String, String>> copy = new HashMap<>();
            pots.forEach((pot, potted) -> copy.put(pot, Map.copyOf(potted)));
            LOG.at(Level.INFO).log("HyVanilla: %d flower pots", copy.size());
            return Map.copyOf(copy);
        } catch (IOException | RuntimeException e) {
            LOG.at(Level.WARNING).withCause(e).log("HyVanilla: %s is unreadable", FILE);
            return Map.of();
        }
    }
}
