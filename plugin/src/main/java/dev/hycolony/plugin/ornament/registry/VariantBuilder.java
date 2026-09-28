package dev.hycolony.plugin.ornament.registry;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import dev.hycolony.core.ornament.VariantKey;
import dev.hycolony.plugin.ornament.runtime.DynamicBlockTypeFactory;
import dev.hycolony.plugin.ornament.runtime.IconMap;
import dev.hycolony.plugin.ornament.runtime.MaterialCatalog;
import dev.hycolony.plugin.ornament.runtime.VariantAssets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import org.jspecify.annotations.Nullable;

/**
 * Builds a batch of variants, not yet registered: each one's layout texture (its material's own, or a pair
 * texture), BlockTypes and Item with its painted icon. A variant that fails is logged and left out.
 */
final class VariantBuilder {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();

    private final DynamicBlockTypeFactory factory = new DynamicBlockTypeFactory();
    private final VariantAssets assets;

    /** A batch: its BlockTypes and Items, the keys built, and whether a pair texture was generated. */
    record Built(List<BlockType> types, List<Item> items, List<VariantKey> done, boolean newTexture) {}

    VariantBuilder(VariantAssets assets) {
        this.assets = assets;
    }

    /** Builds keys with materials' textures; must run off world threads (it reads and writes images). */
    Built build(List<VariantKey> keys, MaterialCatalog materials) {
        List<BlockType> types = new ArrayList<>();
        List<Item> items = new ArrayList<>();
        List<VariantKey> done = new ArrayList<>();
        boolean newTexture = false;
        for (VariantKey key : keys) {
            try {
                VariantAssets.Published texture = layoutTexture(key, materials);
                List<BlockType> family = factory.create(key, texture.name());
                items.add(factory.createItem(key, icon(key, texture.name())));
                types.addAll(family);
                done.add(key);
                newTexture |= texture.created();
            } catch (RuntimeException | LinkageError | java.awt.AWTError e) { // AWT may lack native libraries
                LOG.at(Level.SEVERE).withCause(e).log("hyornament: cannot create %s", key.id());
            }
        }
        return new Built(types, items, done, newTexture);
    }

    /** The texture key's models read: its material's own, or the pair texture of its two materials. */
    private VariantAssets.Published layoutTexture(VariantKey key, MaterialCatalog materials) {
        List<String> textures = key.materials().stream()
                .map(m -> materials.texture(m).orElseThrow(() -> new IllegalStateException("not a material: " + m)))
                .toList();
        if (textures.size() == 1) {
            return new VariantAssets.Published(textures.getFirst(), false);
        }
        return assets.pairTexture(key.materials().get(0), key.materials().get(1), textures.get(0), textures.get(1));
    }

    /** key's icon painted through its shape's icon map; null (the template's icon) when it cannot be. */
    private @Nullable String icon(VariantKey key, String layoutTexture) {
        try {
            Optional<IconMap> map = IconMap.load(key.shape().id());
            if (map.isPresent()) {
                return assets.icon(key, map.get(), layoutTexture);
            }
            LOG.at(Level.WARNING).log(
                    "hyornament: no icon map for %s, template icon kept",
                    key.shape().id());
        } catch (RuntimeException | LinkageError | java.awt.AWTError e) { // AWT may lack native libraries
            LOG.at(Level.SEVERE).withCause(e).log("hyornament: icon of %s failed, template icon kept", key.id());
        }
        return null;
    }
}
