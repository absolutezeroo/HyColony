# Inventaire du citoyen comme MC : plan d'implémentation

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** l'inventaire du citoyen de MC (27 cases, 4 d'armure, mains pointant vers une case) dans le cœur, montré dans notre propre fenêtre à la disposition de MC, avec l'aperçu du citoyen par la caméra du serveur et son armure visible sur le PNJ.

**Architecture:** le cœur garde la vérité (`CitizenData.equipment()`), les règles de MC (`GuardGear`, `ArmorLevels`, `ArmorWear`, retour au retrait du métier) et l'affichage demandé au port `CitizenBodies`. Le plugin montre une page personnalisée à la HyDomum (`CutterPage`) avec les grilles de HyBlockUI, deux conteneurs adossés au cœur, et pilote la caméra.

**Tech Stack:** Java 25, Gradle, JUnit 5, Hytale 0.7.0 (serveur décompilé dans `build/vineflower/hytale-server`), HyBlockUI.

**Spec:** `docs/superpowers/specs/2026-10-02-hycolony-citizen-inventory-mc-design.md`

## Global Constraints

- CLAUDE.md en entier : cœurs sans `com.hypixel`, 400 lignes par fichier, 15 fichiers par paquet, 40 lignes par méthode, 5 paramètres, Javadoc courte, `Deviation from MC:`, TDD, `./gradlew build` vert avant chaque commit, spotless fichier par fichier.
- Travail dans le worktree `C:\Users\Ctuto\Desktop\HyColony-inv` (branche `citizen-inventory-mc`) ; `sources/` et `build/vineflower/` se lisent dans le dépôt principal par leur chemin absolu.
- Ordre des cases d'armure : celui de Hytale (`ItemArmorSlot` : tête 0, torse 1, mains 2, jambes 3).
- `NO_SLOT = -1` pour une main vide (MC `NO_SLOT`).
- Le paquet `citizen` est plein (15 fichiers) : tout le nouveau code de l'inventaire va dans `citizen/inventory/`.
- `kernel/item` : 12 fichiers ; un seul nouveau fichier (`ArmorInfo`, avec son enum `Slot` imbriqué).
- Textes : clés en-US et fr-FR (`checkLangParity`).

## Review Focus

1. Une sauvegarde de schéma 9, ou une sauvegarde abîmée (indice de main à 99, armure de 6 cases) : elle se charge, l'indice devient −1, l'armure garde ses 4 premières cases.
2. Une pièce d'armure posée par un joueur dans la mauvaise case, ou trop forte pour la hutte : refusée, rien ne se perd (elle reste dans la main du joueur ou son inventaire).
3. Le citoyen meurt, est déchargé ou change de monde pendant que sa fenêtre est ouverte : la caméra revient au joueur, la fenêtre se ferme ou montre un cadre vide, sans exception.
4. Le joueur se déconnecte avec la fenêtre ouverte : rien ne reste accroché (abonnements, caméra).
5. Un citoyen sans métier reçoit de l'armure : refusée (MC : sans bâtiment, liste vide).

---

### Task 1 : le port d'armure (`ArmorInfo`, `ItemCatalog.armor`)

**Files:**
- Create: `core/src/main/java/dev/hycolony/core/kernel/item/ArmorInfo.java`
- Modify: `core/src/main/java/dev/hycolony/core/kernel/port/ItemCatalog.java`
- Modify: `core/src/test/java/dev/hycolony/core/testing/FakeCatalog.java`
- Modify: `plugin/src/main/java/dev/hycolony/plugin/adapter/HytaleItemCatalog.java` (et `plugin/item/HytaleItemInfo.java` si la lecture y va)
- Test: `core/src/test/java/dev/hycolony/core/kernel/item/ArmorInfoTest.java`

**Interfaces:**
- Produces: `record ArmorInfo(ArmorInfo.Slot slot, int itemLevel)`, `enum ArmorInfo.Slot { HEAD, CHEST, HANDS, LEGS }` (ordre de `ItemArmorSlot`), `ArmorInfo.Slot.index()` ; `Optional<ArmorInfo> ItemCatalog.armor(ItemKey item)` ; `FakeCatalog.armors` (`Map<ItemKey, ArmorInfo>`).

