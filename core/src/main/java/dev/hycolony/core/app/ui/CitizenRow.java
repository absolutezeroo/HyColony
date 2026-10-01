package dev.hycolony.core.app.ui;

import dev.hycolony.core.citizen.Gender;
import dev.hycolony.core.citizen.Skill;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * A citizen of the town hall's list (MC WindowCitizenPage): {@code jobId} is a job type id, empty without a job;
 * {@code status} an i18n key suffix ("idle", "wandering", "absent"...); {@code skills} every skill in {@link Skill}
 * order; {@code vitals} the selected citizen's short labels.
 */
public record CitizenRow(
        int id,
        String name,
        Gender gender,
        Optional<String> jobId,
        String status,
        List<SkillLevel> skills,
        Vitals vitals) {
    /** A skill and its level (MC tooltip "skill: level"). */
    public record SkillLevel(Skill skill, int level) {}

    /**
     * MC WindowCitizenPage's labels, truncated as MC casts them: health over its maximum, happiness over 10 and
     * saturation (MC writes it over 20, though it goes up to 60).
     */
    public record Vitals(int health, int maxHealth, int happiness, int saturation) {}

    public CitizenRow {
        skills = List.copyOf(skills);
    }

    /**
     * MC WindowCitizenPage.updateCitizens: true for an empty filter, or when the name or the job's shown name
     * ({@code jobName}, in the viewer's language) contains it, case ignored.
     */
    public boolean matches(String filter, String jobName) {
        String f = filter.toLowerCase(Locale.ROOT);
        return f.isEmpty()
                || name.toLowerCase(Locale.ROOT).contains(f)
                || jobName.toLowerCase(Locale.ROOT).contains(f);
    }
}
