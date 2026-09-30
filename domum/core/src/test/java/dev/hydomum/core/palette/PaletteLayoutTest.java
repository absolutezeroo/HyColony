package dev.hydomum.core.palette;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class PaletteLayoutTest {
    @Test
    void materialsAreSortedOnceIntoASquareGridOfTiles() {
        PaletteLayout layout = PaletteLayout.of(List.of("b", "a", "c", "a"));
        assertEquals(List.of("a", "b", "c"), layout.materials());
        assertEquals(new PaletteLayout.Tile(0, 0), layout.tile("a").orElseThrow());
        assertEquals(new PaletteLayout.Tile(32, 0), layout.tile("b").orElseThrow());
        assertEquals(new PaletteLayout.Tile(0, 32), layout.tile("c").orElseThrow());
        assertEquals(64, layout.widthPx());
        assertEquals(64, layout.heightPx());
    }

    @Test
    void unknownMaterialHasNoTile() {
        assertTrue(PaletteLayout.of(List.of("a")).tile("z").isEmpty());
    }

    @Test
    void emptyPaletteStillHasOneTile() {
        PaletteLayout layout = PaletteLayout.of(List.of());
        assertEquals(32, layout.widthPx());
        assertEquals(32, layout.heightPx());
    }
}
