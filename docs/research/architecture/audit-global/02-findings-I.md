# Audit global HyColony : 02, axe I, persistance et versionnement

```
ÉTAT : phase 2, axe I écrit. Code audité : commit 3e2e70ca. Sources : rapport « modèle de données et
persistance », relecteurs cœur (app/colony/building/citizen ; construction/job ; request/logistics/crafting/
farming), plugin. Chemins relatifs à la racine du dépôt.
```

**Note de l'axe : 3/5.** Le socle est solide : `schemaVersion` 5, `MigrationChain` avec 4 migrations et 8 fixtures, copie `.vN` avant migration, écriture `.tmp` → `.bak` → move atomique, quarantaine `corrupt/`, `archive/` à la suppression, ids jamais réutilisés, `heal` qui répare et marque à réécrire, contenu inconnu **gardé brut** pour les bâtiments, modules et métiers, `SavedJson` pour la lecture tolérante. Mais trois lecteurs ne l'utilisent pas et verrouillent la colonie entière, les requêtes inconnues sont perdues malgré le commentaire qui dit le contraire, et un verrou laisse le territoire libre.

## 1. Constats

### I-1 — HAUT — `WorkerModule.read` plante sur une valeur inconnue : la colonie entière n'est plus chargée
`core/src/main/java/dev/hycolony/core/job/WorkerModule.java:166-171`
```java
in.getAsJsonArray("workers").forEach(e -> workers.add(e.getAsInt()));
...
hiringMode = HiringMode.valueOf(in.get("hiringMode").getAsString());
```
Mécanisme : `valueOf` lève `IllegalArgumentException` sur un nom inconnu, `getAsInt` sur un élément non numérique, `getAsJsonArray` sur un non-tableau. `BuildingSerializer.read:87` appelle `pm.read(saved)` sans garde ; `ColonyPersistence.java:100-102` attrape la `RuntimeException` et ajoute l'id à `lockedIds` (« Colony N failed to load; file left untouched »). `SavedJson.enumOf` (`kernel/persist/SavedJson.java:54`) existe et `WorkOrder.read:174`, `BuilderSettingsModule.read:70-74` l'appliquent déjà.
Impact : une sauvegarde dont `hiringMode` est renommé (ajout puis retrait d'un mode, retour à une version antérieure), ou un `workers` mal écrit, rend **toute la colonie** invisible au serveur : ses huttes ne sont plus protégées, ses corps restent debout sans IA. Le module est présent dans chaque hutte à ouvriers (bâtisseur, livreur, fermier).
Règle : § 5 (« une valeur inconnue prend sa valeur de repli. On ne plante jamais sur une vieille sauvegarde »). Remède : `SavedJson.enumOf(HiringMode.class, in.get("hiringMode")).orElse(HiringMode.DEFAULT)` ; `workers` lu élément par élément, les non-nombres ignorés ; test `unknownHiringModeFallsBackToDefault` dans `WorkerModuleTest`.
Sévérité HAUT · effort S · confiance HAUTE · DÉJÀ CONNU non.

### I-2 — MOYEN — `PermissionsSerializer.read` : une clé de rang ou de membre absente verrouille la colonie, sans test
`core/src/main/java/dev/hycolony/core/colony/permission/PermissionsSerializer.java:49-56, 61-64`
```java
Rank rank = new Rank(r.get("id").getAsInt(), r.get("name").getAsString(),
        r.get("permissions").getAsLong(), r.get("initial").getAsBoolean());
rank.setColonyManager(r.get("colonyManager").getAsBoolean());
```
Mécanisme : chaque champ est lu par `get(...).getAsX()` sans `SavedJson` ; une entrée de `ranks` sans `colonyManager`, `hostile`, `initial` ou `name`, ou un membre sans `name`/`rank`, lève une `NullPointerException` → `lockedIds`. `MissingKeysLoadTest`/`TolerantLoadTest` ne touchent ni `permissions` ni `ranks` (grep vide). Seule l'identité (`owner`, `id`, `center`) est documentée comme bloquante (`ColonySerializer.java:76-78`).
Impact : un fichier édité à la main (ajout d'un rang) ou écrit par une version antérieure sans le drapeau `hostile` suffit.
Règle : § 5, § 8. Remède : `intOr/stringOr/boolOr` (défauts `initial=true`, `colonyManager=false`, `hostile=false`, nom `defaultName(id)`), membre sans `uuid` valide ignoré ; test `rankWithoutItsFlagsAndMemberWithoutNameLoadWithDefaults`.
Sévérité MOYEN · effort S · confiance HAUTE · DÉJÀ CONNU non.

### I-3 — MOYEN — `CourierAssignmentModule.read` : une entrée mal formée verrouille la colonie
`core/src/main/java/dev/hycolony/core/logistics/warehouse/CourierAssignmentModule.java:97-98, 103`
```java
in.getAsJsonArray("couriers").forEach(e -> couriers.add(e.getAsInt()));
...
hiringMode = HiringMode.valueOf(in.get("hiringMode").getAsString());
```
Mécanisme : `"couriers": {}` ou `["a"]` lève `ClassCastException`/`NumberFormatException` ; `"hiringMode": {}` lève `UnsupportedOperationException`, que le `catch (IllegalArgumentException)` ne prend pas. `WarehouseRequestQueue.read` du même paquet fait déjà la lecture tolérante avec `RequestToken.fromJson`.
Règle : § 5. Remède : `SavedJson.arrayOr` + filtre `JsonPrimitive.isNumber`, `SavedJson.enumOf` ; test `malformedCouriersEntryIsSkippedAndTheModuleStillLoads`.
Sévérité MOYEN · effort S · confiance HAUTE · DÉJÀ CONNU non (audit B § 1.2 ne couvrait que `RequestSerializer`).

### I-4 — MOYEN — Les requêtes d'un type inconnu sont perdues à la sauvegarde suivante, malgré le commentaire
`core/src/main/java/dev/hycolony/core/request/SavedRequests.java:30` ; `core/src/main/java/dev/hycolony/core/request/RequestSerializer.java:33-37` ; `RequestableJson.java:88-110`
```java
 * @param repaired whether broken parent or child links were dropped, so the save must be rewritten; requests left
 *     out for an unknown type or state do not count, so they are read again once their pack is back
```
```java
m.all().forEach(r -> requests.add(request(r)));
```
Mécanisme : `write` n'écrit que les requêtes vivantes ; une requête abandonnée à la lecture (type, état, outil inconnus) n'est pas conservée brute, contrairement aux bâtiments (`ColonySerializer.java:141-145`), modules (`BuildingSerializer.java:88-89`) et métiers (`CitizenSerializer.java:91-101`) ; la première autosave (une colonie active est toujours sale) l'efface avec sa famille. `TolerantLoadTest.aRequestOfAnUnknownTypeIsSkippedAndTheColonyLoads` (:59-73) ne vérifie que le chargement.
Impact : désactiver un pack (`HyColony.SubPlugins`) puis le réactiver perd ses requêtes en cours ; le commentaire promet le contraire.
Règle : § 5 (lecture tolérante **et** conservation), § 3 (commentaire faux). Remède : garder le JSON brut des requêtes illisibles (comme `unknownModules`) et le réécrire ; ou corriger le commentaire et documenter la perte. Test : sauvegarde avec pack, chargée sans, puis avec, identique.
Sévérité MOYEN · effort S/M · confiance HAUTE · DÉJÀ CONNU non.

### I-5 — MOYEN — Une colonie verrouillée laisse son territoire libre : une autre colonie peut s'y fonder
`core/src/main/java/dev/hycolony/core/app/ColonyPersistence.java:29, 94-103` ; `core/src/main/java/dev/hycolony/core/app/action/HutActions.java:82-88` ; `colony/territory/TerritoryIndex.java:49-60`
```java
/** Ids whose file must never be touched (newer schema). */
private final Set<Integer> lockedIds = new HashSet<>();
```
Mécanisme : une colonie verrouillée (schéma trop récent, `IOException`, `RuntimeException` des lecteurs I-1 à I-3) n'est pas enregistrée : `register` est sauté, donc `TerritoryIndex` ne contient aucune de ses cellules ; `HutActions.checkOutsideColonies` → `isFreeForNewColony` accepte une fondation dessus.
Impact : après un retour à une version plus récente (ou la correction du fichier), deux colonies se chevauchent ; le verrou est en mémoire seulement, chaque redémarrage retente.
Règle : § 5 (une sauvegarde incohérente est réparée, jamais écrasée : ici elle est écrasée **territorialement**). Remède : à la lecture, revendiquer au moins la cellule du centre et le carré initial d'une colonie verrouillée (le `center` est lisible sans le reste, ou depuis le nom du fichier) ; test `aLockedColonyKeepsItsLandClaimed`.
Sévérité MOYEN · effort S · confiance HAUTE · DÉJÀ CONNU non.

### I-6 — MOYEN — La passe de vérification finale du bâtisseur n'est pas persistée
`core/src/main/java/dev/hycolony/core/construction/builder/BuildSite.java:34` ; `BuilderAI.java:213-220` ; `StructureScan.java:71-73` ; `construction/workorder/WorkOrder.java:149-167`
```java
/** The walk over SOLID and DECORATE after the last stage ran for the loaded order. */
private boolean finalCheckDone;
```
Mécanisme : le drapeau vit en mémoire ; l'ordre sauve `stage=SOLID, progressIndex=k` sans distinguer première passe et passe finale. Au rechargement, `BuildSite.load` repart avec `finalCheckDone=false` et `StructureScan.needsWork` applique la règle de première passe (`open = world == null || kind != UNBREAKABLE`) au lieu de « seulement l'air ».
Impact : un bloc que le joueur a remplacé dans l'emprise (garanti par `verificationPassOnlyRefillsAir`, `BuilderAITest.java:1012`) est miné puis reposé après un redémarrage tombé pendant la passe finale ; SOLID, DECORATE, CLEAR_LEFTOVERS puis SOLID, DECORATE rejoués (jusqu'à trois parcours de plus sur un plan de 20 000 cases).
Règle : § 5 (stade + position de reprise). Remède : `finalCheck` sur `WorkOrder` (`boolOr(…, false)`, remis à `false` dans `release()`), écrit par `BuildSite.startFinalCheck` comme `progress` ; test dans `BuilderCleanupTest` : redémarrage pendant la passe finale → le bloc échangé reste.
Sévérité MOYEN · effort S/M · confiance HAUTE · DÉJÀ CONNU non.

### I-7 — MOYEN (DÉJÀ CONNU) — Pas de garde « chaque sorte de `Requestable` fait l'aller-retour JSON »
`core/src/main/java/dev/hycolony/core/request/RequestableJson.java:89-111`
```java
return switch (SavedJson.stringOr(o.get("type"), "")) { case "stack" -> …; … default -> Optional.empty(); };
```
Mécanisme : `write` est un `switch` exhaustif sur la hiérarchie scellée, `read` un `switch` sur chaîne : une 7e sorte compile, s'écrit, et se relit `Optional.empty()` (puis I-4). Les 6 sortes ont chacune un test d'aller-retour éparpillé ; `everyRequestableKindRoundTrips` (réflexion sur `getPermittedSubclasses()` récursif) n'existe pas.
Règle : § 5, § 8. Remède : le test paramétré proposé par l'audit B § 1.3.
Sévérité MOYEN · effort S · confiance HAUTE · DÉJÀ CONNU oui (audit B § 1.3 : `RequestableJson` extrait, le test non écrit).

### I-8 — BAS — Deux constantes de schéma à garder en phase à la main, pas de fixture v5
`core/src/main/java/dev/hycolony/core/app/persistence/ColonySerializer.java:36` ; `core/src/main/java/dev/hycolony/core/kernel/persist/MigrationChain.java:34-41` ; `core/src/test/resources/fixtures/`
Mécanisme : `SCHEMA_VERSION = 5` et `MigrationChain.sp3b()` (`current = 5`, nom de jalon) sont deux sources de vérité, gardées par `MigrationV3ToV4Test.java:60-62` seulement ; le schéma courant n'a pas de fixture (aller-retour généré par `PersistenceTest.fullRoundTrip`) : la prochaine migration 5→6 n'aura pas d'entrée figée à migrer.
Règle : § 5 (« garde une fixture de l'ancienne version »). Remède : `MigrationChain.current()` unique ; ajouter `colony-v5.json` maintenant (le fichier généré aujourd'hui, figé).
Sévérité BAS · effort S · confiance HAUTE · DÉJÀ CONNU non.

### I-9 — BAS — Ce que `heal` ne répare pas : file d'entrepôt, `homeBuilding`, `FieldChoice.checked` qui grandit
`core/src/main/java/dev/hycolony/core/logistics/warehouse/WarehouseRequestQueue.java:16` ; `core/.../citizen/CitizenData.java` (`homeBuilding`) ; `core/.../farming/hut/FieldChoice.java:25`
Mécanisme : les tokens de la file d'entrepôt ne sont élagués que paresseusement, et `CourierResolver.suitability`/`WarehouseStockResolver` (:137-146) comptent `tokens().size()` avec les périmés ; `homeBuilding` n'est jamais validé (seul lecteur `JobXp.java:28`) ; `FieldChoice.checked` (positions de champs vues) n'est jamais élagué et est sauvegardé.
Règle : § 5 (`heal`), § 4 (« listes globales qui ne font que croître », faiblesse MC à ne pas copier). Remède : `heal` élague la file d'entrepôt et `checked` sur les champs existants ; `homeBuilding` validé comme `workBuilding`.
Sévérité BAS · effort S · confiance HAUTE · DÉJÀ CONNU non.

### I-10 — BAS — Écriture : pas de `fsync`, une seule génération `.bak`, `backupVersion` non atomique ; marquage sale incident
`core/src/main/java/dev/hycolony/core/kernel/persist/FileColonyStorage.java:139-150, 154-160` ; `core/.../citizen/CitizenManager.java:80-82` ; `core/.../app/ColonyPersistence.java:83-85, 89-93`
Mécanisme : `Files.writeString` sans `fsync` puis deux `move` (seul le dernier atomique) ; un crash entre les deux laisse `.bak` + `.tmp`, ce que `colonyIds()` et `load` rattrapent ; `backupVersion` (copie `.vN` avant migration) est un `writeString` direct, jamais réécrit, et son `IOException` verrouille la colonie ; `tickData` marque sale toutes les 60 ticks dès qu'un corps est lié (une colonie active est toujours réécrite à l'autosave, K-1) alors qu'une colonie sans corps qui reçoit un « Fournir » n'est sauvée qu'à l'arrêt ; une colonie migrée mais saine reste sur l'ancien schéma sur disque tant que rien ne la salit.
Règle : § 5. Remède : `StandardOpenOption.SYNC` sur le `.tmp` ; `markDirty` dans les actions de requête ; `markDirty` après migration.
Sévérité BAS · effort S · confiance HAUTE · DÉJÀ CONNU non.

