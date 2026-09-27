package dev.hycolony.core.colony.persistence;

import static dev.hycolony.core.colony.persistence.JsonPositions.pos;
import static dev.hycolony.core.colony.persistence.JsonPositions.readPos;
import static dev.hycolony.core.colony.persistence.JsonPositions.readVec;
import static dev.hycolony.core.colony.persistence.JsonPositions.vec;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.citizen.Gender;
import dev.hycolony.core.citizen.Skill;
import dev.hycolony.core.citizen.Skills;
import dev.hycolony.core.colony.ColonyContext;
import dev.hycolony.core.job.Job;
import dev.hycolony.core.job.JobType;
import dev.hycolony.core.kernel.item.Inventory;
import java.util.Optional;

/** One citizen's {@link CitizenData} to and from JSON. An unknown job is kept verbatim, like unknown buildings. */
final class CitizenSerializer {
    private CitizenSerializer() {}

    static JsonObject write(CitizenData d) {
        JsonObject o = new JsonObject();
        o.addProperty("id", d.id());
        o.addProperty("name", d.name());
        o.addProperty("gender", d.gender().name());
        o.addProperty("child", d.isChild());
        JsonObject skills = new JsonObject();
        for (Skill s : Skill.values()) {
            JsonObject so = new JsonObject();
            so.addProperty("level", d.skills().level(s));
            so.addProperty("xp", d.skills().experience(s));
            skills.add(s.name(), so);
        }
        o.add("skills", skills);
        o.add("lastPosition", vec(d.lastPosition()));
        o.add("respawnPosition", pos(d.respawnPosition()));
        o.add("home", pos(d.homeBuilding()));
        o.add("work", pos(d.workBuilding()));
        o.addProperty("saturation", d.saturation());
        o.add("inventory", d.inventory().write());
        o.add(
                "job",
                d.job()
                        .<JsonElement>map(Job::write)
                        .or(() -> d.unknownJob().map(JsonElement.class::cast))
                        .orElse(JsonNull.INSTANCE));
        return o;
    }

    static CitizenData read(JsonObject o, ColonyContext ctx) {
        CitizenData d = new CitizenData(o.get("id").getAsInt());
        d.setName(o.get("name").getAsString());
        d.setGender(Gender.valueOf(o.get("gender").getAsString()));
        d.setChild(o.get("child").getAsBoolean());
        Skills skills = Skills.empty();
        JsonObject so = o.getAsJsonObject("skills");
        for (Skill s : Skill.values()) {
            if (so.has(s.name())) {
                JsonObject e = so.getAsJsonObject(s.name());
                skills.set(s, e.get("level").getAsInt(), e.get("xp").getAsDouble());
            }
        }
        d.setSkills(skills);
        d.setLastPosition(readVec(o.get("lastPosition")));
        d.setRespawnPosition(readPos(o.get("respawnPosition")));
        d.setHomeBuilding(readPos(o.get("home")));
        d.setWorkBuilding(readPos(o.get("work")));
        d.setSaturation(o.get("saturation").getAsDouble());
        d.setInventory(Inventory.read(o.getAsJsonArray("inventory"), CitizenData.INVENTORY_SLOTS));
        if (o.has("job") && !o.get("job").isJsonNull()) {
            readJob(o.getAsJsonObject("job"), d, ctx);
        }
        return d;
    }

    private static void readJob(JsonObject jobJson, CitizenData d, ColonyContext ctx) {
        String typeId = jobJson.get("type").getAsString();
        Optional<JobType> type = ctx.jobs().byId(typeId);
        if (type.isPresent()) {
            Job job = type.get().factory().apply(d);
            job.read(jobJson);
            d.setJob(job);
        } else {
            // Its pack may only be disabled: keep it verbatim; heal keeps the assignment only with its kept hut.
            d.keepUnknownJob(jobJson);
        }
    }
}
