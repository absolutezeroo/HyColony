package dev.hycolony.plugin.ornament.runtime;

import com.hypixel.hytale.function.supplier.CachedSupplier;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.common.CommonAssetModule;
import com.hypixel.hytale.server.core.asset.common.CommonAssetRegistry;
import com.hypixel.hytale.server.core.asset.common.asset.FileCommonAsset;
import com.hypixel.hytale.server.core.universe.Universe;
import dev.hycolony.plugin.ornament.api.VariantKey;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.ref.Reference;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Gives each variant its own inventory icon, once per {@link VariantKey}: renders the PNG, keeps it on disk and
 * registers it as common asset {@code Icons/ItemsGenerated/<blockTypeKey>.png}. Registering sends only this file to
 * connected players ({@code sendAsset(asset, false)}: no {@code RequestCommonAssetsRebuild}) and adds it to the
 * assets a joining player downloads.
 *
 * <p>{@code notify} uses {@code CommonAssetModule.addCommonAsset}, as vanilla's pack loader does, which also shows
 * an "asset created" notification to connected players. Without it, the asset is added to the registry and sent by
 * hand, and the private list of assets a joining player downloads is refreshed by reflection (experiment, pinned
 * 0.6.8). Must run off world threads (it reads textures and writes a file).
 */
public final class VariantIconPublisher {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();

    private final String packKey;
    private final Path dir;
    private final VariantIconRenderer renderer = new VariantIconRenderer();
    // Written from creation threads and the boot thread.
    private final Map<VariantKey, String> published = new ConcurrentHashMap<>();

    /**
     * @param packKey the plugin's asset pack name ({@code Group:Name})
     * @param dir where the PNGs are kept: {@code FileCommonAsset} rereads the file once its bytes are collected
     */
    public VariantIconPublisher(String packKey, Path dir) {
        this.packKey = packKey;
        this.dir = dir;
    }

    /**
     * The common asset name of {@code key}'s icon, rendered and registered on the first call only ({@code notify}
     * is ignored afterwards). Throws when the textures cannot be read or the file cannot be written.
     */
    public String publish(VariantKey key, boolean notify) {
        return published.computeIfAbsent(key, k -> renderAndRegister(k, notify));
    }

    private String renderAndRegister(VariantKey key, boolean notify) {
        long start = System.nanoTime();
        byte[] png = renderer.render(key);
        String name = "Icons/ItemsGenerated/" + key.blockTypeKey() + ".png";
        Path file;
        try {
            Files.createDirectories(dir);
            file = Files.write(dir.resolve(key.blockTypeKey() + ".png"), png);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        FileCommonAsset asset = new FileCommonAsset(file, name, png);
        // The asset holds its bytes by weak reference: keeping the blob reachable makes sendAsset write the parts
        // now, on this thread, before the UpdateItems that names the icon.
        CompletableFuture<byte[]> blob = asset.getBlob();
        try {
            if (notify) {
                CommonAssetModule.get().addCommonAsset(packKey, asset, false);
            } else {
                registerSilently(asset);
            }
        } finally {
            Reference.reachabilityFence(blob);
        }
        LOG.at(Level.INFO).log(
                "hyornament: icon %s (%d bytes, notify=%b) registered in %d us",
                name, png.length, notify, (System.nanoTime() - start) / 1_000);
        return name;
    }

    /**
     * What {@code addCommonAsset} does for a new asset, minus its universe-wide notification: registry, joining
     * players' list, and this one file to connected players without {@code RequestCommonAssetsRebuild}.
     */
    private void registerSilently(FileCommonAsset asset) {
        CommonAssetRegistry.AddCommonAssetResult result = CommonAssetRegistry.addCommonAsset(packKey, asset);
        if (!result.getActiveAsset().equals(result.getNewPackAsset())) {
            LOG.at(Level.WARNING).log("hyornament: icon %s hidden by another pack's asset", asset.getName());
            return;
        }
        refreshRequiredAssets();
        if (Universe.get().getPlayerCount() > 0) {
            CommonAssetModule.get().sendAsset(asset, false);
        }
    }

    /**
     * Invalidates {@code CommonAssetModule.assets}, the cached list sent to joining players, which only
     * {@code addCommonAsset} and pack changes invalidate (CommonAssetModule.java:78-85, 214). A failure is logged:
     * players joining before the next restart then lack the icon.
     */
    private static void refreshRequiredAssets() {
        try {
            Field field = CommonAssetModule.class.getDeclaredField("assets");
            field.setAccessible(true);
            ((CachedSupplier<?>) field.get(CommonAssetModule.get())).invalidate();
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOG.at(Level.WARNING).withCause(e).log("hyornament: joining players will lack new icons until restart");
        }
    }
}
