package dev.hycolony.core.app.action;

import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.citizen.home.LivingModule;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.port.Msg;
import java.util.Optional;
import java.util.UUID;

/**
 * The residence's Residents tab buttons: assign, unassign, hiring mode and recall (MC AssignUnassignMessage,
 * BuildingHiringModeMessage, RecallCitizenHutMessage). Each needs MANAGE_HUTS (MC AbstractColonyServerMessage) and
 * re-shows the hut's window. Built by its caller, like {@code FieldActions}.
 */
public final class HousingActions {
    private final ColonyManager manager;

    public HousingActions(ColonyManager manager) {
        this.manager = manager;
    }

    /**
     * MC AssignUnassignMessage (assign): the citizen moves in, leaving its old home; MC checks the mode in the window
     * only. At level 0, MC WindowHutLiving's chat refusal (workerhuts.level0); false when nothing changed.
     */
    public boolean assign(UUID player, BlockPos hutPos, int citizenId) {
        Optional<Residence> r = Residence.find(manager, player, hutPos);
        if (r.isEmpty()) {
            return false;
        }
        Residence h = r.get();
        if (h.hut().building().level() == 0) {
            manager.context().notifier().send(player, Msg.of("hycolony.hut.notBuiltYet"));
            return false;
        }
        CitizenData citizen = h.colony().citizens().get(citizenId).orElse(null);
        boolean done = citizen != null
                && !hutPos.equals(citizen.homeBuilding())
                && h.living().assign(h.colony(), h.hut().building(), citizen);
        h.show(manager, player);
        return done;
    }

    /** MC AssignUnassignMessage (unassign): the resident leaves, homeless; false for a citizen not living here. */
    public boolean unassign(UUID player, BlockPos hutPos, int citizenId) {
        Optional<Residence> r = Residence.find(manager, player, hutPos);
        boolean done = r.map(h -> h.living().remove(h.colony(), h.hut().building(), citizenId))
                .orElse(false);
        r.ifPresent(h -> h.show(manager, player));
        return done;
    }

    /** MC BuildingHiringModeMessage: the next hiring mode (DEFAULT, AUTO, MANUAL, LOCKED, then DEFAULT again). */
    public boolean cycleMode(UUID player, BlockPos hutPos) {
        Optional<Residence> r = Residence.find(manager, player, hutPos);
        r.ifPresent(h -> {
            h.living().setHiringMode(h.living().hiringMode().next());
            h.colony().markDirty();
            h.show(manager, player);
        });
        return r.isPresent();
    }

    /**
     * MC RecallCitizenHutMessage: every resident is teleported to the hut (asleep, it wakes first), a resident without
     * a body gets one there; if one could not appear, MC's {@code workerhuts.recallfail}.
     */
    public boolean recall(UUID player, BlockPos hutPos) {
        Optional<Residence> r = Residence.find(manager, player, hutPos);
        if (r.isEmpty()) {
            return false;
        }
        Colony c = r.get().colony();
        boolean failed = false;
        for (int id : r.get().living().residents()) {
            failed |= !CitizenRecall.bring(manager, c, id, hutPos);
        }
        if (failed) {
            manager.context().notifier().send(player, Msg.of("hycolony.hut.recallFail"));
        }
        r.get().show(manager, player);
        return true;
    }

    /** A residence its player may manage, with its living module. */
    private record Residence(ManagedHut hut, LivingModule living) {
        static Optional<Residence> find(ColonyManager manager, UUID player, BlockPos pos) {
            return ManagedHut.find(manager, player, pos)
                    .flatMap(h -> h.building().module(LivingModule.class).map(l -> new Residence(h, l)));
        }

        Colony colony() {
            return hut.colony();
        }

        void show(ColonyManager manager, UUID player) {
            manager.windows().showBuilding(hut.colony(), hut.building(), player);
        }
    }
}
