package dev.hycolony.core.colony;

import dev.hycolony.core.building.BuildingRegistry;
import dev.hycolony.core.citizen.CitizenNames;
import dev.hycolony.core.colony.ui.UiPort;
import dev.hycolony.core.kernel.WorldKey;
import dev.hycolony.core.kernel.config.ColonyConfig;
import dev.hycolony.core.kernel.event.EventBus;
import dev.hycolony.core.kernel.port.CitizenBodies;
import dev.hycolony.core.kernel.port.GameClock;
import dev.hycolony.core.kernel.port.Notifier;
import dev.hycolony.core.kernel.port.PlayerDirectory;
import dev.hycolony.core.kernel.port.WorldQuery;
import java.util.random.RandomGenerator;

/** Everything a colony needs from the outside world, for one game world. */
public record ColonyContext(
        WorldKey world,
        ColonyConfig config,
        GameClock clock,
        CitizenBodies bodies,
        WorldQuery worldQuery,
        Notifier notifier,
        UiPort ui,
        PlayerDirectory players,
        BuildingRegistry buildingTypes,
        CitizenNames names,
        RandomGenerator random,
        EventBus bus) {}
