package dev.hycolony.plugin;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.particle.config.ParticleSystem;
import com.hypixel.hytale.server.npc.NPCPlugin;
import dev.hycolony.core.citizen.Skill;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

/** Logical keys -> Hytale asset ids. The only place asset ids live (spec § 4.3). */
public final class IdMap {
    private record Data(
            Map<String, String> items,
            Map<String, String> blocks,
            Map<String, String> npcRoles,
            Map<String, String> skillIcons,
            List<String> fireworks,
            List<String> precipitationParticles,
            Map<String, String> speedEffects) {}

    private final Data data;

    private IdMap(Data data) {
        this.data = data;
    }

    /** The id-map read from {@code json}: the core's hycolony/id-map.json merged with the enabled packs' fragments. */
    public static IdMap of(JsonObject json) {
        return new IdMap(new Gson().fromJson(json, Data.class));
    }

    public String itemId(String key) {
        return require(data.items(), key);
    }

    public String blockId(String key) {
        return require(data.blocks(), key);
    }

    public String npcRole(String key) {
        return require(data.npcRoles(), key);
    }

    /** The item shown as {@code skill}'s icon in the citizen window. */
    public String skillIcon(Skill skill) {
        return require(data.skillIcons(), skill.name());
    }

    /** Particle systems fired when a building level rises. */
    public List<String> fireworks() {
        return List.copyOf(data.fireworks());
    }

    /** Weather particle systems that count as rain or snow (MC Level.isRaining). */
    public Set<String> precipitationParticles() {
        return Set.copyOf(precipitation());
    }

    /** Walking-speed factor -> infinite entity effect with that HorizontalSpeedMultiplier. */
    public Map<Double, String> speedEffects() {
        Map<Double, String> out = new HashMap<>();
        speeds().forEach((factor, id) -> out.put(Double.valueOf(factor), id));
        return Map.copyOf(out);
    }

    /** Absent from an older id-map file: no precipitation particle (never rains). */
    private List<String> precipitation() {
        return Objects.requireNonNullElse(data.precipitationParticles(), List.of());
    }

    /** Absent from an older id-map file: no speed effect (normal speed). */
    private Map<String, String> speeds() {
        return Objects.requireNonNullElse(data.speedEffects(), Map.of());
    }

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
        Predicate<String> item = id -> Item.getAssetMap().getAsset(id) != null;
        Predicate<String> particle = id -> ParticleSystem.getAssetMap().getAsset(id) != null;
        check(errors, "item", data.items(), item);
        check(errors, "block", data.blocks(), id -> BlockType.getAssetMap().getIndex(id) != Integer.MIN_VALUE);
        Map<String, String> icons = new LinkedHashMap<>();
        for (Skill skill : Skill.values()) {
            icons.put(skill.name(), data.skillIcons().get(skill.name()));
        }
        check(errors, "skill icon", icons, item);
        check(errors, "particle system", byId(data.fireworks()), particle);
        check(errors, "precipitation particle system", byId(precipitation()), particle);
        check(errors, "speed effect", speeds(), id -> EntityEffect.getAssetMap().getAsset(id) != null);
        check(errors, "npc role", data.npcRoles(), id -> NPCPlugin.get().hasRoleName(id));
        return errors;
    }

    /** Adds "what key -> id" to {@code errors} for each missing (or unknown) id. */
    private static void check(List<String> errors, String what, Map<String, String> ids, Predicate<String> exists) {
        ids.forEach((key, id) -> {
            if (id == null || !exists.test(id)) {
                errors.add(what + " " + key + " -> " + id);
            }
        });
    }

    private static Map<String, String> byId(List<String> ids) {
        Map<String, String> out = new LinkedHashMap<>();
        ids.forEach(id -> out.put(id, id));
        return out;
    }
}
