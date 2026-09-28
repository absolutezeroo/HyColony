package dev.hycolony.core.testing;

import com.google.gson.JsonObject;
import dev.hycolony.core.building.BuildingType;
import dev.hycolony.core.building.ModuleProducer;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.citizen.Skill;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.crafting.job.Crafter;
import dev.hycolony.core.crafting.job.CraftingTasks;
import dev.hycolony.core.crafting.module.CraftingModule;
import dev.hycolony.core.job.Job;
import dev.hycolony.core.job.JobAI;
import dev.hycolony.core.job.JobType;
import dev.hycolony.core.job.WorkerModule;
import dev.hycolony.core.kernel.port.BodyId;
import java.util.List;

/**
 * A crafter job and its hut, built on the crafting components the way a concrete crafter (the farmer) will be: the job
 * owns a {@link CraftingTasks}, saved with it, and fails its tasks when taken away (MC AbstractJobCrafter.onRemoval).
 */
public final class TestCrafters {
    public static final String ID = "test:crafter";
    public static final JobType JOB = new JobType(ID, TestCrafterJob::new);
    /** A hut of max level 5 with one crafter place, whose crafting module may learn many recipes. */
    public static final BuildingType HUT = hut(true, 1);

    private TestCrafters() {}

    /**
     * The crafter hut with {@code places} crafter places; {@code many}: MC canLearnManyRecipes, false for a
     * SimpleCraftingModule.
     */
    public static BuildingType hut(boolean many, int places) {
        return new BuildingType(
                ID,
                "hut.test_crafter",
                5,
                List.of(
                        new ModuleProducer(
                                "worker", () -> new WorkerModule(JOB, Skill.Dexterity, Skill.Knowledge, places, false)),
                        new ModuleProducer("crafting", () -> new CraftingModule(ID, many))));
    }

    /** The crafter job; its AI stands idle until the crafting AI steps exist. */
    public static final class TestCrafterJob extends Job implements Crafter {
        private final CraftingTasks tasks = new CraftingTasks();

        TestCrafterJob(CitizenData citizen) {
            super(JOB, citizen);
        }

        @Override
        public CraftingTasks craftingTasks() {
            return tasks;
        }

        @Override
        public JobAI createAI(Colony colony, BodyId body) {
            return new JobAI() {
                @Override
                public void tick() {}

                @Override
                public String stateName() {
                    return "idle";
                }

                @Override
                public boolean canBeInterrupted() {
                    return true;
                }
            };
        }

        /** MC AbstractJobCrafter.onRemoval: the crafter's tasks fail, their parents go back to the request system. */
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
}
