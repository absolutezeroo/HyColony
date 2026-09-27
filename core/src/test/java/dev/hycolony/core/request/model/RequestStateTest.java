package dev.hycolony.core.request.model;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RequestStateTest {
    @Test
    void isBeforeFollowsMineColoniesDeclarationOrder() {
        assertTrue(RequestState.FOLLOWUP_IN_PROGRESS.isBefore(RequestState.COMPLETED));
        assertTrue(RequestState.CREATED.isBefore(RequestState.IN_PROGRESS));
        assertFalse(RequestState.COMPLETED.isBefore(RequestState.COMPLETED));
        assertFalse(RequestState.CANCELLED.isBefore(RequestState.COMPLETED));
    }
}
