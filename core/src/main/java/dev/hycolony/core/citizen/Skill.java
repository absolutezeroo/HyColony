package dev.hycolony.core.citizen;

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

    public Skill complementary() {
        return switch (this) {
            case Athletics -> Strength;
            case Dexterity -> Agility;
            case Strength -> Athletics;
            case Agility -> Dexterity;
            case Stamina -> Knowledge;
            case Mana -> Focus;
            case Adaptability -> Creativity;
            case Focus -> Mana;
            case Creativity -> Adaptability;
            case Knowledge -> Stamina;
            case Intelligence -> null;
        };
    }

    public Skill adverse() {
        return switch (this) {
            case Athletics -> Dexterity;
            case Dexterity -> Athletics;
            case Strength -> Agility;
            case Agility -> Strength;
            case Stamina -> Mana;
            case Mana -> Stamina;
            case Adaptability -> Focus;
            case Focus -> Adaptability;
            case Creativity -> Knowledge;
            case Knowledge -> Creativity;
            case Intelligence -> null;
        };
    }
}
