package dev.hydomum.plugin.runtime;

import com.hypixel.hytale.server.core.asset.common.CommonAsset;
import com.hypixel.hytale.server.core.asset.common.CommonAssetRegistry;
import dev.hydomum.core.palette.PaletteLayout;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.function.Function;
import javax.imageio.ImageIO;

/** Image helpers of the generated textures and icons: read a loaded texture, fit it to a face, pair two, write PNG. */
final class Textures {
    /** Texture size, in pixels, of one block face. */
    static final int FACE = 32;

    private Textures() {}

    /** The bytes of the loaded common texture {@code name}; throws {@link IllegalStateException} when missing. */
    static byte[] bytes(String name) {
        CommonAsset asset = CommonAssetRegistry.getByName(name);
        if (asset == null) {
            throw new IllegalStateException("missing texture " + name);
        }
        return asset.getBlob().join();
    }

    /** The texture {@code name} decoded from png; throws {@link IllegalStateException} when unreadable. */
    static BufferedImage decode(String name, byte[] png) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
            if (image == null) {
                throw new IllegalStateException("unreadable texture " + name);
            }
            return image;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** {@code texture} scaled to one {@link #FACE} x {@link #FACE} face, nearest neighbour. */
    static BufferedImage face(BufferedImage texture) {
        BufferedImage face = new BufferedImage(FACE, FACE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = face.createGraphics();
        g.drawImage(texture, 0, 0, FACE, FACE, null);
        g.dispose();
        return face;
    }

    /** The two-material layout: first's face on the left, second's on the right (2 x {@link #FACE} by FACE). */
    static BufferedImage pair(BufferedImage first, BufferedImage second) {
        BufferedImage pair = new BufferedImage(2 * FACE, FACE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = pair.createGraphics();
        g.drawImage(face(first), 0, 0, null);
        g.drawImage(face(second), FACE, 0, null);
        g.dispose();
        return pair;
    }

    /** The palette: each material's face (from faceOf) in its layout tile, transparent elsewhere. */
    static BufferedImage palette(PaletteLayout layout, Function<String, BufferedImage> faceOf) {
        BufferedImage palette = new BufferedImage(layout.widthPx(), layout.heightPx(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = palette.createGraphics();
        for (String material : layout.materials()) {
            PaletteLayout.Tile tile = layout.tile(material).orElseThrow();
            g.drawImage(face(faceOf.apply(material)), tile.x(), tile.y(), null);
        }
        g.dispose();
        return palette;
    }

    /** {@code image} as PNG bytes. */
    static byte[] png(BufferedImage image) {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", bytes);
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
