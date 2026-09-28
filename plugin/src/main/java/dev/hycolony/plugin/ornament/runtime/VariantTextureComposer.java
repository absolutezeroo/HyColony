package dev.hycolony.plugin.ornament.runtime;

import dev.hycolony.plugin.ornament.api.VariantKey;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;

/**
 * Builds a composed variant's model texture: the shape's vanilla layout texture, each opaque pixel replaced by the
 * same pixel of the tiled primary or secondary material texture, depending on its region. Transparent pixels stay
 * transparent, so the model's cut-outs (thatch fringe) keep their shape.
 *
 * <p>Ponytail: one region rule, the shingle's ({@code Slope_Hay} layout: covering in the top-left 56x32 px, support
 * everywhere else, read off the vanilla texture); a second composed shape needs its own rule.
 */
final class VariantTextureComposer {
    /** Texture region drawn with the primary material, in layout pixels. */
    private static final Rectangle PRIMARY_REGION = new Rectangle(0, 0, 56, 32);

    /** PNG bytes of {@code key}'s model texture; throws when the shape has no layout or a texture is unreadable. */
    byte[] compose(VariantKey key) {
        String layoutName = key.shape()
                .layoutTexture()
                .orElseThrow(() -> new IllegalStateException(key.shape() + " has no texture layout"));
        BufferedImage layout = Textures.read(layoutName);
        BufferedImage primary = Textures.face(Textures.read(key.primary().texture()));
        BufferedImage secondary = Textures.face(Textures.read(key.secondary().texture()));
        BufferedImage out = new BufferedImage(layout.getWidth(), layout.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < layout.getHeight(); y++) {
            for (int x = 0; x < layout.getWidth(); x++) {
                if ((layout.getRGB(x, y) >>> 24) != 0) {
                    BufferedImage src = PRIMARY_REGION.contains(x, y) ? primary : secondary;
                    out.setRGB(x, y, src.getRGB(x % Textures.FACE, y % Textures.FACE));
                }
            }
        }
        return Textures.png(out);
    }
}
