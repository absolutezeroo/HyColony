package dev.hycolony.plugin.config;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.codec.util.RawJsonReader;
import com.hypixel.hytale.logger.HytaleLogger;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.Level;

/**
 * Keeps a malformed config file from aborting the server start. Hytale's {@code Config.load} decodes the file in
 * {@code PluginBase.preLoad}, and its exception escapes {@code PluginManager}'s join: moving the bad file aside first
 * makes {@code Config.load} fall back to the codec defaults, which the plugin then writes back at setup.
 */
public final class ConfigQuarantine {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final int READ_BUFFER_CHARS = 8192;

    private ConfigQuarantine() {}

    /**
     * Decodes {@code file} as {@code Config.load} would; on any failure logs SEVERE and renames it
     * {@code <name>.broken-<timestamp>}. A missing file is left alone. Never throws.
     */
    public static void moveAsideIfUnreadable(Path file, Codec<?> codec) {
        if (!Files.exists(file)) {
            return;
        }
        try {
            decode(file, codec);
        } catch (IOException | RuntimeException e) {
            Path broken = file.resolveSibling(
                    file.getFileName() + ".broken-" + LocalDateTime.now().format(STAMP));
            LOG.at(Level.SEVERE).withCause(e).log(
                    "HyColony: %s is not valid (%s); moved to %s, starting with defaults", file, e, broken);
            try {
                Files.move(file, broken);
            } catch (IOException | RuntimeException moveFailed) {
                LOG.at(Level.SEVERE).withCause(moveFailed).log("HyColony: could not move %s aside", file);
            }
        }
    }

    /** Same steps as {@code RawJsonReader.readSync}, with a fresh ExtraInfo so no thread-local state is left over. */
    private static void decode(Path file, Codec<?> codec) throws IOException {
        try (RawJsonReader reader = RawJsonReader.fromPath(file, new char[READ_BUFFER_CHARS])) {
            ExtraInfo extraInfo = new ExtraInfo();
            codec.decodeJson(reader, extraInfo);
            extraInfo.getValidationResults().logOrThrowValidatorExceptions(LOG);
        }
    }
}
