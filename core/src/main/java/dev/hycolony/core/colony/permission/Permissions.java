package dev.hycolony.core.colony.permission;

import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Colony ranks and members. Default ranks replicate MineColonies' cascade. */
public final class Permissions {
    public static final int OWNER = 0, OFFICER = 1, FRIEND = 2, NEUTRAL = 3, HOSTILE = 4;

    public record Member(String name, int rankId) {}

    /** MC Permissions.OP_RANK: what a player bypassing the colony's permissions may do, whatever the colony. */
    private static final Set<Action> OP_RANK = EnumSet.of(
            Action.ACCESS_HUTS,
            Action.USE_SCAN_TOOL,
            Action.TOSS_ITEM,
            Action.PICKUP_ITEM,
            Action.RIGHTCLICK_BLOCK,
            Action.RIGHTCLICK_ENTITY,
            Action.THROW_POTION,
            Action.SHOOT_ARROW,
            Action.ATTACK_CITIZEN,
            Action.ATTACK_ENTITY,
            Action.TELEPORT_TO_COLONY,
            Action.ACCESS_TOGGLEABLES,
            Action.PLACE_HUTS,
            Action.BREAK_HUTS,
            Action.MANAGE_HUTS,
            Action.PLACE_BLOCKS,
            Action.BREAK_BLOCKS,
            Action.FILL_BUCKET,
            Action.OPEN_CONTAINER,
            Action.RALLY_GUARDS,
            Action.MAP_BORDER);

    private final UUID owner;
    private String ownerName;
    private final Map<Integer, Rank> ranks;
    private final Map<UUID, Member> members;
    /** Not saved: who was told of a refusal lately, for this colony only (MC has one event handler per colony). */
    private final DenialNotices denials = new DenialNotices();

    private final PermissionEvents events = new PermissionEvents();

    private Permissions(UUID owner, String ownerName, Map<Integer, Rank> ranks, Map<UUID, Member> members) {
        this.owner = owner;
        this.ownerName = ownerName;
        this.ranks = ranks;
        this.members = members;
    }

    /** When this colony tells a refused player again; see {@link DenialNotices}. */
    public DenialNotices denials() {
        return denials;
    }

    /** The refused actions logged for the town hall's Permissions tab (MC permission events). */
    public PermissionEvents events() {
        return events;
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

    private static final Action[] OFFICER_ACTIONS = {
        Action.PLACE_HUTS,
        Action.BREAK_HUTS,
        Action.MANAGE_HUTS,
        Action.RECEIVE_MESSAGES,
        Action.PLACE_BLOCKS,
        Action.BREAK_BLOCKS,
        Action.FILL_BUCKET,
        Action.OPEN_CONTAINER,
        Action.RALLY_GUARDS,
        Action.MAP_BORDER,
        Action.MAP_DEATHS
    };
    private static final Action[] FRIEND_ACTIONS = {
        Action.ACCESS_HUTS,
        Action.USE_SCAN_TOOL,
        Action.TOSS_ITEM,
        Action.PICKUP_ITEM,
        Action.RIGHTCLICK_BLOCK,
        Action.RIGHTCLICK_ENTITY,
        Action.THROW_POTION,
        Action.SHOOT_ARROW,
        Action.ATTACK_CITIZEN,
        Action.ATTACK_ENTITY,
        Action.TELEPORT_TO_COLONY,
        Action.ACCESS_TOGGLEABLES,
        Action.MAP_BORDER
    };

    /** Same fall-through as MineColonies Permissions: OWNER ⊃ OFFICER ⊃ FRIEND ⊃ NEUTRAL; HOSTILE apart. */
    private static Rank defaultRank(int id) {
        Rank rank = new Rank(id, defaultName(id), 0L, true);
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
            addAll(rank, OFFICER_ACTIONS);
            rank.setColonyManager(true);
        }
        if (id <= FRIEND) {
            addAll(rank, FRIEND_ACTIONS);
        }
        rank.add(Action.ACCESS_TOGGLEABLES);
        rank.add(Action.MAP_BORDER);
        return rank;
    }

    private static String defaultName(int id) {
        return switch (id) {
            case OWNER -> "Owner";
            case OFFICER -> "Officer";
            case FRIEND -> "Friend";
            case NEUTRAL -> "Neutral";
            default -> "Hostile";
        };
    }

    private static void addAll(Rank rank, Action... actions) {
        for (Action a : actions) {
            rank.add(a);
        }
    }

