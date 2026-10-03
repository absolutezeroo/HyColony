package dev.hycolony.plugin.adapter;

import com.hypixel.hytale.logger.HytaleLogger;
import dev.hycolony.core.kernel.catalog.ItemCatalog;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.item.ToolInfo;
import dev.hycolony.plugin.item.HytaleItemInfo;
import dev.hycolony.plugin.item.HytaleStacks;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import org.jspecify.annotations.Nullable;

/**
 * ItemCatalog over the Hytale item asset map (cheat sheet § 2). Results are cached per key; the cache is not cleared on
 * an asset reload (restart after changing assets). World thread only.
 *
 * <p>Hytale has no MineColonies tool levels, so these are mapped:
 * <ul>
 *   <li>tool level = {@code max(0, Quality - 1)} of the tool's spec for its own gather type (pickaxe → Rocks,
 *       axe → Woods, shovel → Soils). Vanilla pickaxes start at Quality 1 (Wood, Crude, Scrap → level 0; Copper 1;
 *       Iron 2; Cobalt, Thorium 3; Adamantite 4; Mithril, Onyxium 5); hatchets and shovels have no Quality, so they
 *       are all level 0. The lowest tier is therefore always 0, as the core requires;</li>
 *   <li>tool speed = {@code power / unarmedPower} for the same gather type, so that with the block hardness of
 *       {@code HytaleBlockCatalog}, {@code hardness / speed = 0.05 / power}: mining time follows Hytale's hits per
 *       block;</li>
 *   <li>durability = blocks of its own gather type mined before breaking:
 *       {@code maxDurability / (lossPerHit * ceil(1 / power))}.</li>
 * </ul>
 */
public final class HytaleItemCatalog implements ItemCatalog {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();

    private final Map<ItemKey, HytaleItemInfo> items = new HashMap<>();
    private @Nullable List<ItemKey> tools;
    /** The id-map's hoes and their tool level: Hytale hoes have no tool spec to map (they till by interaction). */
    private final Map<String, Integer> hoeLevels;

    private final HytaleStacks stacks = new HytaleStacks(this::durability);
    private boolean warned;

    /** {@code hoeLevels}: the id-map's hoes and their tool level. */
    public HytaleItemCatalog(Map<String, Integer> hoeLevels) {
        this.hoeLevels = Map.copyOf(hoeLevels);
    }

    /** Every Hytale item {@link #tool} knows; listed once, at the first call. */
    @Override
    public List<ItemKey> tools() {
        if (tools == null) {
            tools = HytaleItemInfo.allIds().stream()
                    .map(ItemKey::new)
                    .filter(k -> tool(k).isPresent())
                    .toList();
        }
        return tools;
    }

    /** The stack conversion that turns a tool's damage into Hytale durability with this catalog's durabilities. */
    public HytaleStacks stacks() {
        return stacks;
    }

    @Override
    public int maxStack(ItemKey item) {
        return item(item).maxStack();
    }

    @Override
    public Optional<ToolInfo> tool(ItemKey item) {
        return item(item).tool();
    }

    @Override
    public int durability(ItemKey item) {
        return item(item).durability();
    }

    private HytaleItemInfo item(ItemKey key) {
        HytaleItemInfo info = items.get(key);
        if (info == null) {
            try {
                info = HytaleItemInfo.of(key.id(), hoeLevels);
            } catch (RuntimeException e) {
                fail(key.id(), e);
                info = HytaleItemInfo.UNKNOWN;
            }
            items.put(key, info);
        }
        return info;
    }

    private void fail(String id, RuntimeException e) {
        LOG.at(warned ? Level.FINE : Level.WARNING).withCause(e).log("ItemCatalog lookup failed for %s", id);
        warned = true;
    }
}
