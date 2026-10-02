package dev.hylens.core.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.api.ApiText;
import dev.hycolony.api.CitizenRef;
import dev.hycolony.api.ColonyRef;
import dev.hycolony.api.Pos;
import dev.hycolony.api.debug.CitizenDebugSnapshot;
import dev.hycolony.api.debug.Violation;
import dev.hycolony.api.read.CitizenSnapshot;
import dev.hycolony.api.read.CitizenWellbeing;
import dev.hycolony.api.read.ColonySummary;
import dev.hycolony.api.read.RequestSnapshot;
import dev.hylens.core.draw.Layers;
import dev.hylens.core.testing.FakeColonyWorld;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** What the HyLens menu shows (spec 2026-09-30, § 6.4). */
class MenuViewsTest {
    private static final ColonyRef A = new ColonyRef("default", 1);
    private static final ColonyRef B = new ColonyRef("default", 2);
    private static final CitizenRef ANN = new CitizenRef(A, 4);
    private static final CitizenRef BOB = new CitizenRef(A, 7);

    private static ColonySummary colony(ColonyRef ref, String name, int citizens) {
        return new ColonySummary(ref, name, new Pos(0, 64, 0), UUID.randomUUID(), citizens);
    }

    private static CitizenSnapshot citizen(CitizenRef ref, String name, Optional<String> job) {
        return new CitizenSnapshot(ref, name, job, Optional.empty(), Optional.empty(), Optional.empty());
    }

    private static CitizenDebugSnapshot state(CitizenRef ref, String ai, String step) {
        return new CitizenDebugSnapshot(
                ref,
                0,
                ai,
                0,
                step,
                0,
                Optional.empty(),
                Optional.empty(),
                List.of(),
                Optional.empty(),
                "",
                0,
                List.of(),
                0,
                List.of());
    }

    private static Violation alert(CitizenRef ref) {
        return new Violation("JOB_STEP_STALE", ApiText.of("k"), Optional.of(ref), Optional.empty());
    }

    private final FakeColonyWorld world = new FakeColonyWorld()
            .colony(
                    colony(A, "Alpha", 2),
                    citizen(ANN, "Ann", Optional.of("hycolony:deliveryman")),
                    citizen(BOB, "Bob", Optional.empty()))
            .colony(colony(B, "Beta", 0))
            .debug(state(ANN, "WORKING", "START_WORKING"))
            .debug(state(BOB, "", ""))
            .alerts(
                    A,
                    alert(ANN),
                    alert(ANN),
                    new Violation("REQUEST_UNRESOLVED", ApiText.of("k"), Optional.empty(), Optional.empty()));

    @Test
    void coloniesAreListedAndNoneChosenAtFirst() {
        MenuView v = MenuViews.of(world, false, MenuState.INITIAL, Optional.empty());

        assertEquals(
                List.of(
                        new MenuView.ColonyRow(A, "Alpha", 2, 3, false),
                        new MenuView.ColonyRow(B, "Beta", 0, 0, false)),
                v.colonies());
        assertEquals(List.of(), v.citizens());
    }

    @Test
    void chosenColonyListsItsCitizensWithTheirStateAndAlerts() {
        MenuView v = MenuViews.of(world, false, MenuState.INITIAL.withCitizen(ANN), Optional.of(ANN));

        assertEquals(
                new MenuView.ColonyRow(A, "Alpha", 2, 3, true), v.colonies().getFirst());
        assertEquals(
                List.of(
                        new MenuView.CitizenRow(
                                ANN,
                                "Ann",
                                ApiText.of("hycolony.ui.job.deliveryman"),
                                "WORKING",
                                "START_WORKING",
                                2,
                                true,
                                true,
                                Optional.empty()),
                        new MenuView.CitizenRow(
                                BOB,
                                "Bob",
                                ApiText.of("hycolony.ui.job.none"),
                                "-",
                                "-",
                                0,
                                false,
                                false,
                                Optional.empty())),
                v.citizens());
        assertEquals(Optional.of(ANN), v.citizen());
    }

    @Test
    void colonyCountsEveryAlertThoughNoCitizenCarriesIt() {
        MenuView v = MenuViews.of(world, false, MenuState.INITIAL, Optional.empty());

        assertEquals(3, v.colonies().getFirst().alerts(), "Ann's two and the request no citizen asked");
    }

    @Test
    void goneColonyOrCitizenIsNoLongerChosen() {
        CitizenRef gone = new CitizenRef(A, 99);

        MenuView noCitizen = MenuViews.of(world, false, MenuState.INITIAL.withCitizen(gone), Optional.empty());
        MenuView noColony =
                MenuViews.of(world, false, MenuState.INITIAL.withColony(new ColonyRef("default", 9)), Optional.empty());

        assertEquals(Optional.empty(), noCitizen.citizen());
        assertEquals(2, noCitizen.citizens().size());
        assertEquals(Optional.empty(), noColony.colony());
        assertEquals(List.of(), noColony.citizens());
    }

