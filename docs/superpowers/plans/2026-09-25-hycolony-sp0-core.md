# HyColony SP0 — Partie A : Core (Java pur) — Plan d'implémentation

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal :** construire le module `core` de HyColony : le moteur d'IA, la colonie, les permissions, le territoire, le squelette des bâtiments, les citoyens et la persistance. Le tout en Java pur, entièrement testé sans serveur Hytale.

**Architecture :** le build Gradle a deux modules, `core` (sans aucune dépendance Hytale) et `plugin` (l'adaptateur, traité dans la partie B). Le core parle au monde par des *ports* (interfaces), qui ont des fakes en test. Il est découpé en packages par fonctionnalité (`kernel`, `colony`, `building`, `citizen`), avec des règles de dépendance vérifiées par ArchUnit.

**Tech Stack :**
- Java 25 (toolchain), avec le core compilé en `--release 21` ;
- Gradle 9.5 ;
- Gson (`compileOnly`, fourni par le serveur Hytale) ;
- JUnit 5.11, ArchUnit 1.3.

**Spec :** `docs/superpowers/specs/2026-09-25-hycolony-sp0-fondations-design.md`
**Références :** `docs/research/minecolonies-analysis.md`, `docs/research/hytale-api-spike.md`
**Suite :** `docs/superpowers/plans/2026-09-25-hycolony-sp0-plugin.md` (partie B)

## Global Constraints

- Package racine : `dev.hycolony.core` (core) et `dev.hycolony.plugin` (plugin).
- **Aucun import `com.hypixel`** dans `core/`.
- Le core ne dépend que du JDK et de Gson (`compileOnly`). Aucune autre dépendance d'exécution.
- Le core est **mono-thread par monde** : pas de synchronisation, sauf le compteur d'offset global des transitions.
- Logs : `System.getLogger(Class.getName())` du JDK. Pas de SLF4J.
- Tick du core : **20 ticks/s**. Les constantes de MineColonies sont reprises **en ticks, sans conversion**.
- Cellule de claim : **16×16 blocs**.
- Valeurs par défaut de la config : `initialCitizenAmount=4`, `maxCitizenPerColony=250`, `initialColonySize=4`, `minColonyDistance=8`, `maxColonySize=20`, `enableColonyProtection=true`, `autosaveIntervalMinutes=5`.
- Sauvegarde : `schemaVersion` courant = **1**.
- Commits : messages en anglais au format conventionnel (`feat:`, `test:`, `build:`), se terminant par `Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>`.
- Commande de test : `./gradlew :core:test` (sous Windows : `./gradlew.bat :core:test`).

## Review Focus

1. **Redémarrage pendant qu'un citoyen existe déjà dans le monde** : le corps rechargé est relié, jamais dupliqué. Testé en Task 9 (`bodyLoadedTwiceKeepsFirstAndDespawnsSecond`).
2. **Fichier de sauvegarde tronqué ou corrompu** : repli sur le `.bak`, sinon mise en quarantaine, sans exception qui remonte. Testé en Task 11 (`corruptMainFallsBackToBak`, `bothCorruptAreQuarantined`).
3. **Sauvegarde écrite par une version future du mod** : elle n'est jamais chargée ni écrasée. Testé en Task 11 (`newerSchemaIsSkippedAndNeverOverwritten`).
4. **Nom de colonie vide, fait d'espaces ou trop long** : refusé avec un message. Testé en Task 10 (`rejectsBlankOrTooLongName`).
5. **Joueur qui pose un hôtel de ville puis se déconnecte avant de confirmer** : la fondation en attente est annulée. Testé en Task 10 (`playerLeavingCancelsPendingFoundation`).

---

## Structure des fichiers

```
settings.gradle.kts                     modules :core et :plugin
build.gradle.kts                        config commune des sous-projets
gradle.properties                       identité du mod, versions épinglées
LICENSE                                 GPL-3.0
core/build.gradle.kts
core/src/main/java/dev/hycolony/core/
  kernel/BlockPos.java, Vec3.java, WorldKey.java
  kernel/event/EventBus.java
  kernel/config/ColonyConfig.java
  kernel/ai/IState.java, IStateEventType.java, AIBlockingEventType.java, IStateSupplier.java,
           TickingTransition.java, AITarget.java, AIEventTarget.java, AIOneTimeEventTarget.java,
           TickRateStateMachine.java
  kernel/port/GameClock.java, BodyId.java, NavStatus.java, CitizenBodies.java, WorldQuery.java,
             Notifier.java, Msg.java, PlayerDirectory.java
  kernel/persist/ColonyStorage.java, FileColonyStorage.java, Migration.java, MigrationChain.java,
                SchemaTooNewException.java
  colony/Action.java, Rank.java, Permissions.java
  colony/ClaimCell.java, TerritoryIndex.java
  colony/ColonyState.java, Colony.java, ColonyContext.java, ColonyManager.java, HutPlacement.java
  colony/EventLog.java, ColonyEvents.java
  colony/ColonySerializer.java
  colony/ui/UiPort.java, FoundColonyView.java, TownHallView.java, CitizenRow.java
  building/BuildingModule.java, PersistentModule.java, TickingModule.java, BuildingEventsModule.java,
           ModuleProducer.java, BuildingType.java, BuildingRegistry.java, BuildingTypes.java,
           Building.java, BuildingManager.java
  citizen/Skill.java, SkillData.java, Skills.java, Experience.java, Gender.java,
          CitizenNames.java, CitizenData.java, CitizenManager.java, CitizenAI.java, CitizenState.java,
          CitizenSpawned.java
core/src/main/resources/dev/hycolony/core/citizen/names/default.json
core/src/test/java/dev/hycolony/core/...          un test par unité + testing/ (fakes)
plugin/build.gradle.kts
plugin/src/main/java/dev/hycolony/plugin/HyColonyPlugin.java      (squelette, étoffé en partie B)
plugin/src/main/resources/manifest.json
```

---

### Task 1 : restructurer le build (core + plugin), licence, garde-fou ArchUnit

**Files :**
- Modify : `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`
- Create : `core/build.gradle.kts`, `plugin/build.gradle.kts`, `LICENSE`, `core/src/test/java/dev/hycolony/core/ArchitectureTest.java`, `core/src/main/java/dev/hycolony/core/kernel/package-info.java`, `plugin/src/main/java/dev/hycolony/plugin/HyColonyPlugin.java`
- Move : `src/main/resources/manifest.json` → `plugin/src/main/resources/manifest.json`
- Delete : `src/main/java/dev/hycolony/**` (les classes d'exemple)

**Interfaces :**
- Produces : les modules Gradle `:core` et `:plugin` ; la classe `dev.hycolony.plugin.HyColonyPlugin extends JavaPlugin` avec le constructeur `(JavaPluginInit)`.

- [ ] **Step 1 : supprimer les exemples et déplacer le manifeste**

```bash
git rm -r -q src/main/java
mkdir -p plugin/src/main/resources
git mv src/main/resources/manifest.json plugin/src/main/resources/manifest.json
```

- [ ] **Step 2 : écrire `settings.gradle.kts`**

```kotlin
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven {
            name = "AzureDoom Maven"
            url = uri("https://maven.azuredoom.com/mods")
        }
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "HyColony"
include(":core", ":plugin")
```

- [ ] **Step 3 : écrire le `build.gradle.kts` racine**

```kotlin
subprojects {
    apply(plugin = "java")

    group = rootProject.property("group").toString()
    version = rootProject.property("version").toString()

    repositories { mavenCentral() }

    extensions.configure<JavaPluginExtension> {
        toolchain.languageVersion.set(JavaLanguageVersion.of(rootProject.property("java_version").toString().toInt()))
    }

    tasks.withType<JavaCompile>().configureEach { options.encoding = "UTF-8" }
    tasks.withType<Javadoc>().configureEach {
        (options as org.gradle.external.javadoc.StandardJavadocDocletOptions).addStringOption("Xdoclint:-missing", "-quiet")
    }
}
```

- [ ] **Step 4 : écrire `core/build.gradle.kts`**

```kotlin
plugins { `java-library` }

val gsonVersion = "2.11.0"

dependencies {
    // Fourni à l'exécution par le serveur Hytale.
    compileOnly("com.google.code.gson:gson:$gsonVersion")

    testImplementation("com.google.code.gson:gson:$gsonVersion")
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("com.tngtech.archunit:archunit-junit5:1.3.0")
}

// Bytecode Java 21 : ArchUnit (ASM) ne lit pas encore de façon fiable le format de classe de Java 25.
// Le plugin (Java 25) charge sans problème des classes Java 21.
tasks.withType<JavaCompile>().configureEach { options.release.set(21) }

tasks.test { useJUnitPlatform() }
```

- [ ] **Step 5 : écrire `plugin/build.gradle.kts`**

```kotlin
plugins {
    java
    id("com.azuredoom.hytale-tools") version "1.+"
}

// Les classes du core sont copiées dans le jar du plugin : un seul jar à déployer.
val bundled: Configuration by configurations.creating

dependencies {
    implementation(project(":core"))
    bundled(project(":core"))
    compileOnly("com.google.code.gson:gson:2.11.0")
}

hytaleTools {
    javaVersion = property("java_version").toString().toInt()
    hytaleVersion = property("hytale_version").toString()
    manifestServerVersion = property("manifestServerVersion").toString()
    manifestGroup = property("manifest_group").toString()
    modId = property("mod_id").toString()
    modDescription = property("mod_description").toString()
    modUrl = property("mod_url").toString()
    mainClass = property("main_class").toString()
    modCredits = property("mod_author").toString()
    manifestDependencies = property("manifest_dependencies").toString()
    manifestOptionalDependencies = property("manifest_opt_dependencies").toString()
    curseforgeId = property("curseforgeID").toString()
    disabledByDefault = property("disabled_by_default").toString().toBoolean()
    includesPack = property("includes_pack").toString().toBoolean()
    patchline = property("patchline").toString()
    injectServerJavadocsIntoSources = property("injectServerJavadocsIntoSources").toString().toBoolean()
    generateAssetsBinary = property("generateAssetsBinary").toString().toBoolean()
}

tasks.named<Jar>("jar") {
    archiveBaseName.set(property("mod_name").toString())
    archiveVersion.set(property("version").toString())
    dependsOn(bundled)
    from({ bundled.filter { it.name.endsWith(".jar") }.map { zipTree(it) } })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
```

- [ ] **Step 6 : mettre à jour `gradle.properties`**

Remplacer les valeurs suivantes, sans toucher aux autres lignes :

```properties
hytale_version = 0.6.8
main_class = dev.hycolony.plugin.HyColonyPlugin
mod_author = Ctuto
mod_id = hycolony
mod_license = GPL-3.0
mod_description = Colony management inspired by MineColonies: found a colony, citizens, buildings.
mod_url = https://github.com/ldtteam/minecolonies
version = 0.1.0
server_version = 0.6.8
manifestServerVersion = >=0.6.8 <0.7.0
manifest_dependencies = Hytale:AssetModule=*,Hytale:NPC=*
```

Supprimer entièrement la ligne `hytaleHomeOverride = ...` et son commentaire.

- [ ] **Step 7 : écrire le squelette du plugin**

`plugin/src/main/java/dev/hycolony/plugin/HyColonyPlugin.java` :

```java
package dev.hycolony.plugin;

import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import java.util.logging.Level;
import javax.annotation.Nonnull;

public final class HyColonyPlugin extends JavaPlugin {

    public HyColonyPlugin(@Nonnull JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        getLogger().at(Level.INFO).log("HyColony setup");
    }
}
```

- [ ] **Step 8 : ajouter la licence**

```bash
curl -sSfL https://www.gnu.org/licenses/gpl-3.0.txt -o LICENSE
head -3 LICENSE
```

Attendu : la première ligne contient `GNU GENERAL PUBLIC LICENSE`.

- [ ] **Step 9 : écrire le test d'architecture qui échoue**

`core/src/test/java/dev/hycolony/core/ArchitectureTest.java` :

```java
package dev.hycolony.core;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "dev.hycolony.core", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule coreNeverTouchesHytale = noClasses()
            .should().dependOnClassesThat().resideInAPackage("com.hypixel..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule kernelDependsOnNothingElse = noClasses()
            .that().resideInAPackage("dev.hycolony.core.kernel..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "dev.hycolony.core.colony..", "dev.hycolony.core.building..", "dev.hycolony.core.citizen..")
            .allowEmptyShould(true);

    /** Futurs packages de fonctionnalités : ils ne communiquent que via le kernel (spec § 2.2). */
    @ArchTest
    static final ArchRule featurePackagesAreIsolated = noClasses()
            .that().resideInAnyPackage("dev.hycolony.core.request..", "dev.hycolony.core.construction..",
                    "dev.hycolony.core.job..", "dev.hycolony.core.life..", "dev.hycolony.core.defense..")
            .should().dependOnClassesThat().resideInAnyPackage("dev.hycolony.core.request..",
                    "dev.hycolony.core.construction..", "dev.hycolony.core.job..", "dev.hycolony.core.life..",
                    "dev.hycolony.core.defense..")
            .allowEmptyShould(true);
}
```

Et `core/src/main/java/dev/hycolony/core/kernel/package-info.java` :

```java
/** Shared kernel: ids, positions, AI engine, events, ports, persistence. Depends on nothing else in core. */
package dev.hycolony.core.kernel;
```

La troisième règle se compare elle-même à ses propres packages. C'est voulu et correct pour l'instant, puisque ces packages n'existent pas encore. Les specs des sous-projets 1 à 5 la remplaceront par des règles deux à deux.

- [ ] **Step 10 : compiler et tester**

Run : `./gradlew build`
Attendu : `BUILD SUCCESSFUL`, avec 3 tests ArchUnit passés dans `:core:test` et `plugin/build/libs/HyColony-0.1.0.jar` créé.

En cas d'échec `Unsupported class file major version`, c'est que `options.release.set(21)` n'est pas appliqué au core : vérifier le Step 4.

- [ ] **Step 11 : commit**

```bash
git add -A
git commit -m "build: split into core and plugin modules, GPL-3.0, architecture guard

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 2 : types de base du kernel, bus d'événements, config

**Files :**
- Create : `core/src/main/java/dev/hycolony/core/kernel/{BlockPos,Vec3,WorldKey}.java`, `kernel/event/EventBus.java`, `kernel/config/ColonyConfig.java`
- Test : `core/src/test/java/dev/hycolony/core/kernel/BlockPosTest.java`, `kernel/event/EventBusTest.java`

**Interfaces :**
- Produces :
  - `record BlockPos(int x, int y, int z)` avec `BlockPos offset(int dx, int dy, int dz)` et `long distSq(BlockPos o)`.
  - `record Vec3(double x, double y, double z)` avec `BlockPos toBlockPos()` et `static Vec3 center(BlockPos p)`.
  - `record WorldKey(String name)`.
  - `EventBus` avec `<E> void subscribe(Class<E>, Consumer<? super E>)` et `void post(Object)`.
  - `record ColonyConfig(int initialCitizenAmount, int maxCitizenPerColony, int initialColonySize, int minColonyDistance, int maxColonySize, boolean enableColonyProtection, int autosaveIntervalMinutes)` avec `static ColonyConfig defaults()`.

- [ ] **Step 1 : écrire les tests qui échouent**

`core/src/test/java/dev/hycolony/core/kernel/BlockPosTest.java` :

```java
package dev.hycolony.core.kernel;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class BlockPosTest {
    @Test
    void offsetAndDistance() {
        BlockPos p = new BlockPos(1, 2, 3);
        assertEquals(new BlockPos(2, 2, 1), p.offset(1, 0, -2));
        assertEquals(1 + 0 + 4, p.distSq(new BlockPos(2, 2, 1)));
    }

    @Test
    void vecFloorsToBlockPos() {
        assertEquals(new BlockPos(-1, 0, 2), new Vec3(-0.5, 0.9, 2.1).toBlockPos());
        assertEquals(new Vec3(1.5, 2, 3.5), Vec3.center(new BlockPos(1, 2, 3)));
    }
}
```

`core/src/test/java/dev/hycolony/core/kernel/event/EventBusTest.java` :

```java
package dev.hycolony.core.kernel.event;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class EventBusTest {
    record Ping(int n) {}
    record Other() {}

    @Test
    void deliversOnlyMatchingTypeInSubscriptionOrder() {
        EventBus bus = new EventBus();
        List<String> seen = new ArrayList<>();
        bus.subscribe(Ping.class, p -> seen.add("a" + p.n()));
        bus.subscribe(Ping.class, p -> seen.add("b" + p.n()));
        bus.subscribe(Other.class, o -> seen.add("other"));
        bus.post(new Ping(1));
        assertEquals(List.of("a1", "b1"), seen);
    }

    @Test
    void failingListenerDoesNotStopOthers() {
        EventBus bus = new EventBus();
        List<String> seen = new ArrayList<>();
        bus.subscribe(Ping.class, p -> { throw new IllegalStateException("boom"); });
        bus.subscribe(Ping.class, p -> seen.add("ok"));
        bus.post(new Ping(1));
        assertEquals(List.of("ok"), seen);
    }
}
```

- [ ] **Step 2 : lancer les tests pour vérifier qu'ils échouent**

Run : `./gradlew :core:test`
Attendu : erreur de compilation, car `BlockPos`, `Vec3` et `EventBus` n'existent pas.

- [ ] **Step 3 : implémenter**

`kernel/BlockPos.java` :

```java
package dev.hycolony.core.kernel;

/** Integer block position, independent of any game engine type. */
public record BlockPos(int x, int y, int z) {
    public BlockPos offset(int dx, int dy, int dz) {
        return new BlockPos(x + dx, y + dy, z + dz);
    }

    public long distSq(BlockPos o) {
        long dx = x - o.x, dy = y - o.y, dz = z - o.z;
        return dx * dx + dy * dy + dz * dz;
    }
}
```

`kernel/Vec3.java` :

```java
package dev.hycolony.core.kernel;

public record Vec3(double x, double y, double z) {
    public BlockPos toBlockPos() {
        return new BlockPos((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
    }

    public static Vec3 center(BlockPos p) {
        return new Vec3(p.x() + 0.5, p.y(), p.z() + 0.5);
    }
}
```

`kernel/WorldKey.java` :

```java
package dev.hycolony.core.kernel;

import java.util.Objects;

/** Identifies a game world by its name. */
public record WorldKey(String name) {
    public WorldKey {
        Objects.requireNonNull(name, "name");
    }
}
```

`kernel/event/EventBus.java` :

```java
package dev.hycolony.core.kernel.event;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Synchronous, exact-type event bus. One per world. A failing listener never blocks the others. */
public final class EventBus {
    private static final System.Logger LOG = System.getLogger(EventBus.class.getName());

    private final Map<Class<?>, List<Consumer<Object>>> listeners = new HashMap<>();

    public <E> void subscribe(Class<E> type, Consumer<? super E> listener) {
        listeners.computeIfAbsent(type, k -> new ArrayList<>()).add(e -> listener.accept(type.cast(e)));
    }

    public void post(Object event) {
        for (Consumer<Object> listener : listeners.getOrDefault(event.getClass(), List.of())) {
            try {
                listener.accept(event);
            } catch (RuntimeException e) {
                LOG.log(System.Logger.Level.WARNING, "Event listener failed for " + event, e);
            }
        }
    }
}
```

`kernel/config/ColonyConfig.java` :

```java
package dev.hycolony.core.kernel.config;

/** Server configuration relevant to colonies. Defaults mirror MineColonies. */
public record ColonyConfig(
        int initialCitizenAmount,
        int maxCitizenPerColony,
        int initialColonySize,
        int minColonyDistance,
        int maxColonySize,
        boolean enableColonyProtection,
        int autosaveIntervalMinutes) {

    public static ColonyConfig defaults() {
        return new ColonyConfig(4, 250, 4, 8, 20, true, 5);
    }
}
```

- [ ] **Step 4 : lancer les tests pour vérifier qu'ils passent**

Run : `./gradlew :core:test`
Attendu : PASS.

- [ ] **Step 5 : commit**

```bash
git add core
git commit -m "feat(core): kernel value types, event bus, colony config

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 3 : moteur d'IA, portage de `TickRateStateMachine`

Portage fidèle de `BasicStateMachine`, `TickRateStateMachine` et `TickingTransition` de MineColonies (`api/entity/ai/statemachine/`), en fusionnant les deux classes de machine. `addTransitionGroup` et `slownessFactor` sont omis : aucun appelant dans le sous-projet 0.

**Files :**
- Create : `core/src/main/java/dev/hycolony/core/kernel/ai/{IState,IStateEventType,AIBlockingEventType,IStateSupplier,TickingTransition,AITarget,AIEventTarget,AIOneTimeEventTarget,TickRateStateMachine}.java`
- Test : `core/src/test/java/dev/hycolony/core/kernel/ai/TickRateStateMachineTest.java`

**Interfaces :**
- Produces :
  - `interface IState` et `interface IStateEventType` ;
  - `enum AIBlockingEventType implements IStateEventType { AI_BLOCKING, STATE_BLOCKING, EVENT }` ;
  - `@FunctionalInterface interface IStateSupplier<S extends IState> { S get(); }` ;
  - `class TickingTransition<S>` avec `getState()`, `getEventType()`, `isOneTime()`, `getTickRate()`, et en package-private `static void resetOffsetVariant()` ;
  - `AITarget<S>(S state, BooleanSupplier cond, IStateSupplier<S> action, int rate)`, `AITarget<S>(S state, IStateSupplier<S> action, int rate)`, `AITarget<S>(S state, S next, int rate)` ;
  - `AIEventTarget<S>(AIBlockingEventType type, BooleanSupplier cond, IStateSupplier<S> action, int rate)`, `AIEventTarget<S>(AIBlockingEventType type, IStateSupplier<S> action, int rate)` ;
  - `AIOneTimeEventTarget<S>(BooleanSupplier cond, IStateSupplier<S> action)`, `AIOneTimeEventTarget<S>(IStateSupplier<S> action)` ;
  - `TickRateStateMachine<S>(S initial, Consumer<RuntimeException> onException)` et `(S initial, Consumer<RuntimeException> onException, int tickRate)`, avec `addTransition`, `removeTransition`, `tick()`, `getState()`, `reset()`, `setCurrentDelay(int)`, `getTickRate()`, `setTickRate(int)` et `List<String> history()`.

- [ ] **Step 1 : écrire les tests qui échouent**

`core/src/test/java/dev/hycolony/core/kernel/ai/TickRateStateMachineTest.java` :

```java
package dev.hycolony.core.kernel.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TickRateStateMachineTest {
    enum S implements IState { A, B, C, EMPTY }

    private final List<RuntimeException> errors = new ArrayList<>();
    private final List<String> log = new ArrayList<>();

    @BeforeEach
    void resetOffsets() {
        TickingTransition.resetOffsetVariant();
    }

    private TickRateStateMachine<S> machine() {
        return new TickRateStateMachine<>(S.A, errors::add);
    }

    private IStateSupplier<S> record(String name, S next) {
        return () -> { log.add(name); return next; };
    }

    @Test
    void evaluationOrderIsAiBlockingEventStateBlockingThenState() {
        var sm = machine();
        sm.addTransition(new AITarget<>(S.A, record("state", null), 1));
        sm.addTransition(new AIEventTarget<>(AIBlockingEventType.STATE_BLOCKING, record("stateBlocking", null), 1));
        sm.addTransition(new AIEventTarget<>(AIBlockingEventType.EVENT, record("event", null), 1));
        sm.addTransition(new AIEventTarget<>(AIBlockingEventType.AI_BLOCKING, record("aiBlocking", null), 1));
        sm.tick();
        assertEquals(List.of("aiBlocking", "event", "stateBlocking", "state"), log);
    }

    @Test
    void firstTransitionReturningAStateEndsTheTick() {
        var sm = machine();
        sm.addTransition(new AIEventTarget<>(AIBlockingEventType.AI_BLOCKING, record("first", S.A), 1));
        sm.addTransition(new AITarget<>(S.A, record("never", S.B), 1));
        sm.tick();
        assertEquals(List.of("first"), log);
        assertEquals(S.A, sm.getState());
    }

    @Test
    void transitionSwitchesToTargetStateTransitions() {
        var sm = machine();
        sm.addTransition(new AITarget<>(S.A, S.B, 1));
        sm.addTransition(new AITarget<>(S.B, record("inB", null), 1));
        sm.tick();
        assertEquals(S.B, sm.getState());
        sm.tick();
        assertEquals(List.of("inB"), log);
    }

    @Test
    void tickRateLimitsHowOftenATransitionRuns() {
        var sm = machine();
        sm.addTransition(new AITarget<>(S.A, record("x", null), 3));
        for (int i = 0; i < 7; i++) {
            sm.tick();
        }
        assertEquals(3, log.size()); // ticks 1, 4, 7
    }

    @Test
    void tickRateIsClampedToAtLeastOne() {
        var sm = machine();
        sm.addTransition(new AITarget<>(S.A, record("x", null), 0));
        sm.tick();
        sm.tick();
        assertEquals(2, log.size());
    }

    @Test
    void setCurrentDelayPostponesTheExecutedTransition() {
        var sm = machine();
        List<Integer> runs = new ArrayList<>();
        int[] tick = {0};
        sm.addTransition(new AITarget<>(S.A, () -> { runs.add(tick[0]); sm.setCurrentDelay(5); return null; }, 1));
        for (tick[0] = 1; tick[0] <= 7; tick[0]++) {
            sm.tick();
        }
        assertEquals(List.of(1, 6), runs);
    }

    @Test
    void missingTransitionsForNewStateReportsAndResets() {
        var sm = machine();
        sm.addTransition(new AITarget<>(S.A, S.EMPTY, 1));
        sm.tick();
        assertEquals(1, errors.size());
        assertEquals(S.A, sm.getState());
    }

    @Test
    void oneTimeEventIsRemovedAfterFiring() {
        var sm = machine();
        sm.addTransition(new AIOneTimeEventTarget<>(record("once", S.A)));
        sm.addTransition(new AITarget<>(S.A, record("state", null), 1));
        sm.tick();
        sm.tick();
        assertEquals(List.of("once", "state"), log);
    }

    @Test
    void exceptionInConditionIsReportedAndEvaluationContinues() {
        var sm = machine();
        sm.addTransition(new AITarget<>(S.A, () -> { throw new IllegalStateException("cond"); }, record("never", null), 1));
        sm.addTransition(new AITarget<>(S.A, record("next", null), 1));
        sm.tick();
        assertEquals(1, errors.size());
        assertEquals(List.of("next"), log);
    }

    @Test
    void exceptionInActionIsReported() {
        var sm = machine();
        sm.addTransition(new AITarget<>(S.A, () -> { throw new IllegalStateException("act"); }, 1));
        sm.tick();
        assertEquals(1, errors.size());
        assertEquals(S.A, sm.getState());
    }

    @Test
    void historyKeepsLastTransitions() {
        var sm = machine();
        sm.addTransition(new AITarget<>(S.A, S.B, 1));
        sm.addTransition(new AITarget<>(S.B, S.A, 1));
        for (int i = 0; i < 30; i++) {
            sm.tick();
        }
        assertEquals(20, sm.history().size());
        assertTrue(sm.history().getLast().endsWith("->A"));
    }
}
```

- [ ] **Step 2 : lancer les tests pour vérifier qu'ils échouent**

Run : `./gradlew :core:test --tests "*TickRateStateMachineTest"`
Attendu : erreur de compilation (classes absentes).

- [ ] **Step 3 : implémenter**

`kernel/ai/IState.java` :

```java
package dev.hycolony.core.kernel.ai;

/** Marker for AI states. */
public interface IState {}
```

`kernel/ai/IStateEventType.java` :

```java
package dev.hycolony.core.kernel.ai;

public interface IStateEventType {}
```

`kernel/ai/AIBlockingEventType.java` :

```java
package dev.hycolony.core.kernel.ai;

/** Priority groups evaluated before state transitions, in the order AI_BLOCKING, EVENT, STATE_BLOCKING. */
public enum AIBlockingEventType implements IStateEventType {
    AI_BLOCKING,
    STATE_BLOCKING,
    EVENT
}
```

`kernel/ai/IStateSupplier.java` :

```java
package dev.hycolony.core.kernel.ai;

/** Transition action. Returns the next state, or null to not transition. */
@FunctionalInterface
public interface IStateSupplier<S extends IState> {
    S get();
}
```

`kernel/ai/TickingTransition.java` :

```java
package dev.hycolony.core.kernel.ai;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

/** A transition evaluated every {@code tickRate} machine ticks. Port of MineColonies' TickingTransition. */
public class TickingTransition<S extends IState> {
    public static final int MAX_AI_TICKRATE = 20 * 60 * 10;
    public static final int MAX_TICKRATE_VARIANT = 50;

    /** Spreads transitions across ticks. Shared by all worlds, hence atomic. */
    private static final AtomicInteger OFFSET_VARIANT = new AtomicInteger();

    private final S state;
    private final BooleanSupplier condition;
    private final IStateSupplier<S> nextState;
    private final int tickRate;
    private int ticksToUpdate;

    protected TickingTransition(S state, BooleanSupplier condition, IStateSupplier<S> nextState, int tickRate) {
        this.state = state;
        this.condition = condition;
        this.nextState = nextState;
        this.tickRate = Math.max(1, Math.min(tickRate, MAX_AI_TICKRATE));
        int variant = OFFSET_VARIANT.getAndUpdate(v -> v + 1 >= MAX_TICKRATE_VARIANT ? 0 : v + 1);
        this.ticksToUpdate = variant % this.tickRate;
    }

    /** Test hook: makes offsets deterministic. */
    static void resetOffsetVariant() {
        OFFSET_VARIANT.set(0);
    }

    public S getState() {
        return state;
    }

    /** Null for plain state transitions. */
    public IStateEventType getEventType() {
        return null;
    }

    public boolean isOneTime() {
        return false;
    }

    public int getTickRate() {
        return tickRate;
    }

    boolean checkCondition() {
        return condition.getAsBoolean();
    }

    S getNextState() {
        return nextState.get();
    }

    int countdownTicksToUpdate(int reduction) {
        return ticksToUpdate -= reduction;
    }

    void setTicksToUpdate(int ticksToUpdate) {
        this.ticksToUpdate = ticksToUpdate;
    }
}
```

`kernel/ai/AITarget.java` :

```java
package dev.hycolony.core.kernel.ai;

import java.util.function.BooleanSupplier;

public class AITarget<S extends IState> extends TickingTransition<S> {
    public AITarget(S state, BooleanSupplier condition, IStateSupplier<S> action, int tickRate) {
        super(state, condition, action, tickRate);
    }

    public AITarget(S state, IStateSupplier<S> action, int tickRate) {
        this(state, () -> true, action, tickRate);
    }

    public AITarget(S state, S next, int tickRate) {
        this(state, () -> true, () -> next, tickRate);
    }
}
```

`kernel/ai/AIEventTarget.java` :

```java
package dev.hycolony.core.kernel.ai;

import java.util.function.BooleanSupplier;

/** Transition not bound to a state, evaluated in its priority group every tick. */
public class AIEventTarget<S extends IState> extends TickingTransition<S> {
    private final AIBlockingEventType eventType;

    public AIEventTarget(AIBlockingEventType eventType, BooleanSupplier condition, IStateSupplier<S> action, int tickRate) {
        super(null, condition, action, tickRate);
        this.eventType = eventType;
    }

    public AIEventTarget(AIBlockingEventType eventType, IStateSupplier<S> action, int tickRate) {
        this(eventType, () -> true, action, tickRate);
    }

    @Override
    public IStateEventType getEventType() {
        return eventType;
    }
}
```

`kernel/ai/AIOneTimeEventTarget.java` :

```java
package dev.hycolony.core.kernel.ai;

import java.util.function.BooleanSupplier;

/** EVENT transition removed after it first returns a state. */
public class AIOneTimeEventTarget<S extends IState> extends AIEventTarget<S> {
    public AIOneTimeEventTarget(BooleanSupplier condition, IStateSupplier<S> action) {
        super(AIBlockingEventType.EVENT, condition, action, 1);
    }

    public AIOneTimeEventTarget(IStateSupplier<S> action) {
        this(() -> true, action);
    }

    @Override
    public boolean isOneTime() {
        return true;
    }
}
```

`kernel/ai/TickRateStateMachine.java` :

```java
package dev.hycolony.core.kernel.ai;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Port of MineColonies' BasicStateMachine + TickRateStateMachine.
 * Each tick evaluates AI_BLOCKING, EVENT, STATE_BLOCKING, then the current state's transitions;
 * the first transition that returns a state ends the tick.
 */
public class TickRateStateMachine<S extends IState> {
    private static final int HISTORY_SIZE = 20;

    private final Map<S, List<TickingTransition<S>>> transitionMap = new HashMap<>();
    private final List<TickingTransition<S>> aiBlocking = new ArrayList<>();
    private final List<TickingTransition<S>> stateBlocking = new ArrayList<>();
    private final List<TickingTransition<S>> events = new ArrayList<>();
    private final Deque<String> history = new ArrayDeque<>(HISTORY_SIZE);
    private final S initState;
    private final Consumer<RuntimeException> exceptionHandler;

    private List<TickingTransition<S>> currentStateTransitions;
    private S state;
    private int tickRate = 1;
    private TickingTransition<S> executedTransition;

    public TickRateStateMachine(S initialState, Consumer<RuntimeException> exceptionHandler) {
        this.initState = initialState;
        this.state = initialState;
        this.exceptionHandler = exceptionHandler;
        this.currentStateTransitions = new ArrayList<>();
        transitionMap.put(initialState, currentStateTransitions);
    }

    public TickRateStateMachine(S initialState, Consumer<RuntimeException> exceptionHandler, int tickRate) {
        this(initialState, exceptionHandler);
        this.tickRate = tickRate;
    }

    public void addTransition(TickingTransition<S> transition) {
        if (transition.getState() != null) {
            transitionMap.computeIfAbsent(transition.getState(), k -> new ArrayList<>()).add(transition);
        }
        if (transition.getEventType() != null) {
            eventList(transition.getEventType()).add(transition);
        }
    }

    public void removeTransition(TickingTransition<S> transition) {
        if (transition.getEventType() != null) {
            eventList(transition.getEventType()).removeIf(t -> t == transition);
        } else {
            transitionMap.get(transition.getState()).removeIf(t -> t == transition);
        }
    }

    private List<TickingTransition<S>> eventList(IStateEventType type) {
        if (!(type instanceof AIBlockingEventType t)) {
            throw new IllegalArgumentException("Unsupported event type " + type);
        }
        return switch (t) {
            case AI_BLOCKING -> aiBlocking;
            case STATE_BLOCKING -> stateBlocking;
            case EVENT -> events;
        };
    }

    public void tick() {
        if (runGroup(aiBlocking) || runGroup(events) || runGroup(stateBlocking)) {
            return;
        }
        runGroup(currentStateTransitions);
    }

    private boolean runGroup(List<TickingTransition<S>> group) {
        for (int i = 0, size = group.size(); i < size && i < group.size(); i++) {
            if (checkTransition(group.get(i))) {
                return true;
            }
        }
        return false;
    }

    private boolean checkTransition(TickingTransition<S> transition) {
        if (transition.countdownTicksToUpdate(tickRate) > 0) {
            return false;
        }
        transition.setTicksToUpdate(transition.getTickRate());
        executedTransition = transition;
        try {
            if (!transition.checkCondition()) {
                return false;
            }
        } catch (RuntimeException e) {
            exceptionHandler.accept(e);
            return false;
        }
        return transitionToNext(transition);
    }

    private boolean transitionToNext(TickingTransition<S> transition) {
        final S newState;
        try {
            newState = transition.getNextState();
        } catch (RuntimeException e) {
            exceptionHandler.accept(e);
            return false;
        }
        if (newState == null) {
            return false;
        }
        if (transition.isOneTime()) {
            removeTransition(transition);
        }
        if (newState != state) {
            currentStateTransitions = transitionMap.get(newState);
            if (currentStateTransitions == null || currentStateTransitions.isEmpty()) {
                exceptionHandler.accept(new IllegalStateException("Missing AI transition for state: " + newState));
                reset();
                return true;
            }
            if (history.size() == HISTORY_SIZE) {
                history.removeFirst();
            }
            history.addLast(state + "->" + newState);
        }
        state = newState;
        return true;
    }

    public S getState() {
        return state;
    }

    public void reset() {
        state = initState;
        currentStateTransitions = transitionMap.get(initState);
    }

    /** Overrides the countdown of the transition currently executing. */
    public void setCurrentDelay(int ticksToNext) {
        executedTransition.setTicksToUpdate(ticksToNext);
    }

    public int getTickRate() {
        return tickRate;
    }

    public void setTickRate(int tickRate) {
        this.tickRate = tickRate;
    }

    public List<String> history() {
        return List.copyOf(history);
    }
}
```

`setCurrentDelay` : dans le test, une action lancée au tick 1 avec un délai de 5 se relance au tick 6. Le compte à rebours passe à 5, puis est décrémenté de 1 aux ticks 2 à 6 et atteint 0 au tick 6. C'est exactement le comportement de MineColonies.

- [ ] **Step 4 : lancer les tests pour vérifier qu'ils passent**

Run : `./gradlew :core:test --tests "*TickRateStateMachineTest"`
Attendu : PASS (11 tests).

- [ ] **Step 5 : commit**

```bash
git add core
git commit -m "feat(core): port MineColonies tick-rate AI state machine

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 4 : compétences, expérience, noms, `CitizenData`

Portage de `Skill`, `CitizenSkillHandler.init(levelCap)` / `addXpToSkill` et `ExperienceUtils.getXPNeededForNextLevel`. Les noms suivent le format de `CitizenData.generateName`, avec des listes de noms **créées pour HyColony**.

**Files :**
- Create : `core/src/main/java/dev/hycolony/core/citizen/{Skill,SkillData,Skills,Experience,Gender,CitizenNames,CitizenData}.java`, `core/src/main/resources/dev/hycolony/core/citizen/names/default.json`
- Test : `core/src/test/java/dev/hycolony/core/citizen/{SkillsTest,CitizenNamesTest}.java`

**Interfaces :**
- Produces :
  - `enum Skill`, dans l'ordre de MineColonies, avec `Skill complementary()` et `Skill adverse()` (null pour `Intelligence`) ;
  - `final class SkillData { int level; double experience; }` avec getters et setters ;
  - `final class Skills` avec `static Skills initRandom(int levelCap, RandomGenerator)`, `int level(Skill)`, `double experience(Skill)`, `boolean addXp(Skill, double xp, int homeLevel, int homeMaxLevel)`, `void set(Skill, int level, double xp)` et les constantes `MAX_CITIZEN_LEVEL = 99`, `MAX_BUILDING_LEVEL = 5` ;
  - `final class Experience` avec `static double xpNeededForNextLevel(int level)` ;
  - `enum Gender { MALE, FEMALE }` ;
  - `final class CitizenNames` avec `static CitizenNames loadDefault()` et `String generate(RandomGenerator, Gender)` ;
  - `final class CitizenData(int id)` avec `id()`, `name()`/`setName`, `gender()`/`setGender`, `isChild()`, `skills()`/`setSkills`, `lastPosition()`/`setLastPosition(Vec3)`, `respawnPosition()`/`setRespawnPosition(BlockPos)`, `homeBuilding()`/`setHomeBuilding(BlockPos)`, `workBuilding()`/`setWorkBuilding(BlockPos)`, `saturation()`/`setSaturation(double)` et la constante `MAX_SATURATION = 60`.

- [ ] **Step 1 : écrire les tests qui échouent**

`core/src/test/java/dev/hycolony/core/citizen/SkillsTest.java` :

```java
package dev.hycolony.core.citizen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import org.junit.jupiter.api.Test;

class SkillsTest {
    @Test
    void xpCurveMatchesMineColonies() {
        assertEquals(1.0, Experience.xpNeededForNextLevel(0));
        assertEquals(6.005, Experience.xpNeededForNextLevel(1), 1e-9);
        assertEquals(56.0, Experience.xpNeededForNextLevel(10), 1e-9);
    }

    @Test
    void relationsMatchMineColonies() {
        assertEquals(Skill.Strength, Skill.Athletics.complementary());
        assertEquals(Skill.Dexterity, Skill.Athletics.adverse());
        assertEquals(Skill.Creativity, Skill.Adaptability.complementary());
        assertNull(Skill.Intelligence.complementary());
    }

    @Test
    void initRandomStaysWithinCap() {
        Skills s = Skills.initRandom(10, new Random(42));
        for (Skill skill : Skill.values()) {
            assertTrue(s.level(skill) >= 1 && s.level(skill) <= 9, skill + "=" + s.level(skill));
        }
        Skills low = Skills.initRandom(1, new Random(42));
        for (Skill skill : Skill.values()) {
            assertEquals(1, low.level(skill));
        }
    }

    @Test
    void addXpLevelsUpAndKeepsRemainder() {
        Skills s = Skills.initRandom(1, new Random(1)); // all level 1
        assertTrue(s.addXp(Skill.Focus, 6.005 + 2.0, 0, 5));
        assertEquals(2, s.level(Skill.Focus));
        assertEquals(2.0, s.experience(Skill.Focus), 1e-9);
    }

    @Test
    void homeLevelCapsSkillGrowth() {
        Skills s = Skills.initRandom(1, new Random(1));
        s.set(Skill.Focus, 10, 0);
        assertFalse(s.addXp(Skill.Focus, 1000, 0, 5)); // (0+1)*10 <= 10 -> blocked
        assertEquals(10, s.level(Skill.Focus));
        assertTrue(s.addXp(Skill.Focus, 60, 1, 5));  // home level 1 allows up to 20
    }

    @Test
    void maxLevelBlocksGrowth() {
        Skills s = Skills.initRandom(1, new Random(1));
        s.set(Skill.Mana, Skills.MAX_CITIZEN_LEVEL, 0);
        assertFalse(s.addXp(Skill.Mana, 1e9, 5, 5));
    }
}
```

`core/src/test/java/dev/hycolony/core/citizen/CitizenNamesTest.java` :

```java
package dev.hycolony.core.citizen;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import org.junit.jupiter.api.Test;

class CitizenNamesTest {
    @Test
    void westernNameHasFirstInitialAndSurname() {
        CitizenNames names = CitizenNames.loadDefault();
        String name = names.generate(new Random(7), Gender.FEMALE);
        assertTrue(name.matches("\\p{L}+ [A-Z]\\. \\p{L}+"), name);
    }
}
```

- [ ] **Step 2 : lancer les tests pour vérifier qu'ils échouent**

Run : `./gradlew :core:test --tests "dev.hycolony.core.citizen.*"`
Attendu : erreur de compilation.

- [ ] **Step 3 : implémenter**

`citizen/Skill.java` :

```java
package dev.hycolony.core.citizen;

/** The 11 citizen skills, in MineColonies order. */
public enum Skill {
    Athletics, Dexterity, Strength, Agility, Stamina, Mana, Adaptability, Focus, Creativity, Knowledge, Intelligence;

    public Skill complementary() {
        return switch (this) {
            case Athletics -> Strength;
            case Dexterity -> Agility;
            case Strength -> Athletics;
            case Agility -> Dexterity;
            case Stamina -> Knowledge;
            case Mana -> Focus;
            case Adaptability -> Creativity;
            case Focus -> Mana;
            case Creativity -> Adaptability;
            case Knowledge -> Stamina;
            case Intelligence -> null;
        };
    }

    public Skill adverse() {
        return switch (this) {
            case Athletics -> Dexterity;
            case Dexterity -> Athletics;
            case Strength -> Agility;
            case Agility -> Strength;
            case Stamina -> Mana;
            case Mana -> Stamina;
            case Adaptability -> Focus;
            case Focus -> Adaptability;
            case Creativity -> Knowledge;
            case Knowledge -> Creativity;
            case Intelligence -> null;
        };
    }
}
```

`citizen/SkillData.java` :

```java
package dev.hycolony.core.citizen;

public final class SkillData {
    private int level;
    private double experience;

    public SkillData(int level, double experience) {
        this.level = level;
        this.experience = experience;
    }

    public int level() { return level; }
    public double experience() { return experience; }
    void setLevel(int level) { this.level = level; }
    void setExperience(double experience) { this.experience = experience; }
}
```

`citizen/Experience.java` :

```java
package dev.hycolony.core.citizen;

/** Port of MineColonies' ExperienceUtils. */
public final class Experience {
    private static final double EXPERIENCE_MULTIPLIER = 1D;

    private Experience() {}

    public static double xpNeededForNextLevel(int currentLevel) {
        if (currentLevel <= 0) {
            return 1;
        }
        return Math.max(1, 1 + EXPERIENCE_MULTIPLIER * 5 * currentLevel
                + 0.005 * ((double) currentLevel * currentLevel * currentLevel));
    }
}
```

`citizen/Skills.java` :

```java
package dev.hycolony.core.citizen;

import java.util.EnumMap;
import java.util.Map;
import java.util.random.RandomGenerator;

/** Port of MineColonies' CitizenSkillHandler (level init + XP gain). */
public final class Skills {
    public static final int MAX_CITIZEN_LEVEL = 99;
    public static final int MAX_BUILDING_LEVEL = 5;

    private final EnumMap<Skill, SkillData> map = new EnumMap<>(Skill.class);

    private Skills() {}

    /** MineColonies init(levelCap): levels uniformly in [1, levelCap-1], or all 1 when levelCap <= 1. */
    public static Skills initRandom(int levelCap, RandomGenerator random) {
        Skills skills = new Skills();
        for (Skill skill : Skill.values()) {
            int level = levelCap <= 1 ? 1 : random.nextInt(levelCap - 1) + 1;
            skills.map.put(skill, new SkillData(level, 0.0));
        }
        return skills;
    }

    public static Skills empty() {
        Skills skills = new Skills();
        for (Skill skill : Skill.values()) {
            skills.map.put(skill, new SkillData(1, 0.0));
        }
        return skills;
    }

    public int level(Skill skill) { return map.get(skill).level(); }
    public double experience(Skill skill) { return map.get(skill).experience(); }
    public Map<Skill, SkillData> view() { return java.util.Collections.unmodifiableMap(map); }

    public void set(Skill skill, int level, double experience) {
        map.put(skill, new SkillData(Math.max(1, Math.min(level, MAX_CITIZEN_LEVEL)), experience));
    }

    /**
     * Port of addXpToSkill. {@code homeLevel}/{@code homeMaxLevel}: citizen's home building level and max
     * level (0 and MAX_BUILDING_LEVEL when homeless). Returns true when the skill leveled up.
     */
    public boolean addXp(Skill skill, double xp, int homeLevel, int homeMaxLevel) {
        SkillData data = map.get(skill);
        if (((homeLevel < homeMaxLevel || homeMaxLevel < MAX_BUILDING_LEVEL) && (homeLevel + 1) * 10 <= data.level())
                || data.level() >= MAX_CITIZEN_LEVEL) {
            return false;
        }
        int originalLevel = data.level();
        double xpToLevelUp = Math.min(Double.MAX_VALUE, data.experience() + xp);
        while (xpToLevelUp > 0) {
            double next = Experience.xpNeededForNextLevel(data.level());
            if (next > xpToLevelUp) {
                data.setExperience(xpToLevelUp);
                break;
            }
            xpToLevelUp -= next;
            data.setLevel(data.level() + 1);
        }
        return data.level() > originalLevel;
    }
}
```

`citizen/Gender.java` :

```java
package dev.hycolony.core.citizen;

public enum Gender { MALE, FEMALE }
```

`citizen/CitizenNames.java` :

```java
package dev.hycolony.core.citizen;

import com.google.gson.Gson;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.random.RandomGenerator;

/** Name lists + MineColonies' generateName format. */
public final class CitizenNames {
    public enum Order { WESTERN, EASTERN }

    private record Data(Order order, List<String> maleFirstNames, List<String> femaleFirstNames, List<String> surnames) {}

    private final Data data;

    private CitizenNames(Data data) {
        this.data = data;
    }

    public static CitizenNames loadDefault() {
        try (InputStream in = CitizenNames.class.getResourceAsStream("names/default.json")) {
            if (in == null) {
                throw new IllegalStateException("Missing names/default.json");
            }
            return new CitizenNames(new Gson().fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), Data.class));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public String generate(RandomGenerator random, Gender gender) {
        String first = pick(random, gender == Gender.FEMALE ? data.femaleFirstNames() : data.maleFirstNames());
        String last = pick(random, data.surnames());
        if (data.order() == Order.EASTERN) {
            return last + " " + first;
        }
        char middle = (char) ('A' + random.nextInt(26));
        return first + " " + middle + ". " + last;
    }

    private static String pick(RandomGenerator random, List<String> list) {
        return list.get(random.nextInt(list.size()));
    }
}
```

`core/src/main/resources/dev/hycolony/core/citizen/names/default.json` :

```json
{
  "order": "WESTERN",
  "maleFirstNames": ["Aldric", "Bram", "Corwin", "Dorian", "Edric", "Falk", "Garrick", "Hale", "Isen", "Jory",
    "Kael", "Lorin", "Merek", "Nils", "Orin", "Perrin", "Quill", "Rook", "Soren", "Tobin", "Ulric", "Varek", "Wystan", "Yorick"],
  "femaleFirstNames": ["Aela", "Brina", "Cessa", "Dalia", "Elowen", "Fenna", "Gwyn", "Hesta", "Ilsa", "Jessa",
    "Kira", "Liora", "Maren", "Nell", "Orla", "Pella", "Rhosyn", "Sela", "Tilda", "Una", "Vessa", "Wren", "Yara", "Zinnia"],
  "surnames": ["Ashdown", "Brightwater", "Coldbrook", "Dunmere", "Emberfield", "Fairwind", "Greymantle", "Hollowell",
    "Ironwood", "Kettleby", "Longmead", "Mossvale", "Northcott", "Oakhart", "Pinecroft", "Quarrystone", "Ravensworth",
    "Stonebridge", "Thornbury", "Underhill", "Westmarch", "Wildermoor"]
}
```

`citizen/CitizenData.java` :

```java
package dev.hycolony.core.citizen;

import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.Vec3;

/** Persistent citizen state. The in-world body is disposable and rebuilt from this. */
public final class CitizenData {
    public static final double MAX_SATURATION = 60;

    private final int id;
    private String name = "";
    private Gender gender = Gender.MALE;
    private boolean child;
    private Skills skills = Skills.empty();
    private Vec3 lastPosition;
    private BlockPos respawnPosition;
    private BlockPos homeBuilding;
    private BlockPos workBuilding;
    private double saturation = MAX_SATURATION;

    public CitizenData(int id) {
        this.id = id;
    }

    public int id() { return id; }
    public String name() { return name; }
    public void setName(String name) { this.name = name; }
    public Gender gender() { return gender; }
    public void setGender(Gender gender) { this.gender = gender; }
    public boolean isChild() { return child; }
    public void setChild(boolean child) { this.child = child; }
    public Skills skills() { return skills; }
    public void setSkills(Skills skills) { this.skills = skills; }
    public Vec3 lastPosition() { return lastPosition; }
    public void setLastPosition(Vec3 lastPosition) { this.lastPosition = lastPosition; }
    public BlockPos respawnPosition() { return respawnPosition; }
    public void setRespawnPosition(BlockPos respawnPosition) { this.respawnPosition = respawnPosition; }
    public BlockPos homeBuilding() { return homeBuilding; }
    public void setHomeBuilding(BlockPos homeBuilding) { this.homeBuilding = homeBuilding; }
    public BlockPos workBuilding() { return workBuilding; }
    public void setWorkBuilding(BlockPos workBuilding) { this.workBuilding = workBuilding; }
    public double saturation() { return saturation; }
    public void setSaturation(double saturation) { this.saturation = saturation; }
}
```

- [ ] **Step 4 : lancer les tests pour vérifier qu'ils passent**

Run : `./gradlew :core:test --tests "dev.hycolony.core.citizen.*"`
Attendu : PASS.

- [ ] **Step 5 : commit**

```bash
git add core
git commit -m "feat(core): citizen skills, XP curve, name generator, citizen data

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 5 : permissions (actions, rangs)

Portage de `Action` (indices de bit **exacts** de MineColonies) et des rangs par défaut de `Permissions` (même cascade de permissions).

**Files :**
- Create : `core/src/main/java/dev/hycolony/core/colony/{Action,Rank,Permissions}.java`
- Test : `core/src/test/java/dev/hycolony/core/colony/PermissionsTest.java`

**Interfaces :**
- Produces :
  - `enum Action` avec `int flag()` et `long mask()` ;
  - `final class Rank(int id, String name, long permissions, boolean initial, boolean colonyManager, boolean hostile)` avec `has(Action)`, `add(Action)`, `remove(Action)`, les getters et `long permissions()` ;
  - `final class Permissions` avec les constantes `OWNER=0, OFFICER=1, FRIEND=2, NEUTRAL=3, HOSTILE=4`, `static Permissions createDefault(UUID owner, String ownerName)`, `UUID owner()`, `String ownerName()`, `Rank rankOf(UUID)`, `boolean hasPermission(UUID, Action)`, `boolean setRank(UUID player, String name, int rankId)`, `boolean isMember(UUID)`, `Map<Integer, Rank> ranks()`, `Map<UUID, Member> members()`, `record Member(String name, int rankId)`, et en package-private `static Permissions restore(UUID owner, String ownerName, Map<Integer, Rank> ranks, Map<UUID, Member> members)`.

- [ ] **Step 1 : écrire les tests qui échouent**

`core/src/test/java/dev/hycolony/core/colony/PermissionsTest.java` :

```java
package dev.hycolony.core.colony;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class PermissionsTest {
    private final UUID owner = UUID.randomUUID();
    private final UUID stranger = UUID.randomUUID();
    private final Permissions perms = Permissions.createDefault(owner, "Alice");

    @Test
    void bitFlagsMatchMineColonies() {
        assertEquals(1L, Action.ACCESS_HUTS.mask());
        assertEquals(1L << 2, Action.PLACE_HUTS.mask());
        assertEquals(1L << 30, Action.ACCESS_TOGGLEABLES.mask());
        assertEquals(27, Action.values().length);
    }

    @Test
    void neutralIsDefaultAndVeryLimited() {
        assertEquals(Permissions.NEUTRAL, perms.rankOf(stranger).id());
        assertTrue(perms.hasPermission(stranger, Action.MAP_BORDER));
        assertTrue(perms.hasPermission(stranger, Action.ACCESS_TOGGLEABLES));
        assertFalse(perms.hasPermission(stranger, Action.ACCESS_HUTS));
        assertFalse(perms.hasPermission(stranger, Action.PLACE_BLOCKS));
    }

    @Test
    void ranksCascadeLikeMineColonies() {
        Rank friend = perms.ranks().get(Permissions.FRIEND);
        Rank officer = perms.ranks().get(Permissions.OFFICER);
        Rank ownerRank = perms.ranks().get(Permissions.OWNER);
        assertTrue(friend.has(Action.ACCESS_HUTS));
        assertFalse(friend.has(Action.PLACE_BLOCKS));
        assertTrue(officer.has(Action.PLACE_BLOCKS));
        assertTrue(officer.has(Action.ACCESS_HUTS));
        assertTrue(officer.isColonyManager());
        assertFalse(officer.has(Action.EDIT_PERMISSIONS));
        assertTrue(ownerRank.has(Action.EDIT_PERMISSIONS));
        assertTrue(ownerRank.has(Action.PLACE_HUTS));
        assertTrue(perms.ranks().get(Permissions.HOSTILE).isHostile());
        assertTrue(perms.ranks().get(Permissions.HOSTILE).has(Action.HURT_CITIZEN));
    }

    @Test
    void ownerHasOwnerRank() {
        assertEquals(Permissions.OWNER, perms.rankOf(owner).id());
        assertTrue(perms.isMember(owner));
    }

    @Test
    void setRankRejectsOwnerChanges() {
        UUID bob = UUID.randomUUID();
        assertTrue(perms.setRank(bob, "Bob", Permissions.OFFICER));
        assertEquals(Permissions.OFFICER, perms.rankOf(bob).id());
        assertTrue(perms.isMember(bob));
        assertFalse(perms.setRank(bob, "Bob", Permissions.OWNER));
        assertFalse(perms.setRank(owner, "Alice", Permissions.FRIEND));
        assertFalse(perms.setRank(bob, "Bob", 99));
    }
}
```

- [ ] **Step 2 : lancer les tests pour vérifier qu'ils échouent**

Run : `./gradlew :core:test --tests "*PermissionsTest"`
Attendu : erreur de compilation.

- [ ] **Step 3 : implémenter**

`colony/Action.java` :

```java
package dev.hycolony.core.colony;

/** Colony permission actions. Flags are MineColonies' exact bit indices: never renumber (saved masks). */
public enum Action {
    ACCESS_HUTS(0),
    PLACE_HUTS(2),
    BREAK_HUTS(3),
    EDIT_PERMISSIONS(4),
    MANAGE_HUTS(5),
    RECEIVE_MESSAGES(6),
    USE_SCAN_TOOL(7),
    PLACE_BLOCKS(8),
    BREAK_BLOCKS(9),
    TOSS_ITEM(10),
    PICKUP_ITEM(11),
    FILL_BUCKET(12),
    OPEN_CONTAINER(13),
    RIGHTCLICK_BLOCK(14),
    RIGHTCLICK_ENTITY(15),
    THROW_POTION(16),
    SHOOT_ARROW(17),
    ATTACK_CITIZEN(18),
    ATTACK_ENTITY(19),
    TELEPORT_TO_COLONY(21),
    EXPLODE(22),
    RALLY_GUARDS(25),
    HURT_CITIZEN(26),
    HURT_VISITOR(27),
    MAP_BORDER(28),
    MAP_DEATHS(29),
    ACCESS_TOGGLEABLES(30);

    private final int flag;

    Action(int flag) {
        this.flag = flag;
    }

    public int flag() { return flag; }

    public long mask() { return 1L << flag; }
}
```

`colony/Rank.java` :

```java
package dev.hycolony.core.colony;

public final class Rank {
    private final int id;
    private final String name;
    private long permissions;
    private final boolean initial;
    private boolean colonyManager;
    private boolean hostile;

    public Rank(int id, String name, long permissions, boolean initial, boolean colonyManager, boolean hostile) {
        this.id = id;
        this.name = name;
        this.permissions = permissions;
        this.initial = initial;
        this.colonyManager = colonyManager;
        this.hostile = hostile;
    }

    public boolean has(Action action) { return (permissions & action.mask()) != 0; }
    public void add(Action action) { permissions |= action.mask(); }
    public void remove(Action action) { permissions &= ~action.mask(); }

    public int id() { return id; }
    public String name() { return name; }
    public long permissions() { return permissions; }
    public boolean isInitial() { return initial; }
    public boolean isColonyManager() { return colonyManager; }
    public boolean isHostile() { return hostile; }
    void setColonyManager(boolean colonyManager) { this.colonyManager = colonyManager; }
    void setHostile(boolean hostile) { this.hostile = hostile; }
}
```

`colony/Permissions.java` :

```java
package dev.hycolony.core.colony;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Colony ranks and members. Default ranks replicate MineColonies' cascade. */
public final class Permissions {
    public static final int OWNER = 0, OFFICER = 1, FRIEND = 2, NEUTRAL = 3, HOSTILE = 4;

    public record Member(String name, int rankId) {}

    private final UUID owner;
    private String ownerName;
    private final Map<Integer, Rank> ranks;
    private final Map<UUID, Member> members;

    private Permissions(UUID owner, String ownerName, Map<Integer, Rank> ranks, Map<UUID, Member> members) {
        this.owner = owner;
        this.ownerName = ownerName;
        this.ranks = ranks;
        this.members = members;
    }

    public static Permissions createDefault(UUID owner, String ownerName) {
        Map<Integer, Rank> ranks = new LinkedHashMap<>();
        for (int id = OWNER; id <= HOSTILE; id++) {
            ranks.put(id, defaultRank(id));
        }
        Map<UUID, Member> members = new LinkedHashMap<>();
        members.put(owner, new Member(ownerName, OWNER));
        return new Permissions(owner, ownerName, ranks, members);
    }

    static Permissions restore(UUID owner, String ownerName, Map<Integer, Rank> ranks, Map<UUID, Member> members) {
        return new Permissions(owner, ownerName, new LinkedHashMap<>(ranks), new LinkedHashMap<>(members));
    }

    /** Same fall-through as MineColonies Permissions: OWNER ⊃ OFFICER ⊃ FRIEND ⊃ NEUTRAL; HOSTILE apart. */
    private static Rank defaultRank(int id) {
        String name = switch (id) {
            case OWNER -> "Owner";
            case OFFICER -> "Officer";
            case FRIEND -> "Friend";
            case NEUTRAL -> "Neutral";
            default -> "Hostile";
        };
        Rank rank = new Rank(id, name, 0L, true, false, false);
        if (id == HOSTILE) {
            rank.add(Action.HURT_CITIZEN);
            rank.add(Action.HURT_VISITOR);
            rank.add(Action.MAP_BORDER);
            rank.setHostile(true);
            return rank;
        }
        if (id <= OWNER) {
            rank.add(Action.EDIT_PERMISSIONS);
            rank.add(Action.MAP_BORDER);
            rank.add(Action.MAP_DEATHS);
        }
        if (id <= OFFICER) {
            for (Action a : new Action[] {Action.PLACE_HUTS, Action.BREAK_HUTS, Action.MANAGE_HUTS, Action.RECEIVE_MESSAGES,
                    Action.PLACE_BLOCKS, Action.BREAK_BLOCKS, Action.FILL_BUCKET, Action.OPEN_CONTAINER, Action.RALLY_GUARDS,
                    Action.MAP_BORDER, Action.MAP_DEATHS}) {
                rank.add(a);
            }
            rank.setColonyManager(true);
        }
        if (id <= FRIEND) {
            for (Action a : new Action[] {Action.ACCESS_HUTS, Action.USE_SCAN_TOOL, Action.TOSS_ITEM, Action.PICKUP_ITEM,
                    Action.RIGHTCLICK_BLOCK, Action.RIGHTCLICK_ENTITY, Action.THROW_POTION, Action.SHOOT_ARROW,
                    Action.ATTACK_CITIZEN, Action.ATTACK_ENTITY, Action.TELEPORT_TO_COLONY, Action.ACCESS_TOGGLEABLES,
                    Action.MAP_BORDER}) {
                rank.add(a);
            }
        }
        rank.add(Action.ACCESS_TOGGLEABLES);
        rank.add(Action.MAP_BORDER);
        return rank;
    }

    public UUID owner() { return owner; }
    public String ownerName() { return ownerName; }

    public Rank rankOf(UUID player) {
        Member member = members.get(player);
        return member != null ? ranks.getOrDefault(member.rankId(), ranks.get(NEUTRAL)) : ranks.get(NEUTRAL);
    }

    public boolean hasPermission(UUID player, Action action) {
        return rankOf(player).has(action);
    }

    /** A member = a player whose rank grants ACCESS_HUTS (Friend and above). */
    public boolean isMember(UUID player) {
        return rankOf(player).has(Action.ACCESS_HUTS);
    }

    /** Refuses to grant OWNER, to change the owner's rank, or to use an unknown rank. */
    public boolean setRank(UUID player, String name, int rankId) {
        if (rankId == OWNER || player.equals(owner) || !ranks.containsKey(rankId)) {
            return false;
        }
        members.put(player, new Member(name, rankId));
        return true;
    }

    public Map<Integer, Rank> ranks() { return Collections.unmodifiableMap(ranks); }
    public Map<UUID, Member> members() { return Collections.unmodifiableMap(members); }
}
```

- [ ] **Step 4 : lancer les tests pour vérifier qu'ils passent**

Run : `./gradlew :core:test --tests "*PermissionsTest"`
Attendu : PASS.

- [ ] **Step 5 : commit**

```bash
git add core
git commit -m "feat(core): colony permissions with MineColonies ranks and action flags

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 6 : territoire (cellules de claim)

**Files :**
- Create : `core/src/main/java/dev/hycolony/core/colony/{ClaimCell,TerritoryIndex}.java`
- Test : `core/src/test/java/dev/hycolony/core/colony/TerritoryIndexTest.java`

**Interfaces :**
- Produces :
  - `record ClaimCell(int x, int z)` avec `SIZE = 16` et `static ClaimCell of(BlockPos)` ;
  - `final class TerritoryIndex` avec `OptionalInt colonyAt(BlockPos)`, `void claimSquare(int colonyId, ClaimCell center, int radius)`, `void releaseAll(int colonyId)`, `boolean isFreeForNewColony(BlockPos center, int initialSize, int minDistance)` et `int claimedCount(int colonyId)`.

- [ ] **Step 1 : écrire les tests qui échouent**

`core/src/test/java/dev/hycolony/core/colony/TerritoryIndexTest.java` :

```java
package dev.hycolony.core.colony;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.kernel.BlockPos;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

class TerritoryIndexTest {
    @Test
    void cellOfNegativeCoordinatesFloors() {
        assertEquals(new ClaimCell(-1, 0), ClaimCell.of(new BlockPos(-1, 64, 15)));
        assertEquals(new ClaimCell(1, -2), ClaimCell.of(new BlockPos(16, 0, -17)));
    }

    @Test
    void initialClaimIsNineByNineCells() {
        TerritoryIndex t = new TerritoryIndex();
        t.claimSquare(1, ClaimCell.of(new BlockPos(0, 64, 0)), 4);
        assertEquals(81, t.claimedCount(1));
        assertEquals(OptionalInt.of(1), t.colonyAt(new BlockPos(4 * 16 + 15, 0, -4 * 16)));
        assertEquals(OptionalInt.empty(), t.colonyAt(new BlockPos(5 * 16, 0, 0)));
    }

    @Test
    void claimNeverStealsCells() {
        TerritoryIndex t = new TerritoryIndex();
        t.claimSquare(1, new ClaimCell(0, 0), 1);
        t.claimSquare(2, new ClaimCell(2, 0), 1);
        assertEquals(OptionalInt.of(1), t.colonyAt(new BlockPos(16, 0, 0)));
        assertEquals(6, t.claimedCount(2));
    }

    @Test
    void newColonyNeedsInitialPlusMinDistanceFree() {
        TerritoryIndex t = new TerritoryIndex();
        t.claimSquare(1, new ClaimCell(0, 0), 4); // cells -4..4
        assertFalse(t.isFreeForNewColony(new BlockPos(16 * 16, 64, 0), 4, 8)); // centre cell 16: 16-12 = 4 claimed
        assertTrue(t.isFreeForNewColony(new BlockPos(17 * 16, 64, 0), 4, 8));  // 17-12 = 5 free
    }

    @Test
    void releaseFreesCells() {
        TerritoryIndex t = new TerritoryIndex();
        t.claimSquare(1, new ClaimCell(0, 0), 2);
        t.releaseAll(1);
        assertEquals(0, t.claimedCount(1));
        assertEquals(OptionalInt.empty(), t.colonyAt(new BlockPos(0, 0, 0)));
    }
}
```

- [ ] **Step 2 : lancer les tests pour vérifier qu'ils échouent**

Run : `./gradlew :core:test --tests "*TerritoryIndexTest"`
Attendu : erreur de compilation.

- [ ] **Step 3 : implémenter**

`colony/ClaimCell.java` :

```java
package dev.hycolony.core.colony;

import dev.hycolony.core.kernel.BlockPos;

/** A 16x16 claim cell (the size of a Minecraft chunk) so distances match MineColonies exactly. */
public record ClaimCell(int x, int z) {
    public static final int SIZE = 16;

    public static ClaimCell of(BlockPos pos) {
        return new ClaimCell(Math.floorDiv(pos.x(), SIZE), Math.floorDiv(pos.z(), SIZE));
    }
}
```

`colony/TerritoryIndex.java` :

```java
package dev.hycolony.core.colony;

import dev.hycolony.core.kernel.BlockPos;
import java.util.HashMap;
import java.util.Map;
import java.util.OptionalInt;

/** Which colony owns which claim cell, for one world. Rebuilt from colonies on load. */
public final class TerritoryIndex {
    private final Map<ClaimCell, Integer> owners = new HashMap<>();

    public OptionalInt colonyAt(BlockPos pos) {
        Integer id = owners.get(ClaimCell.of(pos));
        return id == null ? OptionalInt.empty() : OptionalInt.of(id);
    }

    /** Claims every free cell in the square of the given radius. Never steals a cell. */
    public void claimSquare(int colonyId, ClaimCell center, int radius) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                owners.putIfAbsent(new ClaimCell(center.x() + dx, center.z() + dz), colonyId);
            }
        }
    }

    public void releaseAll(int colonyId) {
        owners.values().removeIf(id -> id == colonyId);
    }

    /** No claimed cell within (initialSize + minDistance) cells of the would-be centre. */
    public boolean isFreeForNewColony(BlockPos center, int initialSize, int minDistance) {
        ClaimCell c = ClaimCell.of(center);
        int r = initialSize + minDistance;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (owners.containsKey(new ClaimCell(c.x() + dx, c.z() + dz))) {
                    return false;
                }
            }
        }
        return true;
    }

    public int claimedCount(int colonyId) {
        return (int) owners.values().stream().filter(id -> id == colonyId).count();
    }
}
```

- [ ] **Step 4 : lancer les tests pour vérifier qu'ils passent**

Run : `./gradlew :core:test --tests "*TerritoryIndexTest"`
Attendu : PASS.

- [ ] **Step 5 : commit**

```bash
git add core
git commit -m "feat(core): colony territory with 16-block claim cells

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 7 : bâtiments (registre, modules, gestionnaire)

