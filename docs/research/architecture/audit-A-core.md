# Audit A : conception du cœur HyColony (lecture seule, HEAD 71f9e7e)

Méthode : lecture du code, script d'imports (`scratchpad/deps_a.py`, composantes fortement connexes de Tarjan), `git diff-tree` des 5 commits du courrier, et sources MineColonies `version/main` (copie locale `scratchpad/mc/minecolonies-version-main`). Les chemins sont relatifs à `core/src/main/java/dev/hycolony/core/` sauf mention contraire.

## 1. Composition ou héritage

Constat :
- Une seule classe abstraite existe dans `core` : `job/Job.java:9`. Elle est étendue par `BuilderJob` et `DeliverymanJob`, sur un seul niveau.
- `BuilderAI` (`construction/builder/BuilderAI.java:32`) et `DeliverymanAI` (`logistics/courier/DeliverymanAI.java:25`) n'implémentent que `JobAI`.
- Le partage passe par composition : `TickRateStateMachine`, `BodyWalker` (`kernel/nav`), `JobXp`, `BuilderContext`/`CourierContext`.

**Verdict : c'est meilleur que la chaîne MC `AbstractEntityAIBasic → Interact → Skill → Structure → …` pour HyColony.** Il faut garder cette forme.

**Problème 1.1, le socle commun est enfermé dans le builder.** Plusieurs fonctions génériques de `AbstractEntityAIBasic` sont codées dans des classes package-private du builder :
- le dépôt au bout de N actions (`dumpDue`) ;
- `keepX` et « un outil par type » (`BuilderStock.java:97-143`) ;
- `getMostEfficientTool` (`BuilderStock.java:185`) ;
- `checkForToolOrWeapon` (`BuilderRequests.java:111-130`).

`BuilderStock` et `BuilderRequests` sont déclarées `final class` sans `public`, aux lignes 29 et 25. Le courrier n'utilise pas d'outil, donc le problème ne s'est pas encore vu.
- **Risque :** le bûcheron, le mineur, le pêcheur et le fermier ont tous besoin de ces fonctions. Sans extraction, chacun recopiera 200 lignes, et PMD CPD ne verra que des fragments.
- **Quand :** maintenant, avant le 2ᵉ métier à outil.
- **Correctif :** extraire dans `job/` (ou `job/worker/`) `WorkerStock` et `ToolRequests`, qui prennent `(Colony, CitizenData, Building)`. Chacun cite sa méthode MC. `BuilderStock` garde seulement ce qui est propre au builder (`ACTIONS_UNTIL_DUMP` du builder, seaux).
- **Garde-fou :** une règle ArchUnit interdit à `..logistics..` et aux futurs métiers de dépendre de `..construction.builder..`.

**Problème 1.2, la cadence est dupliquée.** La constante `MACHINE_RATE = 5` apparaît deux fois (`BuilderAI.java:36`, `DeliverymanAI.java:29`). Le garde `++calls < MACHINE_RATE` est répété (`BuilderAI.java:87`, `DeliverymanAI.java:72`), ainsi que le couple `onException` + `reset` (`BuilderAI.java:123-129`, `DeliverymanAI.java:99-104`).
- **Quand :** plus tard, au 3ᵉ métier (règle de trois).
- **Correctif :** un petit `JobTicker` composé, avec la constante MC `AbstractEntityAIBasic` en un seul endroit.
- **Pas de classe abstraite :** la seule vraie invariante commune est « un tick régulé, puis un reset sur exception », et une composition de 15 lignes suffit à la porter.

**Où une classe abstraite se justifie :** nulle part de plus. `Job` suffit, car il porte un état partagé et un cycle de vie (voir § 2).

## 2. `Job`

Vérification : `toolUses`, `setToolUses` et `forgetWearOfToolsNotHeld` **n'existent plus**. Une recherche dans `core/src/main` et `plugin/src/main` ne trouve plus que la migration `kernel/persist/MigrationChain.java:58-68`, qui convertit l'ancien `toolUses` en usure des piles. La crainte de l'utilisateur ne vaut donc plus.

Le reste de `Job` (`job/Job.java`) :

