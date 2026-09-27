package dev.hycolony.plugin.subplugin;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import dev.hycolony.core.FeaturePack;
import dev.hycolony.core.building.BuildingRegistry;
import dev.hycolony.core.job.JobRegistry;
import dev.hycolony.core.kernel.config.FeatureFlags;
import dev.hycolony.core.kernel.config.JsonFragments;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;

/**
 * The optional sub-plugins bundled in the jar ({@code subplugins/index.txt}), set up once in {@code setup()}: an
 * enabled pack ({@code HyColony.SubPlugins} in config.json, else its manifest's {@code EnabledByDefault}) has its
 * assets registered with Hytale, its data fragments merged into the core's files and its registrar run. A pack that
 * fails is logged SEVERE and skipped; HyColony keeps running. A disabled pack adds nothing: its ids are never checked.
 *
 * <p>Translations: a pack's {@code Server/Languages/<locale>/hycolony.lang} keys join the core's, the first loaded
 * winning (plugin-b-api § 21.2), so a pack only adds keys and never redefines a core one.
 */
public final class SubPlugins {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();
    private static final String ROOT = "/subplugins/";

    /** How startup left a pack. */
    public enum State {
        DISABLED,
        ENABLED,
        FAILED
    }

    /** One bundled pack, for the selftest. */
    public record Status(String name, String version, State state) {}

    private record Pack(SubPluginManifest manifest, State state) {}

    private final List<Pack> packs;
    private int fragments;

    private SubPlugins(List<Pack> packs) {
        this.packs = packs;
    }

    /** Reads every bundled pack and registers the enabled ones' assets with Hytale. Call from setup() only. */
    public static SubPlugins load(JavaPlugin plugin, FeatureFlags flags) {
        List<Pack> packs = new ArrayList<>();
        for (String name : index()) {
            SubPluginManifest manifest = new SubPluginManifest(name, "?", false, null, null);
            State state;
            try {
                manifest = readManifest(name);
                state = flags.enabled(name, manifest.enabledByDefault())
                        ? registerAssets(plugin, manifest)
                        : State.DISABLED;
            } catch (IOException | RuntimeException e) {
                LOG.at(Level.SEVERE).withCause(e).log("HyColony sub-plugin %s could not be loaded: skipped", name);
                state = State.FAILED;
            }
            LOG.at(Level.INFO).log("HyColony sub-plugin %s %s: %s", name, manifest.version(), state);
            packs.add(new Pack(manifest, state));
        }
        return new SubPlugins(List.copyOf(packs));
    }

    /**
     * {@code hycolony/<file>} of the core, then each enabled pack's fragment of it, merged {@code depth} levels deep
     * (see {@link JsonFragments}); every key defined twice is logged SEVERE and keeps its first definition.
     */
    public JsonObject merged(String file, int depth) {
        JsonFragments merged = new JsonFragments(depth);
        merged.add("HyColony", read("/hycolony/" + file).orElseThrow());
        for (Pack pack : enabled()) {
            String name = pack.manifest().name();
            try {
                read(ROOT + name + "/hycolony/" + file).ifPresent(fragment -> {
                    fragments++;
                    merged.add(name, fragment)
                            .forEach(c -> LOG.at(Level.SEVERE).log(
                                    "HyColony %s: %s is defined by %s and by %s; %s is kept",
                                    file, c.path(), c.first(), c.second(), c.first()));
                });
            } catch (RuntimeException e) {
                LOG.at(Level.SEVERE).withCause(e).log("HyColony sub-plugin %s: bad %s, not merged", name, file);
            }
        }
        return merged.merged();
    }

    /** Runs each enabled pack's {@code Registrar}; one that fails is logged SEVERE, the others still register. */
    public void registerFeatures(BuildingRegistry buildings, JobRegistry jobs) {
        for (Pack pack : enabled()) {
            String registrar = pack.manifest().registrar();
            if (registrar == null) {
                continue;
            }
            try {
                FeaturePack features = Class.forName(registrar)
                        .asSubclass(FeaturePack.class)
                        .getDeclaredConstructor()
                        .newInstance();
                features.register(buildings, jobs);
            } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                LOG.at(Level.SEVERE).withCause(e).log(
                        "HyColony sub-plugin %s: registrar %s failed",
                        pack.manifest().name(), registrar);
            }
        }
    }

    /** Every bundled pack, in index order. */
    public List<Status> statuses() {
        return packs.stream()
                .map(p -> new Status(p.manifest().name(), p.manifest().version(), p.state()))
                .toList();
    }

    /** Fragments merged by {@link #merged} so far, all files together. */
    public int fragmentsMerged() {
        return fragments;
    }

    private List<Pack> enabled() {
        return packs.stream().filter(p -> p.state() == State.ENABLED).toList();
    }

    /** ENABLED once its assets are registered, or at once for a pack without assets (data fragments only). */
    private static State registerAssets(JavaPlugin plugin, SubPluginManifest manifest) throws IOException {
        try (InputStream zip = SubPlugins.class.getResourceAsStream(ROOT + manifest.name() + ".zip")) {
            if (zip == null) {
                return State.ENABLED;
            }
            if (PackAssets.register(zip, manifest, plugin.getManifest(), plugin.getDataDirectory())) {
                return State.ENABLED;
            }
            LOG.at(Level.SEVERE).log("HyColony sub-plugin %s: Hytale refused its asset pack", manifest.name());
            return State.FAILED;
        }
    }

    private static SubPluginManifest readManifest(String name) throws IOException {
        try (InputStream in = SubPlugins.class.getResourceAsStream(ROOT + name + "/subplugin.json")) {
            if (in == null) {
                throw new IOException("no subplugin.json");
            }
            return new Gson().fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), SubPluginManifest.class);
        }
    }

    /** The bundled pack names; none when the index is missing. */
    private static List<String> index() {
        try (InputStream in = SubPlugins.class.getResourceAsStream(ROOT + "index.txt")) {
            if (in == null) {
                return List.of();
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8)
                    .lines()
                    .map(String::strip)
                    .filter(s -> !s.isEmpty())
                    .toList();
        } catch (IOException e) {
            LOG.at(Level.SEVERE).withCause(e).log("HyColony: cannot read the sub-plugin index");
            return List.of();
        }
    }

    /** A classpath JSON object, or empty when absent; throws when it is not a JSON object. */
    private static Optional<JsonObject> read(String path) {
        try (InputStream in = SubPlugins.class.getResourceAsStream(path)) {
            if (in == null) {
                return Optional.empty();
            }
            return Optional.of(JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                    .getAsJsonObject());
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + path, e);
        }
    }
}
