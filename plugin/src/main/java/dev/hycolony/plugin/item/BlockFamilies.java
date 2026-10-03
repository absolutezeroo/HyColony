package dev.hycolony.plugin.item;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * The {@code construction} section of the id-map (docs/research/domaine1-suite.md): the blocks of MC's dirt tag, the
 * plan blocks any of them answers (Structurize GrassPlacementHandler: grass and dirt), the dirt paths
 * (BlockGrassPathPlacementHandler), MC's plain {@code Blocks.DIRT}, and the connection templates of the walls,
 * fences, bars and gates whose shape follows their neighbours (MC GeneralBlockPlacementHandler). Absent from an older
 * id-map: none.
 */
public record BlockFamilies(
        @Nullable List<String> dirt,
        @Nullable List<String> dirtCells,
        @Nullable List<String> dirtPaths,
        @Nullable String plainDirt,
        @Nullable List<String> freeShapeTemplates) {
    /** An id-map without the section. */
    public static final BlockFamilies NONE = new BlockFamilies(null, null, null, null, null);

    public Set<String> dirtBlocks() {
        return Set.copyOf(Objects.requireNonNullElse(dirt, List.of()));
    }

    public Set<String> dirtCellBlocks() {
        return Set.copyOf(Objects.requireNonNullElse(dirtCells, List.of()));
    }

    public Set<String> dirtPathBlocks() {
        return Set.copyOf(Objects.requireNonNullElse(dirtPaths, List.of()));
    }

    public Optional<String> plainDirtBlock() {
        return Optional.ofNullable(plainDirt);
    }

    public Set<String> freeShapeTemplateIds() {
        return Set.copyOf(Objects.requireNonNullElse(freeShapeTemplates, List.of()));
    }
}
