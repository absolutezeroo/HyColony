package dev.hycolony.core.construction;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.job.Job;
import dev.hycolony.core.job.JobAI;
import dev.hycolony.core.job.JobType;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.port.BodyId;
import java.util.HashMap;
import java.util.Map;

/**
 * MineColonies JobBuilder. Its progress lives in the work order; it saves the wear of its tools, which the core
 * tracks itself (item metadata does not travel through citizen inventories).
 */
public final class BuilderJob extends Job {
    public static final JobType TYPE = new JobType("hycolony:builder", BuilderJob::new);

    /** Blocks mined with each tool since the last one of that kind broke. */
    private final Map<ItemKey, Integer> toolUses = new HashMap<>();

    public BuilderJob(CitizenData citizen) {
        super(TYPE, citizen);
    }

    @Override
    public JobAI createAI(Colony colony, BodyId body) {
        return new BuilderAI(colony, citizen(), body);
    }

    /**
     * One more block mined with {@code tool}. True when that use breaks it ({@code durability} uses reached); its
     * count then starts again for the next one. A durability of 0 never breaks.
     */
    boolean wear(ItemKey tool, int durability) {
        if (durability <= 0) {
            return false;
        }
        if (toolUses.merge(tool, 1, Integer::sum) < durability) {
            return false;
        }
        toolUses.remove(tool);
        return true;
    }

    @Override
    public JsonObject write() {
        JsonObject o = super.write();
        JsonObject uses = new JsonObject();
        toolUses.forEach((tool, n) -> uses.addProperty(tool.id(), n));
        o.add("toolUses", uses);
        return o;
    }

    @Override
    public void read(JsonObject o) {
        super.read(o);
        toolUses.clear();
        if (o.has("toolUses")) {
            for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("toolUses").entrySet()) {
                toolUses.put(new ItemKey(e.getKey()), e.getValue().getAsInt());
            }
        }
    }
}
