package dev.hycolony.core.testing;

import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.job.Job;
import dev.hycolony.core.job.JobAI;
import dev.hycolony.core.job.JobType;
import dev.hycolony.core.kernel.port.BodyId;
import dev.hycolony.core.logistics.warehouse.CourierAssignmentModule;

/** A minimal job for tests; real jobs (builder, etc.) arrive with the construction task. */
public final class TestJobs {
    public static final JobType TYPE = new JobType("test:worker", c -> new TestJob(TestJobs.TYPE, c));
    /** A citizen with this job counts as a courier for the warehouse (the real courier job comes later). */
    public static final JobType COURIER =
            new JobType(CourierAssignmentModule.COURIER_JOB_ID, c -> new TestJob(TestJobs.COURIER, c));

    private TestJobs() {}

    private static final class TestJob extends Job {
        TestJob(JobType type, CitizenData citizen) {
            super(type, citizen);
        }

        @Override
        public JobAI createAI(Colony colony, BodyId body) {
            return new JobAI() {
                @Override
                public void tick() {}

                @Override
                public String stateName() {
                    return "working";
                }

                @Override
                public boolean canBeInterrupted() {
                    return true;
                }
            };
        }
    }
}