**Files :**
- Create : `core/src/main/java/dev/hycolony/core/building/{BuildingModule,PersistentModule,TickingModule,BuildingEventsModule,ModuleProducer,BuildingType,BuildingRegistry,BuildingTypes,Building,BuildingManager}.java`
- Test : `core/src/test/java/dev/hycolony/core/building/BuildingManagerTest.java`

**Interfaces :**
- Produces :
  - `interface BuildingModule {}` ;
  - `interface PersistentModule extends BuildingModule { void write(JsonObject out); void read(JsonObject in); }` ;
  - `interface TickingModule extends BuildingModule { void onColonyTick(Building building); }` ;
  - `interface BuildingEventsModule extends BuildingModule` avec les méthodes par défaut `onPlaced(Building)`, `onRemoved(Building)`, `onUpgradeComplete(Building, int)` et `onWakeUp(Building)` ;
  - `record ModuleProducer(String key, Supplier<? extends BuildingModule> factory)` ;
  - `record BuildingType(String id, String hutBlockKey, int maxLevel, List<ModuleProducer> modules)` ;
  - `final class BuildingRegistry` avec `register(BuildingType)`, `Optional<BuildingType> byId(String)` et `Optional<BuildingType> byHutKey(String)` ;
  - `final class BuildingTypes` avec `TOWN_HALL` et `static BuildingRegistry defaults()` ;
  - `final class Building` avec `static Building create(BuildingType, BlockPos, int rotation)`, `type()`, `position()`, `rotation()`, `level()`/`setLevel`, `isBuilt()`/`setBuilt`, `customName()`/`setCustomName`, `style()`/`setStyle`, `<T extends BuildingModule> Optional<T> module(Class<T>)`, `Map<String, BuildingModule> modules()` et `Map<String, JsonObject> unknownModules()` ;
  - `final class BuildingManager` avec `add(Building)`, `Optional<Building> remove(BlockPos)`, `Optional<Building> at(BlockPos)`, `Optional<Building> townHall()`, `Collection<Building> all()`, `void onColonyTick()`, `void keepUnknown(JsonObject raw)` et `List<JsonObject> unknown()`.

