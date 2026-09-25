package dev.hycolony.core.citizen;

import java.util.EnumMap;
import java.util.Map;
import java.util.random.RandomGenerator;

/** Port of MineColonies' CitizenSkillHandler (level init + XP gain). */
public final class Skills {
    public static final int MAX_CITIZEN_LEVEL = 99;
    public static final int MAX_BUILDING_LEVEL = 5;

    private final EnumMap<Skill, SkillData> map = new EnumMap<>(Skill.class);

    private Skills() {}

    /** MineColonies init(levelCap): levels uniformly in [1, levelCap-1], or all 1 when levelCap <= 1. */
    public static Skills initRandom(int levelCap, RandomGenerator random) {
        Skills skills = new Skills();
        for (Skill skill : Skill.values()) {
            int level = levelCap <= 1 ? 1 : random.nextInt(levelCap - 1) + 1;
            skills.map.put(skill, new SkillData(level, 0.0));
        }
        return skills;
    }

    public static Skills empty() {
        Skills skills = new Skills();
        for (Skill skill : Skill.values()) {
            skills.map.put(skill, new SkillData(1, 0.0));
        }
        return skills;
    }

    public int level(Skill skill) { return map.get(skill).level(); }
    public double experience(Skill skill) { return map.get(skill).experience(); }
    public Map<Skill, SkillData> view() { return java.util.Collections.unmodifiableMap(map); }

    public void set(Skill skill, int level, double experience) {
        map.put(skill, new SkillData(Math.max(1, Math.min(level, MAX_CITIZEN_LEVEL)), experience));
    }

    /**
     * Port of addXpToSkill. {@code homeLevel}/{@code homeMaxLevel}: citizen's home building level and max
     * level (0 and MAX_BUILDING_LEVEL when homeless). Returns true when the skill leveled up.
     */
    public boolean addXp(Skill skill, double xp, int homeLevel, int homeMaxLevel) {
        SkillData data = map.get(skill);
        if (((homeLevel < homeMaxLevel || homeMaxLevel < MAX_BUILDING_LEVEL) && (homeLevel + 1) * 10 <= data.level())
                || data.level() >= MAX_CITIZEN_LEVEL) {
            return false;
        }
        int originalLevel = data.level();
        double xpToLevelUp = Math.min(Double.MAX_VALUE, data.experience() + xp);
        while (xpToLevelUp > 0 && data.level() < MAX_CITIZEN_LEVEL) {
            double next = Experience.xpNeededForNextLevel(data.level());
            if (next > xpToLevelUp) {
                break;
            }
            xpToLevelUp -= next;
            data.setLevel(data.level() + 1);
        }
        data.setExperience(data.level() >= MAX_CITIZEN_LEVEL ? 0 : xpToLevelUp);
        return data.level() > originalLevel;
    }

    /**
     * Port of removeXpFromSkill: borrows across levels (de-leveling as it goes), floors at level 1, and
     * experience never goes negative.
     */
    public void removeXp(Skill skill, double xp) {
        SkillData data = map.get(skill);
        double xpToRemove = xp;
        while (xpToRemove > 0) {
            if (data.experience() >= xpToRemove || data.level() <= 1) {
                data.setExperience(Math.max(0, data.experience() - xpToRemove));
                break;
            }
            xpToRemove -= data.experience();
            data.setExperience(Experience.xpNeededForNextLevel(data.level() - 1));
            data.setLevel(data.level() - 1);
        }
    }
}
