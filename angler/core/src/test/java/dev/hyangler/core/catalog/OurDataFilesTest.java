package dev.hyangler.core.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hyangler.api.CatchCategory;
import dev.hyangler.api.CatchChance;
import dev.hyangler.api.FishingContext;
import dev.hyangler.api.Tackle;
import dev.hyangler.api.WaterKind;
import dev.hyangler.core.catalog.RawFile.Kind;
import dev.hyangler.core.condition.ConditionRegistry;
import dev.hyangler.core.roll.CatchRoller;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** HyAngler's own data files, read straight from the pack (without rarity states): none may be left out. */
class OurDataFilesTest {
    /** Gradle runs the tests from angler/core. */
    private static final Path DATA = Path.of("..", "plugin", "src", "main", "resources", "Server", "HyAngler");

    private static List<RawFile> files() throws IOException {
        List<RawFile> out = new ArrayList<>();
        for (Kind kind : Kind.values()) {
            String dir = switch (kind) {
                case FISH -> "Fish";
                case CATCH -> "Catches";
                case ROD -> "Rods";
            };
            try (Stream<Path> paths = Files.list(DATA.resolve(dir))) {
                for (Path p : paths.sorted().toList()) {
                    String id = p.getFileName().toString().replace(".json", "");
                    out.add(new RawFile(kind, id, Files.readString(p, StandardCharsets.UTF_8)));
                }
            }
        }
        return out;
    }

    private static Catalog catalog() throws IOException {
        return CatalogReader.read(files(), new ConditionRegistry(), Set.of(), 32);
    }

    private static List<String> fishAt(String env, String zone, WaterKind water, double hour) throws IOException {
        FishingContext ctx =
                new FishingContext(env, zone, water, 4, true, true, hour, "Zone1_Sunny", false, 0, Tackle.NONE);
        return new CatchRoller(catalog(), e -> {
                    throw new AssertionError(e);
                })
                .chances(ctx).stream()
                        .filter(c -> c.category() == CatchCategory.FISH)
                        .map(CatchChance::itemId)
                        .toList();
    }

    @Test
    void everyFileOfOurPackIsRead() throws IOException {
        Catalog c = catalog();
        assertTrue(c.rejections().isEmpty(), () -> "rejected: " + c.rejections());
        assertEquals(28, c.fish().size());
        assertEquals(21, c.junk().size() + c.treasure().size());
        assertEquals(8, c.rods().size());
    }

    @Test
    void troutLivesInForestsByDayOnly() throws IOException {
        assertTrue(fishAt("Env_Zone1_Forests", "Zone1", WaterKind.FRESH, 12).contains("Fish_Trout_Rainbow_Item"));
        assertFalse(fishAt("Env_Zone1_Forests", "Zone1", WaterKind.FRESH, 23.5).contains("Fish_Trout_Rainbow_Item"));
        assertFalse(fishAt("Env_Zone1_Plains", "Zone1", WaterKind.FRESH, 12).contains("Fish_Trout_Rainbow_Item"));
    }

    @Test
    void catfishBitesAtNight() throws IOException {
        assertTrue(fishAt("Env_Zone1_Forests", "Zone1", WaterKind.FRESH, 23).contains("Fish_Catfish_Item"));
        assertFalse(fishAt("Env_Zone1_Forests", "Zone1", WaterKind.FRESH, 12).contains("Fish_Catfish_Item"));
    }

    @Test
    void noWhaleAndNoLavaShellfish() throws IOException {
        List<String> ids = catalog().fish().stream().map(Entry::id).toList();
        assertFalse(ids.contains("Fish_Whale_Humpback_Item"));
        assertFalse(ids.contains("Fish_Shellfish_Lava_Item"));
    }

    @Test
    void reefFishLiveOnTheShores() throws IOException {
        assertTrue(fishAt("Env_Zone1_Shores", "Zone1", WaterKind.SALT, 12).contains("Fish_Clownfish_Item"));
    }
}
