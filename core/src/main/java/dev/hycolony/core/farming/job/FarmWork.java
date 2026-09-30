package dev.hycolony.core.farming.job;

import dev.hycolony.core.farming.field.FarmField;
import dev.hycolony.core.farming.field.FieldStage;
import dev.hycolony.core.farming.hut.FieldWalk;
import dev.hycolony.core.job.work.WorkDelay;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.item.ToolType;
import dev.hycolony.core.kernel.port.Msg;
import dev.hycolony.core.request.Request;
import dev.hycolony.core.request.model.RequestState;
import dev.hycolony.core.request.model.StackRequest;
import java.util.Optional;
import java.util.function.Function;

/**
 * The farming steps of the farmer's AI (MC EntityAIWorkFarmer prepareForFarming and canGoPlanting); the pass over a
 * field is {@link FieldPass}. Each step returns the next state.
 */
final class FarmWork {
    /** MC: a field stage with nothing to do is skipped; after this many skips in a row the field is left for today. */
    static final int MAX_SKIPS = 4;

    /** MC canGoPlanting: how many seeds the farmer asks for or takes from its hut. */
    static final int SEEDS_ASKED = 64;

    private final FarmWorkContext ctx;
    private final FieldScan scan;
    private final FieldPass pass;
    private int skippedState;
    /**
     * MC didWork set by prepareForFarming on the fourth skip: the next pass to end leaves its field whatever it did,
     * even a pass days later on another field (MC keeps didWork on the AI the same way).
     */
    private boolean forceLeave;

    private final WorkDelay delay = new WorkDelay();
    private boolean dumpRequested;
    private Optional<Msg> status = Optional.empty();

    FarmWork(FarmWorkContext ctx) {
        this.ctx = ctx;
        this.scan = new FieldScan(
                ctx.colony().context().ports().blocks(),
                ctx.colony().context().ports().catalog(),
                ctx.farming());
        this.pass = new FieldPass(ctx, scan, this);
    }

    /** Why the farmer does not work, for the citizen window (MC's blocking interaction); empty while it works. */
    Optional<Msg> status() {
        return status;
    }

    /** MC prepareForFarming, every 20 ticks: what the farmer does next with its fields. */
    FarmerState prepare() {
        status = Optional.empty();
        if (ctx.hut().level() < 1) {
            return FarmerState.PREPARING;
        }
        if (!fertilizerReady()) {
            return FarmerState.PREPARING;
        }
        if (ctx.colony().registries().fields().ownedBy(ctx.hut().position()).isEmpty()) {
            status = Optional.of(Msg.of("hycolony.farmer.noFields"));
            return FarmerState.IDLE;
        }
        Optional<FarmField> field = ctx.fields().fieldToWorkOn(ctx.colony(), ctx.hut());
        if (field.isEmpty()) {
            // Deviation from MC: says why the farmer waits (every field had its pass today); MC shows nothing.
            status = Optional.of(Msg.of("hycolony.farmer.fieldsDoneToday"));
            return FarmerState.IDLE;
        }
        if (ctx.tools().missing(ToolType.HOE, ctx.stock(), ctx::walkToHut)) {
            return FarmerState.PREPARING;
        }
        return byStage(field.get());
    }

    /** MC prepareForFarming's stage switch, then the skip of a stage with nothing to do. */
    private FarmerState byStage(FarmField field) {
        if (field.stage() == FieldStage.PLANTED && shouldExecute(field, scan::harvestable)) {
            return FarmerState.FARMER_HARVEST;
        }
        if (field.stage() == FieldStage.HOED) {
            return canGoPlanting(field);
        }
        if (field.stage() == FieldStage.EMPTY && shouldExecute(field, scan::hoeable)) {
            return FarmerState.FARMER_HOE;
        }
        field.nextStage();
        skipped();
        return FarmerState.IDLE;
    }

    /** MC: one more stage skipped; the fourth in a row leaves the field for today. */
    void skipped() {
        if (++skippedState >= MAX_SKIPS) {
            skippedState = 0;
            forceLeave = true;
            ctx.fields().resetCurrentField(ctx.colony());
        }
    }

    /**
     * MC workAtField's end: a pass that worked, one ending after four skipped stages ({@link #forceLeave}), or the
     * fourth pass in a row that did not work, leaves the field.
     */
    void endPass(boolean didWork) {
        boolean leave = didWork || forceLeave;
        forceLeave = false;
        if (leave || ++skippedState >= MAX_SKIPS) {
            ctx.fields().resetCurrentField(ctx.colony());
            skippedState = 0;
        }
    }

