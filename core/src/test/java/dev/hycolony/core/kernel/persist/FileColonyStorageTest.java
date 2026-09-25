package dev.hycolony.core.kernel.persist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileColonyStorageTest {
    @TempDir Path dir;

    @Test
    void saveThenLoadKeepsBackupOfPrevious() throws Exception {
        FileColonyStorage s = new FileColonyStorage(dir);
        s.save(1, "{\"v\":1}");
        s.save(1, "{\"v\":2}");
        assertEquals(2, s.load(1).orElseThrow().get("v").getAsInt());
        assertEquals("{\"v\":1}", Files.readString(dir.resolve("colony-1.json.bak")));
        assertEquals(List.of(1), s.colonyIds());
    }

    @Test
    void corruptMainFallsBackToBak() throws Exception {
        FileColonyStorage s = new FileColonyStorage(dir);
        s.save(1, "{\"v\":1}");
        s.save(1, "{\"v\":2}");
        Files.writeString(dir.resolve("colony-1.json"), "{\"v\":");
        assertEquals(1, s.load(1).orElseThrow().get("v").getAsInt());
    }

    @Test
    void bothCorruptAreQuarantined() throws Exception {
        FileColonyStorage s = new FileColonyStorage(dir);
        Files.writeString(dir.resolve("colony-3.json"), "garbage");
        Files.writeString(dir.resolve("colony-3.json.bak"), "garbage");
        assertTrue(s.load(3).isEmpty());
        assertTrue(Files.notExists(dir.resolve("colony-3.json")));
        try (var files = Files.list(dir.resolve("corrupt"))) {
            assertEquals(2, files.count());
        }
        assertEquals(3, s.highestIdEverUsed());
    }

    @Test
    void archiveMovesFilesAndKeepsIdReserved() throws Exception {
        FileColonyStorage s = new FileColonyStorage(dir);
        s.save(5, "{}");
        s.archive(5);
        assertTrue(s.colonyIds().isEmpty());
        assertEquals(5, s.highestIdEverUsed());
    }

    @Test
    void versionBackupIsWrittenOnce() throws Exception {
        FileColonyStorage s = new FileColonyStorage(dir);
        s.backupVersion(1, 1, "{\"first\":true}");
        s.backupVersion(1, 1, "{\"second\":true}");
        assertEquals("{\"first\":true}", Files.readString(dir.resolve("colony-1.v1.json")));
    }

    @Test
    void colonyIdsIncludesBackupOnlyColonies() throws Exception {
        FileColonyStorage s = new FileColonyStorage(dir);
        Files.writeString(dir.resolve("colony-4.json.bak"), "{\"v\":9}");
        assertEquals(List.of(4), s.colonyIds());
        assertEquals(9, s.load(4).orElseThrow().get("v").getAsInt());
    }

    @Test
    void corruptMainIsQuarantinedSoBackupSurvivesNextSave() throws Exception {
        FileColonyStorage s = new FileColonyStorage(dir);
        s.save(1, "{\"v\":1}");
        s.save(1, "{\"v\":2}");
        Files.writeString(dir.resolve("colony-1.json"), "{\"v\":");
        assertEquals(1, s.load(1).orElseThrow().get("v").getAsInt());
        s.save(1, "{\"v\":3}");
        assertEquals("{\"v\":1}", Files.readString(dir.resolve("colony-1.json.bak")));
        try (var files = Files.list(dir.resolve("corrupt"))) {
            assertEquals(1, files.count());
        }
    }
}
