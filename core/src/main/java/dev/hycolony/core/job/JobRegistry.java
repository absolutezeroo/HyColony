package dev.hycolony.core.job;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class JobRegistry {
    private final Map<String, JobType> byId = new LinkedHashMap<>();

    public void register(JobType type) {
        if (byId.putIfAbsent(type.id(), type) != null) {
            throw new IllegalArgumentException("Duplicate job type " + type.id());
        }
    }

    public Optional<JobType> byId(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    /** Every registered type, in registration order. */
    public List<JobType> all() {
        return List.copyOf(byId.values());
    }
}