- [ ] **Step 1 : test** — `ArmorInfoTest.slotsFollowHytalesArmorSlotOrder` : `HEAD.index()==0`, `CHEST==1`, `HANDS==2`, `LEGS==3` ; `aNegativeItemLevelIsRefused` (`IllegalArgumentException`).
- [ ] **Step 2 : RED** — `./gradlew :core:test --tests '*ArmorInfoTest'` : échec de compilation.
- [ ] **Step 3 : code**

```java
/** What the catalog knows of an armour piece: the slot it goes in (Hytale ItemArmorSlot order) and its ItemLevel. */
public record ArmorInfo(Slot slot, int itemLevel) {
    public ArmorInfo {
        if (itemLevel < 0) {
            throw new IllegalArgumentException("itemLevel must be >= 0: " + itemLevel);
        }
    }

    /** Hytale's armour slots, in ItemArmorSlot order. Deviation from MC: hands instead of feet. */
    public enum Slot {
        HEAD, CHEST, HANDS, LEGS;

        /** Its slot in a citizen's armour. */
        public int index() {
            return ordinal();
        }
    }
}
```

`ItemCatalog` : `/** The armour piece {@code item} is; empty for anything else. */ Optional<ArmorInfo> armor(ItemKey item);` — `FakeCatalog` : `public final Map<ItemKey, ArmorInfo> armors = new LinkedHashMap<>();` et `armor(k) = Optional.ofNullable(armors.get(k))`.
`HytaleItemCatalog.armor` : `Item.getAssetMap().getAsset(id)`, `item.getArmor()` non nul → `new ArmorInfo(Slot.values()[armor.getArmorSlot().ordinal()], item.getItemLevel())` ; sinon vide ; mis en cache comme `tool`. Vérifié : `Item.getItemLevel()` l. 1041, `Item.getArmor()` l. 1065, `ItemArmor.getArmorSlot()` l. 249.
- [ ] **Step 4 : GREEN** — test vert, `./gradlew :plugin:compileJava` vert.
- [ ] **Step 5 : commit** `feat(core): the catalog tells an armour piece's slot and level (ItemCatalog.armor)`.

### Task 2 : l'équipement du citoyen et sa sauvegarde (schéma 10)

