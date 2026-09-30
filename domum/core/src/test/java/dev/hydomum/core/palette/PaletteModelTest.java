package dev.hydomum.core.palette;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

class PaletteModelTest {
    private static final PaletteLayout.Tile FIRST = new PaletteLayout.Tile(96, 64);
    private static final PaletteLayout.Tile SECOND = new PaletteLayout.Tile(0, 128);

    /** A one-box model: shape type and size, then one face's side, offset, x mirror and angle. */
    private static final String BOX = """
            {"nodes": [{"id": "1", "children": [{"id": "2", "children": [],
              "shape": {"type": "%s", "settings": {"size": {"x": %d, "y": %d, "z": %d}}, "textureLayout": {
                "%s": {"offset": {"x": %d, "y": %d}, "mirror": {"x": %b, "y": false}, "angle": %d}}}}]}]}""";

    private static String box(String type, int sx, int sy, int sz, String side, int x, int y, boolean mx, int angle) {
        return BOX.formatted(type, sx, sy, sz, side, x, y, mx, angle);
    }

    private static JsonObject face(String remapped, String side) {
        return JsonParser.parseString(remapped)
                .getAsJsonObject()
                .getAsJsonArray("nodes")
                .get(0)
                .getAsJsonObject()
                .getAsJsonArray("children")
                .get(0)
                .getAsJsonObject()
                .getAsJsonObject("shape")
                .getAsJsonObject("textureLayout")
                .getAsJsonObject(side);
    }

    private static int offset(String remapped, String side, String axis) {
        return face(remapped, side).getAsJsonObject("offset").get(axis).getAsInt();
    }

    @Test
    void secondMaterialFaceMovesIntoTheSecondTile() {
        String remapped = PaletteModel.remap(box("box", 4, 2, 2, "front", 34, 2, false, 0), FIRST, SECOND);
        assertEquals(2, offset(remapped, "front", "x"));
        assertEquals(130, offset(remapped, "front", "y"));
    }

    @Test
    void firstMaterialFaceTurnedHalfWayKeepsItsPivotOnTheFirstTileEdge() {
        // TimberFrame_Framed E6 top: offset (32, 32), angle 180, reads texels 30..32 of the frame.
        String remapped = PaletteModel.remap(box("box", 2, 4, 2, "top", 32, 32, false, 180), FIRST, SECOND);
        assertEquals(128, offset(remapped, "top", "x"));
        assertEquals(96, offset(remapped, "top", "y"));
    }

    @Test
    void mirroredSecondMaterialFaceWithItsPivotAtTheLayoutEdgeStaysOnTheSecondMaterial() {
        String remapped = PaletteModel.remap(box("box", 6, 4, 2, "front", 64, 0, true, 0), FIRST, SECOND);
        assertEquals(32, offset(remapped, "front", "x"));
        assertEquals(
                true, face(remapped, "front").getAsJsonObject("mirror").get("x").getAsBoolean());
    }

    @Test
    void quarterTurnsReadTheSizeTheirWay() {
        // 90: x spans -6..0 from the pivot, first material; 270 on a left face: x spans 0..y, second material.
        String turned = PaletteModel.remap(box("box", 4, 6, 2, "front", 10, 0, false, 90), FIRST, SECOND);
        assertEquals(106, offset(turned, "front", "x"));
        String back = PaletteModel.remap(box("box", 2, 6, 4, "left", 40, 4, false, 270), FIRST, SECOND);
        assertEquals(8, offset(back, "left", "x"));
        assertEquals(132, offset(back, "left", "y"));
    }

    @Test
    void quadReadsItsXAndYSizeOnEverySide() {
        // A quad's left face reads x 28..34, across both materials; a box's would read its z (0 wide) and pass.
        assertThrows(
                IllegalArgumentException.class,
                () -> PaletteModel.remap(box("quad", 6, 4, 0, "left", 28, 0, false, 0), FIRST, SECOND));
    }

    @Test
    void faceAcrossBothMaterialsIsRefused() {
        assertThrows(
                IllegalArgumentException.class,
                () -> PaletteModel.remap(box("box", 8, 2, 2, "front", 28, 0, false, 0), FIRST, SECOND));
    }

    @Test
    void malformedModelsAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> PaletteModel.remap("not json", FIRST, SECOND));
        assertThrows(IllegalArgumentException.class, () -> PaletteModel.remap("{\"nodes\": 3}", FIRST, SECOND));
        String noX = box("box", 4, 2, 2, "front", 34, 2, false, 0).replace("\"x\": 34, ", "");
        assertThrows(IllegalArgumentException.class, () -> PaletteModel.remap(noX, FIRST, SECOND));
    }
}
