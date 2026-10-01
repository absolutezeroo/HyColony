# Fenêtres de huttes comme MineColonies : plan d'implémentation

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** chaque fenêtre de hutte (résidence, constructeur, fermier, livreur, entrepôt) a l'apparence et le contenu de MineColonies, pour tout ce que le cœur sait faire (lots 0 à 6 de la spec).

**Architecture:** `Building.ui` devient un cadre (papier, bandeau, titre, onglets latéraux, `#Page`) ; chaque onglet a son `.ui` sous `Pages/HyColony/Hut/` ajouté dans `#Page` quand il est ouvert ; les fenêtres annexes (embauche, affectation, renommage, aide, inventaire total) sont des pages à part ; chaque bouton appelle une action du cœur qui vérifie `MANAGE_HUTS` puis réaffiche.

**Tech Stack:** Java 25, cœur pur (`core/`, JUnit 5, `Fake*`), plugin Hytale 0.7.0-pre.4 (`.ui`, `InteractiveCustomUIPage`).

**Spec:** `docs/superpowers/specs/2026-10-01-hycolony-hut-windows-mc-design.md` ; inventaire : `docs/research/ui-vs-minecolonies.md` § 8.

## Global Constraints

- Porter une fenêtre MC = son XML + la classe Java qui la remplit + le message serveur de chaque bouton ; positions MC ×2 ; textures MC ×4 au plus proche voisin en `@2x` sous `Pages/HyColony/Mc/` (script `copy_mc_tex.py` du scratchpad : copie de PNG, pas une modification de source).
- Un écart à MC seulement s'il est imposé, avec `Deviation from MC: …`, et listé à la spec § 10.
- Droit des messages de hutte MC : `MANAGE_HUTS` ; les boutons restent actifs pour tous, le cœur refuse et le dit (`ColonyRefusal.tellNoPermission`).
- Textes : clé dans en-US et fr-FR (`hycolony.lang`), mots anglais de `manual_en_us.json` ; message imbriqué sur `.TextSpans`.
- CLAUDE.md : 400 lignes max par fichier (300 visé), 40 par méthode, 15 fichiers par paquet (nouveau sous-paquet `app/hut` pour les vues et actions des fenêtres annexes), Javadoc courte, pas de séparateurs, `./gradlew build` vert avant commit, relecture indépendante de chaque lot (`hycolony-reviewer`, `ui-lang-checker`, `mc-fidelity-checker`) et de ses corrections, `git add` explicite et `git commit -- <chemins>`, rien d'indexé en attente, fichiers des autres sessions jamais touchés.
- Modifier les fichiers avec Edit/Write, jamais par script.
- Persistance : pas de nouvel état prévu ; sinon `add-migration`.
- Ne jamais lancer le serveur Hytale. Valider chaque `.ui` avec `check-ui.mts` (racine : `plugin/src/main/resources`).

## Review Focus

