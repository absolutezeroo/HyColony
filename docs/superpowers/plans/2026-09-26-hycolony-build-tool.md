# Baguette de construction (Build Tool) : plan d'implémentation

> **Pour les agents :** sous-skill requis : superpowers:subagent-driven-development (recommandé) ou superpowers:executing-plans. Les étapes se cochent (`- [ ]`).

**But :** poser une hutte comme avec le Build Tool de Structurize : choisir un style, une hutte et un niveau, voir le fantôme, le déplacer et le tourner, puis valider (`SurvivalHandler` de MineColonies).

**Architecture :** un nouveau paquet du cœur, `app/wand` :
- l'état par joueur (`WandSessions`) ;
- les déplacements (`WandMoves`) ;
- la validation (`WandPlacement`), qui porte `SurvivalHandler` ;
- les actions des boutons (`WandActions`), qui ré-affichent une vue `WandView` par `UiPort`.

Le fantôme passe par le `PreviewPort` des lunettes. Côté plugin : l'objet, avec `OpenCustomUI` sur les deux clics, la page `WandPage` et son `.ui`, et la lecture de la direction du joueur.

**Technique :** Java 21 (cœur), Hytale 0.6.8 (plugin), JUnit 5, Gradle.

**Spec :** `docs/superpowers/specs/2026-09-26-hycolony-build-tool-design.md`.

## Contraintes globales

- `CLAUDE.md` est lu en entier avant de commencer : tailles, Javadoc, ports sans exception, 15 fichiers par paquet, TDD, `./gradlew build` vert avant chaque commit.
- Chaque système porté cite sa source : `ST ItemBuildTool`, `ST AbstractBlueprintManipulationWindow`, `MC SurvivalHandler`.
- La rotation est un `int` de 0 à 3, en quarts de tour horaires, comme dans `Building`, `WorkOrder` et `BlueprintSource.load`.
- Tout texte joueur passe par une clé en-US **et** fr-FR (skill `add-lang-key`).
- Toute API Hytale est vérifiée dans `build/vineflower/hytale-server` ; les découvertes vont dans `docs/research/plugin-b-api.md`.
- Le serveur n'est jamais lancé. `git add` se fait avec des chemins explicites.

## Points à surveiller en relecture

1. **Style perdu :** une hutte posée à la baguette dans le style « Kweebec » doit être construite en Kweebec quand le joueur clique « Construire ». Or `WorkOrderValidation.resolveStyle` prend le style envoyé par le bouton avant `b.style()`. Test obligatoire (tâche 5).
2. **Hôtel de ville à la baguette hors colonie :** il doit passer par la fondation (`foundation().begin`), comme la pose à la main, et non par `huts().place`. Test obligatoire (tâche 3).
3. **Clic dans le vide sans ancre :** message `hycolony.wand.missingPos`, pas d'exception, pas de fenêtre ouverte (tâche 4).
4. **Joueur déconnecté pendant une session :** la session et le fantôme sont effacés (tâche 4 pour le cœur, tâche 6 pour le branchement).
5. **Bloc de hutte retiré de l'inventaire entre l'ouverture et la validation :** la validation refuse, et aucun bloc n'est posé (tâche 3).

---

### Tâche 1 : direction du joueur (port)

**Fichiers :**
- Modifier : `core/src/main/java/dev/hycolony/core/kernel/port/PlayerDirectory.java`
- Modifier : `core/src/test/java/dev/hycolony/core/testing/FakePlayers.java`
- Modifier : `plugin/src/main/java/dev/hycolony/plugin/adapter/HytalePlayerDirectory.java`

**Interfaces :**
- Produit : `int facing(UUID player)` dans `PlayerDirectory`. La valeur va de 0 à 3 : 0 = nord (−Z), 1 = est (+X), 2 = sud (+Z), 3 = ouest (−X). Le joueur hors ligne ou inconnu donne 0, sans exception.

- [ ] Ajouter la méthode au port, avec sa Javadoc (repère des directions, valeur par défaut).
- [ ] `FakePlayers` : ajouter `setFacing(UUID, int)`, avec 0 par défaut.
- [ ] `HytalePlayerDirectory` : lire le lacet par `TransformComponent.getRotation()` (ou `HeadRotation`, à vérifier dans vineflower laquelle suit la caméra), puis l'arrondir au quart de tour le plus proche.
  - Vérifier le sens du lacet de Hytale (radians ou degrés, et quelle valeur correspond au nord) et le noter dans `plugin-b-api.md`.
  - Journaliser le premier échec en WARNING, les suivants en FINE.
- [ ] `./gradlew build`, puis commit `feat: player facing through PlayerDirectory`.

