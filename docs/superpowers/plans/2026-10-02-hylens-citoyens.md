# HyLens V2, lot 2 : faire apparaître un citoyen et régler sa saturation — plan d'implémentation

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal :** porter `/mc citizens spawnNew` et `/mc citizens modify … saturation` dans l'API de HyColony (1.2) et en boutons dans HyLens.

**Architecture :** le cœur de HyColony gagne l'arrivée forcée (`CitizenManager.spawnForced`, la création d'un nouveau citoyen extraite dans `Newcomers`) et deux actions de débogage (`CoreDebugActions`), avec un réglage de config. L'API passe en 1.2. Le cœur de HyLens gagne `SaturationStep` et la saturation dans `MenuView` ; son plugin, les boutons.

**Tech Stack :** Java 25, JUnit 5, Gradle (`:core`, `:api`, `:plugin`, `:hylens-core`, `:hylens-plugin`).

**Spec :** `docs/superpowers/specs/2026-10-02-hylens-citoyens-design.md`

## Global Constraints

- CLAUDE.md en entier : 400 lignes par fichier au plus (300 visé), 40 par méthode, 5 paramètres, 15 fichiers par paquet, Javadoc courte, 120 colonnes, PMD (`CouplingBetweenObjects` ≤ 20), Error Prone.
- Chaque système porté cite sa source MC ; un écart porte `Deviation from MC:` et figure dans la spec (§ 7).
- Textes : une clé en en-US et fr-FR, dans le `.lang` du mod qui l'affiche ; `param(key, Message)` sur `.TextSpans`.
- API : `@since 1.2` sur chaque ajout ; `./gradlew :api:apiDump` et le fichier `api/api.txt` régénéré commité avec le changement.
- Config : un réglage de MC passe par `config.json` (section `Commands`), défaut de MC ; une config sans la clé prend le défaut.
- Commits : `git add` puis `git commit -m … -- <chemins>` explicites (d'autres sessions indexent dans le même dossier) ; fin de message `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`. `./gradlew build` vert avant chaque commit.
- Formatage fichier par fichier : `./gradlew :<projet>:spotlessApply -PspotlessIdeHook="<chemin absolu>"`.

## Review Focus

1. **Un citoyen forcé sans place** : rien ne doit rester enregistré (pas de citoyen fantôme sans corps), et les joueurs sont prévenus une fois. Test de la tâche 2.
2. **Saturation non numérique ou hors bornes** (NaN, négatif, > 60) : refus pour NaN, borne sinon ; aucune valeur folle ne peut être sauvée. Test de la tâche 3.
3. **Ordre des refus de `setSaturation`** : comme MC, un gestionnaire sans la config est refusé avant même de chercher le citoyen. Test de la tâche 3.
4. **HyLens contre une HyColony 1.1** : `ApiCompatibility` refuse, sinon `NoSuchMethodError` au premier clic. Test de la tâche 3.
5. **« − » à 0 et « + » au maximum** : la valeur reste bornée côté HyLens aussi (pas d'appel inutile hors bornes). Test de la tâche 4.

---

### Tâche 1 : le réglage `CanPlayerUseModifyCitizensCommand`

**Files :**
- Modify : `core/src/main/java/dev/hycolony/core/kernel/config/ColonyConfig.java:67-70,121`
- Modify : `plugin/src/main/java/dev/hycolony/plugin/config/CommandsSection.java`
- Modify : `docs/research/config-inventory.md:67,224`
- Test : `core/src/test/java/dev/hycolony/core/kernel/config/ColonyConfigTest.java` (créé s'il n'existe pas, sinon complété)

**Interfaces :**
- Produces : `ColonyConfig.Commands(boolean canPlayerUseShowColonyInfoCommand, boolean canPlayerUseAddOfficerCommand, boolean canPlayerUseDeleteColonyCommand, boolean canPlayerUseModifyCitizensCommand)`.

- [ ] **Étape 1 : le test qui échoue**

```java
    @Test
    void managersMayNotModifyCitizensByDefaultAsMineColonies() {
        assertFalse(ColonyConfig.defaults().commands().canPlayerUseModifyCitizensCommand());
    }
```

- [ ] **Étape 2 :** `./gradlew :core:test --tests "*ColonyConfigTest"` → échec de compilation (composante absente).
- [ ] **Étape 3 : le code**

`ColonyConfig.Commands` gagne en dernière composante `boolean canPlayerUseModifyCitizensCommand`, avec dans sa Javadoc `@param canPlayerUseModifyCitizensCommand MC canplayerusemodifycitizenscommand: a colony manager who is not an operator may set a citizen's saturation (/mc citizens modify)`. `defaults()` : `new Commands(true, true, false, false)`.

`CommandsSection` : un quatrième `append` sur le modèle des autres, clé `"CanPlayerUseModifyCitizensCommand"`, champ `boolean canPlayerUseModifyCitizensCommand = DEFAULTS.canPlayerUseModifyCitizensCommand();`, et `toCore()` le passe en dernier.

`config-inventory.md` : l. 67, « futur » devient « porté (`Commands.CanPlayerUseModifyCitizensCommand`, HyLens lot 2) » ; l'exemple gagne `"CanPlayerUseModifyCitizensCommand": false` après `CanPlayerUseDeleteColonyCommand` (virgule ajoutée à la ligne d'avant).

- [ ] **Étape 4 :** `./gradlew :core:test :plugin:compileJava` → PASS.
- [ ] **Étape 5 : commit** `feat(core): MC's canPlayerUseModifyCitizensCommand setting, off by default` (les 4 fichiers).

---

### Tâche 2 : l'arrivée forcée (`CitizenManager.spawnForced`)

**Files :**
- Create : `core/src/main/java/dev/hycolony/core/citizen/Newcomers.java`
- Modify : `core/src/main/java/dev/hycolony/core/citizen/CitizenManager.java:155-195`
- Test : `core/src/test/java/dev/hycolony/core/citizen/CitizenForcedArrivalTest.java`

**Interfaces :**
- Produces : `public boolean CitizenManager.spawnForced()`.

- [ ] **Étape 1 : les tests qui échouent**

```java
package dev.hycolony.core.citizen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.building.BuildingTypes;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.permission.Permissions;
import dev.hycolony.core.colony.territory.ClaimCell;
import dev.hycolony.core.colony.territory.TerritoryIndex;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.testing.TestContexts;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** MC spawnOrCreateCivilian(force = true), as /mc citizens spawnNew asks it (spec 2026-10-02 lot 2, § 4). */
class CitizenForcedArrivalTest {
    private final TestContexts t = new TestContexts();
    private final BlockPos hall = new BlockPos(0, 64, 0);

    private Colony colony(boolean townHall) {
        TerritoryIndex territory = new TerritoryIndex();
        territory.claimSquare(1, ClaimCell.of(hall), 4);
        Colony c = new Colony(
                t.context(),
                territory,
                new Colony.Founding(1, "Test", hall, Permissions.createDefault(UUID.randomUUID(), "A")));
        if (townHall) {
            c.buildings().add(Building.create(BuildingTypes.TOWN_HALL, hall, 0));
        }
        return c;
    }

    private long warnings() {
        return t.notifier.sent.stream()
                .filter(s -> s.msg().key().equals("hycolony.citizen.noArrivalSpace"))
                .count();
    }

    @Test
    void aForcedArrivalIgnoresNewCitizensOffAndTheInitialAmount() {
        Colony c = colony(true);
        c.settings().setMoveIn(false);

        for (int i = 0; i < 6; i++) {
            assertTrue(c.citizens().spawnForced());
        }

        assertEquals(6, c.citizens().all().size(), "beyond initialCitizenAmount (4)");
        assertEquals(6, t.bodies.aliveCount());
    }

    @Test
    void aForcedArrivalIsSavedJournaledAndAnnounced() {
        Colony c = colony(true);
        c.clearDirty();
        List<CitizenSpawned> heard = t.heard(CitizenSpawned.class);

        c.citizens().spawnForced();

        assertTrue(c.isDirty());
        assertEquals(1, heard.size());
    }

    @Test
    void noTownHallNoCitizen() {
        Colony c = colony(false);

        assertFalse(c.citizens().spawnForced());
        assertEquals(0, c.citizens().all().size());
    }

    @Test
    void anUnloadedTownHallCreatesNoneAndWarnsNobody() {
        Colony c = colony(true);
        t.players.online.put(c.permissions().owner(), hall);
        t.world.unloaded.add(hall);

        assertFalse(c.citizens().spawnForced());
        assertEquals(0, c.citizens().all().size());
        assertEquals(0, warnings());
    }

    @Test
    void noRoomAtTheTownHallCreatesNoneAndWarnsOnce() {
        Colony c = colony(true);
        t.players.online.put(c.permissions().owner(), hall);
        t.bodies.refuseSpawnAround.add(hall);

        assertFalse(c.citizens().spawnForced());
        assertEquals(0, c.citizens().all().size(), "MC creates the citizen only once its spawn point is found");
        assertEquals(1, warnings());
    }
}
```

(`setMoveIn`, `clearDirty`, `isDirty`, `t.heard`, `t.bodies.refuseSpawnAround`, `t.world.unloaded` existent déjà : `ColonySettings`, `Colony:247-258`, `TestContexts:90`, `CitizenManagerTest:169-195`.)

- [ ] **Étape 2 :** `./gradlew :core:test --tests "*CitizenForcedArrivalTest"` → échec de compilation (`spawnForced`).
- [ ] **Étape 3 : le code**

`Newcomers.java` reprend, sans changer le comportement, `createAndRegister` et le tirage du genre et du nom de `spawnInitialCitizen` :

```java
package dev.hycolony.core.citizen;

import dev.hycolony.core.citizen.happiness.CitizenHappiness;
import dev.hycolony.core.colony.Colony;
import java.util.Map;

/** A new citizen's data, registered in its colony (MC createAndRegisterCivilianData and initForNewCivilian). */
final class Newcomers {
    private Newcomers() {}

    /**
     * Registers in {@code citizens} a new citizen of {@code colony}: the first free id, full saturation, skills capped
     * by the colony's mean happiness (at least 5 below the initial amount), MC's balanced gender and a name.
     */
    static CitizenData register(Colony colony, Map<Integer, CitizenData> citizens) {
        // (corps repris de createAndRegister puis des lignes « gender » et « setName » de spawnInitialCitizen,
        // colony.context() à la place de ctx(), le comptage des femmes fait avant l'enregistrement comme aujourd'hui)
    }
}
```

Le corps est la recopie exacte de l'existant (`CitizenManager.java:156-170` pour le genre et le nom, `179-195` pour la création), avec `colony.context()` pour `ctx()`. `CitizenManager` devient :

```java
    private void spawnInitialCitizen(BlockPos townHall) {
        CitizenData data = Newcomers.register(colony, citizens);
        if (!spawnBody(data, townHall) && ctx().worldQuery().isLoaded(townHall)) {
            CitizenArrival.tellNoSpace(colony, townHall); // MC spawnOrCreateCivilian, on a loaded town hall only
        }
        arrived(data, townHall);
    }

    /**
     * MC spawnOrCreateCivilian(null, world, [], force = true), asked by /mc citizens spawnNew: a new citizen at the town
     * hall even with "new citizens" off and beyond the initial amount. False, and nothing created, without a loaded
     * town hall, or when no body finds room there, the colony then warned (MC creates the citizen only once its spawn
     * point is found).
     */
    public boolean spawnForced() {
        Optional<BlockPos> hall = colony.buildings().townHall().map(Building::position);
        if (hall.isEmpty() || !ctx().worldQuery().isLoaded(hall.get())) {
            return false;
        }
        CitizenData data = Newcomers.register(colony, citizens);
        if (!spawnBody(data, hall.get())) {
            citizens.remove(data.id());
            CitizenArrival.tellNoSpace(colony, hall.get());
            return false;
        }
        arrived(data, hall.get());
        return true;
    }

    /** A newcomer moved in at {@code townHall}: the colony's journal, a save, the event (MC CitizenManager). */
    private void arrived(CitizenData data, BlockPos townHall) {
        colony.log().addAt(townHall, "citizenSpawned", colony.day(), data.name());
        colony.markDirty();
        ctx().bus().post(new CitizenSpawned(colony, data));
    }
```

`createAndRegister` disparaît de `CitizenManager` ; les imports devenus inutiles (`Skills`, `Gender`, `CitizenHappiness` s'ils ne servent plus) sont retirés.

- [ ] **Étape 4 :** `./gradlew :core:test` → PASS (dont tout `CitizenManagerTest`, qui garde le comportement de l'arrivée normale).
- [ ] **Étape 5 : commit** `feat(core): a forced citizen arrival, as MC's spawnOrCreateCivilian with force; newcomers' data extracted`.

---

### Tâche 3 : l'API 1.2 et ses deux actions

**Files :**
- Modify : `api/src/main/java/dev/hycolony/api/debug/DebugAccess.java`, `api/src/main/java/dev/hycolony/api/ApiVersion.java:12`, `api/api.txt` (régénéré), `api/README.md` (§ 5 et § 8)
- Modify : `core/src/main/java/dev/hycolony/core/app/api/CoreDebugAccess.java`, `CoreDebugActions.java`
- Modify : `plugin/src/main/resources/Server/Languages/en-US/hycolony.lang`, `fr-FR/hycolony.lang`
- Modify : `hylens/core/src/main/java/dev/hylens/core/ApiCompatibility.java:9`, `hylens/core/src/test/java/dev/hylens/core/testing/FakeColonyWorld.java`, `hylens/core/src/test/java/dev/hylens/core/ApiCompatibilityTest.java` (si un attendu cite 1.1)
- Test : `core/src/test/java/dev/hycolony/core/app/api/CoreDebugSpawnTest.java`, `CoreDebugSaturationTest.java`

**Interfaces :**
- Produces : `ActionResult DebugAccess.spawnCitizen(Actor actor, ColonyRef colony)` ; `ActionResult DebugAccess.setSaturation(Actor actor, CitizenRef ref, double value)` ; `ApiVersion.CURRENT = 1.2.0`.

- [ ] **Étape 1 : les tests qui échouent**

`CoreDebugSpawnTest.java` (fixture de `CoreDebugActionsTest`) :

```java
/** The api's spawnCitizen (MC /mc citizens spawnNew, an operator command; spec 2026-10-02 lot 2, § 3). */
class CoreDebugSpawnTest {
    private static final BlockPos HALL = new BlockPos(0, 64, 0);
    private static final Actor PLUGIN = new Actor.Plugin("Tests:HyLens");

    private final TestContexts t = new TestContexts();
    private final ColonyManager manager = t.manager();
    private final DebugAccess debug = new CoreColonyWorld(manager, () -> true).debug();
    private final UUID owner = UUID.randomUUID();
    private final Colony colony;
    private final ColonyRef ref;

    CoreDebugSpawnTest() {
        manager.foundation().begin(owner, "Owner", HALL, 0);
        colony = manager.foundation().confirm(owner, "Rivendell").orElseThrow();
        ref = new ColonyRef("world", colony.id());
    }

    @Test
    void anOperatorSpawnsACitizenEvenWithNewCitizensOff() {
        UUID op = UUID.randomUUID();
        t.players.operators.add(op);
        colony.settings().setMoveIn(false);
        int before = colony.citizens().all().size();

        assertEquals(new ActionResult.Done(), debug.spawnCitizen(new Actor.Player(op), ref));
        assertEquals(before + 1, colony.citizens().all().size());
    }

    @Test
    void aManagerWhoIsNotAnOperatorMayNotSpawnAsMcsOperatorCommand() {
        int before = colony.citizens().all().size();

        assertEquals(
                new ActionResult.Refused(ApiText.of("hycolony.permission.denied", "Rivendell")),
                debug.spawnCitizen(new Actor.Player(owner), ref));
        assertEquals(before, colony.citizens().all().size());
    }

    @Test
    void aPluginMaySpawnAndTheColonyMayNot() {
        assertEquals(new ActionResult.Done(), debug.spawnCitizen(PLUGIN, ref));
        assertEquals(
                new ActionResult.Refused(ApiText.of("hycolony.debug.refused.colony")),
                debug.spawnCitizen(new Actor.Colony(), ref));
    }

    @Test
    void anUnloadedTownHallIsUnavailable() {
        t.world.unloaded.add(HALL);

        assertEquals(new ActionResult.Unavailable(), debug.spawnCitizen(PLUGIN, ref));
    }

    @Test
    void anUnknownColonyIsNotFound() {
        assertEquals(new ActionResult.NotFound(), debug.spawnCitizen(PLUGIN, new ColonyRef("world", 99)));
    }
}
```

`CoreDebugSaturationTest.java` :

```java
/** The api's setSaturation (MC /mc citizens modify saturation; spec 2026-10-02 lot 2, § 3). */
class CoreDebugSaturationTest {
    private static final BlockPos HALL = new BlockPos(0, 64, 0);
    private static final Actor PLUGIN = new Actor.Plugin("Tests:HyLens");

    /** One colony with citizen 1, its managers allowed to modify citizens or not. */
    private record World(DebugAccess debug, Colony colony, CitizenData citizen, CitizenRef ref, UUID owner) {}

    private static World world(boolean managersMayModify) {
        TestContexts t = new TestContexts();
        ColonyConfig d = ColonyConfig.defaults();
        t.config = new ColonyConfig(
                d.gameplay(),
                d.claims(),
                d.permissions(),
                new ColonyConfig.Commands(true, true, false, managersMayModify),
                d.client(),
                d.hycolony(),
                d.structurize());
        ColonyManager manager = t.manager();
        UUID owner = UUID.randomUUID();
        manager.foundation().begin(owner, "Owner", HALL, 0);
        Colony colony = manager.foundation().confirm(owner, "Rivendell").orElseThrow();
        CitizenData citizen = new CitizenData(1);
        colony.citizens().restore(citizen);
        CitizenRef ref = new CitizenRef(new ColonyRef("world", colony.id()), 1);
        return new World(new CoreColonyWorld(manager, () -> true).debug(), colony, citizen, ref, owner);
    }

    @Test
    void theSaturationIsSetKeptBetweenZeroAndTheMaximumAndSaved() {
        World w = world(false);
        w.colony().clearDirty();

        assertEquals(new ActionResult.Done(), w.debug().setSaturation(PLUGIN, w.ref(), 12.5));
        assertEquals(12.5, w.citizen().saturation());
        assertTrue(w.colony().isDirty());
        w.debug().setSaturation(PLUGIN, w.ref(), 99);
        assertEquals(CitizenData.MAX_SATURATION, w.citizen().saturation());
        w.debug().setSaturation(PLUGIN, w.ref(), -3);
        assertEquals(0, w.citizen().saturation());
    }

    @Test
    void notANumberIsRefused() {
        World w = world(false);
        double before = w.citizen().saturation();

        assertEquals(
                new ActionResult.Refused(ApiText.of("hycolony.debug.refused.value")),
                w.debug().setSaturation(PLUGIN, w.ref(), Double.NaN));
        assertEquals(before, w.citizen().saturation());
    }

    @Test
    void aManagerNeedsTheServerSettingAsMc() {
        World off = world(false);
        assertEquals(
                new ActionResult.Refused(ApiText.of("hycolony.debug.refused.config")),
                off.debug().setSaturation(new Actor.Player(off.owner()), off.ref(), 10));
        assertEquals(
                new ActionResult.Refused(ApiText.of("hycolony.debug.refused.config")),
                off.debug().setSaturation(new Actor.Player(off.owner()), new CitizenRef(off.ref().colony(), 42), 10),
                "refused before the citizen is looked up, as MC");

        World on = world(true);
        assertEquals(new ActionResult.Done(), on.debug().setSaturation(new Actor.Player(on.owner()), on.ref(), 10));
    }

    @Test
    void anUnknownCitizenIsNotFound() {
        World w = world(false);

        assertEquals(
                new ActionResult.NotFound(),
                w.debug().setSaturation(PLUGIN, new CitizenRef(w.ref().colony(), 42), 10));
    }
}
```

(imports : ceux de `CoreDebugActionsTest` plus `ColonyConfig`.)

- [ ] **Étape 2 :** `./gradlew :core:test --tests "*CoreDebugSpawnTest" --tests "*CoreDebugSaturationTest"` → échec de compilation.
- [ ] **Étape 3 : l'API**

`DebugAccess` gagne, après `respawnBody` :

```java
    /**
     * A new citizen arrives at the colony's town hall (MC {@code /mc citizens spawnNew}), even with "new citizens" off
     * and beyond the colony's room. Operators only, and plugins; {@link ActionResult.Unavailable} without a loaded town
     * hall or room for its body there, nothing created then.
     *
     * @since 1.2
     */
    ActionResult spawnCitizen(Actor actor, ColonyRef colony);

    /**
     * Sets the citizen {@code ref}'s saturation to {@code value}, kept between 0 and its maximum (MC
     * {@code /mc citizens modify saturation}); a value that is not a number is refused. A colony manager who is not an
     * operator needs the server's Commands.CanPlayerUseModifyCitizensCommand.
     *
     * @since 1.2
     */
    ActionResult setSaturation(Actor actor, CitizenRef ref, double value);
```

`ApiVersion.CURRENT = new ApiVersion(1, 2, 0)`. `README.md` § 5 : après la liste des acteurs, « `spawnCitizen` is for operators only (MC's `spawnNew`), and `setSaturation` lets a colony manager act only if the server's `Commands.CanPlayerUseModifyCitizensCommand` allows it (MC's `modify`). Both since 1.2. » ; § 3 et § 8 inchangés sinon.

- [ ] **Étape 4 : le cœur**

`CoreDebugAccess` délègue (`return actions.spawnCitizen(actor, colony);`, `return actions.setSaturation(actor, ref, value);`). `CoreDebugActions` :

```java
    /**
     * MC CommandCitizenSpawnNew (an IMCOPCommand): a citizen arrives by force ({@code CitizenManager.spawnForced});
     * unavailable when none could.
     */
    ActionResult spawnCitizen(Actor actor, ColonyRef ref) {
        world.checkThread();
        Colony c = world.find(ref).orElse(null);
        if (c == null) {
            return new ActionResult.NotFound();
        }
        return operatorRefusal(actor, c)
                .<ActionResult>map(ActionResult.Refused::new)
                .orElseGet(() -> c.citizens().spawnForced() ? new ActionResult.Done() : new ActionResult.Unavailable());
    }

    /**
     * MC CommandCitizenModify saturation: sets it, kept between 0 and the maximum, and saves; refused for a value that
     * is not a number, and for a manager who is not an operator unless the server allows it (checked before the
     * citizen, as MC).
     */
    ActionResult setSaturation(Actor actor, CitizenRef ref, double value) {
        if (Double.isNaN(value)) {
            return new ActionResult.Refused(ApiText.of("hycolony.debug.refused.value"));
        }
        return act(actor, ref, CoreDebugActions::modifyRefusal, (c, d) -> {
            d.setSaturation(Math.clamp(value, 0, CitizenData.MAX_SATURATION));
            c.markDirty();
            return new ActionResult.Done();
        });
    }

    /** Why {@code actor} may not act as an operator on {@code c} (MC IMCOPCommand); a plugin may. */
    private static Optional<ApiText> operatorRefusal(Actor actor, Colony c) {
        return switch (actor) {
            case Actor.Colony _ -> Optional.of(ApiText.of("hycolony.debug.refused.colony"));
            case Actor.Plugin _ -> Optional.empty();
            case Actor.Player p ->
                c.context().players().isOperator(p.id())
                        ? Optional.empty()
                        : Optional.of(ApiText.of("hycolony.permission.denied", c.name()));
        };
    }

    /** {@link #refusal}, then MC CommandCitizenModify's canPlayerUseModifyCitizensCommand for a non-operator player. */
    private static Optional<ApiText> modifyRefusal(Actor actor, Colony c) {
        return refusal(actor, c).or(() -> actor instanceof Actor.Player p
                        && !c.context().players().isOperator(p.id())
                        && !c.context().config().commands().canPlayerUseModifyCitizensCommand()
                ? Optional.of(ApiText.of("hycolony.debug.refused.config"))
                : Optional.empty());
    }
```

`act(actor, ref, action)` devient `act(actor, ref, CoreDebugActions::refusal, action)` : une surcharge à quatre paramètres prend la règle `BiFunction<Actor, Colony, Optional<ApiText>>` et remplace l'appel direct à `refusal`. Si `CoreDebugActions` dépasse 150 lignes, `spawnCitizen`, `setSaturation`, `operatorRefusal` et `modifyRefusal` vont dans `CoreDebugEdits` (même paquet), auquel `CoreDebugAccess` délègue.

- [ ] **Étape 5 : textes de HyColony**

en-US (`hycolony.lang`, après `debug.refused.colony`) :

```
debug.refused.config = This command is disabled in the server's configuration.
debug.refused.value = That value is not a number.
```

fr-FR :

```
debug.refused.config = Cette commande est désactivée dans la configuration du serveur.
debug.refused.value = Cette valeur n'est pas un nombre.
```

- [ ] **Étape 6 : HyLens suit la 1.2**

`ApiCompatibility.BUILT_AGAINST = new ApiVersion(1, 2, 0)`. `FakeColonyWorld` gagne :

```java
    @Override
    public ActionResult spawnCitizen(Actor actor, ColonyRef colony) {
        return new ActionResult.Done();
    }

    @Override
    public ActionResult setSaturation(Actor actor, CitizenRef ref, double value) {
        return new ActionResult.Done();
    }
```

`ApiCompatibilityTest` : tout attendu qui cite la 1.1 comme version de construction passe à la 1.2 ; ajouter `assertFalse(ApiCompatibility.accepts(new ApiVersion(1, 1, 0)), "HyColony 1.1 lacks spawnCitizen and setSaturation");`.

- [ ] **Étape 7 :** `./gradlew :api:apiDump` puis `./gradlew build` → BUILD SUCCESSFUL ; `git diff api/api.txt` montre les deux méthodes.
- [ ] **Étape 8 : commit** `feat(api): spawnCitizen and setSaturation (API 1.2), as MC's spawnNew and modify saturation` (api, core, lang, hylens core et tests).

---

### Tâche 4 : la saturation dans le menu (cœur de HyLens)

**Files :**
- Create : `hylens/core/src/main/java/dev/hylens/core/menu/SaturationStep.java`
- Modify : `hylens/core/src/main/java/dev/hylens/core/menu/MenuView.java`, `MenuViews.java`, `ActionReport.java`
- Modify : `hylens/core/src/test/java/dev/hylens/core/testing/FakeColonyWorld.java`
- Test : `SaturationStepTest.java`, `MenuViewsTest.java`, `ActionReportTest.java`

**Interfaces :**
- Produces : `public enum SaturationStep { ZERO, LESS, MORE, MAX; public double from(double current, double max) }` ; `MenuView.Saturation(double value, double max)` ; `MenuView.CitizenRow.saturation() : Optional<MenuView.Saturation>` (dernière composante) ; `ActionReport.spawned(ActionResult) : ApiText`.

- [ ] **Étape 1 : les tests qui échouent**

```java
/** The menu's saturation buttons, as MC's suggestions (spec 2026-10-02 lot 2, § 5). */
class SaturationStepTest {
    @Test
    void zeroAndMaxAreMcsSuggestionsForEquals() {
        assertEquals(0, SaturationStep.ZERO.from(42, 60));
        assertEquals(60, SaturationStep.MAX.from(42, 60));
    }

    @Test
    void lessAndMoreStepByOneAsMcsSuggestion() {
        assertEquals(41, SaturationStep.LESS.from(42, 60));
        assertEquals(43, SaturationStep.MORE.from(42, 60));
    }

    @Test
    void theStepStaysBetweenZeroAndTheMaximum() {
        assertEquals(0, SaturationStep.LESS.from(0.5, 60));
        assertEquals(60, SaturationStep.MORE.from(59.5, 60));
    }
}
```

`MenuViewsTest` : les deux `new MenuView.CitizenRow(…)` attendus gagnent `Optional.empty()` en dernier ; ajouter

```java
    @Test
    void aRowShowsTheSaturationReadFromHyColony() {
        world.wellbeing(new CitizenWellbeing(ANN, 42, 60, 7, List.of()));

        MenuView v = MenuViews.of(world, false, MenuState.INITIAL.withColony(A), Optional.empty());

        assertEquals(
                Optional.of(new MenuView.Saturation(42, 60)),
                v.citizens().stream().filter(r -> r.ref().equals(ANN)).findFirst().orElseThrow().saturation());
    }
```

`ActionReportTest` :

```java
    @Test
    void aSpawnThatCannotHappenSaysWhy() {
        assertEquals(ApiText.of("hylens.action.spawnUnavailable"), ActionReport.spawned(new ActionResult.Unavailable()));
        assertEquals(ApiText.of("hylens.action.colonyNotFound"), ActionReport.spawned(new ActionResult.NotFound()));
        assertEquals(ApiText.of("hylens.action.done"), ActionReport.spawned(new ActionResult.Done()));
    }
```

- [ ] **Étape 2 :** `./gradlew :hylens-core:test` → échec de compilation.
- [ ] **Étape 3 : le code**

```java
package dev.hylens.core.menu;

/**
 * The menu's saturation buttons (spec 2026-10-02 lot 2, § 5), MC's suggestions for /mc citizens modify saturation: "="
 * 0 or the maximum, "+" and "-" 1.
 */
public enum SaturationStep {
    ZERO,
    LESS,
    MORE,
    MAX;

    /** What "+" and "-" add or take (MC's suggestion). */
    static final double STEP = 1.0;

    /** The saturation this button asks, from {@code current}, kept between 0 and {@code max}. */
    public double from(double current, double max) {
        double asked = switch (this) {
            case ZERO -> 0;
            case LESS -> current - STEP;
            case MORE -> current + STEP;
            case MAX -> max;
        };
        return Math.clamp(asked, 0, max);
    }
}
```

`MenuView` : `public record Saturation(double value, double max) {}` (Javadoc : « A citizen's saturation, as HyColony read it, and its maximum. ») ; `CitizenRow` gagne `Optional<Saturation> saturation` en dernier, sa Javadoc « …, and its saturation (empty when HyColony reads none) ». `MenuViews.row` passe `world.wellbeing(m.ref()).map(w -> new MenuView.Saturation(w.saturation(), w.maxSaturation()))`.

`ActionReport.spawned` :

```java
    /** The text of a spawn's {@code result}: as {@link #text}, but its colony and town hall named when it failed. */
    public static ApiText spawned(ActionResult result) {
        return switch (result) {
            case ActionResult.NotFound _ -> ApiText.of("hylens.action.colonyNotFound");
            case ActionResult.Unavailable _ -> ApiText.of("hylens.action.spawnUnavailable");
            default -> text(result);
        };
    }
```

`FakeColonyWorld` : une `Map<CitizenRef, CitizenWellbeing> wellbeing`, un constructeur fluide `public FakeColonyWorld wellbeing(CitizenWellbeing w)`, et `wellbeing(ref)` la lit ; la Javadoc « None » est retirée.

- [ ] **Étape 4 :** `./gradlew :hylens-core:test :hylens-plugin:compileJava` → PASS.
- [ ] **Étape 5 : commit** `feat(hylens-core): the menu reads each citizen's saturation and MC's saturation steps`.

---

### Tâche 5 : les boutons dans le livre

**Files :**
- Modify : `hylens/plugin/src/main/resources/Common/UI/Custom/Pages/HyLens/Book/Colonies.ui`, `Book/Citizens.ui`, `Mc/Book.ui` ; copier `builder_button_very_small{,_hover,_dim}@2x.png` de `Pages/HyColony/Mc/`
- Modify : `hylens/plugin/src/main/java/dev/hylens/plugin/command/book/ColoniesTab.java`, `CitizensTab.java`, `command/MenuClicks.java`, `MenuActions.java`, `MenuPage.java`
- Modify : `hylens.lang` (en-US, fr-FR), `docs/TESTING.md`

**Interfaces :**
- Consumes : `DebugAccess.spawnCitizen`, `setSaturation` (tâche 3) ; `SaturationStep`, `MenuView.Saturation`, `CitizenRow.saturation()`, `ActionReport.spawned` (tâche 4).

- [ ] **Étape 1 : `Book.ui`**

`@CompactInkCentered` gagne `ShrinkTextToFit: true, MinShrinkTextToFitFontSize: 9` (motif de `Pages/HyColony/TownHall/Actions.ui:30`). Ajouter :

```
// MC's builder_button_very_small (29 x 15), doubled: the saturation's "0" and "Max".
@VerySmallButtonStyle = TextButtonStyle(
  Default: (Background: "builder_button_very_small.png", LabelStyle: @CompactInkCentered),
  Hovered: (Background: "builder_button_very_small_hover.png", LabelStyle: @CompactInkCentered),
  Pressed: (Background: "builder_button_very_small_hover.png", LabelStyle: @CompactInkCentered),
  Disabled: (Background: "builder_button_very_small_dim.png", LabelStyle: @CompactInkCentered),
  Sounds: $C.@ButtonSounds
);
```

et copier les trois textures (`cp plugin/.../Pages/HyColony/Mc/builder_button_very_small{,_hover,_dim}@2x.png hylens/plugin/.../Pages/HyLens/Mc/`).

- [ ] **Étape 2 : `Citizens.ui`** (page de droite, de haut en bas)

- `#WatchButton` / `#FreeButton` / `#UnwatchButton` : inchangés (Top 116) ;
- `#LeisureButton` : `Anchor: (Left: 552, Top: 156, Width: 128, Height: 34)`, `Style: $B.@SmallCompactButtonStyle` ; `#TeleportButton` : `Left: 720, Top: 156`, même style ; `#RespawnButton` : `Left: 552, Top: 196`, même style ;
- un groupe `Group #SaturationRow { Anchor: (Left: 552, Top: 238, Width: 296, Height: 30); Visible: false; … }` contenant, en coordonnées relatives : `Label #Saturation` (`Left: 0, Width: 110, Height: 30`, `Style: $B.@Ink`), `TextButton #SatZeroButton` (`Left: 112, Width: 58`, `@VerySmallButtonStyle`, `Text: %hylens.menu.saturation.zero`), `#SatLessButton` (`Left: 174, Width: 28`, `@MiniButtonStyle`, `%hylens.menu.saturation.less`), `#SatMoreButton` (`Left: 206, Width: 28`, `@MiniButtonStyle`, `%hylens.menu.saturation.more`), `#SatMaxButton` (`Left: 238, Width: 58`, `@VerySmallButtonStyle`, `%hylens.menu.saturation.max`), chacun `Height: 30` ;
- « Envoyer ici » et ses champs : inchangés (Top 284 à 390).

- [ ] **Étape 3 : `Colonies.ui`**

```
  TextButton #SpawnButton {
    Anchor: (Left: 571, Top: 172, Width: 258, Height: 34);
    Style: $B.@WideButtonStyle;
    Text: %hylens.menu.spawn;
  }
```

- [ ] **Étape 4 : les rendus**

`ColoniesTab.render` : `binds.on("#SpawnButton", "spawn", "");`.

`CitizensTab.render`, après `#ChosenState` :

```java
        Optional<MenuView.Saturation> saturation = chosen.flatMap(MenuView.CitizenRow::saturation);
        ui.set("#SaturationRow.Visible", saturation.isPresent());
        saturation.ifPresent(s -> ui.set(
                "#Saturation.TextSpans",
                Message.translation("hylens.menu.saturation")
                        .param("p0", String.format(Locale.ROOT, "%.1f", s.value()))
                        .param("p1", String.valueOf((int) s.max()))));
        for (SaturationStep step : SaturationStep.values()) {
            binds.on(SATURATION_BUTTONS.get(step), "saturation", step.name());
        }
```

avec `private static final Map<SaturationStep, String> SATURATION_BUTTONS = Map.of(SaturationStep.ZERO, "#SatZeroButton", SaturationStep.LESS, "#SatLessButton", SaturationStep.MORE, "#SatMoreButton", SaturationStep.MAX, "#SatMaxButton");` (le format est celui du HUD, `WatchHudView.tenth`). Si `render` dépasse 40 lignes, la saturation va dans une méthode privée `saturation(ui, binds, chosen)`.

- [ ] **Étape 5 : les clics**

`MenuClicks` : `static final Set<String> EDITS = Set.of("spawn", "saturation");` et `static Optional<SaturationStep> saturation(String name)` sur le modèle de `tab`.

`MenuActions` :

```java
    /**
     * Runs the edit {@code action}: "spawn" a citizen in {@code v}'s chosen colony, or set the chosen citizen's
     * "saturation" by the step named {@code index}, from the saturation HyColony reads now; as {@code operator}. The
     * text of its result, or asks to choose first; empty for an unknown step or an unread saturation.
     */
    static Optional<ApiText> edit(
            String action, String index, MenuView v, Optional<ColonyWorld> colonies, UUID operator) {
        Actor actor = new Actor.Player(operator);
        if ("spawn".equals(action)) {
            return Optional.of(colonies.flatMap(w -> v.colony().map(c -> ActionReport.spawned(w.debug().spawnCitizen(actor, c))))
                    .orElse(ApiText.of("hylens.action.noColony")));
        }
        if (v.citizen().isEmpty() || colonies.isEmpty()) {
            return Optional.of(ApiText.of("hylens.action.noneChosen"));
        }
        CitizenRef c = v.citizen().get();
        ColonyWorld w = colonies.get();
        return MenuClicks.saturation(index)
                .flatMap(step -> w.wellbeing(c)
                        .map(now -> ActionReport.text(w.debug()
                                .setSaturation(actor, c, step.from(now.saturation(), now.maxSaturation())))));
    }
```

(lignes repliées à 120 colonnes par spotless.)

`MenuPage` : `dispatch` passe `index` à `act` ; `act` devient

```java
    /** Runs the action {@code action} named by {@code index}; its result shows under the right page. */
    private void act(String action, String index, MenuView v, Ref<EntityStore> ref, Store<EntityStore> store) {
        UUID operator = playerRef.getUuid();
        (MenuClicks.EDITS.contains(action)
                        ? MenuActions.edit(action, index, v, colonies(store), operator)
                        : MenuActions.run(action, v.citizen(), colonies(store), operator, MenuActions.feet(ref, store)))
                .ifPresent(r -> result = Optional.of(r));
    }
```

(si PMD refuse le couplage de `MenuPage`, déplacer le choix entre `edit` et `run` dans `MenuActions.click(...)`.)

- [ ] **Étape 6 : les textes de HyLens**

en-US :

```
menu.spawn = New citizen
menu.saturation = Saturation: {p0}/{p1}
menu.saturation.zero = 0
menu.saturation.less = -
menu.saturation.more = +
menu.saturation.max = Max
action.noColony = Choose a colony first.
action.colonyNotFound = HyColony no longer knows this colony.
action.spawnUnavailable = Unavailable: the town hall is not loaded, or has no room for a citizen.
```

fr-FR :

```
menu.spawn = Nouveau citoyen
menu.saturation = Saturation : {p0}/{p1}
menu.saturation.zero = 0
menu.saturation.less = -
menu.saturation.more = +
menu.saturation.max = Max
action.noColony = Choisissez d'abord une colonie.
action.colonyNotFound = HyColony ne connaît plus cette colonie.
action.spawnUnavailable = Indisponible : l'hôtel de ville n'est pas chargé, ou n'a pas de place pour un citoyen.
```

- [ ] **Étape 7 : essais en jeu** (section « HyLens : citoyens », à la suite du dernier essai au moment du commit)

- **Nouveau citoyen.** Onglet Colonies, colonie choisie : « Nouveau citoyen » → « Fait. », un citoyen de plus dans la liste et devant l'hôtel de ville, une ligne au journal de la mairie. Avec « Nouveaux citoyens » coupé dans la mairie, et au-delà de 4 citoyens : ça marche aussi.
- **Sans place.** Murer l'hôtel de ville (aucune place autour), « Nouveau citoyen » → « Indisponible : … », aucun citoyen ajouté, le message « pas de place » de la colonie.
- **Saturation.** Onglet Citoyens, un citoyen choisi : « Saturation : x/60 » ; 0, −, +, Max changent la valeur, bornée à 0 et 60 ; le HUD du suivi suit ; la valeur survit à un redémarrage.
- **Gestionnaire.** Un joueur gestionnaire non opérateur (avec /hylens accordé) : « Nouveau citoyen » refusé ; saturation refusée « désactivée dans la configuration » ; avec `"CanPlayerUseModifyCitizensCommand": true` dans `config.json` (section `Commands`), la saturation marche.

- [ ] **Étape 8 :** `./gradlew build` → BUILD SUCCESSFUL ; commit `feat(hylens-plugin): a New citizen button and the chosen citizen's saturation buttons`.

---

### Tâche 6 : relectures

- [ ] `hycolony-reviewer` sur les commits du lot, `mc-fidelity-checker` sur les tâches 2 et 3 (MC `spawnOrCreateCivilian`, `CommandCitizenSpawnNew`, `CommandCitizenModify`), `ui-lang-checker` sur la tâche 5 ; corriger, faire relire les corrections ; feu vert à l'utilisateur.
