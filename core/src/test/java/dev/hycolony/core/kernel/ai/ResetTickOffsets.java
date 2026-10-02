package dev.hycolony.core.kernel.ai;

import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Resets {@link TickingTransition}'s shared tick offset before every test (registered for all tests through
 * {@code META-INF/services}): each AI's first checks then fall on the same ticks whatever tests ran before, so no test
 * depends on how many AIs earlier tests built.
 */
public final class ResetTickOffsets implements BeforeEachCallback {
    @Override
    public void beforeEach(ExtensionContext context) {
        TickingTransition.resetOffsetVariant();
    }
}
