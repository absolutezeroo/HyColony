package dev.hycolony.core.farming.job;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.citizen.Skill;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.permission.Permissions;
import dev.hycolony.core.colony.territory.TerritoryIndex;
import dev.hycolony.core.farming.field.FarmField;
import dev.hycolony.core.farming.field.FieldRadii;
import dev.hycolony.core.farming.hut.FarmerFieldsModule;
import dev.hycolony.core.farming.hut.FarmerHut;
import dev.hycolony.core.farming.hut.FarmerSettingsModule;
import dev.hycolony.core.job.WorkerModule;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.Vec3;
import dev.hycolony.core.kernel.item.BlockKey;
import dev.hycolony.core.kernel.item.BlockState;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.item.ToolInfo;
import dev.hycolony.core.kernel.item.ToolType;
import dev.hycolony.core.kernel.port.BodyId;
import dev.hycolony.core.request.Request;
import dev.hycolony.core.request.model.StackRequest;
import dev.hycolony.core.testing.TestContexts;
import dev.hycolony.core.testing.farming.FakeFarming;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * A colony with a level-1 farmer hut, a hired farmer and one field of radius 1 around {@link #FIELD}: its 8 cells are
 * dirt one block below the field block, tillable until tilled.
 */
abstract class FarmerTestBase {
    static final BlockPos HUT = new BlockPos(0, 64, 0);
    static final BlockPos FIELD = new BlockPos(10, 64, 0);
    static final ItemKey SEEDS = FakeFarming.WHEAT_SEEDS;
    static final ItemKey HOE = new ItemKey("Tool_Hoe_Crude");
    static final ItemKey FERTILIZER = FakeFarming.FERTILIZER;
    static final BlockKey DIRT = new BlockKey("Soil_Dirt");

    final TestContexts t = new TestContexts();
    final Colony colony = new Colony(
            t.context(),
            new TerritoryIndex(),
            new Colony.Founding(1, "T", HUT, Permissions.createDefault(UUID.randomUUID(), "A")));
    final Building hut = Building.create(FarmerHut.TYPE, HUT, 0);
    final CitizenData citizen = new CitizenData(1);
    FarmerJob job;
    BodyId body;
    FarmWork work;

    FarmerTestBase() {
        hut.setLevel(1);
        hut.setBuilt(true);
        colony.buildings().add(hut);
        t.bodies.instant = true;
        t.catalog.tools.put(HOE, new ToolInfo(ToolType.HOE, 0, 1f));
        t.catalog.durability.put(HOE, 100);
        t.catalog.durability.put(FERTILIZER, 5);
        citizen.skills().set(Skill.Stamina, 10, 0);
        citizen.setSaturation(CitizenData.MAX_SATURATION);
        colony.citizens().restore(citizen);
        assertTrue(hut.module(WorkerModule.class).orElseThrow().hire(colony, hut, citizen));
        colony.buildings().onColonyTick(colony);
        job = (FarmerJob) citizen.job().orElseThrow();
        body = t.bodies.existing(1, 1, Vec3.center(HUT));
        work = new FarmWork(FarmWorkContext.of(colony, job, body).orElseThrow());
    }

    FarmerFieldsModule fields() {
        return hut.module(FarmerFieldsModule.class).orElseThrow();
    }

    FarmerSettingsModule settings() {
        return hut.module(FarmerSettingsModule.class).orElseThrow();
    }

    /** The field around {@link #FIELD}, radius 1, owned by the hut, with {@code seeded} wheat seeds; tillable dirt. */
    FarmField field(boolean seeded) {
        colony.registries().fields().add(FIELD);
        FarmField f = colony.registries().fields().get(FIELD).orElseThrow();
        f.setRadii(new FieldRadii(1, 1, 1, 1));
        if (seeded) {
            f.setSeed(Optional.of(SEEDS));
        }
        f.setOwner(Optional.of(HUT));
        t.farming.fieldBlocks.add(FIELD);
        for (BlockPos cell : cells()) {
            t.blocks.blocks.put(cell, new BlockState(DIRT, 0));
            t.farming.tillable.add(cell);
        }
        return f;
    }

    /** The 8 soil cells of the field, one below the field block. */
    static List<BlockPos> cells() {
        return List.of(
                FIELD.offset(1, -1, 1),
                FIELD.offset(0, -1, 1),
                FIELD.offset(-1, -1, 1),
                FIELD.offset(-1, -1, 0),
                FIELD.offset(-1, -1, -1),
                FIELD.offset(0, -1, -1),
                FIELD.offset(1, -1, -1),
                FIELD.offset(1, -1, 0));
    }

    void give(ItemKey item, int count) {
        citizen.inventory().insert(new ItemAmount(item, count), i -> 64);
    }

    void putInHut(ItemKey item, int count) {
        t.containers.containers.computeIfAbsent(HUT, p -> new LinkedHashMap<>()).merge(item, count, Integer::sum);
    }

    int carried(ItemKey item) {
        return citizen.inventory().count(item);
    }

    /** The hut's open or completed requests for {@code item}. */
    List<Request> requestsFor(ItemKey item) {
        return colony.requests().byRequester(hut.requesterId()).stream()
                .filter(r ->
                        r.requestable() instanceof StackRequest s && s.item().equals(item))
                .toList();
    }
}
