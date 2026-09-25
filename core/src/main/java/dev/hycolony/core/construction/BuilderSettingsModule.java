package dev.hycolony.core.construction;

import com.google.gson.JsonObject;
import dev.hycolony.core.building.PersistentModule;

/** The builder hut's work mode: AUTO takes orders from the work manager, MANUAL picks them itself. */
public final class BuilderSettingsModule implements PersistentModule {
    public enum Mode { AUTO, MANUAL }

    private Mode mode = Mode.AUTO;

    public Mode mode() { return mode; }
    public void setMode(Mode mode) { this.mode = mode; }

    @Override
    public void write(JsonObject out) {
        out.addProperty("mode", mode.name());
    }

    @Override
    public void read(JsonObject in) {
        if (in.has("mode")) {
            mode = Mode.valueOf(in.get("mode").getAsString());
        }
    }
}
