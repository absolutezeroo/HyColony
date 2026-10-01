package dev.hycolony.plugin.ui.citizen;

import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.app.action.CitizenSkillActions;
import dev.hycolony.core.app.ui.CitizenView;
import dev.hycolony.core.app.ui.CitizenView.SkillRow;
import dev.hycolony.core.citizen.Skill;
import dev.hycolony.plugin.ui.ColonyPage;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * The Main page's skills (MC main.xml and CitizenWindowUtils.createSkillContent): name, small icon and level, + and -
 * shown while the icon is hovered (MC onHoverId) and active in creative mode (MC AdjustSkillCitizenMessage). Kept at
 * the user's request: the job's skills first in bold, an XP bar and "XP x / y" over the level.
 *
 * <p>Deviation from MC: the hover is told to the server (MouseEntered and MouseExited events), so + and - appear after
 * a round trip; moving from the icon onto them sends an exit then an entry, which leaves them shown.
 */
final class CitizenSkillLines {
    private static final String LIST = "#MainPage #Skills";
    private static final String SHOW = "skillShow";
    private static final String HIDE = "skillHide";

    private final ColonyManager manager;
    private final UUID player;
    private final CitizenView view;

    CitizenSkillLines(ColonyManager manager, UUID player, CitizenView view) {
        this.manager = manager;
        this.player = player;
        this.view = view;
    }

    void render(UICommandBuilder ui, UIEventBuilder events) {
        List<SkillRow> skills = view.skills();
        for (int i = 0; i < skills.size(); i++) {
            SkillRow r = skills.get(i);
            String row = LIST + "[" + i + "]";
            String skill = r.skill().name();
            ui.append(LIST, "Pages/HyColony/Mc/SkillLine.ui");
            ui.set(row + " #Name.Text", Message.translation("hycolony.ui.skill." + skill.toLowerCase(Locale.ROOT)));
            ui.set(row + " #Name.Style.RenderBold", r.jobSkill());
            ui.set(row + " #Icon #Icons #" + skill + ".Visible", true);
            ui.set(row + " #Level.Text", String.valueOf(r.level()));
            ui.set(row + " #Level.TooltipText", xp(r));
            ui.set(row + " #Xp.Value", r.progress());
            for (String hover : new String[] {" #Hover", " #Plus", " #Minus"}) {
                bind(events, CustomUIEventBindingType.MouseEntered, row + hover, SHOW, skill);
                bind(events, CustomUIEventBindingType.MouseExited, row + hover, HIDE, skill);
            }
            if (view.creative()) {
                ColonyPage.bindRef(events, row + " #Plus", "skillPlus", skill);
                ColonyPage.bindRef(events, row + " #Minus", "skillMinus", skill);
            } else {
                ui.set(row + " #Plus.Disabled", true);
                ui.set(row + " #Minus.Disabled", true);
            }
        }
    }

    private static Message xp(SkillRow r) {
        return r.maxed()
                ? Message.translation("hycolony.ui.citizen.skillXpMax")
                : Message.translation("hycolony.ui.citizen.skillXp")
                        .param("p0", String.valueOf(r.xp()))
                        .param("p1", String.valueOf(r.xpForNextLevel()));
    }

    private static void bind(
            UIEventBuilder events, CustomUIEventBindingType type, String selector, String action, String ref) {
        events.addEventBinding(type, selector, EventData.of("Action", action).append("Ref", ref), false);
    }

    /**
     * Handles a skill event: + or - goes to the core (which shows the window again), a hover returns the update that
     * shows or hides that skill's buttons; empty for any other event or an unknown skill.
     */
    Optional<UICommandBuilder> handle(ColonyPage.Act act) {
        int index = indexOf(act.ref());
        if (index < 0) {
            return Optional.empty();
        }
        Skill skill = view.skills().get(index).skill();
        CitizenSkillActions skills = new CitizenSkillActions(manager);
        switch (act.action()) {
            case "skillPlus" -> skills.adjust(player, view.colonyId(), view.citizenId(), skill, 1);
            case "skillMinus" -> skills.adjust(player, view.colonyId(), view.citizenId(), skill, -1);
            case SHOW, HIDE -> {
                UICommandBuilder ui = new UICommandBuilder();
                ui.set(LIST + "[" + index + "] #Buttons.Visible", SHOW.equals(act.action()));
                return Optional.of(ui);
            }
            default -> {}
        }
        return Optional.empty();
    }

    private int indexOf(String skill) {
        List<SkillRow> skills = view.skills();
        for (int i = 0; i < skills.size(); i++) {
            if (skills.get(i).skill().name().equals(skill)) {
                return i;
            }
        }
        return -1;
    }
}
