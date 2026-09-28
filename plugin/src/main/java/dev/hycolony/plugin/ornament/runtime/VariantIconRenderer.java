package dev.hycolony.plugin.ornament.runtime;

import dev.hycolony.plugin.ornament.api.VariantKey;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;

/**
 * Draws a variant's 64x64 inventory icon from the textures the client already has: the fill texture on the three
 * visible faces of an isometric cube, the frame texture as a 4 px border on each face (the model's edge beams).
 *
 * <p>Hytale has no server-side icon renderer: vanilla {@code Icons/ItemsGenerated} PNGs are rendered by the Asset
 * Editor client and uploaded (docs/research/domum-ornamentum.md B.11). The cube geometry is measured on the vanilla
 * {@code Rock_Stone_Brick} icon. Ponytail: one painter for the timber frame's box shape; another shape needs its own
 * painter, or a real {@code .blockymodel} rasterizer if shapes multiply.
 */
final class VariantIconRenderer {
    /** Icon width and height, in pixels (vanilla ItemsGenerated icons are 64x64). */
    static final int SIZE = 64;

    private static final int FACE = Textures.FACE;
    /** Frame beam width on a face, in texture pixels (TimberFrame.blockymodel beams are 4 units wide). */
    private static final int BEAM = 4;

    /** Face -> icon transforms: origin, then where the face's x and y axes (32 px each) land in the icon. */
    private static final AffineTransform TOP = face(new Point(5, 16), new Point(32, 2), new Point(32, 30));

    private static final AffineTransform LEFT = face(new Point(5, 16), new Point(32, 30), new Point(5, 48));
    private static final AffineTransform RIGHT = face(new Point(32, 30), new Point(59, 16), new Point(32, 62));
    private static final float LEFT_SHADE = 0.15f;
    private static final float RIGHT_SHADE = 0.3f;

    /** PNG bytes of {@code key}'s icon; throws when a texture is missing or unreadable. */
    byte[] render(VariantKey key) {
        BufferedImage face = frameOnFill(
                Textures.face(Textures.read(key.primary().texture())),
                Textures.face(Textures.read(key.secondary().texture())));
        BufferedImage icon = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = icon.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        draw(g, face, TOP, 0);
        draw(g, face, LEFT, LEFT_SHADE);
        draw(g, face, RIGHT, RIGHT_SHADE);
        g.dispose();
        return Textures.png(icon);
    }

    /** One 32x32 face: the fill, with a {@link #BEAM} px border cut from the frame texture. */
    private static BufferedImage frameOnFill(BufferedImage frame, BufferedImage fill) {
        BufferedImage face = new BufferedImage(FACE, FACE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = face.createGraphics();
        g.drawImage(fill, 0, 0, null);
        strip(g, frame, new Rectangle(0, 0, FACE, BEAM));
        strip(g, frame, new Rectangle(0, FACE - BEAM, FACE, BEAM));
        strip(g, frame, new Rectangle(0, 0, BEAM, FACE));
        strip(g, frame, new Rectangle(FACE - BEAM, 0, BEAM, FACE));
        g.dispose();
        return face;
    }

    /** Copies region {@code r} of {@code src} to the same place. */
    private static void strip(Graphics2D g, BufferedImage src, Rectangle r) {
        g.drawImage(src.getSubimage(r.x, r.y, r.width, r.height), r.x, r.y, null);
    }

    /** Draws {@code face} through {@code at}, darkened by {@code shade} (0 = unchanged) like a lit cube side. */
    private static void draw(Graphics2D g, BufferedImage face, AffineTransform at, float shade) {
        AffineTransform saved = g.getTransform();
        g.transform(at);
        g.drawImage(face, 0, 0, null);
        if (shade > 0) {
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_ATOP, shade));
            g.setColor(Color.BLACK);
            g.fillRect(0, 0, FACE, FACE);
            g.setComposite(AlphaComposite.SrcOver);
        }
        g.setTransform(saved);
    }

    /** Maps the face square: (0,0) to {@code o}, (32,0) to {@code x}, (0,32) to {@code y}. */
    private static AffineTransform face(Point o, Point x, Point y) {
        return new AffineTransform(
                (double) (x.x - o.x) / FACE,
                (double) (x.y - o.y) / FACE,
                (double) (y.x - o.x) / FACE,
                (double) (y.y - o.y) / FACE,
                o.x,
                o.y);
    }
}
