package dev.hycolony.core.colony;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Colony ranks and members. Default ranks replicate MineColonies' cascade. */
public final class Permissions {
    public static final int OWNER = 0, OFFICER = 1, FRIEND = 2, NEUTRAL = 3, HOSTILE = 4;

    public record Member(String name, int rankId) {}

    private final UUID owner;
    private String ownerName;
    private final Map<Integer, Rank> ranks;
    private final Map<UUID, Member> members;

    private Permissions(UUID owner, String ownerName, Map<Integer, Rank> ranks, Map<UUID, Member> members) {
        this.owner = owner;
        this.ownerName = ownerName;
        this.ranks = ranks;
        this.members = members;
    }

    public static Permissions createDefault(UUID owner, String ownerName) {
        Map<Integer, Rank> ranks = new LinkedHashMap<>();
        for (int id = OWNER; id <= HOSTILE; id++) {
            ranks.put(id, defaultRank(id));
        }
        Map<UUID, Member> members = new LinkedHashMap<>();
        members.put(owner, new Member(ownerName, OWNER));
        return new Permissions(owner, ownerName, ranks, members);
    }

    static Permissions restore(UUID owner, String ownerName, Map<Integer, Rank> ranks, Map<UUID, Member> members) {
        return new Permissions(owner, ownerName, new LinkedHashMap<>(ranks), new LinkedHashMap<>(members));
    }

    /** Same fall-through as MineColonies Permissions: OWNER ⊃ OFFICER ⊃ FRIEND ⊃ NEUTRAL; HOSTILE apart. */
    private static Rank defaultRank(int id) {
        String name = switch (id) {
            case OWNER -> "Owner";
            case OFFICER -> "Officer";
            case FRIEND -> "Friend";
            case NEUTRAL -> "Neutral";
            default -> "Hostile";
        };
        Rank rank = new Rank(id, name, 0L, true, false, false);
        if (id == HOSTILE) {
            rank.add(Action.HURT_CITIZEN);
            rank.add(Action.HURT_VISITOR);
            rank.add(Action.MAP_BORDER);
            rank.setHostile(true);
            return rank;
        }
        if (id <= OWNER) {
            rank.add(Action.EDIT_PERMISSIONS);
            rank.add(Action.MAP_BORDER);
            rank.add(Action.MAP_DEATHS);
        }
        if (id <= OFFICER) {
            for (Action a : new Action[] {Action.PLACE_HUTS, Action.BREAK_HUTS, Action.MANAGE_HUTS, Action.RECEIVE_MESSAGES,
                    Action.PLACE_BLOCKS, Action.BREAK_BLOCKS, Action.FILL_BUCKET, Action.OPEN_CONTAINER, Action.RALLY_GUARDS,
                    Action.MAP_BORDER, Action.MAP_DEATHS}) {
                rank.add(a);
            }
            rank.setColonyManager(true);
        }
        if (id <= FRIEND) {
            for (Action a : new Action[] {Action.ACCESS_HUTS, Action.USE_SCAN_TOOL, Action.TOSS_ITEM, Action.PICKUP_ITEM,
                    Action.RIGHTCLICK_BLOCK, Action.RIGHTCLICK_ENTITY, Action.THROW_POTION, Action.SHOOT_ARROW,
                    Action.ATTACK_CITIZEN, Action.ATTACK_ENTITY, Action.TELEPORT_TO_COLONY, Action.ACCESS_TOGGLEABLES,
                    Action.MAP_BORDER}) {
                rank.add(a);
            }
        }
        rank.add(Action.ACCESS_TOGGLEABLES);
        rank.add(Action.MAP_BORDER);
        return rank;
    }

    public UUID owner() { return owner; }
    public String ownerName() { return ownerName; }

    public Rank rankOf(UUID player) {
        Member member = members.get(player);
        return member != null ? ranks.getOrDefault(member.rankId(), ranks.get(NEUTRAL)) : ranks.get(NEUTRAL);
    }

    public boolean hasPermission(UUID player, Action action) {
        return rankOf(player).has(action);
    }

    /** A member = a player whose rank grants ACCESS_HUTS (Friend and above). */
    public boolean isMember(UUID player) {
        return rankOf(player).has(Action.ACCESS_HUTS);
    }

    /** Refuses to grant OWNER, to change the owner's rank, or to use an unknown rank. */
    public boolean setRank(UUID player, String name, int rankId) {
        if (rankId == OWNER || player.equals(owner) || !ranks.containsKey(rankId)) {
            return false;
        }
        members.put(player, new Member(name, rankId));
        return true;
    }

    public Map<Integer, Rank> ranks() { return Collections.unmodifiableMap(ranks); }
    public Map<UUID, Member> members() { return Collections.unmodifiableMap(members); }
}
