package dev.hycolony.core.farming.field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.kernel.BlockPos;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** MC RegisteredStructureManager: the colony's building extensions (fields), added, removed, cleaned and saved. */
class FieldRegistryTest {
    private static final BlockPos A = new BlockPos(10, 64, 0);
    private static final BlockPos B = new BlockPos(20, 64, 0);
    private static final BlockPos HUT = new BlockPos(0, 64, 0);

    @Test
    void addingTwiceKeepsOneField() {
        FieldRegistry r = new FieldRegistry();
        assertTrue(r.add(A));
        assertFalse(r.add(A));
        assertEquals(1, r.all().size());
    }

    @Test
    void freeListsOnlyUntakenFieldsInRegistrationOrder() {
        FieldRegistry r = new FieldRegistry();
        r.add(A);
        r.add(B);
        r.get(A).orElseThrow().setOwner(Optional.of(HUT));

        assertEquals(List.of(B), r.free().stream().map(FarmField::pos).toList());
        assertEquals(List.of(A), r.ownedBy(HUT).stream().map(FarmField::pos).toList());
    }

    @Test
    void cleanUpDropsAFieldOutsideTheColony() {
        FieldRegistry r = registry();
        assertTrue(r.cleanUp(p -> true, p -> !p.equals(A), p -> true), "a change to save");
        assertEquals(List.of(B), positions(r));
    }

    @Test
    void cleanUpDropsAFieldWhoseBlockIsGone() {
        FieldRegistry r = registry();
        r.cleanUp(p -> true, p -> true, p -> !p.equals(B));
        assertEquals(List.of(A), positions(r));
    }

    @Test
    void cleanUpKeepsAnUnloadedField() {
        FieldRegistry r = registry();
        assertFalse(r.cleanUp(p -> false, p -> false, p -> false), "nothing to save");
        assertEquals(List.of(A, B), positions(r));
    }

    @Test
    void fieldsSurviveSaveAndLoad() {
        FieldRegistry r = registry();
        r.get(B).orElseThrow().nextStage();

        FieldRegistry back = FieldRegistry.read(r.write());

        assertEquals(List.of(A, B), positions(back));
        assertEquals(FieldStage.HOED, back.get(B).orElseThrow().stage());
    }

    @Test
    void ownersOutsideTheLivingHutsAreFreed() {
        FieldRegistry r = registry();
        r.get(A).orElseThrow().setOwner(Optional.of(HUT));
        r.get(B).orElseThrow().setOwner(Optional.of(new BlockPos(99, 64, 99)));

        assertTrue(r.freeOwnersNotIn(Set.of(HUT)));

        assertTrue(r.get(A).orElseThrow().isTaken());
        assertFalse(r.get(B).orElseThrow().isTaken());
        assertFalse(r.freeOwnersNotIn(Set.of(HUT)));
    }

    private static FieldRegistry registry() {
        FieldRegistry r = new FieldRegistry();
        r.add(A);
        r.add(B);
        return r;
    }

    private static List<BlockPos> positions(FieldRegistry r) {
        return r.all().stream().map(FarmField::pos).toList();
    }
}
