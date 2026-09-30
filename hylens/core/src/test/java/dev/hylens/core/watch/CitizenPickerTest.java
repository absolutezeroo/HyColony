package dev.hylens.core.watch;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.hycolony.api.CitizenRef;
import dev.hycolony.api.ColonyRef;
import dev.hycolony.api.read.CitizenSnapshot;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Which citizen /hylens watch names (spec 2026-09-30, § 6.1). */
class CitizenPickerTest {
    private static final ColonyRef COLONY = new ColonyRef("default", 1);

    private static CitizenSnapshot citizen(int id, String name) {
        return new CitizenSnapshot(
                new CitizenRef(COLONY, id),
                name,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty());
    }

    private final CitizenSnapshot ann = citizen(1, "Ann Miller");
    private final CitizenSnapshot anna = citizen(2, "Anna Smith");
    private final CitizenSnapshot bob = citizen(3, "Bob Stone");
    private final List<CitizenSnapshot> all = List.of(anna, bob, ann);

    @Test
    void fullNameIsFoundWhateverItsCase() {
        assertEquals(new CitizenPicker.Found(bob), CitizenPicker.pick(all, "bob STONE"));
    }

    @Test
    void fullNameWinsOverLongerNamesContainingIt() {
        List<CitizenSnapshot> citizens = List.of(citizen(1, "Ann"), citizen(2, "Anna"));

        assertEquals(new CitizenPicker.Found(citizens.getFirst()), CitizenPicker.pick(citizens, "ann"));
    }

    @Test
    void partOfOneNameIsFound() {
        assertEquals(new CitizenPicker.Found(bob), CitizenPicker.pick(all, "ston"));
    }

    @Test
    void partOfSeveralNamesListsThemSorted() {
        assertEquals(new CitizenPicker.Ambiguous(List.of("Ann Miller", "Anna Smith")), CitizenPicker.pick(all, "An"));
    }

    @Test
    void twoCitizensOfTheSameNameAreAmbiguous() {
        List<CitizenSnapshot> twins = List.of(citizen(1, "Ann"), citizen(2, "Ann"));

        assertEquals(new CitizenPicker.Ambiguous(List.of("Ann", "Ann")), CitizenPicker.pick(twins, "ann"));
    }

    @Test
    void unknownNameIsNotFound() {
        assertEquals(new CitizenPicker.NotFound(), CitizenPicker.pick(all, "carl"));
    }

    @Test
    void blankQueryIsNotFound() {
        assertEquals(new CitizenPicker.NotFound(), CitizenPicker.pick(all, "  "));
    }

    @Test
    void surroundingSpacesAreIgnored() {
        assertEquals(new CitizenPicker.Found(bob), CitizenPicker.pick(all, "  Bob Stone "));
    }
}
