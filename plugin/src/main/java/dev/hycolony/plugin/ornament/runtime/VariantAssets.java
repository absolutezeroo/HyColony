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
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * The PNGs a variant needs beyond vanilla ones: its inventory icon and, for a composed shape, its model texture.
 * Each is generated once per asset name (so once per {@link VariantKey}), kept on disk and registered as a common
 * asset; registering sends only this file to connected players ({@code sendAsset(asset, false)}: no
 * {@code RequestCommonAssetsRebuild}) and adds it to the assets a joining player downloads.
 *
 * <p>{@code announce} uses {@code CommonAssetModule.addCommonAsset}, as vanilla's pack loader does, which also shows
 * an "asset created" notification to connected players. Without it, the asset is added to the registry and sent by
 * hand, and the private list of assets a joining player downloads is refreshed by reflection (pinned 0.6.8). Must
 * run off world threads (it reads textures and writes files).
 */
public final class VariantAssets {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();

    private final String packKey;
    private final Path dir;
    private final VariantIconRenderer icons = new VariantIconRenderer();
    private final VariantTextureComposer textures = new VariantTextureComposer();
    // Written from creation threads and the boot thread.
    private final Map<String, String> published = new ConcurrentHashMap<>();

    /**
     * @param packKey the plugin's asset pack name ({@code Group:Name})
     * @param dir where the PNGs are kept: {@code FileCommonAsset} rereads a file once its bytes are collected
     */
    public VariantAssets(String packKey, Path dir) {
        this.packKey = packKey;
        this.dir = dir;
    }

    /** The common asset name of {@code key}'s generated icon; throws when it cannot be rendered or written. */
    public String icon(VariantKey key, boolean announce) {
        return publish("Icons/ItemsGenerated/" + key.blockTypeKey() + ".png", () -> icons.render(key), announce);
    }

    /**
     * The common asset name of {@code key}'s composed model texture; throws when the shape is not composed or the
     * texture cannot be built or written.
     */
    public String modelTexture(VariantKey key, boolean announce) {
        return publish(
                "Blocks/HyColony/Ornament/Generated/" + key.blockTypeKey() + ".png",
                () -> textures.compose(key),
                announce);
    }

    /** {@code name}, generated and registered on the first call only ({@code announce} is ignored afterwards). */
    private String publish(String name, Supplier<byte[]> png, boolean announce) {
        return published.computeIfAbsent(name, n -> register(n, png.get(), announce));
    }

    private String register(String name, byte[] png, boolean announce) {
        long start = System.nanoTime();
        Path file = dir.resolve(name);
        try {
            Files.createDirectories(file.getParent());
            Files.write(file, png);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        FileCommonAsset asset = new FileCommonAsset(file, name, png);
        // The asset holds its bytes by weak reference: keeping the blob reachable makes sendAsset write the parts
        // now, on this thread, before the packet that names the asset.
        CompletableFuture<byte[]> blob = asset.getBlob();
        try {
            if (announce) {
                CommonAssetModule.get().addCommonAsset(packKey, asset, false);
            } else {
                registerSilently(asset);
            }
        } finally {
            Reference.reachabilityFence(blob);
        }
        LOG.at(Level.INFO).log(
                "hyornament: asset %s (%d bytes, announce=%b) registered in %d us",
                name, png.length, announce, (System.nanoTime() - start) / 1_000);
        return name;
    }

    /**
     * What {@code addCommonAsset} does for a new asset, minus its universe-wide notification: registry, joining
     * players' list, and this one file to connected players without {@code RequestCommonAssetsRebuild}.
     */
    private void registerSilently(FileCommonAsset asset) {
        CommonAssetRegistry.AddCommonAssetResult result = CommonAssetRegistry.addCommonAsset(packKey, asset);
        if (!result.getActiveAsset().equals(result.getNewPackAsset())) {
            LOG.at(Level.WARNING).log("hyornament: %s hidden by another pack's asset", asset.getName());
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
     * players joining before the next restart then lack the asset.
     */
    private static void refreshRequiredAssets() {
        try {
            Field field = CommonAssetModule.class.getDeclaredField("assets");
            field.setAccessible(true);
            ((CachedSupplier<?>) field.get(CommonAssetModule.get())).invalidate();
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOG.at(Level.WARNING).withCause(e).log("hyornament: joining players will lack new assets until restart");
        }
    }
}
