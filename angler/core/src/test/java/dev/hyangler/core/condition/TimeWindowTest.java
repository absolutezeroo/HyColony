package dev.hyangler.core.condition;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TimeWindowTest {

    @Test
    void aDayWindowHoldsItsStartButNotItsEnd() {
        TimeWindow day = new TimeWindow(5, 22);
        assertTrue(day.contains(5.0));
        assertTrue(day.contains(21.99));
        assertFalse(day.contains(22.0));
        assertFalse(day.contains(4.5));
    }

    @Test
    void aNightWindowPassesMidnight() {
        TimeWindow night = new TimeWindow(19, 6);
        assertTrue(night.contains(23.0));
        assertTrue(night.contains(0.0));
        assertTrue(night.contains(5.9));
        assertFalse(night.contains(12.0));
    }

    @Test
    void aWholeDayWindowHoldsEveryHour() {
        assertTrue(new TimeWindow(0, 24).contains(23.9));
        assertTrue(new TimeWindow(0, 24).contains(0.0));
    }
}
