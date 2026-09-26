package dev.hycolony.core.citizen;

import java.util.Map;

/** The 11 citizen skills, in MineColonies order. */
public enum Skill {
    Athletics,
    Dexterity,
    Strength,
    Agility,
    Stamina,
    Mana,
    Adaptability,
    Focus,
    Creativity,
    Knowledge,
    Intelligence;

    /** MC Skill.getComplement: each skill but Intelligence has one. */
    private static final Map<Skill, Skill> COMPLEMENTARY = Map.of(
            Athletics, Strength,
            Dexterity, Agility,
            Strength, Athletics,
            Agility, Dexterity,
            Stamina, Knowledge,
            Mana, Focus,
            Adaptability, Creativity,
            Focus, Mana,
            Creativity, Adaptability,
            Knowledge, Stamina);

    /** MC Skill.getAdverse: each skill but Intelligence has one. */
    private static final Map<Skill, Skill> ADVERSE = Map.of(
            Athletics, Dexterity,
            Dexterity, Athletics,
            Strength, Agility,
            Agility, Strength,
            Stamina, Mana,
            Mana, Stamina,
            Adaptability, Focus,
            Focus, Adaptability,
            Creativity, Knowledge,
            Knowledge, Creativity);

    /** Null for Intelligence. */
    public Skill complementary() {
        return COMPLEMENTARY.get(this);
    }

    /** Null for Intelligence. */
    public Skill adverse() {
        return ADVERSE.get(this);
    }
}