| Membre | Utilisé par | Commun dans MC ? |
|---|---|---|
| `actionsDone` (l. 34-44) | builder seul (`BuilderAI:163,247,301`, `BuilderBlockWork:170,195`) | oui : `IJob.incrementActionsDone`, utilisé par `AbstractEntityAIBasic`, le bûcheron (`EntityAIWorkLumberjack`), le cuisinier, etc. |
| `working` et inactivité (l. 46-82) | courrier seul (`DeliverymanAI:114`) | oui : `setWorking` est appelé par une vingtaine d'IA MC (artisanat, éleveurs, bûcheron) |
| `onRemoval` (l. 85) | `DeliverymanJob:148`, appelé par `WorkerModule:101` et `ColonySerializer:158` | oui : `IJob.onRemoval` |
| `createAI` (l. 32) | tous | oui |

**Verdict : c'est un faux problème.** `Job` reste une vraie base : un état commun sérialisé (`write` et `read`, l. 88-99) et des méthodes patron sans effet par défaut, fidèles à `AbstractJob`. Créer un `ToolWearTracker` ou une interface par capacité reviendrait à créer des interfaces à une implémentation, ce que CLAUDE.md § 2 interdit. **Jamais.**
- **Garde-fou :** une règle ArchUnit impose que seul `Job` soit abstrait et que les sous-classes de `Job` soient `final`. Elle interdit ainsi une profondeur supérieure à 1.

## 3. Modules de bâtiment

**Problème 3.1, `module(Class)` ne renvoie qu'un seul module.** `Building.module` (`building/Building.java:63-68`) parcourt les modules avec un stream puis `findFirst()`. Or MC déclare plusieurs modules de la même classe dans un bâtiment (`apiimp/initializer/ModBuildingsInitializer.java`) :
- la tour de garde a 4 `GuardBuildingModule` (l. 233-236), et `GuardBuildingModule` étend `WorkerBuildingModule` ;
- la caserne a 5 modules de garde (l. 70-74) ;
- l'école a `TEACHER_WORK` et `PUPIL_WORK` (l. 469-470) ;
- l'université et le cimetière n'ont qu'un module de travail (l. 441-443, 608).

MC résout un module par `getModule(Class, Predicate)` et par `getModules(Class)` (`api/colony/modules/IModuleContainer.java`). `getFirstModuleOccurance`, `getModuleMatching` et `getModulesByType` y sont `@Deprecated`, et MC n'utilise pas de clé typée.

Chez nous, 20 appels à `module(WorkerModule.class)` supposent un module unique. Les plus exposés :
- `CitizenViews:43`, pour les compétences du citoyen ;
- `CitizenAI:209`, pour la pluie ;
- `ColonySerializer:165`, qui ne répare qu'un seul module ;
- `HutActions:151,174,191`, où embauche et renvoi se font par hutte, sans identifiant de module ;
- `TownHallStats:27`.
- **Risque :** à l'école ou à la tour de garde, un citoyen affecté au 2ᵉ module obtiendrait les compétences, les règles de pluie et la réparation du 1er.
- **Quand :** plus tard, avant le premier bâtiment à plusieurs modules de travail (école, défense).
- **Correctif minimal :**
  - ajouter `Building.modules(Class<T>)` (une `List`) et `module(Class<T>, Predicate<T>)`, comme MC ;
  - ajouter `WorkerModule.of(Building, JobType)` et remplacer les appels qui partent d'un citoyen ;
  - pour une identité exacte, rendre `ModuleProducer` générique (`ModuleProducer<T>`, `building/ModuleProducer.java:6`) et le réutiliser comme clé typée : `building.module(DeliverymanHut.WORKER)` lit directement `modules.get(key)`. Aucun nouveau type n'est nécessaire.
- **Garde-fou :** un test ArchUnit (`callMethod`) interdit `Building.module(WorkerModule.class)` hors de `WorkerModule.of`.

**Coût par tick : faux problème.** Chaque appel crée un stream sur 1 à 5 modules. Le chemin fréquent, `CitizenAI.shouldWork` via `rainStopsWork`, tourne toutes les 10 à 20 ticks par citoyen. `BuildingManager.onColonyTick` (l. 91-97) utilise une boucle, sans stream, toutes les 500 ticks. Il n'y a rien à optimiser avant une mesure.

