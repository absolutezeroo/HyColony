package dev.hycolony.plugin.ornament.api;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * A material an ornament slot can take, drawn with a vanilla block texture that every client already holds: each one
 * is used by vanilla blocks both as a cube texture and as a {@code CustomModelTexture}.
 *
 * <p>Prototype table for the runtime-variant experiment (docs/research/domum-ornamentum.md B.11); the real material
 * list comes with the DO cutter (DO-2) and its ids then move to hycolony/id-map.json (CLAUDE.md § 7).
 */
public enum OrnamentMaterial {
    OAK("Wood_Hardwood_Planks", "Wood_Hardwood_Planks"),
    BIRCH("Wood_Lightwood_Planks", "Wood_Lightwood_Planks"),
    SPRUCE("Wood_Softwood_Planks_Top", "Wood_Softwood_Planks"),
    REDWOOD("Wood_Redwood_Planks", "Wood_Redwood_Planks"),
    STONE("Rock_Stone_Brick", "Rock_Stone_Brick"),
    PLASTER("Wall_Painted_Beige", "Soil_Clay_Smooth_Yellow"),
    CLAY("Clay_Smooth_White", "Soil_Clay_Smooth_White"),
    SANDSTONE("Rock_Sandstone_White_Brick", "Rock_Sandstone_White_Brick");

    private final String texture;
    private final String icon;

    /**
     * @param texture file name (without .png) under {@code BlockTextures/}
     * @param item the vanilla block item drawn with that texture, whose generated icon stands for the material
     */
    OrnamentMaterial(String texture, String item) {
        this.texture = "BlockTextures/" + texture + ".png";
        this.icon = "Icons/ItemsGenerated/" + item + ".png";
    }

    /** Common asset path of the texture, relative to {@code Common/}. */
    public String texture() {
        return texture;
    }

    /** Common asset path of a vanilla item icon showing this material, relative to {@code Common/}. */
    public String icon() {
        return icon;
    }

    /** The material named {@code name}, case-insensitive; empty when unknown. */
    public static Optional<OrnamentMaterial> parse(String name) {
        return Arrays.stream(values())
                .filter(m -> m.name().equalsIgnoreCase(name))
                .findFirst();
    }

    /** Every material name in lower case, comma-separated, for help messages. */
    public static String names() {
        return Arrays.stream(values())
                .map(m -> m.name().toLowerCase(Locale.ROOT))
                .collect(Collectors.joining(", "));
    }
}
