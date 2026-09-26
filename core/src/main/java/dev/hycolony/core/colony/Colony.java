package dev.hycolony.core.colony;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.building.BuildingManager;
import dev.hycolony.core.building.BuildingModule;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.citizen.CitizenManager;
import dev.hycolony.core.colony.ui.NeedsPlayerNotice;
import dev.hycolony.core.construction.WorkManager;
import dev.hycolony.core.job.WorkerModule;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.ai.AITarget;
import dev.hycolony.core.kernel.ai.IStateSupplier;
import dev.hycolony.core.kernel.ai.TickRateStateMachine;
import dev.hycolony.core.request.Request;
import dev.hycolony.core.request.RequestManager;
import dev.hycolony.core.request.Requester;
import dev.hycolony.core.request.RequesterId;
import dev.hycolony.core.request.resolver.PlayerResolver;
import dev.hycolony.core.request.resolver.RetryingResolver;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;

/** One colony. Ticked 20 times per second on its world thread. */
public final class Colony {
    public static final int UPDATE_STATE_INTERVAL = 100;
    public static final int CITIZEN_DATA_INTERVAL = 60;
    public static final int DAYTIME_INTERVAL = 20;
    public static final int SLOW_TICK = 500;
    /** MineColonies delays a colony for 5 minutes after an exception. */
    public static final int EXCEPTION_SUSPEND_TICKS = 5 * 60 * 20;

    private static final System.Logger LOG = System.getLogger(Colony.class.getName());

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
    private final TickRateStateMachine<ColonyState> machine;
    private int day;
    private boolean wasDaytime;
    private boolean dirty;
    private long suspendedUntilTick = Long.MIN_VALUE;

    public Colony(
            ColonyContext ctx,
            TerritoryIndex territory,
            int id,
            String name,
            BlockPos center,
            Permissions permissions) {
        this.ctx = ctx;
        this.territory = territory;
        this.id = id;
        this.name = name;
        this.center = center;
        this.permissions = permissions;
        this.requests = new RequestManager(this::requester, ctx.ports().catalog());
        this.buildings = new BuildingManager(new BuildingManager.Listener() {
            @Override
            public void added(Building building) {
                building.attachContainers(ctx.ports().containers());
                requests.onProviderAdded(building);
            }

            @Override
            public void removed(Building building) {
                requests.cancelAllFrom(building.requesterId());
                requests.onProviderRemoved(building);
                work.onBuildingRemoved(building.position());
                for (BuildingModule module : building.modules().values()) {
                    if (module instanceof WorkerModule worker) {
                        // Snapshot: fire() mutates worker.workers(), which this would otherwise iterate live.
                        for (int citizenId : List.copyOf(worker.workers())) {
                            worker.fire(Colony.this, building, citizenId);
                        }
                    }
                }
            }
        });
        PlayerResolver playerResolver = new PlayerResolver(center);
        playerResolver.setOnNeedsPlayer(this::announceNeedsPlayer);
        requests.registerBuiltIn(playerResolver);
        requests.registerBuiltIn(new RetryingResolver(center));
        this.citizens = new CitizenManager(this);
        this.wasDaytime = ctx.clock().isDaytime();
        this.machine = new TickRateStateMachine<>(ColonyState.INACTIVE, this::onException);
        for (ColonyState s : ColonyState.values()) {
            machine.addTransition(
                    new AITarget<>(s, (IStateSupplier<ColonyState>) this::updateState, UPDATE_STATE_INTERVAL));
        }
        machine.addTransition(new AITarget<>(
                ColonyState.ACTIVE,
                (IStateSupplier<ColonyState>) () -> {
                    citizens.tickData();
                    return null;
                },
                CITIZEN_DATA_INTERVAL));
        machine.addTransition(new AITarget<>(
                ColonyState.ACTIVE,
                (IStateSupplier<ColonyState>) () -> {
                    checkDayTime();
                    return null;
                },
                DAYTIME_INTERVAL));
        machine.addTransition(new AITarget<>(
                ColonyState.ACTIVE,
                (IStateSupplier<ColonyState>) () -> {
                    slowTick();
                    return null;
                },
                SLOW_TICK));
        machine.addTransition(new AITarget<>(
                ColonyState.ACTIVE,
                (IStateSupplier<ColonyState>) () -> {
                    requests.tick();
                    return null;
                },
                RequestManager.TICK_INTERVAL));
        machine.addTransition(new AITarget<>(
                ColonyState.ACTIVE,
                (IStateSupplier<ColonyState>) () -> {
                    work.tick();
                    return null;
                },
                WorkManager.TICK_INTERVAL));
        machine.addTransition(new AITarget<>(
                ColonyState.ACTIVE,
                (IStateSupplier<ColonyState>) () -> {
                    nameplates.refresh();
                    return null;
                },
                CitizenNameplates.INTERVAL));
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

    /** MineColonies-equivalent activity rule (spec § 3.2). */
    private ColonyState updateState() {
        boolean playerInside = false;
        boolean memberOnline = false;
        for (UUID player : ctx.players().onlineIn(ctx.world())) {
            if (ctx.players().position(player).map(this::contains).orElse(false)) {
                playerInside = true;
            }
            if (permissions.isMember(player)) {
                memberOnline = true;
            }
        }
        if (playerInside || (memberOnline && ctx.worldQuery().isLoaded(center))) {
            return ColonyState.ACTIVE;
        }
        return memberOnline ? ColonyState.UNLOADED : ColonyState.INACTIVE;
    }

    private void checkDayTime() {
        boolean daytime = ctx.clock().isDaytime();
        if (daytime && !wasDaytime) {
            day++;
            markDirty();
            ctx.bus().post(new ColonyEvents.DayStarted(this));
        } else if (!daytime && wasDaytime) {
            ctx.bus().post(new ColonyEvents.NightFell(this));
        }
        wasDaytime = daytime;
    }

    private void slowTick() {
        buildings.onColonyTick(this);
        citizens.onColonyTick();
    }

    /**
     * MC StandardPlayerRequestResolver's "needs" message: a request only a player can provide is told to the online
     * owner and officers, naming the citizen (or the building) and its job.
     */
    private void announceNeedsPlayer(Request r) {
        Building b = buildings.byRequester(r.requester()).orElse(null);
        Optional<CitizenData> citizen = r.citizenId() != -1
                ? citizens.get(r.citizenId())
                : Optional.ofNullable(b)
                        .flatMap(hut -> hut.module(WorkerModule.class))
                        .flatMap(w -> w.workers().stream().findFirst())
                        .flatMap(citizens::get);
        String who = r.citizenId() != -1
                ? citizen.map(CitizenData::name).orElse("")
                : b != null ? b.displayName() : r.requester().value();
        String job = citizen.flatMap(CitizenData::job).map(j -> j.type().id()).orElse("");
        NeedsPlayerNotice notice = new NeedsPlayerNotice(who, job, r.requestable());
        Set<UUID> to = new LinkedHashSet<>();
        to.add(permissions.owner());
        permissions.members().forEach((p, m) -> {
            if (m.rankId() <= Permissions.OFFICER) {
                to.add(p);
            }
        });
        for (UUID p : to) {
            if (ctx.players().isOnline(p)) {
                ctx.ui().notifyNeedsPlayer(p, notice);
            }
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
                ctx.config().maxColonySize());
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
