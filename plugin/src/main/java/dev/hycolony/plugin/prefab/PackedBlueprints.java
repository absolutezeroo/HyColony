package dev.hycolony.plugin.prefab;

import dev.hycolony.core.construction.blueprint.Blueprint;
import dev.hycolony.core.construction.blueprint.BlueprintSource;
import dev.hycolony.core.construction.blueprint.PackInfo;
import dev.hycolony.core.kernel.item.BlockKey;
import java.util.List;
import java.util.Optional;

/**
 * A blueprint source with the folders and pack metadata of {@code hycolony/packs.json} ({@link PrefabPacks}). Every
 * other method of the port is passed on to the wrapped source: a method added to the port must be passed on here too.
 */
public final class PackedBlueprints implements BlueprintSource {
    private final BlueprintSource blueprints;
    private final PrefabPacks packs;

    public PackedBlueprints(BlueprintSource blueprints, PrefabPacks packs) {
        this.blueprints = blueprints;
        this.packs = packs;
    }

    @Override
    public Optional<Blueprint> load(String style, String buildingTypeId, int level, int rotation) {
        return blueprints.load(style, buildingTypeId, level, rotation);
    }

    @Override
    public List<String> styles() {
        return blueprints.styles();
    }

    @Override
    public boolean hasPlan(String style, String buildingTypeId, int level) {
        return blueprints.hasPlan(style, buildingTypeId, level);
    }

    @Override
    public PackInfo pack(String style) {
        return packs.pack(style);
    }

    @Override
    public String category(String buildingTypeId) {
        return packs.category(buildingTypeId);
    }

    @Override
    public Optional<BlockKey> defaultFillBlock() {
        return blueprints.defaultFillBlock();
    }

    @Override
    public List<BlockKey> fillBlockChoices() {
        return blueprints.fillBlockChoices();
    }
}