### Tâche 2 : session et déplacements (cœur)

**Fichiers :**
- Créer : `core/src/main/java/dev/hycolony/core/app/wand/WandSession.java`, `WandSessions.java`, `WandMoves.java`
- Tests : `core/src/test/java/dev/hycolony/core/app/wand/WandMovesTest.java`, `WandSessionsTest.java`

**Interfaces :**
- `record WandSession(Optional<BlockPos> anchor, String style, String buildingTypeId, int level, int rotation)`.
  - `static WandSession empty()` : pas d'ancre, chaînes vides, niveau 1, rotation 0.
  - Des méthodes `withAnchor`, `withStyle`, `withBuilding`, `withLevel` et `withRotation` renvoient une copie.
  - `boolean hasBuilding()` : vrai quand une hutte est choisie.
- `final class WandSessions` :
  - `WandSession get(UUID)` : renvoie `empty()` si le joueur n'a pas de session ;
  - `void put(UUID, WandSession)` ;
  - `void clear(UUID)`.
- `final class WandMoves` (fonctions statiques pures) :
  - `enum Dir { FORWARD, BACK, LEFT, RIGHT, UP, DOWN }` ;
  - `static BlockPos move(BlockPos anchor, Dir dir, int facing)` ;
  - `static int rotate(int rotation, boolean clockwise)`.

- [ ] **Tests d'abord :**
  - `forwardFollowsThePlayerFacing` : un test paramétré sur les 4 directions. Pour la direction 1 (est), avancer donne x+1.
  - `leftIsCounterClockwiseOfFacing` : pour la direction 0 (nord, −Z), gauche donne x−1. C'est la règle de Structurize : gauche = direction tournée dans le sens antihoraire.
  - `upAndDownMoveOnYOnly`.
  - `rotateWrapsBothWays` : 3 en sens horaire donne 0, 0 en sens antihoraire donne 3.
  - `unknownPlayerGetsAnEmptySession`, puis `clearForgetsTheSession`.
- [ ] Lancer `./gradlew :core:test --tests "*Wand*"` : les tests échouent, les classes n'existent pas encore.
- [ ] Écrire le code minimal. Chaque classe porte une Javadoc qui cite `ST AbstractBlueprintManipulationWindow` (l.525-590).
- [ ] Tests verts, `./gradlew build`, puis commit `feat(core): build tool session and moves`.

### Tâche 3 : validation (port de `SurvivalHandler`)

**Fichiers :**
- Créer : `core/src/main/java/dev/hycolony/core/app/wand/WandPlacement.java`
- Test : `core/src/test/java/dev/hycolony/core/app/wand/WandPlacementTest.java`
- À lire d'abord :
  - `app/action/HutActions.java` (`checkPlacement`, `place`) ;
  - le démarrage d'une fondation, `foundation().begin`, et son appel dans `plugin/.../block/HutBlockSystems.java` ;
  - `WorkOrderValidation` : la vérification « emprise dans la colonie » (`isWorkOrderWithinColony`), à réutiliser ou à extraire, sans la dupliquer.

**Interfaces :**
- `sealed interface Result permits Refused, Placed, FoundColony`, avec :
  - `record Refused(Msg reason)` ;
  - `record Placed(Building building)` ;
  - `record FoundColony()`.
- `Result confirm(UUID player, WandSession s)` : fait les étapes 1 à 7 de la spec, dans l'ordre.
- La hutte demandée se retrouve par `BuildingRegistry` (`byId`). Son objet passe par `PlayerInventory.count` et `take`, avec l'`ItemKey` de son bloc de hutte.
  - Vérifier comment `ItemKey` est formé pour une hutte : clé logique `hutBlockKey` ou id d'asset, voir `HytalePlayerInventory` et `IdMap`.
  - Le bloc est posé par `WorldBlocks.place(pos, new BlockState(key, rotation), false)`, puis la hutte est enregistrée par `HutActions.place` et reçoit son style par `building.setStyle(style)`.

- [ ] **Tests d'abord**, un par règle :
  - `refusesWithoutManageHuts` ;
  - `refusesASecondTownHall` ;
  - `refusesWhenTheFootprintLeavesTheColony` ;
  - `refusesWhenTheHutBlockIsNoLongerInTheInventory` (aucun bloc posé) ;
  - `survivalPlacementTakesOneHutBlock` ;
  - `creativePlacementTakesNothing` ;
  - `placedHutIsLevelZeroWithTheChosenStyleAndRotation` ;
  - `placementCreatesNoWorkOrder` ;
  - `townHallOutsideAnyColonyStartsTheFoundation` : le résultat est `FoundColony`, le bloc est posé et la fondation est commencée ;
  - `refusesWhenNoBuildingIsChosen`.
