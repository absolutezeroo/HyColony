package dev.hyangler.api;

/**
 * A caught fish's rarity: Hytale's item qualities, whose states a fish item carries ({@code *Fish_X_Item_State_Rare}).
 *
 * @since 1.0
 */
public enum Rarity {
    COMMON("Common"),
    UNCOMMON("Uncommon"),
    RARE("Rare"),
    EPIC("Epic"),
    LEGENDARY("Legendary");

    private final String hytaleName;

    Rarity(String hytaleName) {
        this.hytaleName = hytaleName;
    }

    /** The Hytale quality's name, as written in a state's id. */
    public String hytaleName() {
        return hytaleName;
    }
}
