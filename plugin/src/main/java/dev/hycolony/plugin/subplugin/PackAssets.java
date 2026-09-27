package dev.hycolony.plugin.subplugin;

import com.hypixel.hytale.assetstore.AssetPack;
import com.hypixel.hytale.common.plugin.PluginIdentifier;
import com.hypixel.hytale.common.plugin.PluginManifest;
import com.hypixel.hytale.common.semver.Semver;
import com.hypixel.hytale.server.core.asset.AssetModule;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * Hands a sub-plugin's {@code Common/} and {@code Server/} to Hytale: the zip bundled in the jar is copied to the
 * plugin's data directory, then registered as an asset pack. Only from {@code setup()}: the pack then loads with all
 * the others at LoadAssetEvent, and no asset lock is taken (plugin-b-api § 21.1).
 */
final class PackAssets {
    private PackAssets() {}

    /**
     * Registers {@code zip} (the bundled bytes) as the asset pack {@code <group>:<plugin>_<Name>}. Returns false if
     * Hytale refuses it (a duplicate name); throws on an I/O error or a bad version, which the caller logs.
     */
    static boolean register(InputStream zip, SubPluginManifest pack, PluginManifest owner, Path dataDirectory)
            throws IOException {
        Path file = extract(zip.readAllBytes(), dataDirectory.resolve("packs").resolve(pack.name() + ".zip"));
        PluginManifest manifest = manifest(pack, owner);
        // Asset packs are sorted by PluginIdentifier.fromString(name): the name must be "<group>:<name>".
        String id = new PluginIdentifier(manifest).toString();
        return AssetModule.get().registerPack(id, file, manifest, AssetPack.PackSource.RUNTIME);
    }

    /** Writes {@code bytes} to {@code target} unless it already holds exactly them (a zip Hytale may have open). */
    private static Path extract(byte[] bytes, Path target) throws IOException {
        if (!Files.isRegularFile(target) || !Arrays.equals(Files.readAllBytes(target), bytes)) {
            Files.createDirectories(target.getParent());
            Files.write(target, bytes);
        }
        return target;
    }

    /**
     * The pack's manifest, built here since registerPack never reads one from the zip. No dependency: a missing one
     * would stop the asset load of the whole server (Mod.calculateLoadOrder).
     */
    private static PluginManifest manifest(SubPluginManifest pack, PluginManifest owner) {
        PluginManifest manifest = new PluginManifest();
        manifest.setGroup(owner.getGroup());
        manifest.setName(owner.getName() + "_" + pack.name());
        manifest.setVersion(Semver.fromString(pack.version()));
        manifest.setDescription(pack.description());
        manifest.setServerVersion(owner.getServerVersion());
        return manifest;
    }
}
