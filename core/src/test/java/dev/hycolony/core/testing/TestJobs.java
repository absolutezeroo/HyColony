package dev.hycolony.core.testing;

import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.job.Job;
import dev.hycolony.core.job.JobAI;
import dev.hycolony.core.job.JobType;
import dev.hycolony.core.kernel.port.BodyId;
import dev.hycolony.core.logistics.warehouse.CourierAssignmentModule;
import dev.hycolony.core.logistics.warehouse.CourierTaskQueue;
import dev.hycolony.core.request.model.RequestToken;
import java.util.ArrayList;
import java.util.List;

/** A minimal job for tests; real jobs (builder, etc.) arrive with the construction task. */
public final class TestJobs {
    public static final JobType TYPE = new JobType("test:worker", c -> new TestJob(TestJobs.TYPE, c));
    /** A courier double with the real courier job id, whose task queue tests fill by hand. */
    public static final JobType COURIER = new JobType(CourierAssignmentModule.COURIER_JOB_ID, TestCourierJob::new);

    private TestJobs() {}

    /** A courier whose task queue tests fill by hand, as the real courier job does when it takes a task. */
    public static final class TestCourierJob extends TestJob implements CourierTaskQueue {
        public final List<RequestToken> tasks = new ArrayList<>();

        TestCourierJob(CitizenData citizen) {
            super(COURIER, citizen);
        }

        @Override
        public boolean removeTask(RequestToken token) {
            return tasks.remove(token);
        }
    }

    private static class TestJob extends Job {
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