- [ ] **Step 1 : écrire les tests qui échouent**

`core/src/test/java/dev/hycolony/core/building/BuildingManagerTest.java` :

```java
package dev.hycolony.core.building;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import dev.hycolony.core.kernel.BlockPos;
import java.util.List;
import org.junit.jupiter.api.Test;

class BuildingManagerTest {
    static final class Counter implements TickingModule {
        int ticks;
        @Override public void onColonyTick(Building building) { ticks++; }
    }

    @Test
    void registryFindsTownHallByIdAndHutKey() {
        BuildingRegistry r = BuildingTypes.defaults();
        assertEquals(BuildingTypes.TOWN_HALL, r.byId("hycolony:townhall").orElseThrow());
        assertEquals(BuildingTypes.TOWN_HALL, r.byHutKey("hut.townhall").orElseThrow());
    }

    @Test
    void createInstantiatesModulesInOrderAndTicksThem() {
        BuildingType type = new BuildingType("test:b", "hut.b", 5, List.of(new ModuleProducer("counter", Counter::new)));
        Building b = Building.create(type, new BlockPos(1, 2, 3), 1);
        BuildingManager m = new BuildingManager();
        m.add(b);
        m.onColonyTick();
        m.onColonyTick();
        assertEquals(2, b.module(Counter.class).orElseThrow().ticks);
        assertEquals(0, b.level());
    }

    @Test
    void townHallLookupAndRemoval() {
        BuildingManager m = new BuildingManager();
        BlockPos pos = new BlockPos(0, 64, 0);
        m.add(Building.create(BuildingTypes.TOWN_HALL, pos, 0));
        assertTrue(m.townHall().isPresent());
        assertTrue(m.remove(pos).isPresent());
        assertTrue(m.townHall().isEmpty());
    }

    @Test
    void unknownBuildingsAreKeptVerbatim() {
        BuildingManager m = new BuildingManager();
        JsonObject raw = new JsonObject();
        raw.addProperty("type", "removed:thing");
        m.keepUnknown(raw);
        assertEquals(List.of(raw), m.unknown());
    }
}
```

