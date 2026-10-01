package dev.hycolony.core.app.action;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.app.ui.CitizenView;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.citizen.Skill;
import dev.hycolony.core.citizen.Skills;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.permission.Permissions;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.testing.TestContexts;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** MC AdjustSkillCitizenMessage: the citizen window's + and - buttons, for a player in creative mode. */
class CitizenSkillActionsTest {
    private final TestContexts t = new TestContexts();
    private final UUID alice = UUID.randomUUID();
    private final UUID carol = UUID.randomUUID();
    private final ColonyManager manager = t.manager();
    private final Colony colony;
    private final CitizenData ann;

    CitizenSkillActionsTest() {
        manager.foundation().begin(alice, "Alice", new BlockPos(0, 64, 0), 0);
        colony = manager.foundation().confirm(alice, "A").orElseThrow();
        assertTrue(manager.administration().setRank(alice, colony.id(), carol, "Carol", Permissions.FRIEND));
        ann = new CitizenData(1);
        colony.citizens().restore(ann);
        ann.skills().set(Skill.Mana, 5, 0);
    }

    private boolean adjust(UUID player, int delta) {
        return new CitizenSkillActions(manager).adjust(player, colony.id(), ann.id(), Skill.Mana, delta);
    }

    @Test
    void aCreativeManagerRaisesAndLowersALevelAndTheWindowShowsAgain() {
        t.players.creative.add(alice);
        colony.clearDirty();
        assertTrue(adjust(alice, 1));
        assertEquals(6, ann.skills().level(Skill.Mana));
        assertTrue(colony.isDirty());
        assertTrue(t.ui.shown.get(alice) instanceof CitizenView);
        assertTrue(adjust(alice, -1));
        assertEquals(5, ann.skills().level(Skill.Mana));
    }

    @Test
    void outsideCreativeNothingChanges() {
        assertFalse(adjust(alice, 1));
        assertEquals(5, ann.skills().level(Skill.Mana));
    }

    @Test
    void aCreativeFriendMayNotManageAndIsToldAsMc() {
        t.players.creative.add(carol);
        assertFalse(adjust(carol, 1));
        assertEquals(5, ann.skills().level(Skill.Mana));
        assertEquals(
                "hycolony.permission.toolDenied",
                t.notifier.sent.getLast().msg().key(),
                "MC AbstractColonyServerMessage: the right is checked, and refused aloud, before creative mode");
    }

    @Test
    void aFriendOutsideCreativeIsToldTooAsMcChecksTheRightFirst() {
        assertFalse(adjust(carol, 1));
        assertEquals(
                "hycolony.permission.toolDenied",
                t.notifier.sent.getLast().msg().key());
    }

    @Test
    void theLevelStaysBetweenOneAndTheMaximumAsMc() {
        t.players.creative.add(alice);
        ann.skills().set(Skill.Mana, 1, 0);
        adjust(alice, -1);
        assertEquals(1, ann.skills().level(Skill.Mana));
        ann.skills().set(Skill.Mana, Skills.MAX_CITIZEN_LEVEL, 0);
        adjust(alice, 1);
        assertEquals(Skills.MAX_CITIZEN_LEVEL, ann.skills().level(Skill.Mana));
    }

    @Test
    void anUnknownColonyOrCitizenChangesNothing() {
        t.players.creative.add(alice);
        CitizenSkillActions actions = new CitizenSkillActions(manager);
        assertFalse(actions.adjust(alice, colony.id() + 1, ann.id(), Skill.Mana, 1));
        assertFalse(actions.adjust(alice, colony.id(), 99, Skill.Mana, 1));
        assertEquals(5, ann.skills().level(Skill.Mana));
    }
}
