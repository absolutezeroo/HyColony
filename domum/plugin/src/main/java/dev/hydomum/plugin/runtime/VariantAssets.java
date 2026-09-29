package dev.hydomum.plugin.runtime;

import com.hypixel.hytale.function.supplier.CachedSupplier;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.common.CommonAsset;
import com.hypixel.hytale.server.core.asset.common.CommonAssetModule;
import com.hypixel.hytale.server.core.asset.common.CommonAssetRegistry;
import com.hypixel.hytale.server.core.asset.common.asset.FileCommonAsset;
import dev.hydomum.api.VariantKey;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.zip.CRC32;

/**
 * The PNGs a variant needs beyond vanilla ones: the texture of a two-material pair (unless the pack ships it) and
 * its inventory icon. Each is generated once per asset name and inputs, kept on disk (reused at the next boot) and
 * registered as a common asset without notification or send: it joins the assets a joining player downloads, whose
 * private cached list is refreshed by reflection, and waits until a batch that names it is registered in the stores
 * and takes it ({@link #takeUnsent}) for {@link BlockTypeSynchronizer#publish}. Must run off world threads (it reads
 * textures and writes files).
 *
 * <p>ponytail: PNGs of older fingerprints stay on disk, unread; add a sweep of the folder if it ever grows large.
 */
public final class VariantAssets {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();
    /** Where pair textures live; tools/domum/pairs.py writes the pack's own under the same names. */
    private static final String PAIRS = "Blocks/HyDomum/Pairs/";
    /** Bump when Textures or IconMap draw differently: PNGs kept on disk by older code are then redrawn. */
    private static final long DRAWING_VERSION = 1;

    private final String packKey;
    private final Path dir;
    // Written from creation threads and the boot thread.
    private final Map<String, String> published = new ConcurrentHashMap<>();
    // Loaded textures never change while the server runs.
    private final Map<String, byte[]> sources = new ConcurrentHashMap<>();
    private final Map<String, BufferedImage> images = new ConcurrentHashMap<>();
    // Registered, not yet sent to connected players, by name: a key that fails keeps its PNGs until it is retried,
    // and of two batches naming one PNG, only the first to take it sends it and its rebuild flag.
    // ponytail: a key never retried keeps its entry until restart (one per name); sweep them if that ever matters.
    private final Map<String, CommonAsset> unsentTextures = new ConcurrentHashMap<>();
    private final Map<String, CommonAsset> unsentIcons = new ConcurrentHashMap<>();

    /** Registered pair textures and icons that connected players do not have yet. */
    public record Unsent(List<CommonAsset> textures, List<CommonAsset> icons) {}

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
    public String pairTexture(String first, String second, String tex1, String tex2) {
        String name = PAIRS + first + "__" + second + ".png";
        if (CommonAssetRegistry.getByName(name) != null) {
            return name;
        }
        return registerOnce(
                name,
                fingerprint(0, source(tex1), source(tex2)),
                () -> Textures.pair(image(tex1), image(tex2)),
                unsentTextures);
    }

    /** The common asset name of key's icon, painted through map from its layout texture; throws on failure. */
    public String icon(VariantKey key, IconMap map, String layoutTexture) {
        return registerOnce(
                "Icons/ItemsGenerated/" + key.blockTypeKey() + ".png",
                fingerprint(map.crc(), source(layoutTexture)),
                () -> map.sample(image(layoutTexture)),
                unsentIcons);
    }

    /**
     * Takes the registered PNGs among {@code names} that were not sent yet; a name taken once is never returned
     * again. Call it once the stores hold the batch that uses these names, so that a batch failing before keeps them.
     */
    public Unsent takeUnsent(Collection<String> names) {
        return new Unsent(take(unsentTextures, names), take(unsentIcons, names));
    }

    /** The assets of {@code names} in unsent, removed from it. */
    private static List<CommonAsset> take(Map<String, CommonAsset> unsent, Collection<String> names) {
        List<CommonAsset> taken = new ArrayList<>();
        for (String name : names) {
            CommonAsset asset = unsent.remove(name);
            if (asset != null) {
                taken.add(asset);
            }
        }
        return taken;
    }

    /** {@code name}, registered on the first call only, whose asset then waits in {@code unsent}. */
    private String registerOnce(
            String name, long fingerprint, Supplier<BufferedImage> image, Map<String, CommonAsset> unsent) {
        return published.computeIfAbsent(name, n -> {
            register(n, stored(n, fingerprint, image)).ifPresent(a -> unsent.put(n, a));
            return n;
        });
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

    /**
     * What {@code addCommonAsset} does for a new asset, minus its universe-wide notification and its send: registry
     * and joining players' list. Returns the asset, or empty when another pack's asset of that name hides it.
     */
    private Optional<CommonAsset> register(String name, Path file) {
        long start = System.nanoTime();
        byte[] png = readAll(file);
        // The asset holds its bytes by weak reference and rereads the file once they are collected.
        FileCommonAsset asset = new FileCommonAsset(file, name, png);
        CommonAssetRegistry.AddCommonAssetResult result = CommonAssetRegistry.addCommonAsset(packKey, asset);
        if (!result.getActiveAsset().equals(result.getNewPackAsset())) {
            LOG.at(Level.WARNING).log("hydomum: %s hidden by another pack's asset", name);
            return Optional.empty();
        }
        refreshRequiredAssets();
        LOG.at(Level.FINE).log(
                "hydomum: asset %s (%d bytes) registered in %d us",
                name, png.length, (System.nanoTime() - start) / 1_000);
        return Optional.of(asset);
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
            LOG.at(Level.WARNING).withCause(e).log("hydomum: joining players will lack new assets until restart");
        }
    }
}
