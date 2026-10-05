package dev.hyangler.plugin;

import com.google.gson.Gson;
import com.hypixel.hytale.logger.HytaleLogger;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;
import org.jspecify.annotations.Nullable;

/**
 * HyAngler's asset ids, read from its own hyangler/id-map.json (CLAUDE.md § 7). No id lives in the code: a missing key
 * gives an empty list or an empty id, which its user treats as an unknown asset.
 *
 * @param saltEnvironments the environments whose water is salt (oceans and shores)
 * @param zones the environment tags that name a zone (ZoneN)
 * @param precipitation the weathers' particle systems that count as rain or snow
 * @param waterSurface the blocks that float on water and keep it open (vanilla's lily pad)
 */
public record AnglerIds(
        Set<String> saltEnvironments,
        List<String> zones,
        Set<String> precipitation,
        Set<String> waterSurface,
        String beam,
        String animations,
        String bobber,
        String bubbles,
        String splash,
        String tease,
        String biteSound,
        String reelSound) {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();
    private static final String FILE = "/hyangler/id-map.json";
    private static final Data EMPTY = new Data(null, null, null, null, null, null, null, null, null);

    private record Data(
            @Nullable List<String> saltEnvironments,
            @Nullable List<String> zones,
            @Nullable List<String> precipitation,
            @Nullable List<String> waterSurface,
            @Nullable String beam,
            @Nullable String animations,
            @Nullable String bobber,
            @Nullable Map<String, String> particles,
            @Nullable Map<String, String> sounds) {}

    /** The id-map on the mod's classpath; empty ids, with a warning, when it is missing or malformed. Never throws. */
    public static AnglerIds load() {
        try (InputStream in = AnglerIds.class.getResourceAsStream(FILE)) {
            Data data = in == null
                    ? null
                    : new Gson().fromJson(new String(in.readAllBytes(), StandardCharsets.UTF_8), Data.class);
            if (data == null) {
                LOG.at(Level.WARNING).log("HyAngler: %s is missing or empty", FILE);
                return from(EMPTY);
            }
            return from(data);
        } catch (IOException | RuntimeException e) { // a null entry throws in from(): treated as malformed
            LOG.at(Level.WARNING).withCause(e).log("HyAngler: %s is unreadable", FILE);
            return from(EMPTY);
        }
    }

    private static AnglerIds from(Data d) {
        Map<String, String> particles = Objects.requireNonNullElse(d.particles(), Map.of());
        Map<String, String> sounds = Objects.requireNonNullElse(d.sounds(), Map.of());
        return new AnglerIds(
                Set.copyOf(Objects.requireNonNullElse(d.saltEnvironments(), List.of())),
                List.copyOf(Objects.requireNonNullElse(d.zones(), List.of())),
                Set.copyOf(Objects.requireNonNullElse(d.precipitation(), List.of())),
                Set.copyOf(Objects.requireNonNullElse(d.waterSurface(), List.of())),
                Objects.requireNonNullElse(d.beam(), ""),
                Objects.requireNonNullElse(d.animations(), ""),
                Objects.requireNonNullElse(d.bobber(), ""),
                Objects.requireNonNullElse(particles.get("bubbles"), ""),
                Objects.requireNonNullElse(particles.get("splash"), ""),
                Objects.requireNonNullElse(particles.get("tease"), ""),
                Objects.requireNonNullElse(sounds.get("bite"), ""),
                Objects.requireNonNullElse(sounds.get("reel"), ""));
    }
}
