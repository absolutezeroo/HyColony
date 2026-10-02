# Remise à zéro du système de requêtes (`/mc colony requestsystem-reset`)

Recherche du 2026-10-02. Sources MineColonies : copie locale `sources/minecolonies/src/main/java/com/minecolonies/`
(abrégée `mc/` ci-dessous ; `version/main`, sans hash, voir CLAUDE.md § 6). Sources HyColony :
`core/src/main/java/dev/hycolony/core/` (abrégée `hc/`). Rien n'a été vérifié en jeu.

## 1. MineColonies

### 1.1 Les commandes

- `CommandRSReset implements IMCCommand` (`mc/core/commands/colonycommands/requestsystem/CommandRSReset.java:15`),
  nom `requestsystem-reset` (l. 45), argument `COLONYID_ARG` (l. 51-52), enregistrée sous `/mc colony`
  (`mc/core/commands/EntryPoint.java:42, 63`).
- Précondition héritée, non surchargée : `IMCCommand.checkPreCondition` = la source est un `Player` **ou** a le
  niveau de permission `OP_PERM_LEVEL = 4` (`mc/core/commands/commandTypes/IMCCommand.java:16, 87-90`). Toute
  exception de la commande est attrapée et journalisée (l. 62-79).
- Puis `onExecute` (`CommandRSReset.java:23-37`) :
  1. colonie introuvable : `ColonyIdArgument.getColony` envoie `COMMAND_COLONY_ID_NOT_FOUND` et lève
     (`mc/core/commands/arguments/ColonyIdArgument.java:103-116`) ;
  2. si la source n'est pas op 4 **et** que `canPlayerUseResetCommand` est faux : message
     `com.minecolonies.command.notenabledinconfig` (« This command is disabled in the config. »,
     `CommandTranslationConstants.java:15`, `manual_en_us.json:471`), envoyé par `sendSuccess`, retour 0 (l. 27-31) ;
  3. `colony.getRequestManager().reset()` (l. 33) ;
  4. `COMMAND_REQUEST_SYSTEM_RESET_SUCCESS` = `com.minecolonies.command.rsreset.success`
     (`CommandTranslationConstants.java:122`) : « The request system for colony %s has been restarted in 1.618
     seconds. » avec le nom de la colonie (`manual_en_us.json:437`), diffusé aux ops (`sendSuccess(..., true)`).
- **Aucune vérification d'appartenance à la colonie** : avec la config à `true`, n'importe quel joueur remet à
  zéro n'importe quelle colonie (aucun test de rang ni de `Action` dans `CommandRSReset`).
- `canPlayerUseResetCommand` : `ServerConfiguration.java:59` (champ), `:160` (`defineBoolean(...,
  "canplayeruseresetcommand", false)`), dans la catégorie `commands` (`swapToCategory(builder, "commands")`, l. 149).
  Déjà listé dans `docs/research/config-inventory.md:70`.
- `CommandRSResetAll implements IMCOPCommand` (`CommandRSResetAll.java:12`), nom `requestsystem-reset-all` (l. 37) :
  op 4, ou joueur op du serveur, sinon `COMMAND_REQUIRES_OP` (`IMCOPCommand.java:20-39`) ; remet à zéro toutes les
  colonies (l. 22-25), puis `com.minecolonies.command.rsresetall.success` : « The request systems for all colonies
  have been restarted in 1.618 seconds. » (`CommandTranslationConstants.java:124`, `manual_en_us.json:438`). Pas
  de clé de config.

### 1.2 `StandardRequestManager.reset()`

`mc/core/colony/requestsystem/management/manager/StandardRequestManager.java` :

- `reset()` (l. 412-416) → `reset(UpdateType.RESET)` (l. 418-424) : `setup()`, `version = -1`,
  `getUpdateHandler().handleUpdate(UpdateType.RESET)`. Le constructeur appelle aussi `reset()` (l. 117-122).