**Problème 3.2, `BuildingEventsModule` est du code mort.** *Résolu (commit 845dcba, ordre et condition corrigés par cf72db1) : `onRemoved` et `onUpgradeComplete` sont distribués ; `onPlaced` (absent de MC) et `onWakeUp` sont retirés.* Aucun appelant n'invoque `onPlaced`, `onRemoved`, `onUpgradeComplete` ni `onWakeUp` sur un module (vérifié par recherche). `WorkerModule` l'implémente (l. 17) sans rien redéfinir. À la place, `ColonyBuildingListener.removed` teste `instanceof WorkerModule` (`colony/ColonyBuildingListener.java:37-44`). MC, lui, distribue `IBuildingEventsModule` (`AbstractBuilding.java:256, 286, 432, 968`).
- **Risque :** chaque nouveau module qui réagit à la destruction ajoutera un `instanceof` de plus dans `colony`.
- **Quand :** maintenant, c'est bon marché.
- **Correctif :** passer `(Colony, Building)` dans les rappels (voir § 4), distribuer `onRemoved` depuis `ColonyBuildingListener` et `onUpgradeComplete` depuis `UpgradeCompletion`, puis déplacer la boucle de renvoi dans `WorkerModule.onRemoved`.
- **Garde-fou :** un test ArchUnit interdit à `..colony..` de dépendre d'une classe concrète de module (`WorkerModule` excepté pour les vues).

## 4. `TickingModule`

`TickingModule` (`building/TickingModule.java:7-12`) déclare deux méthodes. Les **deux seules implémentations** laissent vide la version à un argument : `WorkerModule.java:109-110` et `CourierAssignmentModule.java:55-56`. Seul `BuildingManagerTest.java:23` l'utilise.
- **Correctif :** ne garder qu'une méthode abstraite, `onColonyTick(Colony colony, Building building)`, qui correspond à MC `ITickingModule.onColonyTick(IColony)` (le module MC connaît déjà son bâtiment). Appliquer la même signature à `BuildingEventsModule`.
- **Quand :** maintenant, en même temps que le § 3.2. Le diff est de 3 fichiers.

## 5. Graphe de dépendances réel

Au niveau des familles, il y a **une seule composante fortement connexe** : `{colony, building, citizen, job, construction, logistics}`. `request` et `kernel` n'en font pas partie : `request` ne dépend que de `kernel`, et `kernel` de rien.

Arêtes des cycles (classe source → cible) :

| Cycle | Arêtes « montantes » | Classement |
|---|---|---|
| building ↔ colony (3/34) | `BuildingManager`, `TickingModule`, `CreatesResolvers` → `Colony` | légitime : des rappels vers l'agrégat |
| citizen ↔ colony (4/29) | `CitizenAI`, `CitizenManager`, `CitizenSpawned` → `Colony` ; `CitizenManager` → `ColonyContext` | légitime |
| job ↔ colony (3/16) | `Job`, `WorkerModule`, `HiringMode` → `Colony` | légitime |
| citizen ↔ job (4/7) | `CitizenData`→`Job`, `CitizenAI`→`JobAI`, `WorkerModule` ; `Job`/`JobType`/`JobXp`→`CitizenData` | légitime (MC `ICitizenData` ↔ `IJob`) |
| colony ↔ construction (33/36) | `Colony`→`WorkManager`, `ColonyEvents`→`WorkOrder`, `ColonyFoundation`→`UpgradeCompletion`, `ColonyPersistence`→`ClaimRadius`, `ConstructionPorts`→`BlueprintSource` ; en retour, 7 classes `wand`/`goggles` → `ColonyManager` | mixte, voir ci-dessous |
| colony ↔ logistics (6/15) | uniquement `colony.view.LogisticsViews`, `RequestViews` et `colony.action.LogisticsActions` | à surveiller : la couche applicative seulement |

Pour `colony ↔ construction` :
- `Colony → WorkManager` est légitime. MC range `WorkManager` dans `core.colony.workorders`, et c'est l'agrégat qui le possède.
- `ColonyPersistence → ClaimRadius` et `ConstructionPorts → BlueprintSource` sont à retirer (voir § 6).
- En sens inverse, `construction → Colony` est normal. En revanche, `construction.wand` et `goggles → ColonyManager` en font des « actions » applicatives rangées dans une fonctionnalité. Il faut l'accepter mais le borner.
- Aucune fonctionnalité ne dépend de `colony.action`, `colony.view` ou `colony.persistence`. La seule exception est `WandActions → colony.ui.WandView` : `colony.ui` est le paquet du port d'interface, donc c'est acceptable.

**Structure réelle :** `colony` (racine = agrégat) ⇄ fonctionnalités, avec `colony.{action,view,persistence}` au-dessus. La racine `colony` compte 14 fichiers, 1 de moins que la limite de 15.

Autres cycles relevés :
- `request` ↔ `request.resolver` : `RequestSerializer` → `resolver`, et 2 fichiers de `resolver` → la racine ;
- `colony` (racine : `ColonyManager`, `ColonyFoundation`) ↔ `colony.action`.

