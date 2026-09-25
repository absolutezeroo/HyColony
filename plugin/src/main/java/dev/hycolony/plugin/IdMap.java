package dev.hycolony.plugin;

import com.google.gson.Gson;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.npc.NPCPlugin;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Logical keys -> Hytale asset ids. The only place asset ids live (spec § 4.3). */
public final class IdMap {
    private record Data(Map<String, String> items, Map<String, String> blocks, Map<String, String> npcRoles) {}

    private final Data data;

    private IdMap(Data data) {
        this.data = data;
    }

    public static IdMap loadBundled() {
        try (InputStream in = IdMap.class.getResourceAsStream("/hycolony/id-map.json")) {
            return new IdMap(new Gson().fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), Data.class));
        } catch (Exception e) {
            throw new IllegalStateException("Cannot read hycolony/id-map.json", e);
        }
    }

    public String itemId(String key) { return require(data.items(), key); }
    public String blockId(String key) { return require(data.blocks(), key); }
    public String npcRole(String key) { return require(data.npcRoles(), key); }

    private static String require(Map<String, String> map, String key) {
        String id = map.get(key);
        if (id == null) {
            throw new IllegalArgumentException("Unknown id-map key " + key);
        }
        return id;
    }

    /** Every mapped id must exist in the loaded assets. Returns human-readable errors. */
    public List<String> validate() {
        List<String> errors = new ArrayList<>();
        data.items().forEach((key, id) -> {
            if (Item.getAssetMap().getAsset(id) == null) {
                errors.add("item " + key + " -> " + id);
            }
        });
        data.blocks().forEach((key, id) -> {
            if (BlockType.getAssetMap().getIndex(id) == Integer.MIN_VALUE) {
                errors.add("block " + key + " -> " + id);
            }
        });
        data.npcRoles().forEach((key, id) -> {
            if (!NPCPlugin.get().hasRoleName(id)) {
                errors.add("npc role " + key + " -> " + id);
            }
        });
        return errors;
    }
}