- `setup()` (l. 124-143) : **nouveau** `IDataStoreManager` (l. 126), cinq nouveaux stores (identités des requêtes,
  identités des résolveurs, provider → résolveurs, résolveur → requêtes, type → résolveurs ; l. 129-133), puis un
  **nouveau** `PlayerRequestResolver` et un **nouveau** `RetryingRequestResolver` enregistrés (l. 135-142). Toutes
  les requêtes, assignations et données des anciens stores sont abandonnées **sans aucun rappel** : ni
  `onRequestCancelled` au demandeur, ni `onAssignedRequestCancelled` au résolveur, ni changement d'état.
- Ni `reset` ni `setup` ne mettent `dirty` à vrai (la seule écriture du drapeau est `setDirty`, l. 196-221). Les
  `registerProvider` qui suivent marquent la **colonie** (`ProviderHandler.java:62`), pas le gestionnaire.

`UpdateHandler` (`mc/core/colony/requestsystem/management/handlers/UpdateHandler.java`) : quatre étapes
(l. 18-23), jouées dans l'ordre pour chaque version `> version courante` (l. 43-50), jamais côté client (l. 38-41).
Avec `version = -1`, toutes passent :

| Étape | Version | Effet avec `RESET` | Source |
|---|---|---|---|
| `InitialUpdate` | 0 | chaque bâtiment du serveur → `manager.onProviderAddedToColony(building)` | `InitialUpdate.java:11-27` (l. 26) |
| `ResetRSToStoreJobInResolvers` | 13 | rien (n'appelle `reset()` que sur `DATA_LOAD`) | `ResetRSToStoreJobInResolvers.java:13-26` |
| `ResetRSToUpdateRestaurantResolver` | 14 | rien (idem) | `ResetRSToUpdateRestaurantResolver.java:13-26` |
| `ResetRSToRemoveAssistantCookResolver` | 15 | rien (idem) | `ResetRSToRemoveAssistantCookResolver.java:13-26` |

Les trois dernières sont des migrations de sauvegarde (une vieille version déclenche un reset au chargement) : sans
équivalent nécessaire chez nous, `MigrationChain` jouant ce rôle.

Ré-enregistrement des huttes : `ProviderHandler.registerProvider` enregistre `provider.getResolvers()`
(`ProviderHandler.java:56-63`). Le provider étant inconnu du nouveau store, `getRegisteredResolvers` rend vide
(l. 38-47), donc `AbstractBuilding.getResolvers()` retombe sur `createResolvers()` : **de nouvelles instances** de
résolveurs, créées par les modules (`AbstractBuilding.java:1902-1935`). Seuls les bâtiments sont des providers
(`RegisteredStructureManager.java:600, 675` sont les seuls appels hors du système).

### 1.3 Ce que deviennent les jetons détenus ailleurs

Un jeton inconnu : `getRequestForToken` rend `null` (`StandardRequestManager.java:262-269` →
`RequestHandler.getRequestOrNull`, `RequestHandler.java:619-622`) ; `updateRequestState`, `assignRequest`,
`reassignRequest`, `getResolverForRequest`, `overruleRequest` passent par `RequestHandler.getRequest`, qui **lève
`IllegalArgumentException`** (`RequestHandler.java:602-610` ; appels `StandardRequestManager.java:232, 257, 282,
297, 348`).

Le point clé : `StandardDataStoreManager.get(id, type)` **crée un store vide** pour un identifiant inconnu
(`mc/core/colony/requestsystem/data/StandardDataStoreManager.java:40-57`). Tout ce qui garde ses jetons dans un
store du gestionnaire les perd donc d'un coup, sans plantage, en gardant son identifiant de store :

| Détenteur | Où sont les jetons | Après le reset | Source |
|---|---|---|---|
| Hutte : requêtes ouvertes par type, par citoyen, terminées par citoyen, citoyen par requête | store du gestionnaire (`rsDataStoreToken`) | vides | `AbstractBuilding.java:1299-1308, 1331-1355` |
| Artisan (`AbstractJobCrafter`) : file et tâches planifiées | store du gestionnaire | vides | `AbstractJobCrafter.java:84-93, 179-185` ; `IRequestSystemCrafterJobDataStore.java:18, 25` |
| Artisan : `progress`, `craftCounter`, `maxCraftingCount`, sorties secondaires | champs du métier | gardés | `AbstractJobCrafter.java:49-65` |
| Livreur (`JobDeliveryman`) : file et livraisons en cours | store du gestionnaire | vides | `JobDeliveryman.java:78-88, 144-147` ; `IRequestSystemDeliveryManJobDataStore.java:17, 23` |
| Entrepôt : file des livraisons (`WarehouseRequestQueueModule`) | NBT du module, **hors** gestionnaire | gardée, purgée paresseusement : `JobDeliveryman.getCurrentTask` retire les jetons dont la requête est `null` | `JobDeliveryman.java:239-246, 272-277` |
| Métier : `asyncRequests` | champ du métier | gardés en mémoire ; filtrés à la sauvegarde (`getRequestForToken != null`) | `AbstractJob.java:80, 193-197` ; `markRequestSync` tolère `null` (l. 254-265) |
| Interactions de citoyen (`RequestBasedInteraction`) | jeton dans l'interaction | invalides : le validateur `REQUEST_RESOLVER_NORMAL` appelle `requestChainNeedsPlayer`, faux pour une requête `null` | `RequestBasedInteraction.java:112-115` ; `InteractionValidatorInitializer.java:134-142` ; `RequestUtils.java:30-36` |
| Recettes (`AbstractCraftingBuildingModule.recipes`) | jetons du **gestionnaire de recettes global** | non concernés | `AbstractCraftingBuildingModule.java:92-97, 501` |
| Ressources du bâtisseur | aucune donnée de requête (`BuildingResourcesModule`, IA de structure : aucun `IToken`) | non concernées | grep `IToken` sans résultat |

Réaction des IA :

- **Ouvrier en attente d'une requête synchrone** : `checkIfNeedsItem` et `lookForRequests` lisent les listes du store
  de la hutte (`AbstractEntityAIBasic.java:267-272, 546-552`) ; vides, l'IA sort de l'attente
  (`afterRequestPickUp`). La redemande vient du prochain besoin : `checkIfRequestForItemExistOrCreate` compte
  d'abord l'inventaire et la hutte, puis crée une requête si aucune ouverte ou terminée ne correspond
  (`AbstractEntityAIBasic.java:1676-1720`). Les objets déjà livrés dans la hutte y restent et servent ce besoin.
  Rien ne redemande activement : la colonie redemande au rythme de chaque IA ou module (par exemple
  `MinimumStockModule`, l. 97-98 et 112-126).
- **Artisan** : `getCurrentTask` rend `null` sur une file vide (`AbstractJobCrafter.java:218-234`) ; l'IA garde un
  `currentRequest` (objet, pas jeton ; `AbstractEntityAICrafting.java:93`), mais `getNextCraftingState` rend `IDLE`
  et `craft` revient à `START_WORKING` (l. 298-317, 511-519). `afterDump` appelle `finishRequest`, sans effet sur
  une file vide (`AbstractJobCrafter.java:251-261` ; `AbstractEntityAICrafting.java:679-694`).
- **Livreur** : `decide` sans tâche retourne à l'entrepôt et vide son inventaire (`DUMPING`)
  (`EntityAIWorkDeliveryman.java:591-614`) ; chaque état relit `getCurrentTask` et redémarre si la tâche a changé
  (l. 148-152, 334-338, 475-479). `finishRequest` ne fait rien sur une file vide (`JobDeliveryman.java:324-329`).
- **Vues client** : `ColonyView` ne renvoie le gestionnaire que s'il est `dirty` ou à un nouvel abonné
  (`ColonyView.java:323-329`). Le reset ne le marque pas : le client reçoit l'état vide au premier `createRequest`
  (`StandardRequestManager.java:188`), c'est-à-dire dès qu'une IA redemande. Les vues de hutte, d'artisan et de
  livreur lisent leurs stores dans le gestionnaire de la vue (`AbstractBuildingView.java:478`,
  `CrafterJobView.java:47`, `DmanJobView.java:47`), vides à leur tour.

En résumé, MC remet à zéro **en silence** : il jette tout et compte sur des lecteurs qui tolèrent `null` ou une
liste vide. Aucun chemin vérifié ne lève après un reset, car les seuls appels qui lèvent (`updateRequestState`…)
partent de files déjà vidées. Pas d'attente infinie, mais du travail perdu : les livraisons en cours, les crafts
partiels et les requêtes au joueur disparaissent.

## 2. HyColony aujourd'hui

### 2.1 Le gestionnaire

- `RequestManager` (`hc/request/RequestManager.java`) : `store`, `resolvers`, `queue`, `canceller`, `assigner`,
  `transitions` sont `final` et construits une fois (l. 31-45) ; **pas de `reset`** (déjà noté dans
  `debug-mod.md:247` et `hycolony-api.md:205`).
- Enregistrement : `registerBuiltIn` (l. 47-50), appelé par `Colony` pour `new PlayerResolver(center)` et
  `new RetryingResolver(center)` (`hc/colony/Colony.java:66-70`), qui pose aussi le `RequestStatePoster` comme
  écouteur d'état (l. 70). Providers : `ColonyBuildingListener.added` attache les résolveurs de la hutte
  (`Building.attachResolvers`, `hc/building/Building.java:158-164`, liste mise en cache dans `resolvers()`, l. 217)
  puis `onProviderAdded` (`ColonyBuildingListener.java:23-35`).
- `ResolverRegistry.register` et `addProvider` lèvent sur un identifiant déjà connu (`ResolverRegistry.java:38, 52,
  72`) : ré-enregistrer sans vider le registre lèverait.
- État des résolveurs : seuls `PlayerResolver` (`open`, `announced` ; `PlayerResolver.java:29, 31`) et
  `RetryingResolver` (`delays`, `tries` ; `RetryingResolver.java:37, 39`) gardent des jetons. Les résolveurs de
  hutte n'ont aucun champ de jeton (grep `Map|Set|List<` dans `building/BuildingResolver`, `crafting/request/*`,
  `logistics/warehouse/WarehouseStockResolver`).
