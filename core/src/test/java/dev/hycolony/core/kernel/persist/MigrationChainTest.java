package dev.hycolony.core.kernel.persist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.gson.JsonObject;
import java.util.List;
import org.junit.jupiter.api.Test;

class MigrationChainTest {
    private static JsonObject doc(int version) {
        JsonObject o = new JsonObject();
        o.addProperty("schemaVersion", version);
        return o;
    }

    @Test
    void appliesMigrationsInOrder() {
        MigrationChain chain = new MigrationChain(3, List.of(
                new Migration(1, o -> { o.addProperty("a", true); return o; }),
                new Migration(2, o -> { o.addProperty("b", o.get("a").getAsBoolean()); return o; })));
        JsonObject out = chain.migrate(doc(1));
        assertEquals(3, out.get("schemaVersion").getAsInt());
        assertEquals(true, out.get("b").getAsBoolean());
    }

    @Test
    void newerSchemaIsRejected() {
        assertThrows(SchemaTooNewException.class, () -> MigrationChain.sp0().migrate(doc(2)));
    }

    @Test
    void missingMigrationStepIsAnError() {
        MigrationChain chain = new MigrationChain(3, List.of(new Migration(1, o -> o)));
        assertThrows(IllegalStateException.class, () -> chain.migrate(doc(1)));
    }
}
