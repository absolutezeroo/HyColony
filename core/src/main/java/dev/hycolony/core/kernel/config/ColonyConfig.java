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
        HyColony hycolony) {

    /** MC ServerConfiguration, section gameplay. */
    public record Gameplay(
            int initialCitizenAmount,
            /** Read by nothing yet: MC only caps immigration and births (housing), and forces the initial spawn. */
            int maxCitizenPerColony,
            /** MC workersalwaysworkinrain: rain never stops a worker (CitizenAI.shouldWorkWhileRaining). */
            boolean workersAlwaysWorkInRain) {
        public Gameplay {
            initialCitizenAmount = clamp(initialCitizenAmount, 1, 10);
            maxCitizenPerColony = clamp(maxCitizenPerColony, 25, 500);
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
            maxColonySize = clamp(maxColonySize, 1, 250);
            minColonyDistance = clamp(minColonyDistance, 1, 200);
            initialColonySize = clamp(initialColonySize, 1, 15);
            maxDistanceFromWorldSpawn = clamp(maxDistanceFromWorldSpawn, 1000, Integer.MAX_VALUE);
            minDistanceFromWorldSpawn = clamp(minDistanceFromWorldSpawn, 0, 1000);
        }
    }

    /** MC ServerConfiguration, section permissions. */
    public record Permissions(
            boolean enableColonyProtection,
            Explosions turnOffExplosionsInColonies,
            /** MC operator level 0-4; see ColonyManager.isAllowed for its Hytale meaning. */
            int permissionEventBypassMinPermLevel) {
        public Permissions {
            turnOffExplosionsInColonies =
                    turnOffExplosionsInColonies == null ? Explosions.DAMAGE_ENTITIES : turnOffExplosionsInColonies;
            permissionEventBypassMinPermLevel = clamp(permissionEventBypassMinPermLevel, 0, 4);
        }
    }

    /** MC ServerConfiguration, section commands: which subcommands non-operators may run. */
    public record Commands(
            boolean canPlayerUseShowColonyInfoCommand,
            boolean canPlayerUseAddOfficerCommand,
            boolean canPlayerUseDeleteColonyCommand) {}

    /**
     * MC ClientConfiguration, section gameplay.
     *
     * <p>Deviation from MC: a server setting here, since a Hytale plugin has no client-side config.
     */
    public record Client(int buildGoggleRange) {
        public Client {
            buildGoggleRange = clamp(buildGoggleRange, 1, 250);
        }
    }

    /** HyColony's own options: none of them is a MineColonies config option. */
    public record HyColony(
            /** Colonies are saved every that many minutes (MC saves with the Minecraft world). */
            int autosaveIntervalMinutes,
            /**
             * Builders need no resources: every build, upgrade and repair order is free. Deviation from MC: MC has
             * this as a hard-coded constant, Constants.BUILDER_INF_RESOURECES (false), not as a config option.
             */
            boolean builderInfiniteResources,
            /** Orders made by an operator in creative mode are free. */
            boolean creativeOperatorFreeBuilds) {
        public HyColony {
            autosaveIntervalMinutes = clamp(autosaveIntervalMinutes, 1, 60);
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    /** MineColonies' defaults, and ours for the HyColony section. */
    public static ColonyConfig defaults() {
        return new ColonyConfig(
                new Gameplay(4, 250, false),
                new Claims(20, 8, 4, 30000, 0),
                new Permissions(true, Explosions.DAMAGE_ENTITIES, 2),
                new Commands(true, true, false),
                new Client(50),
                new HyColony(5, false, true));
    }
}