1. Un événement périmé (citoyen embauché ailleurs, réglage ou conteneur disparu entre l'affichage et le clic) : ignoré sans exception, la fenêtre se réaffiche.
2. Un joueur sans `MANAGE_HUTS` (ami) qui clique partout : chaque action refusée par le cœur avec le message de MC, rien ne change.
3. Une hutte au niveau 0 : Gérer dit `workerhuts.level0`, pas de fenêtre d'embauche.
4. Un renommage vide, de 15, 16 ou 25 caractères : nom du type, gardé, coupé à 15 avec le message.
5. Une hutte retirée pendant qu'une fenêtre annexe est ouverte : l'action renvoie faux, aucune fenêtre ne se rouvre sur une hutte absente.

---

## Lot 0 : cadre

### Task 0.1 : textures, styles et cadre papier

**Files:**
- Copy: inventaire § 8.7 (papier, bandeau, `red_wax_information`, `chest`, `modules/tab_left_side1..4`, icônes `main crafting inventory settings info stock stats field entity`, boutons `small`, `quite_small`, `large`, `mini_check`, `mini_disabled`, `mini_disabled_check`, `_disabled` utiles, `builder_paper_wide2`).
- Modify: `Pages/HyColony/Mc/Book.ui` (+ `@SmallButtonStyle`, `@QuiteSmallButtonStyle`, `@LargeButtonStyle`, `@VerySmallButtonStyle`, `@MediumButtonStyle` avec `Disabled`), `Mc/RankButtonRow.ui` (utilise `@MediumButtonStyle`).
- Rewrite: `Pages/HyColony/Building.ui` (papier 380 × 488, bandeau (48,24)/(60,24)/(320,24), `#Title` rouge (60,28) 256 × 22, `#EditName` (300,22), `#Build` (60,220), `#Info` (28,428), `#Inventory` (104,428), `#AllInventory` (318,428), `#Page`, `#Tabs`).
- Create: `Mc/SideTab.ui` (onglet 64 × 52, fond, icône 40 × 40 en (10,6), infobulle).
- Modify: `plugin/.../ui/BuildingPage.java` (onglets latéraux à la place de `TabBar` ; seul l'onglet ouvert est ajouté dans `#Page`), `HutTab` (`document()` relatif à `#Page`, + `icon()`, `tooltipKey()` à la place de `labelKey()`), chaque `ui/hut/*Tab` (icône et infobulle de MC, inventaire § 8.1 tableau des vues).
- Create: `plugin/.../ui/hut/SideTabs.java` (rendu des onglets : graine = position de la hutte, fond `tab_left_side` 1..4, son).

**Interfaces:**
- Produces: `HutTab.icon()` → nom de texture (`"settings"`), `HutTab.tooltipKey()` → clé complète ; `SideTabs.render(UICommandBuilder, UIEventBuilder, BlockPos seed, List<SideTabs.Tab>)` ; action `"tab"` avec `Index`.

- [ ] Étape 1 : copier les textures, écrire `Book.ui`, `SideTab.ui`, `Building.ui` ; `check-ui.mts` sans diagnostic.
- [ ] Étape 2 : `BuildingPage` + `SideTabs` ; chaque onglet existant garde son contenu actuel dans `#Page` (rendu MC aux lots suivants).
- [ ] Étape 3 : chercher un son de page de livre dans les assets Hytale (`docs/research/plugin-b-api.md`) ; sinon le son d'onglet actuel.
- [ ] Étape 4 : `./gradlew build`, commit `feat(plugin): hut windows on MC's paper with side tabs`.

### Task 0.2 : titre, inventaire et droits du cadre (cœur)

**Files:**
- Modify: `core/.../app/ui/BuildingView.java` (+ `String customName`, `MainKind mainKind`, `WorkerRow.jobId`), `core/.../app/view/BuildingViews.java`.
- Modify: `plugin/.../ui/HutStorage.java` → l'ouverture passe par une action cœur `HutActions.mayOpenInventory` (`MANAGE_HUTS`, refus dit).
- Test: `core/src/test/.../app/view/HutFrameViewTest.java`, `.../app/action/HutInventoryRightTest.java`.

**Interfaces:**
- Produces: `BuildingView.MainKind {WORKERS, SIMPLE, LIVING}` (WORKERS si un module de travailleur, LIVING si un `LivingModule`, sinon SIMPLE) ; `WorkerRow(int citizenId, String name, String jobId, HomeLine home, int homeDistance)` ; `HutActions.mayOpenInventory(UUID, BlockPos)` → `boolean`.

- [ ] Étape 1 : tests qui échouent (`titleIsTheCustomNameElseTheType`, `mainKindFollowsTheModules`, `workerRowsCarryTheirJob`, `aFriendMayNotOpenTheHutInventoryAsMc`).
- [ ] Étape 2 : implémentation, tests verts, plugin : titre « nom niveau » rouge.
- [ ] Étape 3 : `./gradlew build`, relectures du lot 0, commit `feat: the hut frame's title, inventory right and main page kind as MC`.

## Lot 1 : pages principales, renommage, aide

### Task 1.1 : rappel des travailleurs et renommage (cœur)

**Files:**
- Create: `core/.../app/hut/HutWindowActions.java` (rappel des travailleurs, renommage).
- Modify: `ColonyManager` (accès `hutWindows()`), `UiPort` + `FakeUi` si une fenêtre s'ouvre par le cœur (non : le renommage est ouvert par le plugin).
- Test: `core/src/test/.../app/hut/WorkerRecallTest.java`, `HutRenameTest.java`.

**Interfaces:**
- Produces: `HutWindowActions.recallWorkers(UUID, BlockPos)` → `boolean` (MC `RecallCitizenMessage` : chaque travailleur ramené par `CitizenRecall.bring`, échec dit `hut.recallFail`) ; `HutWindowActions.rename(UUID, BlockPos, String)` → `boolean` (au-delà de 15 : coupé, message `hycolony.gui.name.toolong` ; vide : nom du type ; `MANAGE_HUTS` ; réaffiche la hutte) ; `HutWindowActions.NAME_CUT_LENGTH = 15`.

- [ ] Étape 1 : tests qui échouent (`recallBringsEveryWorkerToTheHut`, `recallNeedsManageHuts`, `aNameOver15IsCutAndSaysSo`, `anEmptyNameShowsTheTypeAgain`, `renameNeedsManageHuts`).
- [ ] Étape 2 : implémentation, tests verts, commit `feat(core): recall a hut's workers and rename a hut as MC`.

### Task 1.2 : les trois pages principales, renommage et aide (plugin)

**Files:**
- Create: `Pages/HyColony/Hut/MainWorkers.ui`, `Hut/MainSimple.ui`, `Hut/MainLiving.ui`, `HutRename.ui`, `HutInfo.ui`, `Mc/WorkerLine.ui`.
- Rewrite: `plugin/.../ui/BuildingMainTab.java` (une classe par variante si elle dépasse 300 lignes : `MainWorkersTab`, `MainLivingTab`).
- Create: `plugin/.../ui/hut/HutRenamePage.java`, `HutInfoPage.java` (pages par type : constante `PAGES` = builder, farmer, deliveryman, warehouse → 4).
- Delete: `ui/hut/ResidentsTab.java`, `ResidentsTab.ui`, `ResidentRow.ui` (après le lot 2, quand l'affectation les remplace).
- Lang: `ui.building.*` de MC, `gui.name.toolong`, `info.<type>.<i>.name/.text` (en-US de `manual_en_us.json`, fr-FR traduit).

- [ ] Étape 1 : `.ui` aux positions ×2 de l'inventaire § 8.1 et § 8.4 ; `check-ui.mts`.
- [ ] Étape 2 : rendu et événements ; Gérer : niveau 0 → message `workerhuts.level0` (action cœur qui le dit), sinon ouvre l'embauche (lot 2 ; d'ici là, l'actuelle liste).
- [ ] Étape 3 : TESTING.md (points des pages principales, renommage, aide) ; `./gradlew build` ; relectures du lot 1 ; commit `feat(plugin): MC's hut main pages, rename and help windows`.

## Lot 2 : embauche et affectation

### Task 2.1 : vue et actions de l'embauche (cœur)

**Files:**
- Create: `core/.../app/hut/HireWorkerView.java`, `HireWorkerViews.java`, `AssignCitizenView.java`, `AssignCitizenViews.java`.
- Modify: `UiPort` (+ `showHireWorker`, `showAssignCitizen`), `FakeUi`, `HytaleUiPort` (stub jusqu'à 2.2), `HiringMode` (`nextForWorkplace()` sans LOCKED), `HutActions.hire/fire` (un employé d'ailleurs quitte d'abord son ancienne hutte, MC `WindowHireWorker:241-258`).
- Test: `HireWorkerViewTest`, `HireWorkerActionsTest`, `AssignCitizenViewTest`.

**Interfaces:**
- Produces: `HireWorkerView(BlockPos hut, String jobId, List<Candidate> candidates, HiringMode mode, boolean showEmployed, int free)` ; `Candidate(int citizenId, String name, Optional<String> jobId, boolean employedHere, HomeLine home, int homeDistance, List<SkillLevel> skills, Skill primary, Skill secondary, Button button)` avec `Button {HIRE, FIRE, NONE}` ; tri MC (`WindowHireWorker:266-284,349-377`) ; `AssignCitizenView` (résidents, candidats, mode, places) ; `HutWindowActions.openHire(UUID, BlockPos, boolean showEmployed)`, `openAssign(UUID, BlockPos)` (niveau 0 → message, `false`) ; `HutWindowActions.cycleHiring(UUID, BlockPos)` (sans LOCKED).

- [ ] Étape 1 : tests qui échouent (`candidatesAreSortedAsMc`, `employedElsewhereShowOnlyWhenAsked`, `hiringAnEmployedCitizenMovesItHere`, `aWorkplaceModeSkipsLocked`, `levelZeroSaysSoInsteadOfOpening`, `residenceCandidatesHomelessFirst`).
- [ ] Étape 2 : implémentation, tests verts, commit `feat(core): MC's hire worker and assign citizen windows, views and actions`.

### Task 2.2 : les deux fenêtres (plugin)

**Files:**
- Create: `HireWorker.ui`, `AssignCitizen.ui`, `Mc/CandidateRow.ui`, `Mc/ResidentLine.ui` ; `plugin/.../ui/hut/HireWorkerPage.java`, `AssignCitizenPage.java`.
- Modify: `HytaleUiPort` (`showHireWorker`, `showAssignCitizen`, rafraîchissement en direct comme les autres pages), pages principales (Gérer ouvre ces fenêtres ; listes en ligne retirées), suppression de `ResidentsTab`.

- [ ] Étape 1 : `.ui` (papier large 800 × 488, positions § 8.6 et § 8.4) ; `check-ui.mts`.
- [ ] Étape 2 : rendu, événements (identifiants de citoyen stables), X rouvre la hutte.
- [ ] Étape 3 : TESTING.md ; `./gradlew build` ; relectures du lot 2 ; commit `feat(plugin): MC's hire worker and assign citizen windows`.

## Lot 3 : constructeur

### Task 3.1 : réglages génériques (cœur)

**Files:**
- Create: `core/.../building/module/SettingsView.java` (`ModuleTab`), `SettingRow`, interface `HutSettings` (implémentée par `BuilderSettingsModule` et `FarmerSettingsModule` : `rows()`, `trigger(String id)`).
- Modify: `BuilderSettingsModule` (lignes `mode`, `recipemode` inactif, `buildmode` inactif, `fillblock`), `HutActions` (`triggerSetting(UUID, BlockPos, String)` ; BLOCK → renvoie l'ouverture du choix), suppression de `BuilderSettingsView`.
- Test: `BuilderSettingsRowsTest`, `TriggerSettingTest`.

**Interfaces:**
- Produces: `SettingRow(String id, Kind kind, String valueKey, Optional<BlockKey> block, boolean active, Optional<String> reasonKey)`, `Kind {BOOL, STRING, BLOCK}` ; `HutActions.triggerSetting` → `boolean`.

- [ ] Étape 1 : tests qui échouent (`builderRowsAreMcsInOrder`, `researchSettingsShowInactiveWithTheReason`, `triggeringModeCycles`, `anInactiveSettingIgnoresClicks`, `triggerNeedsManageHuts`).
- [ ] Étape 2 : implémentation, tests verts, commit `feat(core): MC's settings module rows for the builder`.

### Task 3.2 : Réglages, Ressources, Ordres de travail (plugin)

**Files:**
- Create: `Hut/Settings.ui`, `Mc/BoolSettingRow.ui`, `Mc/StringSettingRow.ui`, `Mc/BlockSettingRow.ui`, `ui/hut/SettingsTab.java` ; rewrite `Hut/BuilderResources.ui`, `Hut/BuilderOrders.ui` (renommés depuis `BuilderResourcesTab.ui`, `BuilderOrdersTab.ui`), `BuilderResourcesTab.java`, `BuilderOrdersTab.java` ; delete `BuilderSettingsTab.java`, `BuilderSettingsTab.ui`.

- [ ] Étape 1 : `.ui` aux positions § 8.5 ; contour vert de l'ordre courant ; NOT_NEEDED en noir.
- [ ] Étape 2 : TESTING.md ; `./gradlew build` ; relectures du lot 3 ; commit `feat(plugin): the builder's settings, resources and work orders as MC`.

## Lot 4 : fermier

### Task 4.1 : réglages et tâches du fermier (cœur)

**Files:**
- Modify: `FarmerSettingsModule` (implémente `HutSettings` : `fertilize`, `recipemode` inactif ; `ProvidesTab`), `FarmerHut` (ordre MC : recettes, champs, réglages, tâches), `FieldActions.toggleFertilize` (passe par `triggerSetting`), `CraftingTasks` (+ vue d'onglet `CrafterTasksView`, rangées de `TaskRow`).
- Test: `FarmerSettingsRowsTest`, `CrafterTasksViewTest`.

- [ ] Étape 1 : tests qui échouent (`farmerRowsAreFertilizeThenRecipeMode`, `fertilizeToggles`, `crafterTasksListTheQueueAsMc`).
- [ ] Étape 2 : implémentation, tests verts, commit `feat(core): the farmer's settings and tasks tabs as MC`.

### Task 4.2 : Recettes, Champs, Réglages, Tâches (plugin)

**Files:**
- Rewrite: `Hut/Recipes.ui`, `Hut/Fields.ui`, `RecipesTab.java`, `FieldsTab.java` ; `Mc/FieldRow.ui` (case mini cochée, icône d'étape avec infobulle) ; create `ui/hut/CrafterTasksTab.java` (réutilise le rendu des tâches du lot 5 si fait avant, sinon `TaskRows`).

- [ ] Étape 1 : `.ui` § 8.5 ; Fertiliser retiré de Champs.
- [ ] Étape 2 : TESTING.md ; `./gradlew build` ; relectures du lot 4 ; commit `feat(plugin): the farmer's recipes, fields, settings and tasks as MC`.

## Lot 5 : livreur et entrepôt

### Task 5.1 : tâches et coursiers (cœur)

**Files:**
- Modify: `logistics/warehouse/TaskRow` (+ icône de requête : objet demandé, positions du demandeur et du parent, priorité de coursier), `CourierAssignmentModule` (rattacher, détacher, mode), `HutWindowActions` ou `LogisticsActions` (`attachCourier`, `detachCourier`, `cycleCourierMode`, `recallCouriers`), `HireWorkerViews` (variante coursiers : candidats = coursiers rattachés à aucun autre entrepôt, maximum niveau × 2).
- Test: `TaskRowDetailTest`, `CourierAssignmentActionsTest`.

- [ ] Étape 1 : tests qui échouent (`taskRowsCarryTheRequesterChainAndPositions`, `aCourierAttachedElsewhereIsNoCandidate`, `attachingStopsAtLevelTimesTwo`, `detachingFreesTheCourier`, `courierActionsNeedManageHuts`).
- [ ] Étape 2 : implémentation, tests verts, commit `feat(core): MC's task rows and warehouse courier assignment`.

### Task 5.2 : Tâches et Coursiers (plugin)

**Files:**
- Create: `Hut/Tasks.ui`, `Mc/TaskLine.ui`, `Hut/Couriers.ui` ; rewrite `TaskRows.java`, `CourierTasksTab.java`, `WarehouseTasksTab.java`, `WarehouseCouriersTab.java` ; `HireWorkerPage` en mode coursiers.

- [ ] Étape 1 : `.ui` § 8.5 ; texte vert foncé pour la tâche en cours.
- [ ] Étape 2 : TESTING.md ; `./gradlew build` ; relectures du lot 5 ; commit `feat(plugin): MC's task lists and warehouse couriers`.

## Lot 6 : inventaire total

### Task 6.1 : vue de l'inventaire total (cœur)

**Files:**
- Create: `core/.../app/hut/HutInventoryView.java`, `HutInventoryViews.java`, `ItemCounts.java` (abréviation MC `Utils.format`) ; `UiPort.showHutInventory`, `FakeUi`.
- Modify: `BuildingView` (retire `stock`), `BuildingViews`.
- Test: `HutInventoryViewTest`, `ItemCountsTest`.

**Interfaces:**
- Produces: `HutInventoryView(BlockPos hut, List<Entry> items)`, `Entry(ItemKey item, int count, List<Holder> holders)`, `Holder(BlockPos pos, int count)` ; `ItemCounts.abbreviate(int)` → « 1.2k » comme MC ; `HutWindowActions.openInventory(UUID, BlockPos)`.

- [ ] Étape 1 : tests qui échouent (`itemsListEveryContainerHoldingThem`, `countsAbbreviateAsMc`, `openingNeedsManageHuts`).
- [ ] Étape 2 : implémentation, tests verts, commit `feat(core): MC's building inventory summary by container`.

### Task 6.2 : la fenêtre (plugin)

**Files:**
- Create: `HutInventory.ui`, `Mc/InventoryLine.ui`, `plugin/.../ui/hut/HutInventoryPage.java` (filtre, tri à 5 états gardé par joueur, Localiser avec `Highlights`, Retour) ; delete `HutStockPanel.java`, `StockRow.ui`.

- [ ] Étape 1 : `.ui` § 8.6 ; `check-ui.mts`.
- [ ] Étape 2 : TESTING.md ; `./gradlew build` ; relectures du lot 6 ; commit `feat(plugin): MC's building inventory summary window`.

## Fin

- [ ] Relecture finale de tout le sous-projet (agent le plus capable), une passe de corrections, relecture des corrections.
- [ ] Feu vert à l'utilisateur pour le test en jeu (`docs/TESTING.md`).
