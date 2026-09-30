package dev.hydomum.core.palette;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Where each material's face sits in the palette texture: a square grid of {@link #TILE_PX} tiles, materials sorted
 * by id, row by row. Models then read both materials of a pair from this one texture, which clients hold from
 * connection, instead of a new pair texture (whose atlas rebuild flickers: docs/research/client-block-atlas.md).
 *
 * <p>Deviation from MC: DO retextures one model per block on the client; Hytale gives a model a single texture.
 */
public final class PaletteLayout {
    /** Side, in pixels, of one material's tile (a block face, as in the pair textures). */
    public static final int TILE_PX = 32;

    private final List<String> materials;
    private final Map<String, Integer> indexes = new HashMap<>();
    private final int columns;

    /** Top-left pixel of a material's tile. */
    public record Tile(int x, int y) {}

    private PaletteLayout(List<String> materials) {
        this.materials = materials;
        this.columns = Math.max(1, (int) Math.ceil(Math.sqrt(materials.size())));
        for (int i = 0; i < materials.size(); i++) {
            indexes.put(materials.get(i), i);
        }
    }

    /** The layout of materials, each once, sorted by id. */
    public static PaletteLayout of(Collection<String> materials) {
        return new PaletteLayout(materials.stream().distinct().sorted().toList());
    }

    /** The materials in tile order. */
    public List<String> materials() {
        return materials;
    }

    /** material's tile; empty when it is not in the palette. */
    public Optional<Tile> tile(String material) {
        Integer i = indexes.get(material);
        return i == null ? Optional.empty() : Optional.of(new Tile(i % columns * TILE_PX, i / columns * TILE_PX));
    }

    /** Palette width in pixels. */
    public int widthPx() {
        return columns * TILE_PX;
    }

    /** Palette height in pixels, at least one row. */
    public int heightPx() {
        int rows = Math.max(1, (materials.size() + columns - 1) / columns);
        return rows * TILE_PX;
    }
}
