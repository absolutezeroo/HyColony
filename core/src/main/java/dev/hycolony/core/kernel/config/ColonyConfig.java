package dev.hycolony.core.kernel.config;

/**
 * Server configuration, in MineColonies' config sections (MC ServerConfiguration, ClientConfiguration). Defaults and
 * bounds are MC's; every section clamps its values, so an out-of-range {@code config.json} value never fails.
 */
public record ColonyConfig(
        Gameplay gameplay,
        Claims claims,
        Permissions permissions,
        Commands commands,
        Client client,
        HyColony hycolony,
        Structurize structurize) {

    /**
     * MC ServerConfiguration, section gameplay.
     *
     * @param maxCitizenPerColony the cap of the colony's housing (HousingCapacity, MC getMaxCitizens); MC also caps
     *     immigration and births with it, not ported
     * @param workersAlwaysWorkInRain MC workersalwaysworkinrain: rain never stops a worker
     *     (CitizenAI.shouldWorkWhileRaining)
     * @param foodModifier MC foodmodifier: multiplies every saturation a citizen loses (CitizenHunger.decrease)
     */
    public record Gameplay(
            int initialCitizenAmount, int maxCitizenPerColony, boolean workersAlwaysWorkInRain, double foodModifier) {
        public Gameplay {
            initialCitizenAmount = Math.clamp(initialCitizenAmount, 1, 10);
            maxCitizenPerColony = Math.clamp(maxCitizenPerColony, 25, 500);
            foodModifier = Double.isNaN(foodModifier) ? 1.0 : Math.clamp(foodModifier, 0.1, 100);
        }
    }

    /** MC ServerConfiguration, section claims; sizes in claim cells, spawn distances in blocks. */
    public record Claims(
            int maxColonySize,
            int minColonyDistance,
            int initialColonySize,
            int maxDistanceFromWorldSpawn,
            int minDistanceFromWorldSpawn) {
        public Claims {
            maxColonySize = Math.clamp(maxColonySize, 1, 250);
            minColonyDistance = Math.clamp(minColonyDistance, 1, 200);
            initialColonySize = Math.clamp(initialColonySize, 1, 15);
            maxDistanceFromWorldSpawn = Math.clamp(maxDistanceFromWorldSpawn, 1000, Integer.MAX_VALUE);
            minDistanceFromWorldSpawn = Math.clamp(minDistanceFromWorldSpawn, 0, 1000);
        }
    }

    /**
     * MC ServerConfiguration, section permissions.
     *
     * @param permissionEventBypassMinPermLevel MC operator level 0-4; see ColonyManager.isAllowed for its Hytale
     *     meaning
     */
    public record Permissions(
            boolean enableColonyProtection,
            Explosions turnOffExplosionsInColonies,
            int permissionEventBypassMinPermLevel) {
        public Permissions {
            turnOffExplosionsInColonies =
                    turnOffExplosionsInColonies == null ? Explosions.DAMAGE_ENTITIES : turnOffExplosionsInColonies;
            permissionEventBypassMinPermLevel = Math.clamp(permissionEventBypassMinPermLevel, 0, 4);
        }
    }

    /**
     * MC ServerConfiguration, section commands: which subcommands non-operators may run.
     *
     * @param canPlayerUseModifyCitizensCommand MC canplayerusemodifycitizenscommand: a colony manager who is not an
     *     operator may change a citizen's saturation (/mc citizens modify, HyColony's DebugAccess.modifySaturation)
     */
    public record Commands(
            boolean canPlayerUseShowColonyInfoCommand,
            boolean canPlayerUseAddOfficerCommand,
            boolean canPlayerUseDeleteColonyCommand,
            boolean canPlayerUseModifyCitizensCommand) {}

    /**
     * MC ClientConfiguration, section gameplay.
     *
     * <p>Deviation from MC: a server setting here, since a Hytale plugin has no client-side config.
     *
     * @param colonyTeamBorders the build tool's colony borders take each colony's team colour, else white for the
     *     nearest colony and red for the others (MC colonyteamborders)
     */
    public record Client(int buildGoggleRange, boolean colonyTeamBorders) {
        public Client {
            buildGoggleRange = Math.clamp(buildGoggleRange, 1, 250);
        }
    }

    /**
     * HyColony's own options: none of them is a MineColonies config option.
     *
     * @param autosaveIntervalMinutes colonies are saved every that many minutes (MC saves with the Minecraft world)
     * @param builderInfiniteResources builders need no resources: every build, upgrade and repair order is free.
     *     Deviation from MC: MC has this as a hard-coded constant, Constants.BUILDER_INF_RESOURECES (false), not as a
     *     config option
     * @param creativeOperatorFreeBuilds orders made by an operator in creative mode are free
     */
    public record HyColony(
            int autosaveIntervalMinutes, boolean builderInfiniteResources, boolean creativeOperatorFreeBuilds) {
        public HyColony {
            autosaveIntervalMinutes = Math.clamp(autosaveIntervalMinutes, 1, 60);
        }
    }

    /**
     * Structurize ServerConfiguration: {@code maxOperationsPerTick} is the blocks a creative paste changes per tick
     * (ST StructurePlacer.getStepsPerCall).
     *
     * <p>Deviation from MC: the minimum is 1, not 0; at 0 a paste would never progress.
     */
    public record Structurize(int maxOperationsPerTick) {
        public Structurize {
            maxOperationsPerTick = Math.clamp(maxOperationsPerTick, 1, 100_000);
        }
    }

    /** MineColonies' defaults, and ours for the HyColony section. */
    public static ColonyConfig defaults() {
        return new ColonyConfig(
                new Gameplay(4, 250, false, 1.0),
                new Claims(20, 8, 4, 30000, 0),
                new Permissions(true, Explosions.DAMAGE_ENTITIES, 2),
                new Commands(true, true, false, false),
                new Client(50, true),
                new HyColony(5, false, true),
                new Structurize(1000));
    }
}
