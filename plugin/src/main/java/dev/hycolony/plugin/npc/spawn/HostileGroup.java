package dev.hycolony.plugin.npc.spawn;

import com.hypixel.hytale.builtin.tagset.TagSetPlugin;
import com.hypixel.hytale.builtin.tagset.config.NPCGroup;
import dev.hycolony.plugin.IdMap;

/**
 * HyColony's hostile NPC group (id-map {@code npc.group.hostile}: vanilla Aggressive, Outlander, Scarak and a few named
 * roles), MC's Monster: what may not spawn in a colony, attacks citizens and is avoided by them.
 */
public final class HostileGroup {
    private final String groupId;

    public HostileGroup(String groupId) {
        this.groupId = groupId;
    }

    /** The group the id-map names {@code npc.group.hostile}. */
    public static HostileGroup of(IdMap ids) {
        return new HostileGroup(ids.npcs().group("npc.group.hostile"));
    }

    /** Whether NPC role {@code role} is in the group; throws if the group is not loaded (callers log it). */
    public boolean contains(int role) {
        int group = NPCGroup.getAssetMap().getIndex(groupId);
        if (group == Integer.MIN_VALUE) {
            throw new IllegalStateException("NPC group " + groupId + " is not loaded");
        }
        return TagSetPlugin.get(NPCGroup.class).tagInSet(group, role);
    }
}
