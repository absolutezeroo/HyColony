package dev.hycolony.core.app.persistence;

import static dev.hycolony.core.kernel.persist.SavedJson.arrayOr;
import static dev.hycolony.core.kernel.persist.SavedJson.boolOr;
import static dev.hycolony.core.kernel.persist.SavedJson.doubleOr;
import static dev.hycolony.core.kernel.persist.SavedJson.enumOf;
import static dev.hycolony.core.kernel.persist.SavedJson.intOr;
import static dev.hycolony.core.kernel.persist.SavedJson.objectOr;
import static dev.hycolony.core.kernel.persist.SavedJson.pos;
import static dev.hycolony.core.kernel.persist.SavedJson.readPos;
import static dev.hycolony.core.kernel.persist.SavedJson.readVec;
import static dev.hycolony.core.kernel.persist.SavedJson.stringOr;
import static dev.hycolony.core.kernel.persist.SavedJson.vec;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.citizen.Gender;
import dev.hycolony.core.citizen.Skill;
import dev.hycolony.core.citizen.Skills;
import dev.hycolony.core.citizen.happiness.HappinessJson;
import dev.hycolony.core.colony.ColonyContext;
import dev.hycolony.core.job.Job;
import dev.hycolony.core.job.JobStatus;
import dev.hycolony.core.job.JobType;
import dev.hycolony.core.kernel.item.Inventory;
import dev.hycolony.core.kernel.item.ItemKey;
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
        o.add("skills", writeSkills(d));
        o.add("lastPosition", vec(d.lastPosition()));
        o.add("respawnPosition", pos(d.respawnPosition()));
        o.add("home", pos(d.homeBuilding()));
        o.add("bedPos", pos(d.bedPos()));
        o.addProperty("asleep", d.asleep());
        o.add("work", pos(d.workBuilding()));
        writeNeeds(d, o);
        o.addProperty("leisureTime", d.leisureTime());
        o.add("inventory", d.inventory().write());
        o.add(
                "job",
                d.job()
                        .<JsonElement>map(Job::write)
                        .or(() -> d.unknownJob().map(JsonElement.class::cast))
                        .orElse(JsonNull.INSTANCE));
        return o;
    }

    /** The saved citizen; empty without an id, a missing optional key takes its default (§ 5). */
    static Optional<CitizenData> read(JsonObject o, ColonyContext ctx) {
        if (!(o.get("id") instanceof JsonPrimitive id && id.isNumber())) {
            return Optional.empty();
        }
        CitizenData d = new CitizenData(id.getAsInt());
        d.setName(stringOr(o.get("name"), ""));
        d.setGender(enumOf(Gender.class, o.get("gender")).orElse(Gender.MALE)); // MC: not female unless saved so
        d.setChild(boolOr(o.get("child"), false));
        d.setSkills(readSkills(objectOr(o.get("skills"))));
        d.setLastPosition(readVec(o.get("lastPosition")));
        d.setRespawnPosition(readPos(o.get("respawnPosition")));
        d.setHomeBuilding(readPos(o.get("home")));
        d.setWorkBuilding(readPos(o.get("work")));
        // Deviation from MC: capped at one break, so an edited save cannot keep a worker on a break forever.
        d.setLeisureTime(Math.min(intOr(o.get("leisureTime"), 0), CitizenData.LEISURE_TICKS));
        d.setBedPos(readPos(o.get("bedPos")));
        if (boolOr(o.get("asleep"), false)) {
            d.setAsleep(true); // after the leisure time: falling asleep ends it (MC CitizenData.setAsleep)
        }
        d.setInventory(Inventory.read(arrayOr(o.get("inventory")), CitizenData.INVENTORY_SLOTS));
        if (o.get("job") instanceof JsonObject job) {
            readJob(job, d, ctx);
        }
        readNeeds(o, d); // after the job, which resets the job status
        return Optional.of(d);
    }

    private static JsonObject writeSkills(CitizenData d) {
        JsonObject skills = new JsonObject();
        for (Skill s : Skill.values()) {
            JsonObject so = new JsonObject();
            so.addProperty("level", d.skills().level(s));
            so.addProperty("xp", d.skills().experience(s));
            skills.add(s.name(), so);
        }
        return skills;
    }

    /** The saved skills; a missing one starts at level 0. */
    private static Skills readSkills(JsonObject so) {
        Skills skills = Skills.empty();
        for (Skill s : Skill.values()) {
            if (so.get(s.name()) instanceof JsonObject e) {
                skills.set(s, intOr(e.get("level"), 0), doubleOr(e.get("xp"), 0));
            }
        }
        return skills;
    }

    /** Writes its hunger, its job status and its happiness into {@code o}. */
    private static void writeNeeds(CitizenData d, JsonObject o) {
        o.addProperty("saturation", d.saturation());
        o.addProperty("justAte", d.hunger().justAte());
        JsonArray foods = new JsonArray();
        d.hunger().history().foods().forEach(f -> foods.add(f.id()));
        o.add("foodHistory", foods);
        o.addProperty("jobStatus", d.jobStatus().name());
        o.add("happiness", HappinessJson.write(d.happiness()));
    }

    /** Reads its hunger, its job status and its happiness from {@code o}; a missing key keeps the default. */
    private static void readNeeds(JsonObject o, CitizenData d) {
        d.setSaturation(doubleOr(o.get("saturation"), d.saturation()));
        d.hunger().setJustAte(boolOr(o.get("justAte"), false));
        for (JsonElement food : arrayOr(o.get("foodHistory"))) {
            if (food instanceof JsonPrimitive p && p.isString()) {
                d.hunger().history().add(new ItemKey(p.getAsString()));
            }
        }
        d.setJobStatus(enumOf(JobStatus.class, o.get("jobStatus")).orElse(JobStatus.IDLE));
        HappinessJson.read(arrayOr(o.get("happiness")), d.happiness());
    }

    private static void readJob(JsonObject jobJson, CitizenData d, ColonyContext ctx) {
        String typeId = stringOr(jobJson.get("type"), "");
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
