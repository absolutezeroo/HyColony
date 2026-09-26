package dev.hycolony.core.colony;

import com.google.gson.JsonObject;
import dev.hycolony.core.building.Building;
import dev.hycolony.core.colony.persistence.ColonySerializer;
import dev.hycolony.core.colony.territory.ClaimCell;
import dev.hycolony.core.construction.shared.ClaimRadius;
import dev.hycolony.core.kernel.persist.ColonyStorage;
import dev.hycolony.core.kernel.persist.MigrationChain;
import dev.hycolony.core.kernel.persist.SchemaTooNewException;
import java.io.IOException;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Loads and saves the colonies of one world through its {@link ColonyStorage}. A colony whose file cannot be read is
 * locked (never written over); a storage that cannot be listed is disabled until restart.
 */
public final class ColonyPersistence {
    private static final System.Logger LOG = System.getLogger(ColonyManager.class.getName());

    private final ColonyManager manager;
    private ColonyStorage storage;
    private MigrationChain migrations = MigrationChain.sp1();
    /** Ids whose file must never be touched (newer schema). */
    private final Set<Integer> lockedIds = new HashSet<>();
    /** Set once listing the storage fails: saves are refused and founding is denied until restart. */
    private boolean storageUnavailable;

    ColonyPersistence(ColonyManager manager) {
        this.manager = manager;
    }

    public void setStorage(ColonyStorage storage, MigrationChain migrations) {
        this.storage = storage;
        this.migrations = migrations;
    }

    /** False once listing the storage has failed: saves write nothing and founding is refused. */
    public boolean available() {
        return !storageUnavailable;
    }

    /** A colony we could not load: its file and its bodies are left alone. */
    boolean isLocked(int colonyId) {
        return lockedIds.contains(colonyId);
    }

    public void loadAll() {
        try {
            manager.reserveId(storage.highestIdEverUsed());
            for (int id : storage.colonyIds()) {
                loadOne(id);
            }
            // Second pass, once every initial square is claimed: a building never takes another colony's start.
            manager.all().forEach(this::claimBuildings);
        } catch (IOException e) {
            storageUnavailable = true;
            LOG.log(
                    System.Logger.Level.ERROR,
                    "Cannot list colonies of " + manager.context().world() + "; storage disabled until restart",
                    e);
        }
    }

    /** One colony's failure (corrupt file, I/O error, newer schema) must not stop the others from loading. */
    private void loadOne(int id) {
        try {
            Optional<JsonObject> raw = storage.load(id);
            if (raw.isEmpty()) {
                return;
            }
            JsonObject json = raw.get();
            int version = migrations.versionOf(json);
            if (version < migrations.current()) {
                storage.backupVersion(id, version, json.toString());
            }
            json = migrations.migrate(json);
            Colony colony = ColonySerializer.read(json, manager.context(), manager.territory());
            manager.register(colony);
            colony.clearDirty();
        } catch (SchemaTooNewException e) {
            lockedIds.add(id);
            LOG.log(System.Logger.Level.ERROR, "Colony " + id + " was saved by a newer HyColony; not loaded", e);
        } catch (IOException e) {
            lockedIds.add(id);
            LOG.log(System.Logger.Level.ERROR, "Colony " + id + " failed to load; file left untouched", e);
        } catch (RuntimeException e) {
            lockedIds.add(id);
            LOG.log(System.Logger.Level.ERROR, "Colony " + id + " failed to load; file left untouched", e);
        }
    }

    /**
     * The territory is not saved: the cells finished buildings claimed are claimed again (level 0 claims none),
     * bounded and never stealing, as at completion. Loading changes nothing to save.
     */
    private void claimBuildings(Colony colony) {
        for (Building b : colony.buildings().all()) {
            if (b.level() > 0) {
                manager.territory()
                        .claimSquareBounded(
                                colony.id(),
                                ClaimCell.of(b.position()),
                                ClaimRadius.of(b.type().id(), b.level()),
                                ClaimCell.of(colony.center()),
                                manager.context().config().claims().maxColonySize());
            }
        }
    }

    public void saveDirty() {
        for (Colony c : manager.all()) {
            if (c.isDirty()) {
                save(c);
            }
        }
    }

    public void saveAll() {
        manager.all().forEach(this::save);
    }

    void save(Colony c) {
        if (storage == null || storageUnavailable || lockedIds.contains(c.id())) {
            return;
        }
        try {
            storage.save(c.id(), ColonySerializer.write(c).toString());
            c.clearDirty();
        } catch (IOException | RuntimeException e) {
            LOG.log(System.Logger.Level.ERROR, "Saving colony " + c.id() + " failed; will retry", e);
        }
    }

    /** False when archiving failed (logged): the colony must then stay registered. */
    boolean archive(int colonyId) {
        if (storage == null) {
            return true;
        }
        try {
            storage.archive(colonyId);
            return true;
        } catch (IOException e) {
            LOG.log(System.Logger.Level.ERROR, "Archiving colony " + colonyId + " failed; colony kept", e);
            return false;
        }
    }
}
