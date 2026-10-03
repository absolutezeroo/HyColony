package dev.hydomum.core.palette;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.OptionalInt;

/**
 * The texture rectangle one face of a blockymodel box or quad reads, in texels: the offset is the pivot, the mirror
 * flips the face's size over it, then the angle turns it about it (the rule tools/blockpaint/models.py face_rects
 * verified on vanilla models, which tools/domum/convert.py writes).
 */
record FaceRect(double minX, double minY, double maxX, double maxY) {
    private static final double EPSILON = 1e-6;

    /** The rectangle side's layout reads; throws {@link IllegalArgumentException} on a bad size, offset or angle. */
    static FaceRect of(JsonObject shape, String side, JsonObject layout) {
        double[] size = mirrored(size(shape, side), layout);
        int angle = layout.has("angle") ? (int) number(layout, "angle") : 0;
        JsonObject offset = object(layout, "offset");
        return turned(number(offset, "x"), number(offset, "y"), size[0], size[1], angle);
    }

    /** (width, height) side reads on shape: (x, y) for front, back and quads, (z, y) sideways, (x, z) flat. */
    private static double[] size(JsonObject shape, String side) {
        JsonObject size = object(object(shape, "settings"), "size");
        boolean quad = shape.get("type") instanceof JsonPrimitive type && "quad".equals(type.getAsString());
        boolean sideways = !quad && ("left".equals(side) || "right".equals(side));
        boolean flat = !quad && ("top".equals(side) || "bottom".equals(side));
        return new double[] {number(size, sideways ? "z" : "x"), number(size, flat ? "z" : "y")};
    }

    /** size with its width or height negated where layout mirrors x or y. */
    private static double[] mirrored(double[] size, JsonObject layout) {
        if (layout.get("mirror") instanceof JsonObject mirror) {
            return new double[] {flag(mirror, "x") ? -size[0] : size[0], flag(mirror, "y") ? -size[1] : size[1]};
        }
        return size;
    }

    /** The rectangle of a w x h face at pivot (u, v), turned by angle about the pivot. */
    private static FaceRect turned(double u, double v, double w, double h, int angle) {
        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        for (double[] c : new double[][] {turn(0, 0, angle), turn(w, 0, angle), turn(0, h, angle), turn(w, h, angle)}) {
            minX = Math.min(minX, c[0]);
            minY = Math.min(minY, c[1]);
            maxX = Math.max(maxX, c[0]);
            maxY = Math.max(maxY, c[1]);
        }
        return new FaceRect(u + minX, v + minY, u + maxX, v + maxY);
    }

    /**
     * The half of a two-tile pair layout (0 or 1, tiles of tilePx) this rectangle lies in; empty when it crosses
     * both or leaves the layout.
     */
    OptionalInt half(int tilePx) {
        int half = (int) Math.floor((minX + EPSILON) / tilePx);
        boolean inside = half >= 0
                && half <= 1
                && maxX <= (half + 1) * tilePx + EPSILON
                && minY >= -EPSILON
                && maxY <= tilePx + EPSILON;
        return inside ? OptionalInt.of(half) : OptionalInt.empty();
    }

    /** (x, y) turned by angle, a quarter turn multiple (tools/blockpaint/models.py TURNS). */
    private static double[] turn(double x, double y, int angle) {
        return switch (Math.floorMod(angle, 360)) {
            case 0 -> new double[] {x, y};
            case 90 -> new double[] {-y, x};
            case 180 -> new double[] {-x, -y};
            case 270 -> new double[] {y, -x};
            default -> throw new IllegalArgumentException("angle " + angle + " is not a quarter turn");
        };
    }

    static JsonObject object(JsonObject parent, String key) {
        if (parent.get(key) instanceof JsonObject child) {
            return child;
        }
        throw new IllegalArgumentException("missing object " + key);
    }

    private static double number(JsonObject parent, String key) {
        if (parent.get(key) instanceof JsonPrimitive p && p.isNumber()) {
            return p.getAsDouble();
        }
        throw new IllegalArgumentException("missing number " + key);
    }

    private static boolean flag(JsonObject parent, String key) {
        JsonElement value = parent.get(key);
        return value instanceof JsonPrimitive p && p.isBoolean() && p.getAsBoolean();
    }
}
