package dev.hycolony.core.construction.shared;

import com.google.gson.JsonObject;
import dev.hycolony.core.building.Building;
import dev.hycolony.core.building.PersistentModule;
import dev.hycolony.core.building.ProvidesTab;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.ui.tab.BuilderSettingsView;
import dev.hycolony.core.colony.ui.tab.ModuleTab;
import dev.hycolony.core.construction.blueprint.BlueprintSource;
import dev.hycolony.core.kernel.item.BlockKey;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * The builder hut's settings (MC BuildingModules.BUILDER_SETTINGS): the work mode (AUTO takes orders from the work
 * manager, MANUAL picks them itself) and the fill block of placeholder cells (MC BuildingMiner.FILL_BLOCK).
 */
public final class BuilderSettingsModule implements PersistentModule, ProvidesTab {
    public enum Mode {
        AUTO,
        MANUAL
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

    /** The builder hut's Settings tab (MC SettingsModuleView of BUILDER_SETTINGS). */
    @Override
    public ModuleTab tab(Colony colony, Building building, UUID viewer) {
        BlueprintSource blueprints = colony.context().ports().blueprints();
        return new BuilderSettingsView(mode, fillBlock(blueprints), blueprints.fillBlockChoices());
    }
}
