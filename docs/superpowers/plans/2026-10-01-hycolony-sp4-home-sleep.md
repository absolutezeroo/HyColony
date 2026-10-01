# SP4 maison, lit et sommeil : plan d'implémentation

> **Pour les agents :** sous-skill requis : superpowers:subagent-driven-development (recommandé) ou superpowers:executing-plans. Étapes à cocher (`- [ ]`).

**Objectif :** les citoyens ont une maison. Le soir, ils rentrent, se couchent dans un vrai lit de leur résidence et se lèvent à l'aube, comme dans MineColonies. Le travail s'arrête la nuit.

**Architecture :**
- **Cœur, maison** (`dev.hycolony.core.citizen.home`) : `LivingModule` (déplacé depuis `construction/hut`), `BedModule`, `HousingCapacity`, `HomePosition`, la vue `ResidentsView` et son constructeur `ResidentsViews`.
- **Cœur, sommeil** (`dev.hycolony.core.citizen.sleep`) : `SleepDecision` (MC `calculateNextState` + `shouldGoSleep`), `SleepHandler` (MC `CitizenSleepHandler`), `SleepAI` (MC `EntityAISleep`) et `SleepNotice` (message « tous dorment »).
- **Cœur, ailleurs** :
  - `colony/HutFootprint` : l'emprise d'une hutte ;
  - `app/action/HousingActions` : les boutons de l'onglet ;
  - `app/persistence/HousingHeal` : la réparation au chargement ;
  - `CitizenAI` branche l'état `SLEEP`, et `CitizenWander` sort de `CitizenAI` pour lui faire de la place.
- **Plugin** : `isBed` dans le catalogue, l'onglet « Habitants », les statistiques, l'horloge calée par phases, le coucher natif (`npc/CitizenBeds`), les particules et les clés de langue.

**Pourquoi `citizen/home` et pas `construction/hut`** : la matrice figée de `FeatureDependenciesTest` interdit à `citizen` de dépendre de `construction`, et l'IA de sommeil doit lire les habitants et les lits. Placés sous `citizen`, ces modules ne dépendent que de `building`, `colony`, `job` et `kernel`, qui sont permis, et `construction` (`ConstructionBuildingTypes`) les déclare sur la résidence. La spec dit `construction/hut` : la Task 1 corrige ce chemin.

**Pile technique :** Java 25, Gradle, JUnit 5, Gson, ArchUnit, palantir-java-format (Spotless), PMD, Error Prone et NullAway ; API serveur Hytale 0.7.0-pre.4 (`build/vineflower/hytale-server`).

