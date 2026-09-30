package dev.hycolony.plugin.command;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hycolony.api.CitizenRef;
import dev.hycolony.api.ColonyWorld;
import dev.hycolony.api.read.CitizenSnapshot;
import dev.hycolony.plugin.api.ColonyClock;
import dev.hycolony.plugin.api.HyColonyApi;
import java.util.Optional;

/**
 * Selftest steps of HyColony's api for other mods (spec 2026-09-30, § 4): installed, refusing another thread, a clock
 * that runs, and a citizen's body found both ways.
 */
final class ApiSelfTest {
    private ApiSelfTest() {}

    /** Reports "api", "api thread", "api clock" and, when a citizen's body is loaded, "api body". */
    static void run(SelfTestReport report, World world, Store<EntityStore> store) {
        HyColonyApi api;
        Optional<ColonyWorld> colonies;
        try {
            api = HyColonyApi.get();
            colonies = api.world(world);
        } catch (RuntimeException e) {
            report.line("api", false, e.toString());
            return;
        }
        report.line("api", colonies.isPresent(), "world() is empty");
        report.line("api thread", refusedOffThread(api, world), "a call from another thread did not throw");
        Optional<ColonyClock> clock = api.clock(world);
        report.line(
                "api clock", clock.map(c -> !c.paused() && !c.step(1)).orElse(false), "no clock, or paused already");
        colonies.flatMap(ApiSelfTest::someCitizen).ifPresent(c -> body(report, api, store, c));
    }

    /** Whether {@link HyColonyApi#world} throws when called off the world's thread. */
    private static boolean refusedOffThread(HyColonyApi api, World world) {
        boolean[] refused = {false};
        Thread other = new Thread(() -> {
            try {
                api.world(world);
            } catch (IllegalStateException e) {
                refused[0] = true;
            }
        });
        other.start();
        try {
            other.join(1000); // a short wait on the world thread: the call returns or throws at once
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return refused[0];
    }

    private static Optional<CitizenRef> someCitizen(ColonyWorld colonies) {
        return colonies.colonies().stream()
                .flatMap(c -> colonies.citizens(c.ref()).stream())
                .filter(c -> c.position().isPresent())
                .map(CitizenSnapshot::ref)
                .findFirst();
    }

    /** The body of {@code citizen}, then the citizen of that body: the same one. */
    private static void body(SelfTestReport report, HyColonyApi api, Store<EntityStore> store, CitizenRef citizen) {
        Optional<Ref<EntityStore>> body = api.bodyOf(citizen);
        Optional<CitizenRef> back = body.flatMap(b -> api.citizenOf(b, store));
        report.line("api body", back.equals(Optional.of(citizen)), "bodyOf then citizenOf gave " + back);
    }
}
