package dev.hycolony.plugin.bridge;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.plugin.PluginBase;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hycolony.api.CitizenRef;
import dev.hycolony.api.ColonyRef;
import dev.hycolony.api.ColonyWorld;
import dev.hycolony.api.Subscription;
import dev.hycolony.core.app.api.CoreColonyWorld;
import dev.hycolony.plugin.WorldRuntime;
import dev.hycolony.plugin.WorldRuntimes;
import dev.hycolony.plugin.api.ColonyClock;
import dev.hycolony.plugin.api.ColonyWorldEvent;
import dev.hycolony.plugin.api.HyColonyApi;
import dev.hycolony.plugin.api.HyColonyApiHolder;
import dev.hycolony.plugin.npc.CitizenTag;
import dev.hycolony.plugin.npc.HyColonyComponents;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** HyColony's {@link HyColonyApi} over its worlds' runtimes (spec 2026-09-30, § 4). */
public final class ApiBridge implements HyColonyApi {
    private final WorldRuntimes runtimes;
    private final WorldAnnouncer announcer = new WorldAnnouncer();
    /** Each running world's api, made at its first use; forgotten when HyColony stops in that world. */
    private final Map<WorldRuntime, CoreColonyWorld> apis = new ConcurrentHashMap<>();

    private ApiBridge(WorldRuntimes runtimes) {
        this.runtimes = runtimes;
    }

    /** HyColony's setup: the api over {@code runtimes}, installed for {@link HyColonyApi#get}. */
    public static ApiBridge install(WorldRuntimes runtimes) {
        ApiBridge api = new ApiBridge(runtimes);
        HyColonyApiHolder.install(api);
        return api;
    }

    /** HyColony's shutdown: no api any more. */
    public static void uninstall() {
        HyColonyApiHolder.clear();
    }

    @Override
    public Optional<ColonyWorld> world(World world) {
        ApiThreads.check(world);
        return running(world).map(this::apiOf);
    }

    @Override
    public Optional<CitizenRef> citizenOf(Ref<EntityStore> entity, ComponentAccessor<EntityStore> accessor) {
        World world = accessor.getExternalData().getWorld();
        ApiThreads.check(world);
        if (running(world).isEmpty() || !entity.isValid()) {
            return Optional.empty();
        }
        CitizenTag tag = accessor.getComponent(entity, HyColonyComponents.citizenTag());
        return tag == null
                ? Optional.empty()
                : Optional.of(new CitizenRef(new ColonyRef(world.getName(), tag.colonyId()), tag.citizenId()));
    }

    @Override
    public Optional<Ref<EntityStore>> bodyOf(CitizenRef citizen) {
        return byName(citizen.colony().world()).flatMap(rt -> {
            ApiThreads.check(rt.world());
            return rt.manager()
                    .byId(citizen.colony().colonyId())
                    .flatMap(c -> c.citizens().bodyOf(citizen.citizenId()))
                    .flatMap(rt.bodies()::entity);
        });
    }

    @Override
    public <E> Optional<Subscription> subscribe(
            PluginBase owner, World world, Class<E> type, Consumer<? super E> listener) {
        return world(world).map(cw -> OwnerBinding.bound(owner, cw.subscribe(type, listener)));
    }

    @Override
    public Optional<Subscription> track(PluginBase owner, CitizenRef citizen) {
        return byName(citizen.colony().world()).flatMap(rt -> {
            ApiThreads.check(rt.world());
            return apiOf(rt).debug().track(citizen).map(s -> OwnerBinding.bound(owner, s));
        });
    }

    @Override
    public Subscription subscribeWorlds(PluginBase owner, Consumer<? super ColonyWorldEvent> listener) {
        return OwnerBinding.bound(owner, announcer.add(listener));
    }

    @Override
    public Optional<ColonyClock> clock(World world) {
        ApiThreads.check(world);
        return running(world).map(rt -> new BridgeClock(world, rt.clockState()));
    }

    /** HyColony started in {@code world}, enabled: tells the listeners, each alone, on its thread. */
    public void started(World world) {
        announcer.started(world);
    }

    /** {@code world} is removed: tells the listeners, each alone, if its start was told. */
    public void stopped(World world) {
        apis.keySet().removeIf(rt -> rt.world().equals(world));
        announcer.stopped(world);
    }

    private ColonyWorld apiOf(WorldRuntime rt) {
        return apis.computeIfAbsent(rt, r -> new CoreColonyWorld(r.manager(), r.world()::isInThread));
    }

    private Optional<WorldRuntime> running(World world) {
        return Optional.ofNullable(runtimes.of(world)).filter(WorldRuntime::enabled);
    }

    private Optional<WorldRuntime> byName(String world) {
        return runtimes.all().stream()
                .filter(rt -> rt.world().getName().equals(world) && rt.enabled())
                .findFirst();
    }
}
