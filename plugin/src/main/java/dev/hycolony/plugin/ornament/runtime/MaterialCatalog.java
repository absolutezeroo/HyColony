package dev.hycolony.plugin.ornament.runtime;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.DrawType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockTypeTextures;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import dev.hycolony.core.ornament.MaterialTags;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * The materials Domum Ornamentum variants can take: the DO tags of the id-map, each block read from the loaded
 * vanilla BlockTypes for its side texture (a cube's north face; the generator only tags blocks whose four sides
 * match) and its item icon. A tagged block that is missing or not a cube is left out with a warning.
 */
public final class MaterialCatalog {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();

    private final MaterialTags tags;
    private final Map<String, String> textures;
    private final Map<String, String> icons;

    private MaterialCatalog(MaterialTags tags, Map<String, String> textures, Map<String, String> icons) {
        this.tags = tags;
        this.textures = Map.copyOf(textures);
        this.icons = Map.copyOf(icons);
    }

    /** Reads every tagged block; call once the BlockType store is loaded (LoadAssetEvent, PRIORITY_LOAD_LATE). */
    public static MaterialCatalog load(Map<String, List<String>> tagged) {
        Map<String, String> textures = new HashMap<>();
        Map<String, String> icons = new HashMap<>();
        // Each block once, however many tags name it: one warning per bad block.
        Set<String> materials = new LinkedHashSet<>();
        tagged.values().forEach(materials::addAll);
        materials.forEach(blockId -> read(blockId, textures, icons));
        Map<String, Set<String>> kept = new HashMap<>();
        tagged.forEach((tag, blocks) -> kept.put(
                tag,
                blocks.stream().filter(textures::containsKey).collect(Collectors.toCollection(LinkedHashSet::new))));
        return new MaterialCatalog(new MaterialTags(kept), textures, icons);
    }

    public MaterialTags tags() {
        return tags;
    }

    /** The Common path of blockId's side texture; empty when it is not a material. */
    public Optional<String> texture(String blockId) {
        return Optional.ofNullable(textures.get(blockId));
    }

    /** The Common path of blockId's item icon; empty when it is not a material or has no icon. */
    public Optional<String> icon(String blockId) {
        return Optional.ofNullable(icons.get(blockId));
    }

    /** Records blockId's texture (and icon) when it is a loaded cube; logs a warning otherwise. */
    private static void read(String blockId, Map<String, String> textures, Map<String, String> icons) {
        BlockType block = BlockType.getAssetMap().getAsset(blockId);
        BlockTypeTextures[] faces = block == null ? null : block.getTextures();
        if (block == null || block.getDrawType() != DrawType.Cube || faces == null || faces.length == 0) {
            LOG.at(Level.WARNING).log("hyornament: tagged material %s is not a loaded cube block, left out", blockId);
            return;
        }
        textures.put(blockId, faces[0].getNorth());
        Item item = block.getItem();
        if (item != null && item.getIcon() != null) {
            icons.put(blockId, item.getIcon());
        }
    }
}
