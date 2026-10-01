package dev.hycolony.core.crafting.restaurant;

import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.job.IdleAI;
import dev.hycolony.core.job.Job;
import dev.hycolony.core.job.JobAI;
import dev.hycolony.core.job.JobType;
import dev.hycolony.core.kernel.port.BodyId;

/**
 * MC JobCook, the dining hall's waiter ("Waiter"): it cooks the menu's raw food at the hall's campfires and serves the
 * customers ({@link CookAI}). No saturation factor of its own.
 */
public final class CookJob extends Job {
    public static final JobType TYPE = new JobType("hycolony:cook", CookJob::new);

    public CookJob(CitizenData citizen) {
        super(TYPE, citizen);
    }

    /** MC EntityAIWorkCook; without a dining hall to work at, an {@link IdleAI}. */
    @Override
    public JobAI createAI(Colony colony, BodyId body) {
        return CookWorkContext.of(colony, this, body).<JobAI>map(CookAI::new).orElseGet(IdleAI::new);
    }

    /** MC: {@code job == ModJobs.cook} in CitizenAI.shouldEat and EntityAIEatTask. */
    @Override
    public boolean servesFood() {
        return true;
    }
}