- [ ] Les voir échouer, puis écrire le code. Aucune méthode de plus de 40 lignes : découper en étapes privées.
- [ ] `./gradlew build`, puis commit `feat(core): build tool placement checks (MC SurvivalHandler)`.

### Tâche 4 : actions, vue et aperçu

**Fichiers :**
- Créer : `core/src/main/java/dev/hycolony/core/app/wand/WandActions.java` (public, le point d'entrée).
- Créer la vue `WandView` (record), au même endroit que les autres vues de `UiPort` : `app/ui`. Si `ArchitectureTest` refuse la dépendance, la mettre là où il l'accepte et le noter.
- Modifier : `app/ui/UiPort.java`, avec `void showWand(UUID player, WandView view);`, et `FakeUi`.
- Test : `core/src/test/java/dev/hycolony/core/app/wand/WandActionsTest.java`

**Interfaces :**
- `record WandView(List<String> styles, List<String> buildingTypeIds, int maxLevel, String style, String buildingTypeId, int level, int rotation, boolean manipulate)`.
- `WandActions(ColonyManager manager, PreviewPort previews, WandSessions sessions)`, avec les méthodes publiques suivantes. Chacune renvoie `boolean` (faux si l'entrée est invalide) et ré-affiche la vue.
  - `open(UUID player, Optional<BlockPos> clicked)` :
    - avec une position, l'ancre devient celle-ci ;
    - sans position et sans ancre, `notifier.send(missingPos)` et la méthode renvoie faux.
  - `selectStyle(UUID, String)`, `selectBuilding(UUID, String)`, `selectLevel(UUID, int)`.
  - `move(UUID, WandMoves.Dir)` utilise `players().facing(player)`. `rotate(UUID, boolean clockwise)`.
  - `confirm(UUID)` : appelle `WandPlacement.confirm`.
    - Sur `Refused` : le message est envoyé, la session et le fantôme sont gardés.
    - Sinon : le fantôme est effacé, la session vidée, et `ui().close(player)` est appelé.
  - `cancel(UUID)` : efface le fantôme et la session.
  - `disconnect(UUID)` : même chose que `cancel`, mais sans message.
- Huttes proposées : toutes en créatif (`players().isCreative`) ; en survie, celles dont `playerInventory().count(bloc de hutte) > 0`.
- Aperçu : dès qu'une hutte est choisie, `previews.show(player, "wand", anchor, blocs)`. Les blocs viennent de `blueprints().load(style, type, level, rotation)`, sans l'air, comme dans `GogglesView.remaining` mais **sans** filtrer le monde (plan complet, comme MineColonies). Si le plan est introuvable, `hide`.

- [ ] **Tests d'abord :**
  - `openOnABlockAnchorsThereAndShowsTheWindow` ;
  - `openInTheAirWithoutAnchorSaysMissingPosAndOpensNothing` ;
  - `openInTheAirKeepsThePreviousAnchor` ;
  - `survivalListsOnlyHutsInTheInventory` ;
  - `creativeListsEveryHut` ;
  - `choosingAHutShowsTheFullPlanPreview` ;
  - `moveUsesThePlayerFacing` ;
  - `rotateReloadsThePlanRotated` ;
  - `manipulationHiddenUntilAHutIsChosen` ;
  - `cancelHidesThePreviewAndForgetsTheSession` ;
  - `disconnectForgetsEverything` ;
  - `refusedConfirmKeepsSessionAndPreview` ;
  - `successfulConfirmClosesTheWindowAndHidesThePreview`.
- [ ] Les voir échouer, puis écrire le code. `WandActions` doit rester sous 300 lignes : l'aperçu peut sortir dans un `WandPreview` package-private.
- [ ] `./gradlew build`, puis commit `feat(core): build tool actions, view and preview`.

### Tâche 5 : le style choisi est gardé à la construction

**Fichiers :**
- À lire : `app/action/WorkOrderActions.order(UUID, BlockPos, WorkOrderType, String style)` et l'endroit où la fenêtre de la hutte fournit `style`.
- Modifier : l'appelant ou `WorkOrderValidation.resolveStyle`, au plus petit endroit correct.
- Test : dans le test existant de `WorkOrderActions` ou de `WorkOrderValidation`.

- [ ] **Test d'abord :** `buildOfAHutPlacedWithAStyleUsesThatStyleByDefault`. Une hutte avec `style = "kweebec"`, niveau 0 : l'ordre BUILD envoyé depuis la fenêtre sans choix explicite doit être en `kweebec`.
- [ ] Vérifier ce que la fenêtre envoie. Si elle envoie le premier style par défaut, elle doit envoyer `b.style()` quand il n'est pas vide. MineColonies construit dans le style du bâtiment (`AbstractBuilding.getStructurePack`).
- [ ] `./gradlew build`, puis commit `fix(core): a hut placed with a style is built in that style`.

### Tâche 6 : plugin (objet, page, branchement)

**Fichiers :**
- Créer : `plugin/src/main/resources/Server/Item/Items/HyColony/HyColony_Build_Tool.json`. S'inspirer de `HyColony_Build_Goggles.json` pour la recette en ligne, à l'établi `Workbench`, avec `Rock_Stone_Cobble` ×1, `Rock_Basalt_Cobble` ×1, `Rock_Slate_Cobble` ×1 et `Ingredient_Stick` ×6. `MaxStack` vaut 1, et `Primary` et `Secondary` déclenchent `OpenCustomUI` avec la page `HyColony_Build_Tool`. S'inspirer de `Tool_Repair_Kit_Crude.json` dans le zip des assets. Modèle : le sceptre ou le bâton vanilla le plus proche, à vérifier dans le zip.
- Modifier : `HyColony_Build_Goggles.json`. La recette prend 1 `HyColony_Build_Tool` en plus, comme prévu par la spec des lunettes.
- Modifier : `plugin/src/main/resources/hycolony/id-map.json` (`build_tool`) et `IdMap.validate`, si la validation liste les clés.
- Créer : `plugin/src/main/java/dev/hycolony/plugin/ui/WandPage.java` et `plugin/src/main/resources/Common/UI/Custom/Pages/HyColony/WandPage.ui`.
  - Modèle de page : `FoundColonyPage`, avec son `Data` décodé par `BuilderCodec` et une clé `Action` plus des paramètres.
  - Les boutons sont copiés des motifs vanilla :
    - une liste de styles ;
    - une liste de huttes, avec icônes par `ItemIcon` comme `ResourceRow.ui` ;
    - les niveaux ;
    - les boutons `↑ ↓ ← →`, Haut, Bas, `↻`, `↺`, Valider et Annuler, dans un groupe `#Manipulator` masqué quand `manipulate` est faux.
  - `onDismiss` ne fait **rien** : Échap garde le fantôme.
- Modifier : `HytaleUiPort` (`showWand`).
- Modifier : `HyColonyPlugin.setup`.
  - Enregistrer la page avec `OpenCustomUIInteraction.registerCustomPageSupplier` et la clé `HyColony_Build_Tool`. `context.getTargetBlock()` peut être nul : dans ce cas l'ancre est `Optional.empty()`, sinon c'est le bloc visé + 1 en Y. Vérifier dans vineflower si la face visée est disponible ; sinon, prendre +1 en Y et le noter.
  - Le fournisseur appelle `WandActions.open`. La page ne s'ouvre que si `open` renvoie vrai.
  - Au `PlayerDisconnectEvent`, appeler `wand().disconnect(uuid)`, à côté de `goggles().unequip`.
- Modifier : `WorldRuntime`, qui construit `WandActions(manager, previews, new WandSessions())` et l'expose par `wand()`.
- Traductions : les clés `hycolony.wand.*` en en-US et fr-FR, avec le skill `add-lang-key`.
  - Le titre, les boutons et les listes.
  - `missingPos` : « Visez un bloc avec la baguette pour placer un plan. »
  - Les refus `noPerm`, `outsideColony` et `noHutBlock`, repris des messages MineColonies.
- Documentation :
  - `docs/TESTING.md` : les points 1 à 7 de la spec ;
  - SP1+2 spec § 11 : les trois écarts de la spec ;
  - `docs/BACKLOG.md` : décorations, miroir, aperçus partagés, `NearBuildPreview` ;
  - `plugin-b-api.md` : `OpenCustomUI` et le lacet.

- [ ] Écrire les assets et le code.
- [ ] `./gradlew build` vert : compilation du plugin, `checkFileSizes`, PMD, `spotlessCheck`.
- [ ] Commits séparés, dans cet ordre :
  1. `feat(plugin): build tool item and recipe` ;
  2. `feat(plugin): build tool window` ;
  3. `docs: build tool in-game checks and deviations`.

### Tâche 7 : relecture

- [ ] Une relecture `hycolony-reviewer` sur tout l'intervalle de commits, et un `mc-fidelity-checker` sur `app/wand` face à `ST AbstractBlueprintManipulationWindow`, `ST WindowExtendedBuildTool` et `MC SurvivalHandler`.
- [ ] Corriger seulement les vraies erreurs. Les cas rares vont dans `docs/BACKLOG.md`. Les corrections sont relues une fois.
- [ ] Feu vert à l'utilisateur pour les tests en jeu.
