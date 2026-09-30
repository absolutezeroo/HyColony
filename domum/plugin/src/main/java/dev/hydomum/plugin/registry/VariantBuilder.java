package dev.hydomum.plugin.registry;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import dev.hydomum.api.VariantKey;
import dev.hydomum.plugin.runtime.DynamicBlockTypeFactory;
import dev.hydomum.plugin.runtime.IconMap;
import dev.hydomum.plugin.runtime.MaterialCatalog;
import dev.hydomum.plugin.runtime.VariantAssets;
import dev.hydomum.plugin.runtime.VariantPalette;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;
import java.util.logging.Level;
import org.jspecify.annotations.Nullable;

/**
 * Builds a batch of variants, not yet registered: each one's BlockTypes (a one-material variant reads its material's
 * texture through its template's models, a two-material one the palette through remapped models) and its Item with
 * its painted icon. A variant that fails is logged and left out. MC DO makes the same block from the cutter's
 * components ({@code ArchitectsCutterRecipe.assemble}) and retextures it at render time
 * ({@code MateriallyTexturedBakedModel}); here each combination is its own BlockType (DynamicBlockTypeFactory).
 */
final class VariantBuilder {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();

    private final DynamicBlockTypeFactory factory = new DynamicBlockTypeFactory();
    private final VariantAssets assets;
    private final VariantPalette palette;
    // Plugin resources, fixed while the server runs; batches may build concurrently.
    private final Map<String, Optional<IconMap>> iconMaps = new ConcurrentHashMap<>();

    /** A batch: its BlockTypes and Items, the keys built, and the models and icons they name. */
    record Built(List<BlockType> types, List<Item> items, List<VariantKey> done, Set<String> assetNames) {}

    VariantBuilder(VariantAssets assets, VariantPalette palette) {
        this.assets = assets;
        this.palette = palette;
    }

    /** Builds keys with materials' textures; must run off world threads (it reads and writes files). */
    Built build(List<VariantKey> keys, MaterialCatalog materials) {
        List<BlockType> types = new ArrayList<>();
        List<Item> items = new ArrayList<>();
        List<VariantKey> done = new ArrayList<>();
        Set<String> assetNames = new HashSet<>();
        for (VariantKey key : keys) {
            try {
                List<String> textures = textures(key, materials);
                Set<String> named = new HashSet<>();
                List<BlockType> family = textures.size() == 1
                        ? factory.create(key, textures.getFirst(), UnaryOperator.identity())
                        : factory.create(key, VariantPalette.TEXTURE, m -> {
                            String model = palette.model(
                                    m, key.materials().get(0), key.materials().get(1));
                            named.add(model);
                            return model;
                        });
                String icon = icon(key, textures);
                items.add(factory.createItem(key, icon));
                types.addAll(family);
                done.add(key);
                assetNames.addAll(named);
                if (icon != null) {
                    assetNames.add(icon);
                }
            } catch (RuntimeException | LinkageError | java.awt.AWTError e) { // AWT may lack native libraries
                LOG.at(Level.SEVERE).withCause(e).log("hydomum: cannot create %s", key.id());
            }
        }
        return new Built(types, items, done, assetNames);
    }

    /** The texture of each of key's materials, in slot order. */
    private static List<String> textures(VariantKey key, MaterialCatalog materials) {
        return key.materials().stream()
                .map(m -> materials.texture(m).orElseThrow(() -> new IllegalStateException("not a material: " + m)))
                .toList();
    }

    /** key's icon painted through its shape's icon map; null (the template's icon) when it cannot be. */
    private @Nullable String icon(VariantKey key, List<String> textures) {
        try {
            Optional<IconMap> map = iconMaps.computeIfAbsent(key.shape().id(), IconMap::load);
            if (map.isPresent()) {
                return assets.icon(key, map.get(), textures);
            }
            LOG.at(Level.WARNING).log(
                    "hydomum: no icon map for %s, template icon kept",
                    key.shape().id());
        } catch (RuntimeException | LinkageError | java.awt.AWTError e) { // AWT may lack native libraries
            LOG.at(Level.SEVERE).withCause(e).log("hydomum: icon of %s failed, template icon kept", key.id());
        }
        return null;
    }
}
