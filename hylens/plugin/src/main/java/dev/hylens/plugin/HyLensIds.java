package dev.hylens.plugin;

import com.google.gson.Gson;
import com.hypixel.hytale.logger.HytaleLogger;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.logging.Level;
import org.jspecify.annotations.Nullable;

/** HyLens's asset ids, read once from its own hylens/id-map.json. */
public record HyLensIds(Optional<String> watchGameMode) {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();
    private static final String FILE = "/hylens/id-map.json";

    private record Data(@Nullable String watchGameMode) {}

    /** The ids on the mod's classpath; each one empty, with a warning, when the file is missing or unreadable. */
    static HyLensIds load() {
        try (InputStream in = HyLensIds.class.getResourceAsStream(FILE)) {
            if (in == null) {
                LOG.at(Level.WARNING).log("HyLens: %s is missing", FILE);
                return new HyLensIds(Optional.empty());
            }
            Data data = new Gson().fromJson(new String(in.readAllBytes(), StandardCharsets.UTF_8), Data.class);
            return new HyLensIds(Optional.ofNullable(data).map(Data::watchGameMode));
        } catch (IOException | RuntimeException e) {
            LOG.at(Level.WARNING).withCause(e).log("HyLens: %s is unreadable", FILE);
            return new HyLensIds(Optional.empty());
        }
    }
}
