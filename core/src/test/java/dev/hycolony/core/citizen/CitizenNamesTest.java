package dev.hycolony.core.citizen;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import org.junit.jupiter.api.Test;

class CitizenNamesTest {
    @Test
    void westernNameHasFirstInitialAndSurname() {
        CitizenNames names = CitizenNames.loadDefault();
        String name = names.generate(new Random(7), Gender.FEMALE);
        assertTrue(name.matches("\\p{L}+ [A-Z]\\. \\p{L}+"), name);
    }
}
