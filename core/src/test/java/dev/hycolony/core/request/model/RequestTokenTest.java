package dev.hycolony.core.request.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The one JSON codec of request tokens, shared by the request saves and the jobs' task queues. */
class RequestTokenTest {
    private static final RequestToken A = new RequestToken(new UUID(0, 1));
    private static final RequestToken B = new RequestToken(new UUID(0, 2));

    @Test
    void tokensRoundTripInOrder() {
        assertEquals(List.of(B, A), RequestToken.fromJson(RequestToken.toJson(List.of(B, A))));
    }

    @Test
    void savedEntryThatIsNotAUuidStringIsDropped() {
        String saved = "[\"" + A.id() + "\", \"garbage\", 3, {\"id\": 1}, null, \"" + B.id() + "\"]";

        assertEquals(List.of(A, B), RequestToken.fromJson(JsonParser.parseString(saved)));
    }

    @Test
    void anythingButAnArrayReadsAsNoToken() {
        assertTrue(RequestToken.fromJson(null).isEmpty());
        assertTrue(RequestToken.fromJson(new JsonPrimitive("x")).isEmpty());
    }
}
