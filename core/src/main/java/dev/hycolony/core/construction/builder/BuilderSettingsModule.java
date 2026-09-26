package dev.hycolony.core.construction.builder;

import com.google.gson.JsonObject;
import dev.hycolony.core.building.PersistentModule;
import java.util.Arrays;

/** The builder hut's work mode: AUTO takes orders from the work manager, MANUAL picks them itself. */
public final class BuilderSettingsModule implements PersistentModule {
    public enum Mode {
        AUTO,
        MANUAL
    }

    private Mode mode = Mode.AUTO;

    public Mode mode() {
        return mode;
    }

    public void setMode(Mode mode) {
        this.mode = mode;
    }

    @Override
    public void write(JsonObject out) {
        out.addProperty("mode", mode.name());
    }

    @Override
    public void read(JsonObject in) {
        String saved = in.has("mode") ? in.get("mode").getAsString() : "";
        mode = Arrays.stream(Mode.values())
                .filter(m -> m.name().equals(saved))
                .findFirst()
                .orElse(Mode.AUTO); // an unknown mode stays AUTO
    }
}
