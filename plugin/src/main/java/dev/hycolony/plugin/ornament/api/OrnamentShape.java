package dev.hycolony.plugin.ornament.api;

/**
 * A DO block shape: one shared {@code .blockymodel}, carried by a template BlockType of our asset pack that every
 * variant copies. The primary material textures the model, the secondary one the cube (DrawType
 * {@code CubeWithModel}, as vanilla ores draw a vein model over a rock cube).
 */
public enum OrnamentShape {
    /** Frame beams (primary) around a filled cube (secondary), like DO's timber frame. */
    TIMBER_FRAME("HyColony_Ornament_TimberFrame");

    private final String templateKey;

    OrnamentShape(String templateKey) {
        this.templateKey = templateKey;
    }

    /** BlockType key of the template in our asset pack; variants are named after it. */
    public String templateKey() {
        return templateKey;
    }
}