- [ ] **Step 2 : lancer les tests pour vérifier qu'ils échouent**

Run : `./gradlew :core:test --tests "*BuildingManagerTest"`
Attendu : erreur de compilation.

- [ ] **Step 3 : implémenter**

`building/BuildingModule.java` :

```java
package dev.hycolony.core.building;

/** A piece of building behaviour. Capabilities come from the sub-interfaces it implements. */
public interface BuildingModule {}
```

`building/PersistentModule.java` :

```java
package dev.hycolony.core.building;

import com.google.gson.JsonObject;

public interface PersistentModule extends BuildingModule {
    void write(JsonObject out);

    void read(JsonObject in);
}
```

`building/TickingModule.java` :

```java
package dev.hycolony.core.building;

/** Ticked on the colony slow tick (every 500 ticks). */
public interface TickingModule extends BuildingModule {
    void onColonyTick(Building building);
}
```

`building/BuildingEventsModule.java` :

```java
package dev.hycolony.core.building;

public interface BuildingEventsModule extends BuildingModule {
    default void onPlaced(Building building) {}
    default void onRemoved(Building building) {}
    default void onUpgradeComplete(Building building, int newLevel) {}
    default void onWakeUp(Building building) {}
}
```

`building/ModuleProducer.java` :

```java
package dev.hycolony.core.building;

import java.util.function.Supplier;

/** {@code key} is stable and used as the save key of the module. */
public record ModuleProducer(String key, Supplier<? extends BuildingModule> factory) {}
```

`building/BuildingType.java` :

```java
package dev.hycolony.core.building;

import java.util.List;

/** Registry entry, like MineColonies' BuildingEntry. {@code hutBlockKey} is a logical id-map key. */
public record BuildingType(String id, String hutBlockKey, int maxLevel, List<ModuleProducer> modules) {
    public BuildingType {
        modules = List.copyOf(modules);
    }
}
```

`building/BuildingRegistry.java` :

```java
package dev.hycolony.core.building;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class BuildingRegistry {
    private final Map<String, BuildingType> byId = new LinkedHashMap<>();

    public void register(BuildingType type) {
        if (byId.putIfAbsent(type.id(), type) != null) {
            throw new IllegalArgumentException("Duplicate building type " + type.id());
        }
    }

    public Optional<BuildingType> byId(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    public Optional<BuildingType> byHutKey(String hutBlockKey) {
        return byId.values().stream().filter(t -> t.hutBlockKey().equals(hutBlockKey)).findFirst();
    }
}
```

`building/BuildingTypes.java` :

```java
package dev.hycolony.core.building;

import java.util.List;

public final class BuildingTypes {
    public static final BuildingType TOWN_HALL = new BuildingType("hycolony:townhall", "hut.townhall", 5, List.of());

    private BuildingTypes() {}

    public static BuildingRegistry defaults() {
        BuildingRegistry registry = new BuildingRegistry();
        registry.register(TOWN_HALL);
        return registry;
    }
}
```

`building/Building.java` :

```java
package dev.hycolony.core.building;

import com.google.gson.JsonObject;
import dev.hycolony.core.kernel.BlockPos;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class Building {
    private final BuildingType type;
    private final BlockPos position;
    private final int rotation;
    private int level;
    private boolean built;
    private String customName = "";
    private String style = "";
    private final Map<String, BuildingModule> modules = new LinkedHashMap<>();
    private final Map<String, JsonObject> unknownModules = new LinkedHashMap<>();

    private Building(BuildingType type, BlockPos position, int rotation) {
        this.type = type;
        this.position = position;
        this.rotation = rotation;
    }

    /** New building at level 0 with one fresh instance of each module of its type. */
    public static Building create(BuildingType type, BlockPos position, int rotation) {
        Building b = new Building(type, position, rotation);
        for (ModuleProducer producer : type.modules()) {
            b.modules.put(producer.key(), producer.factory().get());
        }
        return b;
    }

    public <T extends BuildingModule> Optional<T> module(Class<T> kind) {
        return modules.values().stream().filter(kind::isInstance).map(kind::cast).findFirst();
    }

    public BuildingType type() { return type; }
    public BlockPos position() { return position; }
    public int rotation() { return rotation; }
    public int level() { return level; }
    public void setLevel(int level) { this.level = level; }
    public boolean isBuilt() { return built; }
    public void setBuilt(boolean built) { this.built = built; }
    public String customName() { return customName; }
    public void setCustomName(String customName) { this.customName = customName; }
    public String style() { return style; }
    public void setStyle(String style) { this.style = style; }
    public Map<String, BuildingModule> modules() { return Collections.unmodifiableMap(modules); }
    /** Saved data of modules no longer registered for this type: written back untouched. */
    public Map<String, JsonObject> unknownModules() { return unknownModules; }
}
```

`building/BuildingManager.java` :

```java
package dev.hycolony.core.building;

import com.google.gson.JsonObject;
import dev.hycolony.core.kernel.BlockPos;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class BuildingManager {
    private final Map<BlockPos, Building> buildings = new LinkedHashMap<>();
    private final List<JsonObject> unknown = new ArrayList<>();

    public void add(Building building) {
        buildings.put(building.position(), building);
    }

    public Optional<Building> remove(BlockPos pos) {
        return Optional.ofNullable(buildings.remove(pos));
    }

    public Optional<Building> at(BlockPos pos) {
        return Optional.ofNullable(buildings.get(pos));
    }

    public Optional<Building> townHall() {
        return buildings.values().stream().filter(b -> b.type().equals(BuildingTypes.TOWN_HALL)).findFirst();
    }

    public Collection<Building> all() {
        return Collections.unmodifiableCollection(buildings.values());
    }

    public void onColonyTick() {
        for (Building building : buildings.values()) {
            for (BuildingModule module : building.modules().values()) {
                if (module instanceof TickingModule ticking) {
                    ticking.onColonyTick(building);
                }
            }
        }
    }

    /** Saved buildings whose type is not registered anymore: kept and written back untouched. */
    public void keepUnknown(JsonObject raw) {
        unknown.add(raw);
    }

    public List<JsonObject> unknown() {
        return Collections.unmodifiableList(unknown);
    }
}
```

- [ ] **Step 4 : lancer les tests pour vérifier qu'ils passent**

Run : `./gradlew :core:test --tests "*BuildingManagerTest"`
Attendu : PASS.

- [ ] **Step 5 : commit**

```bash
git add core
git commit -m "feat(core): building types, module system and building manager

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 8 : ports, contexte, journal et `Colony` (machine d'état, jour/nuit)

**Files :**
- Create : `core/src/main/java/dev/hycolony/core/kernel/port/{GameClock,BodyId,NavStatus,CitizenBodies,WorldQuery,Notifier,Msg,PlayerDirectory}.java`, `colony/ui/{UiPort,FoundColonyView,TownHallView,CitizenRow}.java`, `colony/{ColonyState,ColonyContext,EventLog,ColonyEvents,Colony}.java`
- Create (test) : `core/src/test/java/dev/hycolony/core/testing/{FakeClock,FakeBodies,FakeWorld,FakeNotifier,FakePlayers,FakeUi,TestContexts}.java`
- Test : `core/src/test/java/dev/hycolony/core/colony/ColonyTest.java`
- Note : `Colony` crée un `CitizenManager`. La Task 8 crée donc une **version minimale** de `citizen/CitizenManager.java`, que la Task 9 complète.

**Interfaces :**
- Consumes : `Permissions`, `TerritoryIndex`, `BuildingManager`, `BuildingRegistry`, `CitizenNames`, `ColonyConfig`, `EventBus`, `TickRateStateMachine`, `AITarget`.
- Produces :
  - `interface GameClock { long currentTick(); boolean isDaytime(); }` ;
  - `record BodyId(long value)` et `enum NavStatus { IDLE, MOVING, ARRIVED, BLOCKED, FAILED }` ;
  - `interface CitizenBodies` avec `Optional<BodyId> spawn(WorldKey, BlockPos near, int colonyId, int citizenId, String displayName)`, `boolean isAlive(BodyId)`, `Optional<Vec3> position(BodyId)`, `void moveTo(BodyId, Vec3)`, `NavStatus navStatus(BodyId)`, `void setDisplayName(BodyId, String)` et `void despawn(BodyId)` ;
  - `interface WorldQuery { boolean isLoaded(BlockPos); }` ;
  - `record Msg(String key, List<String> params)` avec `static Msg of(String key, String... params)`, et `interface Notifier { void send(UUID player, Msg msg); }` ;
  - `interface PlayerDirectory` avec `boolean isOnline(UUID)`, `Optional<BlockPos> position(UUID)` et `Collection<UUID> onlineIn(WorldKey)` ;
  - `interface UiPort` avec `showFoundColony(UUID, FoundColonyView)`, `showTownHall(UUID, TownHallView)` et `close(UUID)` ;
  - `record FoundColonyView(String suggestedName)`, `record CitizenRow(String name, Gender gender, String status)` et `record TownHallView(int colonyId, String colonyName, String ownerName, int day, List<CitizenRow> citizens, boolean canRename)` ;
  - `enum ColonyState implements IState { ACTIVE, UNLOADED, INACTIVE }` ;
  - `record ColonyContext(WorldKey world, ColonyConfig config, GameClock clock, CitizenBodies bodies, WorldQuery worldQuery, Notifier notifier, UiPort ui, PlayerDirectory players, BuildingRegistry buildingTypes, CitizenNames names, RandomGenerator random, EventBus bus)` ;
  - `final class EventLog` avec `MAX_ENTRIES = 100`, `record Entry(String type, int day, List<String> params)`, `void add(String type, int day, String... params)`, `void restore(Entry)` et `List<Entry> entries()` ;
  - `final class ColonyEvents` qui regroupe les records `ColonyCreated(Colony colony)`, `ColonyDeleted(int colonyId)`, `BuildingPlaced(Colony colony, Building building)`, `BuildingRemoved(Colony colony, Building building)`, `DayStarted(Colony colony)` et `NightFell(Colony colony)` ;
  - `final class Colony` :
    - constantes `UPDATE_STATE_INTERVAL = 100`, `CITIZEN_DATA_INTERVAL = 60`, `DAYTIME_INTERVAL = 20`, `SLOW_TICK = 500`, `EXCEPTION_SUSPEND_TICKS = 6000` ;
    - constructeur `Colony(ColonyContext ctx, TerritoryIndex territory, int id, String name, BlockPos center, Permissions permissions)` ;
    - `tick()`, `id()`, `name()`/`setName`, `center()`, `permissions()`, `buildings()`, `citizens()`, `log()`, `day()`/`setDay`, `state()`, `context()`, `isDirty()`, `markDirty()`, `clearDirty()` et `boolean contains(BlockPos)`.

- [ ] **Step 1 : écrire les ports et les vues (interfaces seulement)**

`kernel/port/GameClock.java` :

```java
package dev.hycolony.core.kernel.port;

/** Core time. {@code currentTick} advances 20 times per second while the world runs. */
public interface GameClock {
    long currentTick();

    boolean isDaytime();
}
```

`kernel/port/BodyId.java` :

```java
package dev.hycolony.core.kernel.port;

/** Opaque handle of an in-world citizen body, assigned by the adapter. */
public record BodyId(long value) {}
```

`kernel/port/NavStatus.java` :

```java
package dev.hycolony.core.kernel.port;

public enum NavStatus { IDLE, MOVING, ARRIVED, BLOCKED, FAILED }
```

`kernel/port/CitizenBodies.java` :

```java
package dev.hycolony.core.kernel.port;

import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.Vec3;
import dev.hycolony.core.kernel.WorldKey;
import java.util.Optional;

/** In-world citizen bodies. Implementations tag each body with (colonyId, citizenId) persistently. */
public interface CitizenBodies {
    /** Spawns a body at a free standing spot near {@code near}. Empty if impossible right now. */
    Optional<BodyId> spawn(WorldKey world, BlockPos near, int colonyId, int citizenId, String displayName);

    boolean isAlive(BodyId body);

    Optional<Vec3> position(BodyId body);

    void moveTo(BodyId body, Vec3 target);

    NavStatus navStatus(BodyId body);

    void setDisplayName(BodyId body, String name);

    void despawn(BodyId body);
}
```

`kernel/port/WorldQuery.java` :

```java
package dev.hycolony.core.kernel.port;

import dev.hycolony.core.kernel.BlockPos;

public interface WorldQuery {
    /** Whether the chunk containing {@code pos} is currently loaded. */
    boolean isLoaded(BlockPos pos);
}
```

`kernel/port/Msg.java` :

```java
package dev.hycolony.core.kernel.port;

import java.util.List;

/** A translatable message: an i18n key and positional parameters. */
public record Msg(String key, List<String> params) {
    public Msg {
        params = List.copyOf(params);
    }

    public static Msg of(String key, String... params) {
        return new Msg(key, List.of(params));
    }
}
```

`kernel/port/Notifier.java` :

```java
package dev.hycolony.core.kernel.port;

import java.util.UUID;

public interface Notifier {
    void send(UUID player, Msg message);
}
```

`kernel/port/PlayerDirectory.java` :

```java
package dev.hycolony.core.kernel.port;

import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.WorldKey;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface PlayerDirectory {
    boolean isOnline(UUID player);

    Optional<BlockPos> position(UUID player);

    Collection<UUID> onlineIn(WorldKey world);
}
```

`colony/ui/FoundColonyView.java` :

```java
package dev.hycolony.core.colony.ui;

public record FoundColonyView(String suggestedName) {}
```

`colony/ui/CitizenRow.java` :

```java
package dev.hycolony.core.colony.ui;

