# Hôtel de ville complet : plan d'implémentation

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** l'hôtel de ville montre ce que montre la fenêtre de MineColonies, rempli comme MC le remplit, pour tout ce que le cœur sait faire (lots 1 à 5 de la spec).

**Architecture:** `TownHall.ui` devient un cadre (livre, sceaux, rubans, `#Page`) ; chaque onglet a son `.ui` sous `Pages/HyColony/TownHall/` et sa classe de rendu ; `TownHallView` porte un record par onglet ; chaque bouton appelle une action du cœur qui vérifie la permission de MC puis réaffiche.

**Tech Stack:** Java 25, cœur pur (`core/`, JUnit 5, `Fake*`), plugin Hytale 0.7.0-pre.4 (`.ui`, `InteractiveCustomUIPage`).

**Spec:** `docs/superpowers/specs/2026-10-01-hycolony-town-hall-complete-design.md` ; inventaire : `docs/research/ui-vs-minecolonies.md` § 1 « Inventaire complet ».

## Global Constraints

- Porter une fenêtre MC = son XML + la classe Java qui la remplit + le message serveur de chaque bouton ; positions MC ×2 ; textures MC ×4 au plus proche voisin en `@2x` sous `Pages/HyColony/Mc/`.
- Un écart à MC seulement si Hytale ou un système absent l'impose, avec `Deviation from MC: …`.
- Permission d'un message de colonie MC : `MANAGE_HUTS` ; messages de permissions : `EDIT_PERMISSIONS`.
- Textes : clé dans en-US et fr-FR (`hycolony.lang`), mots anglais de `manual_en_us.json` ; message imbriqué sur `.TextSpans`.
- CLAUDE.md : 400 lignes max par fichier (300 visé), 40 par méthode, 15 fichiers par paquet, Javadoc courte, pas de séparateurs, `./gradlew build` vert avant commit, relecture indépendante de chaque lot (`hycolony-reviewer`, `ui-lang-checker`, `mc-fidelity-checker`), `git add` explicite et `git commit -- <chemins>`, rien d'indexé en attente.
- Persistance : tout nouvel état persistant passe par `add-migration` (fixture de l'ancienne version, lecture tolérante).
- Ne jamais lancer le serveur Hytale. Valider chaque `.ui` avec l'éditeur de l'utilisateur (script `check-ui.mts` du scratchpad).

## Review Focus

1. Un événement de fenêtre forgé ou périmé (index hors liste, rang ou citoyen disparu entre l'affichage et le clic) : ignoré, sans exception, la fenêtre se réaffiche.
2. Une liste déroulante qui renvoie une valeur inconnue (style retiré, rang supprimé) : ignorée.
3. Un joueur qui perd sa permission pendant que la fenêtre est ouverte : l'action est refusée par le cœur, pas par la fenêtre.
4. Une vieille sauvegarde (sans style de colonie, sans position d'événement, sans `moveIn`, sans rangs personnalisés ni refus) : se charge avec les défauts.
5. Le propriétaire qui tente de se retirer ou de retirer à son propre rang `EDIT_PERMISSIONS` : refusé, comme MC.

---

## Lot 0 : cadre de la fenêtre

### Task 0.1 : le cadre et les pages par onglet

**Files:**
- Modify: `plugin/src/main/resources/Common/UI/Custom/Pages/HyColony/TownHall.ui` (cadre seul : livre, 7 emplacements de sceaux aux positions exactes de MC, rubans, `#Page`)
- Create: `Pages/HyColony/TownHall/{Actions,Info,Citizens,Stats}.ui` (contenu actuel déplacé)
- Modify: `plugin/.../ui/townhall/TownHallPage.java` (l'onglet ouvert ajoute son `.ui` dans `#Page` puis délègue à son rendu)
- Copy: textures `bookmark_short_ribbon_03/06`, `bookmark_ribbon_03/06`, `red_wax_permissions/settings/information` (script de copie ×4 du scratchpad)

**Interfaces:**
- Produces: `TownHallPage.Tab` enum `{ACTIONS, INFO, PERMISSIONS, CITIZENS, STATS, SETTINGS}` (ordre et emplacements MC, Alliances absent), chacun avec son document `.ui` ; `#Seal<i>`, `#Mark<i>`, `#Ribbon<i>` indexés par l'emplacement MC (0..6, l'emplacement 5 des Alliances vide).

- [ ] Étape 1 : copier les textures, réécrire `TownHall.ui` en cadre, créer les quatre `.ui` d'onglet avec le contenu actuel ; valider avec `check-ui.mts`.
- [ ] Étape 2 : `TownHallPage` : n'ajouter que le document de l'onglet ouvert ; Permissions et Réglages restent cachés tant que leur lot n'est pas fait (pas de sceau).
- [ ] Étape 3 : `./gradlew :plugin:build`, relecture, commit `refactor(plugin): the town hall frame appends only the open tab's page`.

## Lot 1 : Accueil et fenêtre de construction

### Task 1.1 : niveau, style de colonie et renommage de MC (cœur)

**Files:**
- Modify: `core/.../colony/Colony.java` (+ `style()`/`setStyle`), `core/.../app/ColonyFoundation.java` (style de colonie = style de l'hôtel de ville), `core/.../app/action/HutActions.java` (nouvelle hutte : style de la colonie par défaut), `ColonySerializer` (+ migration), `core/.../app/action/ColonyAdministration.java` (`rename` à la MC, `setStyle`), `core/.../app/ui/TownHallView.java` (record `Actions`), `core/.../app/view/TownHallViews.java`
- Test: `core/src/test/.../app/action/TownHallRenameTest.java`, `.../app/ColonyStyleTest.java`, migration test + fixture

**Interfaces:**
- Produces: `TownHallView.Actions(int townHallLevel, String style, List<String> styles, boolean canManage, Optional<OrderButton> order)` où `OrderButton(WorkOrderType type)` dit le libellé « Annuler … » ; `ColonyAdministration.setStyle(UUID actor, int colonyId, String style)` → `boolean` ; `ColonyAdministration.rename` : >25 caractères → 24 premiers (MC `TownHallRenameMessage`), vide refusé.

Tests (TDD, d'abord rouges) :
- `renameTruncatesANameOverTwentyFiveCharactersToTwentyFour`
- `renameKeepsANameOfTwentyFiveCharacters`
- `renameRefusesABlankName`
- `renameNeedsManageHuts`
- `foundedColonyTakesItsTownHallStyle`
- `newHutDefaultsToTheColonyStyle`
- `setStyleNeedsManageHutsAndRefusesAnUnknownStyle`
- `oldSaveWithoutColonyStyleLoadsWithTheTownHallStyle` (migration)
- `townHallViewCarriesTheTownHallLevelAndRunningOrder`

- [ ] Étapes : test rouge → code → vert, par groupe ; `add-migration` pour le style de colonie ; commit `feat(core): the colony style and MC's rename rule for the town hall`.

### Task 1.2 : vue de la fenêtre de construction (cœur)

**Files:**
- Create: `core/.../app/ui/BuildOptionsView.java`, `core/.../app/view/BuildOptionsViews.java`
- Modify: `UiPort` (+ `showBuildOptions`), `ColonyWindows` (+ `openBuildOptions(UUID, BlockPos)`), `WorkOrderActions.order` (+ `Optional<BlockPos> builder`, réaffiche la fenêtre d'origine : hôtel de ville pour l'hôtel de ville, hutte sinon), `FakeUi` des tests
- Test: `core/src/test/.../app/view/BuildOptionsViewsTest.java`, `WorkOrderActionsTest` (constructeur choisi)

**Interfaces:**
- Produces: `BuildOptionsView(int colonyId, BlockPos pos, String typeId, int level, int maxLevel, boolean deconstructed, Set<WorkOrderType> allowed, List<String> styles, String style, List<BuilderChoice> builders, List<ItemAmount> resources, boolean canPickUp, Optional<String> upgradeWarning, boolean fromTownHall)` ; `BuilderChoice(BlockPos hut, String workerName)` triés par distance à la hutte ; `resources` = plan du niveau visé (suivant si la hutte peut monter, sinon actuel), vide si plan introuvable.

Tests :
- `buildersAreTheColonyBuilderHutsWithAWorkerSortedByDistance` (MC `updateBuilders`, sans le mineur)
- `resourcesListTheNextLevelBlueprintItems`
- `resourcesListTheCurrentLevelAtMaxLevel`
- `missingBlueprintGivesNoResources`
- `orderWithAChosenBuilderIsClaimedByIt`
- `orderFromTheTownHallReshowsTheTownHall`

- [ ] Étapes TDD ; commit `feat(core): the build options window's view, its builders and resources`.

### Task 1.3 : onglet Accueil, fenêtre de renommage et fenêtre de construction (plugin)

**Files:**
- Modify: `TownHall/Actions.ui`, `TownHallActionsTab.java`
- Create: `Pages/HyColony/RenameColony.ui` + `ui/townhall/RenameColonyPage.java` ; `Pages/HyColony/BuildOptions.ui` + `ui/BuildOptionsPage.java` (remplace `BuildOptionsPanel`, que la fenêtre de hutte n'embarque plus : son bouton ouvre la page) ; lignes `Mc/ResourceRow.ui`
- Modify: `HytaleUiPort` (+ `showBuildOptions`), `BuildingMainTab`, `Building.ui`
- Delete: `ui/BuildOptionsPanel.java`
- Copy: `builder_button_medium_large_build`, `builder_button_medium`, `builder_button_medium_disabled`, `button_x`
- Lang: `currtownhallname` « Colony Name: », `visual` « Cosmetic Options: », `townhall.build` « Build Options », `pickcolonystyle` « Colony Pack: », `picktexturestyle` « Citizen Style: », `picknamestyle` « Name Pack: », `rename.title`, « Done », « Cancel », « Builder: », cancel labels (existants)

- [ ] Étapes : `.ui` validés ; rendu ; `docs/TESTING.md` (renommage, construction depuis l'hôtel de ville et depuis une hutte, choix du constructeur, liste des ressources, annulation) ; build ; relectures ; commit `feat(plugin): the town hall's Home tab and MC's rename and build options windows`.

## Lot 2 : Informations

### Task 2.1 : position des événements et vue (cœur)

**Files:** `colony/EventLog.java` (`Entry` + `Optional<BlockPos> pos`), producteurs (`ColonyFoundation`, `CitizenManager`, `BuildCompletion`), `ColonySerializer` + migration, `TownHallView.Info(List<EventRow> events, WorkOrdersView orders)`, `TownHallViews`.

Tests : `eventsCarryTheirPosition`, `eventsListNewestFirst`, `oldEventWithoutPositionLoads`, `hyColonyOnlyEventTypesAreNotShown` ; vérifier les lecteurs de `buildingPlaced/Removed/debrisLost` et cesser de les produire s'il n'y en a aucun.

### Task 2.2 : onglet Informations (plugin)

`TownHall/Info.ui` : liste déroulante des intervalles (`$C.@DropdownBox`, style texturé), liste d'événements à gauche ; filtre `day >= today - interval` dans la page (état de la page, comme MC côté client). Commit par tâche.

## Lot 3 : Citoyens

### Task 3.1 : vue et rappel (cœur)

`TownHallView.Citizens(List<CitizenEntry>)` trié par nom, `CitizenEntry(int id, String name, String jobId, Gender gender, List<SkillLevel> skills, String status)` ; `CitizenActions.recall(UUID, int colonyId, int citizenId)` (`MANAGE_HUTS`, MC `RecallSingleCitizenMessage`).

Tests : `citizensAreSortedByName`, `recallBringsTheCitizenToTheTownHall`, `recallRespawnsAMissingBody`, `recallNeedsManageHuts`, `recallFailsWithMcMessageWithoutTownHall`.

### Task 3.2 : onglet Citoyens (plugin)

`TownHall/Citizens.ui` : recherche (32 caractères, filtre nom/métier sans casse), liste de boutons, sélection (désactivé + infobulle compétences), métier, sceau de genre, Rappeler. Textures `builder_button_medium_large_disabled`, `colonist_wax_male_smaller`, `_female_smaller`.

## Lot 4 : Réglages

### Task 4.1 : `moveIn` et action (cœur)

`ColonySettings.moveIn` (défaut vrai, persisté + migration) ; `CitizenManager.onColonyTick` n'ajoute de citoyen initial que si `moveIn` ; `ColonyAdministration.toggleSetting(UUID, int, ColonySetting)` (`MANAGE_HUTS`) ; `TownHallView.Settings(boolean moveIn, boolean autoHiring, boolean autoHousing, boolean canManage)`.

Tests : `noInitialCitizenArrivesWhenMoveInIsOff`, `toggleSettingNeedsManageHuts`, `toggleSettingFlipsAndPersists`, `oldSaveWithoutMoveInLoadsWithMoveInOn`.

### Task 4.2 : onglet Réglages (plugin)

`TownHall/Settings.ui` (`layoutsettings.xml` ×2), boutons `builder_button_very_small` marche/arrêt, sceau Réglages visible.

## Lot 5 : Permissions

### Task 5.1 : règles de MC (cœur)

`Permissions` : `canAlterPermission(Rank actorRank, Rank rank, Action action)`, `alterPermission(UUID actor, int rankId, Action, boolean on)`, exception Neutre (`hasPermission`), `addRank(String)`, `removeRank(int)`, `setRankType(int, RankType)`, `removePlayer(UUID actor, UUID target)` ; `PermissionActions` (nouvelle classe, `EDIT_PERMISSIONS`).

Tests : un par règle de `Permissions.java:304-323,641,1054-1090` et `PermissionsMessage.RemovePlayer:561-568`.

### Task 5.2 : joueurs par nom et journal des refus (cœur + port)

`PlayerDirectory.uuidByName(String)`, `name(UUID)` (vérifiés dans les sources décompilées par `hytale-api`) ; `FakePlayers` ; `PermissionEvents` (100 refus, persistés, migration) enregistrés par `ColonyProtection`.

### Task 5.3 : onglet Permissions (plugin)

`TownHall/Permissions.ui` + sous-pages Joueurs et Rangs (rendu découpé par sous-page), `turn_page_left/right`, listes déroulantes de rang et de type, interrupteurs d'action.

---

Chaque tâche se termine par : build vert, `docs/TESTING.md` mis à jour pour ce qui se teste en jeu, relecture indépendante (et `ui-lang-checker` si `.ui`/`.lang`, `mc-fidelity-checker` si règle MC), corrections relues, commit.
