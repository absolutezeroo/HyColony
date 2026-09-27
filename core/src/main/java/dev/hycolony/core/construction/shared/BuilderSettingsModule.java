package dev.hycolony.core.construction.shared;

import com.google.gson.JsonObject;
import dev.hycolony.core.building.Building;
import dev.hycolony.core.building.PersistentModule;
import dev.hycolony.core.building.ProvidesTab;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.ui.tab.BuilderSettingsView;
import dev.hycolony.core.colony.ui.tab.ModuleTab;
import java.util.Arrays;
import java.util.UUID;

/** The builder hut's work mode: AUTO takes orders from the work manager, MANUAL picks them itself. */
public final class BuilderSettingsModule implements PersistentModule, ProvidesTab {
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

    /** The builder hut's Settings tab (MC SettingsModuleView of BUILDER_SETTINGS). */
    @Override
    public ModuleTab tab(Colony colony, Building building, UUID viewer) {
        return new BuilderSettingsView(mode);
    }
}
