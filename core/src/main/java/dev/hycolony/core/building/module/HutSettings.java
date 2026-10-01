package dev.hycolony.core.building.module;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.colony.Colony;
import java.util.List;
import java.util.UUID;

/**
 * A hut module holding settings (MC SettingsModule): its rows for the Settings tab, and what a click on one does (MC
 * TriggerSettingMessage).
 */
public interface HutSettings extends ProvidesTab {
    /** The rows, in MC's order. */
    List<SettingRow> settingRows(Colony colony);

    /**
     * MC ISetting.trigger for the BOOL or STRING row {@code id}: turns it over or moves to its next value. False for
     * an unknown, inactive or BLOCK row (a block is chosen from a list instead).
     */
    boolean trigger(String id);

    /** The Settings tab (MC SettingsModuleView). */
    @Override
    default ModuleTab tab(Colony colony, Building building, UUID viewer) {
        return new SettingsView(settingRows(colony));
    }
}
