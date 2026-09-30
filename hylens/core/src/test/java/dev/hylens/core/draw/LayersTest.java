package dev.hylens.core.draw;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Which drawings an operator wants (spec 2026-09-30, § 6.4, layers). */
class LayersTest {
    @Test
    void everyLayerIsShownAtFirst() {
        for (Layers.Layer layer : Layers.Layer.values()) {
            assertTrue(Layers.ALL.shows(layer), layer.name());
        }
    }

    @Test
    void togglingHidesOnlyThatLayerThenShowsItAgain() {
        Layers noTarget = Layers.ALL.toggle(Layers.Layer.TARGET);

        assertFalse(noTarget.shows(Layers.Layer.TARGET));
        assertTrue(noTarget.shows(Layers.Layer.STOP));
        assertTrue(noTarget.shows(Layers.Layer.ZONE));
        assertTrue(noTarget.shows(Layers.Layer.ALERTS));
        assertTrue(noTarget.toggle(Layers.Layer.TARGET).shows(Layers.Layer.TARGET));
    }
}
