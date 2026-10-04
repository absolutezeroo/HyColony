package dev.hycolony.core.app.persistence;

import static dev.hycolony.core.kernel.persist.SavedJson.arrayOr;
import static dev.hycolony.core.kernel.persist.SavedJson.boolOr;
import static dev.hycolony.core.kernel.persist.SavedJson.doubleOr;
import static dev.hycolony.core.kernel.persist.SavedJson.enumOf;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.citizen.happiness.HappinessJson;
import dev.hycolony.core.citizen.mourn.MourningJson;
import dev.hycolony.core.job.JobStatus;
import dev.hycolony.core.kernel.item.ItemKey;

/** A citizen's needs in its save: its hunger, its job status, its happiness and its mourning. */
final class CitizenNeedsJson {
    private CitizenNeedsJson() {}

    /** Writes the needs of {@code d} into {@code o}. */
    static void write(CitizenData d, JsonObject o) {
        o.addProperty("saturation", d.saturation());
        o.addProperty("justAte", d.hunger().justAte());
        JsonArray foods = new JsonArray();
        d.hunger().history().foods().forEach(f -> foods.add(f.id()));
        o.add("foodHistory", foods);
        o.addProperty("jobStatus", d.jobStatus().name());
        o.add("happiness", HappinessJson.write(d.happiness()));
        o.add("mourning", MourningJson.write(d.mourning()));
    }

    /** Reads the needs of {@code d} from {@code o}; a missing key keeps the default (CLAUDE.md § 5). */
    static void read(JsonObject o, CitizenData d) {
        d.setSaturation(doubleOr(o.get("saturation"), d.saturation()));
        d.hunger().setJustAte(boolOr(o.get("justAte"), false));
        for (JsonElement food : arrayOr(o.get("foodHistory"))) {
            if (food instanceof JsonPrimitive p && p.isString()) {
                d.hunger().history().add(new ItemKey(p.getAsString()));
            }
        }
        d.setJobStatus(enumOf(JobStatus.class, o.get("jobStatus")).orElse(JobStatus.IDLE));
        HappinessJson.read(arrayOr(o.get("happiness")), d.happiness());
        MourningJson.read(o.get("mourning"), d.mourning());
    }
}