Couverture de `ArchitectureTest` (`core/src/test/java/dev/hycolony/core/ArchitectureTest.java`) :
- **couvert :** pas de `com.hypixel`, `kernel` isolé, `request` ↛ colony/building/construction/job/citizen, `job` ↛ construction, `building` ↛ construction, pas de cycle entre sous-paquets de `construction`, racine `construction` vide ;
- **non couvert :**
  - `building` ↛ job/logistics/citizen ;
  - `job` ↛ logistics ;
  - la racine `colony` ↛ logistics (vrai aujourd'hui) ;
  - fonctionnalités ↛ `colony.action`/`view`/`persistence` (vrai aujourd'hui) ;
  - pas de cycle entre sous-paquets de `logistics` (vrai : courier → pickup/warehouse seulement) ;
  - fonctionnalité métier ↛ `construction.builder` ;
  - profondeur d'héritage.

Tous ces cas sont vrais aujourd'hui. Il faut les **figer maintenant**, au coût d'une règle par ligne.

**Correction de l'audit précédent :** sa ligne « citizen → construction : 7 » est inversée. Il s'agit en réalité de `construction → citizen` (`BuilderAI`, `BuilderContext`… → `CitizenData`/`Skill`). `citizen` ne dépend pas de `construction`.

## 6. `ColonyContext`

`ColonyContext` (`colony/ColonyContext.java:18-32`) est un record immuable de 14 champs typés, dont `ConstructionPorts` qui en regroupe 6 (`colony/ConstructionPorts.java:25-31`). Il n'y a ni recherche par clé ni par type : **ce n'est pas un service locator au sens strict**. C'est un objet de contexte construit à la main.

La dérive existe pourtant. 36 classes appellent `context()`. Celles qui y puisent le plus de services différents :

| Classe | Services distincts via `context()` |
|---|---|
| `WandActions` | 6 |
| `HutActions` | 6 |
| `CitizenAI` | 6 |
| `CourierContext`, `BuilderContext` | 5 |
| `WorkManager`, `WandPlacement` | 4 |

`wand`/`goggles` enchaînent `manager.context().ports()` 10 fois (loi de Déméter). Des **modules** vont chercher des ports globaux : `WarehouseStorage` (3), `HutKeep`, `PickupRequests`, `WarehouseStockResolver`.

`BuilderContext.of` (`construction/builder/BuilderContext.java:46-58`) montre le bon modèle : il résout les ports une fois, au bord, puis les collaborateurs reçoivent des dépendances étroites.
- **Risque :** avec des dizaines de métiers, chaque classe voit tous les ports, les faux de test grossissent, et le graphe réel devient invisible.
- **Quand :** plus tard, sous forme de règle.
- **Correctif :** seuls les `*Context`, `*Actions`, les serializers et `Colony*` appellent `context()`. Les modules et les résolveurs reçoivent le port en paramètre.
- **Garde-fou :** `noClasses().that().implement(BuildingModule.class).should().callMethod(Colony.class, "context")` (ArchUnit `callMethod`).

**Problème 6.1, le nom de `ConstructionPorts` trompe et `unavailable()` est du code mort.**
- `ConstructionPorts` sert aussi la logistique (`containers`).
- `unavailable()` (l. 33-186, environ 150 lignes) n'a **aucun appelant** en `main` ni en test. Les seuls constructeurs sont `plugin/WorldRuntime.java:94` et `TestContexts.java:79`.
- Chaque méthode ajoutée à un port oblige à modifier cette 3ᵉ implémentation.
- **Quand :** maintenant.
- **Correctif :** supprimer `unavailable()`, puis renommer en `WorldPorts` au prochain passage.

## 7. Test d'extensibilité : le bûcheron

Référence : les 5 commits du courrier ont modifié à la main **38 fichiers préexistants** hors tests. Beaucoup étaient des ajouts **génériques** qui ne se paieront plus : `Job` (inactivité), `CitizenManager` (`tickInactivity`), `CitizenAI` (pluie), `WorkerModule` (`onRemoval`), `ColonySerializer`. Aujourd'hui, `CitizenAI`, `CitizenSerializer` (`JobRegistry.byId`, l. 75-85), `ColonySerializer` et `Job` n'ont **aucun switch par type** : un bûcheron ne les touche pas. Les clés de langue se déduisent de l'identifiant (`plugin/ui/ColonyPage.java:125-133`).

Fichiers qu'un `LumberjackJob` + `LumberjackAI` + hutte devrait modifier **aujourd'hui** :
1. `plugin/WorldRuntime.java:72-78` : 2 lignes `register`.
2. `plugin/block/HutBlockSystems.java:39-45` : la liste `HUT_TYPES`. Le commentaire « BuildingRegistry has no listing » est **périmé**, car `BuildingRegistry.all()` existe (`building/BuildingRegistry.java:22`).
3. `id-map.json`, `styles.json`, `hycolony.lang` en en-US et fr-FR.
4. Onglets propres (réglages de replantation, liste de pousses, zone) :
   - `colony/ui/BuildingView.java`, un `Optional<…Tabs>` de plus : il en a déjà 3 (l. `builder`, `warehouse`, `courier`) ;
   - `colony/view/BuildingViews.java` ;
   - `plugin/ui/BuildingPage.java:71-77, 116-122` ;
   - `Building.ui`.
5. Actions de réglage : `colony/action/HutActions.java`, sur le modèle de `setBuilderMode` (l. 202-208, où `colony.action` importe `BuilderSettingsModule`), ou `LogisticsActions` + un champ dans `ColonyManager` (l. 45).
6. Détection des arbres : `kernel/port/ItemCatalog`, `HytaleItemCatalog`, `FakeCatalog`, **et** `ConstructionPorts.unavailable()`.
7. Outils et dépôt : `BuilderStock`/`BuilderRequests` (§ 1.1), soit extraits, soit recopiés.
8. Autotest : `HyColonyCommand`, ou un `LumberjackSelfTest` sur le modèle de `LogisticsSelfTest`.

Autres tests de type : `LogisticsViews.java:82` (`m.job().equals(DeliverymanJob.TYPE)`) et les tests `TOWN_HALL`. Ces derniers sont légitimes, car MC traite aussi la mairie à part.

**Mécanisme d'enregistrement minimal (maintenant, avant le bûcheron) :**
- `JobType` (`record JobType(String id, Function<CitizenData, Job> factory)`) correspond déjà à `JobEntry`. Rien à ajouter tant qu'il n'y a pas de vue de job côté client.
- **Une liste d'enregistrement dans le cœur** : `CoreFeatures.register(JobRegistry, BuildingRegistry)` appelle `ConstructionBuildingTypes`, `WarehouseBuilding`, `DeliverymanHut`, puis le bûcheron. `WorldRuntime` ne l'appelle qu'une fois, et `HutBlockSystems` utilise `buildings.all()`, avec le registre construit avant `byBlockId`. On retire ainsi 3 lignes du plugin par métier.
- **Vues de modules** (équivalent de `ModuleProducer.viewProducer` dans MC) :
  - remplacer les `Optional<XTabs>` de `BuildingView` par `List<ModuleTab> tabs` ;
  - `ModuleTab` est une interface `sealed` qui permet `BuilderTabs`, `WarehouseTabs`, `CourierTabs`… ;
  - un module qui a une fenêtre implémente `ViewsModule { Optional<ModuleTab> view(Colony, Building, UUID) }` ;
  - `BuildingPage` fait un `switch` exhaustif (motifs sur types scellés), si bien qu'un onglet sans rendu **ne compile pas**. C'est le seul endroit où `sealed` rend un vrai service.
- **Garde-fou :** un test ArchUnit interdit à `..colony.view..` et `..colony.action..` d'importer une classe concrète de métier (`*Job`, `*SettingsModule`), sauf via `ViewsModule`.

## 8. Sous-plugins optionnels, façon Aetherhaven

Principe observé chez Aetherhaven (idées seulement, tous droits réservés) :
- **tout le code** est dans le jar unique ;
- une fonctionnalité est activée par un réglage serveur (`AetherhavenFeatures.isEnabledInServerConfig`) ;
- seuls les **assets** sont découpés en packs (`subplugin-packs/<Nom>/`, `asset-pack.json` + un `manifest.json` bouchon) ;
- ces packs sont enregistrés au `setup()` par `AssetModule.registerPack(name, path, manifest, PackSource.RUNTIME)`. Cette API existe bien en 0.6.8 : `build/vineflower/hytale-server/.../asset/AssetModule.java:418`, avec `unregisterPack` l. 471.

Pour HyColony :
- **Restent dans le cœur :** mairie, citoyens, requêtes, builder et construction (tout le reste en dépend), `kernel`, persistance. MC traite aussi le courrier comme obligatoire, puisque le builder reçoit ses livraisons par lui. **La logistique n'est donc pas optionnelle.**
- **Candidats optionnels :** une famille de métiers de production (bûcheron, mineur, fermier), la défense (gardes, casernes, raids), la recherche et l'université, et les styles décoratifs.
- **Packs d'assets seuls :** styles et prefabs, qui ne contiennent aucun code. C'est le cas le plus simple et le plus sûr, car il n'y a que `styles.json` à fusionner.
- **Packs de code + assets :** le code reste dans le jar et un drapeau de `config.json` (section `HyColony`, puisque c'est notre option) choisit si on appelle `register` de la fonctionnalité. Des jars Java séparés n'apportent rien et compliquent le chargement de classes.

Ce que le cœur doit avoir **avant** :
- le mécanisme du § 7 : un `register` par fonctionnalité appelé depuis une liste, et `ViewsModule`/`ModuleTab` pour les onglets. Il faut noter qu'une interface scellée ne peut pas être étendue par un pack : pour des packs, `ModuleTab` devient ouvert, avec un rendu enregistré par pack. On choisit l'un ou l'autre, et le choix fermé suffit tant que tout est dans le jar ;
- des fragments `id-map`/`styles` par pack, fusionnés au chargement ;
- des `.lang` par pack. Il reste à **vérifier** dans les sources décompilées que plusieurs packs peuvent fournir des clés d'un même fichier de langue ;
- les `Requestable` restent scellés (`request/model/Requestable.java:7`) : un pack ne peut pas ajouter de type de requête, ce qui est acceptable. `RequestSerializer.java:236` lève une exception sur un type inconnu : c'est sans danger tant que le type est fermé.

Tolérance des sauvegardes (CLAUDE.md § 5) quand un pack est désactivé :
- un bâtiment inconnu est conservé brut (`ColonySerializer.java:104-107`, `BuildingManager.keepUnknown`), et ses modules inconnus aussi (`BuildingSerializer.java:36, 69`). C'est bien ;
- **mais** un job inconnu est **perdu**. `CitizenSerializer.java:82-84` efface `workBuilding` et ne garde pas le JSON du job, puis la réparation (`ColonySerializer.java:155-170`) retire le citoyen des ouvriers. Réactiver le pack ne rend ni le job ni l'affectation ;
- une hutte inconnue n'est plus enregistrée : son bloc n'est plus protégé ni compté dans les revendications.
- **Correctif :** garder le job inconnu brut dans `CitizenData`, comme `unknownModules`, et ne pas « réparer » une affectation vers un bâtiment conservé brut. Test : une sauvegarde avec pack, chargée sans pack puis avec pack, reste identique.

**Quand :** plus tard pour les packs eux-mêmes. **Premier pas minimal maintenant :** `CoreFeatures.register` avec un drapeau par fonctionnalité, plus la conservation brute du job inconnu. Les deux sont utiles même sans pack.

## Faux problèmes à ignorer

- **`toolUses` dans `Job` :** déjà supprimé, ne subsiste que la migration.
- **`Job` porte-tout :** `actionsDone`, `working` et `onRemoval` sont communs dans MC.
- **Coût par tick de `Building.module` :** négligeable, pas de mesure qui le justifie.
- **Casser les cycles autour de `Colony` :** il s'agit de l'agrégat et de la racine de composition, fidèles à MC. On fige les frontières, on ne les supprime pas.
- **`ColonyContext` « service locator » :** c'est un record typé, pas une recherche dynamique. Le seul sujet est de limiter qui l'appelle.
- **`sealed` sur les requêtes :** ensemble fermé, c'est correct.
- **Un module Gradle ou JPMS par fonctionnalité :** inutile, ArchUnit suffit.
- **`ModuleKey<T>` nouveau :** si nécessaire, rendre `ModuleProducer` générique suffit.

## Priorités

1. Supprimer `ConstructionPorts.unavailable()`.
2. `TickingModule` à une seule méthode (`Colony`, `Building`) et distribution réelle de `BuildingEventsModule`.
3. Règles ArchUnit du § 5.
4. `CoreFeatures.register`, et `HutBlockSystems` sur `buildings.all()`.
5. Extraction de `WorkerStock`/`ToolRequests` avant le bûcheron.
6. `BuildingView.tabs` + `ViewsModule`.
7. Conservation brute du job inconnu.
8. Plus tard : `modules(Class)`, `module(Class, Predicate)` et `WorkerModule.of` avant l'école et la défense ; `JobTicker` au 3ᵉ métier.
