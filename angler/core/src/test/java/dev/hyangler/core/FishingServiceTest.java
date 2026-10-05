package dev.hyangler.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hyangler.api.Angler;
import dev.hyangler.api.Catch;
import dev.hyangler.api.Pos;
import dev.hyangler.api.RodStats;
import dev.hyangler.api.Subscription;
import dev.hyangler.api.condition.CatchHook;
import dev.hyangler.api.event.CastStarted;
import dev.hyangler.api.event.FishBiting;
import dev.hyangler.core.catalog.RawFile;
import dev.hyangler.core.catalog.RawFile.Kind;
import dev.hyangler.core.testing.Contexts;
import dev.hyangler.core.testing.ScriptedRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FishingServiceTest {
    private static final Angler PLAYER = new Angler.Player(new UUID(0, 1));
    private final List<RuntimeException> failures = new ArrayList<>();
    private final FishingService service = new FishingService(AnglerSettings.DEFAULTS, failures::add);

    private void loadBluegill() {
        service.load(
                List.of(
                        new RawFile(Kind.FISH, "Fish_Bluegill_Item", "{\"Weight\":40,\"Rarities\":false}"),
                        new RawFile(Kind.ROD, "HyAngler_Rod_Copper", "{\"Tier\":1,\"Lure\":1}")),
                Set.of());
    }

    @Test
    void nothingBitesBeforeTheDataLoads() {
        assertTrue(service.roll(Contexts.base(), new ScriptedRandom()).isEmpty());
        assertTrue(service.chances(Contexts.base()).isEmpty());
    }

    @Test
    void aLoadedFishIsRolledAndItsRodRead() {
        loadBluegill();
        assertEquals(
                "Fish_Bluegill_Item",
                service.roll(Contexts.base(), new ScriptedRandom(0, 0))
                        .orElseThrow()
                        .itemId());
        assertEquals(Optional.of(new RodStats(1, 1, 0, 32)), service.rod("HyAngler_Rod_Copper"));
    }

    @Test
    void anotherModsConditionTypeIsReadAfterItRegisters() {
        service.conditionTypes().register("Season", spec -> ctx -> false);
        service.load(
                List.of(new RawFile(
                        Kind.FISH, "Fish_Bluegill_Item", "{\"Weight\":40,\"Conditions\":{\"Type\":\"Season\"}}")),
                Set.of());
        assertTrue(service.catalog().rejections().isEmpty());
        assertTrue(service.chances(Contexts.base()).isEmpty());
    }

    @Test
    void catchHooksChangeAndCancelInOrder() {
        loadBluegill();
        service.addCatchHook(
                "a",
                (angler, ctx, c) ->
                        Optional.of(new Catch("Fish_Salmon_Item", c.count(), c.category(), c.rarity(), c.source())));
        Subscription cancel = service.addCatchHook("b", (angler, ctx, c) -> Optional.empty());
        assertTrue(
                service.land(PLAYER, Contexts.base(), new ScriptedRandom(0, 0)).isEmpty());
        cancel.close();
        assertEquals(
                "Fish_Salmon_Item",
                service.land(PLAYER, Contexts.base(), new ScriptedRandom(0, 0))
                        .orElseThrow()
                        .itemId());
    }

    @Test
    void aFailingCatchHookIsReportedAndTheCatchKept() {
        loadBluegill();
        service.addCatchHook("broken", (angler, ctx, c) -> {
            throw new IllegalStateException("boom");
        });
        assertEquals(
                "Fish_Bluegill_Item",
                service.land(PLAYER, Contexts.base(), new ScriptedRandom(0, 0))
                        .orElseThrow()
                        .itemId());
        assertEquals(1, failures.size());
    }

    @Test
    void aCatchHookReturningNullIsReportedAndTheCatchKept() {
        loadBluegill();
        service.addCatchHook("careless", (angler, ctx, c) -> null);
        assertEquals(
                "Fish_Bluegill_Item",
                service.land(PLAYER, Contexts.base(), new ScriptedRandom(0, 0))
                        .orElseThrow()
                        .itemId());
        assertEquals(1, failures.size());
    }

    @Test
    void closingTwiceRemovesOnlyItsOwnRegistration() {
        loadBluegill();
        CatchHook salmon =
                (angler, ctx, c) -> Optional.of(new Catch("Fish_Salmon_Item", c.count(), c.category(), c.rarity(), ""));
        Subscription first = service.addCatchHook("a", salmon);
        service.addCatchHook("a", salmon); // an equal registration: same owner, same hook
        first.close();
        first.close();
        assertEquals(
                "Fish_Salmon_Item",
                service.land(PLAYER, Contexts.base(), new ScriptedRandom(0, 0))
                        .orElseThrow()
                        .itemId());
    }

    @Test
    void aNullRegistrationIsRefusedAtOnce() {
        assertThrows(NullPointerException.class, () -> service.subscribe(null, e -> {}));
        assertThrows(NullPointerException.class, () -> service.addCatchHook("a", null));
    }

    @Test
    void listenersHearTheirTypeAndAFailingOneSparesTheOthers() {
        List<Object> heard = new ArrayList<>();
        service.subscribe(CastStarted.class, e -> {
            throw new IllegalStateException("boom");
        });
        service.subscribe(CastStarted.class, heard::add);
        service.subscribe(FishBiting.class, heard::add);
        service.publish(new CastStarted(PLAYER, new Pos(0, 0, 0)));
        assertEquals(1, heard.size());
        assertEquals(1, failures.size());
    }

    @Test
    void aClosedListenerHearsNothingMore() {
        List<Object> heard = new ArrayList<>();
        Subscription sub = service.subscribe(CastStarted.class, heard::add);
        sub.close();
        service.publish(new CastStarted(PLAYER, new Pos(0, 0, 0)));
        assertTrue(heard.isEmpty());
    }

    @Test
    void biteTimesApplyTheConfigsMultiplier() {
        FishingService slow = new FishingService(new AnglerSettings(2.0, true, 32, true), failures::add);
        assertEquals(400, slow.biteTimes(0, new ScriptedRandom(200, 20, 20)).waitTicks());
    }

    @Test
    void aRodWithoutALineTakesTheConfigsDefault() {
        FishingService longLines = new FishingService(new AnglerSettings(1.0, true, 48, true), failures::add);
        longLines.load(List.of(new RawFile(Kind.ROD, "HyAngler_Rod_Crude", "{}")), Set.of());
        assertEquals(48, longLines.rod("HyAngler_Rod_Crude").orElseThrow().maxLine());
    }

    @Test
    void settingsOutOfBoundsAreBroughtBack() {
        AnglerSettings wild = new AnglerSettings(50, true, 2, false).bounded();
        assertEquals(10.0, wild.biteTimeMultiplier());
        assertEquals(8, wild.maxLineDefault());
        assertEquals(0.1, new AnglerSettings(0, true, 32, true).bounded().biteTimeMultiplier());
        assertEquals(64, new AnglerSettings(1, true, 500, true).bounded().maxLineDefault());
        assertEquals(
                1.0, new AnglerSettings(Double.NaN, true, 32, true).bounded().biteTimeMultiplier());
    }
}
