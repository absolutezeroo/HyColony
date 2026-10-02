# Blockymodel Viewer : plan d'implémentation

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or
> superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal :** un plugin IntelliJ IDEA qui affiche un `.blockymodel` avec le moteur du Blockbench installé, à côté de son
JSON, et suit les modifications en direct.

**Architecture :** le bundle de bureau de Blockbench (`resources/app.asar`) est servi à un navigateur JCEF par un
gestionnaire de requêtes Java, avec une couche « faux Node » injectée avant lui et un `viewer.js` après lui. Le JS
charge `hytale_plugin.js`, masque l'interface et expose `bbv.showModel(chemin, texte)` ; le Java l'appelle 300 ms
après chaque frappe. Les classes testables (`AsarArchive`, `BlockbenchInstall`, `BlockbenchRoutes`) sont du Java pur.

**Tech Stack :** Java 25, Gradle 9.5.1, IntelliJ Platform Gradle Plugin 2.19.0, IDEA 2026.2.3 local (build
262.10968.63), JCEF (`intellij.platform.ui.jcef`, `intellij.libraries.jcef`), Gson 2.11.0, JUnit 6.1.3, Edge headless
pour le test de démarrage.

**Spec :** `docs/superpowers/specs/2026-10-02-blockymodel-viewer-intellij-design.md` (dépôt HyColony).

## Global Constraints

- Dépôt séparé : `C:\Users\Ctuto\Desktop\BlockymodelViewer`, branche `main`. Rien n'est écrit dans HyColony.
- Java 25, paquet `dev.blockymodelviewer`, 4 espaces, 120 colonnes, LF, UTF-8.
- Compilé contre l'IDE local `C:/Program Files/JetBrains/IntelliJ IDEA 2026.2.3` (propriété Gradle `ideaDir`),
  `since-build` 262, pas d'`until-build`.
- Dépendances du descripteur : plugins `com.intellij.modules.platform`, `com.intellij.modules.json` ; modules
  `intellij.libraries.jcef`, `intellij.platform.ui.jcef`.
- L'aperçu ne touche jamais au disque en écriture et ne sort jamais de la machine : toute requête hors de
  `http://blockbench.localhost/` reçoit 404 ; `/fs/…` est en lecture seule et limité aux racines autorisées.
- Hôte fictif : `http://blockbench.localhost`. Page : `/bb/index.html`. État JS : attribut `data-bbv` de `<html>`
  (`ready`, `model:<n>`, `error:<texte>`).
- Délai d'attente après frappe : 300 ms. Délai maximal de démarrage de Blockbench : 20 000 ms.
- Blockbench par défaut : `%LOCALAPPDATA%\Programs\Blockbench` ; plugin Hytale : `%APPDATA%\Blockbench\plugins\hytale_plugin.js`.
- Commits : `type: description` en anglais, `git add` de chemins explicites, avec la ligne
  `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- On ne lance pas l'IDE de test (`runIde`) : l'utilisateur le fait et essaie le plugin.

## Review Focus

1. Chemin avec espaces ou accents (`C:\Program Files\…`, dossier utilisateur accentué) : le `fs` virtuel et les
   textures doivent le servir. Test : `fsReadServesFileWithSpacesAndAccents` (tâche 3).
2. Remontée de dossier (`..`) dans `/fs/…`, `/plugins/…` ou un chemin disque : 404, jamais le fichier. Tests :
   `fsReadRefusesParentTraversal`, `pluginsRouteRefusesParentTraversal` (tâche 3).
3. Blockbench ou `hytale_plugin.js` absent : une raison lisible, pas d'exception. Tests de la tâche 2.
4. JSON à moitié tapé : l'aperçu garde le dernier rendu valide, sans message d'erreur. Vérifié en IDE (tâche 7,
   point 4) ; le code est dans `showModel` (tâche 4).
5. Modèle ouvert hors du projet (glissé depuis l'Explorateur) : son dossier est ajouté aux racines autorisées, la
   texture s'affiche. Vérifié en IDE (tâche 7, point 6) ; le code est dans `BlockymodelPreview.allowedRoots` (tâche 5).

---

## Structure des fichiers

```
BlockymodelViewer/
  settings.gradle.kts, build.gradle.kts, gradle.properties, .gitignore, .editorconfig, README.md
  gradlew, gradlew.bat, gradle/wrapper/          (copiés de HyColony : Gradle 9.5.1)
  src/main/java/dev/blockymodelviewer/
    AsarArchive.java               lit les entrées d'un app.asar
    BlockbenchInstall.java         trouve Blockbench et son plugin Hytale
    BlockbenchRoutes.java          URL -> réponse (asar, viewer, fs virtuel, textures), Java pur
    CefRoutes.java                 adaptateur JCEF de BlockbenchRoutes, 404 pour le reste
    BlockymodelPreview.java        FileEditor : navigateur, frappe -> showModel, textures -> showModel
    BlockymodelEditorProvider.java éditeur partagé texte + aperçu
    BlockbenchSettings.java        réglage du dossier de Blockbench
    BlockbenchConfigurable.java    page de Settings
    OpenInBlockbenchAction.java    lance Blockbench.exe sur le fichier
  src/main/resources/META-INF/plugin.xml
  src/main/resources/viewer/node-shim.js   couche « faux Node »
  src/main/resources/viewer/viewer.js      plugin Hytale, interface masquée, window.bbv
  src/test/java/dev/blockymodelviewer/
    AsarWriter.java, AsarArchiveTest.java, BlockbenchInstallTest.java, BlockbenchRoutesTest.java,
    BlockbenchSmokeTest.java
  src/test/resources/fixture/Blocks/Tape/Corner.blockymodel, Texture.png   (copiés de HyColony)
```

---

### Task 1 : dépôt, build et `AsarArchive`

**Files :**
- Create : `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `.gitignore`, `.editorconfig`,
  `src/main/resources/META-INF/plugin.xml`
- Copy : `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`, `gradle/wrapper/gradle-wrapper.properties`
- Create : `src/main/java/dev/blockymodelviewer/AsarArchive.java`
- Test : `src/test/java/dev/blockymodelviewer/AsarWriter.java`, `src/test/java/dev/blockymodelviewer/AsarArchiveTest.java`

**Interfaces :**
- Produces : `final class AsarArchive { AsarArchive(Path archive) throws IOException; Optional<byte[]> read(String path) throws IOException; }`
  et, côté tests, `final class AsarWriter { static Path write(Path target, Map<String, String> entries, Set<String> unpacked) throws IOException; }`.

- [ ] **Step 1 : créer le dépôt et copier le wrapper Gradle**

```bash
mkdir -p /c/Users/Ctuto/Desktop/BlockymodelViewer/gradle/wrapper
cd /c/Users/Ctuto/Desktop/BlockymodelViewer
git init -b main
cp /c/Users/Ctuto/Desktop/HyColony/gradlew /c/Users/Ctuto/Desktop/HyColony/gradlew.bat .
cp /c/Users/Ctuto/Desktop/HyColony/gradle/wrapper/gradle-wrapper.jar /c/Users/Ctuto/Desktop/HyColony/gradle/wrapper/gradle-wrapper.properties gradle/wrapper/
```

- [ ] **Step 2 : écrire les fichiers de build**

`settings.gradle.kts` :

```kotlin
rootProject.name = "blockymodel-viewer"
```

`gradle.properties` :

```properties
ideaDir=C:/Program Files/JetBrains/IntelliJ IDEA 2026.2.3
org.gradle.jvmargs=-Xmx2g -Dfile.encoding=UTF-8
```

`build.gradle.kts` :

```kotlin
plugins {
    java
    id("org.jetbrains.intellij.platform") version "2.19.0"
}

group = "dev.blockymodelviewer"
version = "0.1.0"

val ideaDir = providers.gradleProperty("ideaDir").get()

java { toolchain { languageVersion = JavaLanguageVersion.of(25) } }

repositories {
    mavenCentral()
    intellijPlatform { defaultRepositories() }
}

dependencies {
    intellijPlatform {
        local(ideaDir)
        bundledPlugin("com.intellij.modules.json")
        bundledPlugin("com.intellij.modules.jcef")
        bundledModule("intellij.libraries.jcef")
        bundledModule("intellij.platform.ui.jcef")
        pluginVerifier()
    }
    implementation("com.google.code.gson:gson:2.11.0")
    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

intellijPlatform {
    instrumentCode = false
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "262"
            untilBuild = provider { null }
        }
    }
    pluginVerification {
        ides { local(file(ideaDir)) }
    }
}

tasks.test { useJUnitPlatform() }
```

`.gitignore` :

```
.gradle/
build/
.intellijPlatform/
.idea/
*.iml
```

`.editorconfig` :

```
root = true

[*]
charset = utf-8
end_of_line = lf
insert_final_newline = true
trim_trailing_whitespace = true
indent_style = space
indent_size = 4
max_line_length = 120

[*.{kts,xml,json}]
indent_size = 4
```

`src/main/resources/META-INF/plugin.xml` :

```xml
<idea-plugin>
    <id>dev.blockymodelviewer</id>
    <name>Blockymodel Viewer</name>
    <vendor>absolutezeroo</vendor>
    <description><![CDATA[
        Previews Hytale <code>.blockymodel</code> files with the Blockbench engine installed on this machine,
        side by side with their JSON, updated as you type.
    ]]></description>

    <dependencies>
        <plugin id="com.intellij.modules.platform"/>
        <plugin id="com.intellij.modules.json"/>
        <module name="intellij.libraries.jcef"/>
        <module name="intellij.platform.ui.jcef"/>
    </dependencies>

    <extensions defaultExtensionNs="com.intellij">
        <fileType name="JSON" extensions="blockymodel"/>
    </extensions>
</idea-plugin>
```

- [ ] **Step 3 : vérifier que le build se configure**

Run : `./gradlew build`
Expected : `BUILD SUCCESSFUL` (aucune source encore). Si l'API de l'IntelliJ Platform Gradle Plugin diffère (par
exemple `ides { local(…) }` ou `untilBuild`), corriger d'après le message d'erreur et la page
<https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-extension.html>, sans changer
l'intention (IDE local, pas d'`until-build`, vérification contre l'IDE local).

