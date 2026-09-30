package dev.hylens.core.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.hycolony.api.ApiText;
import dev.hycolony.api.CitizenRef;
import dev.hycolony.api.ColonyRef;
import dev.hycolony.api.Pos;
import dev.hycolony.api.debug.CitizenDebugSnapshot;
import dev.hycolony.api.debug.Violation;
import dev.hycolony.api.read.CitizenSnapshot;
import dev.hycolony.api.read.ColonySummary;
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
        MenuView v = MenuViews.of(world, MenuState.INITIAL, Optional.empty());

        assertEquals(
                List.of(new MenuView.ColonyRow(A, "Alpha", 2, false), new MenuView.ColonyRow(B, "Beta", 0, false)),
                v.colonies());
        assertEquals(List.of(), v.citizens());
    }

    @Test
    void chosenColonyListsItsCitizensWithTheirStateAndAlerts() {
        MenuView v = MenuViews.of(world, MenuState.INITIAL.withCitizen(ANN), Optional.of(ANN));

        assertEquals(new MenuView.ColonyRow(A, "Alpha", 2, true), v.colonies().getFirst());
        assertEquals(
                List.of(
                        new MenuView.CitizenRow(
                                ANN, "Ann", "hycolony:deliveryman", "WORKING", "START_WORKING", 2, true, true),
                        new MenuView.CitizenRow(BOB, "Bob", "-", "-", "-", 0, false, false)),
                v.citizens());
        assertEquals(Optional.of(ANN), v.citizen());
    }

    @Test
    void goneColonyOrCitizenIsNoLongerChosen() {
        CitizenRef gone = new CitizenRef(A, 99);

        MenuView noCitizen = MenuViews.of(world, MenuState.INITIAL.withCitizen(gone), Optional.empty());
        MenuView noColony =
                MenuViews.of(world, MenuState.INITIAL.withColony(new ColonyRef("default", 9)), Optional.empty());

        assertEquals(Optional.empty(), noCitizen.citizen());
        assertEquals(2, noCitizen.citizens().size());
        assertEquals(Optional.empty(), noColony.colony());
        assertEquals(List.of(), noColony.citizens());
    }

    @Test
    void layersAreShownAsChosen() {
        MenuView v = MenuViews.of(world, MenuState.INITIAL.toggle(Layers.Layer.STOP), Optional.empty());

        assertEquals(Layers.ALL.toggle(Layers.Layer.STOP), v.layers());
    }

    @Test
    void watchedAndChosenCitizensAreToldApart() {
        MenuView v = MenuViews.of(world, MenuState.INITIAL.withCitizen(ANN), Optional.of(BOB));

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
}
