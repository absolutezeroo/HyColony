package dev.hycolony.core.kernel.persist;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.List;
import java.util.Optional;

public interface ColonyStorage {
    List<Integer> colonyIds() throws IOException;

    /** Highest id among live, archived and quarantined files: ids are never reused. */
    int highestIdEverUsed() throws IOException;

    /** Main file, else its .bak; empty (and both quarantined) if neither parses. */
    Optional<JsonObject> load(int id) throws IOException;

    /** Writes a .tmp file, rotates the current file to .bak, then atomically moves .tmp into place: only that final move is atomic. */
    void save(int id, String json) throws IOException;

    /** Copy kept before migrating; written once per version. */
    void backupVersion(int id, int schemaVersion, String json) throws IOException;

    void archive(int id) throws IOException;
}