- [ ] **Step 4 : écrire l'outil de test `AsarWriter`**

`src/test/java/dev/blockymodelviewer/AsarWriter.java` :

```java
package dev.blockymodelviewer;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.gson.JsonObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

/** Writes a small asar archive for tests; the listed entries go to the sibling {@code .unpacked} folder. */
final class AsarWriter {
    private AsarWriter() {}

    /** Writes {@code entries} (slash-separated path to text) to {@code target} and returns it. */
    static Path write(Path target, Map<String, String> entries, Set<String> unpacked) throws IOException {
        JsonObject root = directory();
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        for (Map.Entry<String, String> entry : entries.entrySet()) {
            String path = entry.getKey();
            byte[] bytes = entry.getValue().getBytes(UTF_8);
            JsonObject file = new JsonObject();
            file.addProperty("size", bytes.length);
            if (unpacked.contains(path)) {
                file.addProperty("unpacked", true);
                Path out = target.resolveSibling(target.getFileName() + ".unpacked").resolve(path);
                Files.createDirectories(out.getParent());
                Files.write(out, bytes);
            } else {
                file.addProperty("offset", String.valueOf(data.size()));
                data.writeBytes(bytes);
            }
            parentOf(root, path).getAsJsonObject("files").add(path.substring(path.lastIndexOf('/') + 1), file);
        }
        byte[] json = root.toString().getBytes(UTF_8);
        int padded = (json.length + 3) & ~3;
        ByteBuffer head = ByteBuffer.allocate(16 + padded).order(ByteOrder.LITTLE_ENDIAN);
        head.putInt(4).putInt(8 + padded).putInt(4 + padded).putInt(json.length).put(json);
        Files.createDirectories(target.getParent());
        try (OutputStream out = Files.newOutputStream(target)) {
            out.write(head.array());
            data.writeTo(out);
        }
        return target;
    }

    private static JsonObject directory() {
        JsonObject dir = new JsonObject();
        dir.add("files", new JsonObject());
        return dir;
    }

    private static JsonObject parentOf(JsonObject root, String path) {
        JsonObject node = root;
        String[] parts = path.split("/");
        for (int i = 0; i < parts.length - 1; i++) {
            JsonObject files = node.getAsJsonObject("files");
            if (!files.has(parts[i])) {
                files.add(parts[i], directory());
            }
            node = files.getAsJsonObject(parts[i]);
        }
        return node;
    }
}
```

- [ ] **Step 5 : écrire les tests qui échouent**

`src/test/java/dev/blockymodelviewer/AsarArchiveTest.java` :

```java
package dev.blockymodelviewer;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AsarArchiveTest {
    @TempDir
    Path tmp;

    @Test
    void readsPackedEntriesAtAnyDepth() throws Exception {
        Path asar = AsarWriter.write(
                tmp.resolve("app.asar"), Map.of("index.html", "<html>", "dist/bundle.js", "code();"), Set.of());
        AsarArchive archive = new AsarArchive(asar);

        assertEquals("<html>", new String(archive.read("index.html").orElseThrow(), UTF_8));
        assertEquals("code();", new String(archive.read("dist/bundle.js").orElseThrow(), UTF_8));
    }

    @Test
    void missingEntryAndDirectoryAreEmpty() throws Exception {
        AsarArchive archive = new AsarArchive(
                AsarWriter.write(tmp.resolve("app.asar"), Map.of("dist/bundle.js", "x"), Set.of()));

        assertTrue(archive.read("dist/missing.js").isEmpty());
        assertTrue(archive.read("dist").isEmpty());
        assertTrue(archive.read("../app.asar").isEmpty());
    }

    @Test
    void unpackedEntryIsReadFromTheSiblingFolder() throws Exception {
        AsarArchive archive = new AsarArchive(AsarWriter.write(
                tmp.resolve("app.asar"), Map.of("lib/native.node", "binary"), Set.of("lib/native.node")));

        assertEquals("binary", new String(archive.read("lib/native.node").orElseThrow(), UTF_8));
    }

    @Test
    void installedBlockbenchArchiveHoldsItsPageAndBundle() throws Exception {
        Path asar = Path.of(System.getenv().getOrDefault("LOCALAPPDATA", ""), "Programs", "Blockbench", "resources",
                "app.asar");
        assumeTrue(Files.isRegularFile(asar), "Blockbench is not installed");
        AsarArchive archive = new AsarArchive(asar);

        assertTrue(new String(archive.read("index.html").orElseThrow(), UTF_8).contains("dist/bundle.js"));
        assertTrue(archive.read("dist/bundle.js").orElseThrow().length > 1_000_000);
    }
}
```

- [ ] **Step 6 : vérifier qu'ils échouent**

Run : `./gradlew test --tests dev.blockymodelviewer.AsarArchiveTest`
Expected : échec de compilation, `cannot find symbol: class AsarArchive`.

- [ ] **Step 7 : écrire `AsarArchive`**

`src/main/java/dev/blockymodelviewer/AsarArchive.java` :

```java
package dev.blockymodelviewer;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Optional;

/**
 * Reads entries of an Electron asar archive: a JSON header (length at byte 12, from byte 16) then the file data,
 * which starts at {@code 8 + uint32@4}. Entries flagged {@code unpacked} live in the sibling {@code .unpacked} folder.
 */
final class AsarArchive {
    private final Path archive;
    private final JsonObject root;
    private final long dataStart;

    AsarArchive(Path archive) throws IOException {
        this.archive = archive;
        try (FileChannel channel = FileChannel.open(archive, StandardOpenOption.READ)) {
            ByteBuffer head = readFully(channel, 0, 16).order(ByteOrder.LITTLE_ENDIAN);
            this.dataStart = 8 + Integer.toUnsignedLong(head.getInt(4));
            ByteBuffer json = readFully(channel, 16, head.getInt(12));
            this.root = JsonParser.parseString(new String(json.array(), UTF_8)).getAsJsonObject();
        }
    }

    /** Returns the bytes of the file at a slash-separated path; empty for a directory or an absent entry. */
    Optional<byte[]> read(String path) throws IOException {
        JsonObject node = root;
        for (String part : path.split("/")) {
            JsonObject files = node.getAsJsonObject("files");
            JsonElement child = files == null ? null : files.get(part);
            if (child == null || !child.isJsonObject()) {
                return Optional.empty();
            }
            node = child.getAsJsonObject();
        }
        if (node.has("files")) {
            return Optional.empty();
        }
        if (node.has("unpacked") && node.get("unpacked").getAsBoolean()) {
            Path file = archive.resolveSibling(archive.getFileName() + ".unpacked").resolve(path);
            return Files.isRegularFile(file) ? Optional.of(Files.readAllBytes(file)) : Optional.empty();
        }
        long offset = Long.parseLong(node.get("offset").getAsString());
        try (FileChannel channel = FileChannel.open(archive, StandardOpenOption.READ)) {
            return Optional.of(readFully(channel, dataStart + offset, node.get("size").getAsInt()).array());
        }
    }

    private static ByteBuffer readFully(FileChannel channel, long position, int size) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(size);
        while (buffer.hasRemaining()) {
            if (channel.read(buffer, position + buffer.position()) < 0) {
                throw new IOException("Truncated asar archive");
            }
        }
        return buffer;
    }
}
```

- [ ] **Step 8 : vérifier qu'ils passent**

Run : `./gradlew test --tests dev.blockymodelviewer.AsarArchiveTest`
Expected : 4 tests passent (le 4ᵉ lit le vrai `app.asar` de Blockbench 5.2.1).

- [ ] **Step 9 : commit**

```bash
git add settings.gradle.kts build.gradle.kts gradle.properties .gitignore .editorconfig gradlew gradlew.bat \
  gradle/wrapper/gradle-wrapper.jar gradle/wrapper/gradle-wrapper.properties \
  src/main/resources/META-INF/plugin.xml src/main/java/dev/blockymodelviewer/AsarArchive.java \
  src/test/java/dev/blockymodelviewer/AsarWriter.java src/test/java/dev/blockymodelviewer/AsarArchiveTest.java
git commit -m "feat: plugin skeleton and asar archive reader

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2 : `BlockbenchInstall`

**Files :**
- Create : `src/main/java/dev/blockymodelviewer/BlockbenchInstall.java`
- Test : `src/test/java/dev/blockymodelviewer/BlockbenchInstallTest.java`

**Interfaces :**
- Produces :
  `sealed interface BlockbenchInstall { record Found(Path exe, Path appAsar, Path pluginsDir) ; record Missing(String reason) ; static BlockbenchInstall locate(String configuredDir, Map<String, String> env); }`

- [ ] **Step 1 : écrire les tests qui échouent**

`src/test/java/dev/blockymodelviewer/BlockbenchInstallTest.java` :

```java
package dev.blockymodelviewer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BlockbenchInstallTest {
    @TempDir
    Path tmp;

    private Map<String, String> env() {
        return Map.of("LOCALAPPDATA", tmp.resolve("local").toString(), "APPDATA", tmp.resolve("roaming").toString());
    }

    private Path installAt(Path dir) throws Exception {
        Files.createDirectories(dir.resolve("resources"));
        Files.writeString(dir.resolve("resources/app.asar"), "asar");
        Files.writeString(dir.resolve("Blockbench.exe"), "exe");
        return dir;
    }

    private void installHytalePlugin() throws Exception {
        Path plugins = Files.createDirectories(tmp.resolve("roaming/Blockbench/plugins"));
        Files.writeString(plugins.resolve("hytale_plugin.js"), "js");
    }

    @Test
    void blankSettingUsesTheDefaultFolderUnderLocalAppData() throws Exception {
        Path dir = installAt(tmp.resolve("local/Programs/Blockbench"));
        installHytalePlugin();

        BlockbenchInstall.Found found =
                assertInstanceOf(BlockbenchInstall.Found.class, BlockbenchInstall.locate("", env()));

        assertEquals(dir.resolve("Blockbench.exe"), found.exe());
        assertEquals(dir.resolve("resources/app.asar"), found.appAsar());
        assertEquals(tmp.resolve("roaming/Blockbench/plugins"), found.pluginsDir());
    }

    @Test
    void configuredFolderWinsOverTheDefault() throws Exception {
        Path dir = installAt(tmp.resolve("custom"));
        installHytalePlugin();

        BlockbenchInstall.Found found = assertInstanceOf(
                BlockbenchInstall.Found.class, BlockbenchInstall.locate("  " + dir + "  ", env()));

        assertEquals(dir.resolve("resources/app.asar"), found.appAsar());
    }

    @Test
    void missingArchiveIsReportedWithItsPath() {
        BlockbenchInstall.Missing missing =
                assertInstanceOf(BlockbenchInstall.Missing.class, BlockbenchInstall.locate("", env()));

        assertTrue(missing.reason().contains("app.asar"), missing.reason());
    }

    @Test
    void missingHytalePluginIsReported() throws Exception {
        installAt(tmp.resolve("local/Programs/Blockbench"));

        BlockbenchInstall.Missing missing =
                assertInstanceOf(BlockbenchInstall.Missing.class, BlockbenchInstall.locate("", env()));

        assertTrue(missing.reason().contains("hytale_plugin.js"), missing.reason());
    }

    @Test
    void emptyEnvironmentIsMissingNotAnException() {
        assertInstanceOf(BlockbenchInstall.Missing.class, BlockbenchInstall.locate("", Map.of()));
    }
}
```

- [ ] **Step 2 : vérifier qu'ils échouent**

Run : `./gradlew test --tests dev.blockymodelviewer.BlockbenchInstallTest`
Expected : échec de compilation, `cannot find symbol: class BlockbenchInstall`.

- [ ] **Step 3 : écrire `BlockbenchInstall`**

`src/main/java/dev/blockymodelviewer/BlockbenchInstall.java` :

```java
package dev.blockymodelviewer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/** The Blockbench desktop install and its Hytale plugin, or why they cannot be used. */
sealed interface BlockbenchInstall {
    /** A usable install: the executable, its bundle archive and Blockbench's plugins folder. */
    record Found(Path exe, Path appAsar, Path pluginsDir) implements BlockbenchInstall {}