## 2. Non retenus

- Schéma trop récent → `lockedIds`, jamais réécrit : conforme au § 5 (« on ne plante jamais » : la colonie est mise de côté, pas cassée), testé (`newerSchemaIsSkippedAndNeverOverwritten`).
- `RequestSerializer` type inconnu : lecture tolérante faite depuis l'audit B (fixture `colony-v3-unknown-request.json`) ; il reste I-4.
- Migration 1→2 : `getAsJsonArray("citizens")` lève si le tableau manque : une v1 sans `citizens` n'existe pas (SP0 l'écrit toujours).
- Doublon de position de bâtiment → `ResolverRegistry.addProvider` lève → verrou : aucun chemin trouvé qui pose deux huttes à la même position (confiance BASSE, écarté).
- `ModuleProducer` clés non vérifiées uniques : `put` écrase, erreur de programmation, pas de sauvegarde.
- `TerritoryIndex` non persisté, reconstruit au chargement : voulu, cohérent avec MC (`ChunkClaimData` recalculé) sauf I-5.
- Rotation de hutte persistée comme ordinal d'enum Hytale : G-3.
- `SavedVariants` (HyDomum) sans `MigrationChain` : documenté (sens des dépendances), fixture v0 testée, `schemaVersion` présent.

## 3. Ce qui est bien fait

