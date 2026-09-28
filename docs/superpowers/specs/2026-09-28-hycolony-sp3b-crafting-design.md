# HyColony SP3b-1 : fabrication (socle des artisans)

Conception validée avec l'utilisateur le 2026-09-28. Source MineColonies `version/main`, commit `6b4e03e337fcb886b4ab8cbe21f9b6731818769d` (`github.com/ldtteam/minecolonies`). Recherches liées : `docs/research/sp3b-mc-farmer.md`, `docs/research/sp3b-hytale-farming.md`, `docs/research/prefab-obtainability.md` (modèle des recettes Hytale).

## Objectif

Les huttes apprennent des recettes Hytale et leurs artisans les fabriquent pour la colonie, par le système de requêtes, comme dans MineColonies. C'est le socle de tous les métiers MC qui héritent d'`AbstractJobCrafter` (fermier, bûcheron, planteur, scieur, forgeron…). Le fermier (SP3b-2) vient juste après et sera le premier métier construit dessus : il fabrique ses graines avec l'essence de vie qu'il récolte.

## Portée

- **Dedans :**
  - recettes Hytale lues par un port ;
  - tables de la hutte lues dans son plan, avec leur niveau ;
  - coût de construction des tables de niveau N ;
  - module de fabrication de la hutte : recettes apprises, désactivées et ordonnées, maximum, compatibilité, recettes « maison », amélioration ;
  - requêtes `Crafting` publique et privée, et leurs résolveurs ;
  - `StackList` (requête « l'un de ces objets ») pour les ingrédients par type de ressource ou tag ;
  - `CrafterJob` et `CraftingAI`, bases abstraites ;
  - onglet Recettes de la fenêtre de hutte ;
  - fichier `hycolony/crafting.json` ;
  - persistance et migration.
- **Hors portée :**
  - tables « processing » de Hytale (four, tannerie, feu de camp) : elles correspondent au four de MC (`AbstractEntityAIUsesFurnace`, `AbstractEntityAIRequestSmelter`), qui viendra avec le fondeur et le cuisinier ;
  - effets de recherche (université absente) : force 0 partout ;
  - réglage `RECIPE_MODE` (verrouillé par la recherche dans MC) : toujours le mode `PRIORITY` ;
  - vrais schémas des huttes d'artisans (voir « Test ») ;
  - statistiques de hutte (`StatsUtil`) ;
  - aucun métier concret : le premier sera le fermier (SP3b-2).

## Règles de jeu

Toutes reprises de MineColonies sauf les écarts listés plus bas. Chaque classe portée cite sa source MC dans sa Javadoc.

### Recettes (MC `RecipeStorage`, `IRecipeManager`)

- Une recette HyColony (`Recipe`, record) contient :
  - `RecipeId id` ;
  - `List<Ingredient> inputs` ;
  - `ItemAmount primaryOutput` ;
  - `List<ItemAmount> secondaryOutputs` : les autres sorties Hytale, par exemple un seau rendu ;
  - `BenchRequirement bench` : `benchId`, `categories`, `requiredTier`. Pour une recette sans table, `benchId` vaut `Fieldcraft` : elle se fait à la main et garde ses catégories ;
  - `Optional<ToolType> requiredTool` ;
  - `RecipeSource source` : `HYTALE(recipeId Hytale)`, `CUSTOM(id)` ou `IMPROVED`.
- `Ingredient` vaut l'un de :
  - `ItemKey`, un objet précis ;
  - `ResourceType(id)`, n'importe quel objet qui a ce type de ressource Hytale (« tronc de bois ») ;
  - `ItemTag(id)`.

  Chacun a une quantité. `cleanedInput` regroupe les ingrédients identiques (MC `getCleanedInput`).
- **`RecipeRegistry`** (MC `StandardRecipeManager`) est propre à la colonie et persisté.
  - Il associe `RecipeId` à `Recipe`. Une recette Hytale a pour identifiant `hytale:<id Hytale>`.
  - `checkOrAdd(recipe)` renvoie l'identifiant existant pour une recette égale, sinon en crée un. Une recette maison reçoit `custom:<id>`, une recette améliorée `improved:<n>`, avec `n` croissant.
  - « Égale » suit MC `RecipeStorage.equals` : mêmes entrées, sorties, table et outil, **et même `recipeSource`**. Dans MC, une recette maison a pour source son identifiant, une recette apprise à la main ou améliorée n'en a pas. Une recette maison ne partage donc jamais l'identifiant d'une recette apprise de même contenu (`checkForWorkerSpecificRecipes` en dépend), alors qu'une recette apprise et une recette améliorée de même contenu le partagent.
  - Une recette Hytale est sauvegardée par son seul identifiant et relue dans le catalogue ; une recette maison ou améliorée est sauvegardée en entier.
  - Au chargement, une recette Hytale disparue du catalogue (mise à jour du jeu) est retirée des modules qui l'ont apprise (`ColonySerializer.heal`).
- **Port `RecipeCatalog`** (plugin `HytaleRecipeCatalog`) :
  - `all()` : les recettes Hytale `Crafting` et `Fieldcraft`. Les recettes `Processing`, `DiagramCrafting` et `StructuralCrafting` sont exclues (hors portée) ;
  - `byId(String)` ;
  - `itemsOf(ResourceType | ItemTag)` : les objets qui correspondent ;
  - `benchUpgradeCost(benchBlockId, fromTier, toTier)` : la somme des `UpgradeRequirement` de Hytale ;
  - `playerKnows(playerId, recipeId)` : pour `KnowledgeRequired`.

  Comme tout port, il ne lève jamais d'exception : entrée inconnue = vide.

### Tables de la hutte (MC `AbstractBuilding.registerBlockPosition`)

- `BlueprintEntry` gagne un champ `Optional<Workstation> workstation`. `Workstation(benchId, tier)` est lu par `PrefabCells` dans le composant `BenchBlock.TierLevel` du prefab, et vaut 1 si le composant manque. Seules les tables `Crafting` comptent ; une table `Processing` est ignorée ici.
- Quand le constructeur pose une case de plan avec `workstation`, la hutte l'enregistre avec sa position, son type et son niveau (`Building.addWorkstation`, persisté), comme `addContainer` pour les coffres. Le plugin pose la table avec son niveau : composant `BenchBlock.TierLevel` et état de bloc `Tier<N>`.
- Une table posée ou montée de niveau par un joueur **n'est jamais enregistrée**. Une table enregistrée qui est cassée est retirée (`removeWorkstation`). Une amélioration de la hutte réenregistre les tables du nouveau plan.
- **Coût** (ajout à MC, écart documenté) : pour une case `workstation` de niveau N, les ressources du chantier comptent l'objet de la table, plus `benchUpgradeCost(bench, 1, N)`. Le constructeur les demande comme tout autre matériau.

### Module de fabrication (MC `AbstractCraftingBuildingModule`)

- **État :** `recipes` (liste ordonnée de `RecipeId`) et `disabledRecipes` (ensemble).
- **Maximum :** `getMaxRecipes() = (int) (2^niveau × (1 + effet RECIPES = 0) × (canLearnManyRecipes ? 5 : 1))`.
  - `canLearnManyRecipes` est vrai par défaut et faux pour un module « simple » (MC `SimpleCraftingModule`).
  - Seules les recettes actives **et apprises à la main** comptent : une recette « maison » ne compte pas (`getActiveRecipes`).
- **Compatibilité** (`isRecipeCompatible`) : la recette est compatible si les trois conditions suivantes sont vraies.
  1. Si elle a une table, la hutte a une table enregistrée avec le même `benchId`, des catégories qui contiennent celles de la recette, et un niveau ≥ `requiredTier`. Une recette sans table est toujours apprenable, comme la grille 2×2 de MC.
  2. Elle passe le filtre du métier dans `crafting.json`.
  3. Si `KnowledgeRequired`, le joueur qui l'apprend la connaît (voir les écarts).
- **Apprendre** (`addRecipe`) : si c'est compatible et qu'il reste de la place, la recette est ajoutée en fin de liste et marquée à réécrire, puis on réveille les requêtes de la colonie que sa sortie peut servir (`handleRecipeUpdate` = `onColonyUpdate` sur les `Deliverable` qui correspondent).
- **Retirer, activer/désactiver, réordonner :**
  - `removeRecipe`, `toggle` et `switchOrder(i, j, fullMove)` suivent MC à l'identique ;
  - `fullMove` envoie en tête (si `i > j`) ou en queue ;
  - réactiver une recette déclenche aussi `handleRecipeUpdate`.
- **Recettes « maison »** (MC `CustomRecipe` et `checkForWorkerSpecificRecipes`, à chaque tick de colonie) :
  - `crafting.json` en déclare, par métier, avec `minBuildingLevel` et `maxBuildingLevel` ;
  - une recette valide pour la hutte est ajoutée si elle manque ;
  - une recette qui ne l'est plus est retirée ;
  - la règle MC du doublon « amélioré » s'applique : même sortie et mêmes objets d'entrée, quelles que soient leurs quantités, font un doublon. Devant un doublon, la recette maison n'est pas ajoutée, qu'il s'agisse de sa version améliorée ou d'une recette apprise à la main ; seule une recette maison de même source dont le contenu a changé est remplacée.
- **Choix d'une recette** (`getFirstRecipe`) : la première recette active dont la sortie principale correspond, dans l'ordre de la liste. MC compare aussi les sorties « alternatives » de ses recettes à sorties multiples, dont une seule sort à chaque exécution ; Hytale n'en a pas. Une sortie secondaire (un seau rendu) n'est jamais comparée, comme dans MC.
- **Recette réalisable** (`getFirstFulfillableRecipe(pred, count, considerReservation)`) : il faut assez d'ingrédients dans l'inventaire des employés et les conteneurs de la hutte (`canFullFillRecipe`). Un outil ou une sortie secondaire utilisée comme ingrédient ne compte qu'une fois, pas `× qty`.
- **Réservations** (`reservedStacksExcluding`) : ce sont les ingrédients des tâches en file et assignées de ses artisans. Ils rejoignent le « à garder » de la hutte (`getRequiredItemsAndAmount`), ingrédients **et** sortie, pour que les livreurs ne les emportent pas.
- **Amélioration** (`improveRecipe(recipe, count, citizen)`, après chaque tâche terminée) :
  - chance = `min(5,0 ; 0,0625 × count + 0,0625 × niveau de la compétence d'amélioration)` en pourcentage, contre un tirage `random × 100` ;
  - si la sortie n'est pas exclue, chaque ingrédient de quantité > 1 marqué « réductible » (`crafting.json`) perd 1 ;
  - la recette améliorée remplace l'ancienne à la même place, si la hutte peut la tenir (`isRecipeCompatibleWithCraftingModule`) ;
  - un message aléatoire parmi 3 est envoyé à la colonie (`RECIPE_IMPROVED` + 0..2).

### Requêtes et résolveurs

- **`Crafting(ItemKey stack, int count, int minCount, String recipeId, boolean isPublic)`** : un seul record pour `PublicCrafting` et `PrivateCrafting`. `count` est un nombre d'**exécutions** de la recette. L'égalité suit MC : `count`, `minCount` et `stack`, sans la recette.
- **`StackList(List<ItemKey> accepted, String description, int count, int minCount)`** (MC `StackList`) : un `Deliverable` qui accepte l'un des objets. Il sert aux ingrédients par type de ressource ou tag, que `description` nomme. L'égalité suit MC : les mêmes objets acceptés, dans n'importe quel ordre, sans les quantités ni la description.
- **Résolveur de fabrication**, public et privé (MC `PublicWorkerCraftingRequestResolver` et `PrivateWorkerCraftingRequestResolver`). Les deux sont créés par le module de fabrication. MC crée les privés dans chaque `WorkerBuildingModule`, mais `job` ne doit pas dépendre de `crafting`. Écart sans effet : les recettes sans table viennent de toute façon d'un module de fabrication.
  Le résolveur :
  - priorité **125** (MC `CONST_CRAFTING_RESOLVER_PRIORITY`) ;
  - gère les `Deliverable` ;
  - `canResolve` : le public accepte tout demandeur, le privé seulement sa propre hutte. Il faut en plus :
    - hutte de niveau > 0 ;
    - un employé pour ce métier ;
    - pas de cycle (`createsCraftingCycle`, profondeur max **20**) ;
    - une recette qui produit l'objet. Le privé n'accepte que les recettes **sans table**, et pour une demande de nourriture, une recette de four est refusée (sans objet ici) ;
    - pour chaque ingrédient, pas de cycle ;
  - `attemptResolve` : découpe en lots. `maxSlots = 27 − 27 % 8 = 24` (effet de recherche `CITIZEN_INV_SLOTS` = 0), et le calcul MC de `batchSize` est repris. Il renvoie une `Crafting` enfant par lot (`count = min(batch, reste)`, `minCount = max(1, min(batch, minReste))`) ;
  - `suitability` : la distance entre le demandeur et la hutte.
- **Résolveur de production**, public et privé (MC `AbstractCraftingProductionResolver`) :
  - ne gère que les `Crafting` de sa propre hutte, avec le même `isPublic` ;
  - priorité 100 (MC `CONST_DEFAULT_RESOLVER_PRIORITY`), adéquation 0 ;
  - `attemptResolve` :
    - aucun module de la hutte ne tient la recette de la tâche (`getCraftingModuleForRecipe`), ou, pour le public, la hutte n'a pas d'artisan de ce métier (`canBuildingCraftStack`) : il ne la prend pas. La tâche attend alors un autre résolveur (le joueur), sans jamais être annulée puis redemandée en boucle ;
    - si la recette est déjà réalisable, réservations des autres tâches déduites (`considerReservation`), il n'y a pas d'enfant ;
    - sinon, un enfant par ingrédient de la première recette du module pour cette sortie : `StackRequest`, ou `StackList` pour un type ou un tag, pour `amount × count` (`minCount = amount × minCount`), ou `amount` seulement pour un outil ou une sortie secondaire ;
  - public, à l'assignation : la tâche va à l'artisan de la hutte le moins chargé (file + assignées, le premier à égalité, MC `onAssignedToThisResolverForBuilding`). Sans artisan, rien (MC appelle son `onAssignedRequestBeingCancelled`, vide) ;
  - public, `resolve` : la tâche passe de « assignée » à la file (`onTaskBeingResolved`). La requête reste en cours tant que l'artisan ne l'a pas finie. Si aucun artisan ne la tient plus, elle est annulée : son parent est réassigné ;
  - privé, `resolve` : fabrique **tout de suite** `count` fois dans la hutte (`fullFillRecipe` : ses conteneurs puis l'inventaire de ses employés, MC `getHandlers`), puis RESOLVED ; FAILED si la recette ou le module qui la tient a disparu (MC `PrivateWorkerCraftingProductionResolver`) ;
  - suites (`followups`) du public : si le parent vient d'une autre hutte, une `Delivery(hutte → demandeur du parent, priorité 13)` par pile livrée. Rien s'il vient de la même hutte ;
  - annulation ou complétion : la tâche est retirée de la file de l'artisan (`onTaskDeletion`).

### Artisan (MC `AbstractJobCrafter`, `AbstractEntityAICrafting`)

- **Composition, pas d'héritage.** `ArchitectureTest` interdit d'hériter d'un sous-type de `Job` ou de `JobAI`. Le socle MC (`AbstractJobCrafter`, `AbstractEntityAICrafting`) devient donc un ensemble de composants qu'un métier concret possède :
  - `CraftingTasks` : l'état ;
  - l'interface `Crafter { CraftingTasks craftingTasks(); }`, qui n'est pas un sous-type de `Job` ;
  - `CraftingWork` : les étapes de l'IA, qui renvoient un `CraftingStep`.
- **`CraftingTasks`** : `taskQueue` (liste de jetons), `assignedTasks`, `maxCraftingCount`, `craftCounter`, `progress` et `secondaryOutputs` (objet → quantité), tous persistés.
  - Les méthodes suivent MC : `currentTask` (retire en tête les jetons morts), `finishRequest(ok)`, `onTaskBeingScheduled`, `onTaskBeingResolved` et `onTaskDeletion`.
  - Au retrait de l'employé, toutes ses tâches passent en FAILED.
  - Bug MC corrigé : `deserializeNBT` relit `maxCraftingCount` et `craftCounter` dans `progress`. On lit chaque clé dans son champ (écart documenté).
- **`CraftingWork`** (composant de l'IA) : chaque méthode d'étape renvoie un `CraftingStep` (`IDLE`, `START_WORKING`, `GET_RECIPE`, `QUERY_ITEMS`, `GATHERING_REQUIRED_MATERIALS`, `CRAFT`, `INVENTORY_FULL`, `NEEDS_ITEM`). L'IA concrète l'associe à ses propres états et à ses délais. Délais en ticks, `STANDARD_DELAY = 5`, `HIT_DELAY = 10`, `TICKS_SECOND = 20`. Le modèle est l'IA de test `TestCrafterAI`, que le fermier suivra : les cibles de MC `AbstractEntityAICrafting`, plus celles de `AbstractEntityAIBasic` pour le vidage (`INVENTORY_FULL` toutes les 20 ticks, déclenché toutes les 100 ticks) et l'attente d'une requête (`NEEDS_ITEM` toutes les 40 ticks, déclenché toutes les 20 ticks).
  - `IDLE` → `START_WORKING` si `hasWorkToDo` (vérifié toutes les 20 ticks). Sinon, sans rien à vider, l'artisan laisse la main à la flânerie du citoyen (`canGoIdle`). Les places assises et debout de MC sont hors portée.
  - `START_WORKING` (`decide`, toutes les 5 ticks) :
    - aller à la hutte ;
    - si `actionsDone ≥ getActionsDoneUntilDumping()` (1 par défaut), attendre le vidage ;
    - sinon `getNextCraftingState` : `INVENTORY_FULL` si plus de 3 cases étrangères à la recette (une seule fois par recette), puis `QUERY_ITEMS` si une recette est en cours, sinon `GET_RECIPE`.
  - `GET_RECIPE` : à reprendre ligne à ligne de MC.
    - Il n'y a pas de module ou pas de recette réalisable → `finishRequest(false)`, puis `START_WORKING`.
    - L'outil requis manque, dans l'inventaire comme dans la hutte → la requête d'outil passe par `ToolRequests`, puis `finishRequest(false)`. Cette requête est celle du citoyen : l'artisan l'attend en `NEEDS_ITEM`, puis prend l'outil livré à la hutte (`SyncRequests`, partagé avec le constructeur).
    - Sinon, calcul de `maxCraftingCount` et `craftCounter` à partir de ce que l'artisan a déjà et de ce qui est disponible, puis `QUERY_ITEMS`.
  - `QUERY_ITEMS` (`checkForItems`) :
    - un ingrédient manque dans l'inventaire mais se trouve dans la hutte → `GATHERING_REQUIRED_MATERIALS` (le transfert existe déjà dans `WorkerStock`) ;
    - il ne se trouve nulle part → on oublie la recette, puis `GET_RECIPE` ;
    - sinon → `CRAFT`.
  - `CRAFT` (toutes les 10 ticks) :
    - aller à la table de la recette (la première table enregistrée qui correspond), ou au bloc de hutte pour une recette sans table ;
    - `progress + 1` et un coup sur le bloc (l'outil ou un ingrédient en main, animation, particules et son par `WorldEffects`) ;
    - si la requête a été annulée ou a échoué, ou n'est plus en tête de file, on abandonne : un vidage suit ;
    - à `progress ≥ 10 / min(compétence de vitesse / 2 + 1, 50) × 3` (division entière, comme MC), la recette est exécutée.
  - **Exécution** (`executeCraftingAction`) :
    - les ingrédients sont consommés dans l'inventaire de l'artisan ;
    - les sorties y sont ajoutées : la principale va aux `deliveries` de la requête, les secondaires à `secondaryOutputs` ;
    - `craftCounter + 1`, et l'outil perd 1 de durabilité ;
    - si `craftCounter ≥ maxCraftingCount` : `incrementActionsDone(1)`, amélioration de la recette, `count / 2` d'expérience (MC `finalizeCraftingTask`), puis `INVENTORY_FULL` ;
    - sinon, si l'outil s'est cassé : échec ;
    - sinon, `progress = 0`, puis `GET_RECIPE`.
  - **Après le vidage** (`afterDump`) :
    - si les compteurs sont à 0 avec une requête en cours, la requête passe en `finishRequest(true)`, et l'artisan gagne encore `count / 2` d'expérience (comme MC, deux fois par tâche) ;
    - chaque sortie secondaire accumulée part à l'entrepôt le plus proche : une `Delivery` par pile, priorité `MAX_BUILDING_PRIORITY = 10`.
  - `isAfterDumpPickupAllowed` = pas de requête en cours.
- La compétence de vitesse et la compétence d'amélioration sont celles du `WorkerModule` : principale et secondaire par défaut, comme MC `CraftingWorkerBuildingModule`.

### Fichier `hycolony/crafting.json`

Fichier de données du plugin, traité comme `id-map.json` et `styles.json` :
- `SubPlugins` le fusionne avec les fragments des sous-plugins activés (profondeur 2 : métier, puis clé) ;
- le plugin le lit en JSON ;
- le cœur le reçoit sous forme de `CraftingRules` (records) au démarrage, par `CraftingRules.parse(JsonObject)`, une fonction pure du cœur testée avec Gson.

Lecture tolérante : clé absente = vide, entrée invalide ignorée et journalisée une fois.

```json
{
  "jobs": {
    "farmer": {
      "allow": [ { "bench": "Farmingbench", "categories": ["*"] } ],
      "includeItems": [],
      "excludeItems": [],
      "custom": [
        { "id": "farmer_wheat_seeds", "hytaleRecipe": "<id>", "minBuildingLevel": 1, "maxBuildingLevel": 5 }
      ]
    }
  },
  "reduceable": { "ingredients": [], "excludedProducts": [] }
}
```

- Une recette passe le filtre d'un métier dans deux cas :
  - sa table (ou `Fieldcraft`) et ses catégories sont dans `allow`, et sa sortie n'est pas dans `excludeItems` ;
  - sa sortie est dans `includeItems`.

  C'est l'équivalent des tags MC `crafterProduct` et `crafterProductExclusions`. Un métier absent ne peut rien apprendre.
- Les recettes `custom` d'un métier sont données à sa hutte automatiquement, selon son niveau. Pour l'instant, une recette maison est une recette Hytale existante désignée par son identifiant. Des recettes entièrement propres à HyColony viendront si un métier en a besoin.
- Le fermier n'est rempli qu'en SP3b-2. SP3b-1 livre le fichier avec `jobs` vide et un test du cœur qui charge un exemple.

### Fenêtre : onglet Recettes (MC `WindowListRecipes`, `CraftingModuleView`)

- L'onglet s'affiche pour une hutte qui a un module de fabrication.
- En tête : `apprises / maximum`, comme MC `getActiveRecipes / getMaxRecipes`.
- **Liste des apprises**, dans l'ordre. Pour chaque recette :
  - la sortie avec sa quantité et ses ingrédients, sur une ligne ;
  - la mention « désactivée » si c'est le cas, et « maison » pour une recette automatique, qui ne peut pas être retirée ;
  - les boutons Monter, Descendre (Maj = tout en haut ou tout en bas), Activer/Désactiver et Retirer.
- **Liste des apprenables** : les recettes compatibles pas encore apprises, triées par nom de sortie, avec un bouton Apprendre. Si le maximum est atteint, le bouton est grisé et le motif est affiché.
- Chaque bouton appelle une action du cœur (`CraftingActions`), qui vérifie la permission `MANAGE_HUTS`, applique l'action et ré-affiche la vue (CLAUDE.md § 7). Tous les textes passent par des clés en-US et fr-FR.

## Écarts à MineColonies

À documenter par `Deviation from MC:` dans le code et à reporter dans le tableau des écarts de la spec SP1+2 (§ 11).

1. **Apprentissage par liste.** On apprend une recette en la choisissant dans l'onglet Recettes, pas en la posant dans une grille, car Hytale n'a pas de grille de fabrication.
2. **Compatibilité par table et niveau de table.** Une recette Hytale demande une table, des catégories et un niveau. La hutte ne connaît que les tables de son plan, posées par le constructeur, avec leur niveau. C'est l'équivalent des « intermediate blocks » de MC (`Blocks.FURNACE`…), étendu aux niveaux de Hytale.
3. **Coût des tables de niveau N.** Le constructeur demande aussi les matériaux de montée de niveau de Hytale. MC ne connaît pas les niveaux de table.
   - La case coûte l'objet de la table (`benchItem`), à défaut l'objet de son bloc, sinon rien, plus `benchUpgradeCost(table, 1, N)`. Chaque unité compte dans les paquets du constructeur, qui attend d'avoir tous ces objets avant de poser la table (`EntryCost`).
   - La hutte enregistre la table au niveau de son plan même si le monde refuse ce niveau (`setBenchTier` faux, journalisé) : le constructeur l'a payé, et la fabrication ne dépend que du niveau enregistré.
   - Un collage créatif pose la table à son niveau et l'enregistre, sans coût.
4. **`KnowledgeRequired`.** Une recette que Hytale réserve aux joueurs qui l'ont apprise ne peut être apprise à la hutte que par un joueur qui la connaît.
5. **Filtre par métier dans `crafting.json`.** Il remplace les tags `crafterProduct` de MC et se fonde sur les tables et catégories Hytale.
6. **Ingrédients par type de ressource ou tag.** Ils sont demandés par une `StackList`. Dans MC, la grille fige l'objet exact au moment de l'apprentissage. Un tel ingrédient est « réductible » par l'amélioration si tous les objets qu'il accepte sont listés dans `reduceable.ingredients`.
7. **Recherche absente.** Les effets `RECIPES` et `CITIZEN_INV_SLOTS` valent 0, et `RECIPE_MODE` reste sur `PRIORITY`.
8. **Bug MC corrigé :** `AbstractJobCrafter.deserializeNBT` range trois clés dans `progress`.
9. **Pas de places assises ni debout** pour l'artisan inactif : sans tâche ni vidage en attente, il laisse la main à la flânerie du citoyen (`canGoIdle`), comme le fermier de MC.
10. **Résolveurs privés créés par le module de fabrication**, pas par chaque `WorkerModule`, pour que `job` ne dépende pas de `crafting`. Ils sont enregistrés après les publics, comme chez le fermier de MC, qui liste son module de fabrication avant ses employés : à priorité et distance égales, le premier enregistré l'emporte. Chez MC, cet ordre suit les modules de chaque hutte.
11. **Composants au lieu d'héritage :** `CraftingTasks` et `CraftingWork` remplacent `AbstractJobCrafter` et `AbstractEntityAICrafting` (règle d'`ArchitectureTest`). Le comportement est le même.
12. **Une recette qui n'est plus valable n'est plus choisie** (`getFirstRecipe`, `getFirstFulfillableRecipe`), mais reste dans la liste : sa table a disparu, le métier ne peut plus l'apprendre, ou c'est une recette maison retirée de `crafting.json`. MC ne la retire qu'au rafraîchissement de sa vue (`serializeToView`), avec le même test : une recette qui fait la même sortie qu'une recette maison du métier (`isPreTaughtRecipe`) reste valable.
13. **Identifiants de recettes lisibles** (`hytale:`, `custom:`, `improved:<n>`) au lieu de jetons aléatoires. Une recette dont le nom est déjà pris par un autre contenu de même source (le jeu ou `crafting.json` l'a changée) remplace l'ancienne sous ce nom. MC l'ajoute sous un nouveau jeton, puis `checkForWorkerSpecificRecipes` l'échange.
14. **Toutes les recettes du registre sont sauvegardées.** MC ne sauvegarde que celles utilisées depuis le démarrage du serveur (`usedRecipes`). La croissance reste bornée : une recette améliorée ne peut l'être qu'un nombre fini de fois.
15. **Le nombre minimum d'une requête de fabrication est sauvegardé.** MC `PublicCrafting.serialize` l'oublie : une tâche rechargée demande alors son nombre complet.
16. **Affichage d'une `StackList`** : « 8 x Tronc de chêne (ou équivalent) », avec le premier objet accepté. MC affiche sa description, une clé de traduction ; un type de ressource ou un tag Hytale n'a pas de nom traduit connu du plugin. L'onglet Recettes affiche de même un ingrédient par type ou tag (`RecipesView.IngredientLine`).
17. **Robustesse du module de fabrication.**
    - Un index hors de la liste (`toggle`, `switchOrder`) ne change rien ; MC lève une exception.
    - Retirer une recette absente de la liste ne change rien ; MC vide toute la liste.
    - Remplacer une recette par une autre déjà listée retire la première ; MC liste alors la seconde deux fois.
    - Un déplacement complet (`fullMove`) marque aussi la colonie à sauvegarder. Chez MC, seul l'échange le fait, mais ses sauvegardes n'en dépendent pas.
18. **Recettes maison réduites.** Ni recherche requise ou exclue (la recherche n'est pas portée), ni `mustExist`, dont aucun métier n'a besoin pour l'instant. Les doublons sont comparés comme des ensembles d'objets : MC trie les deux listes par un hachage de l'objet et de sa quantité avant de les comparer deux à deux, ce qui peut manquer un doublon aux quantités différentes.
19. **Message d'amélioration :** la sortie et l'ingrédient réduit sont nommés par leur identifiant Hytale, le cœur ne connaissant pas leur nom traduit. MC affiche leur nom (`getHoverName`).
20. **File de l'artisan sans jeton bloquant** (`CraftingTasks`). Un jeton dont la requête a disparu ne bloque jamais :
    - `currentTask` s'arrête sur une file vidée de ses jetons morts ; MC boucle alors sans fin ;
    - `finishRequest` retire une tête morte, et `cancelAll` saute une tâche déjà emportée par l'échec d'un lot frère ; MC lève une exception sur un jeton inconnu ;
    - le chargement retire les jetons morts de la file et des tâches assignées (`CraftingHeal`) ; MC les garde, et une tâche assignée morte compterait pour toujours dans la charge de l'artisan.
21. **Lot d'une seule exécution trop grande.** Si une seule exécution de la recette ne tient pas dans l'inventaire, les lots font une exécution. Chez MC, la taille de lot tombe à 0 et le découpage ne s'arrête plus.
22. **Demandeur inconnu** (sa hutte a disparu) : le résolveur de fabrication le juge le plus loin possible. MC garde la position sauvegardée du demandeur.
23. **Réservations sans exclusion.** Le stock que la hutte sert à ses propres requêtes (`BuildingResolver`) ne déduit pas les réservations de ses artisans : `building` ne dépend pas de `crafting`. MC les déduit dans `BuildingRequestResolver`, en excluant la tâche dont la requête descend (`reservedStacksExcluding(request)`). Les réservations servent ici au choix d'une recette réalisable et au « à garder ».
24. **Place vérifiée exactement quand l'artisan fabrique** (`RecipeExecution.craftOnce`) : l'exécution est d'abord jouée sur une copie de son inventaire, et rien ne change si une sortie ne tient pas. MC estime la place par le nombre de cases libres (`checkForFreeSpace`) et peut consommer les ingrédients d'une sortie qui ne trouve ensuite aucune case : elle est perdue. La fabrication immédiate du résolveur privé, dans la hutte, garde l'estimation de MC (les conteneurs ne se copient pas).
25. **L'artisan travaille à la table de la recette** : il y marche et la frappe, ou frappe le bloc de hutte pour une recette sans table. MC marche vers un emplacement « work » du schéma et frappe le bloc de hutte. Il tient un seul objet (l'outil, sinon un ingrédient) : MC remplit les deux mains.
26. **Tâche abandonnée sans reste** (`CraftingWork.abandon`). Une tâche qui quitte la tête de file en pleine fabrication (annulée, échouée, finie ailleurs) est abandonnée comme une tâche annulée de MC : compteurs remis à 0 et vidage. MC ne l'abandonne que si la requête est encore là : sinon il poursuit la tâche suivante avec la recette et les compteurs de l'ancienne, ou s'arrête sans vider si la file est vide. De même, une tâche échouée faute de module n'est jamais suivie de sa recette.
27. **Ingrédients pris en une fois** : l'artisan prend l'ingrédient qui manque dans tous les conteneurs de la hutte à la fois (`WorkerStock.take`, comme le constructeur). MC marche d'un coffre à l'autre.
28. **Sorties d'une tâche regroupées à la fin** : l'artisan ajoute la sortie de chaque exécution comme une pile à part, et les piles sont regroupées en piles pleines quand la tâche est finie (`CraftingProductionResolver.followups`). MC les regroupe à chaque ajout (`AbstractRequest.addDelivery`). Le livreur reçoit dans les deux cas une livraison par pile pleine.
29. **Une recette maison ne peut pas être retirée** (`RecipeEdits.remove`). Chez MC, le bouton Retirer d'une recette intégrée est grisé, mais Ctrl le débloque pour « réinitialiser » une recette cassée, que le tick suivant de la colonie redonne. Ici, l'identifiant d'une recette maison suit son contenu (écart 13) : elle ne peut pas être cassée.
30. **Retour de l'apprentissage** (`CraftingActions.learn`). Un refus nomme son motif (hutte pleine, recette incompatible, recette inconnue du joueur) ; MC nomme la hutte et les deux motifs possibles. Les boutons de l'onglet ne jouent pas les sons de succès et d'erreur de MC.

## Architecture

- **Cœur**, nouveau paquet racine `crafting`, au plus 15 fichiers par sous-paquet, classes courtes. Il dépend de `kernel`, `request`, `building`, `citizen`, `job`, `colony` et `logistics` (la position d'un demandeur, `RequesterLocation`), jamais de `construction`, `colony.action`, `colony.view` ni `colony.persistence` :
  - `crafting/recipe` :
    - `Recipe`, `RecipeId`, `Ingredient`, `BenchRequirement`, `RecipeSource` ;
    - `RecipeCatalog`, le port. Il vit ici, comme `BlueprintSource`, parce que `kernel/port` a déjà 15 fichiers ;
    - `RecipeRegistry` ;
    - `RecipeMatching` : la correspondance d'un ingrédient avec un objet ;
    - `CraftingRules` : la lecture de `crafting.json` et le filtre des métiers ;
    - `CraftingSetup(RecipeCatalog, CraftingRules)`, que `ConstructionPorts` reçoit ;
  - `crafting/module` : `CraftingModule` (état et liste), `CraftingModules` (les modules d'une hutte), `RecipeCompatibility`, `CustomRecipes`, `RecipeImprovement`, `RecipeReservations` ;
  - `crafting/request` : `CraftingResolvers` (le point d'entrée, que le module appelle), `CraftingRequestResolver`, `CraftingProductionResolver`, `CraftingBatches` (découpage en lots), `CraftingCycles`, `IngredientRequests` (la requête d'un ingrédient) et `HutLookups` (modules, employés et demandeurs de la hutte) ;
  - `crafting/job` :
    - `Crafter`, `CraftingTasks`, `CraftingWork`, `CraftingStep` ;
    - `CraftingWorkContext` : l'artisan, sa hutte et les pièces partagées de `job/work` (`WorkerStock`, `ToolRequests`, `SyncRequests`) ;
    - `CraftingProgress` : la durée d'une exécution ; `RecipeCounts` : le calcul de `maxCraftingCount` et des ingrédients qui manquent ;
    - `RecipeExecution` : consommer et produire dans un `Inventory` ; `CraftingRun` : une exécution de l'artisan (`executeCraftingAction`) ;
    - `CrafterHands` (la marche vers la table et les coups) et `CraftedOutputs` (où vont les sorties) ;
  - `colony/ui/tab/RecipesView` (record) et `colony/action/CraftingActions` ;
  - `kernel/item/Workstation(String benchId, int tier)` : `building` ne peut dépendre ni de `construction` ni de `crafting` ;
  - `request/model/StackList` et `request/model/Crafting`. `Requestable` et `Deliverable` sont scellés dans ce paquet, et `request` ne doit pas dépendre de `crafting` : `Crafting` porte donc un `String recipeId`.
- **`Colony`** garde le `RecipeRegistry` en champ (accès `recipes()`). Le paquet `colony` a déjà 15 fichiers : aucun fichier n'y est ajouté.
- **Existant modifié :**
  - `BlueprintEntry` (+ `workstation`) ;
  - `Building` (+ tables enregistrées) ;
  - le calcul des ressources du chantier (+ coût des niveaux) ;
  - `RequestableJson` (+ `crafting` et `stackList`) ;
  - `ColonySerializer` et `MigrationChain` (schéma +1, avec une fixture de l'ancienne version) ;
  - `ColonySerializer.heal`, par `CraftingHeal` : les recettes apprises que le registre a perdues et les tâches d'artisan dont la requête a disparu. Les tables enregistrées ne sont pas vérifiées : le chargement n'a pas accès au monde, où un chunk non chargé ressemble à une table absente.
- **Plugin :**
  - `HytaleRecipeCatalog`, construit à partir de `CraftingRecipe.getAssetMap()`, des types de ressources, des tags et de `Bench.getTierLevel(...).UpgradeRequirement`. Chaque API est à vérifier dans `build/vineflower/hytale-server` et à noter dans `docs/research/plugin-b-api.md` ;
  - `PrefabCells` lit `BenchBlock` ;
  - la pose d'une table avec son niveau ;
  - l'onglet Recettes (`.ui` sur le modèle vanilla), son contrôleur et les clés de langue.
- **Tests :** `FakeRecipeCatalog`, dans `core/src/test/.../testing`.

## Test

- **Cœur, en TDD :** chaque règle ci-dessus a son test, et un scénario complet passe avec `FakeRecipeCatalog` et une petite hutte d'artisan de test (métier de test, dans `testing`).
  - Scénario : une requête de 10 objets, un lot, les ingrédients demandés puis livrés, la fabrication, la livraison au demandeur.
  - Plus le privé instantané, le cycle refusé, l'amélioration, le maximum de recettes et une table de niveau insuffisant.
- **En jeu :** avec le fermier de SP3b-2, aucun métier concret n'existant avant. Pour ce test, le plan de la hutte du fermier est un prefab vanilla avec un `Bench_Farming` ajouté à la main (les vrais schémas viendront plus tard). SP3b-1 ajoute à `/hycolony selftest` la lecture du catalogue (nombre de recettes, une recette connue du `Farmingbench`, le coût de montée d'un niveau).

## À vérifier pendant le plan (sources décompilées)

- Comment une table Hytale est reconnue « de type Crafting » depuis le `BlockType` (`Bench` et `BenchType`), et où lire ses catégories.
- Le calcul exact de la condition de catégories entre table et recette (`CraftingRecipe` / `BenchRecipeRegistry`).
- Le format de `UpgradeRequirement` de chaque `BenchTierLevel`.
- La pose d'un bloc avec son composant `BenchBlock` au bon niveau, sans le bit 2 de `setBlock` (voir `sp3b-hytale-farming.md` § 4).
- Où lire `KnowledgeRequired` et les recettes connues d'un joueur.
