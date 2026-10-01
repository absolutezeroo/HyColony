package dev.hycolony.core.app.ui;

import dev.hycolony.core.app.citizen.HappinessRows;
import dev.hycolony.core.app.ui.RequestsView.RequestRow;
import dev.hycolony.core.citizen.Gender;
import dev.hycolony.core.citizen.Skill;
import dev.hycolony.core.citizen.Skills;
import dev.hycolony.core.kernel.port.Msg;
import dev.hycolony.core.request.model.Requestable;
import java.util.List;
import java.util.Optional;

/**
 * MineColonies' citizen window (MainWindowCitizen, RequestWindowCitizen, JobWindowCitizen). {@code workBuilding} is a
 * building type id or custom name. {@code activity} is an i18n key suffix: "waitingFor" (with {@code waitingFor}, the
 * first open request), else "working", "wandering", "idle" or "absent"; {@code jobActivity} is the job AI's own line
 * (e.g. the builder's stage, block and action); with the job and workplace, they are kept at the user's request (MC's
 * window has no such lines). {@code health} is in MC points (20 for ten red hearts, see HealthBar), 20 without a
 * body as MC. {@code happiness} (0 to 10, MC getHappiness) feeds the smiley bar and {@code happinessRows} the
 * Happiness tab. {@code creative}: the viewer may raise or lower skills (MC AdjustSkillCitizenMessage). {@code
 * requests} are the citizen's requests in its workplace then the workplace's own, each followed by its children (MC
 * RequestWindowCitizen), with what the viewer holds of it. {@code skills} lists the job's primary and secondary
 * skills first, then the rest in MineColonies order. {@code jobSkills} feeds the Job tab and is empty for a citizen
 * without a workplace.
 */
public record CitizenView(
        int colonyId,
        int citizenId,
        String name,
        Optional<String> jobId,
        Optional<String> workBuilding,
        String activity,
        Optional<Requestable> waitingFor,
        Optional<Msg> jobActivity,
        int health,
        double saturation,
        double happiness,
        List<HappinessRows.Row> happinessRows,
        Gender gender,
        boolean creative,
        List<SkillRow> skills,
        List<RequestRow> requests,
        Optional<JobSkills> jobSkills) {
    public CitizenView {
        happinessRows = List.copyOf(happinessRows);
        skills = List.copyOf(skills);
        requests = List.copyOf(requests);
    }

    /**
     * One skill: its level, the whole XP earned in it and the XP its next level needs (MC ExperienceUtils, rounded).
     * {@code jobSkill} marks the job's primary or secondary skill. Both XP values are 0 at the maximum level.
     */
    public record SkillRow(Skill skill, int level, int xp, int xpForNextLevel, boolean jobSkill) {
        public boolean maxed() {
            return level >= Skills.MAX_CITIZEN_LEVEL;
        }

        /** Share of the bar toward the next level, in [0, 1]; full at the maximum level. */
        public float progress() {
            return maxed() || xpForNextLevel <= 0 ? 1f : Math.min(1f, (float) xp / xpForNextLevel);
        }
    }

    /**
     * MC CitizenWindowUtils.updateJobPage: the primary skill then its complementary and adverse skill, the same for the
     * secondary; the complementary and adverse lines are absent for Intelligence.
     */
    public record JobSkills(List<SkillShare> primary, List<SkillShare> secondary) {
        public JobSkills {
            primary = List.copyOf(primary);
            secondary = List.copyOf(secondary);
        }
    }

    /** A skill and the share of the job's XP it gets, in percent; negative for an adverse skill, which loses XP. */
    public record SkillShare(Skill skill, int xpPercent) {}
}