    public UUID owner() {
        return owner;
    }

    public String ownerName() {
        return ownerName;
    }

    public Rank rankOf(UUID player) {
        Member member = members.get(player);
        // Every default rank exists: createDefault maps them all and PermissionsSerializer.read starts from them.
        Rank neutral = Objects.requireNonNull(ranks.get(NEUTRAL), "neutral rank");
        return member != null ? ranks.getOrDefault(member.rankId(), neutral) : neutral;
    }

    public boolean hasPermission(UUID player, Action action) {
        return hasPermission(rankOf(player), action);
    }

    /** MC hasPermission(Rank, Action): the rank's flag, but the neutral rank never edits permissions nor teleports. */
    public boolean hasPermission(Rank rank, Action action) {
        if (rank.id() == NEUTRAL && (action == Action.EDIT_PERMISSIONS || action == Action.TELEPORT_TO_COLONY)) {
            return false;
        }
        return rank.has(action);
    }

    /**
     * MC canAlterPermission: only the owner alters the owner's rank; the actor needs EDIT_PERMISSIONS and may not take
     * from its own rank EDIT_PERMISSIONS, MANAGE_HUTS nor ACCESS_HUTS.
     */
    public boolean canAlterPermission(Rank actor, Rank rank, Action action) {
        if (rank.id() == OWNER && actor.id() != OWNER) {
            return false;
        }
        boolean ownKeyRight = actor.id() == rank.id()
                && (action == Action.EDIT_PERMISSIONS || action == Action.MANAGE_HUTS || action == Action.ACCESS_HUTS);
        return hasPermission(actor, Action.EDIT_PERMISSIONS) && !ownKeyRight;
    }

    /** MC alterPermission: sets or clears {@code action} on {@code rank} if {@link #canAlterPermission}; true if so. */
    public boolean alterPermission(Rank actor, Rank rank, Action action, boolean enable) {
        if (!canAlterPermission(actor, rank, action)) {
            return false;
        }
        if (enable) {
            rank.add(action);
        } else {
            rank.remove(action);
        }
        return true;
    }

    /** MC addRank: a rank without rights, at the first free id from the hostile rank's on. */
    public Rank addRank(String name) {
        int id = HOSTILE;
        while (ranks.containsKey(id)) {
            id++;
        }
        Rank rank = new Rank(id, name, 0L, false);
        ranks.put(id, rank);
        return rank;
    }

    /** MC removeRank: a rank that is not initial goes, its players become neutral; false otherwise. */
    public boolean removeRank(int rankId) {
        Rank rank = ranks.get(rankId);
        if (rank == null || rank.isInitial()) {
            return false;
        }
        members.replaceAll((id, m) -> m.rankId() == rankId ? new Member(m.name(), NEUTRAL) : m);
        ranks.remove(rankId);
        return true;
    }

    /** MC EditRankType: makes the rank a colony manager, hostile, or neither; false for an unknown rank. */
    public boolean setRankType(int rankId, RankType type) {
        Rank rank = ranks.get(rankId);
        if (rank == null) {
            return false;
        }
        rank.setColonyManager(type == RankType.COLONY_MANAGER);
        rank.setHostile(type == RankType.HOSTILE);
        return true;
    }

    /**
     * MC addPlayer: puts the player at {@code rankId}, replacing its rank. Never the owner (MC's by-name addPlayer
     * refuses it; Deviation from MC: its by-UUID one does not) nor an unknown rank.
     */
    public boolean addPlayer(UUID player, String name, int rankId) {
        if (player.equals(owner) || !ranks.containsKey(rankId)) {
            return false;
        }
        members.put(player, new Member(name, rankId));
        return true;
    }

    /** MC removePlayer: a member leaves the colony's list; never the owner. */
    public boolean removePlayer(UUID player) {
        Member m = members.get(player);
        if (m == null || m.rankId() == OWNER) {
            return false;
        }
        members.remove(player);
        return true;
    }

    /** MC hasPermission(OP_RANK, action): whether a player bypassing the permissions may do {@code action}. */
    public static boolean operatorRankHas(Action action) {
        return OP_RANK.contains(action);
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

    public Map<Integer, Rank> ranks() {
        return Collections.unmodifiableMap(ranks);
    }

    public Map<UUID, Member> members() {
        return Collections.unmodifiableMap(members);
    }
}
