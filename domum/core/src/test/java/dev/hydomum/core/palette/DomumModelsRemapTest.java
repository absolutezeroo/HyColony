package dev.hydomum.core.palette;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/** Every generated two-material model remaps without a face across materials, each onto its own tile. */
class DomumModelsRemapTest {
    // The plugin's generated pack, read from domum/core (Gradle runs tests in the project directory).
    private static final Path PACK = Path.of("../plugin/src/main/resources");
    private static final PaletteLayout.Tile FIRST = new PaletteLayout.Tile(96, 64);
    private static final PaletteLayout.Tile SECOND = new PaletteLayout.Tile(0, 128);

    private record Face(JsonObject shape, String side, JsonObject layout) {}

    @Test
    void everyTwoMaterialModelMovesEachFaceOntoItsMaterialsTile() throws IOException {
        TreeSet<String> models = twoMaterialModels();
        assertFalse(models.isEmpty());
        for (String model : models) {
            String json = Files.readString(PACK.resolve("Common").resolve(model), StandardCharsets.UTF_8);
            List<Face> before = faces(JsonParser.parseString(json).getAsJsonObject());
            List<Face> after = faces(JsonParser.parseString(PaletteModel.remap(json, FIRST, SECOND))
                    .getAsJsonObject());
            assertEquals(before.size(), after.size(), model);
            for (int i = 0; i < before.size(); i++) {
                FaceRect was = FaceRect.of(
                        before.get(i).shape(),
                        before.get(i).side(),
                        before.get(i).layout());
                FaceRect now = FaceRect.of(
                        after.get(i).shape(), after.get(i).side(), after.get(i).layout());
                int half = was.half(PaletteLayout.TILE_PX).orElseThrow();
                PaletteLayout.Tile tile = half == 0 ? FIRST : SECOND;
                double dx = tile.x() - half * PaletteLayout.TILE_PX;
                String where = model + " " + before.get(i).side();
                assertEquals(was.minX() + dx, now.minX(), 1e-6, where);
                assertEquals(was.maxX() + dx, now.maxX(), 1e-6, where);
                assertEquals(was.minY() + tile.y(), now.minY(), 1e-6, where);
            }
        }
    }

    /** The Common paths of the models (main block and states) of every template of a two-slot shape. */
    private static TreeSet<String> twoMaterialModels() throws IOException {
        JsonObject manifest = read(PACK.resolve("hydomum/shapes.json"));
        TreeSet<String> models = new TreeSet<>();
        for (JsonElement shape : manifest.getAsJsonArray("shapes")) {
            JsonObject s = shape.getAsJsonObject();
            if (s.getAsJsonArray("slots").size() != 2) {
                continue;
            }
            Path item = PACK.resolve(
                    "Server/Item/Items/HyDomum/" + s.get("template").getAsString() + ".json");
            JsonObject block = read(item).getAsJsonObject("BlockType");
            addModel(block, models);
            if (block.get("State") instanceof JsonObject state
                    && state.get("Definitions") instanceof JsonObject definitions) {
                definitions.entrySet().forEach(e -> {
                    if (e.getValue() instanceof JsonObject def) {
                        addModel(def, models);
                    }
                });
            }
        }
        return models;
    }

    private static void addModel(JsonObject block, TreeSet<String> models) {
        if (block.has("CustomModel")) {
            models.add(block.get("CustomModel").getAsString());
        }
    }

    private static JsonObject read(Path file) throws IOException {
        return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8))
                .getAsJsonObject();
    }

    /** Every face of the model, in document order. */
    private static List<Face> faces(JsonObject model) {
        List<Face> faces = new ArrayList<>();
        model.getAsJsonArray("nodes").forEach(n -> collect(n.getAsJsonObject(), faces));
        return faces;
    }

    private static void collect(JsonObject node, List<Face> faces) {
        if (node.get("shape") instanceof JsonObject shape && shape.get("textureLayout") instanceof JsonObject layout) {
            layout.entrySet()
                    .forEach(e ->
                            faces.add(new Face(shape, e.getKey(), e.getValue().getAsJsonObject())));
        }
        if (node.get("children") instanceof JsonArray children) {
            children.forEach(c -> collect(c.getAsJsonObject(), faces));
        }
    }
}
