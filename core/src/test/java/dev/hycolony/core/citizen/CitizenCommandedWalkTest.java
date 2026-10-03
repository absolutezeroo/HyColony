package dev.hycolony.core.citizen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.citizen.vitals.EndedWalk;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.permission.Permissions;
import dev.hycolony.core.colony.territory.TerritoryIndex;
import dev.hycolony.core.job.Job;
import dev.hycolony.core.job.JobAI;
import dev.hycolony.core.job.JobType;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.Vec3;
import dev.hycolony.core.kernel.nav.WalkEnd;
import dev.hycolony.core.kernel.port.BodyId;
import dev.hycolony.core.kernel.port.NavStatus;
import dev.hycolony.core.testing.FakeBlueprints;
import dev.hycolony.core.testing.TestContexts;
import dev.hycolony.core.testing.TestJobs;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** MC CommandCitizenTriggerWalkTo: a citizen sent somewhere walks there, its AI waiting (spec 2026-09-30, § 5). */
class CitizenCommandedWalkTest {
    private static final BlockPos THERE = new BlockPos(30, 64, 0);
    private static final BlockPos ELSEWHERE = new BlockPos(0, 64, 30);

    private final TestContexts t = new TestContexts();
    private final Colony colony = new Colony(
            t.context(),
            new TerritoryIndex(),
            new Colony.Founding(1, "T", new BlockPos(0, 64, 0), Permissions.createDefault(UUID.randomUUID(), "A")));
    private final CitizenData citizen = new CitizenData(1);
    private final BodyId body = t.bodies.existing(1, 1, new Vec3(0.5, 64, 0.5));
    /** How many times its job AI ticked. */
    private int jobTicks;
    /** How many job AIs its job made. */
    private int jobAis;
    /** How many times its job AI was reset. */
    private int jobResets;

    CitizenCommandedWalkTest() {
        colony.citizens().restore(citizen);
    }

    /** A citizen whose job AI counts its ticks, working. */
    private CitizenAI worker() {
        citizen.setJob(new JobType("test:counting", c -> new Job(TestJobs.TYPE, c) {
                    @Override
                    public JobAI createAI(Colony colony, BodyId body) {
                        jobAis++;
                        return new JobAI() {
                            @Override
                            public void tick() {
                                jobTicks++;
                            }

                            @Override
                            public String stateName() {
                                return "WORK";
                            }

                            @Override
                            public boolean canBeInterrupted() {
                                return true;
                            }

                            @Override
                            public void resetAI() {
                                jobResets++;
                            }
                        };
                    }
                })
                .factory()
                .apply(citizen));
        CitizenAI ai = new CitizenAI(colony, citizen, body);
        for (int i = 0; i < 30 && ai.state() != CitizenState.WORKING; i++) {
            tick(ai);
        }
        assertEquals(CitizenState.WORKING, ai.state());
        return ai;
    }

    private void tick(CitizenAI ai) {
        t.clock.tick++;
        ai.tick();
    }

    /** Ticks {@code ai} until its job AI ticks again; returns how many ticks that took. */
    private int ticksUntilItWorks(CitizenAI ai, int max) {
        int before = jobTicks;
        int n = 0;
        while (jobTicks == before && n < max) {
            tick(ai);
            n++;
        }
        return n;
    }

    private Vec3 position() {
        return t.bodies.position(body).orElseThrow();
    }

    @Test
    void workerWalksThereThenHoldsStillBeforeWorkingAgain() {
        t.bodies.instant = true;
        CitizenAI ai = worker();

        ai.walkTo(THERE);
        int ticks = ticksUntilItWorks(ai, 1000);

        assertEquals(Vec3.center(THERE), position());
        assertTrue(
                ticks > CommandedWalk.HOLD_TICKS && ticks <= CommandedWalk.HOLD_TICKS + 3,
                "it arrived, held still " + CommandedWalk.HOLD_TICKS + " ticks, then worked: " + ticks);
        assertEquals(Optional.of(THERE), citizen.vitals().walks().target(), "its walk is in its vital signs");
    }

    @Test
    void navEndingWithinFourBlocksCountsAsThere() {
        t.bodies.navEndsAt = new Vec3(27.5, 64, 0.5); // 3 blocks short: MC walkToPos(target, 4)
        t.bodies.navEndStatus = NavStatus.BLOCKED;
        CitizenAI ai = worker();

        ai.walkTo(THERE);
        int ticks = ticksUntilItWorks(ai, 1000);

        assertTrue(ticks <= CommandedWalk.HOLD_TICKS + 3, "arrived where its nav ended, then held: " + ticks);
        assertEquals(
                Optional.of(WalkEnd.IN_REACH),
                citizen.vitals().walks().lastEnd().map(EndedWalk::how));
    }

