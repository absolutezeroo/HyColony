package dev.hycolony.core.colony;

import dev.hycolony.core.building.BuildingRegistry;
import dev.hycolony.core.citizen.CitizenNames;
import dev.hycolony.core.job.JobRegistry;
import dev.hycolony.core.kernel.WorldKey;
import dev.hycolony.core.kernel.config.ColonyConfig;
import dev.hycolony.core.kernel.event.EventBus;
import dev.hycolony.core.kernel.perf.TickTimings;
import dev.hycolony.core.kernel.port.CitizenBodies;
import dev.hycolony.core.kernel.port.GameClock;
import dev.hycolony.core.kernel.port.Notifier;
import dev.hycolony.core.kernel.port.PlayerDirectory;
import dev.hycolony.core.kernel.port.WorldQuery;
import dev.hycolony.core.kernel.port.body.BodyHealth;
import dev.hycolony.core.kernel.port.body.BodySeats;
import java.util.random.RandomGenerator;

/**
 * Everything a colony needs from the outside world, for one game world. The citizen bodies are three ports: their
 * moves and work ({@code bodies}), their health ({@code health}) and their seats ({@code seats}).
 */
public record ColonyContext(
        WorldKey world,
        ColonyConfig config,
        GameClock clock,
        CitizenBodies bodies,
        BodyHealth health,
        BodySeats seats,
        WorldQuery worldQuery,
        Notifier notifier,
        PlayerDirectory players,
        BuildingRegistry buildingTypes,
        JobRegistry jobs,
        CitizenNames names,
        RandomGenerator random,
        EventBus bus,
        GamePorts ports,
        TickTimings timings) {}
