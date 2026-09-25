package dev.hycolony.core.job;

import dev.hycolony.core.citizen.CitizenData;
import java.util.function.Function;

/** Registry entry: a job's id and how to create one for a citizen. */
public record JobType(String id, Function<CitizenData, Job> factory) {}