**Spec :** `docs/superpowers/specs/2026-10-01-hycolony-sp4-home-sleep-design.md` (« S § »). À lire en entier, avec :
- `CLAUDE.md` ;
- `docs/research/sp4-sleep-home.md` (« R § », surtout la **section E**, l'audit sur les sources locales) ;
- `docs/research/plugin-b-api.md` § 41 (« API § 41 »).

Sources MC locales : `C:\Users\Ctuto\Desktop\HyColony\sources\minecolonies\src\main\java\com\minecolonies\`. Ce dossier est ignoré par git : passe ce chemin à Grep. Relis la méthode MC avant de la porter. Les textes de langue de MC absents de `sources/` se lisent sur GitHub (`ldtteam/minecolonies`, `version/main`, `src/main/resources/assets/minecolonies/lang/manual_en_us.json`).

**Qui fait quoi :**
- **Tasks 1 à 7 et 10 à 16 (cœur)** : Java pur.
- **Tasks 8, 9, 17 et 18 (plugin, docs)** : demandent les sources décompilées, donc se font **en local**.

L'étape 1 (Tasks 1 à 9) est testable en jeu seule. L'utilisateur peut la tester avant l'étape 2.

## Contraintes globales

- `core/` ne touche jamais `com.hypixel` **[ArchitectureTest]**. Aucun nouveau lien entre paquets de premier niveau **[FeatureDependenciesTest, matrice figée]**.
- **Paquets déjà pleins (15 fichiers) :** `kernel/port`, `construction/builder`, `request`. On n'y ajoute **aucun fichier**, seulement des méthodes. `colony` est à 14 : `HutFootprint` sera son 15e et dernier fichier.
- Taille : 400 lignes au plus par fichier (300 visées), 40 lignes par méthode, 5 paramètres **[checkFileSizes, PMD]**. `CitizenAI` (330 lignes) et `HytaleCitizenBodies` (387) sont découpés **avant** qu'on leur ajoute quoi que ce soit (Tasks 11 et 17).
- Javadoc courte sur chaque classe et méthode non triviale, avec sa source MC. Un écart porte un commentaire `Deviation from MC: …`. Pas de commentaire séparateur.
- **Constantes MC exactes :**
  - `NIGHT = 12600`, soir à `NIGHT - 2000` ;
  - `Y_DIFF_WEIGHT = 1.5`, `TIME_PER_BLOCK = 6` ;
  - `RANGE_TO_BE_HOME = 16` (distance au carré) ;
  - `MAX_BED_TICKS = 10`, cadences du sommeil 20/30/30/30 ticks, redécision toutes les `20 * 15` ticks en dormant ;
  - `FAR_DISTANCE_THRESHOLD = 300`, `MAX_NO_COMPLAIN_DISTANCE = 160` (plainte reportée, S § 5.1) ;
  - portée dans la hutte : 12 (`walkToPosInBuilding(…, 12)`).
- **Persistance :** le schéma passe de 5 à 6 (Task 5), avec la fixture `colony-v5-homes.json`. Lecture tolérante.
- **Textes :** clés en en-US **et** fr-FR (skill `add-lang-key`) ; un paramètre traduit imbriqué va sur `.TextSpans` ; sur un bouton, une clé complète par variante.
- **Processus :**
  - TDD ;
  - `./gradlew build` vert avant chaque commit ;
  - `git add <chemins>` explicites. **D'autres sessions travaillent dans le même dossier** : vérifie `git status` avant chaque commit, n'indexe que tes fichiers, et formate fichier par fichier (`./gradlew :core:spotlessApply -PspotlessIdeHook="<chemin absolu>"`) ;
  - commits en `type(module): description` ;
  - **ne jamais lancer le serveur Hytale**.
- Fin de message de commit : les lignes demandées par la session qui exécute.

## Points à surveiller en relecture

1. **Une résidence retirée pendant la nuit, alors que ses habitants dorment** : chacun garde son lit Hytale jusqu'à son réveil. La décision suivante le traite en sans-abri, le réveil à l'aube le descend, et rien ne reste bloqué (Task 15, `removedHomeAtNightStillWakesAtDawn`).
2. **Un lit cassé, ou une nuit passée par les joueurs, sous un citoyen endormi** : `isInBed` devient faux. `sleep()` repart en `WALKING_HOME`, sans appeler les crochets de réveil, et le citoyen se recouche (Task 14, `bedLeftWithoutUsLiesDownAgain`).
3. **L'heure du monde en pause** : `realTicksUntil` renvoie `Long.MAX_VALUE`, donc personne ne part en avance, et `dayTime` ne bouge plus. Pas d'exception, pas de boucle (Task 12, `pausedClockNeverSendsHomeEarly`).
4. **Deux résidences qui listent le même habitant dans une vieille sauvegarde** : le chargement le donne à la dernière, comme `assignCitizen` dans l'ordre. Il n'apparaît que dans une liste (Task 5, `citizenListedInTwoHomesEndsInOne`).
5. **Un habitant sans corps à la tombée de la nuit** : son IA n'existe pas, donc il ne décide rien. À l'aube, il réapparaît (Task 15, `bodilessCitizenRespawnsAtDawn`), et il n'est jamais compté parmi « tous dorment » à tort (Task 13, `allAsleepNeedsEveryCitizenAsleep`).

---

# Étape 1 : la résidence

### Task 1 : `LivingModule` attribue les maisons

**Fichiers :**
- Déplacer : `core/src/main/java/dev/hycolony/core/construction/hut/LivingModule.java` vers `core/src/main/java/dev/hycolony/core/citizen/home/LivingModule.java` (`git mv`)
- Modifier : `core/src/main/java/dev/hycolony/core/construction/hut/ConstructionBuildingTypes.java` (import)
- Modifier : `core/src/main/java/dev/hycolony/core/citizen/CitizenData.java` (`bedPos`, `asleep`)
- Modifier : `core/src/main/java/dev/hycolony/core/colony/ColonySettings.java` (`autoHousing`)
- Modifier : `docs/superpowers/specs/2026-10-01-hycolony-sp4-home-sleep-design.md` (chemins `construction/hut/LivingModule` et `construction/hut/BedModule` vers `citizen/home/…`, avec la raison en une ligne)
- Test : `core/src/test/java/dev/hycolony/core/citizen/home/LivingModuleTest.java`

**Interfaces :**
- Produit :
  - `CitizenData` : `@Nullable BlockPos bedPos()`, `setBedPos(@Nullable BlockPos)`, `boolean asleep()`, `setAsleep(boolean)` (vrai remet `leisureTime` à 0, MC `setAsleep`) ;
  - `ColonySettings` : `boolean autoHousing()`, `setAutoHousing(boolean)`, défaut `true` ;
  - `LivingModule` (`PersistentModule`, `TickingModule`, `BuildingEventsModule`) :
    - `int max(Building)`, `boolean isFull(Building)`, `List<Integer> residents()` ;
    - `HiringMode hiringMode()`, `setHiringMode(HiringMode)` ;
    - `boolean autoHousing(Colony)`, `boolean manual(Colony)` ;
    - `boolean assign(Colony, Building, CitizenData)`, `boolean remove(Colony, Building, int citizenId)` ;
    - `List<Integer> takeSavedResidents()` (Task 5).

- [ ] **Step 1 : le test qui échoue**

```java
package dev.hycolony.core.citizen.home;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import dev.hycolony.core.building.Building;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.permission.Permissions;
import dev.hycolony.core.colony.territory.TerritoryIndex;
import dev.hycolony.core.construction.hut.ConstructionBuildingTypes;
import dev.hycolony.core.job.HiringMode;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.testing.TestContexts;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LivingModuleTest {
    private final TestContexts t = new TestContexts();

    private Colony colony(int citizens) {
        Colony c = new Colony(
                t.context(),
                new TerritoryIndex(),
                new Colony.Founding(1, "T", new BlockPos(0, 64, 0), Permissions.createDefault(UUID.randomUUID(), "A")));
        for (int id = 1; id <= citizens; id++) {
            c.citizens().restore(new CitizenData(id));
        }
        return c;
    }

    private Building residence(Colony c, BlockPos at, int level) {
        Building b = Building.create(ConstructionBuildingTypes.RESIDENCE, at, 0);
        b.setLevel(level);
        b.setBuilt(level > 0);
        c.buildings().add(b);
        return b;
    }

    private static LivingModule living(Building b) {
        return b.module(LivingModule.class).orElseThrow();
    }

    private static CitizenData citizen(Colony c, int id) {
        return c.citizens().get(id).orElseThrow();
    }

    @Test
    void autoHousingFillsTheHouseWithTheHomelessInOneTick() {
        Colony c = colony(3);
        Building b = residence(c, new BlockPos(8, 64, 0), 2);

        c.buildings().onColonyTick(c);

        assertEquals(List.of(1, 2), living(b).residents()); // MC: as many as fit, in citizen order
        assertEquals(b.position(), citizen(c, 1).homeBuilding());
        assertNull(citizen(c, 3).homeBuilding());
    }

    @Test
    void manualLockedOrColonyAutoHousingOffTakeNobody() {
        for (HiringMode mode : List.of(HiringMode.MANUAL, HiringMode.LOCKED, HiringMode.DEFAULT)) {
            Colony c = colony(1);
            c.settings().setAutoHousing(mode != HiringMode.DEFAULT);
            Building b = residence(c, new BlockPos(8, 64, 0), 1);
            living(b).setHiringMode(mode);

            c.buildings().onColonyTick(c);

            assertTrue(living(b).residents().isEmpty(), mode.name());
        }
    }

    @Test
    void levelZeroIsFullAndAutoModeIgnoresTheColonySetting() {
        Colony c = colony(1);
        c.settings().setAutoHousing(false);
        Building zero = residence(c, new BlockPos(8, 64, 0), 0);
        Building one = residence(c, new BlockPos(20, 64, 0), 1);
        living(one).setHiringMode(HiringMode.AUTO);

        assertFalse(living(zero).assign(c, zero, citizen(c, 1)));
        c.buildings().onColonyTick(c);

        assertEquals(List.of(1), living(one).residents());
    }

    @Test
    void assignRefusesDuplicatesAndFullHouses() {
        Colony c = colony(2);
        Building b = residence(c, new BlockPos(8, 64, 0), 1);

        assertTrue(living(b).assign(c, b, citizen(c, 1)));
        assertFalse(living(b).assign(c, b, citizen(c, 1)));
        assertFalse(living(b).assign(c, b, citizen(c, 2)));
    }

    @Test
    void movingHouseLeavesTheOldOneAndForgetsTheBed() {
        Colony c = colony(1);
        Building first = residence(c, new BlockPos(8, 64, 0), 1);
        Building second = residence(c, new BlockPos(20, 64, 0), 1);
        living(first).assign(c, first, citizen(c, 1));
        citizen(c, 1).setBedPos(new BlockPos(9, 64, 0));

        assertTrue(living(second).assign(c, second, citizen(c, 1)));

        assertTrue(living(first).residents().isEmpty());
        assertEquals(second.position(), citizen(c, 1).homeBuilding());
        assertNull(citizen(c, 1).bedPos());
    }

    @Test
    void housedCitizenIsNeverTakenByAnotherHouse() {
        Colony c = colony(1);
        Building first = residence(c, new BlockPos(8, 64, 0), 1);
        living(first).assign(c, first, citizen(c, 1));
        Building second = residence(c, new BlockPos(20, 64, 0), 1);

        c.buildings().onColonyTick(c);

        assertTrue(living(second).residents().isEmpty());
    }

    @Test
    void removeAndRemovalMakeHomelessWithoutWakingUp() {
        Colony c = colony(2);
        Building b = residence(c, new BlockPos(8, 64, 0), 2);
        c.buildings().onColonyTick(c);
        citizen(c, 1).setAsleep(true);

        assertTrue(living(b).remove(c, b, 1));
        assertNull(citizen(c, 1).homeBuilding());
        assertTrue(citizen(c, 1).asleep()); // MC LivingBuildingModule.onRemoval only clears the home and bed

        living(b).onRemoved(c, b);
        assertNull(citizen(c, 2).homeBuilding());
        assertTrue(living(b).residents().isEmpty());
    }

    @Test
    void residentsAndModeRoundTrip() {
        Colony c = colony(1);
        Building b = residence(c, new BlockPos(8, 64, 0), 1);
        living(b).assign(c, b, citizen(c, 1));
        living(b).setHiringMode(HiringMode.MANUAL);
        JsonObject saved = new JsonObject();
        living(b).write(saved);

        LivingModule read = new LivingModule();
        read.read(saved);

        assertEquals(HiringMode.MANUAL, read.hiringMode());
        assertEquals(List.of(1), read.takeSavedResidents());
        assertTrue(read.residents().isEmpty());
    }

    @Test
    void asleepClearsLeisure() {
        CitizenData d = new CitizenData(1);
        d.setLeisureTime(100);

        d.setAsleep(true);

        assertEquals(0, d.leisureTime());
    }
}
```

- [ ] **Step 2 : lancer, constater l'échec**

Run : `./gradlew :core:test --tests "*LivingModuleTest"`
Attendu : échec de compilation (`citizen.home.LivingModule`, `setAutoHousing`, `setBedPos`, `setAsleep` absents).

- [ ] **Step 3 : déplacer le module, ajouter les champs**

`git mv core/src/main/java/dev/hycolony/core/construction/hut/LivingModule.java core/src/main/java/dev/hycolony/core/citizen/home/LivingModule.java`, puis corrige l'import dans `ConstructionBuildingTypes`.

`ColonySettings` :

```java
    private boolean autoHousing = true;

    /** MC BuildingTownHall.AUTO_HOUSING_MODE (default true): DEFAULT residences take the homeless. */
    public boolean autoHousing() {
        return autoHousing;
    }

    public void setAutoHousing(boolean autoHousing) {
        this.autoHousing = autoHousing;
    }
```

`CitizenData` (champs `private @Nullable BlockPos bedPos;` et `private boolean asleep;`) :

```java
    /** MC CitizenData.bedPos: the bed it lies in; null while it has none (MC BlockPos.ZERO). */
    public @Nullable BlockPos bedPos() {
        return bedPos;
    }

    public void setBedPos(@Nullable BlockPos bedPos) {
        this.bedPos = bedPos;
    }

    public boolean asleep() {
        return asleep;
    }

    /** MC CitizenData.setAsleep: falling asleep ends its leisure time. */
    public void setAsleep(boolean asleep) {
        this.asleep = asleep;
        if (asleep) {
            leisureTime = 0;
        }
    }
```

`citizen/home/LivingModule.java` :

```java
package dev.hycolony.core.citizen.home;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.hycolony.core.building.Building;
import dev.hycolony.core.building.module.BuildingEventsModule;
import dev.hycolony.core.building.module.PersistentModule;
import dev.hycolony.core.building.module.TickingModule;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.job.HiringMode;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.persist.SavedJson;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * The residents of a residence: one per hut level, assigned by hand or taken among the homeless. Port of MC
 * LivingBuildingModule and its AbstractAssignedCitizenModule.
 */
public final class LivingModule implements PersistentModule, TickingModule, BuildingEventsModule {
    private final List<Integer> residents = new ArrayList<>();
    private HiringMode hiringMode = HiringMode.DEFAULT;

    /** MC LivingBuildingModule.getModuleMax: the hut's level, so none at level 0. */
    public int max(Building b) {
        return b.level();
    }

    public boolean isFull(Building b) {
        return residents.size() >= max(b);
    }

    public List<Integer> residents() {
        return Collections.unmodifiableList(residents);
    }

    public HiringMode hiringMode() {
        return hiringMode;
    }

    public void setHiringMode(HiringMode hiringMode) {
        this.hiringMode = hiringMode;
    }

    /** MC onColonyTick: AUTO, or DEFAULT while the colony's {@link ColonySettings#autoHousing} is on. */
    public boolean autoHousing(Colony c) {
        return hiringMode == HiringMode.AUTO || (hiringMode == HiringMode.DEFAULT && c.settings().autoHousing());
    }

    /** MC WindowAssignCitizen: Assign and Unassign only work in MANUAL, or DEFAULT while autoHousing is off. */
    public boolean manual(Colony c) {
        return hiringMode == HiringMode.MANUAL || (hiringMode == HiringMode.DEFAULT && !c.settings().autoHousing());
    }

    /**
     * MC assignCitizen: makes {@code b} the citizen's home, after it left its old one (MC setHomeBuilding); false for
     * a resident already here or a full hut.
     */
    public boolean assign(Colony c, Building b, CitizenData citizen) {
        if (residents.contains(citizen.id()) || isFull(b)) {
            return false;
        }
        residents.add(citizen.id());
        moveHome(c, citizen, b.position());
        c.markDirty();
        return true;
    }

    /** MC removeCitizen: the resident leaves, homeless and bedless; false for a citizen who does not live here. */
    public boolean remove(Colony c, Building b, int citizenId) {
        if (!residents.remove(Integer.valueOf(citizenId))) {
            return false;
        }
        c.citizens().get(citizenId).ifPresent(d -> moveHome(c, d, null));
        c.markDirty();
        return true;
    }

    /**
     * MC CitizenData.setHomeBuilding: a citizen moving house leaves its old one first; any change of an existing home
     * forgets its bed.
     */
    private static void moveHome(Colony c, CitizenData citizen, @Nullable BlockPos home) {
        BlockPos old = citizen.homeBuilding();
        if (old != null && home != null && !old.equals(home)) {
            c.buildings().at(old).ifPresent(o -> o.module(LivingModule.class)
                    .ifPresent(m -> m.remove(c, o, citizen.id())));
        }
        if (citizen.homeBuilding() != null) {
            citizen.setBedPos(null);
        }
        citizen.setHomeBuilding(home);
    }

    /** MC AbstractAssignedCitizenModule.onDestroyed: every resident leaves. */
    @Override
    public void onRemoved(Colony colony, Building building) {
        for (int id : List.copyOf(residents)) {
            remove(colony, building, id);
        }
    }

    /** MC LivingBuildingModule.onColonyTick: takes the homeless, in citizen order, as many as fit. */
    @Override
    public void onColonyTick(Colony colony, Building building) {
        if (isFull(building) || !autoHousing(colony)) {
            return;
        }
        for (CitizenData citizen : colony.citizens().all()) {
            if (isFull(building)) {
                return;
            }
            if (citizen.homeBuilding() == null) {
                assign(colony, building, citizen);
            }
        }
    }

    /**
     * The residents read from a save, removed from this module: the load assigns each again in order (MC
     * LivingBuildingModule.deserializeNBT), see {@code HousingHeal}.
     */
    public List<Integer> takeSavedResidents() {
        List<Integer> saved = List.copyOf(residents);
        residents.clear();
        return saved;
    }

    @Override
    public void write(JsonObject out) {
        JsonArray arr = new JsonArray();
        residents.forEach(arr::add);
        out.add("residents", arr);
        out.addProperty("hiringMode", hiringMode.name());
    }

    /** Tolerant (CLAUDE.md § 5): a non-number resident is skipped, an unknown or missing mode reads as DEFAULT. */
    @Override
    public void read(JsonObject in) {
        residents.clear();
        residents.addAll(SavedJson.ints(in.get("residents")));
        hiringMode = SavedJson.enumOf(HiringMode.class, in.get("hiringMode")).orElse(HiringMode.DEFAULT);
    }
}
```

(Ajoute l'import `dev.hycolony.core.colony.ColonySettings` pour le lien Javadoc, ou écris `{@code autoHousing}`.)

- [ ] **Step 4 : lancer les tests**

Run : `./gradlew :core:test --tests "*LivingModuleTest" --tests "*ArchitectureTest" --tests "*FeatureDependenciesTest"`
Attendu : PASS. Puis corrige les appelants de l'ancien `LivingModule.capacity` (`grep -rn "capacity(" core/src`) en `max(b)`.

- [ ] **Step 5 : spec, build et commit**

Corrige les chemins dans la spec (S § 1 et § 2.2 : `citizen/home/…`, raison : la matrice de `FeatureDependenciesTest`). Ensuite, `./gradlew build`.

```bash
git add core/src/main/java/dev/hycolony/core/citizen/home/LivingModule.java core/src/main/java/dev/hycolony/core/construction/hut/LivingModule.java core/src/main/java/dev/hycolony/core/construction/hut/ConstructionBuildingTypes.java core/src/main/java/dev/hycolony/core/citizen/CitizenData.java core/src/main/java/dev/hycolony/core/colony/ColonySettings.java core/src/test/java/dev/hycolony/core/citizen/home/LivingModuleTest.java docs/superpowers/specs/2026-10-01-hycolony-sp4-home-sleep-design.md
git commit -m "feat(core): residences take residents, by hand or among the homeless, as MC's living module"
```

---

### Task 2 : capacité de logement de la colonie

**Fichiers :**
- Créer : `core/src/main/java/dev/hycolony/core/citizen/home/HousingCapacity.java`
- Modifier : `core/src/main/java/dev/hycolony/core/app/ui/TownHallView.java` (`Stats`)
- Modifier : `core/src/main/java/dev/hycolony/core/app/view/TownHallStats.java` (retirer l'écart « no housing capacity »)
- Modifier : `core/src/main/java/dev/hycolony/core/kernel/config/ColonyConfig.java` (Javadoc de `maxCitizenPerColony` : « read by HousingCapacity »)
- Test : `core/src/test/java/dev/hycolony/core/citizen/home/HousingCapacityTest.java`

**Interfaces :**
- Produit :
  - `record HousingCapacity(int citizens, int housing, int cap)`, avec :
    - `static HousingCapacity of(Colony)` ;
    - `int shownMax()` ;
    - `Population population()` ;
    - `enum Population { OK, NEEDS_HOUSING, CONFIG_LIMITED }` ;
  - `TownHallView.Stats(int citizens, int maxCitizens, HousingCapacity.Population population, List<JobCount> jobs, int children, int unemployed)`.

Règles (MC `CitizenManager.calculateMaxCitizens`, `getMaxCitizens`, `WindowStatsPage:84-123`) :

- `housing` est la somme, sur les résidences de niveau > 0, de `max(b)`, ou du nombre d'habitants pour une résidence `LOCKED`. Le résultat est borné par `max(1, min(somme, cap))`.
- `cap = config.gameplay().maxCitizenPerColony()`. *Deviation from MC* : MC prend aussi `maxCitizensFromResearch`, qui ne vaut que 25 tant que la recherche `CITIZEN_CAP` n'est pas faite. HyColony n'a pas de recherche, donc seule la configuration compte.
- `population()` :
  - `OK` si `citizens < cap * 0.9` et `citizens < housing * 0.9` ;
  - sinon `NEEDS_HOUSING` si `citizens < cap` ;
  - sinon `CONFIG_LIMITED`.
- `shownMax()` : `cap` en `CONFIG_LIMITED`, sinon `max(citizens, housing)`.

- [ ] **Step 1 : le test qui échoue**

```java
package dev.hycolony.core.citizen.home;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.permission.Permissions;
import dev.hycolony.core.colony.territory.TerritoryIndex;
import dev.hycolony.core.construction.hut.ConstructionBuildingTypes;
import dev.hycolony.core.job.HiringMode;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.config.ColonyConfig;
import dev.hycolony.core.testing.TestContexts;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class HousingCapacityTest {
    private final TestContexts t = new TestContexts();

    private Colony colony(int citizens) {
        Colony c = new Colony(
                t.context(),
                new TerritoryIndex(),
                new Colony.Founding(1, "T", new BlockPos(0, 64, 0), Permissions.createDefault(UUID.randomUUID(), "A")));
        for (int id = 1; id <= citizens; id++) {
            c.citizens().restore(new CitizenData(id));
        }
        return c;
    }

    private Building residence(Colony c, int x, int level) {
        Building b = Building.create(ConstructionBuildingTypes.RESIDENCE, new BlockPos(x, 64, 0), 0);
        b.setLevel(level);
        c.buildings().add(b);
        return b;
    }

    @Test
    void sumsBuiltResidencesAndLockedOnesCountTheirResidents() {
        Colony c = colony(2);
        residence(c, 8, 3);
        residence(c, 20, 0);
        Building locked = residence(c, 40, 4);
        locked.module(LivingModule.class).orElseThrow().assign(c, locked, c.citizens().get(1).orElseThrow());
        locked.module(LivingModule.class).orElseThrow().setHiringMode(HiringMode.LOCKED);

        assertEquals(4, HousingCapacity.of(c).housing());
    }

    @Test
    void noHousingStillAllowsOneAndConfigCaps() {
        assertEquals(1, HousingCapacity.of(colony(0)).housing());
        t.config = ColonyConfig.defaults(); // maxCitizenPerColony: 25 after clamping (25..500)
        Colony c = colony(0);
        for (int i = 0; i < 6; i++) {
            residence(c, 8 + 12 * i, 5);
        }
        assertEquals(25, HousingCapacity.of(c).housing());
    }

    @Test
    void populationFollowsMcStatisticsColours() {
        Colony ok = colony(1);
        residence(ok, 8, 5);
        assertEquals(HousingCapacity.Population.OK, HousingCapacity.of(ok).population());

        Colony crowded = colony(5);
        residence(crowded, 8, 5);
        HousingCapacity full = HousingCapacity.of(crowded);
        assertEquals(HousingCapacity.Population.NEEDS_HOUSING, full.population());
        assertEquals(5, full.shownMax());

        Colony capped = colony(25);
        assertEquals(HousingCapacity.Population.CONFIG_LIMITED, HousingCapacity.of(capped).population());
        assertEquals(25, HousingCapacity.of(capped).shownMax());
    }
}
```

(Vérifie la valeur par défaut de `maxCitizenPerColony` dans `ColonyConfig.defaults()`. Si ce n'est pas 25, construis une config de test avec 25.)

- [ ] **Step 2 : lancer, constater l'échec**

Run : `./gradlew :core:test --tests "*HousingCapacityTest"` ; attendu : échec de compilation.

- [ ] **Step 3 : implémenter**

```java
package dev.hycolony.core.citizen.home;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.job.HiringMode;

/**
 * The colony's housing: its citizens, the places its residences offer, and the configured cap. Port of MC
 * CitizenManager.calculateMaxCitizens and getMaxCitizens, read by the town hall's Statistics (MC WindowStatsPage).
 *
 * <p>Deviation from MC: MC also caps by maxCitizensFromResearch (25 until the CITIZEN_CAP research); without research,
 * only the configuration caps.
 */
public record HousingCapacity(int citizens, int housing, int cap) {
    /** MC WindowStatsPage's colour: green, orange (needs housing) or red (reached the configured limit). */
    public enum Population {
        OK,
        NEEDS_HOUSING,
        CONFIG_LIMITED
    }

    public static HousingCapacity of(Colony c) {
        int sum = 0;
        for (Building b : c.buildings().all()) {
            if (b.level() <= 0) {
                continue;
            }
            LivingModule living = b.module(LivingModule.class).orElse(null);
            if (living != null) {
                sum += living.hiringMode() == HiringMode.LOCKED ? living.residents().size() : living.max(b);
            }
        }
        int cap = c.context().config().gameplay().maxCitizenPerColony();
        return new HousingCapacity(c.citizens().all().size(), Math.max(1, Math.min(sum, cap)), cap);
    }

    public Population population() {
        if (citizens < cap * 0.9 && citizens < housing * 0.9) {
            return Population.OK;
        }
        return citizens < cap ? Population.NEEDS_HOUSING : Population.CONFIG_LIMITED;
    }

    /** MC: "citizens/max(citizens, housing)", or "citizens/cap" once the configured limit is reached. */
    public int shownMax() {
        return population() == Population.CONFIG_LIMITED ? cap : Math.max(citizens, housing);
    }
}
```

`TownHallStats.of` remplit `Stats` avec `HousingCapacity cap = HousingCapacity.of(c)`, en mettant `cap.shownMax()` et `cap.population()`. Retire la phrase « no housing capacity nor its colour and warning (no housing system yet) » de sa Javadoc, et mets à jour les appelants de `Stats` (`grep -rn "new Stats\|Stats(" core/src plugin/src`). Le plugin lit encore `stats.citizens()` ; la Task 9 affiche le reste.

- [ ] **Step 4 : lancer les tests** (`./gradlew :core:test`) : PASS.

- [ ] **Step 5 : build et commit**

```bash
git add core/src/main/java/dev/hycolony/core/citizen/home/HousingCapacity.java core/src/main/java/dev/hycolony/core/app/ui/TownHallView.java core/src/main/java/dev/hycolony/core/app/view/TownHallStats.java core/src/main/java/dev/hycolony/core/kernel/config/ColonyConfig.java core/src/test/java/dev/hycolony/core/citizen/home/HousingCapacityTest.java <autres appelants modifiés>
git commit -m "feat(core): the colony's housing capacity, as MC's max citizens and statistics colours"
```

---

### Task 3 : l'emprise d'une hutte

**Fichiers :**
- Créer : `core/src/main/java/dev/hycolony/core/colony/HutFootprint.java`
- Modifier : `core/src/main/java/dev/hycolony/core/colony/BlockApproach.java` (`centre` réutilise l'emprise)
- Test : `core/src/test/java/dev/hycolony/core/colony/HutFootprintTest.java`

**Interfaces :**
- Produit :
  - `record HutFootprint.Box(BlockPos min, BlockPos max)`, avec `boolean contains(BlockPos)` (bornes incluses) ;
  - `static HutFootprint.Box of(GamePorts ports, Building b)` : les coins du plan au niveau `max(1, level)`, à la rotation de la hutte, autour de sa position. Sans style ou sans plan, la boîte se réduit à la position de la hutte (MC : coins par défaut) ;
  - `static boolean isInBuilding(GamePorts ports, Building b, BlockPos pos)`, MC `AbstractSchematicProvider.isInBuilding:505-520` : la boîte élargie d'**1 bloc** sur chaque axe.

Le `BlueprintSource` du plugin met déjà ses plans en cache par (style, type, niveau, rotation) : pas de cache ici.

- [ ] **Step 1 : le test qui échoue**

```java
package dev.hycolony.core.colony;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.construction.blueprint.Blueprint;
import dev.hycolony.core.construction.blueprint.BlueprintSource;
import dev.hycolony.core.construction.hut.ConstructionBuildingTypes;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.testing.TestContexts;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class HutFootprintTest {
    private final TestContexts t = new TestContexts();
    private int askedLevel;

    private GamePorts ports() {
        t.blueprints = new BlueprintSource() {
            @Override
            public Optional<Blueprint> load(String style, String type, int level, int rotation) {
                askedLevel = level;
                return Optional.of(
                        new Blueprint("k", List.of(), new BlockPos(-2, -1, -3), new BlockPos(4, 6, 2)));
            }

            @Override
            public List<String> styles() {
                return List.of("s");
            }
        };
        return t.context().ports();
    }

    private static Building hut(int level, String style) {
        Building b = Building.create(ConstructionBuildingTypes.RESIDENCE, new BlockPos(100, 64, 100), 0);
        b.setLevel(level);
        b.setStyle(style);
        return b;
    }

    @Test
    void cornersOfThePlanWidenedByOneBlock() {
        GamePorts p = ports();
        Building b = hut(2, "s");

        assertTrue(HutFootprint.isInBuilding(p, b, new BlockPos(97, 62, 96))); // min corner - 1
        assertTrue(HutFootprint.isInBuilding(p, b, new BlockPos(105, 71, 103))); // max corner + 1
        assertFalse(HutFootprint.isInBuilding(p, b, new BlockPos(106, 64, 100)));
        assertEquals(2, askedLevel);
    }

    @Test
    void levelZeroUsesThePlanOfLevelOne() {
        GamePorts p = ports();

        HutFootprint.of(p, hut(0, "s"));

        assertEquals(1, askedLevel);
    }

    @Test
    void withoutPlanOnlyTheHutAndItsNeighbours() {
        GamePorts p = t.context().ports(); // TestContexts' source loads nothing
        Building b = hut(1, "");

        assertTrue(HutFootprint.isInBuilding(p, b, new BlockPos(101, 65, 99)));
        assertFalse(HutFootprint.isInBuilding(p, b, new BlockPos(102, 64, 100)));
    }
}
```

- [ ] **Step 2 : lancer, constater l'échec** (`./gradlew :core:test --tests "*HutFootprintTest"`).

- [ ] **Step 3 : implémenter**

```java
package dev.hycolony.core.colony;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.kernel.BlockPos;

/**
 * A hut's footprint: the corners of its plan at its level (at least 1), around the hut block. Port of MC
 * AbstractSchematicProvider.getCorners and isInBuilding; without a plan, MC's default corners are the hut block.
 */
public final class HutFootprint {
    private HutFootprint() {}

    /** The plan's corners, inclusive, in world positions. */
    public record Box(BlockPos min, BlockPos max) {
        public boolean contains(BlockPos p) {
            return p.x() >= min.x() && p.x() <= max.x()
                    && p.y() >= min.y() && p.y() <= max.y()
                    && p.z() >= min.z() && p.z() <= max.z();
        }
    }

    /** {@code b}'s corners from the plan of its level (at least 1) and rotation; the hut block alone without one. */
    public static Box of(GamePorts ports, Building b) {
        BlockPos at = b.position();
        if (b.style().isEmpty()) {
            return new Box(at, at);
        }
        return ports.blueprints()
                .load(b.style(), b.type().id(), Math.max(1, b.level()), b.rotation())
                .map(bp -> new Box(
                        at.offset(bp.min().x(), bp.min().y(), bp.min().z()),
                        at.offset(bp.max().x(), bp.max().y(), bp.max().z())))
                .orElse(new Box(at, at));
    }

    /** MC isInBuilding: {@code pos} within the corners widened by one block on every axis. */
    public static boolean isInBuilding(GamePorts ports, Building b, BlockPos pos) {
        Box box = of(ports, b);
        return new Box(box.min().offset(-1, -1, -1), box.max().offset(1, 1, 1)).contains(pos);
    }
}
```

`BlockApproach.centre(building)` devient :

```java
    private BlockPos centre(Building building) {
        HutFootprint.Box box = HutFootprint.of(ports, building);
        BlockPos at = building.position();
        return new BlockPos((box.min().x() + box.max().x()) / 2, at.y(), (box.min().z() + box.max().z()) / 2);
    }
```

Le résultat est le même qu'avant : `(2·at + min + max) / 2`, et la position de la hutte sans plan.

- [ ] **Step 4 : lancer les tests** (`./gradlew :core:test`) : PASS, `BlockApproachTest` compris.

- [ ] **Step 5 : build et commit**

```bash
git add core/src/main/java/dev/hycolony/core/colony/HutFootprint.java core/src/main/java/dev/hycolony/core/colony/BlockApproach.java core/src/test/java/dev/hycolony/core/colony/HutFootprintTest.java
git commit -m "feat(core): a hut's footprint from its plan, as MC isInBuilding"
```

---

### Task 4 : `BedModule` et l'enregistrement des lits

**Fichiers :**
- Créer : `core/src/main/java/dev/hycolony/core/citizen/home/BedModule.java`
- Modifier :
  - `core/src/main/java/dev/hycolony/core/kernel/port/ItemCatalog.java` (`isBed`) ;
  - `core/src/test/java/dev/hycolony/core/testing/FakeCatalog.java` (`beds`) ;
  - `core/src/main/java/dev/hycolony/core/building/module/BuildingEventsModule.java` (crochet `onBlockPlacedInBuilding` et aide statique `blockPlaced`) ;
  - `core/src/main/java/dev/hycolony/core/construction/builder/PlannedBlocks.java` ;
  - `core/src/main/java/dev/hycolony/core/app/wand/PasteQueue.java` ;
  - `core/src/main/java/dev/hycolony/core/construction/hut/ConstructionBuildingTypes.java` (module `bed`) ;
  - `plugin/src/main/java/dev/hycolony/plugin/adapter/HytaleItemCatalog.java` (`isBed`, pour que le plugin compile).
- Test :
  - `core/src/test/java/dev/hycolony/core/citizen/home/BedModuleTest.java` ;
  - un cas dans le test existant de `PlannedBlocks` ou du constructeur, et un dans celui de `PasteQueue` (`grep -rln "PasteQueue\|foundAsPlanned" core/src/test`).

**Interfaces :**
- Produit :
  - `ItemCatalog.boolean isBed(BlockKey)` ;
  - `BuildingEventsModule` :
    - `default void onBlockPlacedInBuilding(Colony, Building, BlockPos, BlockKey) {}` ;
    - `static void blockPlaced(Colony, Building, BlockPos, BlockKey)`, qui l'appelle sur chaque module ;
  - `BedModule` (`PersistentModule`, `BuildingEventsModule`) :
    - `List<BlockPos> beds()` ;
    - `void addBed(BlockPos)` ;
    - `void removeBed(BlockPos)` ;
    - `Optional<BlockPos> bed(int rank)`.

Règles (MC `BedHandlingModule`) : la liste est ordonnée par ordre de pose et sans doublon. Seul le **bloc de base** est enregistré : un `BlueprintEntry` ne porte jamais de cellule de remplissage (Javadoc de `Blueprint`).

- [ ] **Step 1 : le test qui échoue**

```java
package dev.hycolony.core.citizen.home;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import dev.hycolony.core.building.Building;
import dev.hycolony.core.building.module.BuildingEventsModule;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.permission.Permissions;
import dev.hycolony.core.colony.territory.TerritoryIndex;
import dev.hycolony.core.construction.hut.ConstructionBuildingTypes;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.BlockKey;
import dev.hycolony.core.testing.TestContexts;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BedModuleTest {
    private static final BlockKey BED = new BlockKey("Furniture_Village_Bed");
    private static final BlockKey CHAIR = new BlockKey("Furniture_Village_Chair");
    private final TestContexts t = new TestContexts();

    @Test
    void placedBedsJoinInOrderWithoutDuplicatesAndOtherBlocksDoNot() {
        t.catalog.beds.add(BED);
        Colony c = new Colony(
                t.context(),
                new TerritoryIndex(),
                new Colony.Founding(1, "T", new BlockPos(0, 64, 0), Permissions.createDefault(UUID.randomUUID(), "A")));
        Building b = Building.create(ConstructionBuildingTypes.RESIDENCE, new BlockPos(8, 64, 0), 0);

        BuildingEventsModule.blockPlaced(c, b, new BlockPos(10, 64, 0), BED);
        BuildingEventsModule.blockPlaced(c, b, new BlockPos(9, 64, 0), BED);
        BuildingEventsModule.blockPlaced(c, b, new BlockPos(10, 64, 0), BED);
        BuildingEventsModule.blockPlaced(c, b, new BlockPos(11, 64, 0), CHAIR);

        BedModule beds = b.module(BedModule.class).orElseThrow();
        assertEquals(List.of(new BlockPos(10, 64, 0), new BlockPos(9, 64, 0)), beds.beds());
        assertEquals(Optional.of(new BlockPos(9, 64, 0)), beds.bed(1));
        assertTrue(beds.bed(2).isEmpty());
    }

    @Test
    void removedBedAndRoundTrip() {
        BedModule beds = new BedModule();
        beds.addBed(new BlockPos(1, 2, 3));
        beds.addBed(new BlockPos(4, 5, 6));
        beds.removeBed(new BlockPos(1, 2, 3));
        JsonObject saved = new JsonObject();
        beds.write(saved);

        BedModule read = new BedModule();
        read.read(saved);

        assertEquals(List.of(new BlockPos(4, 5, 6)), read.beds());
    }
}
```

Ajoute aussi `builderRegistersThePlannedBedItPlaces` (le constructeur pose un lit du plan et la résidence l'enregistre), `bedFoundAsPlannedJoinsTheResidence` (`foundAsPlanned`) et `pastedResidenceRegistersItsBeds` (`PasteQueue`). Mets-les dans les tests existants de ces chemins, en copiant la mise en place de leurs cas « container ».

- [ ] **Step 2 : lancer, constater l'échec.**

- [ ] **Step 3 : implémenter**

`ItemCatalog` :

```java
    /** Whether this block is a bed citizens lie in: its block type has sleeping points (Hytale BlockType.getBeds). */
    boolean isBed(BlockKey block);
```

`FakeCatalog` : `public final Set<BlockKey> beds = new HashSet<>();` et `isBed` qui renvoie `beds.contains(block)`.

`HytaleItemCatalog.isBed` (API § 41 : `BlockType.getBeds()`, `BlockType.java:1661`). Reprends la façon dont `isHarmful` y trouve le `BlockType` d'une clé, puis :

```java
    /** BlockType.getBeds() is non-null for every bed (vanilla and HyVanilla); an unknown block is no bed. */
    @Override
    public boolean isBed(BlockKey block) {
        BlockType type = blockType(block); // the helper isHarmful already uses
        return type != null && type.getBeds() != null;
    }
```

`BuildingEventsModule`, ajouts :

```java
    /**
     * A block of its plan was placed at {@code pos} in the building, or found there as planned (MC
     * IBuildingEventsModule.onBlockPlacedInBuilding, from AbstractBuilding.registerBlockPosition).
     */
    default void onBlockPlacedInBuilding(Colony colony, Building building, BlockPos pos, BlockKey block) {}

    /** MC registerBlockPosition: tells every events module of {@code building} about the block placed at {@code pos}. */
    static void blockPlaced(Colony colony, Building building, BlockPos pos, BlockKey block) {
        for (BuildingModule module : building.modules().values()) {
            if (module instanceof BuildingEventsModule events) {
                events.onBlockPlacedInBuilding(colony, building, pos, block);
            }
        }
    }
```

`citizen/home/BedModule.java` :

```java
package dev.hycolony.core.citizen.home;

/**
 * The beds of a residence, in the order they were placed (MC BedHandlingModule). Only a bed's base block is kept,
 * Hytale's equivalent of MC's bed head.
 *
 * <p>Deviation from MC: MC keeps a HashSet, so its bed order (and each resident's bed) may change between sessions;
 * here it is the placing order. MC's onWakeUp clears each bed's OCCUPIED flag; Hytale keeps a bed's occupancy itself.
 */
public final class BedModule implements PersistentModule, BuildingEventsModule {
    private final List<BlockPos> beds = new ArrayList<>();

    public List<BlockPos> beds() {
        return Collections.unmodifiableList(beds);
    }

    /** The bed of the resident of rank {@code rank} (MC EntityAISleep: by index); empty past the list. */
    public Optional<BlockPos> bed(int rank) {
        return rank >= 0 && rank < beds.size() ? Optional.of(beds.get(rank)) : Optional.empty();
    }

    public void addBed(BlockPos pos) {
        if (!beds.contains(pos)) {
            beds.add(pos);
        }
    }

    /** MC removeBed: a position that is no bed any more. */
    public void removeBed(BlockPos pos) {
        beds.remove(pos);
    }

    /** MC onBlockPlacedInBuilding: a bed placed in the building joins its list. */
    @Override
    public void onBlockPlacedInBuilding(Colony colony, Building building, BlockPos pos, BlockKey block) {
        if (colony.context().ports().catalog().isBed(block)) {
            addBed(pos);
        }
    }

    @Override
    public void write(JsonObject out) {
        JsonArray arr = new JsonArray();
        beds.forEach(p -> arr.add(SavedJson.pos(p)));
        out.add("beds", arr);
    }

    /** Tolerant (CLAUDE.md § 5): a malformed position is skipped. */
    @Override
    public void read(JsonObject in) {
        beds.clear();
        for (JsonElement e : SavedJson.arrayOr(in.get("beds"))) {
            SavedJson.tryPos(e).ifPresent(this::addBed);
        }
    }
}
```

`ConstructionBuildingTypes.RESIDENCE` : modules `living` puis `bed` :

```java
List.of(new ModuleProducer("living", LivingModule::new), new ModuleProducer("bed", BedModule::new))
```

`PlannedBlocks.placed` : à la fin, `BuildingEventsModule.blockPlaced(ctx.colony(), ctx.site().target(), pos, e.state().key());`.

`PlannedBlocks.foundAsPlanned` : la condition de la boucle devient `e.hasContainer() || e.workstation().isPresent() || ctx.catalog().isBed(e.state().key())`. `registerIfAsPlanned` appelle le même `blockPlaced` une fois la correspondance vérifiée.

`PasteQueue` :
- `placed` : enregistre aussi quand `catalog.isBed(e.state().key())`, et `register` appelle `BuildingEventsModule.blockPlaced(colony.get(), b, pos, e.state().key())` ;
- `registerFound` : même condition élargie et même appel ;
- garde `markDirty`.

- [ ] **Step 4 : lancer les tests** (`./gradlew :core:test` puis `./gradlew :plugin:compileJava`) : PASS.

- [ ] **Step 5 : build et commit**

```bash
git add core/src/main/java/dev/hycolony/core/citizen/home/BedModule.java core/src/main/java/dev/hycolony/core/kernel/port/ItemCatalog.java core/src/test/java/dev/hycolony/core/testing/FakeCatalog.java core/src/main/java/dev/hycolony/core/building/module/BuildingEventsModule.java core/src/main/java/dev/hycolony/core/construction/builder/PlannedBlocks.java core/src/main/java/dev/hycolony/core/app/wand/PasteQueue.java core/src/main/java/dev/hycolony/core/construction/hut/ConstructionBuildingTypes.java plugin/src/main/java/dev/hycolony/plugin/adapter/HytaleItemCatalog.java core/src/test/java/dev/hycolony/core/citizen/home/BedModuleTest.java <tests modifiés>
git commit -m "feat(core): residences register the beds the builder or the wand places, as MC's bed module"
```

---

### Task 5 : schéma 6 et réparation au chargement

**Fichiers :**
- Créer : `core/src/main/java/dev/hycolony/core/app/persistence/HousingHeal.java`
- Créer : `core/src/test/resources/fixtures/colony-v5-homes.json`
- Modifier :
  - `core/src/main/java/dev/hycolony/core/kernel/persist/MigrationChain.java` (`sp4()`, `v5ToV6`) ;
  - `core/src/main/java/dev/hycolony/core/app/ColonyPersistence.java` (`MigrationChain.sp4()`) ;
  - `core/src/main/java/dev/hycolony/core/app/persistence/ColonySerializer.java` (`SCHEMA_VERSION = 6`, `autoHousing`, appel de `HousingHeal.heal`) ;
  - `core/src/main/java/dev/hycolony/core/app/persistence/CitizenSerializer.java` (`asleep`, `bedPos`).
- Test : `core/src/test/java/dev/hycolony/core/app/persistence/MigrationV5ToV6Test.java` et `HousingHealTest.java`

Utilise le skill `add-migration`.

**Interfaces :**
- Consomme : `LivingModule.takeSavedResidents`, `assign`, `BedModule.addBed`, `ItemCatalog.isBed`.
- Produit : `static boolean HousingHeal.heal(Colony c)`, vrai si quelque chose a changé.

Règles :

- **Migration 5 → 6** : chaque bâtiment `hycolony:residence` reçoit `modules.living = {residents: [ids des citoyens dont "home" == pos, dans l'ordre des citoyens], hiringMode: "DEFAULT"}` et `modules.bed = {beds: []}`, sauf si ces clés existent déjà. Les `settings` reçoivent `autoHousing: true` ; chaque citoyen reçoit `asleep: false` et `bedPos: null`.
- **`HousingHeal.heal`**, appelé dans `ColonySerializer.heal` après la boucle des travailleurs (MC `LivingBuildingModule.deserializeNBT`) :
  1. Note la maison sauvée de chaque citoyen, puis la met à `null` sans toucher `bedPos` : MC ne sauve pas la maison.
  2. Pour chaque bâtiment, dans l'ordre de `buildings().all()`, qui a un `LivingModule` : `takeSavedResidents()`, puis `assign(c, b, citizen)` pour chaque id connu. Un refus (hutte pleine) laisse le citoyen sans-abri, et un doublon se règle par `assign` lui-même (le dernier gagne).
  3. Un citoyen resté sans maison perd son `bedPos`.
  4. Pour chaque résidence de niveau > 0 dont le `BedModule` est vide et qui a un style : charge le plan (style, type, niveau, rotation) et `addBed(hut + offset)` pour chaque entrée dont `isBed(entry.state().key())`.
  5. Renvoie vrai si une maison ou un lit a changé.

- [ ] **Step 1 : la fixture**

Copie `colony-v4-fields.json` en `colony-v5-homes.json` et règle `"schemaVersion": 5`. Ajoute `"fields": []`, puis donne au citoyen 1 `"home": {"x": 8, "y": 64, "z": 0}`, la position de la résidence de niveau 1. Ajoute un citoyen 2 (copie du 1 avec `"id": 2`) qui a la même maison : la résidence est pleine à 1.

- [ ] **Step 2 : les tests qui échouent**

```java
package dev.hycolony.core.app.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.citizen.home.LivingModule;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.persist.FileColonyStorage;
import dev.hycolony.core.kernel.persist.MigrationChain;
import dev.hycolony.core.testing.TestContexts;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Schema 6 (SP4): residences keep their residents and beds, citizens their sleep. */
class MigrationV5ToV6Test {
    private static final String FIXTURE = "colony-v5-homes.json";
    private static final BlockPos RESIDENCE = new BlockPos(8, 64, 0);

    @TempDir
    Path dir;

    private static String fixture() throws IOException {
        try (InputStream in = MigrationV5ToV6Test.class.getResourceAsStream("/fixtures/" + FIXTURE)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void v5ToV6GivesResidencesTheirSavedResidents() throws IOException {
        JsonObject migrated = MigrationChain.sp4().migrate(JsonParser.parseString(fixture()).getAsJsonObject());

        assertEquals(6, migrated.get(MigrationChain.VERSION_KEY).getAsInt());
        JsonObject living = migrated.getAsJsonArray("buildings").get(1).getAsJsonObject()
                .getAsJsonObject("modules").getAsJsonObject("living");
        assertEquals("[1,2]", living.getAsJsonArray("residents").toString());
        assertTrue(migrated.getAsJsonObject("settings").get("autoHousing").getAsBoolean());
    }

    @Test
    void v5ColonyLoadsWithResidentsUpToTheLevelAndTheRestHomeless() throws IOException {
        Files.writeString(dir.resolve("colony-1.json"), fixture());
        ColonyManager m = new TestContexts().manager();
        m.persistence().setStorage(new FileColonyStorage(dir), MigrationChain.sp4());

        m.persistence().loadAll();

        Colony c = m.byId(1).orElseThrow();
        LivingModule living = c.buildings().at(RESIDENCE).orElseThrow().module(LivingModule.class).orElseThrow();
        assertEquals(List.of(1), living.residents()); // level 1: one place, MC assignCitizen refuses the second
        assertEquals(RESIDENCE, c.citizens().get(1).orElseThrow().homeBuilding());
        assertNull(c.citizens().get(2).orElseThrow().homeBuilding());
        assertFalse(c.citizens().get(1).orElseThrow().asleep());
        m.persistence().saveAll();
        assertTrue(Files.readString(dir.resolve("colony-1.json")).contains("\"schemaVersion\":6"));
    }
}
```

`HousingHealTest` (colonie construite comme dans `LivingModuleTest`) :
- `citizenListedInTwoHomesEndsInOne` : deux résidences dont les `read()` listent toutes deux le citoyen 1 ; après `heal`, il n'est que dans la seconde, et sa maison est celle-là ;
- `unknownResidentIsDropped` : un id 99 lu disparaît ;
- `strayHomeIsCleared` : un citoyen dont `homeBuilding` désigne une résidence qui ne le liste pas devient sans-abri, et son `bedPos` est effacé ;
- `bedsOfAResidenceBuiltBeforeSp4AreFoundInItsPlan` : `t.blueprints` renvoie un plan avec une entrée lit en `(2, 0, 1)`, avec `t.catalog.beds` ; après `heal`, `beds()` vaut `[hut + (2, 0, 1)]`. Un second `heal` n'en ajoute pas ;
- `healReportsNoChangeOnAHealthySave` : `heal` sur un état cohérent renvoie faux.

- [ ] **Step 3 : lancer, constater l'échec.**

- [ ] **Step 4 : implémenter**

`MigrationChain` : renomme `sp3b()` en `sp4()`, avec un schéma courant de 6 et la migration `new Migration(5, MigrationChain::v5ToV6)`. Mets à jour la Javadoc (« schema 6 the residences' residents and beds, the citizens' sleep ») et les appelants (`grep -rn "sp3b()" core/src`).

```java
    /**
     * Schema 6 (SP4): each residence lists the citizens whose saved home it is, in citizen order (the load assigns
     * them again, as MC), and no bed yet (the load finds them in its plan); citizens are awake; the colony houses its
     * homeless automatically (MC AUTO_HOUSING_MODE).
     */
    private static JsonObject v5ToV6(JsonObject doc) {
        for (JsonElement el : doc.getAsJsonArray("buildings")) {
            if (el instanceof JsonObject b && "hycolony:residence".equals(SavedJson.stringOr(b.get("type"), ""))) {
                residenceModules(doc, b);
            }
        }
        for (JsonElement el : doc.getAsJsonArray("citizens")) {
            if (el instanceof JsonObject citizen) {
                citizen.addProperty("asleep", false);
                citizen.add("bedPos", JsonNull.INSTANCE);
            }
        }
        SavedJson.objectOr(doc.get("settings")).addProperty("autoHousing", true);
        return doc;
    }

    private static void residenceModules(JsonObject doc, JsonObject residence) {
        JsonObject modules = residence.get("modules") instanceof JsonObject m ? m : new JsonObject();
        residence.add("modules", modules);
        JsonElement pos = residence.get("pos");
        if (!modules.has("living")) {
            JsonArray residents = new JsonArray();
            for (JsonElement el : doc.getAsJsonArray("citizens")) {
                if (el instanceof JsonObject c && pos != null && pos.equals(c.get("home")) && c.has("id")) {
                    residents.add(c.get("id"));
                }
            }
            JsonObject living = new JsonObject();
            living.add("residents", residents);
            living.addProperty("hiringMode", "DEFAULT");
            modules.add("living", living);
        }
        if (!modules.has("bed")) {
            JsonObject bed = new JsonObject();
            bed.add("beds", new JsonArray());
            modules.add("bed", bed);
        }
    }
```

(Si `objectOr` renvoie un nouvel objet quand `settings` manque, écris d'abord `doc.add("settings", …)` pour que la clé soit gardée.)

`CitizenSerializer.write` : `o.addProperty("asleep", d.asleep()); o.add("bedPos", pos(d.bedPos()));`. `read` : `d.setBedPos(readPos(o.get("bedPos")));` puis `if (boolOr(o.get("asleep"), false)) { d.setAsleep(true); }`. `setAsleep(true)` remet le loisir à 0 : appelle-le **après** `setLeisureTime`.

`ColonySerializer` :
- `SCHEMA_VERSION = 6` ;
- `settings.addProperty("autoHousing", …)` à l'écriture, `setAutoHousing(boolOr(settings.get("autoHousing"), …))` à la lecture ;
- dans `heal` : `changed |= HousingHeal.heal(c);` juste après la boucle des `WorkerModule`.

`HousingHeal.java` :

```java
package dev.hycolony.core.app.persistence;

/**
 * Rebuilds the colony's housing after a load. MC saves no citizen's home: each residence assigns its saved residents
 * again, in order (LivingBuildingModule.deserializeNBT), so one past the hut's level becomes homeless. A residence
 * built before SP4 finds the beds of its plan once.
 */
final class HousingHeal {
    private HousingHeal() {}

    /** Reassigns the residents and fills empty bed lists from the plans; true when a home or a bed changed. */
    static boolean heal(Colony c) {
        Map<Integer, Optional<BlockPos>> saved = new HashMap<>();
        for (CitizenData d : c.citizens().all()) {
            saved.put(d.id(), Optional.ofNullable(d.homeBuilding()));
            d.setHomeBuilding(null);
        }
        for (Building b : c.buildings().all()) {
            b.module(LivingModule.class).ifPresent(living -> reassign(c, b, living));
        }
        boolean changed = false;
        for (CitizenData d : c.citizens().all()) {
            if (d.homeBuilding() == null && d.bedPos() != null) {
                d.setBedPos(null);
                changed = true;
            }
            changed |= !saved.get(d.id()).equals(Optional.ofNullable(d.homeBuilding()));
        }
        for (Building b : c.buildings().all()) {
            changed |= findPlannedBeds(c, b);
        }
        return changed;
    }

    private static void reassign(Colony c, Building b, LivingModule living) {
        for (int id : living.takeSavedResidents()) {
            c.citizens().get(id).ifPresent(d -> living.assign(c, b, d));
        }
    }

    /** A built residence without beds registers those of its plan (MC registerBlockPosition never ran for it). */
    private static boolean findPlannedBeds(Colony c, Building b) {
        BedModule beds = b.module(BedModule.class).orElse(null);
        if (beds == null || !beds.beds().isEmpty() || b.level() <= 0 || b.style().isEmpty()) {
            return false;
        }
        GamePorts ports = c.context().ports();
        ports.blueprints()
                .load(b.style(), b.type().id(), b.level(), b.rotation())
                .ifPresent(bp -> bp.entries().stream()
                        .filter(e -> ports.catalog().isBed(e.state().key()))
                        .forEach(e -> beds.addBed(b.position().offset(e.offset().x(), e.offset().y(), e.offset().z()))));
        return !beds.beds().isEmpty();
    }
}
```

`assign` appelle `markDirty`, mais `ColonySerializer.read` remet l'état propre (`clearDirty`) après `heal`, puis le marque à réécrire si `heal` a renvoyé vrai. Vérifie ce comportement.

- [ ] **Step 5 : lancer les tests** (`./gradlew :core:test`) : PASS. Les tests des migrations plus anciennes passent toujours par la chaîne complète.

- [ ] **Step 6 : build et commit**

```bash
git add core/src/main/java/dev/hycolony/core/app/persistence/HousingHeal.java core/src/main/java/dev/hycolony/core/kernel/persist/MigrationChain.java core/src/main/java/dev/hycolony/core/app/ColonyPersistence.java core/src/main/java/dev/hycolony/core/app/persistence/ColonySerializer.java core/src/main/java/dev/hycolony/core/app/persistence/CitizenSerializer.java core/src/test/resources/fixtures/colony-v5-homes.json core/src/test/java/dev/hycolony/core/app/persistence/MigrationV5ToV6Test.java core/src/test/java/dev/hycolony/core/app/persistence/HousingHealTest.java <appelants de sp3b()>
git commit -m "feat(core): schema 6, residences reassign their residents on load and find their planned beds"
```

---

### Task 6 : l'onglet « Habitants » et ses actions

**Fichiers :**
- Créer :
  - `core/src/main/java/dev/hycolony/core/citizen/home/ResidentsView.java` ;
  - `core/src/main/java/dev/hycolony/core/citizen/home/ResidentsViews.java` ;
  - `core/src/main/java/dev/hycolony/core/app/action/HousingActions.java`.
- Modifier :
  - `LivingModule` (`implements ProvidesTab`) ;
  - `core/src/main/java/dev/hycolony/core/app/ui/BuildingView.java` (`upgradeWarning`) ;
  - `core/src/main/java/dev/hycolony/core/app/view/BuildingViews.java`.
- Test : `core/src/test/java/dev/hycolony/core/citizen/home/ResidentsViewsTest.java` et `core/src/test/java/dev/hycolony/core/app/action/HousingActionsTest.java`

**Interfaces :**
- Produit :
  - `record ResidentsView(int assigned, int max, HiringMode mode, boolean manual, boolean canManage, List<Resident> residents, List<Candidate> candidates) implements ModuleTab`, avec :
    - `record Resident(int citizenId, String name, Optional<String> jobId, OptionalInt workDistance, boolean far)` ;
    - `record Candidate(int citizenId, String name, Optional<String> jobId, OptionalInt workDistance, boolean closer, Home home)` ;
    - `record Home(boolean homeless, OptionalInt currentDistance, boolean far)` ;
  - `static ResidentsView ResidentsViews.of(Colony, Building, LivingModule, UUID viewer)` ;
  - `HousingActions(ColonyManager)` :
    - `boolean assign(UUID player, BlockPos hut, int citizenId)` ;
    - `boolean unassign(UUID, BlockPos, int)` ;
    - `boolean cycleMode(UUID, BlockPos)` ;
    - `boolean recall(UUID, BlockPos)` ;
  - `BuildingView.upgradeWarning: Optional<String>`, une clé de langue.

Règles (MC `WindowAssignCitizen:184-380`, `AssignUnassignMessage:119-130`, `RecallCitizenHutMessage:47-74`, `WindowHutLiving:115-124`, `LivingBuildingView.getHoverWarningForLevel`) :

- **Distances** : distance euclidienne 3D tronquée en `int` (MC `BlockPosUtil.getDistance`), `(int) Math.sqrt(a.distSq(b))`.
- **Habitants** (`Resident`) : `workDistance` va de l'atelier à cette hutte, et `far` vaut `workDistance > FAR_DISTANCE_THRESHOLD = 300`.
- **Candidats** : tous les citoyens sauf les habitants de cette hutte et ceux dont la maison est leur atelier (`home != null && home.equals(work)`).
  - Tri stable dans l'ordre des citoyens, d'abord les sans-abri (clé 0 pour sans-abri, 1 pour logé), puis par valeur : sans atelier, 0 s'il est sans-abri et `Integer.MAX_VALUE` s'il est logé ; sinon `workDistance`.
  - `workDistance` va de l'atelier à cette hutte. `Home.currentDistance` va de l'atelier à la maison actuelle, seulement si le citoyen a les deux.
  - `closer` vaut `workDistance < currentDistance`, et `Home.far` vaut `currentDistance > 300`.
- **`manual`** vient de `LivingModule.manual(c)`, et `canManage` de `MANAGE_HUTS`.
- **Avertissement d'amélioration** (MC `getHoverWarningForLevel`) : `hycolony.ui.residence.warning.<niveau+1>` :
  - au niveau 1, seulement si la colonie n'a ni hutte de fermier (`hycolony:farmer`, vérifie l'id) ni pêcheur de niveau ≥ 1 ;
  - aux niveaux 2, 3 et 4, toujours (pas de cantine dans HyColony) ;
  - sinon vide.

  `BuildingViews.of` le remplit pour une hutte qui a un `LivingModule`, et le laisse vide ailleurs.
- **Actions** (`MANAGE_HUTS` par `ManagedHut.find`, puis `manager.windows().showBuilding(…)`) :
  - `assign` :
    - niveau 0 : message `hycolony.hut.notBuiltYet` (le texte MC `workerhuts.level0`), false ;
    - citoyen inconnu, hutte pleine ou déjà sa maison : false, mais la fenêtre est ré-affichée ;
    - sinon `living.assign`, qui retire le citoyen de son ancienne maison. **Le mode n'est pas vérifié** : MC ne le vérifie qu'à l'écran ;
  - `unassign` : `living.remove` ;
  - `cycleMode` : `setHiringMode(mode.next())`, `markDirty` ;
  - `recall` : pour chaque habitant, si son IA tourne sur un corps vivant, `ai.teleport(Vec3.center(hut.position()))`. Sinon, `citizens().respawnBody(id)`, et un échec envoie `hycolony.hut.recallFail` (une seule fois).

- [ ] **Step 1 : les tests qui échouent**

Dans `ResidentsViewsTest`, monte la colonie comme dans `LivingModuleTest`, avec deux huttes de travail : un `BuildingType` de test avec `WorkerModule`, comme `WorkerModuleTest`, et `hire` pour donner un atelier.
- `candidatesHomelessFirstThenByWorkDistance` : sans-abri sans atelier, sans-abri à 30 blocs, logé à 10, logé sans atelier. Attendu : ids dans l'ordre `[sans-abri sans atelier, sans-abri 30, logé 10, logé sans atelier]` ;
- `candidateLinesSayCloserAndFar` : un logé dont l'atelier est à 400 blocs de sa maison actuelle et à 5 de cette hutte : `closer` vrai, `home.far` vrai, `currentDistance` 400 ;
- `residentsAndWorkFromHomeAreNoCandidates` ;
- `viewerWithoutManageHutsCannotManage`.

Dans `HousingActionsTest`, sur `t.manager()` avec une colonie fondée comme dans `WorkerModule`/`HutActions` (vois `HutActionsTest` pour la mise en place) :
- `assignWorksWhateverTheModeAsMcServer` ;
- `assignAtLevelZeroSaysNotBuilt` (le message dans `t.notifier`) ;
- `assignWithoutManageHutsDoesNothing` ;
- `unassignAndCycleMode` (`DEFAULT → AUTO → MANUAL → LOCKED → DEFAULT`) ;
- `recallTeleportsLivingResidentsToTheHut` (`t.bodies.teleports` contient le centre de la hutte) ;
- `recallRespawnsABodilessResidentAndSaysWhenItFails` (`t.bodies.refuseSpawn = true` : message `hycolony.hut.recallFail`).

Dans `BuildingViewsTest` (ou le test existant de `BuildingViews`) :
- `residenceWarnsBeforeEachUpgradeWithoutFoodBuildings` : niveau 1 sans fermier, puis `warning.2` ; niveau 1 avec une ferme de niveau 1, puis vide ; niveaux 2 à 4, puis `warning.3` à `.5` ; niveau 5, puis vide.

- [ ] **Step 2 : lancer, constater l'échec.**

- [ ] **Step 3 : implémenter**

`ResidentsViews.of` construit les deux listes avec les règles ci-dessus (une méthode par liste, 40 lignes au plus chacune). `distance(BlockPos a, BlockPos b)` vaut `(int) Math.sqrt(a.distSq(b))`. `LivingModule.tab(c, b, viewer)` renvoie `ResidentsViews.of(c, b, this, viewer)`. Garde `FAR_DISTANCE_THRESHOLD` dans `ResidentsViews`, avec sa source MC.

`HousingActions` reprend la forme de `FieldActions` (construite par l'appelant, `ManagedHut.find`, ré-affichage). Exemple :

```java
    /**
     * MC AssignUnassignMessage (assign): the citizen moves in, leaving its old home; MC checks the mode in the window
     * only. At level 0, MC WindowHutLiving's chat refusal (workerhuts.level0).
     */
    public boolean assign(UUID player, BlockPos hutPos, int citizenId) {
        ManagedHut h = ManagedHut.find(manager, player, hutPos).orElse(null);
        LivingModule living = h == null ? null : h.building().module(LivingModule.class).orElse(null);
        if (living == null) {
            return false;
        }
        if (h.building().level() == 0) {
            manager.context().notifier().send(player, Msg.of("hycolony.hut.notBuiltYet"));
            return false;
        }
        CitizenData citizen = h.colony().citizens().get(citizenId).orElse(null);
        boolean done = citizen != null
                && !h.building().position().equals(citizen.homeBuilding())
                && living.assign(h.colony(), h.building(), citizen);
        manager.windows().showBuilding(h.colony(), h.building(), player);
        return done;
    }
```

`BuildingView` reçoit la composante `Optional<String> upgradeWarning` (à la fin, et dans le constructeur compact). Elle est remplie par une méthode `residenceWarning(Colony, Building)` de `BuildingViews`. Mets à jour les constructions de `BuildingView` dans les tests (`grep -rn "new BuildingView(" core plugin`).

- [ ] **Step 4 : lancer les tests** (`./gradlew :core:test`) : PASS.

- [ ] **Step 5 : build et commit**

```bash
git add core/src/main/java/dev/hycolony/core/citizen/home/ResidentsView.java core/src/main/java/dev/hycolony/core/citizen/home/ResidentsViews.java core/src/main/java/dev/hycolony/core/citizen/home/LivingModule.java core/src/main/java/dev/hycolony/core/app/action/HousingActions.java core/src/main/java/dev/hycolony/core/app/ui/BuildingView.java core/src/main/java/dev/hycolony/core/app/view/BuildingViews.java core/src/test/java/dev/hycolony/core/citizen/home/ResidentsViewsTest.java core/src/test/java/dev/hycolony/core/app/action/HousingActionsTest.java <tests modifiés>
git commit -m "feat(core): the residence's residents tab, assign, unassign, mode, recall and upgrade warnings"
```

---

### Task 7 : la maison dans la liste d'embauche

**Fichiers :**
- Modifier : `core/src/main/java/dev/hycolony/core/app/ui/BuildingView.java` (`WorkerRow`)
- Modifier : `core/src/main/java/dev/hycolony/core/app/view/BuildingViews.java` (`hireable`, `workerRow`)
- Test : le test de `BuildingViews`

**Interfaces :**
- Produit : `BuildingView.WorkerRow(int citizenId, String name, HomeLine home)` et `enum HomeLine { HOMELESS, LIVES_HERE, LIVES_AT_WORK, DISTANCE }`, avec `int homeDistance` : `record WorkerRow(int citizenId, String name, HomeLine home, int homeDistance)`.

Règles (MC `WindowHireWorker:349-376, 479-495`) :

- **Tri** des candidats, stable :
  - par distance maison → cette hutte, arrondie à 40 blocs : `d % 40 > 20` donne `d - d % 40 + 40`, sinon `d - d % 40` ;
  - un sans-abri vaut `100.0` ;
  - puis par nom.

  MC trie d'abord par `getCitizenPriority`, qui ne distingue que les citoyens employés, absents de notre liste.
- **Ligne** :
  - sans maison : `HOMELESS` ;
  - maison égale à cette hutte : `LIVES_HERE` ;
  - maison égale à l'atelier du citoyen : `LIVES_AT_WORK` ;
  - sinon `DISTANCE`, avec `(int) sqrt(distSq(home, hut))`.

- [ ] **Step 1 : le test qui échoue** : `hireListSortsByHomeDistanceThenName` (deux sans-abri, un logé à 30 blocs, un à 70 ; attendu : 30 (arrondi 40), 70 (arrondi 80), puis les deux sans-abri à 100 par nom) et `hireRowsSayWhereTheyLive`.
- [ ] **Step 2 : lancer, constater l'échec.**
- [ ] **Step 3 : implémenter** dans `BuildingViews` (`hireable` trie, `workerRow(c, b, d)` calcule la ligne). Les travailleurs actuels gardent la même ligne.
- [ ] **Step 4 : lancer les tests**, puis `./gradlew :plugin:compileJava` (`WorkerRow` a changé : corrige `BuildingMainTab`, qui n'affiche pas encore la ligne).
- [ ] **Step 5 : build et commit**

```bash
git add core/src/main/java/dev/hycolony/core/app/ui/BuildingView.java core/src/main/java/dev/hycolony/core/app/view/BuildingViews.java <test> plugin/src/main/java/dev/hycolony/plugin/ui/BuildingMainTab.java
git commit -m "feat(core): the hire list sorts by home distance and tells where each candidate lives, as MC"
```

---

### Task 8 (plugin) : l'onglet « Habitants »

**Fichiers :**
- Créer :
  - `plugin/src/main/java/dev/hycolony/plugin/ui/hut/ResidentsTab.java` ;
  - `plugin/src/main/resources/Common/UI/Custom/Pages/HyColony/ResidentsTab.ui` ;
  - `plugin/src/main/resources/Common/UI/Custom/Pages/HyColony/ResidentRow.ui`.
- Modifier :
  - `plugin/src/main/java/dev/hycolony/plugin/ui/hut/HutTabs.java` (`case ResidentsView r -> …`) ;
  - `plugin/src/main/resources/Server/Languages/en-US/hycolony.lang` et `fr-FR/hycolony.lang`.

Il n'y a pas de test unitaire : le plugin se vérifie en jeu (§ 8). L'agent `ui-lang-checker` relit cette tâche.

- [ ] **Step 1 : les `.ui`**, copiés de `FieldsTab.ui` et `FieldRow.ui` :
  - **`ResidentsTab.ui`** :
    - `Label #Assigned` (gras) ;
    - une rangée de boutons `#ModeButton` et `#RecallButton` ;
    - `Label #ResidentsTitle` puis `Group #Residents` (`TopScrolling`, hauteur 150) ;
    - `Label #CandidatesTitle` puis `Group #Candidates` (`TopScrolling`, hauteur 190) ;
    - `Label #Empty` caché.
  - **`ResidentRow.ui`** : `Label #Name` (gras), `Label #Line` (couleur `#6fa8dc` par défaut), et un `$C.@SecondaryTextButton #ActionButton` de largeur 130 avec `TextTooltipStyle`.
- [ ] **Step 2 : `ResidentsTab`**, sur le modèle de `FieldsTab` :
  - **En-tête** :
    - `#Assigned.Text` : `hycolony.ui.residence.assigned` avec `{p0}/{p1}` ;
    - `#ModeButton.Text` : `hycolony.ui.hiringmode.<mode>`, une clé par mode ;
    - `#RecallButton.Text` : `hycolony.ui.residence.recall`.

    Ces deux boutons sont désactivés si `!canManage`.
  - **Ligne d'habitant** :
    - nom, et ligne `.TextSpans` : métier traduit (la clé de métier qu'utilise déjà la fenêtre du citoyen) + « : » + `hycolony.ui.residence.works` `{p0}`, en rouge (`#c0392b`) si `far`, ou `hycolony.ui.residence.unemployed` ;
    - bouton `hycolony.ui.residence.unassign`.
  - **Ligne de candidat** :
    - même début ;
    - `works` en vert (`#3c9a4f`) si `closer` ;
    - puis `hycolony.ui.residence.homeless` ou `hycolony.ui.residence.currently` `{p0}` (rouge si `home.far`) ;
    - bouton `hycolony.ui.residence.assign`.
  - **Boutons Assign et Unassign** :
    - si `!manual` : désactivés, avec l'infobulle `hycolony.ui.residence.hireWarning` ;
    - si `manual` et la hutte est pleine (`assigned >= max`) : Assign désactivé ;
    - si `!canManage` : désactivés.
  - **`handle`** :
    - `residentsMode` → `cycleMode` ;
    - `residentsRecall` → `recall` ;
    - `residentAssign` / `residentUnassign` avec l'index de la ligne → `assign` / `unassign` ;
    - un index inconnu ne fait rien ;
    - par `new HousingActions(manager)`.
  - `labelKey()` : `hycolony.ui.building.tab.residents`.
- [ ] **Step 3 : les clés** (skill `add-lang-key`), avec les textes de MC (R § E.5) en en-US, puis leur traduction en fr-FR :
  - `ui.building.tab.residents` : Residents / Habitants ;
  - `ui.residence.assigned` : Assigned Citizens: {p0}/{p1} / Citoyens assignés : {p0}/{p1} ;
  - `ui.residence.recall` : Recall Citizens / Rappeler les citoyens ;
  - `ui.residence.assign`, `ui.residence.unassign` : Assign, Unassign / Attribuer, Retirer ;
  - `ui.hiringmode.default`, `auto`, `manual`, `locked` : Default (colony override), Automatic, Manual, Locked (no kids). Vérifie d'abord si ces clés existent déjà pour l'onglet des travailleurs, et réutilise-les si c'est le cas ;
  - `ui.residence.works` : Works {p0} blocks from here. ;
  - `ui.residence.homeless` : Homeless ;
  - `ui.residence.currently` : Current work distance: {p0} blocks ;
  - `ui.residence.unemployed` : Unemployed ;
  - `ui.residence.hireWarning` : Turn the hiring mode of this hut (or colony) to manual to remove this citizen or assign another one. ;
  - `ui.residence.residentsTitle`, `ui.residence.candidatesTitle` : Living here, Other citizens / Habitent ici, Autres citoyens ;
  - `hut.recallFail` : Recall failed. Please make more space around the location. ;
  - `ui.residence.warning.2` à `.5` : les textes de MC (`com.minecolonies.core.gui.residence.warning.2` à `.5`, sur GitHub).
- [ ] **Step 4 : build et commit**

`./gradlew build` (avec `checkLangParity`).

```bash
git add plugin/src/main/java/dev/hycolony/plugin/ui/hut/ResidentsTab.java plugin/src/main/java/dev/hycolony/plugin/ui/hut/HutTabs.java plugin/src/main/resources/Common/UI/Custom/Pages/HyColony/ResidentsTab.ui plugin/src/main/resources/Common/UI/Custom/Pages/HyColony/ResidentRow.ui plugin/src/main/resources/Server/Languages/en-US/hycolony.lang plugin/src/main/resources/Server/Languages/fr-FR/hycolony.lang
git commit -m "feat(plugin): the residence's residents tab"
```

---

### Task 9 (plugin) : statistiques, `/hycolony info`, embauche et avertissements

**Fichiers :**
- Modifier :
  - le rendu de l'onglet Statistiques de l'hôtel de ville (`plugin/.../ui/townhall/*`) ;
  - `plugin/src/main/java/dev/hycolony/plugin/command/HyColonyCommand.java` ;
  - `plugin/src/main/java/dev/hycolony/plugin/ui/BuildingMainTab.java` (ligne de maison des candidats et des travailleurs) ;
  - `plugin/src/main/java/dev/hycolony/plugin/ui/BuildOptionsPanel.java` (avertissement) ;
  - les deux `hycolony.lang`.
- Créer : `plugin/src/main/java/dev/hycolony/plugin/ui/ConfirmPage.java` et `ConfirmPage.ui`, sur le modèle de `FoundColonyPage` (titre, texte, Confirmer, Annuler).

- [ ] **Step 1 : statistiques.**
  - `ui.townhall.stats.citizens` devient « Total citizens: {p0}/{p1} » / « Citoyens : {p0}/{p1} ». Les paramètres sont `stats.citizens()` et `stats.maxCitizens()`.
  - Couleur : `OK` en vert `#3c9a4f`, `NEEDS_HOUSING` en orange `#e69138`, `CONFIG_LIMITED` en rouge `#c0392b`.
  - Infobulle : `NEEDS_HOUSING` affiche `ui.townhall.stats.needsHousing` (« Needs Housing » / « Manque de logements ») ; `CONFIG_LIMITED` affiche `ui.townhall.stats.configLimited` (« Reached Configured Limit » / « Limite de la configuration atteinte »).
- [ ] **Step 2 : `/hycolony info`.** `cmd.info` se termine par « {p5}/{p6} citizens ». Le paramètre `p6` vaut `HousingCapacity.of(c).shownMax()`.
- [ ] **Step 3 : embauche.** Dans `BuildingMainTab`, chaque ligne de `hireable` reçoit sous le nom :
  - `HOMELESS` : `ui.hiring.homeless` (« Currently homeless ») ;
  - `LIVES_HERE` : `ui.hiring.livesHere` (« Lives here ») ;
  - `LIVES_AT_WORK` : `ui.hiring.livesAtWork` (« Lives at current work building ») ;
  - `DISTANCE` : `ui.hiring.distance` (« Lives {p0} blocks from here »).

  Ajoute un `Label` à `WorkerRow.ui` si la ligne n'en a qu'un.
- [ ] **Step 4 : avertissement d'amélioration** (MC `WindowBuildBuilding:130, 207-211`).
  - Si `view.upgradeWarning()` est présent, le bouton Construire / Améliorer de `BuildOptionsPanel` porte ce texte en infobulle.
  - Le clic ouvre `ConfirmPage` : titre `ui.build.confirm.title` (« Are you sure? » ; vérifie le texte de MC `com.minecolonies.core.gui.build.confirm.title`), texte de l'avertissement.
  - Confirmer lance l'ordre comme aujourd'hui ; Annuler rouvre la fenêtre de la hutte.
- [ ] **Step 5 : build et commit**

`./gradlew build`, puis ajoute à `docs/TESTING.md` les pas de l'étape 1 (S § 11, trois premières puces, plus la résidence d'avant SP4).

```bash
git add <fichiers ci-dessus> docs/TESTING.md
git commit -m "feat(plugin): housing in the town hall statistics, info, hire list and residence upgrade warnings"
```

**Fin de l'étape 1** : l'utilisateur peut tester la résidence en jeu.

---

# Étape 2 : le sommeil

### Task 10 : l'horloge « façon MC »

**Fichiers :**
- Modifier : `core/src/main/java/dev/hycolony/core/kernel/port/GameClock.java`
- Modifier : `core/src/test/java/dev/hycolony/core/testing/FakeClock.java`
- Modifier : `plugin/src/main/java/dev/hycolony/plugin/adapter/HytaleGameClock.java`, une première version pour que le plugin compile (la version complète vient en Task 17)
- Test : `core/src/test/java/dev/hycolony/core/testing/FakeClockTest.java`, pour fixer le contrat que les tests suivants supposent

**Interfaces :**
- Produit :
  - `GameClock` :
    - `int dayTime()` : l'heure MC dans [0, 24000[, avec le jour Hytale sur [0, 12600[ et la nuit sur [12600, 24000[ ;
    - `long realTicksUntil(int dayTime)` : les ticks réels avant la prochaine fois que l'horloge atteint cette heure ; `Long.MAX_VALUE` si l'heure est en pause ;
  - `FakeClock` :
    - champs publics `int dayTime = 0`, `boolean paused`, `double realTicksPerDayTick = 1` ;
    - `isDaytime()` reste piloté par le champ `daytime` existant, pour ne pas casser les tests en cours.

Javadoc de `GameClock` : le calage par phases (S § 4), et MC `WorldUtil.isDayTime`, qui vaut `dayTime <= NIGHT`. `isDaytime()` reste dans le port : `Colony.checkDayTime` le lit, et `HytaleGameClock` l'implémente avec les mêmes bornes.

- [ ] **Step 1 : le test**

```java
package dev.hycolony.core.testing;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class FakeClockTest {
    @Test
    void realTicksUntilWrapsAroundTheDayAndStopsWhenPaused() {
        FakeClock c = new FakeClock();
        c.dayTime = 12000;
        c.realTicksPerDayTick = 2;

        assertEquals(1200, c.realTicksUntil(12600));
        assertEquals(2 * 23400, c.realTicksUntil(11400));
        c.paused = true;
        assertEquals(Long.MAX_VALUE, c.realTicksUntil(12600));
    }
}
```

- [ ] **Step 2 : lancer, constater l'échec.**
- [ ] **Step 3 : implémenter** `FakeClock` (`floorMod(target - dayTime, 24000) * ratio`, arrondi en `long`), le port, et une première version de `HytaleGameClock` : `dayTime` et `realTicksUntil` complets (Task 17, étape 1). Si tu les écris dès maintenant, fais-le directement en version finale.
- [ ] **Step 4 : lancer les tests**, puis `./gradlew :plugin:compileJava`.
- [ ] **Step 5 : build et commit** : `feat(core): the colony clock reads MC's day time, mapped on Hytale's day and night`.

---

### Task 11 : sortir l'errance de `CitizenAI`

Refactorisation sans changement de comportement, pour faire de la place au sommeil.

**Fichiers :**
- Créer : `core/src/main/java/dev/hycolony/core/citizen/CitizenWander.java` (le paquet passe à 14 fichiers)
- Modifier : `core/src/main/java/dev/hycolony/core/citizen/CitizenAI.java`

**Interfaces :**
- Produit : `final class CitizenWander`, package-private, avec :
  - le constructeur `CitizenWander(Colony colony, BodyId body)` ;
  - `@Nullable CitizenState wander()` (la transition `IDLE` de 100 ticks, renvoie toujours `null`) ;
  - `void restartWait()`, qui remplace les `waitingSince = NOT_WAITING` de `dropJobAI` et `forgetJobAI`.

- [ ] **Step 1** : déplace sans les changer `WANDER_RADIUS`, `WANDER_TRIES`, `WANDER_DANGER_HALF_HEIGHT`, `WANDER_TIMEOUT_TICKS`, `NOT_WAITING`, `waitingSince`, `danger`, `wander()` et `wanderTarget()`, avec leurs Javadocs. `CitizenAI` garde `WANDER_RATE_TICKS` et la transition `machine.addTransition(new AITarget<>(CitizenState.IDLE, (IStateSupplier<CitizenState>) wander::wander, WANDER_RATE_TICKS))`.
- [ ] **Step 2** : les tests de l'errance qui lisent `CitizenAI.WANDER_TIMEOUT_TICKS` lisent maintenant `CitizenWander.WANDER_TIMEOUT_TICKS`.
- [ ] **Step 3** : `./gradlew :core:test` : PASS, avec les **mêmes** tests et sans changer une seule assertion. `CitizenAI` doit passer sous 260 lignes.
- [ ] **Step 4** : commit `refactor(core): the citizen's wander leaves CitizenAI`.

---

### Task 12 : `SleepDecision`

**Fichiers :**
- Créer : `core/src/main/java/dev/hycolony/core/citizen/sleep/SleepDecision.java`, `core/src/main/java/dev/hycolony/core/citizen/home/HomePosition.java`
- Test : `core/src/test/java/dev/hycolony/core/citizen/sleep/SleepDecisionTest.java`, `core/src/test/java/dev/hycolony/core/citizen/home/HomePositionTest.java`

**Interfaces :**
- Produit :
  - `HomePosition.of(Colony, CitizenData)` renvoie `Optional<BlockPos>`, MC `getHomePosition` : la maison, sinon l'hôtel de ville, sinon vide ;
  - `SleepDecision` :
    - les constantes `NIGHT = 12600`, `EVENING = NIGHT - 2000`, `SLEEP_DECIDE_DELAY_TICKS = 20 * 15`, `Y_DIFF_WEIGHT = 1.5`, `TIME_PER_BLOCK = 6` ;
    - `enum Verdict { NONE, GO_TO_SLEEP, STAY_ASLEEP, WAKE_UP }` ;
    - `static Verdict decide(GameClock clock, boolean inSleepState, boolean asleep, Optional<BlockPos> home, BlockPos at)` ;
    - `static boolean shouldGoSleep(GameClock clock, Optional<BlockPos> home, BlockPos at)`.

Règles (MC `CitizenAI.calculateNextState:168-201`, `CitizenSleepHandler.shouldGoSleep:231-281`) :

```java
    /**
     * MC calculateNextState, sleep part: in the evening and night (day time past EVENING), a citizen already in SLEEP
     * stays there and one that should go to bed does; by day, a citizen in SLEEP or asleep wakes up.
     */
    public static Verdict decide(
            GameClock clock, boolean inSleepState, boolean asleep, Optional<BlockPos> home, BlockPos at) {
        if (clock.dayTime() > EVENING) {
            if (inSleepState) {
                return Verdict.STAY_ASLEEP;
            }
            return shouldGoSleep(clock, home, at) ? Verdict.GO_TO_SLEEP : Verdict.NONE;
        }
        return inSleepState || asleep ? Verdict.WAKE_UP : Verdict.NONE;
    }

    /**
     * MC CitizenSleepHandler.shouldGoSleep: leaves just in time to be home at NIGHT, at TIME_PER_BLOCK per block of a
     * distance weighting height by Y_DIFF_WEIGHT, truncated to int as MC. Deviation from MC: MC counts the walk in day
     * ticks, which are real ticks in Minecraft; with the day mapped by phases (spec § 4) both durations are real ticks.
     */
    public static boolean shouldGoSleep(GameClock clock, Optional<BlockPos> home, BlockPos at) {
        if (home.isEmpty()) {
            return false;
        }
        BlockPos h = home.get();
        int xDiff = Math.abs(h.x() - at.x());
        int zDiff = Math.abs(h.z() - at.z());
        int yDiff = (int) (Math.abs(h.y() - at.y()) * Y_DIFF_WEIGHT);
        double timeNeeded = Math.sqrt((double) xDiff * xDiff + (double) zDiff * zDiff + (double) yDiff * yDiff)
                * TIME_PER_BLOCK;
        long timeLeft = clock.dayTime() >= NIGHT ? 0 : clock.realTicksUntil(NIGHT);
        return timeLeft <= 0 || timeLeft - timeNeeded <= 0;
    }
```

Javadoc de la classe :
- la plainte « hometoofar » (`MAX_NO_COMPLAIN_DISTANCE = 160`) est reportée avec les interactions (S § 5.1) ;
- MC réveille un malade près de son lit plus tard (maladie non portée).

- [ ] **Step 1 : les tests qui échouent**, avec `FakeClock` :
  - `noBedtimeBeforeTheEvening` (`dayTime = 10600`) ;
  - `leavesJustInTimeForItsDistance` (`dayTime = 12000`, ratio 1 : 100 blocs plats, soit 600 ticks, part ; 101 blocs part aussi ; 99 blocs reste) ;
  - `heightWeighsOneAndAHalfTruncated` (`dy = 3` donne `yDiff = 4`) ;
  - `pastNightfallLeavesAtOnce` (`dayTime = 13000`, maison à 500 blocs) ;
  - `pausedClockNeverSendsHomeEarly` (`paused`, `dayTime = 11000`, maison à 50 blocs : NONE) ;
  - `withoutHomeNorTownHallNeverSleeps` ;
  - `asleepStaysAsleepAtNight` ;
  - `dawnWakesTheSleepStateAndTheAsleep` (`dayTime = 0` : WAKE_UP pour `inSleepState` ou `asleep`, NONE sinon).

  `HomePositionTest` : la maison, sinon l'hôtel de ville, sinon vide.
- [ ] **Step 2 : lancer, constater l'échec.**
- [ ] **Step 3 : implémenter.**
- [ ] **Step 4 : lancer les tests** : PASS.
- [ ] **Step 5 : commit** `feat(core): MC's bedtime decision, leaving just in time to be home at nightfall`.

---

### Task 13 : le port des lits, `SleepHandler` et `SleepNotice`

**Fichiers :**
- Modifier :
  - `core/src/main/java/dev/hycolony/core/kernel/port/CitizenBodies.java` (`sleepIn`, `isInBed`, `wakeUp`) ;
  - `core/src/main/java/dev/hycolony/core/kernel/port/WorldEffects.java` (`sleeping`) ;
  - `core/src/test/java/dev/hycolony/core/testing/FakeBodies.java`, `FakeWorldEffects.java` ;
  - `core/src/main/java/dev/hycolony/core/job/Job.java` (`onWakeUp`) ;
  - `core/src/main/java/dev/hycolony/core/building/module/BuildingEventsModule.java` (`onWakeUp` et aide statique `wakeUp`) ;
  - `core/src/main/java/dev/hycolony/core/citizen/CitizenManager.java` (`sleepNotice()`) ;
  - `plugin/src/main/java/dev/hycolony/plugin/adapter/HytaleCitizenBodies.java` et `HytaleWorldEffects.java` : des versions qui compilent (`false`, aucune action), remplacées en Task 17.
- Créer :
  - `core/src/main/java/dev/hycolony/core/citizen/sleep/SleepHandler.java` ;
  - `core/src/main/java/dev/hycolony/core/citizen/sleep/SleepNotice.java`.
- Test : `SleepHandlerTest.java` et `SleepNoticeTest.java` sous `core/src/test/java/dev/hycolony/core/citizen/sleep/`

**Interfaces :**
- Produit :
  - `CitizenBodies` :
    - `boolean sleepIn(BodyId, BlockPos bed)` : couche le corps dans le lit dont `bed` est le bloc de base ; faux si ce n'est pas un lit chargé, si le lit est pris ou si le corps est inconnu ;
    - `boolean isInBed(BodyId)` ;
    - `void wakeUp(BodyId)` : lève le corps au point de sortie à côté du lit ; sans effet sur un corps debout ;
  - `WorldEffects.void sleeping(Vec3 at)` : les particules de sommeil (MC `SleepingParticleMessage`) ;
  - `FakeBodies` :
    - `Body.inBed` (`BlockPos`, null debout) ;
    - `public final Set<BlockPos> takenBeds` ;
    - `sleepIn` refuse un lit présent dans `takenBeds` ou occupé par un autre corps, et un lit hors de `beds` (champ `public final Set<BlockPos> beds`) ;
    - `wakeUp` remet `inBed` à null ;
  - `FakeWorldEffects.sleeping` : une liste `sleeps` ;
  - `Job.void onWakeUp(Colony colony) {}`, et `BuildingEventsModule` : `default void onWakeUp(Colony, Building) {}` et `static void wakeUp(Colony, Building)` ;
  - `SleepHandler(Colony colony, CitizenData data, BodyId body)` :
    - `boolean trySleep(BlockPos bed)` ;
    - `void wakeUp()` ;
    - `void leftBed()` ;
  - `SleepNotice` : `void onCitizenSleep(Colony)` et `void onNightFall()` ; `CitizenManager.sleepNotice()`.

Règles :

- **`trySleep`** (MC `CitizenSleepHandler.trySleep:97-139`) : si `!bodies.sleepIn(body, bed)`, faux. Sinon :
  - `setHeldItem(empty)` ;
  - `data.setAsleep(true)` ;
  - `data.setBedPos(bed)` ;
  - `colony.citizens().sleepNotice().onCitizenSleep(colony)` ;
  - `colony.markDirty()` ;
  - vrai.
- **`wakeUp`** (MC `onWakeUp:144-218`), sans effet si `!data.asleep()` (chaque appelant de MC teste `isAsleep`) :
  - `BuildingEventsModule.wakeUp` sur l'atelier ;
  - `job.onWakeUp(colony)` ;
  - `BuildingEventsModule.wakeUp` sur la maison ;
  - puis `bodies.wakeUp(body)`, `bedPos = null`, `asleep = false`, `markDirty`.
- **`leftBed`** : le lit a été quitté sans nous (S § 6, `isInBed`). On met `asleep = false` et `bedPos = null`, sans crochets. *Deviation from MC* : un joueur ne fait jamais sortir un citoyen du lit dans MC.
- **`SleepNotice.onCitizenSleep`** (MC `CitizenManager.onCitizenSleep:672-688`) : si **tous** les citoyens ont `asleep()` et que le message n'est pas encore parti, envoie `Msg.of("hycolony.citizen.allAsleep")` à chaque joueur de la colonie en ligne (propriétaire et membres de `permissions()`, `players().isOnline`), puis marque l'envoi. *Deviation from MC* : MC n'exclut que les gardes, que HyColony n'a pas. `onNightFall()` remet la marque à faux.

- [ ] **Step 1 : les tests qui échouent**
  - `SleepHandlerTest` :
    - `lyingDownDropsTheHeldItemEndsLeisureAndRemembersTheBed` ;
    - `refusedBedChangesNothing` ;
    - `wakeUpTellsWorkJobAndHomeThenStandsUp` (un `BuildingEventsModule` de test sur l'atelier et la maison compte ses `onWakeUp`, et un `Job` de test compte le sien, voir `TestJobs`) ;
    - `wakeUpOfAnAwakeCitizenDoesNothing` ;
    - `leftBedForgetsTheBedWithoutHooks`.
  - `SleepNoticeTest` :
    - `allAsleepNeedsEveryCitizenAsleep` ;
    - `announcedOnceToOnlineMembersUntilNightFalls`.
- [ ] **Step 2 : lancer, constater l'échec.**
- [ ] **Step 3 : implémenter**, avec les versions temporaires du plugin, pour que `./gradlew :plugin:compileJava` passe.
- [ ] **Step 4 : lancer les tests** : PASS.
- [ ] **Step 5 : commit** `feat(core): lying down in and getting out of a bed, and the all-asleep notice, as MC's sleep handler`.

---

### Task 14 : `SleepAI`

**Fichiers :**
- Créer : `core/src/main/java/dev/hycolony/core/citizen/sleep/SleepAI.java`
- Test : `core/src/test/java/dev/hycolony/core/citizen/sleep/SleepAITest.java`

**Interfaces :**
- Consomme :
  - `SleepHandler`, `HomePosition`, `HutFootprint.isInBuilding` ;
  - `BlockApproach` : `walkToBuilding`, `walkToPosInBuilding(pos, building, 12)` et `walkToSafePos` ;
  - `LivingModule.residents()`, `BedModule.bed(rank)` et `removeBed`, `ItemCatalog.isBed` et `kind`, `WorldEffects.sleeping`.
- Produit :
  - `final class SleepAI` : `SleepAI(Colony colony, CitizenData data, BodyId body, SleepHandler handler)` ;
  - `void tick()` ;
  - `SleepAI.State state()`, avec `enum State { INIT, WALKING_HOME, FIND_BED, SLEEPING }`.

Port de MC `EntityAISleep` sur un `TickRateStateMachine<State>` (`IState`), de départ `INIT` :

| Transition | Cadence | Effet |
|---|---|---|
| `INIT` | 20 | `initAI()` (`usedBed = null`, `bedTicks = 0`), puis `WALKING_HOME` |
| `WALKING_HOME` | 30 | `walkHome()` |
| `FIND_BED` | 30 | condition `findBed()`, puis `SLEEPING` |
| `SLEEPING` | 30 | `sleep()` |

Le marcheur est un `BlockApproach` neuf, créé comme dans `CitizenAI` (`BodyWalker` + `CitizenWalkReports`), dans le constructeur.

```java
    /** MC walkHome: FIND_BED once inside the home (or within 4 blocks of the town hall without one), else walks on. */
    private @Nullable State walkHome() {
        BlockPos at = here().orElse(null);
        if (at == null) {
            return null;
        }
        Optional<Building> home = home();
        if (home.isEmpty()) {
            Optional<BlockPos> townHall = HomePosition.of(colony, data);
            if (townHall.isPresent() && townHall.get().distSq(at) <= RANGE_TO_BE_HOME) {
                return State.FIND_BED;
            }
            townHall.ifPresent(walker::walkToSafePos); // MC walkToPos(homePosition, 4, true)
            return State.WALKING_HOME;
        }
        if (HutFootprint.isInBuilding(colony.context().ports(), home.get(), at)) {
            return State.FIND_BED;
        }
        walker.walkToBuilding(home.get());
        return State.WALKING_HOME;
    }

    /** MC findBed: tries to lie down until asleep or MAX_BED_TICKS arrivals. */
    private boolean findBed() {
        if (!data.asleep() && bedTicks < MAX_BED_TICKS) {
            findBedAndTryToSleep();
            return false;
        }
        return true;
    }
```

`findBedAndTryToSleep()` (MC l. 159-220) : rien sans maison. Ensuite :

```java
        Building hut = home().orElse(null);
        if (hut == null) {
            return;
        }
        BlockPos homePos = hut.position();
        if (usedBed == null || usedBed.equals(homePos)) {
            Optional<BlockPos> pick = pickBed(hut, homePos); // empty: a stale bed was just removed
            if (pick.isEmpty()) {
                return;
            }
            usedBed = pick.get();
            if (!usedBed.equals(homePos)) {
                return; // MC: the bed is chosen; the walk starts at the next attempt
            }
        }
        if (walker.walkToPosInBuilding(usedBed, hut, IN_BUILDING_REACH)) {
            bedTicks++;
            if (!handler.trySleep(usedBed)) { // a taken bed, or the hut block: MC falls back to the hut, then fails
                data.setBedPos(null);
                usedBed = null;
            }
        } else {
            bedTicks = 0;
        }
```

`pickBed(hut, homePos)` renvoie le lit choisi, `homePos` pour un repli sur la hutte, ou vide quand un lit périmé vient d'être retiré. Ses règles :
- `rank = living.residents().indexOf(data.id())`, puis `beds.bed(rank)` ;
- sans lit à ce rang, ou si le bloc n'est pas chargé (`blocks.get(pos)` vide) : `Optional.of(homePos)` ;
- si le bloc n'est plus un lit (`!isBed(key)`) : `removeBed(pos)`, `markDirty`, `Optional.empty()` ;
- si c'est un lit et que le bloc au-dessus est un lit ou n'est pas `SOLID` (`catalog.kind`) : ce lit ;
- sinon : `Optional.of(homePos)`.

```java
    /**
     * MC sleep: a citizen whose bed is more than 3 blocks away walks back; without a bed it tries again. Deviation
     * from MC: a bed left without us (players' night skip, bed broken: {@link CitizenBodies#isInBed}) also walks back,
     * awake, to lie down again; MC re-applies the pose instead.
     */
    private @Nullable State sleep() {
        BlockPos at = here().orElse(null);
        if (at == null) {
            return null;
        }
        if (usedBed != null) {
            if (usedBed.distSq(at) > 9 || (data.asleep() && !bodies.isInBed(body))) {
                if (data.asleep()) {
                    handler.leftBed();
                }
                initAI();
                return State.WALKING_HOME;
            }
        } else {
            findBedAndTryToSleep();
        }
        effects.sleeping(new Vec3(at.x() + 0.5, at.y() + 1.0, at.z() + 0.5)); // MC: at the citizen, one block up
        return null;
    }
```

- [ ] **Step 1 : les tests qui échouent**, avec `FakeBodies` (`instant = true` pour que les marches arrivent), `FakeWorldBlocks` (pose les lits), `FakeCatalog.beds` et une résidence au plan simple (`t.blueprints`). Fais avancer d'abord la machine de 20 ticks, puis de 30 :
  - `walksHomeThenLiesInTheBedOfItsRank` (deux habitants, deux lits : le second habitant prend le second lit) ;
  - `rankPastTheBedsSleepsStandingInTheHut` (après `MAX_BED_TICKS` arrivées : `SLEEPING`, pas couché, particules envoyées) ;
  - `bedNoLongerABedIsRemovedWithoutWalking` (puis le rang est recalculé sur la liste raccourcie) ;
  - `solidBlockAboveRefusesTheBed` ;
  - `takenBedFallsBackAndClearsTheBedPos` ;
  - `farFromItsBedWalksBack` ;
  - `bedLeftWithoutUsLiesDownAgain` (`FakeBodies` met `inBed = null` : `WALKING_HOME`, `asleep` faux, puis recouché) ;
  - `homelessStandsByTheTownHall` (à 4 blocs, `FIND_BED`, jamais couché) ;
  - `notArrivedResetsTheBedTicks`.
- [ ] **Step 2 : lancer, constater l'échec.**
- [ ] **Step 3 : implémenter.**
- [ ] **Step 4 : lancer les tests** : PASS.
- [ ] **Step 5 : commit** `feat(core): citizens walk home, find their bed and sleep, as MC's EntityAISleep`.

---

### Task 15 : brancher le sommeil

**Fichiers :**
- Modifier :
  - `core/src/main/java/dev/hycolony/core/citizen/CitizenState.java` (`SLEEP`) ;
  - `core/src/main/java/dev/hycolony/core/citizen/CitizenAI.java` ;
  - `core/src/main/java/dev/hycolony/core/citizen/CitizenManager.java` (`onNightFall`, `onWakeUp`) ;
  - `core/src/main/java/dev/hycolony/core/colony/Colony.java` (`checkDayTime`).
- Test : `core/src/test/java/dev/hycolony/core/citizen/sleep/SleepCycleTest.java`

**Interfaces :**
- Consomme : `SleepDecision`, `SleepAI`, `SleepHandler`.
- Produit :
  - `CitizenState.SLEEP` ;
  - `CitizenManager` : `void onNightFall()` et `void onWakeUp()`.

Branchement dans `CitizenAI` :

```java
        this.sleep = new SleepHandler(colony, data, body);
        machine.addTransition(new AIEventTarget<>(AIBlockingEventType.EVENT, this::decideSleep, DECIDE_INTERVAL_TICKS));
        machine.addTransition(new AITarget<>(CitizenState.SLEEP, (IStateSupplier<CitizenState>) this::sleeping, 1));
        sleep.wakeUp(); // MC CitizenData:574: a body appears standing (Hytale saves no NPC in bed)
```

```java
    /**
     * MC CitizenAI.decideAiTask, sleep part, every DECIDE_INTERVAL_TICKS in any state, before rain, leisure and work:
     * see {@link SleepDecision#decide}. Asleep, it decides again only every 15 s (MC setCurrentDelay(20 * 15)).
     */
    private @Nullable CitizenState decideSleep() {
        BlockPos at = bodies.position(body).map(Vec3::toBlockPos).orElse(null);
        if (at == null) {
            return null;
        }
        CitizenState now = machine.getState();
        return switch (SleepDecision.decide(
                colony.context().clock(), now == CitizenState.SLEEP, data.asleep(), HomePosition.of(colony, data), at)) {
            case STAY_ASLEEP -> {
                machine.setCurrentDelay(SleepDecision.SLEEP_DECIDE_DELAY_TICKS);
                yield null;
            }
            case GO_TO_SLEEP -> {
                dropJobAI();
                sleepAI = new SleepAI(colony, data, body, sleep);
                yield CitizenState.SLEEP;
            }
            case WAKE_UP -> {
                sleep.wakeUp();
                sleepAI = null;
                yield now == CitizenState.SLEEP ? CitizenState.IDLE : null;
            }
            case NONE -> null;
        };
    }
```

- `sleeping()` appelle `sleepAI.tick()` (et en crée un si `sleepAI` est null) et renvoie `null`.
- `teleport(Vec3)` commence par `sleep.wakeUp()` (MC `TeleportHelper:49-52`).
- `jobActivity` ne change pas.

`Colony.checkDayTime` :
- à la tombée de la nuit, `citizens.onNightFall()` (qui appelle `sleepNotice().onNightFall()`, MC `updateCitizenSleep(false)`), **avant** de publier `NightFell` ;
- à l'aube, `citizens.onWakeUp()`, MC `CitizenManager.onWakeUp`, qui appelle `updateBodyIfNecessary` pour chaque citizen (rendre `updateBodyIfNecessary` accessible à cette méthode suffit, elle est dans la même classe).

- [ ] **Step 1 : les tests qui échouent** (`SleepCycleTest`), sur une colonie complète avec `FakeClock`, `FakeBodies` (`instant`), une résidence de niveau 1 avec un lit, un hôtel de ville et un constructeur :
  - `workerStopsWorkingAndGoesToBedAtNight` (`dayTime` à 12600, puis 40 ticks : état `SLEEP`, IA de métier lâchée) ;
  - `sleepOutranksRainAndLeisure` ;
  - `wakesAtDawnAndWorksAgain` (`dayTime = 0` : `asleep` faux, corps levé, état `IDLE`, puis `WORKING`) ;
  - `asleepDecidesOnlyEveryFifteenSeconds` (compte les appels de `realTicksUntil`, ou observe qu'un `dayTime = 0` n'est vu qu'après 300 ticks) ;
  - `teleportWakesFirst` ;
  - `spawnedBodyWakesASavedSleeper` (`data.setAsleep(true)` avant `bind`) ;
  - `bodilessCitizenRespawnsAtDawn` ;
  - `removedHomeAtNightStillWakesAtDawn` ;
  - `nightfallRearmsTheAllAsleepNotice`.
- [ ] **Step 2 : lancer, constater l'échec.**
- [ ] **Step 3 : implémenter.** `CitizenAI` doit rester sous 300 lignes. S'il dépasse, sors `decideSleep` et `sleeping` dans un `CitizenSleep` du paquet `citizen/sleep`, qui tient `SleepHandler` et `SleepAI`.
- [ ] **Step 4 : lancer les tests**, la suite complète du cœur : PASS. Les tests existants de l'IA tournent en plein jour (`dayTime = 0` par défaut dans `FakeClock`) : aucun ne doit changer.
- [ ] **Step 5 : commit** `feat(core): citizens sleep at night and wake at dawn, before rain, leisure and work`.

---

### Task 16 : le sommeil sur la plaque de nom

**Fichiers :**
- Modifier : `core/src/main/java/dev/hycolony/core/colony/CitizenNameplates.java`
- Test : son test existant

Règle : MC dessine le statut `SLEEP` au-dessus de la tête, pendant la marche du retour et le sommeil (R § E.3, point 12). Le nom devient `"zZz " + nom` tant que l'IA est en `SLEEP`. Le « ! » d'une demande en attente garde la priorité, comme MC qui dessine l'interaction avant le statut. La Javadoc de la classe ajoute cet écart (pas d'icône au-dessus de la tête dans Hytale).

- [ ] **Step 1 : test** `sleepingCitizenShowsZzzAndAPendingRequestWins`.
- [ ] **Step 2 : constater l'échec.**
- [ ] **Step 3 : implémenter.** `nameFor` et `refresh` lisent `colony.citizens().aiState(id)`.
- [ ] **Step 4 : tests** : PASS.
- [ ] **Step 5 : commit** `feat(core): a sleeping citizen's nameplate says zZz, as MC's sleep status`.

---

### Task 17 (plugin) : horloge, coucher natif, particules, textes

**Fichiers :**
- Modifier :
  - `plugin/src/main/java/dev/hycolony/plugin/adapter/HytaleGameClock.java` ;
  - `plugin/src/main/java/dev/hycolony/plugin/adapter/HytaleCitizenBodies.java` ;
  - `plugin/src/main/java/dev/hycolony/plugin/adapter/HytaleWorldEffects.java` ;
  - les deux `hycolony.lang`.
- Créer : `plugin/src/main/java/dev/hycolony/plugin/npc/BodyTeleport.java` et `plugin/src/main/java/dev/hycolony/plugin/npc/CitizenBeds.java`

Revérifie chaque appel dans `build/vineflower/hytale-server` (skill `hytale-api`), et ajoute à `plugin-b-api.md` § 41 ce qui change.

- [ ] **Step 1 : `HytaleGameClock`** (API § 41, « Horloge »).
  - Lis l'heure de jeu en secondes `s` : `getGameDateTime().toLocalTime().toSecondOfDay()`.
  - Les durées réelles `D` et `N` viennent de `world.getDaytimeDurationSeconds()` et `getNighttimeDurationSeconds()`.
  - `SUNRISE`, `DAYTIME` et `NIGHTTIME` sont les **valeurs d'exécution** de `WorldTimeResource` : lis les constantes publiques, ne les recopie pas.
  - `dayTime()` :
    - de jour, `(s − SUNRISE) · 12600 / DAYTIME` ;
    - de nuit, `12600 + floorMod(s − SUNRISE − DAYTIME, 86400) · 11400 / NIGHTTIME` ;
    - borné à [0, 24000[.
  - `realTicksUntil(t)` : convertis `t` en secondes de jeu par la fonction inverse, puis `20 · floorMod(x(cible) − x(s), D + N)`, avec `x` donné par l'API § 41. Si `world.getWorldConfig().isGameTimePaused()`, renvoie `Long.MAX_VALUE`.
  - Garde `isDaytime()` tel quel.
  - Javadoc : « Deviation » sur l'interpolation des commandes d'administration, pendant laquelle l'estimation est fausse.
- [ ] **Step 2 : place dans `HytaleCitizenBodies`.** Déplace `teleport` et `warnNoFreeSpot` (l. 345-395) dans `npc/BodyTeleport` : `void teleport(World, Ref<EntityStore>, Vec3)`, avec le même comportement et la même journalisation. `HytaleCitizenBodies.teleport` lui délègue en une ligne.
- [ ] **Step 3 : `npc/CitizenBeds`**, branché dans `HytaleCitizenBodies` par trois délégations d'une ligne (`sleepIn`, `isInBed`, `wakeUp`).
  - `sleepIn(ref, bed)` :
    - si le corps a déjà un `MountedComponent`, faux ;
    - arrête la navigation (`MoveTarget.active = false`, comme `haltAll`) ;
    - puis, dans `store.forEachChunk(query de CitizenTag, (chunk, cb) -> …)`, une seule fois, `BlockMountAPI.mountOnBlock(ref, cb, new Vector3i(bed…), centre du bloc)`. Garde le résultat dans une variable locale. `Mounted` renvoie vrai, `DidNotMount` renvoie faux : journalisé une fois en WARNING avec la raison, ensuite en FINE ;
    - si l'essai en jeu montre le PNJ debout sur le lit, ajoute `MovementStates.sleeping = true` et joue l'animation `Sleep` (créneau `Status`) ;
    - s'il glisse, pose `Frozen` ;
    - ces deux options restent derrière une constante `boolean`, avec le résultat de l'essai **[in-game]** noté dans `plugin-b-api.md`.
  - `isInBed(ref)` : `store.getComponent(ref, MountedComponent.getComponentType()) != null`, avec le type `Bed`.
  - `wakeUp(ref)` :
    - `store.tryRemoveComponent(ref, MountedComponent…)` ;
    - retire `PlayerSomnolence` (posé par `WakeUpOnDismountSystem`, API § 41), et `Frozen` s'il est utilisé ;
    - puis `BodyTeleport` vers le bloc libre à côté du lit, par la même recherche de point libre.
  - Tout se passe sur le fil du monde. Aucune exception ne sort (CLAUDE.md § 4).
  - **Poussée** (S § 5.8, MC `EntityCitizen:1512-1517`) : si l'essai en jeu montre qu'un joueur pousse le citoyen couché hors du lit, coupe la poussée le temps du sommeil (vérifie le composant de collision entre entités dans les sources) **[in-game]**.
  - **Lit occupé** (S § 6, MC `EventHandler:610-631`) : Hytale refuse déjà le lit au joueur (`NO_MOUNT_POINT_FOUND`). Si on peut intercepter l'usage du lit (vois comment `CitizenUseSystem` intercepte l'usage d'un PNJ), le plugin envoie `hycolony.bed.occupied` (« This bed is occupied » / « Ce lit est occupé ») quand un citoyen y dort. Sinon, le refus natif suffit, et on le note **[in-game]**.
- [ ] **Step 4 : particules.** `HytaleWorldEffects.sleeping(at)` : `ParticleUtil.spawnParticleEffect("Sleepy", …)`, comme les autres effets de la classe. Vérifie l'identifiant exact dans les assets (`Server/Particles/NPC/Emotions/Sleepy.particlesystem`), et mets-le dans l'id-map (`hycolony/id-map.json`) s'il y a une section pour les effets. Sinon, une constante avec sa source.
- [ ] **Step 5 : textes.**
  - `status.sleep` : « Sleeping zZZ » / « Dort zZZ » ;
  - `citizen.allAsleep` : « All citizens are tucked into bed. » / « Tous les citoyens sont au lit. » ;
  - la fenêtre du citoyen affiche `status.sleep` pour l'état `SLEEP` (vérifie que `TownHallViews.status` renvoie bien `"sleep"`).
- [ ] **Step 6 : build et commit.** `./gradlew build`, puis :

```bash
git add plugin/src/main/java/dev/hycolony/plugin/adapter/HytaleGameClock.java plugin/src/main/java/dev/hycolony/plugin/adapter/HytaleCitizenBodies.java plugin/src/main/java/dev/hycolony/plugin/adapter/HytaleWorldEffects.java plugin/src/main/java/dev/hycolony/plugin/npc/BodyTeleport.java plugin/src/main/java/dev/hycolony/plugin/npc/CitizenBeds.java plugin/src/main/resources/Server/Languages/en-US/hycolony.lang plugin/src/main/resources/Server/Languages/fr-FR/hycolony.lang docs/research/plugin-b-api.md
git commit -m "feat(plugin): citizens lie in Hytale beds through the native bed mount, on MC's day clock"
```

---

### Task 18 : documentation et relecture finale

- [ ] **Step 1 : `docs/TESTING.md`** : les étapes en jeu de S § 11, plus les trois essais **[in-game]** de la Task 17 (pose couchée, glissement et poussée, particule).
- [ ] **Step 2 : la spec.** Chaque écart introduit par le plan y figure :
  - les paquets `citizen/home` et `citizen/sleep` ;
  - `HutFootprint` dans `colony` ;
  - `isInBed` / `leftBed` ;
  - le plafond de la configuration sans recherche ;
  - la priorité du « ! » sur « zZz ».
- [ ] **Step 3 : relectures.** `./gradlew build` vert. Lance ensuite `hycolony-reviewer`, `mc-fidelity-checker` et `ui-lang-checker` sur toute la branche, puis fais relire leurs corrections à leur tour (CLAUDE.md § 9.3).
- [ ] **Step 4 : commit** `docs: SP4 test steps and deviations`. L'utilisateur relance le serveur et teste.