    @Test
    void walkThatNeverArrivesEndsAfterThreeMinutes() {
        t.bodies.frozen = true;
        CitizenAI ai = worker();
        ai.walkTo(THERE);

        int before = jobTicks;
        int ticks = 0;
        while (jobTicks == before && ticks < 10_000) {
            // it keeps moving, so the stuck handler never acts, yet never gets there
            t.bodies.bodies.get(body).position = new Vec3(0.5 + (ticks / 10 % 2) * 2, 64, 0.5);
            tick(ai);
            ticks++;
        }

        int limit = CommandedWalk.MAX_WALK_TICKS + CommandedWalk.HOLD_TICKS;
        assertTrue(ticks > limit && ticks <= limit + 3, "gave up after 3 minutes, then held still: " + ticks);
    }

    @Test
    void newCommandReplacesTheWalkUnderWay() {
        t.bodies.instant = true;
        CitizenAI ai = worker();
        ai.walkTo(THERE);
        tick(ai);

        ai.walkTo(ELSEWHERE);
        int ticks = ticksUntilItWorks(ai, 1000);

        assertEquals(Vec3.center(ELSEWHERE), position());
        assertTrue(ticks <= CommandedWalk.HOLD_TICKS + 3, "one walk there, one hold: " + ticks);
    }

    @Test
    void citizenWithoutAJobWalksThereToo() {
        CitizenAI ai = new CitizenAI(colony, citizen, body);

        ai.walkTo(THERE);
        tick(ai);

        assertEquals(Vec3.center(THERE), t.bodies.moves.getLast());
        assertEquals(Optional.of(THERE), citizen.vitals().walks().target(), "watched, bounded: not a bare moveTo");
    }

    @Test
    void jobAiIsResetAfterACommandedWalk() {
        t.bodies.instant = true;
        CitizenAI ai = worker();
        ticksUntilItWorks(ai, 100); // its entry into WORKING reset it already
        int made = jobAis;
        int resets = jobResets;

        ai.walkTo(THERE);
        ticksUntilItWorks(ai, 1000);

        assertEquals(made, jobAis, "kept, with its fields");
        assertEquals(resets + 1, jobResets, "its old walks would believe it where it was");
    }

    @Test
    void workerKeepsItsSpeedDuringTheWalk() {
        t.bodies.frozen = true;
        CitizenAI ai = worker();
        t.bodies.setMovementSpeed(body, 1.5); // a courier's Agility (MC JobDeliveryman.onLevelUp): kept with its job

        ai.walkTo(THERE);
        tick(ai);
        tick(ai);

        assertEquals(1.5, t.bodies.bodies.get(body).speed);
    }

    @Test
    void stuckBesideACheckedCellItIsTeleportedThere() {
        FakeBlueprints.registerBlocks(t.catalog);
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                t.blocks.blocks.put(THERE.offset(x, -1, z), FakeBlueprints.state(FakeBlueprints.PLANKS));
            }
        }
        t.bodies.frozen = true;
        CitizenAI ai = worker();

        ai.walkTo(THERE);
        ticksUntilItWorks(ai, 10_000);

        assertEquals(1, t.bodies.teleports.size(), "a standable cell beside the target: MC teleports its citizens");
        assertTrue(t.bodies.teleports.getFirst().distance(Vec3.center(THERE)) <= 1.5, "beside it");
    }

    @Test
    void jobAiIsResetAfterATeleport() {
        CitizenAI ai = worker();
        ticksUntilItWorks(ai, 100);
        int made = jobAis;
        int resets = jobResets;

        ai.teleport(Vec3.center(THERE));
        ticksUntilItWorks(ai, 1000);

        assertEquals(Vec3.center(THERE), position());
        assertEquals(made, jobAis);
        assertEquals(resets + 1, jobResets);
    }

    @Test
    void stuckOnAnUncheckedTargetItGivesUpRatherThanTeleport() {
        t.bodies.frozen = true; // no floor anywhere: no standable cell beside the target
        CitizenAI ai = worker();

        ai.walkTo(THERE);
        int ticks = ticksUntilItWorks(ai, 10_000);

        assertEquals(List.of(), t.bodies.teleports, "never into a wall, lava or the air");
        assertEquals(
                Optional.of(WalkEnd.GAVE_UP), citizen.vitals().walks().lastEnd().map(EndedWalk::how));
        assertTrue(ticks < CommandedWalk.MAX_WALK_TICKS, "it gave up before its time ran out: " + ticks);
    }

    @Test
    void sameTargetSentAgainMidWalkWalksAfresh() {
        t.bodies.frozen = true;
        CitizenAI ai = worker();
        ai.walkTo(THERE);
        tick(ai);
        tick(ai);
        int moves = t.bodies.moves.size();

        ai.walkTo(THERE);
        tick(ai);

        assertEquals(moves + 1, t.bodies.moves.size(), "a new command is a new walk, its stuck watch anew");
    }

    @Test
    void sameTargetSentAgainWalksAgain() {
        t.bodies.frozen = true;
        CitizenAI ai = worker();
        ai.walkTo(THERE);
        ticksUntilItWorks(ai, 10_000); // given up
        int moves = t.bodies.moves.size();

        ai.walkTo(THERE);
        tick(ai);

        assertEquals(moves + 1, t.bodies.moves.size(), "a fresh walk, not the one given up");
        assertEquals(Vec3.center(THERE), t.bodies.moves.getLast());
    }
}
