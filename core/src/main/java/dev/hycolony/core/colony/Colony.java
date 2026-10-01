package dev.hycolony.core.colony;

import dev.hycolony.core.building.BuildingManager;
import dev.hycolony.core.citizen.CitizenManager;
import dev.hycolony.core.citizen.food.HungerTicks;
import dev.hycolony.core.citizen.happiness.HappinessEvents;
import dev.hycolony.core.citizen.sleep.SleepNotice;
import dev.hycolony.core.colony.permission.Permissions;
import dev.hycolony.core.colony.territory.ClaimCell;
import dev.hycolony.core.colony.territory.TerritoryIndex;
import dev.hycolony.core.construction.workorder.WorkManager;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.ai.AITarget;
import dev.hycolony.core.kernel.ai.IStateSupplier;
import dev.hycolony.core.kernel.ai.TickRateStateMachine;
import dev.hycolony.core.request.RequestManager;
import dev.hycolony.core.request.Requester;
import dev.hycolony.core.request.model.RequesterId;
import dev.hycolony.core.request.resolver.PlayerResolver;
import dev.hycolony.core.request.resolver.RetryingResolver;
import java.util.Optional;
import java.util.OptionalInt;

/** One colony. Ticked 20 times per second on its world thread. */
public final class Colony {
    public static final int UPDATE_STATE_INTERVAL = 100;
    public static final int CITIZEN_DATA_INTERVAL = 60;
    public static final int DAYTIME_INTERVAL = 20;
    public static final int SLOW_TICK = 500;
    /** MineColonies delays a colony for 5 minutes after an exception. */
    public static final int EXCEPTION_SUSPEND_TICKS = 5 * 60 * 20;

    private static final System.Logger LOG = System.getLogger(Colony.class.getName());

    /** What a colony is founded with (or loaded with): its id, first name, centre and permissions. */
    public record Founding(int id, String name, BlockPos center, Permissions permissions) {}

    private final ColonyContext ctx;
    private final TerritoryIndex territory;
    private final int id;
    private String name;
    private final BlockPos center;
    private final Permissions permissions;
    private final BuildingManager buildings;
    private final RequestManager requests;
    private final CitizenManager citizens;
    private final WorkManager work = new WorkManager(this);
    private final CitizenNameplates nameplates = new CitizenNameplates(this);
    private final ColonySettings settings = new ColonySettings();
    private final EventLog log = new EventLog();
    private final ColonyRegistries registries = new ColonyRegistries();
    private final TickRateStateMachine<ColonyState> machine;
    private int day;
    private boolean wasDaytime;
    private boolean dirty;
    private long suspendedUntilTick = Long.MIN_VALUE;

    public Colony(ColonyContext ctx, TerritoryIndex territory, Founding founding) {
        this.ctx = ctx;
        this.territory = territory;
        this.id = founding.id();
        this.name = founding.name();
        this.center = founding.center();
        this.permissions = founding.permissions();
        this.requests = new RequestManager(this::requester, ctx.ports().catalog());
        this.buildings = new BuildingManager(new ColonyBuildingListener(this));
        requests.registerBuiltIn(new PlayerResolver(center));
        requests.registerBuiltIn(new RetryingResolver(center));
        requests.setStateListener(new RequestStatePoster(ctx.bus(), id));
        this.citizens = new CitizenManager(this);
        this.wasDaytime = ctx.clock().isDaytime();
        this.machine = new TickRateStateMachine<>(ColonyState.INACTIVE, this::onException);
        registerTicks();
    }

