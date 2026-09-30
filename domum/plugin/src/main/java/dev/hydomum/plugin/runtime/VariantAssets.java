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
import org.jspecify.annotations.Nullable;

/**
 * The files a variant needs beyond vanilla ones: the palette texture (VariantPalette), each two-material model
 * remapped onto it, and each variant's inventory icon. Each is generated once per asset name and inputs, kept on disk
 * (reused at the next boot) and registered as a common asset without notification or send: it joins the assets a
 * joining player downloads, whose private cached list is refreshed by reflection. A model or icon then waits until a
 * batch that names it takes it ({@link #takeUnsentModels}, {@link #takeUnsentIcons}) to send it to connected players.
 * Must run off world threads (it reads textures and writes files).
 *
 * <p>ponytail: files of older fingerprints stay on disk, unread; add a sweep of the folder if it ever grows large.
 */
public final class VariantAssets {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();
    /**
     * Bump when Textures, IconMap or PaletteLayout draw differently: files kept on disk by older code are redrawn.
     */
    private static final long DRAWING_VERSION = 1;

    private final String packKey;
    private final Path dir;
    // Written from creation threads and the boot thread.
    private final Map<String, String> published = new ConcurrentHashMap<>();
    // Loaded textures never change while the server runs.
    private final Map<String, byte[]> sources = new ConcurrentHashMap<>();
    private final Map<String, BufferedImage> images = new ConcurrentHashMap<>();
    // Registered, not yet sent to connected players, by name: a key that fails keeps its files until it is retried,
    // and of two batches naming one file, only the first to take it sends it.
    // ponytail: a key never retried keeps its entry until restart (one per name); sweep them if that ever matters.
    private final Map<String, CommonAsset> unsentModels = new ConcurrentHashMap<>();
    private final Map<String, CommonAsset> unsentIcons = new ConcurrentHashMap<>();

    /**
     * @param packKey the plugin's asset pack name ({@code Group:Name})
     * @param dir where the PNGs are kept: {@code FileCommonAsset} rereads a file once its bytes are collected
     */
    public VariantAssets(String packKey, Path dir) {
        this.packKey = packKey;
        this.dir = dir;
    }

    /**
     * The common asset name of key's icon, painted through map from its layout: the material's texture, or for two
     * materials the 64 x 32 pair drawn in memory (never registered). Throws on failure.
     */
    public String icon(VariantKey key, IconMap map, List<String> textures) {
        byte[][] inputs = textures.stream().map(this::source).toArray(byte[][]::new);
        return registerOnce(
                "Icons/ItemsGenerated/" + key.blockTypeKey() + ".png",
                fingerprint(map.crc(), inputs),
                () -> Textures.png(map.sample(
                        textures.size() == 1
                                ? image(textures.getFirst())
                                : Textures.pair(image(textures.get(0)), image(textures.get(1))))),
                unsentIcons);
    }

    /** The model JSON registered under the common asset name, once; it waits to be sent ({@link #takeUnsentModels}). */
    public String model(String name, byte[] json) {
        return registerOnce(name, fingerprint(0, json), () -> json, unsentModels);
    }

    /**
     * The file drawn by content, registered once under the common asset name for seed and inputs, never sent at
     * runtime (the palette, drawn at boot). Throws when content fails.
     */
    String generated(String name, long seed, Supplier<byte[]> content, byte[]... inputs) {
        return registerOnce(name, fingerprint(seed, inputs), content, null);
    }

    /**
     * Takes the registered models among names not sent yet; a name taken once is never returned again. Call it
     * right before sending them, ahead of the blocks that name them.
     */
    public List<CommonAsset> takeUnsentModels(Collection<String> names) {
        return take(unsentModels, names);
    }

    /**
     * Takes the registered icons among names not sent yet; a name taken once is never returned again. Call it once
     * the stores hold the batch, so that a batch failing before keeps them.
     */
    public List<CommonAsset> takeUnsentIcons(Collection<String> names) {
        return take(unsentIcons, names);
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

    /** {@code name}, registered on the first call only, whose asset then waits in {@code unsent} when given. */
    private String registerOnce(
            String name, long fingerprint, Supplier<byte[]> bytes, @Nullable Map<String, CommonAsset> unsent) {
        return published.computeIfAbsent(name, n -> {
            Optional<CommonAsset> asset = register(n, stored(n, fingerprint, bytes));
            if (unsent != null) {
                asset.ifPresent(a -> unsent.put(n, a));
            }
            return n;
        });
    }

    /**
     * The file of name's content for these inputs: the one an earlier boot wrote when there (a restart redraws
     * nothing), else drawn and written. The fingerprint is in the file name, so a changed texture, icon map or
     * drawing code draws a new file.
     */
    private Path stored(String name, long fingerprint, Supplier<byte[]> bytes) {
        int dot = name.lastIndexOf('.');
        String stem = name.substring(0, dot);
        Path file = dir.resolve(stem + "." + Long.toHexString(fingerprint) + name.substring(dot));
        try {
            if (Files.isRegularFile(file)) {
                return file;
            }
            byte[] content = bytes.get();
            Files.createDirectories(file.getParent());
            // A crash mid-write must not leave a truncated file that the next boot would reuse.
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.write(tmp, content);
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
    byte[] source(String name) {
        return sources.computeIfAbsent(name, Textures::bytes);
    }

    /** The loaded texture {@code name}, decoded once. */
    BufferedImage image(String name) {
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