    @Test
    void layersAreShownAsChosen() {
        MenuView v = MenuViews.of(world, false, MenuState.INITIAL.toggle(Layers.Layer.STOP), Optional.empty());

        assertEquals(Layers.ALL.toggle(Layers.Layer.STOP), v.layers());
    }

    @Test
    void watchedAndChosenCitizensAreToldApart() {
        MenuView v = MenuViews.of(world, false, MenuState.INITIAL.withCitizen(ANN), Optional.of(BOB));

        assertEquals(
                List.of(true, false),
                v.citizens().stream().map(MenuView.CitizenRow::chosen).toList());
        assertEquals(
                List.of(false, true),
                v.citizens().stream().map(MenuView.CitizenRow::watched).toList());
        assertEquals(
                List.of(true, false),
                v.colonies().stream().map(MenuView.ColonyRow::chosen).toList(),
                "only the chosen colony");
    }

    @Test
    void clockAndStepAreShown() {
        MenuView v = MenuViews.of(world, true, MenuState.INITIAL.withStep(5), Optional.empty());

        assertTrue(v.paused());
        assertFalse(
                MenuViews.of(world, false, MenuState.INITIAL, Optional.empty()).paused());
        assertEquals(5, v.step());
        assertFalse(v.autoCheck());
        assertTrue(MenuViews.of(world, false, MenuState.INITIAL.toggleAutoCheck(), Optional.empty())
                .autoCheck());
    }

    @Test
    void aRowShowsTheSaturationReadFromHyColony() {
        world.wellbeing(new CitizenWellbeing(ANN, 42, 60, 7, List.of()));

        MenuView v = MenuViews.of(world, false, MenuState.INITIAL.withColony(A), Optional.empty());

        assertEquals(
                Optional.of(new MenuView.Saturation(42, 60)),
                v.citizens().stream()
                        .filter(r -> r.ref().equals(ANN))
                        .findFirst()
                        .orElseThrow()
                        .saturation());
    }

    private static RequestSnapshot requestOf(String id, String state, Optional<CitizenRef> citizen) {
        return new RequestSnapshot(
                id,
                A,
                state,
                "stack",
                Optional.of("Wood_Planks"),
                16,
                Optional.of(new Pos(0, 64, 0)),
                citizen,
                Optional.of("retrying"),
                Optional.empty(),
                List.of());
    }

    @Test
    void theChosenColonysOpenRequestsAreListedTheChosenOneMarked() {
        world.request(requestOf("r1", "ASSIGNED", Optional.of(ANN)))
                .request(requestOf("r2", "COMPLETED", Optional.empty()))
                .request(requestOf("r3", "RESOLVED", Optional.empty()));

        MenuView v = MenuViews.of(world, false, MenuState.INITIAL.withColony(A).withRequest("r3"), Optional.empty());

        assertEquals(
                List.of(
                        new MenuView.RequestRow(
                                "r1",
                                Optional.of("Wood_Planks"),
                                16,
                                "stack",
                                Optional.of("Ann"),
                                Optional.of(new Pos(0, 64, 0)),
                                "ASSIGNED",
                                Optional.of("retrying"),
                                false),
                        new MenuView.RequestRow(
                                "r3",
                                Optional.of("Wood_Planks"),
                                16,
                                "stack",
                                Optional.empty(),
                                Optional.of(new Pos(0, 64, 0)),
                                "RESOLVED",
                                Optional.of("retrying"),
                                true)),
                v.requests(),
                "COMPLETED is closed; RESOLVED, before it, is still open as HyColony's core counts");
    }

    @Test
    void cancelledFailedAndReceivedRequestsAreNotListed() {
        world.request(requestOf("r1", "CANCELLED", Optional.empty()))
                .request(requestOf("r2", "FAILED", Optional.empty()))
                .request(requestOf("r3", "RECEIVED", Optional.empty()));

        assertEquals(
                List.of(),
                MenuViews.of(world, false, MenuState.INITIAL.withColony(A), Optional.empty())
                        .requests());
    }

    @Test
    void onlyARequestForItemsCanBeFulfilledAsMcIDeliverable() {
        for (String kind : List.of("stack", "tool", "stack_list")) {
            assertTrue(rowOf(kind).asksItems(), kind);
        }
        for (String kind : List.of("delivery", "pickup", "crafting", "unknown")) {
            assertFalse(rowOf(kind).asksItems(), kind);
        }
    }

    private static MenuView.RequestRow rowOf(String kind) {
        return new MenuView.RequestRow(
                "r", Optional.empty(), 1, kind, Optional.empty(), Optional.empty(), "ASSIGNED", Optional.empty(), true);
    }

    @Test
    void noColonyNoRequests() {
        world.request(requestOf("r1", "ASSIGNED", Optional.empty()));

        assertEquals(
                List.of(),
                MenuViews.of(world, false, MenuState.INITIAL, Optional.empty()).requests());
    }

    @Test
    void theViewShowsTheOpenTab() {
        MenuView v = MenuViews.of(world, false, MenuState.INITIAL.withTab(MenuTab.VIEW), Optional.empty());

        assertEquals(MenuTab.VIEW, v.tab());
    }
}