- Jeton inconnu : `get` rend `Optional.empty()` (l. 208-210) ; `createChild`, `assign`, `reassign`, `updateState`,
  `overrule`, `makeSync`, `addDelivery` passent par `RequestStore.require`, qui **lève
  `IllegalArgumentException`** (`RequestStore.java:69-75`), comme MC.
- Les requêtes ouvertes d'une hutte ne sont pas stockées ailleurs : elles se lisent dans le store par demandeur
  (`RequestStore.byRequester`, l. 131-133). `SyncRequests.pending`/`mine` (`hc/job/work/SyncRequests.java:37-50`),
  `BuilderRequests`, `FarmWork`, `FuelRequests`, `MenuRequests`, `PickupRequests` relisent toutes
  `byRequester` ; l'asynchronie est un drapeau de la requête (`RequestManager.java:74-79`). Vider le store reproduit
  donc exactement l'effet MC sur les huttes.
- Persistance : `RequestSerializer.write/read` dans la sauvegarde de la colonie (`ColonySerializer.java:52, 103`).
- Aides existantes : `cancelOrphans` (`RequestManager.java:164-168` → `RequestCanceller.java:61-71`) annule, avec
  rappels, les racines dont le demandeur n'existe plus (appelé au chargement, `ColonySerializer.java:232`) ;
  `CraftingHeal.heal` (package-private, `app/persistence/CraftingHeal.java:27-58`) retire au chargement les tâches
  d'artisan dont la requête a disparu.

