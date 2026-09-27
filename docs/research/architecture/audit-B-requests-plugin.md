# Audit B : système de requêtes, classes du plugin signalées par PMD, UiPort, persistance

Audit en lecture seule, sur l'état de `sp0-foundations` (71f9e7e). Mesures PMD : PMD 7.28.0 lancé hors Gradle avec `config/pmd/ruleset.xml` **sans** la liste d'exceptions (script dans le scratchpad, sans auxclasspath, donc les règles qui demandent la résolution de types peuvent se taire).

## 1. Système de requêtes

### 1.1 `RequestManager` : une vraie façade, garder l'exception PMD

**Mesures.** 239 lignes, 25 méthodes publiques et 2 package-private (`store()` l. 220, `reassignLoaded` l. 230). CBO mesuré à 25 pour un seuil de 20 (PMD, l. 26). Ce couplage se répartit ainsi : 6 collaborateurs internes (l. 30-36), environ 11 types de modèle présents dans les signatures (`RequestToken`, `Requestable`, `RequesterId`, `ItemAmount`, `Request`, `Resolver`…) et 6 types du JDK (`Consumer`, `Predicate`, `Optional`…).

**Travail réel.** Presque tout est `store.require` suivi de `queue.submit(() -> collaborateur.x())`. La seule logique propre est faite de gardes : une requête déjà assignée (l. 85-87), une transition non publique (l. 106-108), l'overrule d'une requête déjà terminée (l. 135-136), plus deux mutations triviales (`setCitizenId` l. 168, `setDeliveredToCitizen` l. 136) et la composition de `onProviderRemoved` (l. 57-60). L'assignation, les transitions et l'annulation vivent dans `RequestAssigner` (183 l.), `RequestTransitions` (121 l.) et `RequestCanceller` (76 l.).

**Comparaison avec MC.** `StandardRequestManager` fait environ 750 lignes et plus de 30 méthodes publiques. Il délègue à 5 handlers (`Update`, `Token`, `Resolver`, `Request`, `Provider`) et à 7 data stores adressés par token. HyColony fait la même correspondance en plus compact : `RequestHandler` devient Assigner, Transitions et Canceller ; `ResolverHandler` et `ProviderHandler` deviennent `ResolverRegistry` ; `TokenHandler` devient l'UUID de `RequestStore.create` ; `UpdateHandler` devient `MigrationChain`. MC a lui aussi des champs dédiés au resolver joueur et au resolver « retrying », ce qui justifie leur traitement à part dans `RequestSerializer` (l. 109-115).

**Verdict : faux problème.** Le couplage vient de la surface de l'API, pas de responsabilités cachées. Séparer les lectures (`get`, `byRequester`, `assignedTo`, `all`, l. 191-218) dans un `RequestQueries` obligerait 22 fichiers appelants à tenir deux objets. Ce découpage ne rapporte rien. L'entrée `CouplingBetweenObjects` est légitime.

**Garde-fou.** Aucun nouveau : le seuil de 400 lignes et `TooManyMethods` bornent déjà la croissance.

### 1.2 Lecture non tolérante d'un type de requête inconnu : **à corriger maintenant**

**Preuve.** `RequestSerializer.readRequestable` lève `IllegalArgumentException("Unknown requestable type")` (l. 236). `RequestState.valueOf` (l. 153) lève aussi une exception. `ColonyPersistence.loadOne` l'attrape (l. 95-97) et **verrouille toute la colonie** (`lockedIds.add`).

**Risque.** Renommer ou retirer un type de requête, ou revenir à une version antérieure après l'ajout d'un type (crafting, nourriture), rend la colonie entière non chargeable. C'est contraire au § 5 : lecture tolérante, sans jamais planter.

**Correctif minimal.** `readRequestable` renvoie un `Optional`. Une requête illisible est ignorée et journalisée (première en WARNING, les suivantes en FINE). `readAssignments` et `readTokens` ignorent déjà les tokens absents (l. 95-98). `cancelOrphans` et `heal` nettoient les parents et les enfants qui pendent, sinon il faut élaguer `children` au chargement.

**Garde-fou.** Un test `unknownRequestableTypeIsDroppedAndColonyLoads` avec une fixture JSON.

