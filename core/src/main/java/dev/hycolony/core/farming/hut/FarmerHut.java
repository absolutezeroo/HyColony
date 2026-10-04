package dev.hycolony.core.farming.hut;

import dev.hycolony.core.building.BuildingRegistry;
import dev.hycolony.core.building.BuildingType;
import dev.hycolony.core.building.module.ModuleProducer;
import dev.hycolony.core.citizen.Skill;
import dev.hycolony.core.crafting.module.CraftingModule;
import dev.hycolony.core.crafting.request.CraftingResolvers;
import dev.hycolony.core.crafting.task.CrafterTaskListModule;
import dev.hycolony.core.farming.job.FarmerJob;
import dev.hycolony.core.job.JobRegistry;
import dev.hycolony.core.job.WorkerModule;
import dev.hycolony.core.kernel.item.ToolType;
import dev.hycolony.core.logistics.pickup.KeepToolsModule;
import java.util.EnumSet;
import java.util.List;

/**
 * The farmer hut type (MC BuildingFarmer, modules from ModBuildingsInitializer): its crafting module first, then one
 * farmer (Stamina, then Athletics, no work in the rain), its fields and its settings. It keeps one hoe (MC keepX).
 */
public final class FarmerHut {
    public static final String TYPE_ID = "hycolony:farmer";

    /** MC MAX_BUILDING_LEVEL. */
    public static final int MAX_LEVEL = 5;

    /** MC TagConstants.CRAFTING_FARMER: the farmer's crafting module reads the {@code farmer_*} job tags. */
    static final String CRAFTER = "farmer";

    public static final BuildingType TYPE = new BuildingType(
            TYPE_ID,
            "hut.farmer",
            MAX_LEVEL,
            List.of(
                    new ModuleProducer("crafting", () -> new CraftingModule(TYPE_ID, CRAFTER, true)),
                    new ModuleProducer(
                            "worker", () -> new WorkerModule(FarmerJob.TYPE, Skill.Stamina, Skill.Athletics, 1, false)),
                    new ModuleProducer("craftingResolvers", CraftingResolvers::new),
                    new ModuleProducer("fields", FarmerFieldsModule::new),
                    new ModuleProducer("settings", FarmerSettingsModule::new),
                    new ModuleProducer("craftTasks", CrafterTaskListModule::new),
                    // Deviation from MC: no axe, which MC's farmer keeps but never uses.
                    new ModuleProducer("keepTools", () -> new KeepToolsModule(EnumSet.of(ToolType.HOE)))));

    private FarmerHut() {}

    /** Registers the farmer hut type. */
    public static void register(BuildingRegistry r) {
        r.register(TYPE);
    }

    /** Registers the farmer job, so saved farmers get their job back on load. */
    public static void register(JobRegistry r) {
        r.register(FarmerJob.TYPE);
    }
}