import dev.hycolony.core.citizen.Gender;

/** {@code status} is an i18n key suffix: "idle", "wandering" or "absent". */
public record CitizenRow(String name, Gender gender, String status) {}
```

`colony/ui/TownHallView.java` :

```java
package dev.hycolony.core.colony.ui;

import java.util.List;

public record TownHallView(int colonyId, String colonyName, String ownerName, int day, List<CitizenRow> citizens,
                           boolean canRename) {
    public TownHallView {
        citizens = List.copyOf(citizens);
    }
}
```

`colony/ui/UiPort.java` :

```java
package dev.hycolony.core.colony.ui;

import java.util.UUID;

/** Renders core view models. Player actions come back through ColonyManager methods. */
public interface UiPort {
    void showFoundColony(UUID player, FoundColonyView view);

    void showTownHall(UUID player, TownHallView view);

    void close(UUID player);
}
```

- [ ] **Step 2 : écrire les fakes de test**

`core/src/test/java/dev/hycolony/core/testing/FakeClock.java` :

```java
package dev.hycolony.core.testing;

import dev.hycolony.core.kernel.port.GameClock;

public final class FakeClock implements GameClock {
    public long tick;
    public boolean daytime = true;

    @Override public long currentTick() { return tick; }
    @Override public boolean isDaytime() { return daytime; }
}
```

`core/src/test/java/dev/hycolony/core/testing/FakeBodies.java` :

```java
package dev.hycolony.core.testing;

import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.Vec3;
import dev.hycolony.core.kernel.WorldKey;
import dev.hycolony.core.kernel.port.BodyId;
import dev.hycolony.core.kernel.port.CitizenBodies;
import dev.hycolony.core.kernel.port.NavStatus;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class FakeBodies implements CitizenBodies {
    public static final class Body {
        public final int colonyId, citizenId;
        public String name;
        public Vec3 position;
        public Vec3 target;
        public NavStatus status = NavStatus.IDLE;
        public boolean alive = true;

        Body(int colonyId, int citizenId, String name, Vec3 position) {
            this.colonyId = colonyId;
            this.citizenId = citizenId;
            this.name = name;
            this.position = position;
        }
    }

    public final Map<BodyId, Body> bodies = new LinkedHashMap<>();
    public boolean refuseSpawn;
    private long next = 1;

    /** Simulates a body that already exists in the world (e.g. loaded from a chunk). */
    public BodyId existing(int colonyId, int citizenId, Vec3 pos) {
        BodyId id = new BodyId(next++);
        bodies.put(id, new Body(colonyId, citizenId, "", pos));
        return id;
    }

    public long aliveCount() {
        return bodies.values().stream().filter(b -> b.alive).count();
    }

    @Override
    public Optional<BodyId> spawn(WorldKey world, BlockPos near, int colonyId, int citizenId, String displayName) {
        if (refuseSpawn) {
            return Optional.empty();
        }
        BodyId id = new BodyId(next++);
        bodies.put(id, new Body(colonyId, citizenId, displayName, Vec3.center(near)));
        return Optional.of(id);
    }

    @Override public boolean isAlive(BodyId body) { Body b = bodies.get(body); return b != null && b.alive; }
    @Override public Optional<Vec3> position(BodyId body) { return isAlive(body) ? Optional.of(bodies.get(body).position) : Optional.empty(); }
    @Override public void moveTo(BodyId body, Vec3 target) { Body b = bodies.get(body); b.target = target; b.status = NavStatus.MOVING; }
    @Override public NavStatus navStatus(BodyId body) { return bodies.get(body).status; }
    @Override public void setDisplayName(BodyId body, String name) { bodies.get(body).name = name; }
    @Override public void despawn(BodyId body) { Body b = bodies.get(body); if (b != null) b.alive = false; }
}
```

`core/src/test/java/dev/hycolony/core/testing/FakeWorld.java` :

```java
package dev.hycolony.core.testing;

import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.port.WorldQuery;

public final class FakeWorld implements WorldQuery {
    public boolean loaded = true;

    @Override public boolean isLoaded(BlockPos pos) { return loaded; }
}
```

`core/src/test/java/dev/hycolony/core/testing/FakeNotifier.java` :

```java
package dev.hycolony.core.testing;

import dev.hycolony.core.kernel.port.Msg;
import dev.hycolony.core.kernel.port.Notifier;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class FakeNotifier implements Notifier {
    public record Sent(UUID player, Msg msg) {}

    public final List<Sent> sent = new ArrayList<>();

    @Override public void send(UUID player, Msg message) { sent.add(new Sent(player, message)); }
}
```

`core/src/test/java/dev/hycolony/core/testing/FakePlayers.java` :

```java
package dev.hycolony.core.testing;

import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.WorldKey;
import dev.hycolony.core.kernel.port.PlayerDirectory;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class FakePlayers implements PlayerDirectory {
    public final Map<UUID, BlockPos> online = new LinkedHashMap<>();

    @Override public boolean isOnline(UUID player) { return online.containsKey(player); }
    @Override public Optional<BlockPos> position(UUID player) { return Optional.ofNullable(online.get(player)); }
    @Override public Collection<UUID> onlineIn(WorldKey world) { return List.copyOf(online.keySet()); }
}
```

`core/src/test/java/dev/hycolony/core/testing/FakeUi.java` :

```java
package dev.hycolony.core.testing;

import dev.hycolony.core.colony.ui.FoundColonyView;
import dev.hycolony.core.colony.ui.TownHallView;
import dev.hycolony.core.colony.ui.UiPort;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class FakeUi implements UiPort {
    public final Map<UUID, Object> shown = new LinkedHashMap<>();

    @Override public void showFoundColony(UUID player, FoundColonyView view) { shown.put(player, view); }
    @Override public void showTownHall(UUID player, TownHallView view) { shown.put(player, view); }
    @Override public void close(UUID player) { shown.remove(player); }
}
```

`core/src/test/java/dev/hycolony/core/testing/TestContexts.java` :

```java
package dev.hycolony.core.testing;

import dev.hycolony.core.building.BuildingTypes;
import dev.hycolony.core.citizen.CitizenNames;
import dev.hycolony.core.colony.ColonyContext;
import dev.hycolony.core.kernel.WorldKey;
import dev.hycolony.core.kernel.config.ColonyConfig;
import dev.hycolony.core.kernel.event.EventBus;
import java.util.Random;

/** A fully faked colony context. Fields are public so tests can steer the fakes. */
public final class TestContexts {
    public final FakeClock clock = new FakeClock();
    public final FakeBodies bodies = new FakeBodies();
    public final FakeWorld world = new FakeWorld();
    public final FakeNotifier notifier = new FakeNotifier();
    public final FakePlayers players = new FakePlayers();
    public final FakeUi ui = new FakeUi();
    public final EventBus bus = new EventBus();
    public ColonyConfig config = ColonyConfig.defaults();

    public ColonyContext context() {
        return new ColonyContext(new WorldKey("world"), config, clock, bodies, world, notifier, ui, players,
                BuildingTypes.defaults(), CitizenNames.loadDefault(), new Random(1234), bus);
    }
}
```

- [ ] **Step 3 : écrire les tests de `Colony` qui échouent**

`core/src/test/java/dev/hycolony/core/colony/ColonyTest.java` :

```java
package dev.hycolony.core.colony;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.testing.TestContexts;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ColonyTest {
    private final TestContexts t = new TestContexts();
    private final UUID owner = UUID.randomUUID();
    private final TerritoryIndex territory = new TerritoryIndex();

    private Colony colony() {
        BlockPos center = new BlockPos(0, 64, 0);
        territory.claimSquare(1, ClaimCell.of(center), 4);
        return new Colony(t.context(), territory, 1, "Test", center, Permissions.createDefault(owner, "Alice"));
    }

    private void run(Colony c, int ticks) {
        for (int i = 0; i < ticks; i++) {
            t.clock.tick++;
            c.tick();
        }
    }

    @Test
    void inactiveWhenNobodyOnline() {
        Colony c = colony();
        run(c, 200);
        assertEquals(ColonyState.INACTIVE, c.state());
    }

    @Test
    void activeWhenAPlayerStandsInTerritory() {
        Colony c = colony();
        t.players.online.put(UUID.randomUUID(), new BlockPos(10, 64, 10));
        run(c, 200);
        assertEquals(ColonyState.ACTIVE, c.state());
    }

    @Test
    void unloadedWhenMemberOnlineButFarAndChunkUnloaded() {
        Colony c = colony();
        t.world.loaded = false;
        t.players.online.put(owner, new BlockPos(10_000, 64, 0));
        run(c, 200);
        assertEquals(ColonyState.UNLOADED, c.state());
    }

    @Test
    void activeWhenMemberOnlineAndTownHallChunkLoaded() {
        Colony c = colony();
        t.players.online.put(owner, new BlockPos(10_000, 64, 0));
        run(c, 200);
        assertEquals(ColonyState.ACTIVE, c.state());
    }

    @Test
    void dawnIncrementsDayAndPostsEvents() {
        Colony c = colony();
        List<Object> events = new ArrayList<>();
        t.bus.subscribe(ColonyEvents.DayStarted.class, events::add);
        t.bus.subscribe(ColonyEvents.NightFell.class, events::add);
        t.players.online.put(owner, new BlockPos(0, 64, 0));
        run(c, 200);
        t.clock.daytime = false;
        run(c, 40);
        t.clock.daytime = true;
        run(c, 40);
        assertEquals(1, c.day());
        assertEquals(2, events.size());
        assertTrue(c.isDirty());
    }

    @Test
    void exceptionSuspendsColonyForFiveMinutes() {
        Colony c = colony();
        t.players.online.put(owner, new BlockPos(0, 64, 0));
        c.citizens().failNextTickForTest();
        run(c, 600);
        long suspendedAt = t.clock.tick;
        assertTrue(c.isSuspended());
        t.clock.tick = suspendedAt + Colony.EXCEPTION_SUSPEND_TICKS + 1;
        c.tick();
        assertTrue(!c.isSuspended());
    }

    @Test
    void eventLogIsBoundedTo100() {
        EventLog log = new EventLog();
        for (int i = 0; i < 150; i++) {
            log.add("x", i);
        }
        assertEquals(100, log.entries().size());
        assertEquals(50, log.entries().getFirst().day());
    }
}
```

- [ ] **Step 4 : lancer les tests pour vérifier qu'ils échouent**

Run : `./gradlew :core:test --tests "*ColonyTest"`
Attendu : erreur de compilation.

- [ ] **Step 5 : implémenter `ColonyState`, `ColonyContext`, `EventLog` et `ColonyEvents`**

`colony/ColonyState.java` :

```java
package dev.hycolony.core.colony;

import dev.hycolony.core.kernel.ai.IState;

public enum ColonyState implements IState { ACTIVE, UNLOADED, INACTIVE }
```

`colony/ColonyContext.java` :

```java
package dev.hycolony.core.colony;

import dev.hycolony.core.building.BuildingRegistry;
import dev.hycolony.core.citizen.CitizenNames;
import dev.hycolony.core.colony.ui.UiPort;
import dev.hycolony.core.kernel.WorldKey;
import dev.hycolony.core.kernel.config.ColonyConfig;
import dev.hycolony.core.kernel.event.EventBus;
import dev.hycolony.core.kernel.port.CitizenBodies;
import dev.hycolony.core.kernel.port.GameClock;
import dev.hycolony.core.kernel.port.Notifier;
import dev.hycolony.core.kernel.port.PlayerDirectory;
import dev.hycolony.core.kernel.port.WorldQuery;
import java.util.random.RandomGenerator;

/** Everything a colony needs from the outside world, for one game world. */
public record ColonyContext(
        WorldKey world,
        ColonyConfig config,
        GameClock clock,
        CitizenBodies bodies,
        WorldQuery worldQuery,
        Notifier notifier,
        UiPort ui,
        PlayerDirectory players,
        BuildingRegistry buildingTypes,
        CitizenNames names,
        RandomGenerator random,
        EventBus bus) {}
```

`colony/EventLog.java` :

```java
package dev.hycolony.core.colony;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/** Colony history shown to players, bounded like MineColonies' EventDescriptionManager. */
public final class EventLog {
    public static final int MAX_ENTRIES = 100;

    public record Entry(String type, int day, List<String> params) {
        public Entry {
            params = List.copyOf(params);
        }
    }

    private final Deque<Entry> entries = new ArrayDeque<>();

    public void add(String type, int day, String... params) {
        restore(new Entry(type, day, List.of(params)));
    }

    public void restore(Entry entry) {
        if (entries.size() == MAX_ENTRIES) {
            entries.removeFirst();
        }
        entries.addLast(entry);
    }

    public List<Entry> entries() {
        return List.copyOf(entries);
    }
}
```

`colony/ColonyEvents.java` :

```java
package dev.hycolony.core.colony;

import dev.hycolony.core.building.Building;

/** Colony-level events posted on the world EventBus. */
public final class ColonyEvents {
    private ColonyEvents() {}

    public record ColonyCreated(Colony colony) {}
    public record ColonyDeleted(int colonyId) {}
    public record BuildingPlaced(Colony colony, Building building) {}
    public record BuildingRemoved(Colony colony, Building building) {}
    public record DayStarted(Colony colony) {}
    public record NightFell(Colony colony) {}
}
```

- [ ] **Step 6 : implémenter un `CitizenManager` minimal, complété en Task 9**

`citizen/CitizenManager.java` :

```java
package dev.hycolony.core.citizen;

import dev.hycolony.core.colony.Colony;

/** Citizens of one colony. Minimal version; spawning, respawn and bodies arrive in Task 9. */
public final class CitizenManager {
    private final Colony colony;
    private boolean failNextTick;

    public CitizenManager(Colony colony) {
        this.colony = colony;
    }

    /** Every 60 ticks while ACTIVE. */
    public void tickData() {
        if (failNextTick) {
            failNextTick = false;
            throw new IllegalStateException("test failure");
        }
    }

    /** Slow tick (every 500 ticks) while ACTIVE. */
    public void onColonyTick() {}

    /** Every core tick: citizens' AI. */
    public void tickAi() {}

    /** Test hook: next tickData throws. */
    public void failNextTickForTest() {
        failNextTick = true;
    }
}
```

- [ ] **Step 7 : implémenter `Colony`**

`colony/Colony.java` :

```java
package dev.hycolony.core.colony;

import dev.hycolony.core.building.BuildingManager;
import dev.hycolony.core.citizen.CitizenManager;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.ai.AITarget;
import dev.hycolony.core.kernel.ai.TickRateStateMachine;
import java.util.OptionalInt;
import java.util.UUID;

/** One colony. Ticked 20 times per second on its world thread. */
public final class Colony {
    public static final int UPDATE_STATE_INTERVAL = 100;
    public static final int CITIZEN_DATA_INTERVAL = 60;
    public static final int DAYTIME_INTERVAL = 20;
    public static final int SLOW_TICK = 500;
    /** MineColonies delays a colony for 5 minutes after an exception. */
    public static final int EXCEPTION_SUSPEND_TICKS = 5 * 60 * 20;

    private static final System.Logger LOG = System.getLogger(Colony.class.getName());

    private final ColonyContext ctx;
    private final TerritoryIndex territory;
    private final int id;
    private String name;
    private final BlockPos center;
    private final Permissions permissions;
    private final BuildingManager buildings = new BuildingManager();
    private final CitizenManager citizens;
    private final EventLog log = new EventLog();
    private final TickRateStateMachine<ColonyState> machine;
    private int day;
    private boolean wasDaytime;
    private boolean dirty;
    private long suspendedUntilTick = Long.MIN_VALUE;

    public Colony(ColonyContext ctx, TerritoryIndex territory, int id, String name, BlockPos center, Permissions permissions) {
        this.ctx = ctx;
        this.territory = territory;
        this.id = id;
        this.name = name;
        this.center = center;
        this.permissions = permissions;
        this.citizens = new CitizenManager(this);
        this.wasDaytime = ctx.clock().isDaytime();
        this.machine = new TickRateStateMachine<>(ColonyState.INACTIVE, this::onException);
        for (ColonyState s : ColonyState.values()) {
            machine.addTransition(new AITarget<>(s, this::updateState, UPDATE_STATE_INTERVAL));
        }
        machine.addTransition(new AITarget<>(ColonyState.ACTIVE, () -> { citizens.tickData(); return null; }, CITIZEN_DATA_INTERVAL));
        machine.addTransition(new AITarget<>(ColonyState.ACTIVE, () -> { checkDayTime(); return null; }, DAYTIME_INTERVAL));
        machine.addTransition(new AITarget<>(ColonyState.ACTIVE, () -> { slowTick(); return null; }, SLOW_TICK));
    }

    public void tick() {
        if (isSuspended()) {
            return;
        }
        try {
            machine.tick();
            citizens.tickAi();
        } catch (RuntimeException e) {
            onException(e);
        }
    }

    public boolean isSuspended() {
        return ctx.clock().currentTick() < suspendedUntilTick;
    }

    private void onException(RuntimeException e) {
        LOG.log(System.Logger.Level.ERROR, "Colony " + id + " (" + name + ") failed, suspending for 5 minutes", e);
        suspendedUntilTick = ctx.clock().currentTick() + EXCEPTION_SUSPEND_TICKS;
    }

    /** MineColonies-equivalent activity rule (spec § 3.2). */
    private ColonyState updateState() {
        boolean playerInside = false;
        boolean memberOnline = false;
        for (UUID player : ctx.players().onlineIn(ctx.world())) {
            if (ctx.players().position(player).map(this::contains).orElse(false)) {
                playerInside = true;
            }
            if (permissions.isMember(player)) {
                memberOnline = true;
            }
        }
        if (playerInside || (memberOnline && ctx.worldQuery().isLoaded(center))) {
            return ColonyState.ACTIVE;
        }
        return memberOnline ? ColonyState.UNLOADED : ColonyState.INACTIVE;
    }

    private void checkDayTime() {
        boolean daytime = ctx.clock().isDaytime();
        if (daytime && !wasDaytime) {
            day++;
            markDirty();
            ctx.bus().post(new ColonyEvents.DayStarted(this));
        } else if (!daytime && wasDaytime) {
            ctx.bus().post(new ColonyEvents.NightFell(this));
        }
        wasDaytime = daytime;
    }

    private void slowTick() {
        buildings.onColonyTick();
        citizens.onColonyTick();
    }

    public boolean contains(BlockPos pos) {
        OptionalInt owner = territory.colonyAt(pos);
        return owner.isPresent() && owner.getAsInt() == id;
    }

    public int id() { return id; }
    public String name() { return name; }
    public void setName(String name) { this.name = name; markDirty(); }
    public BlockPos center() { return center; }
    public Permissions permissions() { return permissions; }
    public BuildingManager buildings() { return buildings; }
    public CitizenManager citizens() { return citizens; }
    public EventLog log() { return log; }
    public int day() { return day; }
    public void setDay(int day) { this.day = day; }
    public ColonyState state() { return machine.getState(); }
    public ColonyContext context() { return ctx; }
    public boolean isDirty() { return dirty; }
    public void markDirty() { dirty = true; }
    public void clearDirty() { dirty = false; }
}
```

- [ ] **Step 8 : lancer les tests pour vérifier qu'ils passent**

Run : `./gradlew :core:test`
Attendu : PASS, y compris les 7 tests de `ColonyTest` et `ArchitectureTest`. Le kernel ne dépend toujours de rien : les ports n'importent que `kernel`.

- [ ] **Step 9 : commit**

```bash
git add core
git commit -m "feat(core): ports, colony context, event log and colony state machine

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 9 : `CitizenManager` complet et `CitizenAI` (errance)

Portage de `CitizenManager.onColonyTick` : minuteurs `respawnInterval = 600` et `citizenRespawnTimer = 6000`, apparition initiale décrémentée de `500 + 60 × niveauHDV`, rééquilibrage des genres, identifiants réutilisés à partir de 1. Portage aussi de `initForNewCivilian`, avec un plafond de niveau égal à `(int) bonheur × 2`, au minimum 5 pour les citoyens initiaux. Tant que le système de bonheur n'existe pas (sous-projet 4), le bonheur vaut **5.5**, la valeur de MineColonies pour une colonie vide.

**Files :**
- Modify : `core/src/main/java/dev/hycolony/core/citizen/CitizenManager.java` (remplacé entièrement)
- Create : `core/src/main/java/dev/hycolony/core/citizen/{CitizenAI,CitizenState,CitizenSpawned}.java`
- Test : `core/src/test/java/dev/hycolony/core/citizen/{CitizenManagerTest,CitizenAITest}.java`

**Interfaces :**
- Consumes : `Colony` (`context()`, `buildings().townHall()`, `log()`, `day()`, `id()`, `markDirty()`), `CitizenBodies`, `WorldQuery`, `CitizenNames`, `Skills.initRandom`.
- Produces :
  - `record CitizenSpawned(Colony colony, CitizenData citizen)` ;
  - `enum CitizenState implements IState { IDLE, WANDERING }` ;
  - `final class CitizenAI(Colony, CitizenData, BodyId)` avec `tick()` et `CitizenState state()` ;
  - dans `CitizenManager` :
    - constantes `RESPAWN_CHECK_TICKS = 6000`, `INITIAL_SPAWN_RESET = 1200`, `INITIAL_SPAWN_FIRST = 600`, `PLACEHOLDER_HAPPINESS = 5.5` ;
    - `Collection<CitizenData> all()`, `Optional<CitizenData> get(int)`, `Optional<BodyId> bodyOf(int)`, `Optional<CitizenState> aiState(int)` ;
    - `void onBodyLoaded(BodyId, int citizenId)`, `void onBodyUnloaded(BodyId)`, `void despawnAll()`, `void restore(CitizenData)` ;
    - `tickData()`, `onColonyTick()`, `tickAi()` et `failNextTickForTest()`, comme en Task 8.

