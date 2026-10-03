package dev.hycolony.plugin;

import com.hypixel.hytale.server.core.universe.world.connectedblocks.CustomConnectedBlockTemplateAsset;
import dev.hycolony.plugin.item.BlockFamilies;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/** The id checks of {@link IdMap#validate}: each mapped id against the loaded assets, reported in plain words. */
final class IdChecks {
    private IdChecks() {}

    /** Adds "what key -> id" to {@code errors} for each missing (or unknown) id. */
    static void check(List<String> errors, String what, Map<String, String> ids, Predicate<String> exists) {
        ids.forEach((key, id) -> {
            if (id == null || !exists.test(id)) {
                errors.add(what + " " + key + " -> " + id);
            }
        });
    }

    /** Checks the construction section: its blocks exist ({@code block}), and its connection templates. */
    static void checkConstruction(List<String> errors, BlockFamilies families, Predicate<String> block) {
        check(errors, "dirt block", byId(List.copyOf(families.dirtBlocks())), block);
        check(errors, "dirt cell block", byId(List.copyOf(families.dirtCellBlocks())), block);
        check(errors, "dirt path block", byId(List.copyOf(families.dirtPathBlocks())), block);
        check(
                errors,
                "plain dirt block",
                byId(families.plainDirtBlock().stream().toList()),
                block);
        check(
                errors,
                "connection template",
                byId(List.copyOf(families.freeShapeTemplateIds())),
                id -> CustomConnectedBlockTemplateAsset.getAssetMap().getAsset(id) != null);
    }

    /** {@code ids} keyed by themselves, for {@link #check} of a plain list. */
    static Map<String, String> byId(List<String> ids) {
        Map<String, String> out = new LinkedHashMap<>();
        ids.forEach(id -> out.put(id, id));
        return out;
    }
}
