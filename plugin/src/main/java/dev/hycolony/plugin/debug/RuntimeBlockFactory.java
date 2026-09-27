package dev.hycolony.plugin.debug;

import com.hypixel.hytale.assetstore.AssetUpdateQuery;
import com.hypixel.hytale.protocol.packets.setup.RequestCommonAssetsRebuild;
import com.hypixel.hytale.server.core.asset.common.CommonAsset;
import com.hypixel.hytale.server.core.asset.common.CommonAssetModule;
import com.hypixel.hytale.server.core.asset.common.CommonAssetRegistry;
import com.hypixel.hytale.server.core.asset.common.asset.FileCommonAsset;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.CustomModelTexture;
import com.hypixel.hytale.server.core.universe.Universe;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.imageio.ImageIO;

/**
 * Creates a new texture and a new BlockType while the server runs, and pushes both to connected clients.
 *
 * <p>Temporary experiment for the Domum Ornamentum port (docs/research/domum-ornamentum.md B.6, option 3): remove
 * once the in-game test has answered whether a runtime BlockType renders without reconnecting.
 *
 * <p>The copied BlockType drops its source's {@code AssetExtraInfo.Data}: kept, it would make every load re-decode the
 * source's contained state assets under our pack and make {@code getItem()} name the source's item
 * (docs/research/plugin-b-api.md § 17).
 */
final class RuntimeBlockFactory {
    /** Vanilla block whose model, hitbox and sounds the generated block copies. */
    static final String SOURCE_BLOCK = "Cloth_Roof_Blue";

    private static final String BASE_TEXTURE = "Blocks/Structures/Roofs/Cloth_Roof_Textures/Tent_Blue.png";
    private static final String STRIPE_TEXTURE = "Blocks/Structures/Roofs/Cloth_Roof_Textures/Tent_Red.png";
    private static final String TEXTURE_DIR = "Blocks/HyColony/DoTest/";
    private static final int STRIPE_PX = 16;

    private final String packKey;
    private Path textureDir;

    /** @param packKey the plugin's asset pack name ({@code Group:Name}, as PluginManager registers it) */
    RuntimeBlockFactory(String packKey) {
        this.packKey = packKey;
    }

    /** Blue tent texture with red vertical stripes, read from the loaded common assets; throws if one is missing. */
    byte[] composeTexture() {
        BufferedImage base = read(BASE_TEXTURE);
        BufferedImage stripes = read(STRIPE_TEXTURE);
        if (base.getWidth() != stripes.getWidth() || base.getHeight() != stripes.getHeight()) {
            throw new IllegalStateException("source textures differ in size");
        }
        BufferedImage out = new BufferedImage(base.getWidth(), base.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int x = 0; x < base.getWidth(); x++) {
            BufferedImage src = (x / STRIPE_PX) % 2 == 0 ? base : stripes;
            for (int y = 0; y < base.getHeight(); y++) {
                out.setRGB(x, y, src.getRGB(x, y));
            }
        }
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            ImageIO.write(out, "png", bytes);
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Writes the PNG to a temp directory (created on first use) and registers it as common asset
     * {@code Blocks/HyColony/DoTest/<name>.png}; {@code CommonAssetModule.addCommonAsset} sends it to every connected
     * player, then every client is asked to rebuild its common assets. Returns the asset name.
     */
    String registerTexture(String name, byte[] png) {
        String assetName = TEXTURE_DIR + name + ".png";
        Path file;
        try {
            // FileCommonAsset rereads the file once its weak reference is gone, so the PNG must stay on disk.
            // Unsynchronized on purpose: a race only creates a second temp dir, and file names are unique.
            if (textureDir == null) {
                textureDir = Files.createTempDirectory("hycolony-dotest");
            }
            file = Files.write(textureDir.resolve(name + ".png"), png);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        CommonAssetModule.get().addCommonAsset(packKey, new FileCommonAsset(file, assetName, png));
        // addCommonAsset sends the file without a rebuild request, so clients never use it (seen in game: untextured
        // block); vanilla's asset monitor follows its sends with RequestCommonAssetsRebuild (CommonAssetModule l.132).
        Universe.get().broadcastPacketNoCache(new RequestCommonAssetsRebuild());
        return assetName;
    }

    /**
     * Loads BlockType {@code id}: a copy of {@link #SOURCE_BLOCK} with {@code texture}, without its states and
     * connected-block rules (so it never turns into a vanilla corner). The DEFAULT query rebuilds every client cache;
     * {@code HytaleAssetStore.handleRemoveOrUpdate} broadcasts the {@code UpdateBlockTypes} packet.
     */
    void registerBlockType(String id, String texture) {
        BlockType source = BlockType.getAssetMap().getAsset(SOURCE_BLOCK);
        if (source == null) {
            throw new IllegalStateException("missing source block " + SOURCE_BLOCK);
        }
        BlockType generated = new GeneratedBlockType(source, id, texture);
        BlockType.getAssetStore().loadAssets(packKey, List.of(generated), AssetUpdateQuery.DEFAULT);
        if (BlockType.getAssetMap().getIndex(id) == Integer.MIN_VALUE) {
            throw new IllegalStateException("BlockType " + id + " was not loaded");
        }
    }

    private static BufferedImage read(String name) {
        CommonAsset asset = CommonAssetRegistry.getByName(name);
        if (asset == null) {
            throw new IllegalStateException("missing common asset " + name);
        }
        try {
            BufferedImage image =
                    ImageIO.read(new ByteArrayInputStream(asset.getBlob().join()));
            if (image == null) {
                throw new IllegalStateException("unreadable image " + name);
            }
            return image;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** BlockType's fields are protected with no setters: a subclass is the only way to change them after decode. */
    private static final class GeneratedBlockType extends BlockType {
        GeneratedBlockType(BlockType source, String id, String texture) {
            super(source);
            this.data = null;
            this.id = id;
            this.customModelTexture = new CustomModelTexture[] {new CustomModelTexture(texture, 1)};
            this.state = null;
            this.connectedBlockRuleSet = null;
        }
    }
}
