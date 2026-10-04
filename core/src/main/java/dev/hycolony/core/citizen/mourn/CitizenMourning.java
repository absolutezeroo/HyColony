package dev.hycolony.core.citizen.mourn;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * A citizen's mourning (MC CitizenMournHandler): the deceased it grieves for, and whether it mourns today. Saved with
 * the citizen.
 */
public final class CitizenMourning {
    private final Set<String> deceased = new LinkedHashSet<>();
    private boolean mourning;

    /** MC addDeceasedCitizen: it will grieve for {@code name}. */
    public void addDeceased(String name) {
        deceased.add(name);
    }

    /** MC getDeceasedCitizens: the names it grieves for, first death first. */
    public Set<String> deceased() {
        return Collections.unmodifiableSet(deceased);
    }

    /** MC shouldMourn: someone it grieves for died. */
    public boolean shouldMourn() {
        return !deceased.isEmpty();
    }

    /** MC isMourning: it mourns today (no work). */
    public boolean isMourning() {
        return mourning;
    }

    /**
     * MC CitizenManager.onWakeUp: a day of mourning ends, forgetting its deceased; else it starts when someone it
     * grieves for died.
     */
    public void onWakeUp() {
        if (mourning) {
            deceased.clear();
            mourning = false;
        } else if (shouldMourn()) {
            mourning = true;
        }
    }

    /** Restores a saved mourning (MC read). */
    public void restore(Set<String> names, boolean mourns) {
        deceased.clear();
        deceased.addAll(names);
        mourning = mourns;
    }
}
