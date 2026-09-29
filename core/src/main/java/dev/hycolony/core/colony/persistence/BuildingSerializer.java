package dev.hycolony.core.colony.persistence;

import static dev.hycolony.core.kernel.persist.SavedJson.arrayOr;
import static dev.hycolony.core.kernel.persist.SavedJson.boolOr;
import static dev.hycolony.core.kernel.persist.SavedJson.intOr;
import static dev.hycolony.core.kernel.persist.SavedJson.objectOr;
import static dev.hycolony.core.kernel.persist.SavedJson.pos;
import static dev.hycolony.core.kernel.persist.SavedJson.requirePos;
import static dev.hycolony.core.kernel.persist.SavedJson.stringOr;
import static dev.hycolony.core.kernel.persist.SavedJson.tryPos;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.hycolony.core.building.Building;
import dev.hycolony.core.building.BuildingModule;
import dev.hycolony.core.building.BuildingType;
import dev.hycolony.core.building.PersistentModule;
import dev.hycolony.core.building.RegisteredBlocks;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.Workstation;
import java.util.Optional;

/** One {@link Building} to and from JSON. Unknown modules are kept verbatim. */
final class BuildingSerializer {
    private BuildingSerializer() {}

    static JsonObject write(Building b) {
        JsonObject o = new JsonObject();
        o.addProperty("type", b.type().id());
        o.add("pos", pos(b.position()));
        o.addProperty("rotation", b.rotation());
        o.addProperty("level", b.level());
        o.addProperty("built", b.isBuilt());
        o.addProperty("customName", b.customName());
        o.addProperty("style", b.style());
        o.addProperty("pickupPriority", b.pickupPriority().value());
        JsonObject modules = new JsonObject();
        b.modules().forEach((key, module) -> {
            if (module instanceof PersistentModule pm) {
                JsonObject m = new JsonObject();
                pm.write(m);
                modules.add(key, m);
            }
        });
        b.unknownModules().forEach(modules::add);
        o.add("modules", modules);
        JsonArray containers = new JsonArray();
        b.registeredBlocks().containers().forEach(p -> containers.add(pos(p)));
        o.add("containers", containers);
        o.add("workstations", workstations(b));
        o.addProperty("deconstructed", b.isDeconstructed());
        return o;
    }

    /** The registered benches as {@code {pos, bench, tier}} entries, in registration order. */
    private static JsonArray workstations(Building b) {
        JsonArray out = new JsonArray();
        b.registeredBlocks().workstations().forEach((p, w) -> {
            JsonObject e = new JsonObject();
            e.add("pos", pos(p));
            e.addProperty("bench", w.benchId());
            e.addProperty("tier", w.tier());
            out.add(e);
        });
        return out;
    }

    /** The saved building at its position (which the caller checked); a missing optional key takes its default (§ 5). */
    static Building read(JsonObject o, BuildingType type) {
        Building b = Building.create(type, requirePos(o.get("pos")), intOr(o.get("rotation"), 0));
        b.setLevel(intOr(o.get("level"), 0));
        b.setBuilt(boolOr(o.get("built"), false));
        b.setCustomName(stringOr(o.get("customName"), b.customName()));
        b.setStyle(stringOr(o.get("style"), b.style()));
        b.pickupPriority().set(intOr(o.get("pickupPriority"), b.pickupPriority().value()));
        b.setDeconstructed(boolOr(o.get("deconstructed"), false));
        readRegisteredBlocks(o, b.registeredBlocks());
        JsonObject modules = objectOr(o.get("modules"));
        for (String key : modules.keySet()) {
            if (!(modules.get(key) instanceof JsonObject saved)) {
                continue;
            }
            BuildingModule module = b.modules().get(key);
            if (module instanceof PersistentModule pm) {
                pm.read(saved);
            } else if (module == null) {
                b.unknownModules().put(key, saved);
            }
        }
        return b;
    }

    /** Saved containers and benches; a missing list (older save) registers none, a malformed position is skipped. */
    private static void readRegisteredBlocks(JsonObject o, RegisteredBlocks blocks) {
        for (JsonElement el : arrayOr(o.get("containers"))) {
            tryPos(el).ifPresent(blocks::addContainer);
        }
        if (o.get("workstations") instanceof JsonArray workstations) {
            for (JsonElement el : workstations) {
                readWorkstation(el, blocks);
            }
        }
    }

    /** Registers one saved bench; an entry without position or bench, or with a tier below 1, is skipped (§ 5). */
    private static void readWorkstation(JsonElement el, RegisteredBlocks blocks) {
        if (!(el instanceof JsonObject e)
                || !(e.get("bench") instanceof JsonPrimitive bench && bench.isString())
                || !(e.get("tier") instanceof JsonPrimitive tier && tier.isNumber())
                || tier.getAsInt() < 1) {
            return;
        }
        Optional<BlockPos> at = tryPos(e.get("pos"));
        at.ifPresent(p -> blocks.addWorkstation(p, new Workstation(bench.getAsString(), tier.getAsInt())));
    }
}