- [ ] **Step 1 : écrire les tests qui échouent**

`core/src/test/java/dev/hycolony/core/citizen/CitizenManagerTest.java` :

```java
package dev.hycolony.core.citizen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.building.BuildingTypes;
import dev.hycolony.core.colony.ClaimCell;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.Permissions;
import dev.hycolony.core.colony.TerritoryIndex;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.Vec3;
import dev.hycolony.core.kernel.port.BodyId;
import dev.hycolony.core.testing.TestContexts;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CitizenManagerTest {
    private final TestContexts t = new TestContexts();
    private final BlockPos hall = new BlockPos(0, 64, 0);

    private Colony colonyWithTownHall() {
        TerritoryIndex territory = new TerritoryIndex();
        territory.claimSquare(1, ClaimCell.of(hall), 4);
        Colony c = new Colony(t.context(), territory, 1, "Test", hall, Permissions.createDefault(UUID.randomUUID(), "A"));
        c.buildings().add(Building.create(BuildingTypes.TOWN_HALL, hall, 0));
        return c;
    }

    /** Slow ticks as the colony would run them. */
    private void slowTicks(Colony c, int n) {
        for (int i = 0; i < n; i++) {
            c.citizens().onColonyTick();
        }
    }

    @Test
    void initialCitizensFollowMineColoniesTimers() {
        Colony c = colonyWithTownHall();
        slowTicks(c, 1); // 600 - 500 = 100 > 0
        assertEquals(0, c.citizens().all().size());
        slowTicks(c, 1); // -400 -> spawn, reset to 1200
        assertEquals(1, c.citizens().all().size());
        slowTicks(c, 2); // 700, 200
        assertEquals(1, c.citizens().all().size());
        slowTicks(c, 1); // -300 -> spawn
        assertEquals(2, c.citizens().all().size());
        slowTicks(c, 30);
        assertEquals(4, c.citizens().all().size()); // capped at initialCitizenAmount
        assertEquals(4, t.bodies.aliveCount());
    }

    @Test
    void noSpawnWithoutTownHall() {
        TerritoryIndex territory = new TerritoryIndex();
        Colony c = new Colony(t.context(), territory, 1, "T", hall, Permissions.createDefault(UUID.randomUUID(), "A"));
        slowTicks(c, 20);
        assertEquals(0, c.citizens().all().size());
    }

    @Test
    void gendersAreBalancedAfterFirstCitizen() {
        Colony c = colonyWithTownHall();
        slowTicks(c, 40);
        long females = c.citizens().all().stream().filter(d -> d.gender() == Gender.FEMALE).count();
        assertEquals(2, females);
    }

    @Test
    void idsStartAtOneAndSkillsRespectInitialCap() {
        Colony c = colonyWithTownHall();
        slowTicks(c, 40);
        assertEquals(java.util.List.of(1, 2, 3, 4), c.citizens().all().stream().map(CitizenData::id).toList());
        for (CitizenData d : c.citizens().all()) {
            for (Skill s : Skill.values()) {
                assertTrue(d.skills().level(s) >= 1 && d.skills().level(s) <= 9); // cap (int)5.5*2 = 10
            }
            assertFalse(d.name().isBlank());
            assertEquals(CitizenData.MAX_SATURATION, d.saturation());
        }
    }

    @Test
    void deadBodyRespawnsOnlyAfterTimerAndIfLoaded() {
        Colony c = colonyWithTownHall();
        slowTicks(c, 2);
        CitizenData d = c.citizens().all().iterator().next();
        BodyId body = c.citizens().bodyOf(d.id()).orElseThrow();
        t.bodies.despawn(body);
        t.world.loaded = false;
        slowTicks(c, 12); // respawn timer (6000) elapses, but chunk unloaded
        assertTrue(c.citizens().bodyOf(d.id()).map(b -> !t.bodies.isAlive(b)).orElse(true));
        t.world.loaded = true;
        slowTicks(c, 13);
        assertTrue(t.bodies.isAlive(c.citizens().bodyOf(d.id()).orElseThrow()));
    }

    @Test
    void bodyLoadedTwiceKeepsFirstAndDespawnsSecond() {
        Colony c = colonyWithTownHall();
        CitizenData d = new CitizenData(7);
        c.citizens().restore(d);
        BodyId first = t.bodies.existing(1, 7, new Vec3(1, 64, 1));
        BodyId second = t.bodies.existing(1, 7, new Vec3(2, 64, 2));
        c.citizens().onBodyLoaded(first, 7);
        c.citizens().onBodyLoaded(second, 7);
        assertEquals(first, c.citizens().bodyOf(7).orElseThrow());
        assertTrue(t.bodies.isAlive(first));
        assertFalse(t.bodies.isAlive(second));
    }

    @Test
    void bodyOfUnknownCitizenIsDespawned() {
        Colony c = colonyWithTownHall();
        BodyId stray = t.bodies.existing(1, 99, new Vec3(0, 64, 0));
        c.citizens().onBodyLoaded(stray, 99);
        assertFalse(t.bodies.isAlive(stray));
    }

    @Test
    void tickDataRecordsLastPosition() {
        Colony c = colonyWithTownHall();
        slowTicks(c, 2);
        CitizenData d = c.citizens().all().iterator().next();
        BodyId body = c.citizens().bodyOf(d.id()).orElseThrow();
        t.bodies.bodies.get(body).position = new Vec3(5, 64, 5);
        c.citizens().tickData();
        assertEquals(new Vec3(5, 64, 5), d.lastPosition());
    }
}
```

`core/src/test/java/dev/hycolony/core/citizen/CitizenAITest.java` :

```java
package dev.hycolony.core.citizen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.building.BuildingTypes;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.Permissions;
import dev.hycolony.core.colony.TerritoryIndex;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.Vec3;
import dev.hycolony.core.kernel.port.BodyId;
import dev.hycolony.core.kernel.port.NavStatus;
import dev.hycolony.core.testing.FakeBodies;
import dev.hycolony.core.testing.TestContexts;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CitizenAITest {
    private final TestContexts t = new TestContexts();

    @Test
    void wandersNearTownHallThenReturnsToIdleOnArrival() {
        BlockPos hall = new BlockPos(100, 64, 100);
        Colony c = new Colony(t.context(), new TerritoryIndex(), 1, "T", hall, Permissions.createDefault(UUID.randomUUID(), "A"));
        c.buildings().add(Building.create(BuildingTypes.TOWN_HALL, hall, 0));
        CitizenData d = new CitizenData(1);
        BodyId body = t.bodies.existing(1, 1, new Vec3(100, 64, 100));
        CitizenAI ai = new CitizenAI(c, d, body);

        for (int i = 0; i < 420 && ai.state() == CitizenState.IDLE; i++) {
            ai.tick();
        }
        assertEquals(CitizenState.WANDERING, ai.state());
        FakeBodies.Body b = t.bodies.bodies.get(body);
        assertNotNull(b.target);
        assertTrue(Math.abs(b.target.x() - 100.5) <= 10 && Math.abs(b.target.z() - 100.5) <= 10);

        b.status = NavStatus.ARRIVED;
        for (int i = 0; i < 10; i++) {
            ai.tick();
        }
        assertEquals(CitizenState.IDLE, ai.state());
    }

    @Test
    void wanderTimesOutAfter30Seconds() {
        BlockPos hall = new BlockPos(0, 64, 0);
        Colony c = new Colony(t.context(), new TerritoryIndex(), 1, "T", hall, Permissions.createDefault(UUID.randomUUID(), "A"));
        BodyId body = t.bodies.existing(1, 1, new Vec3(0, 64, 0));
        CitizenAI ai = new CitizenAI(c, new CitizenData(1), body);
        for (int i = 0; i < 420 && ai.state() == CitizenState.IDLE; i++) {
            ai.tick();
        }
        assertEquals(CitizenState.WANDERING, ai.state()); // no town hall: anchor = own position
        for (int i = 0; i < 610; i++) {
            ai.tick();
        }
        assertEquals(CitizenState.IDLE, ai.state());
    }
}
```

- [ ] **Step 2 : lancer les tests pour vérifier qu'ils échouent**

Run : `./gradlew :core:test --tests "dev.hycolony.core.citizen.*"`
Attendu : erreur de compilation (`restore`, `bodyOf`, `CitizenAI`… absents).

- [ ] **Step 3 : implémenter**

`citizen/CitizenSpawned.java` :

```java
package dev.hycolony.core.citizen;

import dev.hycolony.core.colony.Colony;

public record CitizenSpawned(Colony colony, CitizenData citizen) {}
```

`citizen/CitizenState.java` :

```java
package dev.hycolony.core.citizen;

import dev.hycolony.core.kernel.ai.IState;

/** SP0 subset of MineColonies' CitizenAIState. */
public enum CitizenState implements IState { IDLE, WANDERING }
```

`citizen/CitizenAI.java` :

```java
package dev.hycolony.core.citizen;

import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.Vec3;
import dev.hycolony.core.kernel.ai.AITarget;
import dev.hycolony.core.kernel.ai.TickRateStateMachine;
import dev.hycolony.core.kernel.port.BodyId;
import dev.hycolony.core.kernel.port.CitizenBodies;
import dev.hycolony.core.kernel.port.NavStatus;
import java.util.random.RandomGenerator;

/** Top-level citizen AI. SP0: idle, then wander within 10 blocks of the town hall. */
public final class CitizenAI {
    private static final System.Logger LOG = System.getLogger(CitizenAI.class.getName());
    private static final int WANDER_RADIUS = 10;
    private static final int IDLE_MIN_TICKS = 200, IDLE_MAX_TICKS = 400;
    private static final int WANDER_TIMEOUT_TICKS = 600;

    private final Colony colony;
    private final CitizenData data;
    private final BodyId body;
    private final CitizenBodies bodies;
    private final RandomGenerator random;
    private final TickRateStateMachine<CitizenState> machine;
    private int idleTicksLeft;
    private int wanderTicks;

    public CitizenAI(Colony colony, CitizenData data, BodyId body) {
        this.colony = colony;
        this.data = data;
        this.body = body;
        this.bodies = colony.context().bodies();
        this.random = colony.context().random();
        this.idleTicksLeft = nextIdle();
        this.machine = new TickRateStateMachine<>(CitizenState.IDLE, this::onException);
        machine.addTransition(new AITarget<>(CitizenState.IDLE, this::idle, 20));
        machine.addTransition(new AITarget<>(CitizenState.WANDERING, this::wander, 5));
    }

    public void tick() {
        machine.tick();
    }

    public CitizenState state() {
        return machine.getState();
    }

    private void onException(RuntimeException e) {
        LOG.log(System.Logger.Level.WARNING, "Citizen AI failed for " + data.name(), e);
        machine.reset();
    }

    private CitizenState idle() {
        idleTicksLeft -= 20;
        if (idleTicksLeft > 0) {
            return null;
        }
        Vec3 here = bodies.position(body).orElse(null);
        if (here == null) {
            return null;
        }
        BlockPos anchor = colony.buildings().townHall().map(b -> b.position()).orElse(here.toBlockPos());
        int dx = random.nextInt(2 * WANDER_RADIUS + 1) - WANDER_RADIUS;
        int dz = random.nextInt(2 * WANDER_RADIUS + 1) - WANDER_RADIUS;
        Vec3 target = new Vec3(anchor.x() + dx + 0.5, here.y(), anchor.z() + dz + 0.5);
        bodies.moveTo(body, target);
        wanderTicks = 0;
        return CitizenState.WANDERING;
    }

    private CitizenState wander() {
        wanderTicks += 5;
        NavStatus status = bodies.navStatus(body);
        boolean done = status == NavStatus.ARRIVED || status == NavStatus.BLOCKED || status == NavStatus.FAILED;
        if (done || wanderTicks >= WANDER_TIMEOUT_TICKS) {
            idleTicksLeft = nextIdle();
            return CitizenState.IDLE;
        }
        return null;
    }

    private int nextIdle() {
        return IDLE_MIN_TICKS + random.nextInt(IDLE_MAX_TICKS - IDLE_MIN_TICKS + 1);
    }
}
```

`citizen/CitizenManager.java` (remplace la version minimale) :

```java
package dev.hycolony.core.citizen;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.ColonyContext;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.port.BodyId;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/** Port of MineColonies' CitizenManager for SP0: initial citizens, respawn, body binding, AI. */
public final class CitizenManager {
    public static final int RESPAWN_CHECK_TICKS = 5 * 60 * 20;
    public static final int INITIAL_SPAWN_FIRST = 30 * 20;
    public static final int INITIAL_SPAWN_RESET = 60 * 20;
    /** MineColonies' overall happiness of an empty colony; replaced by the happiness system in SP4. */
    public static final double PLACEHOLDER_HAPPINESS = 5.5;

    private final Colony colony;
    private final Map<Integer, CitizenData> citizens = new TreeMap<>();
    private final Map<Integer, BodyId> bodies = new HashMap<>();
    private final Map<Integer, CitizenAI> ais = new HashMap<>();
    private int respawnInterval = INITIAL_SPAWN_FIRST;
    private int citizenRespawnTimer = RESPAWN_CHECK_TICKS;
    private boolean failNextTick;

    public CitizenManager(Colony colony) {
        this.colony = colony;
    }

    private ColonyContext ctx() {
        return colony.context();
    }

    public Collection<CitizenData> all() { return Collections.unmodifiableCollection(citizens.values()); }
    public Optional<CitizenData> get(int id) { return Optional.ofNullable(citizens.get(id)); }
    public Optional<BodyId> bodyOf(int id) { return Optional.ofNullable(bodies.get(id)); }
    public Optional<CitizenState> aiState(int id) { return Optional.ofNullable(ais.get(id)).map(CitizenAI::state); }

    /** Adds a citizen loaded from disk. */
    public void restore(CitizenData data) {
        citizens.put(data.id(), data);
    }

    /** Every 60 ticks while ACTIVE: record positions. */
    public void tickData() {
        if (failNextTick) {
            failNextTick = false;
            throw new IllegalStateException("test failure");
        }
        for (Map.Entry<Integer, BodyId> e : bodies.entrySet()) {
            ctx().bodies().position(e.getValue()).ifPresent(p -> citizens.get(e.getKey()).setLastPosition(p));
        }
        if (!bodies.isEmpty()) {
            colony.markDirty();
        }
    }

    /** Every core tick: AI of citizens whose body is alive. */
    public void tickAi() {
        for (Map.Entry<Integer, CitizenAI> e : ais.entrySet()) {
            if (ctx().bodies().isAlive(bodies.get(e.getKey()))) {
                e.getValue().tick();
            }
        }
    }

    /** Slow tick (500 ticks) while ACTIVE. Mirrors MineColonies CitizenManager.onColonyTick. */
    public void onColonyTick() {
        Optional<Building> townHall = colony.buildings().townHall();
        if (townHall.isEmpty()) {
            return;
        }
        if ((citizenRespawnTimer -= 500) < 0) {
            citizenRespawnTimer = RESPAWN_CHECK_TICKS;
            citizens.values().forEach(this::updateBodyIfNecessary);
        }
        if (citizens.size() < ctx().config().initialCitizenAmount()) {
            respawnInterval -= 500 + 60 * townHall.get().level();
            if (respawnInterval <= 0) {
                respawnInterval = INITIAL_SPAWN_RESET;
                spawnInitialCitizen(townHall.get().position());
            }
        }
    }

    private void spawnInitialCitizen(BlockPos townHall) {
        int femaleCount = (int) citizens.values().stream().filter(c -> c.gender() == Gender.FEMALE).count();
        CitizenData data = createAndRegister();
        Gender gender;
        if (citizens.size() == 1) {
            gender = ctx().random().nextBoolean() ? Gender.FEMALE : Gender.MALE;
        } else if (femaleCount < (citizens.size() - 1) / 2.0) {
            gender = Gender.FEMALE;
        } else {
            gender = Gender.MALE;
        }
        data.setGender(gender);
        data.setName(ctx().names().generate(ctx().random(), gender));
        spawnBody(data, townHall);
        colony.log().add("citizenSpawned", colony.day(), data.name());
        colony.markDirty();
        ctx().bus().post(new CitizenSpawned(colony, data));
    }

    /** MineColonies createAndRegisterCivilianData + initForNewCivilian. */
    private CitizenData createAndRegister() {
        int id = 1;
        while (citizens.containsKey(id)) {
            id++;
        }
        CitizenData data = new CitizenData(id);
        data.setSaturation(CitizenData.MAX_SATURATION);
        int levelCap = (int) PLACEHOLDER_HAPPINESS * 2;
        if (citizens.size() < ctx().config().initialCitizenAmount()) {
            levelCap = Math.max(5, levelCap);
        }
        data.setSkills(Skills.initRandom(levelCap, ctx().random()));
        citizens.put(id, data);
        return data;
    }

    private void updateBodyIfNecessary(CitizenData data) {
        BodyId body = bodies.get(data.id());
        if (body != null && ctx().bodies().isAlive(body)) {
            return;
        }
        BlockPos target = data.respawnPosition() != null ? data.respawnPosition()
                : data.lastPosition() != null ? data.lastPosition().toBlockPos()
                : colony.buildings().townHall().map(Building::position).orElse(colony.center());
        if (!ctx().worldQuery().isLoaded(target)) {
            return;
        }
        spawnBody(data, target);
    }

    private void spawnBody(CitizenData data, BlockPos near) {
        ctx().bodies().spawn(ctx().world(), near, colony.id(), data.id(), data.name())
                .ifPresent(body -> bind(data, body));
    }

    private void bind(CitizenData data, BodyId body) {
        bodies.put(data.id(), body);
        ais.put(data.id(), new CitizenAI(colony, data, body));
    }

    /** A body tagged with this colony was loaded into the world. */
    public void onBodyLoaded(BodyId body, int citizenId) {
        CitizenData data = citizens.get(citizenId);
        BodyId current = bodies.get(citizenId);
        boolean duplicate = current != null && !current.equals(body) && ctx().bodies().isAlive(current);
        if (data == null || duplicate) {
            ctx().bodies().despawn(body);
            return;
        }
        bind(data, body);
    }

    public void onBodyUnloaded(BodyId body) {
        bodies.entrySet().removeIf(e -> {
            if (e.getValue().equals(body)) {
                ais.remove(e.getKey());
                return true;
            }
            return false;
        });
    }

    public void despawnAll() {
        bodies.values().forEach(ctx().bodies()::despawn);
        bodies.clear();
        ais.clear();
    }

    /** Test hook: next tickData throws. */
    public void failNextTickForTest() {
        failNextTick = true;
    }
}
```

- [ ] **Step 4 : lancer les tests pour vérifier qu'ils passent**

Run : `./gradlew :core:test`
Attendu : PASS (tous les tests, y compris `ColonyTest`).

- [ ] **Step 5 : commit**

