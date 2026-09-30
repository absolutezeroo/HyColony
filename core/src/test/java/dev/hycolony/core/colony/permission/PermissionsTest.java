package dev.hycolony.core.colony.permission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PermissionsTest {
    private final UUID owner = UUID.randomUUID();
    private final UUID stranger = UUID.randomUUID();
    private final Permissions perms = Permissions.createDefault(owner, "Alice");

    @Test
    void bitFlagsMatchMineColonies() {
        assertEquals(1L, Action.ACCESS_HUTS.mask());
        assertEquals(1L << 2, Action.PLACE_HUTS.mask());
        assertEquals(1L << 30, Action.ACCESS_TOGGLEABLES.mask());
        assertEquals(27, Action.values().length);
    }

    @Test
    void neutralIsDefaultAndVeryLimited() {
        assertEquals(Permissions.NEUTRAL, perms.rankOf(stranger).id());
        assertTrue(perms.hasPermission(stranger, Action.MAP_BORDER));
        assertTrue(perms.hasPermission(stranger, Action.ACCESS_TOGGLEABLES));
        assertFalse(perms.hasPermission(stranger, Action.ACCESS_HUTS));
        assertFalse(perms.hasPermission(stranger, Action.PLACE_BLOCKS));
    }

    @Test
    void ranksCascadeLikeMineColonies() {
        Rank friend = perms.ranks().get(Permissions.FRIEND);
        Rank officer = perms.ranks().get(Permissions.OFFICER);
        Rank ownerRank = perms.ranks().get(Permissions.OWNER);
        assertTrue(friend.has(Action.ACCESS_HUTS));
        assertFalse(friend.has(Action.PLACE_BLOCKS));
        assertTrue(officer.has(Action.PLACE_BLOCKS));
        assertTrue(officer.has(Action.ACCESS_HUTS));
        assertTrue(officer.isColonyManager());
        assertFalse(officer.has(Action.EDIT_PERMISSIONS));
        assertTrue(ownerRank.has(Action.EDIT_PERMISSIONS));
        assertTrue(ownerRank.has(Action.PLACE_HUTS));
        assertTrue(perms.ranks().get(Permissions.HOSTILE).isHostile());
        assertTrue(perms.ranks().get(Permissions.HOSTILE).has(Action.HURT_CITIZEN));
    }

    @Test
    void ownerHasOwnerRank() {
        assertEquals(Permissions.OWNER, perms.rankOf(owner).id());
        assertTrue(perms.isMember(owner));
    }

    @Test
    void setRankRejectsOwnerChanges() {
        UUID bob = UUID.randomUUID();
        assertTrue(perms.setRank(bob, "Bob", Permissions.OFFICER));
        assertEquals(Permissions.OFFICER, perms.rankOf(bob).id());
        assertTrue(perms.isMember(bob));
        assertFalse(perms.setRank(bob, "Bob", Permissions.OWNER));
        assertFalse(perms.setRank(owner, "Alice", Permissions.FRIEND));
        assertFalse(perms.setRank(bob, "Bob", 99));
    }

    /** CLAUDE.md § 5: a rank or member missing keys takes the defaults; one without its id or uuid is dropped. */
    @Test
    void ranksAndMembersMissingKeysLoadWithDefaults() {
        UUID owner = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        Permissions saved = PermissionsSerializer.read(
                JsonParser.parseString("""
                {"owner": "%s", "ownerName": "A",
                 "ranks": [{"id": 1}, {"name": "no id"}, "not a rank"],
                 "members": [{"uuid": "%s", "rank": 1}, {"name": "no uuid"}, {"uuid": "bad", "rank": 2}]}
                """.formatted(owner, bob)).getAsJsonObject());

        Rank officer = Permissions.createDefault(owner, "A").ranks().get(Permissions.OFFICER);
        Rank read = saved.ranks().get(Permissions.OFFICER);
        assertEquals(officer.name(), read.name());
        assertEquals(officer.permissions(), read.permissions());
        assertEquals(officer.isColonyManager(), read.isColonyManager());
        assertEquals(Permissions.OFFICER, saved.rankOf(bob).id());
        assertEquals(2, saved.members().size()); // the owner and bob
    }

    @Test
    void aSaveWithoutOwnerNameLoadsWithABlankName() {
        UUID owner = UUID.randomUUID();

        Permissions saved = PermissionsSerializer.read(
                JsonParser.parseString("{\"owner\": \"%s\"}".formatted(owner)).getAsJsonObject());

        assertEquals(owner, saved.owner());
        assertEquals("", saved.ownerName());
    }
}