    /** No usable install; {@code reason} is shown to the user. */
    record Missing(String reason) implements BlockbenchInstall {}

    /**
     * Looks in {@code configuredDir}, or in {@code %LOCALAPPDATA%\Programs\Blockbench} when it is blank, and requires
     * {@code %APPDATA%\Blockbench\plugins\hytale_plugin.js}. Never throws.
     */
    static BlockbenchInstall locate(String configuredDir, Map<String, String> env) {
        String configured = configuredDir.strip();
        Path dir = configured.isEmpty()
                ? Path.of(env.getOrDefault("LOCALAPPDATA", ""), "Programs", "Blockbench")
                : Path.of(configured);
        Path asar = dir.resolve("resources").resolve("app.asar");
        if (!Files.isRegularFile(asar)) {
            return new Missing("Blockbench not found: " + asar.toAbsolutePath());
        }
        Path plugins = Path.of(env.getOrDefault("APPDATA", ""), "Blockbench", "plugins");
        Path hytale = plugins.resolve("hytale_plugin.js");
        if (!Files.isRegularFile(hytale)) {
            return new Missing("The Hytale plugin is not installed in Blockbench: " + hytale.toAbsolutePath());
        }
        return new Found(dir.resolve("Blockbench.exe"), asar, plugins);
    }
}
```

- [ ] **Step 4 : vérifier qu'ils passent**

Run : `./gradlew test --tests dev.blockymodelviewer.BlockbenchInstallTest`
Expected : 5 tests passent.

- [ ] **Step 5 : commit**

```bash
git add src/main/java/dev/blockymodelviewer/BlockbenchInstall.java \
  src/test/java/dev/blockymodelviewer/BlockbenchInstallTest.java
git commit -m "feat: locate the Blockbench install and its Hytale plugin

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3 : `BlockbenchRoutes`

**Files :**
- Create : `src/main/java/dev/blockymodelviewer/BlockbenchRoutes.java`
- Test : `src/test/java/dev/blockymodelviewer/BlockbenchRoutesTest.java`

**Interfaces :**
- Consumes : `AsarArchive.read(String)` (tâche 1), `AsarWriter.write(…)` (tâche 1, tests).
- Produces :
  `final class BlockbenchRoutes { record Response(int status, String mimeType, byte[] body) { static Response notFound(); } BlockbenchRoutes(AsarArchive asar, Path pluginsDir, List<Path> allowedRoots); Response handle(String url); }`
  Routes : `/bb/index.html` (page modifiée), `/bb/<entrée>`, `/viewer/<ressource>`, `/plugins/<fichier>`,
  `/fs/read|stat|list?p=<chemin>`, `/<lettre>:/<chemin>`.

- [ ] **Step 1 : écrire les tests qui échouent**

`src/test/java/dev/blockymodelviewer/BlockbenchRoutesTest.java` :

```java
package dev.blockymodelviewer;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BlockbenchRoutesTest {
    private static final String ORIGIN = "http://blockbench.localhost";
    private static final String INDEX =
            "<html><head><title>BB</title></head><body><script type=\"module\" src=\"dist/bundle.js\"></script>"
                    + "</body></html>";

    @TempDir
    Path tmp;

    private Path project;
    private Path plugins;
    private BlockbenchRoutes routes;

    @BeforeEach
    void setUp() throws Exception {
        project = Files.createDirectories(tmp.resolve("project"));
        plugins = Files.createDirectories(tmp.resolve("plugins"));
        Files.writeString(plugins.resolve("hytale_plugin.js"), "plugin();");
        Files.writeString(tmp.resolve("secret.txt"), "secret");
        Path asar = AsarWriter.write(tmp.resolve("bb/app.asar"),
                Map.of("index.html", INDEX, "dist/bundle.js", "bundle();", "css/general.css", "body{}"), Set.of());
        routes = new BlockbenchRoutes(new AsarArchive(asar), plugins, List.of(project));
    }

    private static String fs(String op, Path path) {
        return ORIGIN + "/fs/" + op + "?p=" + URLEncoder.encode(path.toString().replace('\\', '/'), UTF_8);
    }

    private static String drive(Path path) throws Exception {
        return new URI("http", "blockbench.localhost", "/" + path.toString().replace('\\', '/'), null).toString();
    }

    private static String text(BlockbenchRoutes.Response response) {
        return new String(response.body(), UTF_8);
    }

    @Test
    void indexLoadsTheShimBeforeTheBundleAndTheViewerAfterIt() {
        BlockbenchRoutes.Response response = routes.handle(ORIGIN + "/bb/index.html");
        String html = text(response);

        assertEquals("text/html", response.mimeType());
        assertTrue(html.contains("<script src=\"/viewer/node-shim.js\"></script></head>"), html);
        assertTrue(html.contains("</script><script type=\"module\" src=\"/viewer/viewer.js\"></script></body>"), html);
    }

    @Test
    void asarEntriesAreServedWithTheirMimeType() {
        BlockbenchRoutes.Response script = routes.handle(ORIGIN + "/bb/dist/bundle.js");
        BlockbenchRoutes.Response style = routes.handle(ORIGIN + "/bb/css/general.css?v=2");

        assertEquals(200, script.status());
        assertEquals("text/javascript", script.mimeType());
        assertEquals("bundle();", text(script));
        assertEquals("text/css", style.mimeType());
    }

    @Test
    void pluginsRouteServesBlockbenchsPluginsFolder() {
        assertEquals("plugin();", text(routes.handle(ORIGIN + "/plugins/hytale_plugin.js")));
    }

    @Test
    void pluginsRouteRefusesParentTraversal() {
        assertEquals(404, routes.handle(ORIGIN + "/plugins/../secret.txt").status());
        assertEquals(404, routes.handle(ORIGIN + "/plugins/%2E%2E/secret.txt").status());
    }

    @Test
    void fsReadServesFileInsideAnAllowedRoot() throws Exception {
        Path model = Files.writeString(project.resolve("Model.blockymodel"), "{\"nodes\":[]}");

        BlockbenchRoutes.Response response = routes.handle(fs("read", model));

        assertEquals(200, response.status());
        assertEquals("{\"nodes\":[]}", text(response));
    }

    @Test
    void fsReadServesFileWithSpacesAndAccents() throws Exception {
        Path dir = Files.createDirectories(project.resolve("Mes modèles"));
        Path texture = Files.writeString(dir.resolve("Texture é.png"), "png");

        assertEquals("png", text(routes.handle(fs("read", texture))));
        assertEquals("png", text(routes.handle(drive(texture))));
    }

    @Test
    void fsReadRefusesFileOutsideTheAllowedRoots() {
        assertEquals(404, routes.handle(fs("read", tmp.resolve("secret.txt"))).status());
    }

    @Test
    void fsReadRefusesParentTraversal() {
        assertEquals(404, routes.handle(fs("read", project.resolve("../secret.txt"))).status());
    }

    @Test
    void fsPluginsPrefixMapsToBlockbenchsPluginsFolder() {
        String url = ORIGIN + "/fs/read?p=" + URLEncoder.encode("/plugins/hytale_plugin.js", UTF_8);

        assertEquals("plugin();", text(routes.handle(url)));
    }

    @Test
    void fsStatDescribesFilesAndFolders() throws Exception {
        Files.writeString(project.resolve("a.png"), "1234");

        assertTrue(text(routes.handle(fs("stat", project))).contains("\"dir\":true"));
        String file = text(routes.handle(fs("stat", project.resolve("a.png"))));
        assertTrue(file.contains("\"file\":true") && file.contains("\"size\":4"), file);
        assertEquals(404, routes.handle(fs("stat", project.resolve("none.png"))).status());
    }

    @Test
    void fsListNamesTheChildrenOfAFolder() throws Exception {
        Files.writeString(project.resolve("a.png"), "");

        BlockbenchRoutes.Response response = routes.handle(fs("list", project));

        assertEquals("application/json", response.mimeType());
        assertEquals("[\"a.png\"]", text(response));
    }

    @Test
    void drivePathServesATextureInsideAnAllowedRoot() throws Exception {
        Path texture = Files.writeString(project.resolve("Texture.png"), "png");

        BlockbenchRoutes.Response response = routes.handle(drive(texture) + "?1");

        assertEquals("image/png", response.mimeType());
        assertEquals("png", text(response));
    }

    @Test
    void unknownRoutesAndMalformedUrlsAreNotFound() {
        assertEquals(404, routes.handle(ORIGIN + "/elsewhere").status());
        assertEquals(404, routes.handle(ORIGIN + "/bb/missing.js").status());
        assertEquals(404, routes.handle(ORIGIN + "/fs/delete?p=x").status());
        assertEquals(404, routes.handle("not a url %").status());
    }
}
```

