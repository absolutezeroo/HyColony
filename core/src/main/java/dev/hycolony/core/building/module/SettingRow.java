package dev.hycolony.core.building.module;

import dev.hycolony.core.kernel.item.BlockKey;
import java.util.List;
import java.util.Optional;

/**
 * One row of a hut's Settings tab (MC SettingsModuleWindow, one ISetting): its id ({@code setting.<id>} names it), its
 * kind, its shown value (a language key for a STRING, {@code on} for a BOOL, the block and its choices for a BLOCK),
 * and whether it is active; an inactive one shows disabled with the research MC asks for.
 */
public record SettingRow(
        String id,
        Kind kind,
        String valueKey,
        boolean on,
        Optional<BlockKey> block,
        List<BlockKey> choices,
        boolean active,
        Optional<String> researchKey) {
    /** MC's setting types: BoolSetting, StringSetting (and its subclasses), BlockSetting. */
    public enum Kind {
        BOOL,
        STRING,
        BLOCK
    }

    public SettingRow {
        choices = List.copyOf(choices);
    }

    /** A BoolSetting showing On or Off. */
    public static SettingRow bool(String id, boolean on) {
        return new SettingRow(id, Kind.BOOL, "", on, Optional.empty(), List.of(), true, Optional.empty());
    }

    /** A StringSetting showing the value named by {@code valueKey}. */
    public static SettingRow string(String id, String valueKey) {
        return new SettingRow(id, Kind.STRING, valueKey, false, Optional.empty(), List.of(), true, Optional.empty());
    }

    /** A BlockSetting showing {@code block}, chosen among {@code choices}. */
    public static SettingRow block(String id, Optional<BlockKey> block, List<BlockKey> choices) {
        return new SettingRow(id, Kind.BLOCK, "", false, block, choices, true, Optional.empty());
    }

    /**
     * A StringSetting a research unlocks (MC isActive false, getInactiveReason "needs research"): HyColony has no
     * research yet, so it shows its default value, disabled, the research's name in {@code researchKey}.
     */
    public static SettingRow needsResearch(String id, String valueKey, String researchKey) {
        return new SettingRow(
                id, Kind.STRING, valueKey, false, Optional.empty(), List.of(), false, Optional.of(researchKey));
    }
}
