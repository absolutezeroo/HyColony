# Audit global HyColony : 02, axe G, idiomes Java 25

```
ÉTAT : phase 2, axe G écrit. Code audité : commit 3e2e70ca. Sources : phase 0 (cmd 14, 15, avertissements de
compilation), relecteurs cœur et mods frères. Chemins relatifs à la racine du dépôt.
```

**Note de l'axe : 4/5.** 212 records, 13 `sealed` avec `switch` exhaustifs là où ils rendent service (`Requestable`, `ModuleTab`, `WindowKey`, `HutPlacement`), variables `_`, aucun preview, aucun `Serializable`, aucun `Unsafe`/`finalize`/`SecurityManager`, aucun `ThreadLocal`, dépendances épinglées (pas de `+`), class-files 69 (ArchUnit 1.5.1 lit Java 25), NullAway en mode JSpecify en erreur. Restent des `Optional` mal placés, des `null` renvoyés hors des ports et 16 avertissements Error Prone que le build laisse passer.

## 1. Constats

### G-1 — BAS — `Optional` en champ et en paramètre
`core/src/main/java/dev/hycolony/core/farming/field/FarmField.java:19-20, 37, 50` ; `farming/hut/FieldWalk.java:51` ; `farming/job/FarmWork.java:41` ; `app/wand/WandSession.java:10` ; `job/work/WorkerHands.java:28` ; `app/action/CitizenInventoryActions.java:82` ; `app/wand/WandPlacement.java:90` ; `plugin/.../adapter/HytaleWorldEffects.java:43` ; `plugin/.../block/HytaleBlockStates.java:26-28` ; `plugin/.../ui/highlight/GlowingBlock.java:46` ; `vanilla/core/.../FlowerPot.java:38` ; `FlowerPotBlocks.java:40`
```java
private Optional<BlockPos> owner = Optional.empty();
public void setOwner(Optional<BlockPos> hut) { owner = Objects.requireNonNull(hut, "hut"); }
```
Mécanisme : le § 2 demande `Optional` pour une absence **renvoyée** ; en champ et en paramètre il sert de `null` déguisé (les appelants écrivent `setOwner(Optional.empty())` six fois) ; `FieldWalk.prevPos` et `FieldChoice.current` font déjà l'inverse (`@Nullable` en champ, `Optional` en retour).
Règle : § 2 (esprit), `<java_25_idiomes>` (`Optional` en retour seulement). Remède : `@Nullable` en champ, `assignTo(BlockPos)`/`release()`, `setSeed(ItemKey)`/`clearSeed()`, `hold(ItemKey)`/`holdNothing()`.
Sévérité BAS · effort S · confiance HAUTE · DÉJÀ CONNU non.

