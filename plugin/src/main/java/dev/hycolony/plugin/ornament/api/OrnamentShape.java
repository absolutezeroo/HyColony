package dev.hycolony.plugin.ornament.api;

import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * A DO block shape: one shared {@code .blockymodel}, carried by a template BlockType of our asset pack that every
 * variant copies. Hytale gives a model a single texture, so a shape draws its two materials one of two ways:
 * <ul>
 *   <li>cube + model (DrawType {@code CubeWithModel}, as vanilla ores): the primary material textures the model, the
 *       secondary one the cube;
 *   <li>composed: one texture per variant, built from the template's texture layout with each region filled by a
 *       material's block texture ({@link #layoutTexture()}).
 * </ul>
 */
public enum OrnamentShape {
    /** Frame beams (primary) around a filled cube (secondary), like DO's timber frame. */
    TIMBER_FRAME("HyColony_Ornament_TimberFrame", null),
    /** A roof slope, covering (primary) over its wooden support (secondary), like DO's shingle; vanilla model. */
    SHINGLE("HyColony_Ornament_Shingle", "Blocks/Structures/Roofs/Slope_Hay_Textures/Softwood.png");

    private final String templateKey;
    private final @Nullable String layoutTexture;

    OrnamentShape(String templateKey, @Nullable String layoutTexture) {
        this.templateKey = templateKey;
        this.layoutTexture = layoutTexture;
    }

    /** BlockType key of the template in our asset pack; variants are named after it. */
    public String templateKey() {
        return templateKey;
    }

    /** The vanilla model texture whose layout a composed variant texture follows; empty for a cube + model shape. */
    public Optional<String> layoutTexture() {
        return Optional.ofNullable(layoutTexture);
    }
}
