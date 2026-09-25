package dev.hycolony.core.testing;

import dev.hycolony.core.building.BuildingTypes;
import dev.hycolony.core.citizen.CitizenNames;
import dev.hycolony.core.colony.ColonyContext;
import dev.hycolony.core.colony.ConstructionPorts;
import dev.hycolony.core.kernel.WorldKey;
import dev.hycolony.core.kernel.config.ColonyConfig;
import dev.hycolony.core.kernel.event.EventBus;
import java.util.Random;

/** A fully faked colony context. Fields are public so tests can steer the fakes. */
public final class TestContexts {
    public final FakeClock clock = new FakeClock();
    public final FakeBodies bodies = new FakeBodies();
    public final FakeWorld world = new FakeWorld();
    public final FakeNotifier notifier = new FakeNotifier();
    public final FakePlayers players = new FakePlayers();
    public final FakeUi ui = new FakeUi();
    public final EventBus bus = new EventBus();
    public ColonyConfig config = ColonyConfig.defaults();

    public ColonyContext context() {
        return new ColonyContext(new WorldKey("world"), config, clock, bodies, world, notifier, ui, players,
                BuildingTypes.defaults(), CitizenNames.loadDefault(), new Random(1234), bus,
                ConstructionPorts.unavailable());
    }
}