```bash
git add core
git commit -m "feat(core): citizen manager (initial spawn, respawn, body binding) and wander AI

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 10 : `ColonyManager` (règles de l'hôtel de ville, fondation, protection, rangs, UI)

Portage des règles de `AbstractBlockHut.canPaste` et de la fondation (`ColonyManager.createColony`), telles que décrites dans la spec § 3.2.

**Files :**
- Create : `core/src/main/java/dev/hycolony/core/colony/{HutPlacement,ColonyManager}.java`
- Test : `core/src/test/java/dev/hycolony/core/colony/ColonyManagerTest.java`

**Interfaces :**
- Consumes : tout ce qui précède.
- Produces :
  - `sealed interface HutPlacement`, avec `record Allowed(Colony colony)`, `record FoundNewColony()` et `record Denied(Msg reason)` ;
  - `final class ColonyManager(ColonyContext)` avec :
    - requêtes : `Optional<Colony> colonyAt(BlockPos)`, `Optional<Colony> byId(int)`, `Optional<Colony> ownedBy(UUID)`, `Collection<Colony> all()`, `TerritoryIndex territory()` ;
    - pose et fondation : `HutPlacement checkHutPlacement(UUID player, BlockPos pos, String buildingTypeId)`, `void beginFoundation(UUID player, String playerName, BlockPos pos, int rotation)`, `Optional<Colony> confirmFoundation(UUID player, String name)`, `Optional<BlockPos> cancelFoundation(UUID player)`, `void onPlayerLeft(UUID player)` ;
    - bâtiments et droits : `void placeHut(Colony, String buildingTypeId, BlockPos, int rotation)`, `void onHutRemoved(BlockPos)`, `boolean isAllowed(UUID player, BlockPos pos, Action action)`, `boolean protectionEnabled()` ;
    - gestion : `boolean setRank(UUID actor, int colonyId, UUID target, String targetName, int rankId)`, `void openTownHall(UUID player, BlockPos hutPos)`, `boolean rename(UUID actor, int colonyId, String newName)`, `void deleteColony(int colonyId)` ;
    - boucle : `void tick()`, `void onBodyLoaded(BodyId, int colonyId, int citizenId)`, `void onBodyUnloaded(BodyId, int colonyId)` ;
    - en package-private : `int allocateId()`, `void register(Colony)`, `void reserveId(int)` (utilisés par la persistance en Task 11).
  - Clés i18n utilisées : `hycolony.hut.noTownHall`, `hycolony.hut.tooFar`, `hycolony.colony.alreadyOwner`, `hycolony.colony.tooClose`, `hycolony.permission.placeHuts`, `hycolony.hut.townHallExists`, `hycolony.colony.invalidName`, `hycolony.colony.created`, `hycolony.permission.denied`.

- [ ] **Step 1 : écrire les tests qui échouent**

`core/src/test/java/dev/hycolony/core/colony/ColonyManagerTest.java` :

```java
package dev.hycolony.core.colony;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.building.BuildingTypes;
import dev.hycolony.core.colony.ui.FoundColonyView;
import dev.hycolony.core.colony.ui.TownHallView;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.Vec3;
import dev.hycolony.core.kernel.port.BodyId;
import dev.hycolony.core.testing.TestContexts;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ColonyManagerTest {
    private final TestContexts t = new TestContexts();
    private final ColonyManager manager = new ColonyManager(t.context());
    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();
    private final BlockPos hall = new BlockPos(0, 64, 0);
    private static final String TOWN_HALL = BuildingTypes.TOWN_HALL.id();

    private Colony found(UUID owner, String name, BlockPos pos) {
        manager.beginFoundation(owner, "Owner", pos, 0);
        return manager.confirmFoundation(owner, name).orElseThrow();
    }

    @Test
    void townHallOutsideColoniesStartsFoundationFlow() {
        assertInstanceOf(HutPlacement.FoundNewColony.class, manager.checkHutPlacement(alice, hall, TOWN_HALL));
        manager.beginFoundation(alice, "Alice", hall, 0);
        assertInstanceOf(FoundColonyView.class, t.ui.shown.get(alice));
        assertEquals("Alice's Colony", ((FoundColonyView) t.ui.shown.get(alice)).suggestedName());
        Colony c = manager.confirmFoundation(alice, "Rivendell").orElseThrow();
        assertEquals("Rivendell", c.name());
        assertEquals(81, manager.territory().claimedCount(c.id()));
        assertTrue(c.buildings().townHall().isPresent());
        assertEquals(alice, c.permissions().owner());
        assertEquals("colonyCreated", c.log().entries().getFirst().type());
    }

    @Test
    void otherHutOutsideColonyIsDenied() {
        manager.checkHutPlacement(alice, hall, TOWN_HALL);
        HutPlacement p = manager.checkHutPlacement(alice, hall, "test:other");
        assertEquals("hycolony.hut.noTownHall", ((HutPlacement.Denied) p).reason().key());
        found(alice, "A", hall);
        HutPlacement far = manager.checkHutPlacement(alice, new BlockPos(5000, 64, 0), "test:other");
        assertEquals("hycolony.hut.tooFar", ((HutPlacement.Denied) far).reason().key());
    }

    @Test
    void onePlayerOwnsOneColony() {
        found(alice, "A", hall);
        HutPlacement p = manager.checkHutPlacement(alice, new BlockPos(5000, 64, 0), TOWN_HALL);
        assertEquals("hycolony.colony.alreadyOwner", ((HutPlacement.Denied) p).reason().key());
    }

    @Test
    void newColonyTooCloseIsDenied() {
        found(alice, "A", hall);
        HutPlacement p = manager.checkHutPlacement(bob, new BlockPos(16 * 16, 64, 0), TOWN_HALL);
        assertEquals("hycolony.colony.tooClose", ((HutPlacement.Denied) p).reason().key());
        assertInstanceOf(HutPlacement.FoundNewColony.class, manager.checkHutPlacement(bob, new BlockPos(17 * 16, 64, 0), TOWN_HALL));
    }

    @Test
    void insideColonyNeedsPlaceHutsAndOneTownHall() {
        Colony c = found(alice, "A", hall);
        HutPlacement strangers = manager.checkHutPlacement(bob, hall.offset(5, 0, 5), TOWN_HALL);
        assertEquals("hycolony.permission.placeHuts", ((HutPlacement.Denied) strangers).reason().key());
        HutPlacement second = manager.checkHutPlacement(alice, hall.offset(5, 0, 5), TOWN_HALL);
        assertEquals("hycolony.hut.townHallExists", ((HutPlacement.Denied) second).reason().key());
        manager.onHutRemoved(hall);
        assertTrue(c.buildings().townHall().isEmpty());
        assertTrue(manager.byId(c.id()).isPresent()); // colony persists
        assertInstanceOf(HutPlacement.Allowed.class, manager.checkHutPlacement(alice, hall.offset(5, 0, 5), TOWN_HALL));
    }

    @Test
    void rejectsBlankOrTooLongName() {
        manager.beginFoundation(alice, "Alice", hall, 0);
        assertTrue(manager.confirmFoundation(alice, "   ").isEmpty());
        assertTrue(manager.confirmFoundation(alice, "x".repeat(33)).isEmpty());
        assertEquals("hycolony.colony.invalidName", t.notifier.sent.getLast().msg().key());
        assertTrue(manager.confirmFoundation(alice, "  Ok  ").isPresent());
        assertEquals("Ok", manager.ownedBy(alice).orElseThrow().name());
    }

    @Test
    void cancelReturnsPositionAndPlayerLeavingCancels() {
        manager.beginFoundation(alice, "Alice", hall, 0);
        assertEquals(hall, manager.cancelFoundation(alice).orElseThrow());
        assertTrue(manager.all().isEmpty());
    }

    @Test
    void playerLeavingCancelsPendingFoundation() {
        manager.beginFoundation(alice, "Alice", hall, 0);
        manager.onPlayerLeft(alice);
        assertTrue(manager.confirmFoundation(alice, "Late").isEmpty());
        assertTrue(manager.all().isEmpty());
    }

    @Test
    void protectionFollowsPermissions() {
        Colony c = found(alice, "A", hall);
        BlockPos inside = hall.offset(3, 0, 3);
        assertTrue(manager.isAllowed(alice, inside, Action.BREAK_BLOCKS));
        assertFalse(manager.isAllowed(bob, inside, Action.BREAK_BLOCKS));
        assertTrue(manager.isAllowed(bob, new BlockPos(9000, 64, 0), Action.BREAK_BLOCKS));
        assertTrue(manager.setRank(alice, c.id(), bob, "Bob", Permissions.OFFICER));
        assertTrue(manager.isAllowed(bob, inside, Action.BREAK_BLOCKS));
        assertFalse(manager.setRank(bob, c.id(), UUID.randomUUID(), "Eve", Permissions.OFFICER)); // no EDIT_PERMISSIONS
    }

    @Test
    void townHallViewRequiresAccessAndRenameRequiresManager() {
        Colony c = found(alice, "A", hall);
        manager.openTownHall(bob, hall);
        assertEquals("hycolony.permission.denied", t.notifier.sent.getLast().msg().key());
        manager.openTownHall(alice, hall);
        TownHallView view = (TownHallView) t.ui.shown.get(alice);
        assertEquals("A", view.colonyName());
        assertTrue(view.canRename());
        assertFalse(manager.rename(bob, c.id(), "Hacked"));
        assertTrue(manager.rename(alice, c.id(), "Renamed"));
        assertEquals("Renamed", c.name());
    }

    @Test
    void deleteDespawnsCitizensAndFreesTerritory() {
        Colony c = found(alice, "A", hall);
        for (int i = 0; i < 20; i++) {
            c.citizens().onColonyTick();
        }
        assertTrue(t.bodies.aliveCount() > 0);
        manager.deleteColony(c.id());
        assertEquals(0, t.bodies.aliveCount());
        assertTrue(manager.colonyAt(hall).isEmpty());
    }

    @Test
    void bodyOfUnknownColonyIsDespawned() {
        BodyId stray = t.bodies.existing(42, 1, new Vec3(0, 64, 0));
        manager.onBodyLoaded(stray, 42, 1);
        assertFalse(t.bodies.isAlive(stray));
    }
}
```

- [ ] **Step 2 : lancer les tests pour vérifier qu'ils échouent**

Run : `./gradlew :core:test --tests "*ColonyManagerTest"`
Attendu : erreur de compilation.

- [ ] **Step 3 : implémenter**

`colony/HutPlacement.java` :

```java
package dev.hycolony.core.colony;

import dev.hycolony.core.kernel.port.Msg;

/** Outcome of placing a hut block. */
public sealed interface HutPlacement {
    /** Placement inside an existing colony is fine. */
    record Allowed(Colony colony) implements HutPlacement {}

    /** Town hall outside any colony: start the "found a colony" flow. */
    record FoundNewColony() implements HutPlacement {}

    record Denied(Msg reason) implements HutPlacement {}
}
```

`colony/ColonyManager.java` :

```java
package dev.hycolony.core.colony;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.building.BuildingType;
import dev.hycolony.core.building.BuildingTypes;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.colony.ui.CitizenRow;
import dev.hycolony.core.colony.ui.FoundColonyView;
import dev.hycolony.core.colony.ui.TownHallView;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.port.BodyId;
import dev.hycolony.core.kernel.port.Msg;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** All colonies of one world. Entry point the plugin calls, always on the world thread. */
public final class ColonyManager {
    public static final int MAX_NAME_LENGTH = 32;

    private record PendingFoundation(String playerName, BlockPos pos, int rotation) {}

    private final ColonyContext ctx;
    private final TerritoryIndex territory = new TerritoryIndex();
    private final Map<Integer, Colony> colonies = new LinkedHashMap<>();
    private final Map<UUID, PendingFoundation> pending = new HashMap<>();
    private int nextId = 1;

    public ColonyManager(ColonyContext ctx) {
        this.ctx = ctx;
    }

    public ColonyContext context() { return ctx; }
    public TerritoryIndex territory() { return territory; }
    public Collection<Colony> all() { return Collections.unmodifiableCollection(colonies.values()); }
    public Optional<Colony> byId(int id) { return Optional.ofNullable(colonies.get(id)); }

    public Optional<Colony> colonyAt(BlockPos pos) {
        var id = territory.colonyAt(pos);
        return id.isPresent() ? byId(id.getAsInt()) : Optional.empty();
    }

    public Optional<Colony> ownedBy(UUID player) {
        return colonies.values().stream().filter(c -> c.permissions().owner().equals(player)).findFirst();
    }

    // ---- Hut placement (port of AbstractBlockHut.canPaste) ----

    public HutPlacement checkHutPlacement(UUID player, BlockPos pos, String buildingTypeId) {
        boolean isTownHall = BuildingTypes.TOWN_HALL.id().equals(buildingTypeId);
        Optional<Colony> colony = colonyAt(pos);
        if (colony.isEmpty()) {
            if (!isTownHall) {
                return new HutPlacement.Denied(Msg.of(ownedBy(player).isPresent() ? "hycolony.hut.tooFar" : "hycolony.hut.noTownHall"));
            }
            if (ownedBy(player).isPresent()) {
                return new HutPlacement.Denied(Msg.of("hycolony.colony.alreadyOwner"));
            }
            if (!territory.isFreeForNewColony(pos, ctx.config().initialColonySize(), ctx.config().minColonyDistance())) {
                return new HutPlacement.Denied(Msg.of("hycolony.colony.tooClose"));
            }
            return new HutPlacement.FoundNewColony();
        }
        Colony c = colony.get();
        if (!c.permissions().hasPermission(player, Action.PLACE_HUTS)) {
            return new HutPlacement.Denied(Msg.of("hycolony.permission.placeHuts", c.name()));
        }
        if (isTownHall && c.buildings().townHall().isPresent()) {
            return new HutPlacement.Denied(Msg.of("hycolony.hut.townHallExists"));
        }
        return new HutPlacement.Allowed(c);
    }

    public void beginFoundation(UUID player, String playerName, BlockPos pos, int rotation) {
        pending.put(player, new PendingFoundation(playerName, pos, rotation));
        ctx.ui().showFoundColony(player, new FoundColonyView(playerName + "'s Colony"));
    }

    /** Empty if nothing is pending, the name is invalid, or the spot became invalid meanwhile. */
    public Optional<Colony> confirmFoundation(UUID player, String rawName) {
        PendingFoundation p = pending.get(player);
        if (p == null) {
            return Optional.empty();
        }
        String name = rawName == null ? "" : rawName.trim();
        if (name.isEmpty() || name.length() > MAX_NAME_LENGTH) {
            ctx.notifier().send(player, Msg.of("hycolony.colony.invalidName", String.valueOf(MAX_NAME_LENGTH)));
            return Optional.empty();
        }
        HutPlacement check = checkHutPlacement(player, p.pos(), BuildingTypes.TOWN_HALL.id());
        if (check instanceof HutPlacement.Denied denied) {
            pending.remove(player);
            ctx.notifier().send(player, denied.reason());
            return Optional.empty();
        }
        pending.remove(player);
        ctx.ui().close(player);
        Colony colony = new Colony(ctx, territory, allocateId(), name, p.pos(), Permissions.createDefault(player, p.playerName()));
        register(colony);
        colony.log().add("colonyCreated", colony.day(), name);
        ctx.bus().post(new ColonyEvents.ColonyCreated(colony));
        placeHut(colony, BuildingTypes.TOWN_HALL.id(), p.pos(), p.rotation());
        ctx.notifier().send(player, Msg.of("hycolony.colony.created", name));
        return Optional.of(colony);
    }

    /** Returns where the unconfirmed town hall stands, so the adapter can remove it. */
    public Optional<BlockPos> cancelFoundation(UUID player) {
        PendingFoundation p = pending.remove(player);
        ctx.ui().close(player);
        return Optional.ofNullable(p).map(PendingFoundation::pos);
    }

    public void onPlayerLeft(UUID player) {
        pending.remove(player);
    }

    public void placeHut(Colony colony, String buildingTypeId, BlockPos pos, int rotation) {
        BuildingType type = ctx.buildingTypes().byId(buildingTypeId).orElseThrow();
        Building building = Building.create(type, pos, rotation);
        colony.buildings().add(building);
        colony.log().add("buildingPlaced", colony.day(), type.id());
        colony.markDirty();
        ctx.bus().post(new ColonyEvents.BuildingPlaced(colony, building));
    }

    public void onHutRemoved(BlockPos pos) {
        colonyAt(pos).ifPresent(c -> c.buildings().remove(pos).ifPresent(b -> {
            c.log().add("buildingRemoved", c.day(), b.type().id());
            c.markDirty();
            ctx.bus().post(new ColonyEvents.BuildingRemoved(c, b));
        }));
    }

    // ---- Protection and management ----

    public boolean protectionEnabled() {
        return ctx.config().enableColonyProtection();
    }

    /** Outside any colony everything is allowed. */
    public boolean isAllowed(UUID player, BlockPos pos, Action action) {
        return colonyAt(pos).map(c -> c.permissions().hasPermission(player, action)).orElse(true);
    }

    public boolean setRank(UUID actor, int colonyId, UUID target, String targetName, int rankId) {
        Colony c = colonies.get(colonyId);
        if (c == null || !c.permissions().hasPermission(actor, Action.EDIT_PERMISSIONS)) {
            return false;
        }
        boolean changed = c.permissions().setRank(target, targetName, rankId);
        if (changed) {
            c.markDirty();
        }
        return changed;
    }

    public void openTownHall(UUID player, BlockPos hutPos) {
        Optional<Colony> colony = colonyAt(hutPos);
        if (colony.isEmpty()) {
            return;
        }
        Colony c = colony.get();
        if (!c.permissions().hasPermission(player, Action.ACCESS_HUTS)) {
            ctx.notifier().send(player, Msg.of("hycolony.permission.denied", c.name()));
            return;
        }
        ctx.ui().showTownHall(player, townHallView(c, player));
    }

    public boolean rename(UUID actor, int colonyId, String rawName) {
        Colony c = colonies.get(colonyId);
        String name = rawName == null ? "" : rawName.trim();
        if (c == null || !c.permissions().rankOf(actor).isColonyManager()) {
            return false;
        }
        if (name.isEmpty() || name.length() > MAX_NAME_LENGTH) {
            ctx.notifier().send(actor, Msg.of("hycolony.colony.invalidName", String.valueOf(MAX_NAME_LENGTH)));
            return false;
        }
        c.setName(name);
        ctx.ui().showTownHall(actor, townHallView(c, actor));
        return true;
    }

    private TownHallView townHallView(Colony c, UUID viewer) {
        List<CitizenRow> rows = c.citizens().all().stream().map(d -> row(c, d)).toList();
        return new TownHallView(c.id(), c.name(), c.permissions().ownerName(), c.day(), rows,
                c.permissions().rankOf(viewer).isColonyManager());
    }

    private CitizenRow row(Colony c, CitizenData d) {
        boolean present = c.citizens().bodyOf(d.id()).map(ctx.bodies()::isAlive).orElse(false);
        String status = !present ? "absent"
                : c.citizens().aiState(d.id()).map(s -> s.name().toLowerCase(java.util.Locale.ROOT)).orElse("idle");
        return new CitizenRow(d.name(), d.gender(), status);
    }

    public void deleteColony(int colonyId) {
        Colony c = colonies.remove(colonyId);
        if (c == null) {
            return;
        }
        c.citizens().despawnAll();
        territory.releaseAll(colonyId);
        ctx.bus().post(new ColonyEvents.ColonyDeleted(colonyId));
    }

    // ---- Ticking and bodies ----

    public void tick() {
        for (Colony colony : colonies.values()) {
            colony.tick();
        }
    }

    public void onBodyLoaded(BodyId body, int colonyId, int citizenId) {
        Colony c = colonies.get(colonyId);
        if (c == null) {
            ctx.bodies().despawn(body);
            return;
        }
        c.citizens().onBodyLoaded(body, citizenId);
    }

    public void onBodyUnloaded(BodyId body, int colonyId) {
        byId(colonyId).ifPresent(c -> c.citizens().onBodyUnloaded(body));
    }

    // ---- Used by persistence ----

    int allocateId() {
        return nextId++;
    }

    void reserveId(int id) {
        nextId = Math.max(nextId, id + 1);
    }

    void register(Colony colony) {
        colonies.put(colony.id(), colony);
        reserveId(colony.id());
        territory.claimSquare(colony.id(), ClaimCell.of(colony.center()), ctx.config().initialColonySize());
        colony.markDirty();
    }
}
```

- [ ] **Step 4 : lancer les tests pour vérifier qu'ils passent**

Run : `./gradlew :core:test`
Attendu : PASS.

- [ ] **Step 5 : commit**

```bash
git add core
git commit -m "feat(core): colony manager with town hall rules, founding flow, protection and ranks

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 11 : persistance (sérialisation, migrations, fichiers atomiques)

