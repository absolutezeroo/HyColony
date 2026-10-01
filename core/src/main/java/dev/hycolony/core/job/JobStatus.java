package dev.hycolony.core.job;

/** What a worker's job is up to, for its happiness (MC JobStatus): STUCK feeds the {@code idleatjob} factor. */
public enum JobStatus {
    /** Not working yet (MC IJob.initEntityValues), or between two decisions. */
    IDLE,
    WORKING,
    /** Unable to work: no tool, no field, hut not built (MC isIdleAtJob). */
    STUCK
}
