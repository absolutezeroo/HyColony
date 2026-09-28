package dev.hydomum.plugin.runtime;

import com.hypixel.hytale.function.supplier.CachedSupplier;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.common.CommonAssetModule;
import com.hypixel.hytale.server.core.asset.common.CommonAssetRegistry;
import com.hypixel.hytale.server.core.asset.common.asset.FileCommonAsset;
import com.hypixel.hytale.server.core.universe.Universe;
import dev.hydomum.api.VariantKey;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.ref.Reference;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.zip.CRC32;

/**
 * The PNGs a variant needs beyond vanilla ones: the texture of a two-material pair (unless the pack ships it) and
 * its inventory icon. Each is generated once per asset name and inputs, kept on disk (reused at the next boot) and
 * registered as a common asset without notification: this one file goes to connected players
 * ({@code sendAsset(asset, false)}, no {@code RequestCommonAssetsRebuild}) and joins the assets a joining player
 * downloads, whose private cached list is refreshed by reflection (pinned 0.6.8). Must run off world threads (it
 * reads textures and writes files).
 *
 * <p>ponytail: PNGs of older fingerprints stay on disk, unread; add a sweep of the folder if it ever grows large.
 */
public final class VariantAssets {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();
    /** Where pair textures live; tools/domum/pairs.py writes the pack's own under the same names. */
    private static final String PAIRS = "Blocks/HyColony/DO/Pairs/";
    /** Bump when Textures or IconMap draw differently: PNGs kept on disk by older code are then redrawn. */
    private static final long DRAWING_VERSION = 1;

    private final String packKey;
    private final Path dir;
    // Written from creation threads and the boot thread.
    private final Map<String, String> published = new ConcurrentHashMap<>();
    // Loaded textures never change while the server runs.
    private final Map<String, byte[]> sources = new ConcurrentHashMap<>();
    private final Map<String, BufferedImage> images = new ConcurrentHashMap<>();

    /**
     * A texture's asset name and whether this call registered it, drawn or reused from disk (clients then rebuild
     * their block atlas).
     */
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
     * when the pack or an earlier call has it, else registered from disk or generated. Throws when a texture is
     * unreadable.
     */
    public Published pairTexture(String first, String second, String tex1, String tex2) {
        String name = PAIRS + first + "__" + second + ".png";
        if (CommonAssetRegistry.getByName(name) != null) {
            return new Published(name, false);
        }
        return publish(name, fingerprint(0, source(tex1), source(tex2)), () -> Textures.pair(image(tex1), image(tex2)));
    }

    /** The common asset name of key's icon, painted through map from its layout texture; throws on failure. */
    public String icon(VariantKey key, IconMap map, String layoutTexture) {
        return publish(
                        "Icons/ItemsGenerated/" + key.blockTypeKey() + ".png",
                        fingerprint(map.crc(), source(layoutTexture)),
                        () -> map.sample(image(layoutTexture)))
                .name();
    }

    /** {@code name}, registered on the first call only; created tells whether this call registered it. */
    private Published publish(String name, long fingerprint, Supplier<BufferedImage> image) {
        boolean[] created = {false};
        String registered = published.computeIfAbsent(name, n -> {
            created[0] = true;
            return register(n, stored(n, fingerprint, image));
        });
        return new Published(registered, created[0]);
    }

    /**
     * The file of name's PNG for these inputs: the one an earlier boot wrote when there (a restart redraws
     * nothing), else drawn and written. The fingerprint is in the file name, so a changed texture, icon map or
     * drawing code draws a new file.
     */
    private Path stored(String name, long fingerprint, Supplier<BufferedImage> image) {
        String stem = name.substring(0, name.length() - ".png".length());
        Path file = dir.resolve(stem + "." + Long.toHexString(fingerprint) + ".png");
        try {
            if (Files.isRegularFile(file)) {
                return file;
            }
            byte[] png = Textures.png(image.get());
            Files.createDirectories(file.getParent());
            // A crash mid-write must not leave a truncated PNG that the next boot would reuse.
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.write(tmp, png);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            return file;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** file's bytes; throws {@link UncheckedIOException} when unreadable. */
    private static byte[] readAll(Path file) {
        try {
            return Files.readAllBytes(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** The bytes of the loaded texture {@code name}, read once. */
    private byte[] source(String name) {
        return sources.computeIfAbsent(name, Textures::bytes);
    }

    /** The loaded texture {@code name}, decoded once. */
    private BufferedImage image(String name) {
        return images.computeIfAbsent(name, n -> Textures.decode(n, source(n)));
    }

    /** CRC-32 of the drawing code version, seed and parts: what a generated PNG is drawn from. */
    private static long fingerprint(long seed, byte[]... parts) {
        CRC32 crc = new CRC32();
        crc.update(ByteBuffer.allocate(2 * Long.BYTES)
                .putLong(DRAWING_VERSION)
                .putLong(seed)
                .flip());
        for (byte[] part : parts) {
            crc.update(part);
        }
        return crc.getValue();
    }

    private String register(String name, Path file) {
        long start = System.nanoTime();
        byte[] png = readAll(file);
        FileCommonAsset asset = new FileCommonAsset(file, name, png);
        // The asset holds its bytes by weak reference: keeping the blob reachable makes sendAsset write the parts
        // now, on this thread, before the packet that names the asset.
        CompletableFuture<byte[]> blob = asset.getBlob();
        try {
            registerSilently(asset);
        } finally {
            Reference.reachabilityFence(blob);
        }
        LOG.at(Level.FINE).log(
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
