# Audit global HyColony : 02, axe J, UI, commandes et validation côté serveur

```
ÉTAT : phase 2, axe J écrit. Code audité : commit 3e2e70ca. Sources : rapport « UI, commandes, permissions »
(chaque bouton tracé jusqu'au cœur), relecteurs plugin, cœur. Chemins relatifs à la racine du dépôt.
```

**Note de l'axe : 4/5.** Toutes les pages sont des `ColonyPage` gardées (`handleDataEvent`), chaque bouton appelle une action du cœur qui vérifie la colonie et la permission avant de muter (tableau complet dans `01-architecture.md` § 6), les vues sont des records sans objet du modèle, les fenêtres hutte/hôtel de ville/citoyen sont rafraîchies sans action, aucune classe de paquet personnalisé. Deux chemins muter le cœur sans passer par une action.

## 1. Constats

### J-1 — MOYEN — L'inventaire du citoyen est écrit par le plugin, et le cœur n'est prévenu qu'après, sans joueur ni permission
`plugin/src/main/java/dev/hycolony/plugin/ui/citizen/CitizenItemContainer.java:80-83, 90-93` ; `core/src/main/java/dev/hycolony/core/app/action/CitizenInventoryActions.java:36-41, 57` ; `plugin/.../ui/citizen/CitizenInventoryWindow.java:33`
```java
Inventory inv = citizen.inventory();
if (slot < inv.size()) {
    inv.set(slot, Optional.of(stacks.toAmount(itemStack)));
```
Mécanisme : `MANAGE_HUTS` est vérifié à l'ouverture (`open:41`) ; ensuite le conteneur Hytale (`InventoryMoves.apply`) écrit directement `citizen.inventory()` puis `report` → `onPlayerEdit` (`:57`, sans argument joueur, sans permission : `markDirty`, `overrule`). `CitizenInventoryWindow.validate` (:33) ne vérifie que « citoyen vivant ». MC vérifie aussi à l'ouverture (`OpenInventoryMessage`), puis les clics passent par le conteneur vanilla (portée du joueur) : le contrôle à l'ouverture est fidèle.
Impact : un rang retiré pendant que la fenêtre est ouverte ne ferme rien (comme MC) ; la mutation vit dans le plugin (§ 1), donc sans test du cœur, et `onPlayerEdit` ne sait pas qui a édité (journal, futurs droits).
Règle : § 1 (le plugin adapte, le cœur décide), § 7 (chaque bouton → action du cœur). Remède : `CitizenInventoryActions.set(UUID player, int citizen, int slot, Optional<ItemAmount>)` qui revérifie `MANAGE_HUTS` et fait la mutation, le conteneur ne faisant que traduire ; test `aDemotedPlayerCanNoLongerEditAnOpenCitizenInventory`.
Sévérité MOYEN · effort M · confiance HAUTE · DÉJÀ CONNU non.

### J-2 — BAS — `FieldActions.open` enregistre le champ sans contrôle ; `placed` ne renvoie rien d'exploité
`core/src/main/java/dev/hycolony/core/app/action/FieldActions.java:32-35, 42, 51-58` ; `plugin/src/main/java/dev/hycolony/plugin/block/FieldBlockSystems.java:39, 66-80, 86, 133, 164`
```java
public boolean open(UUID player, BlockPos pos) {
    if (!placed(player, pos)) { return false; }
    show(manager.colonyAt(pos).orElseThrow(), pos, player);
```
Mécanisme : `placed` (« MC addBuildingExtensionIfMissing ») ignore le joueur, `open` enregistre le champ si absent puis ouvre la fenêtre sans rang ; la permission vient seulement de l'ordre des systèmes (`AFTER ProtectionSystems.*`, `AFTER BlockUseProtectionSystem`) et disparaît avec `EnableColonyProtection = false` ; le plugin ignore le retour de `placed` (`:75`) et annule toujours l'usage (`:164`) ; un `FieldActions` neuf est construit par événement (`:32-35`). MC fait de même pour un épouvantail (permission par l'événement de bloc, `ScarecrowTileEntity` enregistré à l'usage) : fidèle.
Impact : avec la protection coupée, tout joueur ouvre et fait enregistrer un champ ; `setSeed`/`cycleRadius` restent gardés (`MANAGE_HUTS` :133).
Règle : § 7 (chaque action de page validée), § 1. Remède : `open` vérifie `ACCESS_HUTS` comme `openBuilding` ; le plugin lit le retour de `placed`.
Sévérité BAS · effort S · confiance HAUTE · DÉJÀ CONNU non.

### J-3 — BAS — Rafraîchissement en direct : une fenêtre par joueur, trois types seulement, onglet Actions figé
`core/src/main/java/dev/hycolony/core/app/view/OpenWindows.java:36` ; `plugin/src/main/java/dev/hycolony/plugin/adapter/LiveWindows.java:70-81` ; `plugin/.../ui/townhall/TownHallPage.java:79` ; `plugin/.../ui/ColonyPage.java:101-105`
```java
open.put(player, new Watch<>(shown.key(), shown.view(), view, redraw));
```
Mécanisme : `OpenWindows` suit la dernière fenêtre montrée par joueur ; `LiveWindows.shows` ne connaît que Hut, TownHall, Citizen : Requests, Field, Wand, FoundColony, ItemPicker ne se rafraîchissent qu'à une action ; l'onglet Actions de l'hôtel de ville (`showsInput()` vrai) n'est pas redessiné pour ne pas effacer la saisie, donc jour et propriétaire y restent figés. MC rafraîchit toute fenêtre ouverte à chaque synchronisation de vue (`onUpdate`), presse-papiers compris (commit 13b341a9 a choisi trois types).
Impact : une requête servie par un livreur reste affichée dans le presse-papiers jusqu'au clic suivant.
Règle : § 6 (MC `ColonyPackageManager` rafraîchit tout), § 7. Remède : `WindowKey` pour Requests et Field dans `LiveWindows.shows` ; redessiner l'onglet Actions hors du champ de saisie.
Sévérité BAS · effort S/M · confiance HAUTE · DÉJÀ CONNU non.

