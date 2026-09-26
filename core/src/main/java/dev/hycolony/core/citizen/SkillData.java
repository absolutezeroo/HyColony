package dev.hycolony.core.citizen;

public final class SkillData {
    private int level;
    private double experience;

    public SkillData(int level, double experience) {
        this.level = level;
        this.experience = experience;
    }

    public int level() {
        return level;
    }

    public double experience() {
        return experience;
    }

    void setLevel(int level) {
        this.level = level;
    }

    void setExperience(double experience) {
        this.experience = experience;
    }
}