### 1.3 Ajouter un type de requête : le `sealed` est le bon choix, mais le sérialiseur va déborder

**Points à éditer aujourd'hui** pour un nouveau type :

- `Requestable` et ses `permits` (`model/Requestable.java:7`), ou `Deliverable` ;
- `RequestSerializer`, écriture (l. 177-205) et lecture (l. 211-237) ;
- `Request.incrementPriorityDueToAging` (l. 60-62) ;
- `LogisticsViews:136-138` ;
- `CourierTaskPicker:146-148, 171-173, 180-182` ;
- `RequestsPage.describe` (plugin, l. 88-99) et les clés de traduction.

Tous ces sites sont des `switch` exhaustifs sur un type scellé : **le compilateur liste chaque endroit à modifier**, **sauf** la lecture, qui fait un `switch` sur une chaîne. MC enregistre des fabriques (`StandardFactoryController`) parce qu'il doit accueillir des addons Forge. HyColony n'en a pas besoin. Un registre ferait perdre l'exhaustivité vérifiée à la compilation. **Ne pas passer à un registre.**

**Risque réel.** Le fichier `RequestSerializer` fait 290 lignes et compte environ 20 lignes par type. MC a une douzaine de types (pile, liste de piles, outil, nourriture, combustible, tag, crafting public et privé, fondant…). Il passerait donc au-dessus de 400 lignes au bout de 5 ou 6 types.

**Quand :** au prochain type de requête.

**Correctif :** extraire `RequestableJson`, qui contient les deux `switch` et `blockPos` (l. 174-251). Il se découpera plus tard par famille (objets, coursier, crafting).

**Garde-fou.** Un test paramétré qui parcourt `Requestable.class.getPermittedSubclasses()` de façon récursive et exige un échantillon pour chaque classe concrète, qui fait l'aller-retour en JSON (`everyRequestableKindRoundTrips`). Il rend impossible d'oublier la lecture d'un nouveau type.

### 1.4 À surveiller (plus tard)

- **Recherche linéaire des resolvers.** `ResolverRegistry.candidates` renvoie tous les resolvers partagés, filtrés ensuite par `handles()` (`RequestAssigner:59-63`). MC indexe les resolvers par type de requête (`requestableTypeRequestResolverAssignmentDataStore`). Avec des dizaines de crafteurs publics, chaque assignation et chaque nouvelle tentative parcourt toute la liste. **Plus tard, sur mesure** : ajouter `Map<Class<? extends Requestable>, List<Resolver>>`.
- **Enregistrements hors de la file.** `onProviderAdded` et `registerBuiltIn` (l. 47-53) sont les seules mutations qui ne passent pas par la file. `candidates` renvoie la liste `shared` elle-même (`ResolverRegistry:179`). Un resolver qui enregistrerait un fournisseur pendant `assign` provoquerait une `ConcurrentModificationException`. Ce n'est pas atteignable aujourd'hui ; il faut le documenter.
- **Méthodes publiques utilisées seulement par les tests.** `assign(token, blacklist)` (l. 79), `createChild` (l. 70) et `setCreationListener` (l. 174). `createChild` fait partie de l'API de MC : il faut le garder.

## 2. Classes du plugin signalées par PMD

Les deux listes de taille (`gradle/file-size-allowlist.txt`, `package-size-allowlist.txt`) sont **vides**, commentaires mis à part.

| Classe | Lignes | Méth. | Imports | Violations mesurées | Nature |
|---|---|---|---|---|---|
| `HytaleItemCatalog` | 294 | 20 | 22 | GodClass (WMC 73, TCC 1,5 %), CBO 23, `computeBlock` CC 19 / NPath 1230, `computeItem` CC 18 | **Deux responsabilités** |
| `HytaleWorldBlocks` | 357 | 18 | 34 | GodClass (WMC 74), `breakBlock` cognitive 18 / CC 16, `place` CC 13 | Adaptateur complexe par nature |
| `HytaleCitizenBodies` | 350 | 19 | 43 | GodClass (TCC 13,7 %), CBO 28, `navStatus` CC 13 | Adaptateur, à découper au moment des gardes |
| `HytaleUiPort` | 205 | 21 | 37 | CBO 28, 37 imports | Un peu de logique mélangée |
| `HyColonyCommand` | 361 | 4 + 4 classes imbriquées | 47 | CBO 26 **sur `SelfTest`** (l. 197), 47 imports | **Vrai fourre-tout** |
| `BuildingPage` | 160 | 10 | 16 | **aucune** | Entrées périmées |
| `WorldRuntime` | 182 | 14 | 41 | 41 imports | Racine de composition : faux problème |
| `HutBlockSystems` | 229 | 8 | 32 | 32 imports, `UnusedLocalVariable` l. 122 | Correction triviale |