- `core/src/main/java/dev/hycolony/core/app/ColonyPersistence.java:75-104` et `kernel/persist/FileColonyStorage.java:99-150` : isolation des échecs par colonie, copie `.vN` avant migration, verrou, état réparé conservé sale, `.tmp` → `.bak` → move atomique, quarantaine du fichier principal corrompu **avant** qu'un `save` ne le fasse tourner sur le `.bak` sain.
- `core/src/main/java/dev/hycolony/core/request/SavedRequests.java:35-59, 82-92` : réparation des familles (parent absent, liens non réciproques, cycles bornés par un `Set`), testée avec fixtures ; `construction/workorder/WorkOrder.java:173-199` : lecture tolérante clé par clé avec `SavedJson`, ancien drapeau ignoré et testé.
- Contenu inconnu **gardé brut** pour bâtiments, modules et métiers (`ColonySerializer.java:141-145`, `BuildingSerializer.java:88-89`, `CitizenSerializer.java:91-101`) : un pack désactivé ne détruit rien de ce qu'il possédait (hors requêtes, I-4).

## 4. Tableau

| Sév. | `chemin:ligne` | Titre |
|---|---|---|
| HAUT | `core/.../job/WorkerModule.java:166-171` | I-1 lecture stricte, colonie verrouillée |
| MOYEN | `core/.../colony/permission/PermissionsSerializer.java:49-64` | I-2 lecture stricte des rangs, sans test |
| MOYEN | `core/.../logistics/warehouse/CourierAssignmentModule.java:97-103` | I-3 lecture stricte des livreurs |
| MOYEN | `core/.../request/SavedRequests.java:30` ; `RequestSerializer.java:33-37` | I-4 requêtes inconnues perdues à la sauvegarde |
| MOYEN | `core/.../app/ColonyPersistence.java:29, 94-103` | I-5 colonie verrouillée, territoire libre |
| MOYEN | `core/.../construction/builder/BuildSite.java:34` | I-6 passe finale non persistée |
| MOYEN (connu) | `core/.../request/RequestableJson.java:89-111` | I-7 pas de test d'aller-retour par sorte |
| BAS | `core/.../app/persistence/ColonySerializer.java:36` ; `kernel/persist/MigrationChain.java:34-41` | I-8 deux constantes de schéma, pas de fixture v5 |
| BAS | `core/.../logistics/warehouse/WarehouseRequestQueue.java:16` ; `farming/hut/FieldChoice.java:25` | I-9 ce que `heal` ne répare pas |
| BAS | `core/.../kernel/persist/FileColonyStorage.java:139-160` | I-10 écriture et marquage sale |
