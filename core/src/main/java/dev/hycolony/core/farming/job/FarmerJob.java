package dev.hycolony.core.farming.job;

import com.google.gson.JsonObject;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.crafting.job.CraftingWork;
import dev.hycolony.core.crafting.job.CraftingWorkContext;
import dev.hycolony.core.crafting.task.Crafter;
import dev.hycolony.core.crafting.task.CraftingTasks;
import dev.hycolony.core.job.IdleAI;
import dev.hycolony.core.job.Job;
import dev.hycolony.core.job.JobAI;
import dev.hycolony.core.job.JobType;
import dev.hycolony.core.kernel.port.BodyId;

/**
 * MC JobFarmer, an AbstractJobCrafter: it owns its crafting tasks (saved with it) and fails them when taken away (MC
 * AbstractJobCrafter.onRemoval). Its AI farms its hut's fields and crafts its tasks between them.
 */
public final class FarmerJob extends Job implements Crafter {
    public static final JobType TYPE = new JobType("hycolony:farmer", FarmerJob::new);
    /** MC JobFarmer.getSaturationFactor: a farmer gets hungry 20 % faster. */
    static final double SATURATION_FACTOR = 1.2;

    private final CraftingTasks tasks = new CraftingTasks();

    public FarmerJob(CitizenData citizen) {
        super(TYPE, citizen);
    }

    @Override
    public CraftingTasks craftingTasks() {
        return tasks;
    }

    /** MC EntityAIWorkFarmer; without a hut or its farmer modules, an {@link IdleAI}. */
    @Override
    public JobAI createAI(Colony colony, BodyId body) {
        return CraftingWorkContext.of(colony, this, body, FarmWorkContext.ACTIONS_UNTIL_DUMP)
                .flatMap(crafting -> FarmWorkContext.of(crafting, this)
                        .<JobAI>map(farm -> new FarmerAI(new CraftingWork(crafting), farm)))
                .orElseGet(IdleAI::new);
    }

    /** MC JobFarmer.getSaturationFactor. */
    @Override
    public double saturationFactor() {
        return SATURATION_FACTOR;
    }

    /** MC AbstractJobCrafter.onRemoval. */
    @Override
    public void onRemoval(Colony colony) {
        tasks.cancelAll(colony);
    }

    @Override
    public JsonObject write() {
        JsonObject out = super.write();
        out.add("crafting", tasks.write());
        return out;
    }

    @Override
    public void read(JsonObject in) {
        super.read(in);
        if (in.get("crafting") instanceof JsonObject crafting) {
            tasks.read(crafting);
        }
    }
}
