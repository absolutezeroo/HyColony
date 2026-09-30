package dev.hydomum.plugin.runtime;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.CustomModelTexture;
import dev.hydomum.api.ShapeCatalog;
import dev.hydomum.core.palette.PaletteLayout;
import dev.hydomum.core.palette.PaletteModel;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import org.jspecify.annotations.Nullable;

/**
 * The palette every two-material variant reads (docs/research/client-block-atlas.md): one texture with each
 * material's face in its {@link PaletteLayout} tile, drawn at boot and read by a hidden block so that clients put it
 * in their atlas on joining. A variant's models are its template's, remapped onto its materials' tiles: a new pair
 * then needs no new block texture, hence no client atlas rebuild and no flicker (checked in game, 2026-09-30).
 *
 * <p>Deviation from MC: DO retextures each component's placeholder sprite of one shared model on the client
 * ({@code RetexturedBakedModelBuilder.build}, {@code MateriallyTexturedBakedModel}); a Hytale model reads a single
 * texture, so both materials come from one palette through a model remapped per pair.
 */
public final class VariantPalette {
    /** The texture two-material variants read. */
    public static final String TEXTURE = "Blocks/HyDomum/Palette.png";

    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();
    private static final String HOLDER = "HyDomum_Palette";
    private static final String MODELS = "Blocks/HyDomum/Variants/";
    /** Side, in pixels, past which the palette is logged as large: arbitrary, the client's atlas page is unknown. */
    private static final int WARN_SIDE_PX = 2048;

    private final VariantAssets assets;
    private final BlockTypeSynchronizer synchronizer;
    private volatile @Nullable PaletteLayout layout;

    public VariantPalette(VariantAssets assets, BlockTypeSynchronizer synchronizer) {
        this.assets = assets;
        this.synchronizer = synchronizer;
    }

    /**
     * Draws and registers the palette of every material, then the hidden block that reads it; call at boot, off world
     * threads, before players join and before saved variants are restored. Throws when a texture is unreadable, AWT
     * is missing or no template is loaded: two-material variants then fail one by one.
     */
    public void start(ShapeCatalog shapes, MaterialCatalog materials) {
        PaletteLayout loaded = PaletteLayout.of(materials.materials());
        List<String> textures = loaded.materials().stream()
                .map(m -> materials.texture(m).orElseThrow())
                .toList();
        byte[][] inputs = textures.stream().map(assets::source).toArray(byte[][]::new);
        // The layout is in the seed: the same textures on another grid draw another palette.
        long seed = ((long) PaletteLayout.TILE_PX << 32) | loaded.widthPx();
        String palette = assets.generated(
                TEXTURE,
                seed,
                () -> Textures.png(Textures.palette(
                        loaded, m -> assets.image(materials.texture(m).orElseThrow()))),
                inputs);
        BlockType template = shapes.all().stream()
                .map(shape -> BlockType.getAssetMap().getAsset(shape.templateKey()))
                .filter(Objects::nonNull)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("no ornament template loaded"));
        synchronizer.register(List.of(new Holder(template, palette)), false);
        layout = loaded;
        if (Math.max(loaded.widthPx(), loaded.heightPx()) > WARN_SIDE_PX) {
            LOG.at(Level.WARNING).log(
                    "hydomum: palette of %dx%d px may not fit a client atlas page",
                    loaded.widthPx(), loaded.heightPx());
        }
        LOG.at(Level.INFO).log(
                "hydomum: palette of %d material(s), %dx%d px",
                loaded.materials().size(), loaded.widthPx(), loaded.heightPx());
    }

    /**
     * The common asset name of templateModel remapped to read first's and second's tiles, registered once (it then
     * waits to be sent, {@link VariantAssets#takeUnsentModels}). Throws before {@link #start}, for a material outside
     * the palette, or for a model that cannot be remapped.
     */
    public String model(String templateModel, String first, String second) {
        PaletteLayout loaded = layout;
        if (loaded == null) {
            throw new IllegalStateException("palette not loaded");
        }
        String stem = templateModel.substring(templateModel.lastIndexOf('/') + 1, templateModel.lastIndexOf('.'));
        String remapped = PaletteModel.remap(
                new String(Textures.bytes(templateModel), StandardCharsets.UTF_8),
                tile(loaded, first),
                tile(loaded, second));
        return assets.model(
                MODELS + stem + "__" + first + "__" + second + ".blockymodel",
                remapped.getBytes(StandardCharsets.UTF_8));
    }

    private static PaletteLayout.Tile tile(PaletteLayout layout, String material) {
        return layout.tile(material).orElseThrow(() -> new IllegalArgumentException("not in the palette: " + material));
    }

    /**
     * The hidden block that makes joining clients put the palette in their atlas: never placed, no item, no state,
     * no connection; {@code data} dropped as in {@link VariantBlockType} (getItem and toPacket read it as absent).
     */
    private static final class Holder extends BlockType {
        Holder(BlockType template, String palette) {
            super(template);
            this.data = null;
            this.id = HOLDER;
            this.customModelTexture = new CustomModelTexture[] {new CustomModelTexture(palette, 1)};
            this.state = null;
            this.connectedBlockRuleSet = null;
        }
    }
}
