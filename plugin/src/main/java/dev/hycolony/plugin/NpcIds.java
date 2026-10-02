package dev.hycolony.plugin;

import java.util.Map;

/**
 * The NPC ids of the id-map: roles ({@code npcRoles}) and groups of roles ({@code npcGroups}, Server/NPC/Groups).
 *
 * @param roles logical key -> role id
 * @param groups logical key -> NPC group id
 */
public record NpcIds(Map<String, String> roles, Map<String, String> groups) {
    public String role(String key) {
        return require(roles, key);
    }

    public String group(String key) {
        return require(groups, key);
    }

    private static String require(Map<String, String> map, String key) {
        String id = map.get(key);
        if (id == null) {
            throw new IllegalArgumentException("Unknown id-map key " + key);
        }
        return id;
    }
}
