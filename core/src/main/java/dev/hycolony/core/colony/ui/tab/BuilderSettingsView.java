package dev.hycolony.core.colony.ui.tab;

import dev.hycolony.core.construction.shared.BuilderSettingsModule;
import dev.hycolony.core.kernel.item.BlockKey;
import java.util.List;
import java.util.Optional;

/**
 * The builder hut's Settings tab (MC SettingsModuleView of BUILDER_SETTINGS): its work mode, its fill block (the
 * hut's choice, else the default; empty when there is neither) and the blocks it may choose from.
 */
public record BuilderSettingsView(
        BuilderSettingsModule.Mode mode, Optional<BlockKey> fillBlock, List<BlockKey> fillChoices)
        implements ModuleTab {
    public BuilderSettingsView {
        fillChoices = List.copyOf(fillChoices);
    }
}
