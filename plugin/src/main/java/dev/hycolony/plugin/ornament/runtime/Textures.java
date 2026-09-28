package dev.hycolony.plugin.ornament.runtime;

import com.hypixel.hytale.server.core.asset.common.CommonAsset;
import com.hypixel.hytale.server.core.asset.common.CommonAssetRegistry;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import javax.imageio.ImageIO;

/** Image helpers shared by the generated icons and textures: read a loaded texture, fit it to a face, write PNG. */
final class Textures {
    /** Texture size, in pixels, of one block face. */
    static final int FACE = 32;

    private Textures() {}

    /** The loaded common texture {@code name}; throws {@link IllegalStateException} when missing or unreadable. */
    static BufferedImage read(String name) {
        CommonAsset asset = CommonAssetRegistry.getByName(name);
        if (asset == null) {
            throw new IllegalStateException("missing texture " + name);
        }
        try {
            BufferedImage image =
                    ImageIO.read(new ByteArrayInputStream(asset.getBlob().join()));
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
