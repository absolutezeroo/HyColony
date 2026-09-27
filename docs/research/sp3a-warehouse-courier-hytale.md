# SP3a : entrepôt et livreur, faits Hytale 0.6.8 et recettes MC

Recherche pour la section « À vérifier » de `docs/superpowers/specs/2026-09-27-hycolony-sp3a-warehouse-courier-design.md`.

Sources :
- `HS/` = `build/vineflower/hytale-server/com/hypixel/hytale/` ;
- `ZIP` = `release-0.6.8-Assets.zip` ;
- `MC/` = `raw.githubusercontent.com/ldtteam/minecolonies/version/main/`.

Les comptes de prefabs viennent des scripts de `prefab-obtainability.md` (`load.py`, `classify.py`, `alt.py`), réutilisés avec deux scripts de plus : `sp3a_pf*.py` et `cont_all.py`, dans le scratchpad de session, non commités.

## 1. Météo : lire la pluie à une position

### 1.1 Ce que fait MineColonies
`MC/src/main/java/com/minecolonies/core/entity/ai/workers/CitizenAI.java`, l. 231-241 :

```java
if (CompatibilityUtils.getWorldFromCitizen(citizen).isRaining() && !shouldWorkWhileRaining() && !WorldUtil.isNetherType(citizen.level))
```

Le citoyen passe alors en `IDLE`, avec le statut `BAD_WEATHER` et l'interaction `COM_MINECOLONIES_COREMOD_ENTITY_CITIZEN_RAINING`.

`shouldWorkWhileRaining()` (l. 305-325) renvoie vrai dans trois cas :
- la configuration `workersAlwaysWorkInRain` est active ;
- l'effet de recherche `WORKING_IN_RAIN` est positif ;
- le `WorkerBuildingModule.canWorkDuringTheRain()` du bâtiment de travail est vrai.

**MC ne regarde ni le biome ni les précipitations** dans ce test. `level.isRaining()` est un état global du monde, sans position. Il est donc vrai aussi dans un biome enneigé ou sec, et sous un toit. Seul le Nether est exclu.

### 1.2 Ce qu'offre Hytale
La météo est **par environnement** (zone ou biome, en 3D), pas globale.

- `HS/builtin/weather/resources/WeatherResource.java` : `Resource<EntityStore>`, une instance par monde.
  - `static ResourceType<EntityStore, WeatherResource> getResourceType()` ;
  - `int getForcedWeatherIndex()` renvoie 0 si aucune météo n'est forcée (`/weather set` ou `WorldConfig.getForcedWeather()`) ;
  - `int getWeatherIndexForEnvironment(int environmentId)` renvoie `Integer.MIN_VALUE` si la valeur n'est pas encore calculée (`defaultReturnValue`, l. 26-28) ;
  - `Int2IntMap getEnvironmentWeather()`.