- [ ] **Step 2 : vérifier qu'ils échouent**

Run : `./gradlew test --tests dev.blockymodelviewer.BlockbenchRoutesTest`
Expected : échec de compilation, `cannot find symbol: class BlockbenchRoutes`.

- [ ] **Step 3 : écrire `BlockbenchRoutes`**

`src/main/java/dev/blockymodelviewer/BlockbenchRoutes.java` :

```java
package dev.blockymodelviewer;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Answers the embedded browser: Blockbench's own files from its asar, the viewer scripts, Blockbench's plugins
 * folder, and a read-only file system limited to the allowed roots. Everything else is a 404. Never throws.
 */
final class BlockbenchRoutes {
    /** An HTTP answer. */
    record Response(int status, String mimeType, byte[] body) {
        static Response notFound() {
            return new Response(404, "text/plain", new byte[0]);
        }

        static Response ok(String mimeType, byte[] body) {
            return new Response(200, mimeType, body);
        }
    }

    private static final Gson GSON = new Gson();
    private static final Pattern DRIVE_PATH = Pattern.compile("/[A-Za-z]:/.*");
    private static final String PLUGINS = "/plugins/";
    private static final Map<String, String> MIME_TYPES = Map.of(
            "html", "text/html", "js", "text/javascript", "css", "text/css", "json", "application/json",
            "png", "image/png", "svg", "image/svg+xml", "woff2", "font/woff2", "woff", "font/woff",
            "ttf", "font/ttf", "webmanifest", "application/manifest+json");

    private final AsarArchive asar;
    private final Path pluginsDir;
    private final List<Path> allowedRoots;

    /** {@code allowedRoots} bound the file system; Blockbench's plugins folder is always allowed. */
    BlockbenchRoutes(AsarArchive asar, Path pluginsDir, List<Path> allowedRoots) {
        this.asar = asar;
        this.pluginsDir = normalize(pluginsDir);
        List<Path> roots = new ArrayList<>(allowedRoots.stream().map(BlockbenchRoutes::normalize).toList());
        roots.add(this.pluginsDir);
        this.allowedRoots = List.copyOf(roots);
    }

    /** Returns the answer for {@code url}; a 404 for anything unknown, malformed, missing or out of bounds. */
    Response handle(String url) {
        try {
            URI uri = URI.create(url);
            String path = Optional.ofNullable(uri.getPath()).orElse("");
            if (path.equals("/bb/index.html")) {
                return indexPage();
            }
            if (path.startsWith("/bb/")) {
                return asar.read(path.substring(4)).map(body -> Response.ok(mimeOf(path), body))
                        .orElseGet(Response::notFound);
            }
            if (path.startsWith("/viewer/")) {
                return viewerResource(path.substring(8));
            }
            if (path.startsWith(PLUGINS)) {
                return file(pluginsDir.resolve(path.substring(PLUGINS.length())));
            }
            if (path.startsWith("/fs/")) {
                return fileSystem(path.substring(4), queryParameter(uri.getRawQuery(), "p"));
            }
            if (DRIVE_PATH.matcher(path).matches()) {
                return file(Path.of(path.substring(1)));
            }
        } catch (IOException | IllegalArgumentException e) {
            // Malformed URL, invalid path or unreadable file: the browser sees a missing resource.
        }
        return Response.notFound();
    }

    /** Blockbench's page with the Node shim before the bundle and the viewer after it. */
    private Response indexPage() throws IOException {
        Optional<byte[]> page = asar.read("index.html");
        if (page.isEmpty()) {
            return Response.notFound();
        }
        String html = new String(page.get(), UTF_8)
                .replace("</head>", "<script src=\"/viewer/node-shim.js\"></script></head>");
        int bodyEnd = html.lastIndexOf("</body>");
        if (bodyEnd >= 0) {
            html = html.substring(0, bodyEnd) + "<script type=\"module\" src=\"/viewer/viewer.js\"></script>"
                    + html.substring(bodyEnd);
        }
        return Response.ok("text/html", html.getBytes(UTF_8));
    }

    private Response viewerResource(String name) throws IOException {
        if (name.contains("/") || name.contains("..")) {
            return Response.notFound();
        }
        try (InputStream in = BlockbenchRoutes.class.getResourceAsStream("/viewer/" + name)) {
            return in == null ? Response.notFound() : Response.ok(mimeOf(name), in.readAllBytes());
        }
    }

    /** {@code read} gives the bytes, {@code stat} and {@code list} give JSON; {@code /plugins/} is Blockbench's folder. */
    private Response fileSystem(String op, Optional<String> target) throws IOException {
        if (target.isEmpty()) {
            return Response.notFound();
        }
        String raw = target.get();
        Path path = raw.startsWith(PLUGINS) ? pluginsDir.resolve(raw.substring(PLUGINS.length())) : Path.of(raw);
        Optional<Path> allowed = allowed(path).filter(Files::exists);
        if (allowed.isEmpty()) {
            return Response.notFound();
        }
        Path found = allowed.get();
        return switch (op) {
            case "read" -> Files.isRegularFile(found)
                    ? Response.ok("application/octet-stream", Files.readAllBytes(found))
                    : Response.notFound();
            case "stat" -> Response.ok("application/json", stat(found).toString().getBytes(UTF_8));
            case "list" -> Response.ok("application/json", list(found).getBytes(UTF_8));
            default -> Response.notFound();
        };
    }

    private static JsonObject stat(Path path) throws IOException {
        JsonObject json = new JsonObject();
        json.addProperty("dir", Files.isDirectory(path));
        json.addProperty("file", Files.isRegularFile(path));
        json.addProperty("size", Files.isRegularFile(path) ? Files.size(path) : 0);
        json.addProperty("mtimeMs", Files.getLastModifiedTime(path).toMillis());
        return json;
    }

    private static String list(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) {
            return "[]";
        }
        try (Stream<Path> children = Files.list(dir)) {
            return GSON.toJson(children.map(child -> child.getFileName().toString()).sorted().toList());
        }
    }

    private Response file(Path path) throws IOException {
        Optional<Path> allowed = allowed(path).filter(Files::isRegularFile);
        return allowed.isPresent()
                ? Response.ok(mimeOf(allowed.get().toString()), Files.readAllBytes(allowed.get()))
                : Response.notFound();
    }

    /** The normalized path when it lies under an allowed root; {@code ..} cannot climb out. */
    private Optional<Path> allowed(Path path) {
        try {
            Path normalized = normalize(path);
            return allowedRoots.stream().anyMatch(normalized::startsWith) ? Optional.of(normalized) : Optional.empty();
        } catch (InvalidPathException e) {
            return Optional.empty();
        }
    }

    private static Path normalize(Path path) {
        return path.toAbsolutePath().normalize();
    }

    private static Optional<String> queryParameter(String rawQuery, String name) {
        if (rawQuery == null) {
            return Optional.empty();
        }
        for (String pair : rawQuery.split("&")) {
            int equals = pair.indexOf('=');
            if (equals > 0 && pair.substring(0, equals).equals(name)) {
                return Optional.of(URLDecoder.decode(pair.substring(equals + 1), UTF_8));
            }
        }
        return Optional.empty();
    }

    private static String mimeOf(String name) {
        String extension = name.substring(name.lastIndexOf('.') + 1).toLowerCase(java.util.Locale.ROOT);
        return MIME_TYPES.getOrDefault(extension, "application/octet-stream");
    }
}
```

- [ ] **Step 4 : vérifier qu'ils passent**

Run : `./gradlew test --tests dev.blockymodelviewer.BlockbenchRoutesTest`
Expected : 13 tests passent. `indexLoadsTheShimBeforeTheBundleAndTheViewerAfterIt` ne demande pas que les scripts
existent : ils arrivent à la tâche 4.

- [ ] **Step 5 : commit**

```bash
git add src/main/java/dev/blockymodelviewer/BlockbenchRoutes.java \
  src/test/java/dev/blockymodelviewer/BlockbenchRoutesTest.java
git commit -m "feat: routes serving Blockbench, its plugins and a read-only file system

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4 : `node-shim.js`, `viewer.js` et le test de démarrage

**Files :**
- Create : `src/main/resources/viewer/node-shim.js`, `src/main/resources/viewer/viewer.js`
- Copy : `src/test/resources/fixture/Blocks/Tape/Corner.blockymodel`, `src/test/resources/fixture/Blocks/Tape/Texture.png`
- Test : `src/test/java/dev/blockymodelviewer/BlockbenchSmokeTest.java`, et un test ajouté à `BlockbenchRoutesTest`

**Interfaces :**
- Consumes : `BlockbenchRoutes` (tâche 3), `BlockbenchInstall.locate` (tâche 2), `AsarArchive` (tâche 1).
- Produces (JS, utilisé par la tâche 5) :
  - `window.bbv.showModel(path: string, text: string): Promise` — recharge le modèle, garde la caméra ;
  - `window.bbv.flush()` — envoie les messages en attente à `window.bbvNotify` ;
  - messages envoyés par `window.bbvNotify(message)` : `"ready"` ou `"error:<texte>"` ;
  - `<html data-bbv="ready|model:<n>|error:<texte>">` ;
  - `window.__bbvFs` : le `fs` virtuel ; `?model=<chemin>` dans l'URL charge ce modèle au démarrage.

- [ ] **Step 1 : copier le modèle de test**

```bash
mkdir -p src/test/resources/fixture/Blocks/Tape
cp /c/Users/Ctuto/Desktop/HyColony/plugin/src/main/resources/Common/Blocks/HyColony/Construction_Tape/Corner.blockymodel \
   src/test/resources/fixture/Blocks/Tape/
