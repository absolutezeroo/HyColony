package dev.hycolony.plugin.subplugin;

import com.google.gson.JsonObject;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import dev.hycolony.core.CoreFeatures;
import dev.hycolony.core.FeaturePack;
import dev.hycolony.core.building.BuildingRegistry;
import dev.hycolony.core.job.JobRegistry;
import dev.hycolony.core.kernel.config.FeatureFlags;
import dev.hycolony.core.kernel.config.JsonFragments;
import dev.hycolony.plugin.IdMap;
import dev.hycolony.plugin.prefab.PrefabStyles;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Level;
import org.jspecify.annotations.Nullable;

/**
 * The optional sub-plugins bundled in the jar, set up once in {@code setup()}, in {@code Order}: an enabled pack
 * ({@code HyColony.SubPlugins} in config.json, else its manifest's {@code EnabledByDefault}) has its fragments checked,
 * its assets registered with Hytale, its fragments merged into the core's files and its registrar run. A pack that
 * fails at any of these steps is FAILED, logged SEVERE, and adds nothing more; HyColony keeps running. A disabled pack
 * adds nothing: its ids are never checked. A missing asset id in an enabled pack's fragment still disables all of
 * HyColony (IdMap.validate, once assets are loaded).
 *
 * <p>Not caught here: once registered, the pack's zip is an immutable pack, and Hytale shuts the whole server down when
 * one of its assets fails to load or validate at LoadAssetEvent (a Common path outside the allowed roots, a missing
 * file...; plugin-b-api § 23). The generator and the build check the packs' asset paths for that reason.
 *
 * <p>Translations: a pack's {@code Server/Languages/<locale>/hycolony.lang} keys join the core's, the first loaded
 * winning (plugin-b-api § 21.2), so a pack only adds keys and never redefines a core one.
 */
public final class SubPlugins {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();
    private static final String ID_MAP = "id-map.json";
    private static final String STYLES = "styles.json";
    /** id-map.json merges key by key inside its sections (items, blocks...). */
    private static final int ID_MAP_DEPTH = 1;
    /** styles.json merges key by key inside a style and its building types; a level is defined once. */
    private static final int STYLES_DEPTH = 2;

    /** How startup left a pack. */
    public enum State {
        DISABLED,
        ENABLED,
        FAILED
    }

    /** One bundled pack, for the selftest. */
    public record Status(String name, String version, State state) {}

    /** {@code assetPackId}: the Hytale asset pack registered for it, if any. */
    private record Pack(
            String name,
            SubPluginManifest manifest,
            State state,
            @Nullable String assetPackId) {
        Pack failed() {
            return new Pack(name, manifest, State.FAILED, assetPackId);
        }
    }

    private final List<Pack> packs;
    private int fragments;

    private SubPlugins(List<Pack> packs) {
        this.packs = packs;
    }

    /** No pack: what the plugin holds before its setup. */
    public static SubPlugins none() {
        return new SubPlugins(new ArrayList<>());
    }

    /** Reads every bundled pack and registers the enabled ones' assets with Hytale. Call from setup() only. */
    public static SubPlugins load(JavaPlugin plugin, FeatureFlags flags) {
        List<Pack> packs = new ArrayList<>();
        try {
            BundledPacks.names().forEach(name -> packs.add(load(plugin, flags, name)));
        } catch (IOException | RuntimeException e) {
            LOG.at(Level.SEVERE).withCause(e).log("HyColony: cannot read the sub-plugin index; no sub-plugin loaded");
        }
        packs.sort(Comparator.comparingInt((Pack p) -> p.manifest().sortOrder()).thenComparing(Pack::name));
        return new SubPlugins(packs);
    }

    private static Pack load(JavaPlugin plugin, FeatureFlags flags, String name) {
        Pack pack;
        try {
            SubPluginManifest manifest = BundledPacks.manifest(name);
            if (flags.enabled(name, manifest.enabledByDefault())) {
                checkFragments(name);
                pack = registerAssets(plugin, name, manifest);
            } else {
                pack = new Pack(name, manifest, State.DISABLED, null);
            }
        } catch (IOException | RuntimeException e) {
            LOG.at(Level.SEVERE).withCause(e).log("HyColony sub-plugin %s could not be loaded: skipped", name);
            pack = new Pack(name, SubPluginManifest.unreadable(name), State.FAILED, null);
        }
        LOG.at(Level.INFO).log(
                "HyColony sub-plugin %s %s: %s", name, pack.manifest().versionOrUnknown(), pack.state());
        return pack;
    }

