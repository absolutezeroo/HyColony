package dev.hycolony.plugin.ornament.cutter;

import dev.hycolony.plugin.ornament.registry.OrnamentVariantRegistry;
import java.util.Optional;

/**
 * What every cutter window shares: the ornaments, the groups players last chose, a craft's time in milliseconds (0: at
 * once), and the sound events played when a window opens and closes (the builder bench's, from the id-map).
 */
public record CutterSettings(
        OrnamentVariantRegistry registry,
        CutterGroupMemory memory,
        long craftMillis,
        Optional<String> openSound,
        Optional<String> closeSound) {}