### G-2 — BAS — `null` renvoyé hors des ports
`core/src/main/java/dev/hycolony/core/citizen/CitizenData.java:70-99` (`lastPosition`, `respawnPosition`, `homeBuilding`, `workBuilding`) ; `citizen/Skill.java:46-53` (`complementary`, `adverse`)
```java
/** Null for Intelligence. */
public @Nullable Skill complementary() { return COMPLEMENTARY.get(this); }
```
Mécanisme : les appelants ré-emballent (`Optional.ofNullable(d.workBuilding())` dans `CitizenInventoryActions:68`, `CitizenViews:42` ; tests `== null` dans `BuildingViews:104`, `CitizenAI:203`, `ColonySerializer:231-238`, `JobSkillShares:24-26`). Les `return null` des suppliers d'état (`IStateSupplier`, `AITarget.every`, `CitizenAI.idle`, `BuilderBlockWork.work`…) sont le contrat MC `TickingTransition`, documenté (`kernel/ai/IStateSupplier.java:6`, « or null to not transition ») ; `SavedJson.readVec`, `ForcedInsert.insert`, `Inventory.insert` sont des « restes » documentés : non retenus.
Règle : § 2 (« jamais un `null` renvoyé, sauf les “reste” des ports »). Remède : `Optional<BlockPos> workBuilding()` etc. (setters inchangés), `Optional<Skill> complementary()/adverse()`. `CitizenData` est en cours de modification par une autre session : à faire après.
Sévérité BAS · effort M (une quinzaine d'appelants) · confiance HAUTE · DÉJÀ CONNU non.

### G-3 — BAS — Seize avertissements Error Prone que le build ne fait pas échouer
`plugin/.../ui/field/FieldPage.java` (×2), `ui/BuildOptionsPanel.java` (×2), `ui/wand/WandPage.java`, `block/HutBlockSystems.java:101`, `core/.../job/HiringMode.java`, `farming/field/FieldStage.java` (`[EnumOrdinal]`) ; `ui/citizen/CitizenInventoryWindows.java`, `block/HytaleBlockBreaker.java`, `adapter/HytaleWorldBlocks.java`, `HytalePlayerInventory.java`, `HytaleItemCatalog.java:223` (`[ReferenceEquality]`) ; `ui/highlight/HighlightMarkers.java:24` (`[removal]` `Player.getPlayerRef()`) ; `ui/citizen/CitizenInventoryWindow.java` (`[ShortCircuitBoolean]`) ; `prefab/PrefabStyles.java` (`[ArrayRecordComponent]`)
```java
int rotation = event.getRotation().yaw().ordinal();
```
Mécanisme : `compileJava --rerun` affiche 16 avertissements ; `hy.java-checks.gradle.kts` ne met en erreur que NullAway. `HutBlockSystems.java:101` **persiste** l'ordinal d'un enum Hytale (`protocol/Rotation.java:5-9` : `None(0), Ninety(1), OneEighty(2), TwoSeventy(3)`, ordinal = valeur aujourd'hui, documenté dans `HytaleBlueprintSource.java:30-33`) ; les autres `ordinal()` sont des index d'événement intra-session bornés ; `getPlayerRef()` est `@Deprecated(forRemoval = true)` (`Player.java:1152`), seul accès que le fournisseur de marqueurs reçoit ; les `ReferenceEquality` sont des identités voulues (`ref.getStore() != store`) déjà supprimées pour PMD mais pas pour Error Prone.
Impact : une réorganisation de l'enum par Hytale tournerait toutes les huttes sauvegardées ; un nouvel avertissement passe inaperçu dans le bruit.
Règle : § 5 (lecture tolérante d'une sauvegarde), § 3 (Error Prone tourne dans le build : un avertissement ignoré est du bruit). Remède : `HutBlockSystems` : la valeur déclarée de l'enum (un `switch` de 4 cas) ; `@SuppressWarnings("ReferenceEquality")` sur les identités voulues (comme `CitizenPage.java:190` le fait déjà) ; puis `-Werror` sur Error Prone (garde-fou, **accord de l'utilisateur requis**).
Sévérité BAS · effort S · confiance HAUTE · DÉJÀ CONNU non.

### G-4 — BAS — Nombres magiques et littéraux dupliqués
`vanilla/plugin/src/main/java/dev/hyvanilla/plugin/block/FlowerPotUse.java:31` ; `core/.../citizen/CitizenAI.java:73-74, 103, 145` ; `core/.../citizen/CitizenManager.java:101, 106` ; `domum/plugin/.../cutter/CutterPage.java:41`, `CutterDrawing.java:129`
```java
/** PERFORM_BLOCK_UPDATE | NO_SEND_PARTICLES, as vanilla ChangeStateInteraction swaps a state. */
private static final int SWAP_SETTINGS = 260;
```
Mécanisme : `SetBlockSettings.PERFORM_BLOCK_UPDATE = 256` et `NO_SEND_PARTICLES = 4` existent (`SetBlockSettings.java:7,13`) ; `CitizenAI` soustrait `20` à `idleTicksLeft` et ajoute `5` à `wanderTicks` en littéraux séparés des `tickRate` 20 et 5 des transitions ; `CitizenManager` code `500` au lieu de `Colony.SLOW_TICK` ; le lot de 10 du cutter vit deux fois dans le plugin et pas dans le cœur (`CutterCraft.MAX_BATCH`).
Impact : une renumérotation Hytale ou un changement de cadence casse sans erreur de compilation.
Règle : § 3 (constantes nommées avec leur unité), § 1 (API référencée). Remède : `SetBlockSettings.PERFORM_BLOCK_UPDATE | NO_SEND_PARTICLES` ; `IDLE_TICK_RATE`, `WANDER_TICK_RATE` ; `Colony.SLOW_TICK` ; `CutterCraft.MAX_BATCH`.
Sévérité BAS · effort S · confiance HAUTE · DÉJÀ CONNU non.

### G-5 — BAS — Code mort
`domum/plugin/src/main/java/dev/hydomum/plugin/runtime/BlockTypeSynchronizer.java:36-42` (`Rebuild.EDITOR`, `ALL`, 0 référence) ; `core/src/main/java/dev/hycolony/core/kernel/ai/TickRateStateMachine.java:139-142` (`history`, lu par un seul test) ; `core/.../construction/hut/LivingModule.java:6-10` (A-8)
```java
history.addLast(state + "->" + newState);
```
Règle : § 3 (code mort, PMD ne voit pas les méthodes publiques inutilisées). Remède : supprimer ; pour `history`, MC le garde pour le débogage : soit une paire d'énumérés sans formatage, soit rien.
Sévérité BAS · effort S · confiance HAUTE · DÉJÀ CONNU non.

## 2. Non retenus

- `catch (RuntimeException)` ×96 : aux frontières prévues par le § 4 (adaptateurs, systèmes, pages) ; `catch (Exception)` `HyColonyCommand.java:239` (selftest), `catch (Exception | Error)` `HytaleBlueprintSource.java:169` (`PrefabBufferUtil` lève `java.lang.Error`, :162, :202, :268, filet `.exceptionally`), `catch (RuntimeException | LinkageError | AWTError)` `VariantBuilder.java:52,80` (AWT sur serveur, commenté) : justifiés.
- `switch` à motifs : 32 sites ; `sealed` là où l'exhaustivité compte (`Requestable`, `HutPlacement`, `WindowKey`, `WorkOrderRefusal`…) ; `ModuleTab` ouvert par choix (rendu par `switch` avec `default` FINE).
- Threads virtuels, `ScopedValue`, Gatherers : aucun besoin identifié (tout tourne sur le thread du monde, pas d'I/O bloquante par tick hors sauvegarde).
- `synchronized` ×2 (`VariantStore`) : hors thread de monde, voir E-9.
- `@SuppressWarnings("unchecked")` ×3 : génériques de l'API Hytale ; `@SuppressWarnings("NullAway")` `CitizenItemContainer.java:101` : localisé.
- Nullité : JSpecify partout ; `javax.annotation.Nonnull` seulement pour recopier les signatures surchargées de Hytale (43 imports).
- `PrefabStyles.Level` (record avec `int[]`) : jamais comparé ni haché.
- `Player.getPlayerRef()` déprécié pour retrait : seul accès disponible dans `MapMarkerProvider` ; à surveiller à la prochaine version (G-3).
- `HiringMode.ordinal()` : ne sert qu'au cycle du bouton ; la persistance passe par `name()` (`WorkerModule.write:160`).

## 3. Ce qui est bien fait

- `core/src/main/java/dev/hycolony/core/request/model/Requestable.java:7` et `request/RequestableJson.java:28` : hiérarchie scellée + `switch` exhaustif à l'écriture, le compilateur liste chaque site à modifier pour un nouveau type (audit B § 1.3 : « ne pas passer à un registre »).
- `core/src/main/java/dev/hycolony/core/kernel/persist/SavedJson.java` : `intOr`, `boolOr`, `stringOr`, `arrayOr`, `objectOr`, `enumOf`, `tryPos` : la lecture tolérante en une bibliothèque de sept fonctions, appliquée partout sauf aux trois lecteurs de l'axe I.
- `build-logic/src/main/kotlin/hy.java-checks.gradle.kts:23-34` : NullAway en mode JSpecify en **erreur** sur les quatre groupes de paquets ; `gradle/libs.versions.toml` : toutes les versions épinglées.

## 4. Tableau

| Sév. | `chemin:ligne` | Titre |
|---|---|---|
| BAS | `core/.../farming/field/FarmField.java:19-20, 37, 50` (+11) | G-1 `Optional` en champ et en paramètre |
| BAS | `core/.../citizen/CitizenData.java:70-99` ; `Skill.java:46-53` | G-2 `null` renvoyé hors des ports |
| BAS | `plugin/.../block/HutBlockSystems.java:101` (+15 avertissements) | G-3 avertissements Error Prone tolérés, ordinal persisté |
| BAS | `vanilla/plugin/.../FlowerPotUse.java:31` ; `core/.../citizen/CitizenAI.java:103,145` ; `CitizenManager.java:101,106` | G-4 nombres magiques et littéraux dupliqués |
| BAS | `domum/plugin/.../runtime/BlockTypeSynchronizer.java:36-42` ; `core/.../kernel/ai/TickRateStateMachine.java:139-142` | G-5 code mort |
