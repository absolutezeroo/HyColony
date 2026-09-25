package dev.hycolony.core.kernel.persist;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** One JSON file per colony in {@code dir}, with .bak, archive/ and corrupt/ subfolders. */
public final class FileColonyStorage implements ColonyStorage {
    private static final System.Logger LOG = System.getLogger(FileColonyStorage.class.getName());
    private static final Pattern LIVE = Pattern.compile("colony-(\\d+)\\.json");
    private static final Pattern ANY = Pattern.compile("colony-(\\d+)\\D.*");

    private final Path dir;

    public FileColonyStorage(Path dir) {
        this.dir = dir;
    }

    private Path main(int id) { return dir.resolve("colony-" + id + ".json"); }
    private Path bak(int id) { return dir.resolve("colony-" + id + ".json.bak"); }

    @Override
    public List<Integer> colonyIds() throws IOException {
        List<Integer> ids = new ArrayList<>();
        if (!Files.isDirectory(dir)) {
            return ids;
        }
        try (Stream<Path> files = Files.list(dir)) {
            files.forEach(f -> {
                Matcher m = LIVE.matcher(f.getFileName().toString());
                if (m.matches()) {
                    ids.add(Integer.parseInt(m.group(1)));
                }
            });
        }
        ids.sort(null);
        return ids;
    }

    @Override
    public int highestIdEverUsed() throws IOException {
        int max = 0;
        for (Path folder : List.of(dir, dir.resolve("archive"), dir.resolve("corrupt"))) {
            if (!Files.isDirectory(folder)) {
                continue;
            }
            try (Stream<Path> files = Files.list(folder)) {
                for (Path f : (Iterable<Path>) files::iterator) {
                    Matcher m = ANY.matcher(f.getFileName().toString());
                    if (m.matches()) {
                        max = Math.max(max, Integer.parseInt(m.group(1)));
                    }
                }
            }
        }
        return max;
    }

    @Override
    public Optional<JsonObject> load(int id) throws IOException {
        Optional<JsonObject> json = parse(main(id)).or(() -> parse(bak(id)));
        if (json.isEmpty() && (Files.exists(main(id)) || Files.exists(bak(id)))) {
            quarantine(id);
        }
        return json;
    }

    private Optional<JsonObject> parse(Path file) {
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        try {
            return Optional.of(JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject());
        } catch (IOException | JsonParseException | IllegalStateException e) {
            LOG.log(System.Logger.Level.WARNING, "Unreadable colony file " + file, e);
            return Optional.empty();
        }
    }

    private void quarantine(int id) throws IOException {
        Path corrupt = Files.createDirectories(dir.resolve("corrupt"));
        long stamp = System.currentTimeMillis();
        for (Path f : List.of(main(id), bak(id))) {
            if (Files.exists(f)) {
                Files.move(f, corrupt.resolve(f.getFileName() + "." + stamp), StandardCopyOption.REPLACE_EXISTING);
            }
        }
        LOG.log(System.Logger.Level.ERROR, "Colony " + id + " is unreadable and was moved to " + corrupt);
    }

    @Override
    public void save(int id, String json) throws IOException {
        Files.createDirectories(dir);
        Path tmp = dir.resolve("colony-" + id + ".json.tmp");
        Files.writeString(tmp, json, StandardCharsets.UTF_8);
        if (Files.exists(main(id))) {
            Files.move(main(id), bak(id), StandardCopyOption.REPLACE_EXISTING);
        }
        try {
            Files.move(tmp, main(id), StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, main(id), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @Override
    public void backupVersion(int id, int schemaVersion, String json) throws IOException {
        Path file = dir.resolve("colony-" + id + ".v" + schemaVersion + ".json");
        if (Files.notExists(file)) {
            Files.createDirectories(dir);
            Files.writeString(file, json, StandardCharsets.UTF_8);
        }
    }

    @Override
    public void archive(int id) throws IOException {
        Path archive = Files.createDirectories(dir.resolve("archive"));
        long stamp = System.currentTimeMillis();
        for (Path f : List.of(main(id), bak(id))) {
            if (Files.exists(f)) {
                Files.move(f, archive.resolve(f.getFileName() + "." + stamp), StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }
}
