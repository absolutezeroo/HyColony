package dev.hycolony.core.testing;

import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.job.Job;
import dev.hycolony.core.job.JobAI;
import dev.hycolony.core.job.JobType;
import dev.hycolony.core.kernel.port.BodyId;

/** A minimal job for tests; real jobs (builder, etc.) arrive with the construction task. */
public final class TestJobs {
    public static final JobType TYPE = new JobType("test:worker", TestJob::new);

    private TestJobs() {}

    private static final class TestJob extends Job {
        TestJob(CitizenData citizen) { super(TYPE, citizen); }

        @Override
        public JobAI createAI(Colony colony, BodyId body) {
            return new JobAI() {
                @Override public void tick() {}
                @Override public String stateName() { return "working"; }
                @Override public boolean canBeInterrupted() { return true; }
            };
        }
    }
}
