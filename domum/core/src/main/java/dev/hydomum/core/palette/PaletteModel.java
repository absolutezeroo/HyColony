package dev.hydomum.core.palette;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.util.Map;

/**
 * Rewrites a two-material {@code .blockymodel} made for a pair layout (first material's face in texels 0-31, second's
 * in 32-63) so that each face reads its material's {@link PaletteLayout} tile instead: the face's rectangle
 * ({@link FaceRect}) tells its half, then its offset moves by that tile's distance to the half. Mirror, angle and
 * every other field are kept.
 */
public final class PaletteModel {
    private PaletteModel() {}

    /**
     * model with every face moved onto first's or second's tile. Throws {@link IllegalArgumentException} when model is
     * not a readable model JSON or a face reads across both halves or outside the pair layout.
     */
    public static String remap(String model, PaletteLayout.Tile first, PaletteLayout.Tile second) {
        JsonObject root;
        try {
            root = JsonParser.parseString(model).getAsJsonObject();
        } catch (JsonParseException | IllegalStateException e) {
            throw new IllegalArgumentException("unreadable model", e);
        }
        for (JsonElement node : array(root, "nodes")) {
            remapNode(node, first, second);
        }
        return root.toString();
    }

    /** Moves the faces of node, then of its children. */
    private static void remapNode(JsonElement node, PaletteLayout.Tile first, PaletteLayout.Tile second) {
        if (!(node instanceof JsonObject object)) {
            throw new IllegalArgumentException("a node is not an object");
        }
        if (object.get("shape") instanceof JsonObject shape && shape.get("textureLayout") instanceof JsonObject faces) {
            for (Map.Entry<String, JsonElement> face : faces.entrySet()) {
                if (!(face.getValue() instanceof JsonObject layout)) {
                    throw new IllegalArgumentException("face " + face.getKey() + " is not an object");
                }
                move(shape, face.getKey(), layout, first, second);
            }
        }
        for (JsonElement child : array(object, "children")) {
            remapNode(child, first, second);
        }
    }

    /** Moves one face's offset onto its material's tile. */
    private static void move(
            JsonObject shape, String side, JsonObject layout, PaletteLayout.Tile first, PaletteLayout.Tile second) {
        int half = FaceRect.of(shape, side, layout)
                .half(PaletteLayout.TILE_PX)
                .orElseThrow(() -> new IllegalArgumentException("face " + side + " reads across both materials"));
        PaletteLayout.Tile tile = half == 0 ? first : second;
        JsonObject offset = FaceRect.object(layout, "offset");
        // Offsets are whole texels: tools/domum/convert.py rounds them (_snap).
        offset.addProperty("x", offset.get("x").getAsInt() + tile.x() - half * PaletteLayout.TILE_PX);
        offset.addProperty("y", offset.get("y").getAsInt() + tile.y());
    }

    /** parent's array key; empty when absent. Throws {@link IllegalArgumentException} when it is not an array. */
    private static JsonArray array(JsonObject parent, String key) {
        JsonElement value = parent.get(key);
        if (value == null) {
            return new JsonArray();
        }
        if (value instanceof JsonArray array) {
            return array;
        }
        throw new IllegalArgumentException(key + " is not an array");
    }
}
