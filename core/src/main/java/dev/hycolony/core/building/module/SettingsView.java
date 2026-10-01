package dev.hycolony.core.building.module;

import java.util.List;

/** A hut's Settings tab (MC SettingsModuleView): its rows in MC's insertion order. */
public record SettingsView(List<SettingRow> rows) implements ModuleTab {
    public SettingsView {
        rows = List.copyOf(rows);
    }
}