### J-4 — BAS — Deux droits différents pour le même acte de la baguette, et un dépôt de fondation dans le plugin
`core/src/main/java/dev/hycolony/core/app/wand/WandPlacement.java:72` (`MANAGE_HUTS`) ; `core/.../app/wand/WandPaste.java:50` → `HutActions.java:40` (`PLACE_HUTS`) ; `plugin/.../adapter/HytaleUiPort.java:91-92`
Mécanisme : `confirm` (survie) exige `MANAGE_HUTS` (commit 158c6f3a, MC `SurvivalHandler`), `paste` (créatif) passe par `checkPlacement` → `PLACE_HUTS` ; `HytaleUiPort` décide de retirer le bloc de l'hôtel de ville quand la fondation est abandonnée (DÉJÀ CONNU, BACKLOG).
Règle : § 6 (cohérence avec MC `CreativeStructureHandler`), § 1. Remède : aligner `paste` sur `MANAGE_HUTS` ou documenter ; remonter le retrait dans le cœur (`confirm` → `Created | Invalid(pos) | Pending`, audit B § 2.4).
Sévérité BAS · effort S · confiance MOYENNE · DÉJÀ CONNU partiel.

## 2. Non retenus

- `/hycolony info` sans rang (`HyColonyCommand.java:96-110`) : MC `colony info` idem, gardé par `canPlayerUseShowColonyInfoCommand`.
- `/hycolony selftest` parle aux ports directement (pose et casse un coffre, fait apparaître un corps) : opérateurs seulement, outil de test voulu (`docs/TESTING.md` point 8).
- `WandActions.open/select/move/rotate` sans permission : session en mémoire seulement, la fenêtre de MC s'ouvre pour tout le monde ; `confirm` et `paste` vérifient ; `USE_SCAN_TOOL` jamais vérifié : outil de scan PLANIFIÉ.
- `PasteQueue` casse et pose sans contrôle par bloc : collage créatif gardé par `isCreativeOperator` (« no footprint check » documenté), comme le `CreativeStructureHandler` de Structurize.
- `ColonyFoundation.cancelAt` sans joueur (tout casseur du bloc annule la fondation en attente) : le bloc n'est dans aucune colonie, donc libre pour tous, comme un bloc non revendiqué chez MC.
- `HutActions.place`, `onRemoved`, `ColonyManager.deleteColony`, `RequestActions.onContainerChanged`, `FieldActions.broken` publics sans contrôle : points d'entrée internes dont tous les appelants vérifient avant (`HutBlockSystems.java:111`, `WandPlacement.java:139`, `ColonyAdministration.java:46`, `HutStorage.java:95`).
- Deux mécanismes de permission (brut vs `ColonyProtection`) : c'est la répartition de MC ; le contournement manquant est M-2.
- `GogglesView.java:41` montre les chantiers de la colonie la plus proche sans rang : les lunettes MC montrent les chantiers à tout porteur.
- Rang `rename` : `isColonyManager()` là où MC exige `MANAGE_HUTS` : indiscernable tant que les rangs personnalisés n'existent pas.
- Aucune classe de paquet personnalisé ; toutes les vues sont des records (`01-architecture.md` § 6).
- `RequestsPage`/`CitizenRequestsTab` choisissent la fenêtre à ré-afficher après « Fournir » : DÉJÀ CONNU (BACKLOG).
- `CutterPage` sans rang : la protection vient de `BlockUseProtectionSystem` ordonné avant `CutterSystem` (vérifié) ; le contournement créatif est lu dans le plugin (`CutterCrafting.java:68`, A-4).

## 3. Ce qui est bien fait

- `core/src/main/java/dev/hycolony/core/app/action/ManagedHut.java:13-16` : la résolution « colonie → `MANAGE_HUTS` → bâtiment à la position » en un seul point, utilisée par 20 actions ; `WorkManager.request:56` et `RequestActions:46,113` font de même pour `MANAGE_HUTS`/`ACCESS_HUTS` : c'est la « base qui résout la colonie et vérifie la permission avant d'exécuter » de MC.
- `core/src/main/java/dev/hycolony/core/app/ui/*.java` : 10 records de vue, listes copiées par `List.copyOf`, aucun objet du modèle ; `OpenWindows.java:83` compare par `equals` avant tout envoi (le drapeau dirty de MC, gratuit).
- `plugin/src/main/java/dev/hycolony/plugin/ui/ColonyPage.java:118-125` + `blockui/.../PageEvents.java` : chaque événement de page est gardé pour que rien n'atteigne le `PageManager` de Hytale, qui n'attrape pas.

## 4. Tableau

| Sév. | `chemin:ligne` | Titre |
|---|---|---|
| MOYEN | `plugin/.../ui/citizen/CitizenItemContainer.java:80-93` ; `core/.../app/action/CitizenInventoryActions.java:57` | J-1 inventaire du citoyen écrit par le plugin |
| BAS | `core/.../app/action/FieldActions.java:51-58` | J-2 `open` enregistre le champ sans contrôle |
| BAS | `core/.../app/view/OpenWindows.java:36` ; `plugin/.../adapter/LiveWindows.java:70-81` | J-3 rafraîchissement partiel |
| BAS | `core/.../app/wand/WandPlacement.java:72` ; `WandPaste.java:50` | J-4 deux droits pour la baguette, retrait dans le plugin |
