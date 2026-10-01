package dev.hycolony.core.app.hut;

import dev.hycolony.core.app.ui.CitizenRow;
import dev.hycolony.core.citizen.Skill;
import dev.hycolony.core.job.HiringMode;
import java.util.List;
import java.util.Optional;

/**
 * A worker hut's hire window (MC WindowHireWorker): its job, its hiring mode, whether it is full, the job's primary
 * and secondary skills, and every adult citizen in MC's order (employed here, unemployed, other jobs, not assignable,
 * then home distance rounded to 40 blocks, then name).
 */
public record HireView(
        String jobId, HiringMode mode, int workers, boolean full, Skill primary, Skill secondary, List<Candidate> all) {
    public HireView {
        all = List.copyOf(all);
    }

    /**
     * A citizen: its job, whether it works {@code here}, whether the hut's module may take it ({@code assignable}, MC
     * WorkerBuildingModuleView.canAssign), where it lives and its skills, the job's primary first then its secondary.
     */
    public record Candidate(
            int citizenId,
            String name,
            Optional<String> jobId,
            boolean here,
            boolean assignable,
            HomeLine home,
            int homeDistance,
            List<CitizenRow.SkillLevel> skills) {
        public Candidate {
            skills = List.copyOf(skills);
        }
    }

    /** MC hiring labels: homeless, lives here, lives at its current workplace, lives N blocks from here. */
    public enum HomeLine {
        HOMELESS,
        LIVES_HERE,
        LIVES_AT_WORK,
        DISTANCE
    }

    /** The button of a candidate's row. */
    public enum Button {
        HIRE,
        FIRE,
        NONE
    }

    /** MC updateCitizens: the assignable citizens, or every adult when "Show employed?" is on. */
    public List<Candidate> listed(boolean showEmployed) {
        return all.stream().filter(c -> showEmployed || c.assignable()).toList();
    }

    /**
     * MC updateElement: Hire for a listed citizen not working here while the hut has room, nothing while it is full,
     * Fire for one working here.
     */
    public Button button(Candidate c, boolean showEmployed) {
        if (c.here()) {
            return Button.FIRE;
        }
        return (showEmployed || c.assignable()) && !full ? Button.HIRE : Button.NONE;
    }
}
