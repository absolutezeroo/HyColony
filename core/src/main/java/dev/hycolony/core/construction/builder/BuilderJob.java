package dev.hycolony.core.construction.builder;

import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.job.IdleAI;
import dev.hycolony.core.job.Job;
import dev.hycolony.core.job.JobAI;
import dev.hycolony.core.job.JobType;
import dev.hycolony.core.kernel.port.BodyId;

/** MineColonies JobBuilder. Its progress lives in the work order; its tools' wear lives on their stacks. */
public final class BuilderJob extends Job {
    public static final JobType TYPE = new JobType("hycolony:builder", BuilderJob::new);

    public BuilderJob(CitizenData citizen) {
        super(TYPE, citizen);
    }

    @Override
    public JobAI createAI(Colony colony, BodyId body) {
        return BuilderContext.of(colony, this, body).<JobAI>map(BuilderAI::new).orElseGet(IdleAI::new);
    }
}