# Each tape shape has its own texture since 2026-10-02: the corner's becomes the fixture's Texture.png.
cp /c/Users/Ctuto/Desktop/HyColony/plugin/src/main/resources/Common/Blocks/HyColony/Construction_Tape/Corner.png \
   src/test/resources/fixture/Blocks/Tape/Texture.png
```

Le chemin contient `Blocks` : le plugin Hytale choisit le format `hytale_prop`, comme pour les vrais modèles.

- [ ] **Step 2 : écrire les tests qui échouent**

Ajouter à `BlockbenchRoutesTest` :

```java
    @Test
    void viewerScriptsAreServedFromThePluginResources() {
        BlockbenchRoutes.Response shim = routes.handle(ORIGIN + "/viewer/node-shim.js");

        assertEquals(200, shim.status());
        assertEquals("text/javascript", shim.mimeType());
        assertTrue(text(shim).contains("__bbvFs"));
        assertTrue(text(routes.handle(ORIGIN + "/viewer/viewer.js")).contains("window.bbv"));
        assertEquals(404, routes.handle(ORIGIN + "/viewer/../META-INF/plugin.xml").status());
    }
```

`src/test/java/dev/blockymodelviewer/BlockbenchSmokeTest.java` :

```java
package dev.blockymodelviewer;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.sun.net.httpserver.HttpServer;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Boots the installed Blockbench through {@link BlockbenchRoutes} in headless Edge and loads a real model. */
class BlockbenchSmokeTest {
    private static final Pattern LOADED = Pattern.compile("data-bbv=\"model:[1-9]\\d*\"");

    @TempDir
    Path tmp;

    @Test
    void installedBlockbenchStartsAndRendersAModel() throws Exception {
        assumeTrue(
                BlockbenchInstall.locate("", System.getenv()) instanceof BlockbenchInstall.Found,
                "Blockbench or its Hytale plugin is not installed");
        BlockbenchInstall.Found found = (BlockbenchInstall.Found) BlockbenchInstall.locate("", System.getenv());
        Path edge = Path.of(System.getenv().getOrDefault("ProgramFiles(x86)", "C:/Program Files (x86)"),
                "Microsoft", "Edge", "Application", "msedge.exe");
        assumeTrue(Files.isRegularFile(edge), "Edge is not installed");
        Path fixture = Path.of("src/test/resources/fixture").toAbsolutePath();
        Path model = fixture.resolve("Blocks/Tape/Corner.blockymodel");
        BlockbenchRoutes routes = new BlockbenchRoutes(new AsarArchive(found.appAsar()), found.pluginsDir(),
                List.of(fixture));

        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            BlockbenchRoutes.Response response =
                    routes.handle("http://blockbench.localhost" + exchange.getRequestURI());
            exchange.getResponseHeaders().set("Content-Type", response.mimeType());
            exchange.sendResponseHeaders(response.status(), response.body().length == 0 ? -1 : response.body().length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(response.body());
            }
        });
        server.start();
        try {
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/bb/index.html?model="
                    + URLEncoder.encode(model.toString().replace('\\', '/'), UTF_8);
            Process browser = new ProcessBuilder(edge.toString(), "--headless=new", "--disable-gpu", "--no-first-run",
                            "--user-data-dir=" + tmp.resolve("edge"), "--virtual-time-budget=20000", "--dump-dom", url)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            String dom = new String(browser.getInputStream().readAllBytes(), UTF_8);
            browser.waitFor(60, TimeUnit.SECONDS);

            assertTrue(LOADED.matcher(dom).find(), "Blockbench did not render the model:\n"
                    + dom.substring(0, Math.min(dom.length(), 2000)));
        } finally {
            server.stop(0);
        }
    }
}
```

- [ ] **Step 3 : vérifier qu'ils échouent**

Run : `./gradlew test --tests dev.blockymodelviewer.BlockbenchRoutesTest --tests dev.blockymodelviewer.BlockbenchSmokeTest`
Expected : `viewerScriptsAreServedFromThePluginResources` échoue (404, ressources absentes) ;
`installedBlockbenchStartsAndRendersAModel` échoue (`Blockbench did not render the model`).

- [ ] **Step 4 : écrire `node-shim.js`**

`src/main/resources/viewer/node-shim.js` :

```js
// Lets Blockbench's desktop bundle, built for Electron, start in a plain Chromium page. Node and Electron modules
// become stand-ins; files are read through the host's read-only /fs routes and writes stay in memory.
(() => {
    const norm = path => String(path).replace(/\\/g, "/");

    // Blockbench restores installed plugins and offers to restore a backup on start: the viewer loads the Hytale
    // plugin itself and never wants the dialog.
    localStorage.removeItem("StateMemory.installed_plugins");
    localStorage.removeItem("backup_model");

    // A value that accepts any property access, call or construction and yields another stand-in.
    const stub = () => {
        const target = function () {};
        return new Proxy(target, {
            get: (t, key) => {
                if (key === Symbol.toPrimitive || key === "toString") return () => "";
                if (key === "then") return undefined;
                if (key in t && key !== "name" && key !== "length") return t[key];
                return (t[key] ??= stub());
            },
            apply: () => stub(),
            construct: () => stub(),
        });
    };

    const memory = new Map();
    const host = (op, path) => {
        const request = new XMLHttpRequest();
        request.open("GET", "/fs/" + op + "?p=" + encodeURIComponent(norm(path)), false);
        request.overrideMimeType("text/plain; charset=x-user-defined");
        request.send();
        return request.status === 200 ? request.responseText : null;
    };
    const bytesOf = text => Uint8Array.from(text, c => c.charCodeAt(0) & 0xff);
    const missing = path => Object.assign(new Error("ENOENT: " + path), { code: "ENOENT" });
    const asBuffer = bytes => {
        bytes.toString = function (encoding) {
            // No spread into fromCharCode: a texture has more bytes than a call has room for arguments.
            return encoding === "base64"
                ? btoa(Array.from(this, b => String.fromCharCode(b)).join(""))
                : new TextDecoder().decode(this);
        };
        return bytes;
    };
    const read = (path, options) => {
        const key = norm(path);
        const encoding = typeof options === "string" ? options : options?.encoding;
        let bytes = memory.get(key);
        if (!bytes) {
            const text = host("read", key);
            if (text === null) throw missing(key);
            bytes = bytesOf(text);
        }
        return encoding ? new TextDecoder().decode(bytes) : asBuffer(bytes.slice());
    };
    const info = (dir, file, size, mtimeMs) =>
        ({ isDirectory: () => dir, isFile: () => file, size, mtimeMs, mtime: new Date(mtimeMs) });
    const stat = path => {
        const key = norm(path);
        if (memory.has(key)) return info(false, true, memory.get(key).length, 0);
        const text = host("stat", key);
        if (text === null) throw missing(key);
        const found = JSON.parse(text);
        return info(found.dir, found.file, found.size, found.mtimeMs);
    };
    const callback = args => [...args].reverse().find(arg => typeof arg === "function");
    // Runs fn and hands its result, or its error, to the Node-style callback found in args.
    const answer = (args, fn) => {
        const done = callback(args);
        try {
            done?.(null, fn());
        } catch (error) {
            done?.(error);
        }
    };
    const toBytes = data =>
        typeof data === "string" ? new TextEncoder().encode(data) : new Uint8Array(data.buffer ?? data);

    const fs = {
        existsSync: path => {
            try {
                stat(path);
                return true;
            } catch {
                return false;
            }
        },
        readFileSync: read,
        statSync: stat,
        lstatSync: stat,
        readdirSync: path => JSON.parse(host("list", path) ?? "[]"),
        writeFileSync: (path, data) => memory.set(norm(path), toBytes(data)),
        mkdirSync() {},
        unlinkSync: path => memory.delete(norm(path)),
        rmSync: path => memory.delete(norm(path)),
        watch: () => ({ close() {} }),
        watchFile() {},
        unwatchFile() {},
        readFile: (path, ...rest) => answer(rest, () => read(path, typeof rest[0] === "function" ? undefined : rest[0])),
        writeFile: (path, data, ...rest) => answer(rest, () => fs.writeFileSync(path, data)),
        readdir: (path, ...rest) => answer(rest, () => fs.readdirSync(path)),
        stat: (path, ...rest) => answer(rest, () => stat(path)),
        access: (path, ...rest) => answer(rest, () => stat(path) && undefined),
        exists: (path, done) => done(fs.existsSync(path)),
        mkdir: (path, ...rest) => answer(rest, () => undefined),
        promises: {
            readFile: async (path, options) => read(path, options),
            writeFile: async (path, data) => fs.writeFileSync(path, data),
            stat: async path => stat(path),
            readdir: async path => fs.readdirSync(path),
            mkdir: async () => {},
            access: async path => { stat(path); },
            unlink: async path => fs.unlinkSync(path),
            rm: async path => fs.unlinkSync(path),
        },
    };
    // Any other fs function: call its callback, if any, with success.
    const fsModule = new Proxy(fs, { get: (t, key) => key in t ? t[key] : (...args) => callback(args)?.(null) });

    const path = {
        sep: "/",
        delimiter: ";",
        join: (...parts) => parts.filter(Boolean).join("/").replace(/\/+/g, "/"),
        resolve: (...parts) => parts.filter(Boolean).join("/").replace(/\/+/g, "/"),
        dirname: p => norm(p).replace(/\/[^/]*$/, ""),
        basename: (p, ext) => { const base = norm(p).split("/").pop(); return ext && base.endsWith(ext) ? base.slice(0, -ext.length) : base; },
        extname: p => (norm(p).match(/\.[^./]*$/) ?? [""])[0],
        relative: (from, to) => to,
        normalize: p => p,
        isAbsolute: () => true,
    };
    path.parse = p => ({ dir: path.dirname(p), base: path.basename(p), ext: path.extname(p), name: path.basename(p, path.extname(p)) });
    path.posix = path;
    path.win32 = path;

    const os = {
        platform: () => "win32", type: () => "Windows_NT", release: () => "10", version: () => "10", arch: () => "x64",
        homedir: () => "C:/", tmpdir: () => "C:/", hostname: () => "ide", cpus: () => [], totalmem: () => 8e9,
        freemem: () => 4e9, EOL: "\n",
    };

    // @electron/remote reads every remote value through ipcRenderer.sendSync and accepts {type: "value"}.
    const ipcRenderer = new Proxy({
        sendSync: () => ({ type: "value", value: stub() }),
        invoke: async () => undefined,
        on() {}, once() {}, send() {}, removeListener() {},
    }, { get: (t, key) => key in t ? t[key] : stub() });
    const electron = new Proxy({ ipcRenderer }, { get: (t, key) => key in t ? t[key] : stub() });

    const modules = { electron, fs: fsModule, "node:fs": fsModule, path, "node:path": path, os };
    window.require = name => modules[name] ?? stub();
    window.global = window;
    window.__dirname = "C:/";
    window.process = {
        platform: "win32", env: {}, argv: [], contextId: "blockymodel-viewer",
        versions: { electron: "38.0.0", node: "22.0.0", chrome: "140" },
        cwd: () => "C:/", on() {}, nextTick: fn => setTimeout(fn),
    };
    window.Buffer = {
        from: (value, encoding) => asBuffer(encoding === "base64" ? bytesOf(atob(value))
            : typeof value === "string" ? new TextEncoder().encode(value) : new Uint8Array(value)),
        alloc: size => asBuffer(new Uint8Array(size)),
        isBuffer: () => false,
        concat: list => asBuffer(new Uint8Array(list.flatMap(part => [...part]))),
    };

    // The Hytale plugin sets disk paths ("C:/…/Texture.png") as image sources; serve them through the host.
    const src = Object.getOwnPropertyDescriptor(HTMLImageElement.prototype, "src");
    Object.defineProperty(HTMLImageElement.prototype, "src", {
        get() { return src.get.call(this); },
        set(value) { src.set.call(this, /^[A-Za-z]:[\\/]/.test(value) ? "/" + norm(value) : value); },
    });

    window.__bbvFs = fsModule;
})();
```

- [ ] **Step 5 : écrire `viewer.js`**

`src/main/resources/viewer/viewer.js` :

```js
// Runs after Blockbench's bundle in the IDE preview: loads the Hytale plugin, reduces the interface to the 3D view
// and exposes window.bbv to the IDE. State for outside observers: <html data-bbv="ready|model:<boxes>|error:<text>">.
const BOOT_TIMEOUT_MS = 20000;
const INTERFACE = ["header", "#title_bar", "#tab_bar", "#main_toolbar", ".toolbar_wrapper", "#status_bar",
    "#panel_selector_bar"];