**Files :**
- Create : `core/src/main/java/dev/hycolony/core/kernel/persist/{ColonyStorage,FileColonyStorage,Migration,MigrationChain,SchemaTooNewException}.java`, `core/src/main/java/dev/hycolony/core/colony/ColonySerializer.java`
- Modify : `core/src/main/java/dev/hycolony/core/colony/ColonyManager.java` (ajout de `loadAll`, `saveDirty`, `saveAll`, `setStorage`, plus l'archivage dans `deleteColony`)
- Test : `core/src/test/java/dev/hycolony/core/kernel/persist/{FileColonyStorageTest,MigrationChainTest}.java`, `core/src/test/java/dev/hycolony/core/colony/PersistenceTest.java`, et la fixture `core/src/test/resources/fixtures/colony-v1.json`

**Interfaces :**
- Produces :
  - `interface ColonyStorage` avec `List<Integer> colonyIds()`, `int highestIdEverUsed()`, `Optional<JsonObject> load(int id)`, `void save(int id, String json)`, `void backupVersion(int id, int schemaVersion, String json)` et `void archive(int id)` (tous lèvent `IOException`) ;
  - `final class FileColonyStorage(Path dir) implements ColonyStorage` ;
  - `record Migration(int from, UnaryOperator<JsonObject> apply)` ;
  - `final class MigrationChain(int current, List<Migration>)` avec `int versionOf(JsonObject)`, `JsonObject migrate(JsonObject)` et `static MigrationChain sp0()` (current = 1, sans migration) ;
  - `class SchemaTooNewException extends RuntimeException` ;
  - `final class ColonySerializer` avec `static JsonObject write(Colony)` et `static Colony read(JsonObject, ColonyContext, TerritoryIndex)` ;
  - dans `ColonyManager` : `void setStorage(ColonyStorage, MigrationChain)`, `void loadAll()`, `void saveDirty()`, `void saveAll()`, et l'archivage à la suppression.

- [ ] **Step 1 : écrire les tests qui échouent**

`core/src/test/java/dev/hycolony/core/kernel/persist/FileColonyStorageTest.java` :

```java
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
}
```

`core/src/test/java/dev/hycolony/core/kernel/persist/MigrationChainTest.java` :

```java
package dev.hycolony.core.kernel.persist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.gson.JsonObject;
import java.util.List;
import org.junit.jupiter.api.Test;

class MigrationChainTest {
    private static JsonObject doc(int version) {
        JsonObject o = new JsonObject();
        o.addProperty("schemaVersion", version);
        return o;
    }

    @Test
    void appliesMigrationsInOrder() {
        MigrationChain chain = new MigrationChain(3, List.of(
                new Migration(1, o -> { o.addProperty("a", true); return o; }),
                new Migration(2, o -> { o.addProperty("b", o.get("a").getAsBoolean()); return o; })));
        JsonObject out = chain.migrate(doc(1));
        assertEquals(3, out.get("schemaVersion").getAsInt());
        assertEquals(true, out.get("b").getAsBoolean());
    }

    @Test
    void newerSchemaIsRejected() {
        assertThrows(SchemaTooNewException.class, () -> MigrationChain.sp0().migrate(doc(2)));
    }

    @Test
    void missingMigrationStepIsAnError() {
        MigrationChain chain = new MigrationChain(3, List.of(new Migration(1, o -> o)));
        assertThrows(IllegalStateException.class, () -> chain.migrate(doc(1)));
    }
}
```

`core/src/test/java/dev/hycolony/core/colony/PersistenceTest.java` :

```java
package dev.hycolony.core.colony;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.citizen.Skill;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.persist.FileColonyStorage;
import dev.hycolony.core.kernel.persist.MigrationChain;
import dev.hycolony.core.testing.TestContexts;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PersistenceTest {
    @TempDir Path dir;
    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();

    private ColonyManager manager(TestContexts t) {
        ColonyManager m = new ColonyManager(t.context());
        m.setStorage(new FileColonyStorage(dir), MigrationChain.sp0());
        return m;
    }

    @Test
    void fullRoundTrip() {
        TestContexts t = new TestContexts();
        ColonyManager m = manager(t);
        m.beginFoundation(alice, "Alice", new BlockPos(0, 64, 0), 1);
        Colony c = m.confirmFoundation(alice, "Rivendell").orElseThrow();
        m.setRank(alice, c.id(), bob, "Bob", Permissions.FRIEND);
        for (int i = 0; i < 20; i++) {
            c.citizens().onColonyTick();
        }
        c.setDay(7);
        m.saveAll();

        ColonyManager reloaded = manager(new TestContexts());
        reloaded.loadAll();
        Colony r = reloaded.byId(c.id()).orElseThrow();
        assertEquals("Rivendell", r.name());
        assertEquals(7, r.day());
        assertEquals(Permissions.FRIEND, r.permissions().rankOf(bob).id());
        assertEquals(alice, r.permissions().owner());
        assertEquals(1, r.buildings().townHall().orElseThrow().rotation());
        assertEquals(4, r.citizens().all().size());
        CitizenData a = c.citizens().all().iterator().next();
        CitizenData b = r.citizens().all().iterator().next();
        assertEquals(a.name(), b.name());
        assertEquals(a.skills().level(Skill.Focus), b.skills().level(Skill.Focus));
        assertEquals(c.log().entries(), r.log().entries());
        assertTrue(reloaded.colonyAt(new BlockPos(10, 64, 10)).isPresent()); // territory rebuilt
    }

    @Test
    void unknownBuildingTypeIsPreservedVerbatim() throws Exception {
        TestContexts t = new TestContexts();
        ColonyManager m = manager(t);
        m.beginFoundation(alice, "Alice", new BlockPos(0, 64, 0), 0);
        Colony c = m.confirmFoundation(alice, "A").orElseThrow();
        m.saveAll();
        Path file = dir.resolve("colony-" + c.id() + ".json");
        JsonObject json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        JsonObject alien = new JsonObject();
        alien.addProperty("type", "future:windmill");
        alien.addProperty("secret", 42);
        json.getAsJsonArray("buildings").add(alien);
        Files.writeString(file, json.toString());

        ColonyManager reloaded = manager(new TestContexts());
        reloaded.loadAll();
        reloaded.byId(c.id()).orElseThrow().markDirty();
        reloaded.saveAll();
        String saved = Files.readString(file);
        assertTrue(saved.contains("future:windmill") && saved.contains("\"secret\":42"), saved);
    }

    @Test
    void newerSchemaIsSkippedAndNeverOverwritten() throws Exception {
        Files.writeString(dir.resolve("colony-9.json"), "{\"schemaVersion\":99,\"id\":9}");
        ColonyManager m = manager(new TestContexts());
        m.loadAll();
        assertTrue(m.byId(9).isEmpty());
        m.beginFoundation(alice, "Alice", new BlockPos(0, 64, 0), 0);
        Colony c = m.confirmFoundation(alice, "New").orElseThrow();
        assertTrue(c.id() > 9);
        m.saveAll();
        assertEquals("{\"schemaVersion\":99,\"id\":9}", Files.readString(dir.resolve("colony-9.json")));
    }

    @Test
    void v1FixtureLoads() throws Exception {
        try (var in = getClass().getResourceAsStream("/fixtures/colony-v1.json")) {
            Files.write(dir.resolve("colony-1.json"), in.readAllBytes());
        }
        ColonyManager m = manager(new TestContexts());
        m.loadAll();
        Colony c = m.byId(1).orElseThrow();
        assertEquals("Fixture", c.name());
        assertEquals(1, c.citizens().all().size());
    }

    @Test
    void deleteArchivesFile() {
        TestContexts t = new TestContexts();
        ColonyManager m = manager(t);
        m.beginFoundation(alice, "Alice", new BlockPos(0, 64, 0), 0);
        Colony c = m.confirmFoundation(alice, "A").orElseThrow();
        m.saveAll();
        m.deleteColony(c.id());
        assertTrue(Files.notExists(dir.resolve("colony-" + c.id() + ".json")));
        assertTrue(Files.isDirectory(dir.resolve("archive")));
    }
}
```

`core/src/test/resources/fixtures/colony-v1.json` :

```json
{
  "schemaVersion": 1,
  "id": 1,
  "name": "Fixture",
  "center": {"x": 0, "y": 64, "z": 0},
  "day": 3,
  "permissions": {
    "owner": "00000000-0000-0000-0000-000000000001",
    "ownerName": "Owner",
    "ranks": [
      {"id": 0, "name": "Owner", "permissions": 2147483647, "initial": true, "colonyManager": true, "hostile": false},
      {"id": 3, "name": "Neutral", "permissions": 1342177280, "initial": true, "colonyManager": false, "hostile": false}
    ],
    "members": [{"uuid": "00000000-0000-0000-0000-000000000001", "name": "Owner", "rank": 0}]
  },
  "buildings": [
    {"type": "hycolony:townhall", "pos": {"x": 0, "y": 64, "z": 0}, "rotation": 0, "level": 0, "built": false,
     "customName": "", "style": "", "modules": {}}
  ],
  "citizens": [
    {"id": 1, "name": "Aela K. Oakhart", "gender": "FEMALE", "child": false,
     "skills": {"Athletics": {"level": 3, "xp": 0.5}},
     "lastPosition": {"x": 1.5, "y": 64.0, "z": 2.5}, "respawnPosition": null,
     "home": null, "work": null, "saturation": 60.0}
  ],
  "eventLog": [{"type": "colonyCreated", "day": 0, "params": ["Fixture"]}]
}
```

`1342177280` = `(1 << 30) | (1 << 28)`, soit ACCESS_TOGGLEABLES et MAP_BORDER : les permissions du rang Neutral.

- [ ] **Step 2 : lancer les tests pour vérifier qu'ils échouent**

Run : `./gradlew :core:test`
Attendu : erreur de compilation (classes de persistance absentes).

- [ ] **Step 3 : implémenter le stockage et les migrations**

`kernel/persist/ColonyStorage.java` :

```java
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

    /** Atomic: write .tmp, move current to .bak, move .tmp into place. */
    void save(int id, String json) throws IOException;

    /** Copy kept before migrating; written once per version. */
    void backupVersion(int id, int schemaVersion, String json) throws IOException;

    void archive(int id) throws IOException;
}
```

`kernel/persist/FileColonyStorage.java` :

```java
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
```

`kernel/persist/Migration.java` :

```java
package dev.hycolony.core.kernel.persist;

import com.google.gson.JsonObject;
import java.util.function.UnaryOperator;

/** Upgrades a document from schema {@code from} to {@code from + 1}. */
public record Migration(int from, UnaryOperator<JsonObject> apply) {}
```

`kernel/persist/SchemaTooNewException.java` :

```java
package dev.hycolony.core.kernel.persist;

public class SchemaTooNewException extends RuntimeException {
    public SchemaTooNewException(int found, int supported) {
        super("Save schema " + found + " is newer than supported " + supported);
    }
}
```

`kernel/persist/MigrationChain.java` :

```java
package dev.hycolony.core.kernel.persist;

import com.google.gson.JsonObject;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class MigrationChain {
    public static final String VERSION_KEY = "schemaVersion";

    private final int current;
    private final Map<Integer, Migration> byFrom;

    public MigrationChain(int current, List<Migration> migrations) {
        this.current = current;
        this.byFrom = migrations.stream().collect(Collectors.toMap(Migration::from, Function.identity()));
    }

    /** SP0: schema 1, no migrations yet. Add one Migration per future schema bump. */
    public static MigrationChain sp0() {
        return new MigrationChain(1, List.of());
    }

    public int current() {
        return current;
    }

    public int versionOf(JsonObject doc) {
        return doc.has(VERSION_KEY) ? doc.get(VERSION_KEY).getAsInt() : 1;
    }

    public JsonObject migrate(JsonObject doc) {
        int version = versionOf(doc);
        if (version > current) {
            throw new SchemaTooNewException(version, current);
        }
        while (version < current) {
            Migration m = byFrom.get(version);
            if (m == null) {
                throw new IllegalStateException("No migration from schema " + version);
            }
            doc = m.apply().apply(doc);
            version++;
            doc.addProperty(VERSION_KEY, version);
        }
        return doc;
    }
}
```

- [ ] **Step 4 : implémenter `ColonySerializer`**

Pour la restauration, le sérialiseur a besoin de trois accès package-private supplémentaires : `Permissions.restore` (déjà prévu en Task 5), `Building.unknownModules()` (public) et `Rank` (public).

`colony/ColonySerializer.java` :

```java
package dev.hycolony.core.colony;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import dev.hycolony.core.building.Building;
import dev.hycolony.core.building.BuildingModule;
import dev.hycolony.core.building.BuildingType;
import dev.hycolony.core.building.PersistentModule;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.citizen.Gender;
import dev.hycolony.core.citizen.Skill;
import dev.hycolony.core.citizen.Skills;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.Vec3;
import dev.hycolony.core.kernel.persist.MigrationChain;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Colony <-> JSON (schema 1). Unknown buildings/modules are kept verbatim. */
public final class ColonySerializer {
    public static final int SCHEMA_VERSION = 1;

    private ColonySerializer() {}

    public static JsonObject write(Colony c) {
        JsonObject o = new JsonObject();
        o.addProperty(MigrationChain.VERSION_KEY, SCHEMA_VERSION);
        o.addProperty("id", c.id());
        o.addProperty("name", c.name());
        o.add("center", pos(c.center()));
        o.addProperty("day", c.day());
        o.add("permissions", permissions(c.permissions()));

        JsonArray buildings = new JsonArray();
        for (Building b : c.buildings().all()) {
            buildings.add(building(b));
        }
        c.buildings().unknown().forEach(buildings::add);
        o.add("buildings", buildings);

        JsonArray citizens = new JsonArray();
        for (CitizenData d : c.citizens().all()) {
            citizens.add(citizen(d));
        }
        o.add("citizens", citizens);

        JsonArray log = new JsonArray();
        for (EventLog.Entry e : c.log().entries()) {
            JsonObject entry = new JsonObject();
            entry.addProperty("type", e.type());
            entry.addProperty("day", e.day());
            JsonArray params = new JsonArray();
            e.params().forEach(params::add);
            entry.add("params", params);
            log.add(entry);
        }
        o.add("eventLog", log);
        return o;
    }

    public static Colony read(JsonObject o, ColonyContext ctx, TerritoryIndex territory) {
        Colony c = new Colony(ctx, territory, o.get("id").getAsInt(), o.get("name").getAsString(),
                readPos(o.getAsJsonObject("center")), readPermissions(o.getAsJsonObject("permissions")));
        c.setDay(o.get("day").getAsInt());
        for (JsonElement el : o.getAsJsonArray("buildings")) {
            JsonObject b = el.getAsJsonObject();
            Optional<BuildingType> type = ctx.buildingTypes().byId(b.get("type").getAsString());
            if (type.isEmpty()) {
                c.buildings().keepUnknown(b);
                continue;
            }
            c.buildings().add(readBuilding(b, type.get()));
        }
        for (JsonElement el : o.getAsJsonArray("citizens")) {
            c.citizens().restore(readCitizen(el.getAsJsonObject()));
        }
        for (JsonElement el : o.getAsJsonArray("eventLog")) {
            JsonObject e = el.getAsJsonObject();
            // Manual loop: JsonArray.asList() needs Gson 2.10+, and the server's Gson version is not guaranteed.
            java.util.List<String> params = new java.util.ArrayList<>();
            for (JsonElement p : e.getAsJsonArray("params")) {
                params.add(p.getAsString());
            }
            c.log().restore(new EventLog.Entry(e.get("type").getAsString(), e.get("day").getAsInt(), params));
        }
        c.clearDirty();
        return c;
    }

    // ---- positions ----

    private static JsonElement pos(BlockPos p) {
        if (p == null) {
            return JsonNull.INSTANCE;
        }
        JsonObject o = new JsonObject();
        o.addProperty("x", p.x());
        o.addProperty("y", p.y());
        o.addProperty("z", p.z());
        return o;
    }

    private static BlockPos readPos(JsonElement e) {
        if (e == null || e.isJsonNull()) {
            return null;
        }
        JsonObject o = e.getAsJsonObject();
        return new BlockPos(o.get("x").getAsInt(), o.get("y").getAsInt(), o.get("z").getAsInt());
    }

    private static JsonElement vec(Vec3 v) {
        if (v == null) {
            return JsonNull.INSTANCE;
        }
        JsonObject o = new JsonObject();
        o.addProperty("x", v.x());
        o.addProperty("y", v.y());
        o.addProperty("z", v.z());
        return o;
    }

    private static Vec3 readVec(JsonElement e) {
        if (e == null || e.isJsonNull()) {
            return null;
        }
        JsonObject o = e.getAsJsonObject();
        return new Vec3(o.get("x").getAsDouble(), o.get("y").getAsDouble(), o.get("z").getAsDouble());
    }

    // ---- permissions ----

    private static JsonObject permissions(Permissions p) {
        JsonObject o = new JsonObject();
        o.addProperty("owner", p.owner().toString());
        o.addProperty("ownerName", p.ownerName());
        JsonArray ranks = new JsonArray();
        for (Rank r : p.ranks().values()) {
            JsonObject ro = new JsonObject();
            ro.addProperty("id", r.id());
            ro.addProperty("name", r.name());
            ro.addProperty("permissions", r.permissions());
            ro.addProperty("initial", r.isInitial());
            ro.addProperty("colonyManager", r.isColonyManager());
            ro.addProperty("hostile", r.isHostile());
            ranks.add(ro);
        }
        o.add("ranks", ranks);
        JsonArray members = new JsonArray();
        p.members().forEach((uuid, m) -> {
            JsonObject mo = new JsonObject();
            mo.addProperty("uuid", uuid.toString());
            mo.addProperty("name", m.name());
            mo.addProperty("rank", m.rankId());
            members.add(mo);
        });
        o.add("members", members);
        return o;
    }

    private static Permissions readPermissions(JsonObject o) {
        UUID owner = UUID.fromString(o.get("owner").getAsString());
        String ownerName = o.get("ownerName").getAsString();
        Permissions defaults = Permissions.createDefault(owner, ownerName);
        Map<Integer, Rank> ranks = new LinkedHashMap<>(defaults.ranks());
        for (JsonElement el : o.getAsJsonArray("ranks")) {
            JsonObject r = el.getAsJsonObject();
            ranks.put(r.get("id").getAsInt(), new Rank(r.get("id").getAsInt(), r.get("name").getAsString(),
                    r.get("permissions").getAsLong(), r.get("initial").getAsBoolean(),
                    r.get("colonyManager").getAsBoolean(), r.get("hostile").getAsBoolean()));
        }
        Map<UUID, Permissions.Member> members = new LinkedHashMap<>();
        for (JsonElement el : o.getAsJsonArray("members")) {
            JsonObject m = el.getAsJsonObject();
            members.put(UUID.fromString(m.get("uuid").getAsString()),
                    new Permissions.Member(m.get("name").getAsString(), m.get("rank").getAsInt()));
        }
        return Permissions.restore(owner, ownerName, ranks, members);
    }

    // ---- buildings ----

    private static JsonObject building(Building b) {
        JsonObject o = new JsonObject();
        o.addProperty("type", b.type().id());
        o.add("pos", pos(b.position()));
        o.addProperty("rotation", b.rotation());
        o.addProperty("level", b.level());
        o.addProperty("built", b.isBuilt());
        o.addProperty("customName", b.customName());
        o.addProperty("style", b.style());
        JsonObject modules = new JsonObject();
        b.modules().forEach((key, module) -> {
            if (module instanceof PersistentModule pm) {
                JsonObject m = new JsonObject();
                pm.write(m);
                modules.add(key, m);
            }
        });
        b.unknownModules().forEach(modules::add);
        o.add("modules", modules);
        return o;
    }

    private static Building readBuilding(JsonObject o, BuildingType type) {
        Building b = Building.create(type, readPos(o.get("pos")), o.get("rotation").getAsInt());
        b.setLevel(o.get("level").getAsInt());
        b.setBuilt(o.get("built").getAsBoolean());
        b.setCustomName(o.get("customName").getAsString());
        b.setStyle(o.get("style").getAsString());
        JsonObject modules = o.getAsJsonObject("modules");
        for (String key : modules.keySet()) {
            BuildingModule module = b.modules().get(key);
            if (module instanceof PersistentModule pm) {
                pm.read(modules.getAsJsonObject(key));
            } else if (module == null) {
                b.unknownModules().put(key, modules.getAsJsonObject(key));
            }
        }
        return b;
    }

    // ---- citizens ----

    private static JsonObject citizen(CitizenData d) {
        JsonObject o = new JsonObject();
        o.addProperty("id", d.id());
        o.addProperty("name", d.name());
        o.addProperty("gender", d.gender().name());
        o.addProperty("child", d.isChild());
        JsonObject skills = new JsonObject();
        for (Skill s : Skill.values()) {
            JsonObject so = new JsonObject();
            so.addProperty("level", d.skills().level(s));
            so.addProperty("xp", d.skills().experience(s));
            skills.add(s.name(), so);
        }
        o.add("skills", skills);
        o.add("lastPosition", vec(d.lastPosition()));
        o.add("respawnPosition", pos(d.respawnPosition()));
        o.add("home", pos(d.homeBuilding()));
        o.add("work", pos(d.workBuilding()));
        o.addProperty("saturation", d.saturation());
        return o;
    }

    private static CitizenData readCitizen(JsonObject o) {
        CitizenData d = new CitizenData(o.get("id").getAsInt());
        d.setName(o.get("name").getAsString());
        d.setGender(Gender.valueOf(o.get("gender").getAsString()));
        d.setChild(o.get("child").getAsBoolean());
        Skills skills = Skills.empty();
        JsonObject so = o.getAsJsonObject("skills");
        for (Skill s : Skill.values()) {
            if (so.has(s.name())) {
                JsonObject e = so.getAsJsonObject(s.name());
                skills.set(s, e.get("level").getAsInt(), e.get("xp").getAsDouble());
            }
        }
        d.setSkills(skills);
        d.setLastPosition(readVec(o.get("lastPosition")));
        d.setRespawnPosition(readPos(o.get("respawnPosition")));
        d.setHomeBuilding(readPos(o.get("home")));
        d.setWorkBuilding(readPos(o.get("work")));
        d.setSaturation(o.get("saturation").getAsDouble());
        return d;
    }
}
```

- [ ] **Step 5 : brancher la persistance dans `ColonyManager`**

Ajouter les champs et méthodes suivants à `colony/ColonyManager.java`, avec les imports `dev.hycolony.core.kernel.persist.*`, `com.google.gson.JsonObject`, `java.io.IOException`, `java.util.HashSet` et `java.util.Set` :

```java
    private static final System.Logger LOG = System.getLogger(ColonyManager.class.getName());

    private ColonyStorage storage;
    private MigrationChain migrations = MigrationChain.sp0();
    /** Ids whose file must never be touched (newer schema). */
    private final Set<Integer> lockedIds = new HashSet<>();

    public void setStorage(ColonyStorage storage, MigrationChain migrations) {
        this.storage = storage;
        this.migrations = migrations;
    }

    public void loadAll() {
        try {
            reserveId(storage.highestIdEverUsed());
            for (int id : storage.colonyIds()) {
                loadOne(id);
            }
        } catch (IOException e) {
            LOG.log(System.Logger.Level.ERROR, "Cannot list colonies of " + ctx.world(), e);
        }
    }

    private void loadOne(int id) throws IOException {
        Optional<JsonObject> raw = storage.load(id);
        if (raw.isEmpty()) {
            return;
        }
        JsonObject json = raw.get();
        int version = migrations.versionOf(json);
        try {
            if (version < migrations.current()) {
                storage.backupVersion(id, version, json.toString());
            }
            json = migrations.migrate(json);
            Colony colony = ColonySerializer.read(json, ctx, territory);
            register(colony);
            colony.clearDirty();
        } catch (SchemaTooNewException e) {
            lockedIds.add(id);
            LOG.log(System.Logger.Level.ERROR, "Colony " + id + " was saved by a newer HyColony; not loaded", e);
        } catch (RuntimeException e) {
            lockedIds.add(id);
            LOG.log(System.Logger.Level.ERROR, "Colony " + id + " failed to load; file left untouched", e);
        }
    }

    public void saveDirty() {
        for (Colony c : colonies.values()) {
            if (c.isDirty()) {
                save(c);
            }
        }
    }

    public void saveAll() {
        colonies.values().forEach(this::save);
    }

    private void save(Colony c) {
        if (storage == null || lockedIds.contains(c.id())) {
            return;
        }
        try {
            storage.save(c.id(), ColonySerializer.write(c).toString());
            c.clearDirty();
        } catch (IOException e) {
            LOG.log(System.Logger.Level.ERROR, "Saving colony " + c.id() + " failed; will retry", e);
        }
    }
```

Remplacer ensuite `deleteColony` par :

```java
    public void deleteColony(int colonyId) {
        Colony c = colonies.remove(colonyId);
        if (c == null) {
            return;
        }
        c.citizens().despawnAll();
        territory.releaseAll(colonyId);
        if (storage != null) {
            try {
                storage.archive(colonyId);
            } catch (IOException e) {
                LOG.log(System.Logger.Level.ERROR, "Archiving colony " + colonyId + " failed", e);
            }
        }
        ctx.bus().post(new ColonyEvents.ColonyDeleted(colonyId));
    }
```

Enfin, dans `confirmFoundation`, juste avant `return Optional.of(colony);`, ajouter la sauvegarde immédiate (spec § 5) :

```java
        save(colony);
```

`saveDirty` est appelée par le plugin toutes les `autosaveIntervalMinutes`. `saveAll` est appelée à l'arrêt.

- [ ] **Step 6 : lancer tous les tests**

Run : `./gradlew :core:test`
Attendu : PASS (tous les tests du core).

- [ ] **Step 7 : commit**

```bash
git add core
git commit -m "feat(core): versioned JSON persistence with atomic writes, quarantine and migrations

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

## Fin de la partie A

À ce stade, `./gradlew build` est vert et le core est complet et testé. La partie B (`2026-09-25-hycolony-sp0-plugin.md`) branche ce core sur Hytale.