    /** MC workAtField, every 5 ticks: see {@link FieldPass}. */
    FarmerState workAtField(FarmerState state) {
        return pass.work(state);
    }

    /** MC setDelay: the ticks the farmer waits before its next step. */
    WorkDelay delay() {
        return delay;
    }

    /** MC shouldDumpInventory: set at the end of every pass. */
    void requestDump() {
        dumpRequested = true;
    }

    /** MC wantInventoryDumped: true once after each pass, then false until the next. */
    boolean consumeDumpRequest() {
        boolean was = dumpRequested;
        dumpRequested = false;
        return was;
    }

    /**
     * MC checkIfShouldExecute: walks the field's cells on from the one in progress (from the first when none is) until
     * one passes {@code test} and stays on it; false when none does.
     */
    boolean shouldExecute(FarmField field, Function<BlockPos, Optional<BlockPos>> test) {
        FieldWalk walk = ctx.fields().walk();
        while (walk.advance(field.radii())) {
            int[] o = walk.offset().orElseThrow();
            if (test.apply(field.pos().offset(o[0], -1, o[1])).isPresent()) {
                return true;
            }
        }
        return false;
    }

    /**
     * MC canGoPlanting: with the field's seed in hand, plant; otherwise, at the hut, take up to 64 or ask for them (one
     * request at a time) and skip the planting stage when none came.
     */
    FarmerState canGoPlanting(FarmField field) {
        Optional<ItemKey> seed = field.seed();
        if (seed.isEmpty()) {
            return FarmerState.PREPARING;
        }
        if (ctx.stock().inventory().count(seed.get()) > 0) {
            return FarmerState.FARMER_PLANT;
        }
        if (!ctx.walkToHut()) {
            return FarmerState.PREPARING;
        }
        if (ctx.stock().take(seed.get(), SEEDS_ASKED) <= 0) {
            askOnce(seed.get(), SEEDS_ASKED);
            field.nextStage();
        }
        return FarmerState.PREPARING;
    }

    /**
     * MC prepareForFarming's compost step, with Hytale's fertilizer tool (deviation 1 of the SP3b-2 spec): none
     * anywhere → one request while the setting is on; one in the hut but none carried → fetch it. False while fetching.
     * Deviation from MC: fetched while PREPARING, where MC goes through GATHERING_REQUIRED_MATERIALS.
     */
    private boolean fertilizerReady() {
        ItemKey fertilizer = ctx.farming().fertilizerItem();
        int carried = usable(fertilizer);
        int inHut = ctx.stock().hutCount(fertilizer);
        if (carried + inHut <= 0) {
            if (ctx.settings().fertilize()) {
                askOnce(fertilizer, 1);
            }
            return true;
        }
        if (carried <= 0) {
            if (!ctx.walkToHut()) {
                return false;
            }
            ctx.stock().take(fertilizer, 1);
        }
        return true;
    }

    /** The unworn stacks of {@code item} the farmer carries. */
    int usable(ItemKey item) {
        int n = 0;
        for (ItemAmount a : ctx.stock().inventory().contents()) {
            if (a.item().equals(item)
                    && !ctx.colony().context().ports().catalog().wornOut(a)) {
                n += a.count();
            }
        }
        return n;
    }

    /**
     * MC checkIfRequestForItemExistOrCreateAsync: a hut request for {@code item} unless one is still open. Called once
     * none is carried nor in the hut, so a completed one was delivered and used up: it is received (MC cleanAsync /
     * markRequestAsAccepted) and the item asked for again. Deviation from MC: filed for the hut, not the citizen, so
     * that the farmer does not wait for it (MC's async request); what is delivered is taken from the hut at the next
     * preparation, without MC's NEEDS_ITEM.
     */
    private void askOnce(ItemKey item, int count) {
        for (Request r : ctx.colony().requests().byRequester(ctx.hut().requesterId())) {
            if (!(r.requestable() instanceof StackRequest s) || !s.item().equals(item)) {
                continue;
            }
            if (r.state() == RequestState.COMPLETED) {
                ctx.colony().requests().updateState(r.token(), RequestState.RECEIVED);
            } else if (r.state().isBefore(RequestState.COMPLETED)) {
                return;
            }
        }
        ctx.colony().requests().createAndAssign(ctx.hut(), new StackRequest(item, count, 1, true), Request.NO_CITIZEN);
    }
}