    /** Reads each fragment alone with its typed reader, so a malformed one fails its pack before anything is merged. */
    private static void checkFragments(String name) {
        BundledPacks.fragment(name, ID_MAP).ifPresent(IdMap::of);
        BundledPacks.fragment(name, STYLES).ifPresent(PrefabStyles::of);
    }

    /** ENABLED once its assets are registered, or at once for a pack without assets (data fragments only). */
    private static Pack registerAssets(JavaPlugin plugin, String name, SubPluginManifest manifest) throws IOException {
        try (InputStream zip = BundledPacks.zip(name)) {
            if (zip == null) {
                return new Pack(name, manifest, State.ENABLED, null);
            }
            String id = PackAssets.register(zip, name, manifest, plugin).orElse(null);
            if (id == null) {
                LOG.at(Level.SEVERE).log("HyColony sub-plugin %s: Hytale refused its asset pack", name);
                return new Pack(name, manifest, State.FAILED, null);
            }
            return new Pack(name, manifest, State.ENABLED, id);
        }
    }

    /** The core id-map merged with the enabled packs' fragments. */
    public IdMap idMap() {
        return IdMap.of(merged(ID_MAP, ID_MAP_DEPTH));
    }

    /** The core styles merged with the enabled packs' fragments; styles come in pack order. */
    public PrefabStyles styles() {
        return PrefabStyles.of(merged(STYLES, STYLES_DEPTH));
    }

    /** {@code hycolony/<file>} merged with each enabled pack's fragment; a key defined twice is logged SEVERE. */
    private JsonObject merged(String file, int depth) {
        JsonFragments merged = new JsonFragments(depth);
        merged.add("HyColony", BundledPacks.json("/hycolony/" + file).orElseThrow());
        for (Pack pack : enabled()) {
            BundledPacks.fragment(pack.name(), file).ifPresent(fragment -> {
                fragments++;
                merged.add(pack.name(), fragment)
                        .forEach(c -> LOG.at(Level.SEVERE).log(
                                "HyColony %s: %s is defined by %s and by %s; %s is kept",
                                file, c.path(), c.first(), c.second(), c.first()));
            });
        }
        return merged.merged();
    }

    /**
     * Runs each enabled pack's {@code Registrar} through {@link CoreFeatures#registerPack}: its huts must be in
     * {@code ids}. A pack that fails or throws registers nothing and becomes FAILED; the others still register.
     */
    public void registerFeatures(BuildingRegistry buildings, JobRegistry jobs, IdMap ids) {
        for (Pack pack : enabled()) {
            String registrar = pack.manifest().registrar();
            if (registrar == null) {
                continue;
            }
            List<String> problems;
            try {
                FeaturePack features = Class.forName(registrar)
                        .asSubclass(FeaturePack.class)
                        .getDeclaredConstructor()
                        .newInstance();
                problems = CoreFeatures.registerPack(features, buildings, jobs, ids::hasHut);
            } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                problems = List.of(e.toString());
            }
            if (!problems.isEmpty()) {
                problems.forEach(p -> LOG.at(Level.SEVERE).log(
                        "HyColony sub-plugin %s: registrar %s registered nothing: %s", pack.name(), registrar, p));
                packs.replaceAll(p -> p.name().equals(pack.name()) ? p.failed() : p);
            }
        }
    }

    /**
     * On a plugin unload (a reload), unregisters the asset packs registered at setup; a failure is logged. On a server
     * shutdown they stay, as vanilla leaves every pack then.
     */
    public void unregisterAssets() {
        if (HytaleServer.get().isShuttingDown()) {
            return;
        }
        for (Pack pack : packs) {
            String id = pack.assetPackId();
            if (id != null) {
                try {
                    PackAssets.unregister(id);
                } catch (RuntimeException e) {
                    LOG.at(Level.SEVERE).withCause(e).log(
                            "HyColony sub-plugin %s: cannot unregister %s", pack.name(), id);
                }
            }
        }
    }

    /** Every bundled pack, in order. */
    public List<Status> statuses() {
        return packs.stream()
                .map(p -> new Status(p.name(), p.manifest().versionOrUnknown(), p.state()))
                .toList();
    }

    /** Fragments merged by {@link #idMap} and {@link #styles} so far, both files together. */
    public int fragmentsMerged() {
        return fragments;
    }

    /** True when the bundled pack {@code name} is ENABLED: set up at startup, and neither disabled nor failed. */
    public boolean isEnabled(String name) {
        return enabled().stream().anyMatch(p -> p.name().equals(name));
    }

    private List<Pack> enabled() {
        return packs.stream().filter(p -> p.state() == State.ENABLED).toList();
    }
}