**Files:**
- Create: `core/src/main/java/dev/hycolony/core/citizen/inventory/CitizenEquipment.java`
- Create: `core/src/main/java/dev/hycolony/core/kernel/persist/MigrationV9ToV10.java`
- Create: `core/src/test/resources/fixtures/colony-v9-equipment.json` (copie d'une sauvegarde de schéma 9 avec un citoyen)
- Modify: `CitizenData.java` (champ `equipment`, accesseur), `CitizenSerializer.java`, `ColonySerializer.SCHEMA_VERSION = 10`, `MigrationChain.sp4()` (10 étapes, Javadoc)
- Test: `core/src/test/java/dev/hycolony/core/citizen/inventory/CitizenEquipmentTest.java`, `core/src/test/java/dev/hycolony/core/app/persistence/MigrationV9ToV10Test.java`

**Interfaces:**
- Produces: `CitizenEquipment` : `static final int NO_SLOT = -1`, `static final int ARMOR_SLOTS = 4`, `Inventory armor()`, `int held(Hand hand)`, `void hold(Hand hand, int slot)` (`IllegalArgumentException` hors `[-1, 26]`), `void clearHands()`, `enum Hand { MAIN, OFF }` ; `CitizenData.equipment()`.
- JSON du citoyen : `"armor"` (comme `"inventory"`, `Inventory.write`), `"heldMain"`, `"heldOff"`.

- [ ] **Step 1 : tests**
  - `CitizenEquipmentTest.handsStartEmptyAndHoldASlot` : `held(MAIN)==NO_SLOT` ; `hold(MAIN, 3)` → 3 ; `clearHands()` → les deux à `NO_SLOT`.
  - `aSlotOutsideTheInventoryIsRefused` : `hold(MAIN, 27)` et `hold(OFF, -2)` lèvent.
  - `armorHasFourSlots` : `armor().size()==4`.
  - `MigrationV9ToV10Test.v9ToV10GivesEachCitizenEmptyArmorAndHands` : après migration, chaque citoyen a `armor: []`, `heldMain: -1`, `heldOff: -1`, `schemaVersion: 10`.
  - `equipmentSurvivesASave` : un casque en case 0 et `heldMain` 2 survivent à `saveAll` / rechargement.
  - `aBrokenSaveHeals` : `heldMain: 99`, `armor` de 6 piles → `NO_SLOT`, 4 cases gardées (`Inventory.read(array, 4)` ignore le reste).
- [ ] **Step 2 : RED** — `./gradlew :core:test --tests '*CitizenEquipmentTest' --tests '*MigrationV9ToV10Test'`.
- [ ] **Step 3 : code** — `CitizenEquipment` (MC `InventoryCitizen` armorInventory, mainItem, offhandItem) ; `CitizenSerializer.write` : `o.add("armor", d.equipment().armor().write()); o.addProperty("heldMain", …); o.addProperty("heldOff", …)` ; `read` : `Inventory.read(arrayOr(o.get("armor")), ARMOR_SLOTS)` puis `hold` avec `intOr(…, NO_SLOT)` borné (hors bornes → `NO_SLOT`, sans lever) ; `MigrationV9ToV10.apply` ajoute les trois clés à chaque `citizens[]` ; `MigrationChain.sp4()` passe à 10.
- [ ] **Step 4 : GREEN** — puis `./gradlew :core:test`.
- [ ] **Step 5 : commit** `feat(core): a citizen's armour and held slots, saved (schema 10), as MC InventoryCitizen`.

### Task 3 : les règles d'armure de MC (`ArmorLevels`, `GuardGear`)

**Files:**
- Create: `core/src/main/java/dev/hycolony/core/citizen/inventory/ArmorLevels.java`, `GuardGear.java`
- Test: `core/src/test/java/dev/hycolony/core/citizen/inventory/ArmorLevelsTest.java`, `GuardGearTest.java`

**Interfaces:**
- Consumes: `ArmorInfo` (Task 1).
- Produces: `ArmorLevels.of(int itemLevel)` → 0..5 ; `GuardGear.allows(int buildingLevel, ArmorInfo piece, ArmorInfo.Slot slot)` (false sans bâtiment : `buildingLevel <= 0`).

- [ ] **Step 1 : tests**
  - `ArmorLevelsTest.eachReferenceIsItsLevelsTop` : 10→0, 15→0, 16→1, 20→1, 25→2, 30→3, 35→4, 40→4, 41→5, 75→5.
  - `GuardGearTest` : niveau 1 accepte 0..1, refuse 2 ; niveau 2 : 0..2 ; niveau 3 : 0..3 ; niveau 4 : 2..4 (refuse 0 et 1, MC « maille à diamant ») ; niveau 5 : 3..5 (refuse 2) ; niveau 0 : rien ; une pièce de torse dans la case des mains : refusée.
- [ ] **Step 2 : RED.**
- [ ] **Step 3 : code**

```java
/** MC ItemStackUtils.getArmorLevel: a piece's level from its ItemLevel against the reference pieces (spec § 3). */
public final class ArmorLevels {
    /** Deviation from MC: Hytale has no armour value; the reference pieces' ItemLevels (leather, iron, bronze,
     *  thorium, adamantite) stand for MC's leather, gold, chain, iron and diamond. */
    private static final int[] REFERENCE_ITEM_LEVELS = {15, 20, 25, 30, 40};
    /** MC: above every reference piece. */
    public static final int ABOVE_ALL = 5;

    private ArmorLevels() {}

    public static int of(int itemLevel) {
        for (int level = 0; level < REFERENCE_ITEM_LEVELS.length; level++) {
            if (itemLevel <= REFERENCE_ITEM_LEVELS[level]) {
                return level;
            }
        }
        return ABOVE_ALL;
    }
}
```

`GuardGear` : `MIN_BY_BUILDING_LEVEL = {0, 0, 0, 2, 3}`, `MAX_BY_BUILDING_LEVEL = {1, 2, 3, 4, Integer.MAX_VALUE}` (MC `ContainerCitizenInventory` l. 153-162, `EquipmentLevelConstants`) ; `allows` : `piece.slot() == slot` et le niveau dans `[min, max]` du niveau de bâtiment (borné à 5).
- [ ] **Step 4 : GREEN.**
- [ ] **Step 5 : commit** `feat(core): MC's armour levels and GuardGear, on Hytale's ItemLevel`.

### Task 4 : afficher l'équipement sur le corps (port, mains, armure)

**Files:**
- Modify: `core/.../kernel/port/CitizenBodies.java` (`setArmor`), `kernel/nav/DetouringBodies.java`, `core/src/test/.../testing/FakeBodies.java`, `plugin/.../adapter/HytaleCitizenBodies.java`, `plugin/.../npc/GuardedBodies.java`
- Create: `core/.../citizen/inventory/HeldItems.java`
- Modify: `job/work/WorkerHands.java`, `citizen/food/EatingTable.java`, `citizen/sleep/SleepHandler.java`, `citizen/CitizenAI.java`, `logistics/courier/CourierContext.java`, `citizen/CitizenManager.java` (`bind`)
- Test: `core/src/test/java/dev/hycolony/core/citizen/inventory/HeldItemsTest.java`

**Interfaces:**
- Produces: `CitizenBodies.setArmor(BodyId body, List<Optional<ItemAmount>> pieces)` (4 éléments, ordre `ArmorInfo.Slot`) ; `HeldItems.holdSlot(CitizenData d, CitizenBodies b, BodyId body, int slot)`, `holdItem(CitizenData d, CitizenBodies b, BodyId body, Optional<ItemKey> item)`, `clear(…)`, `show(CitizenData d, CitizenBodies b, BodyId body)` (main d'après sa case, armure).
- `WorkerHands` reçoit la `CitizenData` (constructeur) et passe par `HeldItems`.

- [ ] **Step 1 : tests** (`HeldItemsTest`, avec `FakeBodies`)
  - `holdingASlotPointsTheHandAtItAndShowsItsItem` (MC `setHeldItem(hand, slot)`).
  - `holdingAnItemOfTheInventoryPointsAtItsFirstSlot`.
  - `anItemNotInTheInventoryIsShownWithoutASlot` (`Deviation from MC:` le bâtisseur montre le bloc déjà consommé).
  - `clearEmptiesTheHandAndItsSlot`.
  - `aNewBodyShowsTheHeldSlotAndTheArmour` (`show` après `bind`).
- [ ] **Step 2 : RED.**
- [ ] **Step 3 : code** — `HeldItems` ; `WorkerHands.hold(item)` → `HeldItems.holdItem`, `holdTool` → `holdSlot` ; `EatingTable.bite` → `holdSlot(foodSlot)` ; les `setHeldItem(body, Optional.empty())` → `HeldItems.clear` ; `CourierContext` → `holdSlot(0)` ; `CitizenManager.bind` → `HeldItems.show`. `HytaleCitizenBodies.setArmor` : dans `world.execute` (comme ses autres écritures), `InventoryComponent.Armor` du corps, chaque case remplacée par `HytaleStacks.toStack` ou vidée. Vérifier dans les sources : `InventoryComponent.Armor.getInventory()` l. 131, `ItemContainer.setItemStackForSlot`.
- [ ] **Step 4 : GREEN** — `./gradlew :core:test :plugin:compileJava`.
- [ ] **Step 5 : commit** `feat(core): held slots and armour shown on the body, as MC setHeldItem and its armour render`.

### Task 5 : la vie de l'équipement (retrait du métier, blessure)

**Files:**
- Create: `core/.../citizen/inventory/EquipmentReturn.java`, `ArmorWear.java`
- Modify: `job/WorkerModule.java` (`fire`), `app/persistence/ColonySerializer.java` (l. 215, retrait au chargement), `plugin/.../npc/CitizenHurtSystem.java`
- Test: `EquipmentReturnTest.java`, `ArmorWearTest.java`

**Interfaces:**
- Produces: `EquipmentReturn.onJobRemoved(Colony c, CitizenData d)` (MC `AbstractJob.onRemoval` l. 449-454 : mains vidées, armure dans l'inventaire si elle y tient, puis affichage) ; `ArmorWear.onHurt(Colony c, CitizenData d, double damagePercent)` (`damagePercent` = dégâts / santé max × 100 ; dégâts MC = `damagePercent × 20 / 100` ; chaque pièce perd `max(1, (int) (dégâts MC / 4))`, cassée → retirée, puis affichage).

- [ ] **Step 1 : tests**
  - `firedWorkerPutsItsArmourBackInItsInventoryAndEmptiesItsHands`.
  - `armourThatDoesNotFitStaysWorn` (inventaire plein : la pièce reste, comme MC).
  - `eachPieceLosesAQuarterOfTheDamageAtLeastOne` : 8 points MC → 2 ; 1 point → 1.
  - `aPieceWornOutIsRemovedFromTheBody`.
  - `noArmourNoChange`.
- [ ] **Step 2 : RED.**
- [ ] **Step 3 : code** — `WorkerModule.fire` et le chemin de `ColonySerializer` appellent `EquipmentReturn.onJobRemoved` après `job.onRemoval` ; `CitizenHurtSystem` appelle `ArmorWear.onHurt` avec `event.getAmount() * 100 / santé max` (lire la santé max de la même façon que `HytaleCitizenBodies.healthPercent`).
- [ ] **Step 4 : GREEN.**
- [ ] **Step 5 : commit** `feat(core): armour back to the inventory when the job goes, and worn when hurt, as MC`.

### Task 6 : les actions de la fenêtre (armure posée, clôture de requête)

**Files:**
- Modify: `core/.../app/action/CitizenInventoryActions.java` ; Create: `core/.../citizen/inventory/ArmorRules.java` (si l'action dépasse 300 lignes ou 25 méthodes)
- Test: `core/src/test/java/dev/hycolony/core/app/action/CitizenArmorActionsTest.java`

**Interfaces:**
- Produces: `CitizenInventoryActions.mayWear(int colonyId, int citizenId, int armorSlot, ItemAmount piece)` (`GuardGear` avec le niveau du bâtiment de travail, armure du catalogue) ; `onArmorEdit(int colonyId, int citizenId, Inventory before)` (chaque pièce posée : clôture de requête comme `onPlayerEdit`, puis affichage sur le corps) ; `view(int colonyId, int citizenId)` → `Optional<CitizenInventoryView>`.
- `record CitizenInventoryView(String name, List<Optional<ItemAmount>> slots, List<Optional<ItemAmount>> armor)` dans `app/ui`.

- [ ] **Step 1 : tests**
  - `aWorkerAtALevelOneHutMayWearLeatherNotBronze`.
  - `aCitizenWithoutWorkplaceMayWearNothing`.
  - `aPieceInTheWrongSlotIsRefused`.
  - `aPiecePutOnClosesTheRequestForItAsMc` (MC `createArmorSlot.set` l. 236-244).
  - `aPiecePutOnShowsOnTheBody`.
- [ ] **Step 2 : RED** ; **Step 3 : code** ; **Step 4 : GREEN.**
- [ ] **Step 5 : commit** `feat(core): the citizen window's armour rules, as MC ContainerCitizenInventory`.

### Task 7 : notre fenêtre d'inventaire

**Files:**
- Create: `plugin/.../ui/citizen/CitizenInventoryPage.java` (page, comme `domum/plugin/.../cutter/CutterPage.java`), `CitizenArmorContainer.java` (4 cases adossées à `equipment().armor()`, `ItemContainerUtil.trySetArmorFilters` + `mayWear`), `plugin/src/main/resources/Common/UI/Custom/Pages/HyColony/Mc/CitizenInventory.ui`, textures `Pages/HyColony/Mc/CitizenContainer*.png` (découpes de `citizen_container.png` ×4 au plus proche voisin, comme les autres textures MC)
- Modify: `CitizenItemContainer.java` (l'inventaire lu par une fonction, pour servir aux 27 cases et à l'armure ; le rapport appelle `onPlayerEdit` ou `onArmorEdit`), `CitizenInventoryWindows.java` (ouvre la page avec `openCustomPageWithWindows` au lieu de `Page.Bench`), `CitizenInventoryWindow.java`, langues (`hycolony.ui.citizenInventory.inventory`)
- Supprimer ce que `Page.Bench` seul utilisait.

**Interfaces:**
- Consumes: `CitizenInventoryActions.mayWear`, `onArmorEdit`, `onPlayerEdit`, `view` ; HyBlockUI `InventoryGrids.drawContainer`, `PlayerPanels`/`PlayerSection`, `InventoryMoves.apply`, `InventoryWatch`, `PageRedraw`.
- Disposition ×2 de MC (spec § 2) : 490 × 336 ; nom en (160, 18) ; 27 cases à partir de (16, 46), pas 36 ; cadre 98 × 144 en (344, 44) **transparent** ; armure en (444, 44 + 36 i) ; « Inventaire » en (16, 158) ; sac en (16, 180 + 36 i) ; barre rapide en (16, 296).

- [ ] **Step 1** : la page s'ouvre (clic « Inventaire » de la fenêtre du citoyen), titre, grilles remplies ; glisser entre les 27 cases, l'armure et l'inventaire du joueur (`InventoryMoves.apply` vers l'id de la bonne fenêtre).
- [ ] **Step 2** : `./gradlew build` vert ; `ui-lang-checker` et `hycolony-reviewer` sur la tâche.
- [ ] **Step 3 : commit** `feat(plugin): the citizen's inventory in our own MC window (armour, grids of HyBlockUI)`.

### Task 8 : l'aperçu par la caméra

**Files:**
- Create: `plugin/.../ui/citizen/CitizenPreviewCamera.java`
- Modify: `CitizenInventoryPage.java` (début à l'ouverture, fin à `onDismiss`), `CitizenInventoryWindows.java` (déconnexion, corps disparu : `CitizenBodyLifecycleSystem` ou l'événement de déchargement existant)

**Interfaces:**
- Produces: `CitizenPreviewCamera.start(PlayerRef player, Ref<EntityStore> body)` (réglages de `SpectatorSystems.applyFollowCamera` l. 254-288 : `attachedToType = EntityId`, `NetworkId`, `followAttachedEntity`, `isFirstPerson = false`, `DistanceOffset`, rotation = lacet du corps + π, `distance` et décalages en constantes) ; `stop(PlayerRef player)` (`SetServerCamera(Custom, false, null)`, comme `PlayerCameraResetCommand`) ; un corps absent : rien.

- [ ] **Step 1** : la caméra se pose à l'ouverture et revient à la fermeture, à la déconnexion, et quand le corps disparaît.
- [ ] **Step 2** : constantes de cadrage réglées en jeu par l'utilisateur ; les noter dans la recherche § 9.
- [ ] **Step 3 : commit** `feat(plugin): the citizen seen in its inventory window through the server camera`.

### Task 9 : documentation et essais en jeu

**Files:**
- Modify: `docs/TESTING.md` (fenêtre, armure refusée, aperçu, armure sur le PNJ, outil en main, sauvegarde), `docs/research/citizen-inventory-window.md` (§ 9 : cadrage retenu), `docs/research/plugin-b-api.md` (API utilisées)
- [ ] **Step 1** : écrire les essais.
- [ ] **Step 2 : commit** `docs: TESTING for the citizen inventory as MC`.

### Fin

- Relecture de toute la branche (`hycolony-reviewer`, `mc-fidelity-checker`, `ui-lang-checker`), toutes les remarques corrigées (mémoire « No deferred minors »), puis fusion dans `sp0-foundations` et feu vert à l'utilisateur.