const outbox = [];
let queue = Promise.resolve();

const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));
const setStatus = text => { document.documentElement.dataset.bbv = text; };

function flush() {
    while (window.bbvNotify && outbox.length) window.bbvNotify(outbox.shift());
}

function notify(message) {
    outbox.push(message);
    flush();
}

function report(error) {
    const text = "error:" + String(error?.stack ?? error);
    setStatus(text);
    notify(text);
}

async function boot() {
    for (let waited = 0; !window.Blockbench?.setup_successful; waited += 100) {
        if (waited >= BOOT_TIMEOUT_MS) {
            throw new Error("Blockbench did not start: " + JSON.stringify(window.ErrorLog ?? []));
        }
        await sleep(100);
    }
    if (!Codecs.blockymodel) {
        await new Plugin("hytale_plugin").loadFromURL(location.origin + "/plugins/hytale_plugin.js");
    }
    if (!Codecs.blockymodel) throw new Error("hytale_plugin.js did not register the blockymodel codec");
    hideInterface();
}

function hideInterface() {
    const style = document.createElement("style");
    style.textContent = INTERFACE.join(",") + "{display:none !important}";
    document.head.append(style);
    // The sidebar toggle is remembered by Blockbench: only toggle when they are showing.
    if (document.getElementById("left_bar")?.offsetWidth) BarItems.toggle_sidebars.click();
    resizeWindow();
}

function cameraState() {
    const preview = window.Preview?.selected;
    return Project && preview
        ? { position: preview.camera.position.toArray(), target: preview.controls.target.toArray() }
        : null;
}

async function showModel(path, text) {
    let json;
    try {
        json = JSON.parse(text);
    } catch {
        return; // half-typed JSON: keep the last good render
    }
    try {
        const camera = cameraState();
        if (Project) {
            Project.saved = true;
            await Project.close(true);
        }
        Codecs.blockymodel.load(json, { path });
        if (camera) Preview.selected.loadAnglePreset(camera);
        setStatus("model:" + Cube.all.length);
    } catch (error) {
        report(error);
    }
}

window.bbv = {
    flush,
    showModel: (path, text) => (queue = queue.then(() => showModel(path, text))),
};

boot().then(() => {
    setStatus("ready");
    notify("ready");
    const model = new URLSearchParams(location.search).get("model");
    if (model) window.bbv.showModel(model, window.__bbvFs.readFileSync(model, "utf8"));
}, report);
```

- [ ] **Step 6 : vérifier qu'ils passent**

Run : `./gradlew test --tests dev.blockymodelviewer.BlockbenchRoutesTest --tests dev.blockymodelviewer.BlockbenchSmokeTest`
Expected : tout passe ; le test de démarrage prend quelques secondes (2,3 s pendant l'étude). S'il échoue, le
message montre le début du DOM : chercher `data-bbv="error:…"`, et `window.ErrorLog` y est cité quand Blockbench ne
démarre pas. Une API Node manquante se corrige dans `node-shim.js` (ajouter la fonction au module concerné).

- [ ] **Step 7 : commit**

```bash
git add src/main/resources/viewer/node-shim.js src/main/resources/viewer/viewer.js \
  src/test/resources/fixture/Blocks/Tape/Corner.blockymodel src/test/resources/fixture/Blocks/Tape/Texture.png \
  src/test/java/dev/blockymodelviewer/BlockbenchSmokeTest.java src/test/java/dev/blockymodelviewer/BlockbenchRoutesTest.java
git commit -m "feat: run Blockbench's desktop bundle in a browser with the Hytale plugin

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 5 : l'aperçu dans l'éditeur

**Files :**
- Create : `src/main/java/dev/blockymodelviewer/CefRoutes.java`, `src/main/java/dev/blockymodelviewer/BlockymodelPreview.java`,
  `src/main/java/dev/blockymodelviewer/BlockymodelEditorProvider.java`, `src/main/java/dev/blockymodelviewer/BlockbenchSettings.java`
- Modify : `src/main/resources/META-INF/plugin.xml`

**Interfaces :**
- Consumes : `BlockbenchRoutes(AsarArchive, Path, List<Path>)` et `handle(String)` (tâche 3) ;
  `BlockbenchInstall.locate(String, Map)` (tâche 2) ; `window.bbv.showModel`, `window.bbv.flush`,
  `window.bbvNotify` et les messages `ready` / `error:…` (tâche 4).
- Produces : `public final class BlockbenchSettings` avec `static BlockbenchSettings getInstance()`,
  `String blockbenchDir()`, `void setBlockbenchDir(String)` (utilisé par la tâche 6).

Pas de test unitaire : ces classes ne font qu'assembler la plateforme. Elles sont vérifiées par la compilation,
`verifyPlugin` et l'essai en IDE (tâche 7).

- [ ] **Step 1 : écrire `BlockbenchSettings`**

`src/main/java/dev/blockymodelviewer/BlockbenchSettings.java` :

```java
package dev.blockymodelviewer;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;

/** The Blockbench folder chosen by the user; blank means the default install folder. */
@Service(Service.Level.APP)
@State(name = "BlockymodelViewer", storages = @Storage("blockymodel-viewer.xml"))
public final class BlockbenchSettings implements PersistentStateComponent<BlockbenchSettings.Values> {
    /** Persisted values; public fields as the platform's XML serializer expects. */
    public static final class Values {
        public String blockbenchDir = "";
    }

    private Values values = new Values();

    static BlockbenchSettings getInstance() {
        return ApplicationManager.getApplication().getService(BlockbenchSettings.class);
    }

    @Override
    public Values getState() {
        return values;
    }

    @Override
    public void loadState(Values loaded) {
        values = loaded;
    }

    String blockbenchDir() {
        return values.blockbenchDir == null ? "" : values.blockbenchDir;
    }

    void setBlockbenchDir(String dir) {
        values.blockbenchDir = dir.strip();
    }
}
```

- [ ] **Step 2 : écrire `CefRoutes`**

`src/main/java/dev/blockymodelviewer/CefRoutes.java` :

