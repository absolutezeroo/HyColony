package dev.hycolony.core.colony;

import static dev.hycolony.core.colony.JsonPositions.pos;
import static dev.hycolony.core.colony.JsonPositions.readPos;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.hycolony.core.building.Building;
import dev.hycolony.core.building.BuildingModule;
import dev.hycolony.core.building.BuildingType;
import dev.hycolony.core.building.PersistentModule;

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
        b.registeredContainers().forEach(p -> containers.add(pos(p)));
        o.add("containers", containers);
        o.addProperty("deconstructed", b.isDeconstructed());
        return o;
    }

    static Building read(JsonObject o, BuildingType type) {
        Building b =
                Building.create(type, readPos(o.get("pos")), o.get("rotation").getAsInt());
        b.setLevel(o.get("level").getAsInt());
        b.setBuilt(o.get("built").getAsBoolean());
        b.setCustomName(o.get("customName").getAsString());
        b.setStyle(o.get("style").getAsString());
        if (o.has("deconstructed")) {
            b.setDeconstructed(o.get("deconstructed").getAsBoolean());
        }
        if (o.has("containers")) {
            for (JsonElement el : o.getAsJsonArray("containers")) {
                b.addContainer(readPos(el));
            }
        }
        JsonObject modules = o.getAsJsonObject("modules");
        for (String key : modules.keySet()) {
            BuildingModule module = b.modules().get(key);
            if (module instanceof PersistentModule pm) {
                pm.read(modules.getAsJsonObject(key));
            } else if (module == null) {
                b.unknownModules().put(key, modules.getAsJsonObject(key));
            }
        }
        return b;
    }
}
