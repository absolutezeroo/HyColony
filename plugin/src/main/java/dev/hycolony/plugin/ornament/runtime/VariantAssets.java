package dev.hycolony.plugin.ornament.runtime;

import com.hypixel.hytale.function.supplier.CachedSupplier;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.common.CommonAssetModule;
import com.hypixel.hytale.server.core.asset.common.CommonAssetRegistry;
import com.hypixel.hytale.server.core.asset.common.asset.FileCommonAsset;
import com.hypixel.hytale.server.core.universe.Universe;
import dev.hycolony.core.ornament.VariantKey;
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
 * The PNGs a variant needs beyond vanilla ones: the texture of a two-material pair (unless the pack ships it) and
 * its inventory icon. Each is generated once per asset name, kept on disk and registered as a common asset without
 * notification: this one file goes to connected players ({@code sendAsset(asset, false)}, no
 * {@code RequestCommonAssetsRebuild}) and joins the assets a joining player downloads, whose private cached list is
 * refreshed by reflection (pinned 0.6.8). Must run off world threads (it reads textures and writes files).
 */
public final class VariantAssets {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();
    /** Where pair textures live; tools/domum/pairs.py writes the pack's own under the same names. */
    private static final String PAIRS = "Blocks/HyColony/DO/Pairs/";

    private final String packKey;
    private final Path dir;
    // Written from creation threads and the boot thread.
    private final Map<String, String> published = new ConcurrentHashMap<>();

    /** A texture's asset name and whether this call generated it (clients then rebuild their block atlas). */
    public record Published(String name, boolean created) {}

    /**
     * @param packKey the plugin's asset pack name ({@code Group:Name})
     * @param dir where the PNGs are kept: {@code FileCommonAsset} rereads a file once its bytes are collected
     */
    public VariantAssets(String packKey, Path dir) {
        this.packKey = packKey;
        this.dir = dir;
    }

    /**
     * The pair texture of materials first and second (64 x 32: tex1's face left, tex2's right): the loaded asset
     * when the pack or an earlier call has it, else generated and registered. Throws when a texture is unreadable.
     */
    public Published pairTexture(String first, String second, String tex1, String tex2) {
        String name = PAIRS + first + "__" + second + ".png";
        if (CommonAssetRegistry.getByName(name) != null) {
            return new Published(name, false);
        }
        boolean[] created = {false};
        String published = publish(name, () -> {
            created[0] = true;
            return Textures.png(Textures.pair(Textures.read(tex1), Textures.read(tex2)));
        });
        return new Published(published, created[0]);
    }

    /** The common asset name of key's icon, painted through map from its layout texture; throws on failure. */
    public String icon(VariantKey key, IconMap map, String layoutTexture) {
        return publish(
                "Icons/ItemsGenerated/" + key.blockTypeKey() + ".png",
                () -> Textures.png(map.sample(Textures.read(layoutTexture))));
    }

    /** {@code name}, generated and registered on the first call only. */
    private String publish(String name, Supplier<byte[]> png) {
        return published.computeIfAbsent(name, n -> register(n, png.get()));
    }

    private String register(String name, byte[] png) {
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
            registerSilently(asset);
        } finally {
            Reference.reachabilityFence(blob);
        }
        LOG.at(Level.INFO).log(
                "hyornament: asset %s (%d bytes) registered in %d us",
                name, png.length, (System.nanoTime() - start) / 1_000);
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