### 2.1 Six entrées périmées pour `BuildingPage` : **maintenant**

**Preuve.** PMD ne signale rien sur `BuildingPage` (160 lignes, 16 imports, aucune méthode de plus de 4 paramètres). Six lignes de `known-violations.txt` la concernent pourtant (`CognitiveComplexity`, `CouplingBetweenObjects`, `CyclomaticComplexity`, `ExcessiveImports`, `ExcessiveParameterList`, `NPathComplexity`). La suppression s'applique au type entier (`build.gradle.kts:117-131`) : une nouvelle dérive passerait donc sans bruit.

**Correctif.** Retirer les six lignes, puis vérifier avec `pmdMain` (mon analyse se faisait sans classpath). `CompareObjectsWithEquals` sur `HytalePlayerInventory` (comparaison d'identité voulue, l. 101 `ref.getStore() != store`) est invérifiable sans résolution de types. Si la règle se déclenche, un `@SuppressWarnings("PMD.CompareObjectsWithEquals")` sur la seule méthode, avec un commentaire, serait plus précis que l'exception sur le type entier.

`HutBlockSystems:122` : `case HutPlacement.FoundNewColony f` devient `FoundNewColony _` (Java 22). Cela retire une entrée de plus.

**Garde-fou.** Une tâche qui fait échouer le build quand une entrée de la liste ne se déclenche plus : PMD lancé sans liste d'exceptions sur les seuls fichiers listés, puis comparaison. C'est un changement de garde-fou (§ 10), qui demande l'**accord de l'utilisateur**.

### 2.2 `HytaleItemCatalog` : vraie God Class, **maintenant ou au prochain ajout au port**

TCC 1,5 % : deux groupes qui ne partagent rien. D'un côté les blocs (`blocks`, `computeBlock` l. 176-217, `toolType`), de l'autre les objets (`items`, `computeItem` l. 233-271, `durability` l. 273-288).

**Découpage minimal :** `HytaleBlockInfo` (cache et classification des blocs) et `HytaleToolStats` (cache, outil, durabilité). `HytaleItemCatalog` reste le port et délègue, sur le modèle de `LiveWindows` et `HutPickUp`. Il faut aussi scinder `computeBlock` (par exemple `gatherOf(type)` et `hardness(gather)`) pour descendre sous CC 10.

**À noter :** `MAX_HARDNESS` (l. 50) est une heuristique d'équilibrage dans le plugin, à la limite du « pas de règle de jeu dans le plugin ».

**Garde-fou :** retirer les 5 entrées correspondantes, et PMD reprend le relais.

### 2.3 `HyColonyCommand` : vrai fourre-tout, **maintenant (avant la prochaine commande)**

**Preuve.** Quatre sous-commandes imbriquées (`Info` l. 86, `Rank` l. 122, `Delete` l. 166, `SelfTest` l. 197-360). `SelfTest` occupe à lui seul 164 lignes et mélange test des corps, construction (l. 290), clés de blocs (l. 341) et rapport. MC compte une quarantaine de commandes.

**Correctif.** Un fichier par sous-commande (`InfoCommand`, `RankCommand`, `DeleteCommand`, `SelfTestCommand`). Les parties de `SelfTest` vont dans `ConstructionSelfTest` et `BodySelfTest`, sur le modèle déjà en place de `LogisticsSelfTest`. `groups`, `where` et `say` deviennent un `CommandSupport` package-private.

**Garde-fou.** Retirer les 2 entrées. Le seuil de 400 lignes et `ExcessiveImports` prennent le relais.

### 2.4 `HytaleUiPort` : extraire la fondation de colonie, **plus tard (prochaine fenêtre)**

La classe anonyme `FoundColonyPage.Handler` (l. 71-98) déduit « emplacement invalide, fondation abandonnée » de `pendingPositionOf` (l. 79-81), puis retire l'hôtel de ville. C'est une décision de jeu dans le plugin. Le cœur devrait renvoyer un résultat explicite, par exemple `confirm` → `Created | Invalid(pos) | Pending`.

**Correctif.** Extraire `FoundColonyWindow` (le gestionnaire, `removeTownHall` et l'ensemble `closing`), puis `PageOpener` (l. 176-204 : `Universe` et `PageManager`).

### 2.5 `HytaleCitizenBodies` et `HytaleWorldBlocks` : complexes par nature

**`HytaleCitizenBodies`.** Un seul port (`CitizenBodies`, 11 méthodes) et une API Hytale dense. Ce n'est pas un fourre-tout aujourd'hui. **Au moment des gardes** (armure, attaque, santé), le port grossira : découper alors selon la même frontière que le port, `BodyNavigation` (`moveTo`, `navStatus`, `role`) et `BodyTeleport` (l. 308-349).

**`HytaleWorldBlocks`.** Un seul port de 7 méthodes. `breakBlock` fait 70 lignes (l. 192-262) et `place` 51 lignes, au-delà des 40 lignes du § 2, qui n'est vérifié par aucun outil. Le correctif minimal extrait la résolution de l'origine d'un bloc multi-cellules (l. 220-230) et le vidage du conteneur. Plus tard, `HytaleBlockStates` (cache de lecture, l. 279-352).

**Garde-fou proposé (accord requis, § 10).** Ajouter `NcssCount` (`methodReportLevel` autour de 30 NCSS) pour rendre la règle des 40 lignes vérifiable par le build.

### 2.6 Onglets des huttes : **à faire maintenant, avant le prochain bâtiment qui a des onglets**

**Preuve.** Chaque bâtiment qui a ses propres onglets touche aujourd'hui cinq endroits :

- `BuildingView` : un composant `Optional` de plus. Le record en compte déjà 20, dont `builder`, `warehouse` et `courier` (`BuildingView.java:36-39`) ;
- `BuildingViews.of` (l. 38-64) ;
- l'enum `BuildingPage.Tab` (l. 28-55) ;
- trois tests `if`/`ifPresent` dans `BuildingPage` (l. 71-79, 116-122, 152) ;
- `Building.ui`, qui contient **tous** les onglets de **tous** les bâtiments (336 lignes : `#ResourcesTab` à `#CourierTasksTab`, l. 186-311).

Les onglets de MC sont des modules de bâtiment : une vue de module avec sa propre fenêtre, enregistrée dans `BuildingEntry`. Avec des dizaines de bâtiments, le fichier `.ui` passerait le millier de lignes, et chaque ajout modifierait les mêmes quatre fichiers.

**Correctif minimal.**

- **Dans le cœur**, `List<ModuleTabs> modules` remplace les trois `Optional`. `sealed interface ModuleTabs permits BuilderTabs, WarehouseTabs, CourierTabs` est rempli dans l'ordre des modules de MC. L'information part déjà des modules (`BuilderTabsViews.of:27`, `LogisticsViews:45, 82`).
- **Dans le plugin**, une interface `HutTab` porte `tabs()`, `render(ui, events)` et `handle(Act)`. Une fabrique fait un seul `switch` exhaustif (on garde l'exhaustivité plutôt qu'une `Map<Class, …>`). Chaque onglet ajoute son propre fichier `.ui` dans un conteneur, comme `RequestsPage` le fait déjà pour ses lignes (`ui.append(list, "…RequestRow.ui")`, l. 56). `BuildingPage` n'est plus modifié à chaque bâtiment.
- Ranger au passage les `Builder*Tab` dans `ui/builder`. `ui/` a 11 fichiers pour une limite de 15.

**Garde-fou.** Un test du cœur vérifie que chaque `ModuleTabs` vient d'un module. Le paquet limité à 15 fichiers et le seuil de 400 lignes du `.ui`, ce dernier à ajouter à `checkFileSizes` avec l'accord de l'utilisateur, font le reste.

## 3. Croissance de `UiPort`

**Preuve.** 13 méthodes (`UiPort.java:7-44`) : 7 `show*` et `open*`, 3 `refresh*` quasi identiques (l. 24-30) et `WindowKey` scellé.

**Jugement honnête.** Les dizaines de fenêtres de MC sont surtout des fenêtres de module de bâtiment, c'est-à-dire des onglets de `BuildingView` (§ 2.6), pas des méthodes de `UiPort`. Les fenêtres de premier niveau à venir (recherche, liste des colonies, permissions…) porteront le total à environ 12-15 méthodes. Un `show(UUID, View)` unique déplacerait simplement le `switch` dans `HytaleUiPort` avec le même nombre de cas. Il simplifierait `FakeUi` (136 lignes), mais n'apporte rien de structurel. **Pas maintenant.**

La vraie duplication est le trio `refresh*` et ses trois sites d'appel (`ColonyWindows:60, 106, 122`). **Plus tard (au 4e refresh)** : `boolean refresh(UUID, LiveView)` avec `sealed interface LiveView permits BuildingView, TownHallView, CitizenView { WindowKey key(); }`.

**Garde-fou :** `TooManyMethods` s'applique déjà à `HytaleUiPort`.

## 4. Extensibilité de la persistance

**Déjà pilotée par registre : c'est un faux problème.**

- Modules : les clés de `b.modules()` et `PersistentModule.write/read` sont utilisées par `BuildingSerializer:38-45, 72-80`. Les modules inconnus sont conservés tels quels (l. 45, 78).
- Métiers : `Job.write/read` (`Job.java:86-98`), résolus par `ctx.jobs().byId` (`CitizenSerializer:158-168`). Un métier inconnu libère le citoyen.
- Bâtiments : `ctx.buildingTypes().byId`, et un bâtiment inconnu est gardé (`ColonySerializer:103-110`).
- Chaque fonctionnalité s'enregistre elle-même (`DeliverymanHut:35-40`, `WarehouseBuilding:32`, `ConstructionBuildingTypes:39-45`).

Ajouter un métier ou un bâtiment avec état ne demande **aucune modification** des sérialiseurs. L'état d'un resolver vit dans un module (`WarehouseRequestQueue implements PersistentModule`), pas dans `RequestSerializer`.

**Point à surveiller (plus tard).** `MigrationChain` (`kernel/persist`) réunit les migrations de tous les domaines. `v2ToV3` (l. 61-76) connaît déjà le `toolUses` du métier de bâtisseur. Au 3e ou 4e schéma, il faudra déplacer chaque `Migration` dans `colony/persistence/migration/VnToVm`, `MigrationChain` ne gardant que l'enchaînement.

**Garde-fou :** la fixture par version, déjà imposée par le § 5.

## Faux problèmes à ignorer

1. Le CBO de `RequestManager` : c'est une façade, son couplage vient de ses signatures.
2. Le traitement à part des resolvers « player » et « retrying » dans `RequestSerializer` : MC fait de même.
3. Un registre de types de requête à la manière de MC : le `sealed` avec des `switch` exhaustifs est plus sûr ici.
4. `WorldRuntime` avec 41 imports : c'est la racine de composition du monde.
5. La complexité de `HytaleWorldBlocks` et `HytaleCitizenBodies` : un port chacune, dense en API Hytale.
6. Un `show(UUID, View)` unique pour `UiPort` : il déplace le `switch` sans le supprimer.
7. Un `switch` dans la persistance des modules et des métiers : il n'y en a pas.

## Priorités

**Maintenant :**

- 1.2, lecture tolérante des types de requête ;
- 2.1, retirer les entrées PMD périmées et le `_` ;
- 2.3, découper `HyColonyCommand` ;
- 2.6, onglets des huttes enregistrés par module ;
- 2.2, découper `HytaleItemCatalog`.

**Au prochain type de requête :** 1.3, `RequestableJson` et le test sur les sous-classes autorisées.

**Plus tard :**

- 1.4, index des resolvers par type ;
- 2.4, extractions dans `HytaleUiPort` ;
- 2.5, découpage de `HytaleCitizenBodies` au moment des gardes ;
- 3, `refresh(LiveView)` ;
- 4, migrations par fichier.

**Avec l'accord de l'utilisateur (garde-fous) :** détection des entrées PMD périmées, `NcssCount`, et limite de taille des fichiers `.ui`.
