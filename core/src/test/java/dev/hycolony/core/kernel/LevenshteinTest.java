package dev.hycolony.core.kernel;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class LevenshteinTest {
    @Test
    void distanceIsTheCaseSensitiveEditDistance() {
        assertEquals(3, Levenshtein.distance("kitten", "sitting"));
        assertEquals(5, Levenshtein.distance("Stone", ""));
        assertEquals(1, Levenshtein.distance("stone", "Stone"));
    }
}
