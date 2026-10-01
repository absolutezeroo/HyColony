package dev.hycolony.core.citizen.happiness;

import java.util.Set;

/** The happiness modifiers' ids (MC HappinessConstants), also the tails of their translation keys. */
public final class HappinessIds {
    public static final String HOMELESSNESS = "homelessness";
    public static final String UNEMPLOYMENT = "unemployment";
    public static final String HEALTH = "health";
    public static final String IDLEATJOB = "idleatjob";
    public static final String SCHOOL = "school";
    public static final String MYSTICAL_SITE = "mysticalsite";
    public static final String SECURITY = "security";
    public static final String SOCIAL = "social";
    public static final String DAMAGE = "damage";
    public static final String DEATH = "death";
    public static final String RAIDWITHOUTDEATH = "raidwithoutdeath";
    public static final String SLEPTTONIGHT = "slepttonight";
    public static final String QUEST = "quest";
    public static final String FOOD = "food";
    public static final String HADGREATFOOD = "greatfood";

    /** MC VALID_HAPPINESS_MODIFIERS: the ids a save may hold. */
    public static final Set<String> VALID = Set.of(
            HOMELESSNESS,
            UNEMPLOYMENT,
            HEALTH,
            IDLEATJOB,
            SCHOOL,
            MYSTICAL_SITE,
            SECURITY,
            SOCIAL,
            DAMAGE,
            DEATH,
            RAIDWITHOUTDEATH,
            SLEPTTONIGHT,
            QUEST,
            FOOD,
            HADGREATFOOD);

    private HappinessIds() {}
}