```java
package dev.blockymodelviewer;

import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.callback.CefCallback;
import org.cef.handler.CefRequestHandlerAdapter;
import org.cef.handler.CefResourceHandler;
import org.cef.handler.CefResourceHandlerAdapter;
import org.cef.handler.CefResourceRequestHandler;
import org.cef.handler.CefResourceRequestHandlerAdapter;
import org.cef.misc.BoolRef;
import org.cef.misc.IntRef;
import org.cef.misc.StringRef;
import org.cef.network.CefRequest;
import org.cef.network.CefResponse;

/** Serves {@link BlockbenchRoutes} on the fake origin; any other http(s) request gets a 404 so nothing goes online. */
final class CefRoutes extends CefRequestHandlerAdapter {
    static final String ORIGIN = "http://blockbench.localhost";

    private final BlockbenchRoutes routes;

    CefRoutes(BlockbenchRoutes routes) {
        this.routes = routes;
    }

    @Override
    public CefResourceRequestHandler getResourceRequestHandler(CefBrowser browser, CefFrame frame,
            CefRequest request, boolean isNavigation, boolean isDownload, String requestInitiator,
            BoolRef disableDefaultHandling) {
        String url = request.getURL();
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return null; // data:, blob:, devtools: stay with Chromium
        }
        boolean ours = url.startsWith(ORIGIN + "/");
        disableDefaultHandling.set(true);
        return new CefResourceRequestHandlerAdapter() {
            @Override
            public CefResourceHandler getResourceHandler(CefBrowser b, CefFrame f, CefRequest r) {
                return new Served(ours ? routes : null);
            }
        };
    }

    /** Streams one {@link BlockbenchRoutes.Response}, computed when Chromium asks for it. */
    private static final class Served extends CefResourceHandlerAdapter {
        private final BlockbenchRoutes routes;
        private BlockbenchRoutes.Response response = BlockbenchRoutes.Response.notFound();
        private int sent;

        Served(BlockbenchRoutes routes) {
            this.routes = routes;
        }

        @Override
        public boolean processRequest(CefRequest request, CefCallback callback) {
            if (routes != null) {
                response = routes.handle(request.getURL());
            }
            callback.Continue();
            return true;
        }

        @Override
        public void getResponseHeaders(CefResponse cefResponse, IntRef length, StringRef redirectUrl) {
            cefResponse.setStatus(response.status());
            cefResponse.setStatusText(response.status() == 200 ? "OK" : "Not Found");
            cefResponse.setMimeType(response.mimeType());
            // Textures are re-read after each bake: never let Chromium answer from its cache.
            cefResponse.setHeaderByName("Cache-Control", "no-store", true);
            length.set(response.body().length);
        }

        @Override
        public boolean readResponse(byte[] out, int bytesToRead, IntRef bytesRead, CefCallback callback) {
            int count = Math.min(bytesToRead, response.body().length - sent);
            if (count <= 0) {
                bytesRead.set(0);
                return false;
            }
            System.arraycopy(response.body(), sent, out, 0, count);
            sent += count;
            bytesRead.set(count);
            return true;
        }
    }
}
```

- [ ] **Step 3 : écrire `BlockymodelPreview`**

`src/main/java/dev/blockymodelviewer/BlockymodelPreview.java` :

```java
package dev.blockymodelviewer;

import com.google.gson.Gson;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.ui.jcef.JBCefApp;
import com.intellij.ui.jcef.JBCefBrowser;
import com.intellij.ui.jcef.JBCefBrowserBase;
import com.intellij.ui.jcef.JBCefJSQuery;
import com.intellij.util.Alarm;
import com.intellij.util.ui.JBUI;
import java.awt.BorderLayout;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.handler.CefLoadHandlerAdapter;

/** Preview half of the split editor: Blockbench's 3D view of the document, refreshed as it is typed. */
final class BlockymodelPreview extends UserDataHolderBase implements FileEditor {
    private static final int REFRESH_DELAY_MS = 300;
    private static final Gson GSON = new Gson();

    private final Project project;
    private final VirtualFile file;
    private final JPanel panel = new JPanel(new BorderLayout());
    private final JLabel message = new JLabel();
    private final Alarm refresh = new Alarm(Alarm.ThreadToUse.SWING_THREAD, this);
    private JBCefBrowser browser;
    private boolean ready;

    BlockymodelPreview(Project project, VirtualFile file) {
        this.project = project;
        this.file = file;
        message.setBorder(JBUI.Borders.empty(8));
        message.setVisible(false);
        panel.add(message, BorderLayout.NORTH);
        switch (BlockbenchInstall.locate(BlockbenchSettings.getInstance().blockbenchDir(), System.getenv())) {
            case BlockbenchInstall.Missing missing -> showMessage(missing.reason());
            case BlockbenchInstall.Found found -> start(found);
        }
    }

    /** Opens Blockbench in an embedded browser and follows the document and the textures next to the model. */
    private void start(BlockbenchInstall.Found found) {
        if (!JBCefApp.isSupported()) {
            showMessage("The embedded browser (JCEF) is not available in this IDE.");
            return;
        }
        BlockbenchRoutes routes;
        try {
            routes = new BlockbenchRoutes(new AsarArchive(found.appAsar()), found.pluginsDir(), allowedRoots());
        } catch (IOException | RuntimeException e) {
            showMessage("Cannot read " + found.appAsar() + ": " + e.getMessage());
            return;
        }
        browser = JBCefBrowser.createBuilder().setEnableOpenDevToolsMenuItem(true).build();
        Disposer.register(this, browser);
        JBCefJSQuery query = JBCefJSQuery.create((JBCefBrowserBase) browser);
        Disposer.register(this, query);
        query.addHandler(text -> {
            ApplicationManager.getApplication().invokeLater(() -> onMessage(text));
            return null;
        });
        browser.getJBCefClient().addRequestHandler(new CefRoutes(routes), browser.getCefBrowser());
        browser.getJBCefClient().addLoadHandler(new CefLoadHandlerAdapter() {
            @Override
            public void onLoadEnd(CefBrowser cefBrowser, CefFrame frame, int httpStatusCode) {
                if (frame.isMain()) {
                    cefBrowser.executeJavaScript("window.bbvNotify = message => {" + query.inject("message")
                            + "}; window.bbv && window.bbv.flush();", frame.getURL(), 0);
                }
            }
        }, browser.getCefBrowser());
        panel.add(browser.getComponent(), BorderLayout.CENTER);
        followDocumentAndTextures();
        browser.loadURL(CefRoutes.ORIGIN + "/bb/index.html");
    }

    /** The project's content roots and the model's own folder (it may be opened from outside the project). */
    private List<Path> allowedRoots() {
        List<Path> roots = new ArrayList<>();
        for (VirtualFile root : ProjectRootManager.getInstance(project).getContentRoots()) {
            if (root.isInLocalFileSystem()) {
                roots.add(root.toNioPath());
            }
        }
        roots.add(file.toNioPath().getParent());
        return roots;
    }

    private void followDocumentAndTextures() {
        Document document = FileDocumentManager.getInstance().getDocument(file);
        if (document != null) {
            document.addDocumentListener(new DocumentListener() {
                @Override
                public void documentChanged(DocumentEvent event) {
                    scheduleRefresh();
                }
            }, this);
        }
        project.getMessageBus().connect(this).subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
            @Override
            public void after(List<? extends VFileEvent> events) {
                if (events.stream().anyMatch(event -> isTextureOfModel(event.getPath()))) {
                    scheduleRefresh();
                }
            }
        });
    }

    /** True for a PNG in the model's folder or in its {@code <Model>_Textures} folder. */
    private boolean isTextureOfModel(String path) {
        if (!path.toLowerCase(Locale.ROOT).endsWith(".png") || file.getParent() == null) {
            return false;
        }
        String folder = path.substring(0, Math.max(0, path.lastIndexOf('/')));
        String modelFolder = file.getParent().getPath();
        String texturesFolder = modelFolder + "/" + file.getNameWithoutExtension() + "_Textures";
        return folder.equals(modelFolder) || folder.equals(texturesFolder);
    }

    private void onMessage(String text) {
        if (text.equals("ready")) {
            ready = true;
            message.setVisible(false);
            sendModel();
        } else if (text.startsWith("error:")) {
            showMessage("Blockbench: " + text.substring("error:".length()).lines().findFirst().orElse(""));
        }
    }

    private void scheduleRefresh() {
        refresh.cancelAllRequests();
        refresh.addRequest(this::sendModel, REFRESH_DELAY_MS);
    }

    /** Sends the current document text (saved or not) to {@code bbv.showModel}. */
    private void sendModel() {
        Document document = FileDocumentManager.getInstance().getDocument(file);
        if (!ready || browser == null || document == null) {
            return;
        }
        String script = "window.bbv.showModel(" + GSON.toJson(file.getPath()) + "," + GSON.toJson(document.getText())
                + ")";
        browser.getCefBrowser().executeJavaScript(script, "", 0);
    }

    private void showMessage(String text) {
        message.setText(text);
        message.setVisible(true);
    }

    @Override
    public JComponent getComponent() {
        return panel;
    }

    @Override
    public JComponent getPreferredFocusedComponent() {
        return browser == null ? panel : browser.getComponent();
    }

    @Override
    public String getName() {
        return "Blockbench";
    }

    @Override
    public void setState(FileEditorState state) {}

    @Override
    public boolean isModified() {
        return false;
    }

    @Override
    public boolean isValid() {
        return file.isValid();
    }

    @Override
    public void addPropertyChangeListener(PropertyChangeListener listener) {}

    @Override
    public void removePropertyChangeListener(PropertyChangeListener listener) {}

    @Override
    public VirtualFile getFile() {
        return file;
    }

    @Override
    public void dispose() {}
}
```

- [ ] **Step 4 : écrire `BlockymodelEditorProvider`**

`src/main/java/dev/blockymodelviewer/BlockymodelEditorProvider.java` :

```java
package dev.blockymodelviewer;

import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorPolicy;
import com.intellij.openapi.fileEditor.FileEditorProvider;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.fileEditor.TextEditorWithPreview;
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

/** Opens {@code .blockymodel} files as their JSON side by side with the Blockbench preview. */
public final class BlockymodelEditorProvider implements FileEditorProvider, DumbAware {
    @Override
    public boolean accept(Project project, VirtualFile file) {
        return "blockymodel".equalsIgnoreCase(file.getExtension()) && file.isInLocalFileSystem();
    }

    @Override
    public FileEditor createEditor(Project project, VirtualFile file) {
        TextEditor text = (TextEditor) TextEditorProvider.getInstance().createEditor(project, file);
        return new TextEditorWithPreview(text, new BlockymodelPreview(project, file), "Blockymodel");
    }

    @Override
    public String getEditorTypeId() {
        return "blockymodel-blockbench";
    }

    @Override
    public FileEditorPolicy getPolicy() {
        return FileEditorPolicy.HIDE_DEFAULT_EDITOR;
    }
}
```

- [ ] **Step 5 : déclarer l'éditeur dans `plugin.xml`**

Dans `<extensions defaultExtensionNs="com.intellij">`, après `<fileType …/>` :

```xml
        <fileEditorProvider implementation="dev.blockymodelviewer.BlockymodelEditorProvider"/>
```

(`BlockbenchSettings` est un service léger `@Service` : il ne se déclare pas.)

- [ ] **Step 6 : compiler et vérifier**

