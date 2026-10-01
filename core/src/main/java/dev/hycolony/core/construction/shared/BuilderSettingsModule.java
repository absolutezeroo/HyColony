package dev.hycolony.core.construction.shared;

import com.google.gson.JsonObject;
import dev.hycolony.core.building.module.HutSettings;
import dev.hycolony.core.building.module.PersistentModule;
import dev.hycolony.core.building.module.SettingRow;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.construction.blueprint.BlueprintSource;
import dev.hycolony.core.kernel.item.BlockKey;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * The builder hut's settings (MC BuildingModules.BUILDER_SETTINGS): the work mode (AUTO takes orders from the work
 * manager, MANUAL picks them itself) and the fill block of placeholder cells (MC BuildingMiner.FILL_BLOCK).
 */
public final class BuilderSettingsModule implements PersistentModule, HutSettings {
    /** MC BuildingBuilder.MODE's id. */
    static final String MODE = "mode";

    /** MC BuildingMiner.FILL_BLOCK's id. */
    public static final String FILL_BLOCK = "fillblock";

    public enum Mode {
        AUTO,
        MANUAL;

        /** The other mode: the Settings tab's button switches automatic and manual (MC's mode setting). */
        public Mode next() {
            return this == AUTO ? MANUAL : AUTO;
        }
    }

    private Mode mode = Mode.AUTO;
    /** Null: the blueprint source's default (dirt in MC). */
    private @Nullable BlockKey fillBlock;

    public Mode mode() {
        return mode;
    }

    public void setMode(Mode mode) {
        this.mode = mode;
    }

    /** The chosen fill block; empty when the hut keeps the default. */
    public Optional<BlockKey> fillBlock() {
        return Optional.ofNullable(fillBlock);
    }

    /**
     * The block fill cells get: the chosen one while {@code blueprints} still offers it, else its default (a saved
     * block since removed from the game falls back); empty when the source has neither.
     */
    public Optional<BlockKey> fillBlock(BlueprintSource blueprints) {
        return fillBlock().filter(blueprints.fillBlockChoices()::contains).or(blueprints::defaultFillBlock);
    }

    public void setFillBlock(BlockKey block) {
        this.fillBlock = block;
    }

    @Override
    public void write(JsonObject out) {
        out.addProperty("mode", mode.name());
        if (fillBlock != null) {
            out.addProperty("fillBlock", fillBlock.id());
        }
    }

    @Override
    public void read(JsonObject in) {
        String saved = in.has("mode") ? in.get("mode").getAsString() : "";
        mode = Arrays.stream(Mode.values())
                .filter(m -> m.name().equals(saved))
                .findFirst()
                .orElse(Mode.AUTO); // an unknown mode stays AUTO
        fillBlock = in.has("fillBlock") && in.get("fillBlock").isJsonPrimitive()
                ? new BlockKey(in.get("fillBlock").getAsString())
                : null;
    }

    /**
     * MC BUILDER_SETTINGS, in its order: the mode, the recipe mode and construction strategy (research settings: shown
     * disabled with their default value), the fill block. Deviation from MC: no "Use Shears" row (no shears in
     * HyColony).
     */
    @Override
    public List<SettingRow> settingRows(Colony colony) {
        BlueprintSource blueprints = colony.context().ports().blueprints();
        return List.of(
                SettingRow.string(MODE, "hycolony.ui.setting.value." + (mode == Mode.AUTO ? "automatic" : "manual")),
                SettingRow.needsResearch(
                        "recipemode",
                        "hycolony.ui.setting.value.priority",
                        "hycolony.ui.setting.research.warehousemaster"),
                SettingRow.needsResearch(
                        "buildmode", "hycolony.ui.setting.value.default", "hycolony.ui.setting.research.buildermodes"),
                SettingRow.block(FILL_BLOCK, fillBlock(blueprints), blueprints.fillBlockChoices()));
    }

    /** MC StringSetting.trigger of the mode: automatic and manual in turn. */
    @Override
    public boolean trigger(String id) {
        if (!MODE.equals(id)) {
            return false;
        }
        mode = mode.next();
        return true;
    }
}
