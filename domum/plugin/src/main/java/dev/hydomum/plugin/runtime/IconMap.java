package dev.hydomum.plugin.runtime;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Optional;
import java.util.zip.CRC32;
import javax.imageio.ImageIO;

/**
 * A shape's icon map, drawn at build time by tools/domum/iconmap.py: each pixel codes where to read the shape's
 * texture layout (R = u * 256 / width, G = v * 256 / height), its shade (B / 255) and whether the model covers it
 * (A). Painting a variant's icon is then a lookup in its layout texture: no 3D rendering at runtime.
 */
public final class IconMap {
    private static final String FOLDER = "/hydomum/icons/";

    private final BufferedImage map;
    private final long crc;

    private IconMap(BufferedImage map, long crc) {
        this.map = map;
        this.crc = crc;
    }

    /** The CRC-32 of the map's PNG: an icon painted through it is out of date once it changes. */
    long crc() {
        return crc;
    }

    /** The icon map of shapeId, a plugin resource; empty when absent. Throws when present but unreadable. */
    public static Optional<IconMap> load(String shapeId) {
        try (InputStream in = IconMap.class.getResourceAsStream(FOLDER + shapeId + ".png")) {
            if (in == null) {
                return Optional.empty();
            }
            byte[] png = in.readAllBytes();
            BufferedImage map = ImageIO.read(new ByteArrayInputStream(png));
            if (map == null) {
                throw new IllegalStateException("unreadable icon map " + shapeId);
            }
            CRC32 crc = new CRC32();
            crc.update(png);
            return Optional.of(new IconMap(map, crc.getValue()));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * The icon of a variant whose layout texture is layout: every covered pixel takes the texel it codes, darkened
     * by its shade, with the texel's own alpha (tools/domum/icon.py from_map does the same).
     */
    public BufferedImage sample(BufferedImage layout) {
        int width = layout.getWidth();
        int height = layout.getHeight();
        BufferedImage icon = new BufferedImage(map.getWidth(), map.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < map.getHeight(); y++) {
            for (int x = 0; x < map.getWidth(); x++) {
                int code = map.getRGB(x, y);
                if (code >>> 24 == 0) {
                    continue;
                }
                int texel = layout.getRGB(((code >> 16) & 0xFF) * width / 256, ((code >> 8) & 0xFF) * height / 256);
                int shade = code & 0xFF;
                int red = ((texel >> 16) & 0xFF) * shade / 255;
                int green = ((texel >> 8) & 0xFF) * shade / 255;
                int blue = (texel & 0xFF) * shade / 255;
                icon.setRGB(x, y, (texel & 0xFF000000) | (red << 16) | (green << 8) | blue);
            }
        }
        return icon;
    }
}