### 2.2 Les détenteurs de jetons hors de `request/`

| Classe | Ce qu'elle garde | Jeton inconnu aujourd'hui | Bloque ? |
|---|---|---|---|
| `crafting/task/CraftingTasks` (l. 43-44, sauvegardé avec le métier, l. 157-199) | file et tâches planifiées de l'artisan (MC les met dans un store) | `currentTask` → `TaskQueues.head` dépile les têtes mortes (l. 69) ; `finishRequest` dépile (l. 77-88) ; `cancelAll` saute (l. 110-122) | non, mais une tâche **planifiée** morte reste et compte dans `load()` (l. 61-63) : seul `CraftingHeal` la retire, au chargement |
| `job/TaskQueues` (l. 17-27) | rien (outil) | dépile les têtes inconnues, marque la colonie | non |
| `job/JobAI.queue()` (l. 47) | vue de la file | lecture seule | non |
| `crafting/job/CraftingWork` (l. 44) | `currentRequest` (objet `Request`) | `isLive` compare à la tête vivante (l. 236-240), sinon `abandon()` (l. 247) | non |
| `crafting/task/Crafters`, `CrafterTaskListModule` | rien ; lisent `CraftingTasks` | lecture | non |
| `crafting/module/RecipeReservations` (`Pending`, l. 37) | calculé à la volée | `task` ignore un jeton inconnu (l. 121-130) | non |
| `logistics/courier/DeliverymanJob` (l. 38-39, sauvegardé, l. 169-183) | file et `ongoing` (MC les met dans un store) | `ownTask` → `TaskQueues.head` ; `cancelAssignedRequests` saute (l. 158-166) | file : non. `ongoing` mort : purgé à la fin de la livraison suivante (`CourierTasks.finish`, l. 26-52, qui itère `ongoing` et ignore l'inconnu) |
| `logistics/courier/CourierTasks`, `CourierTaskPicker` | `shared`/`toRemove` temporaires | `pull` retire les jetons inconnus de la file d'entrepôt (`CourierTaskPicker.java:60-67`) | non |
| `logistics/courier/DeliveryPreparation` (l. 27) | `walkingFor` | seulement comparé à la tâche courante (l. 67-73) | non |
| `logistics/warehouse/WarehouseRequestQueue` (l. 16, sauvegardé) | file de l'entrepôt (MC aussi la garde hors store) | purgée par `pull` ; l'onglet filtre (`TaskRows.java:24`) | non, mais `CourierResolver` compte `queue().size()` dans son score (`CourierResolver.java:102`) tant qu'un livreur n'a pas tiré |
| `logistics/warehouse/CourierResolver`, `CourierTaskQueue`, `TaskRow(s)` | rien | `onCancelled` retire des files (l. 110-118) ; `TaskRows` filtre | non |
| `app/action/RequestActions` | jeton venu d'une fenêtre | `fulfil`/`cancel` testent `get` d'abord et rendent `false` (l. 49-60, 94-111, 115-120) | non |
| `app/persistence/CraftingHeal` | rien | voir § 2.1 | — |
| `app/diagnostics/CitizenInvariants` (l. 133-157) | rien | signale `queueHeadGone` | non (diagnostic) |
| `app/view/RequestViews`, `app/ui/RequestsView`, `app/api/ApiSnapshots`, `ApiDebugSnapshots` | rien | dérivés du gestionnaire | non |
| `construction/builder/*`, `job/work/SyncRequests`, `ToolRequests`, `FarmWork`, `FuelRequests`, `MenuRequests`, `PickupRequests` | aucun jeton gardé | relisent `byRequester` | non |

Aucun détenteur ne lève ni ne bloque pour toujours sur un jeton inconnu. Le seul reste durable serait une tâche
planifiée morte dans `CraftingTasks.assigned`, et des entrées mortes dans les files d'entrepôt sans livreur.

## 3. Portage recommandé

Reproduire la remise à zéro **silencieuse** de MC : ne pas passer par `cancel` ni `cancelOrphans`. Leurs rappels
(`onCancelled`, `onRequestCancelled`) ne sont pas joués par MC ; ils relanceraient des requêtes au milieu du reset
et dépendraient des résolveurs mêmes qu'on veut remettre à zéro.

1. **`RequestManager.reset()`** (cœur, passé par l'`OperationQueue` comme toute mutation) : vider le `RequestStore`
   (requêtes, `resolverOf`, `assigned`, `byRequester`) **en gardant ses deux écouteurs** (le `RequestStatePoster` et
   HyLens), et vider le `ResolverRegistry` (y compris `providers` et `beingRemoved`). Vider plutôt que remplacer :
   `RequestCanceller`, `RequestAssigner` et `RequestTransitions` gardent une référence au store et au registre.
2. **Côté colonie** (l'équivalent de `setup()` puis `InitialUpdate`) : `registerBuiltIn(new PlayerResolver(center))`
   et `new RetryingResolver(center)` (instances neuves, comme MC l. 135-142 ; elles vident `open`, `announced`,
   `delays`, `tries`), puis `onProviderAdded(building)` pour chaque hutte. Réutiliser `building.resolvers()` suffit,
   ils ne gardent pas d'état ; rappeler `ColonyBuildingListener.added` les recréerait comme `createResolvers`.
3. **Vider ce que MC garde dans ses stores et nous sur les métiers** (sinon écart) : pour chaque citoyen, la file et
   les tâches planifiées de `CraftingTasks` (qui restent sinon dans `load()`), la file et `ongoing` de
   `DeliverymanJob`. Ne **pas** toucher aux compteurs d'artisan ni aux sorties secondaires (champs du métier chez
   MC), ni à `WarehouseRequestQueue` (MC la garde et la purge paresseusement). Vider aussi la file d'entrepôt
   serait un écart volontaire, justifiable (score de `CourierResolver`, entrepôt sans livreur), à documenter.
4. **`colony.markDirty()`** pour réécrire la sauvegarde. Nos fenêtres relisent le cœur à chaque affichage : pas de
   synchronisation de vue à porter.
5. **Rien d'autre à notifier** : les ouvriers sortent seuls de `NEEDS_ITEM` (`SyncRequests.pending` faux) et
   redemandent à leur prochain besoin ; artisans et livreurs retombent sur `START_WORKING` par `TaskQueues.head`.
   C'est le comportement MC (§ 1.3).
6. **Commandes** : `requestsystem-reset <colonie>` et `requestsystem-reset-all`, sur le fil du monde. Il faut
   l'op (niveau 4 chez MC) ou `Commands.canPlayerUseResetCommand` (défaut `false`, composante à ajouter à
   `ColonyConfig.Commands`, `hc/kernel/config/ColonyConfig.java:73-77`). `-all` est réservé à l'op. Messages en
   `en-US`/`fr-FR` : succès avec le nom de la colonie, « désactivée dans la config ». Pas de contrôle de rang, comme
   MC.

Écarts imposés ou à trancher :

- **« in 1.618 seconds »** : durée fictive codée dans le texte MC. La reprendre telle quelle est fidèle ; c'est une
  blague, pas une mesure.
- **Événements `RequestStateChanged`** (API, `RequestStatePoster`) : un reset silencieux n'en émet aucun. Un
  observateur (HyLens) garderait alors des requêtes ouvertes fantômes. MC n'a pas d'événement : un événement
  « reset » dédié serait un ajout d'API, avec `@since`, `apiDump` et une décision de l'utilisateur.
- **Version/`UpdateHandler`** : rien à porter, `MigrationChain` couvre les resets de migration (`DATA_LOAD`).
- Le reset jette les livraisons en cours. Le livreur dépose ce qu'il porte à l'entrepôt : fidèle chez MC
  (`DUMPING`) ; chez nous à confirmer **[in-game]**. Les crafts partiels et les requêtes au joueur sont perdus,
  comme chez MC.