    /** The colony's periodic work: the state update in every state, the rest only while ACTIVE. */
    private void registerTicks() {
        IStateSupplier<ColonyState> activity = () -> ColonyState.of(this);
        for (ColonyState s : ColonyState.values()) {
            machine.addTransition(new AITarget<>(s, activity, UPDATE_STATE_INTERVAL));
        }
        // Each part is timed for HyLens's /hylens perf: Hytale measures the whole core as one system.
        var t = ctx.timings();
        machine.addTransition(
                AITarget.every(ColonyState.ACTIVE, t.timed("citizen data", citizens::tickData), CITIZEN_DATA_INTERVAL));
        machine.addTransition(
                AITarget.every(ColonyState.ACTIVE, t.timed("daytime", this::checkDayTime), DAYTIME_INTERVAL));
        // Deviation from MC: hunger and healing run for every citizen at once here, not on each citizen's entity, and
        // only while the colony is ACTIVE; the state update ends a tick every 100, so they come about 1 % later.
        machine.addTransition(AITarget.every(
                ColonyState.ACTIVE,
                t.timed("hunger", () -> HungerTicks.decreaseIdleSaturation(this)),
                HungerTicks.SATURATION_DECREASE_AFTER));
        machine.addTransition(AITarget.every(
                ColonyState.ACTIVE,
                t.timed("healing", () -> HungerTicks.updateHealing(this)),
                HungerTicks.HEAL_CITIZENS_AFTER));
        machine.addTransition(AITarget.every(ColonyState.ACTIVE, t.timed("colony upkeep", this::slowTick), SLOW_TICK));
        machine.addTransition(
                AITarget.every(ColonyState.ACTIVE, t.timed("requests", requests::tick), RequestManager.TICK_INTERVAL));
        machine.addTransition(
                AITarget.every(ColonyState.ACTIVE, t.timed("work orders", work::tick), WorkManager.TICK_INTERVAL));
        machine.addTransition(AITarget.every(
                ColonyState.ACTIVE, t.timed("nameplates", nameplates::refresh), CitizenNameplates.INTERVAL));
    }

    public void tick() {
        if (isSuspended()) {
            return;
        }
        try {
            machine.tick();
            citizens.tickAi();
        } catch (RuntimeException e) {
            onException(e);
        }
    }

    public boolean isSuspended() {
        return ctx.clock().currentTick() < suspendedUntilTick;
    }

    private void onException(RuntimeException e) {
        LOG.log(System.Logger.Level.ERROR, "Colony " + id + " (" + name + ") failed, suspending for 5 minutes", e);
        suspendedUntilTick = ctx.clock().currentTick() + EXCEPTION_SUSPEND_TICKS;
    }

    private void checkDayTime() {
        boolean daytime = ctx.clock().isDaytime();
        if (daytime && !wasDaytime) {
            day++;
            markDirty();
            citizens.onWakeUp(); // MC Colony.checkDayTime: citizenManager.onWakeUp()
            ctx.bus().post(new ColonyEvents.DayStarted(this));
        } else if (!daytime && wasDaytime) {
            HappinessEvents.onNightFall(this); // MC: checkCitizensForHappiness, then updateCitizenSleep(false)
            SleepNotice.onNightFall(this);
            ctx.bus().post(new ColonyEvents.NightFell(this));
        }
        wasDaytime = daytime;
    }

    private void slowTick() {
        buildings.onColonyTick(this);
        citizens.onColonyTick();
        if (registries
                .fields()
                .cleanUp(ctx.worldQuery()::isLoaded, this::contains, ctx.ports().farming()::isFieldBlock)) {
            markDirty();
        }
    }

    private Optional<Requester> requester(RequesterId id) {
        return buildings.byRequester(id).map(Requester.class::cast);
    }

    /**
     * Claims the free cells within {@code radius} of {@code pos}'s cell, never beyond {@code maxColonySize} from the
     * centre and never stealing a cell.
     */
    public void claimAround(BlockPos pos, int radius) {
        territory.claimSquareBounded(
                id,
                ClaimCell.of(pos),
                radius,
                ClaimCell.of(center),
                ctx.config().claims().maxColonySize());
        markDirty();
    }

    public boolean contains(BlockPos pos) {
        OptionalInt owner = territory.colonyAt(pos);
        return owner.isPresent() && owner.getAsInt() == id;
    }

    public int id() {
        return id;
    }

    public String name() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
        markDirty();
    }

    public BlockPos center() {
        return center;
    }

    public Permissions permissions() {
        return permissions;
    }

    public BuildingManager buildings() {
        return buildings;
    }

    public CitizenManager citizens() {
        return citizens;
    }

    public ColonySettings settings() {
        return settings;
    }

    public RequestManager requests() {
        return requests;
    }

    public WorkManager work() {
        return work;
    }

    public CitizenNameplates nameplates() {
        return nameplates;
    }

    public EventLog log() {
        return log;
    }

    /** The colony-wide registries: learnt recipes and fields. */
    public ColonyRegistries registries() {
        return registries;
    }

    public int day() {
        return day;
    }

    public void setDay(int day) {
        this.day = day;
    }

    public ColonyState state() {
        return machine.getState();
    }

    public ColonyContext context() {
        return ctx;
    }

    public boolean isDirty() {
        return dirty;
    }

    public void markDirty() {
        dirty = true;
    }

    public void clearDirty() {
        dirty = false;
    }
}