Run : `./gradlew build verifyPlugin`
Expected : `BUILD SUCCESSFUL`, tous les tests verts, et le rapport du Plugin Verifier sans problème de compatibilité
(« Compatible »). Une classe JCEF introuvable à la compilation (`package org.cef… does not exist`) veut dire que
`bundledModule(…)` ne met pas les jars de `plugins/jcef-plugin/lib/modules/` sur le classpath : remplacer alors les deux
`bundledModule` par
`compileOnly(files("$ideaDir/plugins/jcef-plugin/lib/modules/intellij.libraries.jcef.jar", "$ideaDir/plugins/jcef-plugin/lib/modules/intellij.platform.ui.jcef.jar"))`
dans `dependencies` (le descripteur garde ses `<module>`).

- [ ] **Step 7 : commit**

```bash
git add src/main/java/dev/blockymodelviewer/CefRoutes.java src/main/java/dev/blockymodelviewer/BlockymodelPreview.java \
  src/main/java/dev/blockymodelviewer/BlockymodelEditorProvider.java src/main/java/dev/blockymodelviewer/BlockbenchSettings.java \
  src/main/resources/META-INF/plugin.xml
git commit -m "feat: split editor with the live Blockbench preview

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 6 : réglage du dossier et « Open in Blockbench »

**Files :**
- Create : `src/main/java/dev/blockymodelviewer/BlockbenchConfigurable.java`,
  `src/main/java/dev/blockymodelviewer/OpenInBlockbenchAction.java`
- Modify : `src/main/resources/META-INF/plugin.xml`

**Interfaces :**
- Consumes : `BlockbenchSettings.getInstance()`, `blockbenchDir()`, `setBlockbenchDir(String)` (tâche 5) ;
  `BlockbenchInstall.locate` et `Found.exe()` (tâche 2).

- [ ] **Step 1 : écrire `BlockbenchConfigurable`**

`src/main/java/dev/blockymodelviewer/BlockbenchConfigurable.java` :

```java
package dev.blockymodelviewer;

import com.intellij.openapi.options.Configurable;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;
import javax.swing.JComponent;
import javax.swing.JPanel;

/** Settings page (Tools | Blockymodel Viewer): the Blockbench folder, blank for the default install. */
public final class BlockbenchConfigurable implements Configurable {
    private JBTextField folder;

    @Override
    public String getDisplayName() {
        return "Blockymodel Viewer";
    }

    @Override
    public JComponent createComponent() {
        folder = new JBTextField();
        folder.getEmptyText().setText("%LOCALAPPDATA%\\Programs\\Blockbench");
        return FormBuilder.createFormBuilder()
                .addLabeledComponent("Blockbench folder:", folder)
                .addComponentFillVertically(new JPanel(), 0)
                .getPanel();
    }

    @Override
    public boolean isModified() {
        return !folder.getText().strip().equals(BlockbenchSettings.getInstance().blockbenchDir());
    }

    @Override
    public void apply() {
        BlockbenchSettings.getInstance().setBlockbenchDir(folder.getText());
    }

    @Override
    public void reset() {
        folder.setText(BlockbenchSettings.getInstance().blockbenchDir());
    }
}
```

- [ ] **Step 2 : écrire `OpenInBlockbenchAction`**

`src/main/java/dev/blockymodelviewer/OpenInBlockbenchAction.java` :

```java
package dev.blockymodelviewer;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import java.io.IOException;

/** Opens the selected {@code .blockymodel} in the Blockbench desktop app. */
public final class OpenInBlockbenchAction extends AnAction implements DumbAware {
    private static final String TITLE = "Blockymodel Viewer";

    @Override
    public ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void update(AnActionEvent event) {
        VirtualFile file = event.getData(CommonDataKeys.VIRTUAL_FILE);
        event.getPresentation().setEnabledAndVisible(
                file != null && file.isInLocalFileSystem() && "blockymodel".equalsIgnoreCase(file.getExtension()));
    }

    @Override
    public void actionPerformed(AnActionEvent event) {
        VirtualFile file = event.getData(CommonDataKeys.VIRTUAL_FILE);
        if (file == null) {
            return;
        }
        switch (BlockbenchInstall.locate(BlockbenchSettings.getInstance().blockbenchDir(), System.getenv())) {
            case BlockbenchInstall.Missing missing ->
                    Messages.showErrorDialog(event.getProject(), missing.reason(), TITLE);
            case BlockbenchInstall.Found found -> {
                try {
                    new ProcessBuilder(found.exe().toString(), file.toNioPath().toString()).start();
                } catch (IOException e) {
                    Messages.showErrorDialog(event.getProject(), "Cannot start Blockbench: " + e.getMessage(), TITLE);
                }
            }
        }
    }
}
```

- [ ] **Step 3 : déclarer la page et l'action dans `plugin.xml`**

Dans `<extensions defaultExtensionNs="com.intellij">` :

```xml
        <applicationConfigurable parentId="tools" id="dev.blockymodelviewer"
                                 instance="dev.blockymodelviewer.BlockbenchConfigurable"
                                 displayName="Blockymodel Viewer"/>
```

Après `</extensions>` :

```xml
    <actions>
        <action id="BlockymodelViewer.OpenInBlockbench" class="dev.blockymodelviewer.OpenInBlockbenchAction"
                text="Open in Blockbench" description="Open this model in the Blockbench desktop app">
            <add-to-group group-id="EditorPopupMenu" anchor="last"/>
            <add-to-group group-id="ProjectViewPopupMenu" anchor="last"/>
            <add-to-group group-id="EditorTabPopupMenu" anchor="last"/>
        </action>
    </actions>
```

- [ ] **Step 4 : compiler et vérifier**

Run : `./gradlew build verifyPlugin`
Expected : `BUILD SUCCESSFUL`, tests verts, Plugin Verifier « Compatible ».

- [ ] **Step 5 : commit**

```bash
git add src/main/java/dev/blockymodelviewer/BlockbenchConfigurable.java \
  src/main/java/dev/blockymodelviewer/OpenInBlockbenchAction.java src/main/resources/META-INF/plugin.xml
git commit -m "feat: Blockbench folder setting and Open in Blockbench action

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 7 : README, relecture et essai par l'utilisateur

**Files :**
- Create : `README.md`

- [ ] **Step 1 : écrire `README.md`**

```markdown
# Blockymodel Viewer

Plugin IntelliJ IDEA (2026.2 et suivantes) : ouvre un `.blockymodel` de Hytale à côté de son rendu par le
Blockbench installé sur la machine, avec le plugin Hytale officiel. Le rendu suit le texte en cours de frappe et
les textures `.png` du dossier du modèle.

## Prérequis

- Blockbench de bureau (`%LOCALAPPDATA%\Programs\Blockbench`, ou le dossier réglé dans Settings | Tools |
  Blockymodel Viewer) ;
- son plugin Hytale (`%APPDATA%\Blockbench\plugins\hytale_plugin.js`).

## Fonctionnement

Le bundle de Blockbench (`resources/app.asar`) est servi à un navigateur JCEF par le plugin. Une couche « faux Node »
(`src/main/resources/viewer/node-shim.js`) remplace les API d'Electron et donne accès en lecture seule aux fichiers
du projet. L'aperçu ne fait aucune requête réseau et n'écrit rien sur le disque.

Si une mise à jour de Blockbench empêche le démarrage, l'aperçu affiche l'erreur : il faut alors compléter
`node-shim.js`. `./gradlew test` le vérifie sur le Blockbench installé (Edge headless).

## Construire

    ./gradlew buildPlugin     # build/distributions/blockymodel-viewer-0.1.0.zip
    ./gradlew runIde          # IDE de test

Installer : Settings | Plugins | ⚙ | Install Plugin from Disk… puis le zip.
```

- [ ] **Step 2 : build complet**

Run : `./gradlew build verifyPlugin buildPlugin`
Expected : `BUILD SUCCESSFUL` et `build/distributions/blockymodel-viewer-0.1.0.zip`.

- [ ] **Step 3 : commit**

```bash
git add README.md
git commit -m "docs: README

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

- [ ] **Step 4 : relecture indépendante**

Lancer un agent `feature-dev:code-reviewer` sur tout le dépôt (`git log --oneline` depuis le premier commit), avec la
spec et ce plan : correction, sécurité des routes (`..`, racines), fuites (navigateur et requêtes libérés avec
l'éditeur), fils (JS → Java passe par `invokeLater`). Corriger ce qu'il trouve, puis faire relire les corrections.

- [ ] **Step 5 : essai en IDE par l'utilisateur**

Donner à l'utilisateur le zip ou `./gradlew runIde`, et cette liste :

1. Ouvrir `HyColony/plugin/src/main/resources/Common/Blocks/HyColony/Huts/Builder.blockymodel` : JSON coloré à
   gauche, banc du bâtisseur texturé à droite, sans menus ni panneaux de Blockbench.
2. Tourner, zoomer, déplacer la vue à la souris.
3. Changer une valeur de `size` dans le JSON : le rendu suit en moins d'une demi-seconde, la caméra ne bouge pas.
4. Effacer une accolade : le rendu reste celui d'avant, sans message ; la remettre : il se met à jour.
5. Relancer `tools/huts` (bake) ou modifier `Builder.png` : la texture se recharge.
6. Ouvrir un `.blockymodel` hors du projet (File | Open) : le rendu et sa texture s'affichent.
7. Clic droit sur le fichier, « Open in Blockbench » : Blockbench s'ouvre sur le modèle.
8. Settings | Tools | Blockymodel Viewer : mettre un dossier faux, rouvrir le fichier : message « Blockbench not
   found… » à la place du rendu ; vider le champ : le rendu revient.
9. Un pot de HyVanilla (`vanilla/plugin/src/main/resources/Common/Blocks/HyVanilla/Flower_Pot/…`) et `TownHall.blockymodel`.

Si le point 1 montre un JSON non coloré (l'extension n'a pas rejoint le type JSON), remplacer dans `plugin.xml` la
déclaration courte par
`<fileType name="JSON" implementationClass="com.intellij.json.JsonFileType" fieldName="INSTANCE" language="JSON" extensions="blockymodel"/>`.
