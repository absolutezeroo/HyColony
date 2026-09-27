package dev.hycolony.core.colony.ui.tab;

import dev.hycolony.core.construction.shared.BuilderSettingsModule;

/** The builder hut's Settings tab (MC SettingsModuleView of BUILDER_SETTINGS): its work mode. */
public record BuilderSettingsView(BuilderSettingsModule.Mode mode) implements ModuleTab {}