- `HS/builtin/weather/systems/WeatherSystem.java` l. 129-160 (`TickingSystem.tick`) : à chaque changement d'heure du jeu (`WorldTimeResource.getCurrentHour()`), le système tire pour **chaque** `Environment` une météo dans `environment.getWeatherForecast(hour)`. C'est une table pondérée, tirée avec la graine `hash(worldSeed, weatherSeedKey, dateTime)`. Le résultat est rangé dans `environmentWeather`. Ce calcul se fait avant la boucle sur les joueurs, et ne dépend donc pas de leur présence. **[in-game]** : à confirmer sur un monde sans joueur proche.
- L'environnement d'un bloc se lit sur le chunk :
  - `HS/server/core/universe/world/chunk/BlockChunk.java` l. 310-320 : `int getEnvironment(int x, int y, int z)`, qui renvoie 0 si `y` est hors de [0, 320[. Il existe aussi en `Vector3i` et `Vector3d`. L'indexation interne masque `x & 31`, `z & 31` (`EnvironmentChunk.idx` → `ChunkUtil.indexColumn`), donc on passe des **coordonnées monde** ;
  - `WorldChunk.getBlockChunk()` (`WorldChunk.java` l. 212).
- Le serveur fait déjà la même chose :
  - pour les PNJ, `HS/server/npc/role/support/WorldSupport.java` l. 246-291 : `getEnvironmentId` via `TransformComponent.getSectionRef()` → `ChunkSection.getChunkColumnReference()` → `BlockChunk.getEnvironment(position)`, puis `getCurrentWeatherIndex`, forcée d'abord, sinon par environnement ;
  - pour les joueurs, `WeatherTracker.updateWeather` (`HS/builtin/weather/components/WeatherTracker.java` l. 45-62).
- Le nom de la météo s'obtient avec `Weather.getAssetMap().getAsset(index).getId()`, comme le fait `SensorWeather.matches`, `HS/server/npc/corecomponents/world/SensorWeather.java` l. 33-45.
- `Weather` (`HS/server/core/asset/type/weather/config/Weather.java`) **n'a aucun drapeau « pluie » ou « précipitation »**. Ses champs sont visuels : ciel, brouillard, nuages, `particle`. La pluie se reconnaît donc à `Weather.getParticle()` (l. 594), qui renvoie un `com.hypixel.hytale.protocol.WeatherParticle` (champs publics `systemId`, `isOvergroundOnly`, …), ou à l'id de l'asset.

API proposée, sur le thread du monde :

```java
Store<EntityStore> store = world.getEntityStore().getStore();
WeatherResource weather = store.getResource(WeatherResource.getResourceType());
int index = weather.getForcedWeatherIndex();
if (index == 0) {
    Ref<ChunkStore> chunkRef = world.getChunkStore().getChunkReference(ChunkUtil.indexChunkFromBlock(x, z));
    if (chunkRef == null) return false;                       // chunk non chargé : pas de pluie connue
    BlockChunk bc = world.getChunkStore().getStore().getComponent(chunkRef, BlockChunk.getComponentType());
    index = weather.getWeatherIndexForEnvironment(bc.getEnvironment(x, y, z));
}
if (index == 0 || index == Integer.MIN_VALUE) return false;
WeatherParticle p = Weather.getAssetMap().getAsset(index).getParticle();
return p != null && RAINY_SYSTEMS.contains(p.systemId);
```

`world.getChunkIfLoaded(long)` existe aussi (`World.java` l. 832), mais il est `@Deprecated`. `HytaleWorldQuery.isLoaded` utilise déjà `getChunkReference`.

### 1.3 Météos à précipitations (ZIP `Server/Weathers/**`, champ `Particle.SystemId`)

| SystemId | Météos |
|---|---|
| `Rain` | `Zone1_Rain`, `Dungeon_Cursed_Crypt_Graveyard`, `Zone4_GhostForest_Rain`, `Zone4_Wastes_Rain` |
| `Rain_Light` | `Zone1_Rain_Light`, `Zone3_Rain` |
| `Rain_Heavy` | `Zone1_Storm`, `Zone1_Swamp_Rain`, `Zone2_Thunder_Storm`, `Zone4_Storm`, `Zone4_Swamp_Storm`, `Zone4_Wastes_Rain_Heavy`, `Skylands_Rapid_Marsh_Stormy` |
| `Snow_Light`, `Snow_Heavy`, `Snow_Storm` | `Zone3_Snow`, `Zone3_Snow_Heavy`, `Zone3_Snow_Storm` |
| sans précipitation | `Sand_Storm` (`Zone2_Sand_Storm`), `Ash_Storm`/`Ash`, `Fireflies_GS`, `Leaves_*`, `Fog`, `Water_Dripping` (grottes Zone 3) … |

Toutes les météos de pluie et de neige ont `"OvergroundOnly": true`.

**Dépendance au biome et à la zone : oui.** Chaque `Server/Environments/**/Env_*.json` a ses `WeatherForecasts` par heure (0-23). Exemples :
- `Env_Zone1` : `Zone1_Sunny` 52, `Zone1_Cloudy_Medium` 10, `Zone1_Rain_Light` 2, `Zone1_Rain` 1, `Zone1_Storm` 1 ;
- `Env_Zone1_Plains` : `Zone1_Sunny` 40, `Zone1_Sunny_Fireflies` 20, `Zone1_Cloudy_Medium` 20, `Zone1_Foggy_Light` 10, `Zone1_Rain_Light` 10, `Zone1_Rain` 5, `Zone1_Storm` 2 ;
- `Env_Zone1_Caves` : `Cave_Shallow` 100 %, jamais de pluie.

L'environnement est stocké par colonne **et par hauteur** (`EnvironmentColumn`). Un tunnel de mine a donc son propre environnement de grotte et n'a pas de pluie, ce qui remplace le test « sous le ciel ». **[in-game]** : vérifier qu'un puits de mineur creusé par la colonie porte bien l'environnement de grotte. Il est posé par la génération, pas recalculé quand on creuse.

### 1.4 Recommandation
- Un port `Weather.isRaining(BlockPos)` côté cœur, et `HytaleWeather` côté plugin, codé comme ci-dessus.
- On teste à la **position de la hutte de travail** du citoyen. C'est l'équivalent le plus proche d'un état global MC, stable pendant le trajet.
- « Pluie » = `systemId` commence par `Rain` **ou** `Snow`. MC traite la neige comme de la pluie (`isRaining()` global).
- `Deviation from MC:` pluie locale à l'environnement de la hutte au lieu d'un état global. Hytale n'a pas d'état global de pluie. À noter dans la spec.
- Retirer ou ajouter un `systemId` est un choix de jeu : il reste dans le cœur ou dans `id-map.json`, jamais codé en dur dans l'adaptateur.

## 2. Recettes MC des deux huttes

Fichiers générés : `MC/src/datagen/generated/minecolonies/data/minecolonies/recipes/`.

| Recette | Motif | Clé | Résultat |
|---|---|---|---|
| `blockhutwarehouse.json` | `XTX` / `XBX` / `XXX` | `X` = tag `minecraft:planks`, `T` = `structurize:sceptergold`, `B` = tag `forge:chests` | 1 `minecolonies:blockhutwarehouse` |
| `blockhutdeliveryman.json` | `XTX` / `XBX` / `XXX` | `X` = planches, `T` = sceptre d'or, `B` = `minecraft:leather_boots` | 1 `minecolonies:blockhutdeliveryman` |
| `blockhutdeliverymaniron.json` | même motif | `B` = `minecraft:iron_boots` | **2** `minecolonies:blockhutdeliveryman` |

Pour comparer, la même famille : `blockhutbuilder` a `B` = tag `minecraft:wooden_doors`, et `blockhutcitizen` (la résidence) a `B` = `minecraft:torch`.

### 2.1 Transposition existante
Les huttes HyColony n'ont gardé **ni le sceptre ni l'objet clé**. `HyColony_Hut_Builder.json` et `HyColony_Hut_Residence.json` demandent tous les deux `Wood_Trunk` ×10 + `Rock` ×2 au `Workbench` [`Workbench_Crafting`]. `HyColony_TownHall.json` demande `Wood_Trunk` ×6 + `Rock` ×3.

### 2.2 Proposition (à valider)
Même base que les autres huttes, plus l'objet clé de MC. Tous les ingrédients sont obtenables en survie (modèle de `prefab-obtainability.md` § 1.2).

| Hutte | `Input` | Banc | Justification |
|---|---|---|---|
| `HyColony_Hut_Warehouse` | `Wood_Trunk` ×10, `Rock` ×2, `{ "ItemId": "Furniture_Crude_Chest_Small", "Quantity": 1 }` | `Workbench` [`Workbench_Crafting`] | Le coffre MC devient le coffre le plus simple de Hytale : `Wood_Trunk` ×10, en `Fieldcraft` [`Tools`] ou au `Workbench` [`Workbench_Survival`] (ZIP `Server/Item/Items/Furniture/Crude/Furniture_Crude_Chest_Small.json`). |
| `HyColony_Hut_Courier` | `Wood_Trunk` ×10, `Rock` ×2, `{ "ItemId": "Ingredient_Leather_Light", "Quantity": 2 }` | idem | Hytale **n'a pas d'emplacement bottes**. `protocol/ItemArmorSlot` = `Head, Chest, Hands, Legs` (`HS/protocol/ItemArmorSlot.java` l. 5-9). Les jambières de cuir (`Armor_Leather_Light_Legs`) demandent le banc `TODO`, qui n'existe pas : elles sont **non obtenables**. Le cuir léger vient de `Ingredient_Hide_Light` ×1 à la `Tannery` (`Processing`), et il est obtenable. |

Variante « fer » (MC : 2 huttes) : `Armor_Iron_Legs` (`Armor_Bench` [`Armor_Legs`], 13 lingots de fer, 6 cuirs légers, 4 chutes de lin) avec `"OutputQuantity": 2`. Le champ `OutputQuantity` existe dans `CraftingRecipe.CODEC` (`HS/server/core/asset/type/item/config/CraftingRecipe.java` l. 58). Mais une recette d'objet n'a qu'**une** sortie par fichier, donc la variante demanderait un second fichier dans `Server/Item/Recipes/`. **[in-game]** Proposé comme optionnel, sans nécessité.

Alternative strictement « comme l'existant » : `Wood_Trunk` ×10 + `Rock` ×2 sans objet clé.

## 3. Prefabs candidats

### 3.1 Constat bloquant : aucun prefab vanilla ne contient de vrai coffre
Parcours de tous les `Server/Prefabs/**/*.prefab.json` : les blocs dont le `BlockType` a `BlockEntity.Components.ItemContainerBlock` (47 objets, tous les `Furniture_*_Chest_*` et `Debug_MusicPlayer`) n'apparaissent que dans `Blocksets/Blocksets_08` et `Goblin_Thief_Chest`. **Aucun prefab Outlander ou Kweebec ne pose un coffre directement.**

Les coffres des villages sont des **`Block_Spawner_Block`** dont le composant est `{"BlockSpawner": {"BlockSpawnerId": "…"}}`. Leur définition est dans ZIP `Server/Item/Block/Spawners/New/*.json` :

| BlockSpawnerId | Tirage | Coffre posé | Obtenable ? |
|---|---|---|---|
| `Zone1_Kweebec_Tier1` / `_Tier2` / `_Tier3` | coffre 100, `Empty` 20 / 50 / 100 | `Furniture_Kweebec_Chest_Small` (18 cases), `Droplist` de butin | **oui** : `Furniture_Bench`, `Ingredient_Life_Essence` ×2, `Ingredient_Fibre` ×2, `Ingredient_Stick` ×1 |
| `Zone3_Outlander_Tier1` / `_Tier2` | 100 % | `Furniture_Human_Ruins_Chest_Small` (18) | **non** (aucune recette) |
| `Zone3_Outlander_Tier3` | 100 % | `Furniture_Temple_Dark_Chest_Small` | non vérifié ici |
| `Zone3_Encounters_Tier1` / `_Tier2` | 100 % | `Furniture_Crude_Chest_Small` / `Furniture_Village_Chest_Small` | oui / oui (`Furniture_Bench`, planches de feuillu ×3, lingot de fer ×2) |

`HytaleBlueprintSource` **écarte** `Block_Spawner_Block` (l. 43 et 165). Aujourd'hui, un bâtiment construit à partir de ces prefabs n'a donc **aucun conteneur en plus** du bloc de hutte.

Pour que l'entrepôt ait des étagères (racks), il faut :
- **(a)** résoudre les `Block_Spawner_Block` de coffre en un coffre obtenable et **vide**, sans `Droplist`, sinon on crée du butin gratuit à chaque construction ;
- **(b)** ou substituer des décors de rangement par des coffres. Les tonneaux `Furniture_Ancient_Barrel` et `Furniture_Tavern_Barrel` ont `HitboxType: Chest_Small`, la même emprise qu'un coffre. Ils sont non obtenables et figurent déjà dans la table de substitution recommandée par `prefab-obtainability.md` § 5.3. `Furniture_Village_Crate` est non obtenable : sa recette vise le banc `Furniture_Misc`, qui n'existe pas.

Une grande caisse `*_Chest_Large` (36 cases) est un autre `BlockType`, formé par le `ConnectedBlockRuleSet` `ChestConnectedBlockTemplate` quand deux petits coffres se touchent (`connected-blocks.md` l. 145).

### 3.2 Méthode et colonnes
- **Blocs** : nombre de blocs du prefab **entier**, avec `filler == 0`, sans `Empty`, `Block_Spawner_Block` ni `Editor_*`. Aucun `hutOffset` n'est encore choisi, donc le filtre `y ≥ -1` n'est pas appliqué.
- **Taille** : `X×Y×Z` de la boîte englobante de ces blocs.
- **Spawners** : nombre de `Block_Spawner_Block` de coffre, c'est-à-dire les conteneurs réels si on applique (a).
- **Rangement** : caisses, tonneaux et étagères du prefab, candidats à (b).
- **Bloquants** : blocs non obtenables après la règle A de `prefab-obtainability.md`, sans compter les cultures. Les principaux sont listés.

Conteneurs posés directement : **0 pour tous** (§ 3.1).

### 3.3 Outlander : entrepôt (niveaux 1 à 5)
Les maisons Outlander sont les plus « entrepôt » : ce sont des réserves pleines de caisses et de tonneaux.

| Niv. | Prefab (`Npc/Outlander/Houses/…`) | Blocs | Taille | Spawners | Rangement | Bloquants |
|---|---|---:|---|---:|---|---|
| 1 | `Tier1/Outlander_Houses_Tier1_008` | 817 | 9×10×19 | 3 | 24 `Village_Crate`, 16 `Ancient_Barrel` | 40 : exactement les 24 caisses et les 16 tonneaux |
| 2 | `Tier1/Outlander_Houses_Tier1_003` | 1386 | 15×12×20 | 2 | 16 caisses, 6 tonneaux, 7 `Lumberjack_Shelf` | 23 : 16 caisses, 6 tonneaux, 1 `Faun_Stool` |
| 3 | `Tier2/Outlander_Houses_Tier2_002` | 1939 | 16×16×24 | 2 | 14 caisses, 4 tonneaux, 4 étagères | 25 : 14 caisses, 6 branches `Redwood`, 4 tonneaux, 1 brasero |
| 4 | `Tier2/Outlander_Houses_Tier2_003` | 2512 | 21×15×21 | 4 | 31 caisses, 10 tonneaux, 10 étagères | 42 : 31 caisses, 10 tonneaux, 1 `Village_Counter` |
| 5 | `Tier3/Outlander_Houses_Tier3_001` | 7424 | 25×22×28 | 4 | 37 caisses, 18 tonneaux, 2 `Ancient_Crate`, 10 étagères | 109 : 37 caisses, 25 branches `Fir`, 17 pièges à pointes, 11 tonneaux |

Le niveau 4 est le même prefab que le niveau 4 actuel des trois autres huttes. Autre choix pour le niveau 2 : `Tier1/Outlander_Houses_Tier1_002` (1207 blocs, 13×11×19, 2 spawners, 18 caisses, 8 tonneaux, 37 bloquants).

### 3.4 Outlander : hutte du livreur (petite)

| Niv. | Prefab (`Npc/Outlander/Houses/…`) | Blocs | Taille | Spawners | Bloquants |
|---|---|---:|---|---:|---|
| 1 | `Tier0/Outlander_Houses_Tier0_006` | 795 | 9×14×11 | 1 | 5 : 2 `Cloth_Roof_Hide_Flap`, `Ancient_Torch`, `Crude_Brazier`, `Ancient_Barrel` |
| 2 | `Tier0/Outlander_Houses_Tier0_002` | 813 | 9×14×11 | 1 (`Encounters_Tier1` → coffre `Crude`, obtenable) | 20 : 11 `Cloth_Roof_Hide`, 3 `_Flat`, 2 branches, 1 caisse, 1 torche, 1 brasero |
| 3 | `Tier1/Outlander_Houses_Tier1_007` | 711 | 9×10×18 | 0 | 15 : 9 caisses, 5 tonneaux, 1 `Faun_Stool` |
| 4 | `Tier1/Outlander_Houses_Tier1_004` | 773 | 10×11×16 | 0 | 20 : 6 branches longues, 5 caisses, 4 branches courtes, 3 tonneaux |
| 5 | `Tier1/Outlander_Houses_Tier1_005` | 1104 | 13×10×16 | 0 | 19 : 11 caisses, 6 tonneaux, 1 comptoir, 1 tabouret |

Les tours `Npc/Outlander/Towers/Tier1/*` (190-253 blocs, 6×16×6) sont plus petites mais ne ressemblent pas à une hutte.

### 3.5 Kweebec : entrepôt
**Aucun bâtiment Kweebec n'est une réserve.** Les plus proches sont les échoppes (`Shops` : étagères et 1 coffre) et les maisons, qui ont au plus 1 spawner. Les `Houses_Guard` (3-5 spawners) font 3000-4900 blocs, dont plus de 200 bloquants.

| Niv. | Prefab (`Npc/Kweebec/…`) | Blocs | Taille | Spawners | Rangement | Bloquants |
|---|---|---:|---|---:|---|---|
| 1 | `Autumn/Shops/Kweebec_Autumn_Shops_007` | 176 | 7×9×7 | 1 (`Tier3`, 50 % vide) | 7 `Kweebec_Shelf` | 15 : 7 étagères, 8 branches `Oak` |
| 2 | `Redwood/Small_Plot/House/Kweebec_Redwood_Small_Plot_House_003` | 345 | 9×10×8 | 0 | 6 étagères | 38 : 21 `Kweebec_Platform`, 7 branches, 6 étagères, 4 racines |
| 3 | `Redwood/Normal_Plot/House/Kweebec_Redwood_Normal_Plot_House_001` | 832 | 12×18×14 | 1 (`Tier1`, 17 % vide) | 2 étagères, 1 armoire | 49 : 19 branches d'angle, 8 plateformes, 7 branches, 7 fenêtres |
| 4 | `Oak/Houses_Small/Kweebec_Oak_Houses_Small_001` | 1375 | 15×24×18 | 1 (`Tier3`) | 1 armoire | 7 : 2 fenêtres, enseigne, tableau, armoire, tabouret, plateforme |
| 5 | `Oak/Houses_Large/Kweebec_Oak_Houses_Large_002` | 2908 | 26×30×26 | 1 (`Tier3`) | 3 étagères, 1 armoire | 47 : 16 branches d'angle, 12 branches, 5 fenêtres, 4 racines |

Le niveau 3 est déjà le niveau 3 des autres huttes Kweebec. `Furniture_Kweebec_Shelf` (`HitboxType: Platform_Half`) est non obtenable. Il n'a pas l'emprise d'un coffre, donc la substitution (b) ne s'y applique pas. Côté Kweebec, les étagères de l'entrepôt devront venir de (a) ou d'un ajout de coffres dans le plan.

### 3.6 Kweebec : hutte du livreur (petite)
Les échoppes `Oak/Shops` font 7-8 × 9-10 × 7, avec 1 spawner `Zone1_Kweebec_Tier1` (coffre Kweebec obtenable, 17 % vide).

| Niv. | Prefab (`Npc/Kweebec/Oak/Shops/…`) | Blocs | Taille | Bloquants |
|---|---|---:|---|---|
| 1 | `Kweebec_Oak_Shops_008` | 156 | 7×9×7 | 17 : 5 étagères, 4 plateformes, 8 branches |
| 2 | `Kweebec_Oak_Shops_002` | 181 | 7×9×7 | 15 : 7 étagères, 8 branches |
| 3 | `Kweebec_Oak_Shops_005` | 193 | 8×10×7 | 14 : 4 étagères, 10 branches |
| 4 | `Kweebec_Oak_Shops_003` | 228 | 7×10×7 | 12 : 5 étagères, 7 branches |
| 5 | `Kweebec_Oak_Shops_001` | 241 | 7×10×7 | 26 : 13 plateformes, 5 étagères, 8 branches |

La progression est faible : 156 → 241 blocs. Comme autre choix pour le niveau 5 : `Oak/Guard_Towers/Kweebec_Oak_Guard_Towers_003` (308 blocs, 8×12×8, 6 bloquants, aucun spawner).

### 3.7 À faire au moment du plan
- Choisir le `hutOffset` de chaque prefab retenu. Aujourd'hui, `styles.json` partage les mêmes prefabs pour `townhall`, `builder` et `residence` ; les nouvelles entrées `hycolony:warehouse` et `hycolony:courier` suivront le même format `{ "prefab", "hutOffset" }`.
- Décider entre (a), (b) ou les deux, et du coffre de remplacement de chaque style. Candidats : `Furniture_Crude_Chest_Small` (Outlander), `Furniture_Kweebec_Chest_Small` (Kweebec), tous deux obtenables.
- Les bloquants restants relèvent de la table de substitution de `prefab-obtainability.md` § 3 et § 5.3, qui n'est pas encore implémentée : aucune occurrence dans `plugin/src/main`.

## 4. Conteneurs : l'existant réutilisable

### 4.1 Plan → drapeau `hasContainer`
- `core/.../construction/blueprint/BlueprintEntry.java` l. 7 :

  ```java
  public record BlueprintEntry(BlockPos offset, BlockState state, boolean hasContainer) {}
  ```

- `plugin/.../adapter/HytaleBlueprintSource.java` l. 169-171 : `hasContainer = type.getBlockEntity() != null && getBlockEntity().getComponent(ItemContainerBlock.getComponentType()) != null`. L. 211 : `new BlueprintEntry(offset, c.state(), c.container())`. Les `Block_Spawner_Block` sont exclus (l. 165).

### 4.2 Pose
- Port `core/.../kernel/port/WorldBlocks.java` l. 15 :

  ```java
  boolean place(BlockPos pos, BlockState state, boolean withContainer);
  ```

- `plugin/.../adapter/HytaleWorldBlocks.java` l. 112-160 : `testPlaceBlock`, puis `BlockOperations.setBlock(…)`. **`withContainer` est ignoré** (commentaire l. 148-150). L'entité de bloc fait partie du `BlockType`, donc un coffre posé reçoit son `ItemContainerBlock` de lui-même, à la capacité de l'asset (18 pour un petit coffre, 27 pour nos huttes).
- `core/.../construction/builder/BuilderBlockWork.java` :
  - l. 157-181 `place(...)` : après une pose réussie, `if (e.hasContainer()) ctx.site().target().addContainer(pos);` (l. 175-176, « MC: racks the builder places become the building's containers ») ;
  - l. 143-144, au minage : `ctx.colony().buildings().owningContainer(pos).ifPresent(b -> b.removeContainer(pos));`.

### 4.3 Enregistrement et lecture
- `core/.../building/Building.java` :
  - l. 33-34 `private final Set<BlockPos> containers = new LinkedHashSet<>()` ;
  - l. 122 `public void attachContainers(ContainerAccess access)`, qui crée `resolvers = List.of(new BuildingResolver(this, access))` ;
  - l. 127 `public List<BlockPos> containers()`, qui renvoie **le bloc de hutte d'abord**, puis les conteneurs enregistrés ;
  - l. 134 `public Set<BlockPos> registeredContainers()` ;
  - l. 138 `public void addContainer(BlockPos pos)`, qui ignore la position de la hutte ;
  - l. 144 `public void removeContainer(BlockPos pos)`.
- `core/.../building/BuildingManager.java` l. 64-73 : `public Optional<Building> owningContainer(BlockPos pos)` renvoie la hutte à cette position, sinon parcourt les `registeredContainers()` (parcours linéaire, marqué `ponytail`).
- Persistance : `core/.../colony/persistence/BuildingSerializer.java` l. 38 (écriture de `registeredContainers()`) et l. 56 (`b.addContainer(readPos(el))`).
- Branchement : `core/.../colony/ColonyBuildingListener.java` l. 19, `building.attachContainers(colony.context().ports().containers())`.
- Port `core/.../kernel/port/ContainerAccess.java` :

  ```java
  int count(List<BlockPos> containers, ItemKey item);
  int extract(List<BlockPos> containers, ItemKey item, int max);
  /** Returns the remainder that did not fit, or {@code null} if everything was inserted. */
  ItemAmount insert(List<BlockPos> containers, ItemAmount amount);
  Map<ItemKey, Integer> contents(List<BlockPos> containers);
  ```

- `plugin/.../adapter/HytaleContainerAccess.java` : `public HytaleContainerAccess(World world)` (l. 30), puis `count` l. 35, `extract` l. 52, `insert` l. 71 et `contents` l. 93. Chaque position est lue par `BlockModule.getComponent(ItemContainerBlock.getComponentType(), world, x, y, z).getItemContainer()` (l. 108-112). Une position sans conteneur ou non chargée est sautée. Les objets sont comparés par id et retirés slot par slot. Assistants statiques package-private : `takeBySlot(ItemContainer, ItemKey, int)` l. 115, `give(ItemContainer, ItemAmount)` l. 131, `addContents(ItemContainer, Map)` l. 137. Ils sont réutilisés par `HytalePlayerInventory`.
- `core/.../building/BuildingResolver.java` : `public BuildingResolver(Building building, ContainerAccess containers)`, `PRIORITY = 200`, `servesOnly() = building.requesterId()`. `available()` lit `containers.contents(building.containers())` moins les livraisons des autres requêtes du bâtiment. C'est le modèle du futur `WarehouseResolver` (priorité MC 150, sans `servesOnly`).
- Autres lecteurs de `hut.containers()` : `BuilderStock` (l. 59, 70, 109, 139, 176), `RequestActions` (l. 90, 111) et `BuilderResourcesViews` (l. 56, 104).
- `plugin/.../ui/HutStorage.java` : `HutStorage(PlayerRef, ColonyManager, BlockPos)` (l. 30), `boolean mayOpen()` (l. 37), `void open(Ref<EntityStore>, Store<EntityStore>)` (l. 45). La méthode ouvre **uniquement le bloc de hutte** dans une `ContainerBlockWindow`. À la fermeture, elle appelle `manager.requestActions().onContainerChanged(pos)` (l. 81). `RequestActions.onContainerChanged(BlockPos)` (l. 123) passe par `owningContainer` et accepte donc déjà une étagère enregistrée. Mais **seul `HutStorage` l'appelle** : un joueur qui remplit à la main un coffre enregistré de l'entrepôt ne déclenche rien aujourd'hui.

### 4.4 Ce que l'entrepôt peut reprendre tel quel
- Ses étagères = `Building.registeredContainers()`, remplies par le constructeur via `hasContainer`. Il n'y a rien à ajouter tant que le plan contient de vrais blocs conteneurs (§ 3.1 : il faut (a) ou (b)).
- Lecture et écriture du stock : `ContainerAccess` sur `building.containers()`.
- Point à trancher dans le plan : `HytaleWorldBlocks.place` ignore `withContainer`. Si (a) est retenu, le coffre substitué doit arriver dans le `BlueprintEntry` avec `hasContainer = true` et son id de coffre, **sans** la `Droplist` du spawner.
