# Le pêcheur de MineColonies (analyse pour un mod de pêche Hytale)

Analyse exacte du pêcheur de MineColonies (`sources/minecolonies`, branche `version/main`, commit `bf9df9f0`,
Minecraft 1.20.1 d'après `sources/minecolonies/gradle.properties:35`), écrite pour concevoir un mod de pêche Hytale
autonome dont HyColony utilisera l'API. Préfixe des chemins Java : `MC/` =
`sources/minecolonies/src/main/java/com/minecolonies/`. Ticks : 20 ticks = 1 s.

Note de version : `gradle.properties:19` épingle aujourd'hui `hytale_version = 0.7.0-pre.5` (le cache d'assets ne
contient que `pre-release-0.7.0-pre.5-Assets.zip`), et non pre.4.

## 1. Le bâtiment : `BuildingFisherman`

- Classe minimale (`MC/core/colony/buildings/workerbuildings/BuildingFisherman.java`) : schéma `"fisherman"` (l. 25,
  46-49), **niveau max 5** (l. 21, 57-60). Elle ne stocke **aucune** position d'eau : les étangs sont dans le métier
  (§ 2).
- `keepX` : garde **1** canne à pêche (`fishing_rod`, niveau `TOOL_LEVEL_WOOD_OR_GOLD`=0 à `getMaxEquipmentLevel()`)
  au vidage, aussi dans l'inventaire (`new Tuple<>(1, true)`, l. 36 ; sémantique de `keepX` :
  `MC/core/colony/buildings/AbstractBuildingContainer.java:48-51`).
- Enregistrement (`MC/apiimp/initializer/ModBuildingsInitializer.java:218-226`) : vue `EmptyView`, modules
  `FISHER_WORK`, `MIN_STOCK`, `STATS_MODULE`. **Pas de module de réglages** (aucun `SettingsModule`).
- `FISHER_WORK` (`MC/core/colony/buildings/modules/BuildingModules.java:211-214`) :
  `new WorkerBuildingModule(ModJobs.fisherman, Skill.Focus, Skill.Agility, false, (b) -> 1)` : primaire **Focus**,
  secondaire **Agility**, `canWorkingDuringRain = false`, **1 ouvrier** à tous les niveaux.
- Ce que débloque chaque niveau (aucune autre différence dans le code) :
  - **niveau d'outil max** (`MC/api/colony/buildings/IBuilding.java:447-458`, `WOOD_HUT_LEVEL = 0` l. 48) : niveau ≤ 0
    → 1 (`BASIC_TOOL_LEVEL`), niveaux 1 à 4 → `niveau - 0` (1, 2, 3, 4), niveau 5 (= max) → `TOOL_LEVEL_MAXIMUM`
    (`Integer.MAX_VALUE`) ;
  - **table de bonus** `minecolonies:fisherman/bonusN` par niveau (§ 5) : vide aux niveaux 1-2, prismarine au 3,
    + éponge aux 4-5 ;
  - **prérequis de recherche** : « Sieving » demande un pêcheur niveau 3
    (`MC/core/generation/defaults/DefaultResearchProvider.java:1660-1667`), « Ocean's Heart » un pêcheur niveau 4
    (l. 1559-1567) ;
  - avertissement de résidence : avant le niveau 2 d'une maison, la colonie doit avoir une ferme **ou un pêcheur** de
    niveau ≥ 1 (`MC/core/colony/buildings/views/LivingBuildingView.java:100-101` ; déjà noté côté HyColony dans
    `core/.../app/view/BuildingViews.java:78`, « fisher, not ported »).
- Plans : `fisherman1..5`, `altfisherman1..5` et variantes par style (`src/main/resources/blueprints/minecolonies/*/
  agriculture/husbandry/fisher*`). Leur eau passe par `structurize:blockfluidsubstitution`
  (`docs/research/structurize-placeholders.md:154, 162`).

### Étangs (`Pond`) : validation

`MC/api/util/Pond.java` :

- États `INVALID`, `SUBOPTIMAL` (eau courante sous la surface), `VALID` (l. 24-29).
- Constantes : `WATER_POOL_WIDTH_REQUIREMENT = 5`, `WATER_DEPTH_REQUIREMENT = 2` (l. 33-34).
- `checkPond(world, water, problemPos)` (l. 44-78) : parcourt en spirale **5×5** autour de `water`
  (`BlockPos.spiralAround(water, (5-1)/2 = 2, SOUTH, EAST)`, l. 48) ; pour chaque colonne, teste jusqu'à 2 blocs de
  profondeur (`y = 0, 1`). Un bloc `INVALID` → `INVALID` immédiat (et `problemPos` rempli) ; un `SUBOPTIMAL` dégrade
  le résultat. Après chaque bloc testé, **30 % de chance d'arrêter la colonne** (`rand.nextInt(100) < 30`, l. 70 ;
  commentaire « 70% chance to check, to on avg prefer cleared areas ») : la validation est donc **aléatoire**.
- `checkWaterForFishing(world, pos)` (l. 87-110) : `INVALID` si air ou nénuphar ; sinon fluide eau **sans forme de
  collision** → `VALID` si source, `SUBOPTIMAL` si courante ; tout le reste `INVALID`.
- À vérifier : la boucle fait `tempPos.setY(tempPos.getY() - y)` sur le curseur mutable rendu par `spiralAround`
  (l. 54). Si le curseur vanilla avance par déplacements relatifs, cette mutation décale vers le bas les colonnes
  suivantes. Le code vanilla de `spiralAround` n'est pas dans `sources/` : non vérifié.

### Étangs : recherche (`PathJobFindWater`)

`MC/core/entity/pathfinding/pathjobs/PathJobFindWater.java` (recherche A* asynchrone,
`MC/core/entity/pathfinding/pathresults/WaterPathResult.java`) :

- Heuristique et score de fin : distance de Manhattan **à la hutte** (l. 68-71, 144-147) : la recherche préfère l'eau
  proche de la hutte.
- `MAX_RANGE = 100` (l. 32) : un nœud à plus de 100 blocs (distance euclidienne) de la hutte n'est jamais une
  destination (l. 76-79), et y coûte ×10 (l. 127-130). La nage ne coûte rien (`swimCostEnter = swimCost = 0`,
  l. 136-141).
- Destination (l. 74-114) : nœud **en nage**, `checkPond` du bloc sous le nœud ≠ `INVALID`, à une distance de
  Manhattan **≥ 7** (`WATER_POOL_WIDTH_REQUIREMENT + 2`) de chaque étang déjà connu (l. 86-92), et une rive trouvée
  par `PathJobFindFishingPos` (l. 94-103). Le résultat garde `pond` (eau), `parent` (rive où se tenir) et
  `pondState`.
- `PathJobFindFishingPos` (l. 152-207) : recherche inverse depuis le nœud d'eau, rayon `10` (l. 94) : premier nœud
  **hors de l'eau**, à moins de 10 blocs (Manhattan) de l'eau, sur une surface marchable et un bloc solide
  (l. 182-188), **avec une ligne de vue** vers l'eau (`hasAnyCollisionAlong`, l. 197-200).
- Taille de la recherche : `AbstractPathJob(range)` borne les nœuds à `min(8000, range²) × pathNodeLimitMultiplier`
  et la zone à ±1,3 × `range` autour du départ (`MC/core/entity/pathfinding/pathjobs/AbstractPathJob.java:181-208`).
  Le pêcheur cherche avec `range = SEARCH_RANGE × 3 = 150` puis `50` (§ 3).
- `WaterPathResult.isEmpty` (l. 30) n'est **jamais écrit** dans le dépôt (grep) : il vaut toujours `false`.

## 2. Le métier : `JobFisherman`

`MC/core/colony/jobs/JobFisherman.java` :

- État : `water` = `Tuple<BlockPos eau, BlockPos rive>` courant (l. 34) ; `ponds` = liste des étangs connus, même
  forme (l. 42). Accès : `getWater/setWater` (l. 136-149), `getPonds` (copie, l. 157), `addToPonds` (l. 168),
  `removeFromPonds` (l. 178).
- Persistance NBT (l. 67-111) : `water` sous `waterpond`/`parentpond`, la liste sous `newPonds`
  (`MC/api/util/constant/NbtTagConstants.java:710-712`). Le bouchon (bobber) n'est **pas** sauvegardé.
- `onStackPickUp` (l. 190-195) : chaque pile ramassée incrémente la statistique `ITEM_OBTAINED` de la hutte (nom de
  l'objet, quantité).
- Compétences (module, § 1) : **Focus** (primaire) agit sur la **vitesse d'appât** (`lureSpeed`) et sur la **chance
  de la table de bonus** ; **Agility** (secondaire) agit sur la **chance de ne pas hésiter** au lancer et au ferrage
  (§ 3). Les commentaires « depends on intelligence » (`EntityAIWorkFisherman.java:517, 584`) sont périmés.
- XP : uniquement par **orbes** : 2 par objet de la table principale (`NewBobberEntity.XP_PER_CATCH = 2`, l. 50, 531-535)
  et 2 par objet de bonus (`EntityAIWorkFisherman.java:715-719`), ramassées par
  `CitizenExperienceHandler.gatherXp` (`MC/core/entity/citizen/citizenhandlers/CitizenExperienceHandler.java:163-205`,
  avec Raccommodage via `applyMending`) puis `addExperience`.
- Effets de recherche : `effects/fishingtreasure` (`MC/api/research/util/ResearchConstants.java:170`), donné par
  « Ocean's Heart » (coût : 1 cœur de la mer ; pêcheur niveau 4 ;
  `src/datagen/generated/minecolonies/data/minecolonies/researches/technology/oceanheart.json`) : le trésor hors de
  l'océan (§ 5). Effets génériques : `workinginrainunlock` (travail sous la pluie,
  `MC/core/entity/ai/workers/CitizenAI.java:305-323`) et `TOOL_DURABILITY` (chance d'épargner la canne,
  `MC/core/util/citizenutils/CitizenItemUtils.java:221-231`).
- Statistique `FISH_CAUGHT = "fish_caught"` (`MC/api/util/constant/StatisticsConstants.java:21`) : importée par l'IA
  (`EntityAIWorkFisherman.java:52`) mais **jamais incrémentée** (grep).

## 3. L'IA : `EntityAIWorkFisherman`

`MC/core/entity/ai/workers/production/agriculture/EntityAIWorkFisherman.java`, sur `AbstractEntityAISkill`
(qui n'ajoute rien à `AbstractEntityAIBasic`, `MC/core/entity/ai/workers/AbstractEntityAISkill.java`).

### Constantes

| Constante | Valeur | Ligne | Rôle |
|---|---|---|---|
| `SUBOPTIMAL_POND_COMPLAINT_DISTANCE` | 12 | 80 | distance (blocs) sous laquelle la plainte « étang médiocre » reste valide |
| `MAX_PONDS` | 20 | 85 | au-delà, il ne cherche plus d'étang neuf |
| `FISHING_SKILL_CHANCE` | 10 | 90 | base du tirage d'hésitation |
| `CHANCE` | 2 | 95 | seuil du tirage d'hésitation |
| `MIN_DISTANCE_TO_WATER` | 3 | 100 | rayon du test « eau proche » |
| `MAX_FISHES_IN_INV` | 10 | 105 | prises avant vidage (`getActionsDoneUntilDumping`, l. 226-229) |
| `MAX_ROTATIONS` | 6 | 110 | passages par `CHECK_WATER` avant d'abandonner l'étang |
| `SEARCH_RANGE` | 50 | 115 | portée de recherche (×3 pour un étang neuf) |
| `CHANCE_NEW_POND` | 0.05 | 120 | chance de changer d'étang après une prise |
| `FISHING_TIMEOUT` | 5 | 125 | délai (ticks) posé à chaque tirage d'hésitation |
| `LURE_SPEED_DIVIDER` | 25 | 130 | points de Focus par niveau d'appât |
| `stuckCounter` (init) | 3 | 158 | compteur de bouchon coincé |

Cadence : l'IA tourne tous les `ENTITY_AI_TICKRATE = 5` ticks
(`MC/api/entity/citizen/AbstractEntityCitizen.java:64, 417`) ; chaque cible a son intervalle en ticks
(`MC/api/entity/ai/statemachine/tickratestatemachine/TickRateStateMachine.java:117-127`). `setDelay(n)` bloque toute
l'IA par l'événement `waitingForSomething` (intervalle 5, décompte de 5 par tick d'IA,
`MC/core/entity/ai/workers/AbstractEntityAIBasic.java:497-516`).

### Machine d'état (l. 168-176)

| État | Intervalle | Action | Interruptible (manger, vider) |
|---|---|---|---|
| `IDLE` | 1 | → `START_WORKING` | — |
| `START_WORKING` | 20 | `startWorkingAtOwnBuilding` | — |
| `PREPARING` | 20 | `prepareForFishing` | — |
| `FISHERMAN_CHECK_WATER` | 1 | `tryDifferentAngles` | oui |
| `FISHERMAN_SEARCHING_WATER` | 20 | `findWater` | oui |
| `FISHERMAN_WALKING_TO_WATER` | 20 | `getToWater` | oui |
| `FISHERMAN_START_FISHING` | 20 | `doFishing` | **non** |

Le drapeau « interruptible » est `isOkayToEat` (`MC/api/entity/ai/statemachine/states/AIWorkerState.java:70-76, 698`),
lu par `canBeInterrupted` (`MC/core/entity/ai/workers/AbstractAISkeleton.java:126-129`) : le vidage
(`inventoryNeedsDump`, `AbstractEntityAIBasic.java:409-416`, événement d'intervalle 100) n'a donc jamais lieu en
pleine pêche, mais au passage suivant par `WALKING_TO_WATER`. Les états communs (vidage `INVENTORY_FULL`, `NEEDS_ITEM`,
etc.) sont ceux d'`AbstractEntityAIBasic` (l. 200-260).

Transitions :

1. **`START_WORKING`** (l. 191-198) : marche à la hutte (`walkToBuilding`) ; arrivé → `PREPARING`.
2. **`PREPARING`** (l. 205-215) : `checkForToolOrWeapon(fishing_rod)` (§ 6). Sans canne : main vide, son
   `MISSING_EQUIPMENT`, reste en `PREPARING`. Avec → `WALKING_TO_WATER`.
3. **`WALKING_TO_WATER`** (`getToWater`, l. 278-308) : `water == null` → `SEARCHING_WATER`. Sinon marche vers la
   **rive** (`walkToSafePos(water.B)`, à 4 blocs près, `AbstractEntityAIBasic.java:830-833`). Arrivé : si le dernier
   résultat de recherche est `SUBOPTIMAL`, plainte `SUBOPTIMAL_POND` (x, y, z de l'eau) et → `SEARCHING_WATER` ;
   sinon → `CHECK_WATER`.
4. **`CHECK_WATER`** (`tryDifferentAngles`, l. 326-359) :
   - `water == null` → `SEARCHING_WATER` ;
   - `executedRotations ≥ 6` → retire l'étang de la liste, `water = null`, remise à 0 → `SEARCHING_WATER` ;
   - s'il se tient dans un liquide : si le bloc sous la rive n'est pas solide → retire l'étang, `water = null`,
     remise à 0 → `START_WORKING` ; sinon `executedRotations++` ;
   - se tourne vers l'**eau** (`WorkerUtil.faceBlock(water.A)`), `executedRotations++` → `START_FISHING`.
   - Conséquence : chaque prise et chaque bouchon coincé repassent par `WALKING_TO_WATER` puis `CHECK_WATER` ; le
     compteur n'est remis à 0 que dans `findWater`. Au bout d'environ **6 lancers** au même étang, le pêcheur
     l'oublie et en cherche un autre : c'est la « rotation des coins de pêche ».
5. **`SEARCHING_WATER`** (`findWater`, l. 366-379) : remet `executedRotations` à 0. Si `ponds.size() ≥ 20`, ou si la
   dernière recherche a abouti sans étang alors que la liste n'est pas vide → `setRandomWater` ; sinon
   `findNewWater`.
   - `findNewWater` (l. 439-467) : lance `searchWater(150)` et attend. Échec → `setRandomWater`. Succès : si un étang
     est trouvé, `water` = (eau, rive) et ajout à la liste ; → `CHECK_WATER`. Annulé → `PREPARING`.
   - `setRandomWater` (l. 409-431) : liste vide → plainte `WATER_TOO_FAR` si la recherche a échoué sans résultat
     antérieur (la branche `lastPathResult.isEmpty` est morte, § 1), relance `searchWater(50)` si aucune n'est en
     cours, → `START_WORKING`. Sinon un étang **au hasard** de la liste → `CHECK_WATER`.
6. **`START_FISHING`** (`doFishing`, l. 476-498) :
   - `isReadyToFish` (l. 601-627) : pas de canne dans l'inventaire (niveau 0 à max de la hutte) → main vide,
     `PREPARING` ; pas de bloc `Blocks.WATER` dans le cube `[pos-3, pos+3[` (6×6×6, `MC/api/util/Utils.java:52-68`)
     → `WALKING_TO_WATER` ; canne pas en main → l'équipe (`CitizenItemUtils.setHeldItem`), reste ;
   - `caughtFish()` (l. 654-673) : vrai si un bouchon existe, est `readyToCatch` et que le tirage d'hésitation passe.
     Il ramène alors la canne (`retrieveRod`) ; puis son `SUCCESS`, `incrementActionsDoneAndDecSaturation()`
     (+0,2 de faim d'action, `MC/api/util/constant/CitizenConstants.java:137`), et **5 %** → `water = null`,
     `SEARCHING_WATER`, sinon `WALKING_TO_WATER` ;
   - sinon `throwOrRetrieveHook` (l. 513-544) : sans bouchon, tirage d'hésitation, puis `throwRod` ; avec bouchon,
     test « coincé » (ci-dessous), puis `setInUse()` (le bouchon vit encore 100 ticks).

### Formules

- **Tirage d'hésitation** `testRandomChance` (l. 588-594) : pose `setDelay(5)`, puis
  `nextInt(10 + Agility/5) <= 2` → il « attend » ce tour. Probabilité d'attendre = `3 / (10 + ⌊Agility/5⌋)` : 30 % à
  Agility 0, 15 % à 50, ≈10,3 % à 99. Appelé avant chaque lancer et à chaque ferrage possible.
- **Rythme** : `doFishing` toutes les 20 ticks, plus environ 5 ticks quand le tirage pose son délai (un tick d'IA
  bloqué) ; soit environ 25 ticks entre deux essais (calcul tiré du source, **[in-game]**).
- **Lancer** `throwRod` (l. 549-568) : se tourne vers l'eau, son `FISHING_BOBBER_THROW` (volume 0,5, hauteur
  `0.4 / (rand*0.4 + 0.8)`), crée `NewBobberEntity` avec `setAngler(citoyen, luck, lureSpeed)` où
  `luck = EnchantmentHelper.getFishingLuckBonus(canne)` (Chance de la mer) et
  `lureSpeed = 5 + Focus/25 + EnchantmentHelper.getFishingSpeedBonus(canne)` (Appât ; division entière), geste du
  bras.
- **Bouchon coincé** `isFishHookStuck` (l. 575-579) : `(!inWater && (onGround || shouldStopFishing())) || !isAlive ||
  caughtEntity != null`. `stuckCounter` démarre à 3 : il ramène la canne quand il dépasse 3 (2ᵉ constat au premier
  lancer, puis 5 constats de suite, soit ≈100 ticks), → `WALKING_TO_WATER`. Un constat non coincé le remet à 0.
- **Ramener** `retrieveRod` (l. 678-688) : geste, `damage = bouchon.getDamage()` (qui génère la prise principale,
  § 4), **puis toujours** `generateBonusLoot()`, puis `damageItemInHand(main, damage)`. La table de bonus est donc
  tirée aussi quand rien n'a mordu, y compris pour un bouchon coincé.
- **Usure de la canne** : `damage` vaut 1 si un poisson était ferrable, 0 sinon (§ 4). `damageItemInHand`
  (`CitizenItemUtils.java:211-241`) saute l'usure avec la chance `1 - 1/(1+TOOL_DURABILITY)` si la recherche est
  active, puis `InventoryCitizen.damageInventoryItem` → `hurtAndBreak` vanilla
  (`MC/api/inventory/InventoryCitizen.java:366-373` ; Solidité appliquée par vanilla, non vérifié localement). Canne
  cassée → main vide, `PREPARING` → nouvelle requête.
- **Bonus** `generateBonusLoot` (l. 693-721) : table `FISHERMAN_BONUS[niveau de la hutte]`, chance =
  **Focus** (`withLuck(getPrimarySkillLevel())`), chaque objet est lancé vers le pêcheur comme la prise et donne une
  orbe de 2 XP.

### Ramassage, rendu, nuit et pluie

- `worker.setCanPickUpLoot(true)` (l. 177, 670) : les objets lancés vers lui (`noPhysics`, vitesse
  `(dx*0.1, dy*0.1 + sqrt(dist)*0.08, dz*0.1)`) sont ramassés comme des entités d'objet ; `onStackPickUp` compte la
  statistique.
- Rendu (l. 235-271) : métadonnées `"rod"`, `"fish"` ou `"rodfish"` selon qu'il a un poisson (`ItemTags.FISHES`) et
  une canne non tenue (modèle du citoyen).
- **Pluie** : `canWorkingDuringRain = false` → `CitizenAI` le met en `IDLE` (`BAD_WEATHER`) quand il pleut, sauf
  config `workersalwaysworkinrain` ou recherche `workinginrainunlock` (`CitizenAI.java:231-240, 305-323`). Le bonus
  « pluie » du bouchon (§ 4) ne sert donc que dans ces deux cas.
- **Nuit** : rien de propre au pêcheur ; le sommeil est celui de tous les citoyens (`CitizenAI`).

## 4. Le bouchon : `NewBobberEntity`

`MC/core/entity/other/NewBobberEntity.java` (un `Projectile`, pas le `FishingHook` vanilla) :

- **Durées de vie** : `tickRemove = 100` décompté chaque tick, retiré à 0 (l. 54, 186-193) ; l'IA le remet à 100 à
  chaque `doFishing` (`setInUse`, l. 635-638). Retiré si le pêcheur meurt, ne tient plus de canne
  (`ToolActions.FISHING_ROD_CAST`) ou s'éloigne de plus de 32 blocs (`distanceSqr > 1024`, `shouldStopFishing`,
  l. 310-325). Pas de sauvegarde (l. 152-162).
- **Lancer** `setAngler` (l. 100-125) : départ aux yeux, 0,3 bloc devant ; tangage aléatoire `Math.random()*40 - 10`
  (−10° à 30°), lacet du citoyen ; vitesse = direction × `(0.6/len + 0.5 + gauss*0.0045)` par axe. La portée découle
  de la physique : le bouchon **ne vise pas** le bloc d'eau, seul le regard du pêcheur l'oriente.
- **Physique** `tick` (l. 182-308) : `FLYING` : gravité −0,03/tick hors de l'eau (l. 298-301), freinage ×0,92
  (l. 305) ; au contact de l'eau (`FluidTags.WATER`, hauteur > 0) vitesse ×(0,3 ; 0,2 ; 0,3) → `BOBBING` (l. 239-244) ;
  `BOBBING` : oscillation vers la surface, ×0,9 horizontal (l. 281-295), et appel de `catchingFish` côté serveur.
- **Code mort** : `inGround` n'est jamais mis à vrai (l. 51, 212, 251, 541 ; aucune affectation) ; `setHookedEntity`
  (l. 366-369) n'est jamais appelé et `onHitEntity` n'est pas redéfini : `caughtEntity` reste `null` côté serveur,
  l'état `HOOKED_IN_ENTITY` et les usures 2, 3 et 5 ne surviennent pas.
- **Morsure** `catchingFish` (l. 371-496), à chaque tick en `BOBBING` :
  - pas du compte à rebours `i = 1`, **+1** avec 25 % de chance s'il pleut au bloc au-dessus, **−1** avec 50 % de
    chance s'il ne voit pas le ciel (l. 374-384) ;
  - si aucun compte : `ticksCaughtDelay = U[1060, 1300] - lureSpeed × 100`, au moins 5 (l. 490-495) ;
  - `ticksCaughtDelay` décroît de `i` (éclaboussures de plus en plus fréquentes, l. 454-483) ; à 0 :
    `ticksCatchableDelay = U[20, 80]` (approche du poisson, bulles et traînée, l. 484-488, 399-423) ;
  - à 0 : **`readyToCatch = true`**, position mémorisée, plongée du bouchon, son `FISHING_BOBBER_SPLASH`,
    `ticksCatchable = U[20, 40]` (fenêtre de ferrage, l. 424-452) ;
  - `ticksCatchable` décroît de 1 ; à 0 les comptes repartent (l. 386-398), mais **`readyToCatch` n'est jamais remis
    à faux** : le ferrage suivant de l'IA réussit alors sans prise (usure 0, pas de table principale, mais table de
    bonus tirée).
  - Exemple : Focus 0 sans Appât → `lureSpeed = 5` → 560 à 800 ticks, + 20-80, fenêtre 20-40. Focus 100 + Appât III →
    `lureSpeed = 12` → `max(5, -140..100)` = 5 à 100 ticks.
- **Comparaison vanilla** (wiki, pas de code vanilla local) : vanilla attend 100 à 600 ticks, −100 par niveau
  d'Appât, mêmes règles pluie et ciel, approche 1 à 4 s, fenêtre 1 à 2 s (https://minecraft.wiki/w/Fishing). MC
  part de 1060-1300 avec un Appât de base 5 : un pêcheur sans Focus ni Appât attend 560-800 ticks, plus qu'un joueur.
- **Remise de la prise** `getDamage` (l. 498-553) : si `ticksCatchable > 0`, tire la table `minecolonies:fisherman`
  (`ModLootTables.FISHING`) avec `ORIGIN` = bouchon, `TOOL` = canne, `THIS_ENTITY` = bouchon, `KILLER_ENTITY` =
  pêcheur, `luck` = Chance de la mer ; chaque objet part de la position mémorisée vers le pêcheur et donne une orbe de
  2 XP ; usure 1. Sinon usure 0. Puis le bouchon disparaît.

## 5. Butin

Tables générées par `MC/core/generation/defaults/workers/DefaultFishermanLootProvider.java` (JSON dans
`sources/minecolonies/src/datagen/generated/minecolonies/data/minecolonies/loot_tables/fisherman*`), identifiants
dans `MC/api/loot/ModLootTables.java:16-38`.

- **`minecolonies:fisherman`** (l. 75-84, type `minecraft:fishing`), un tirage :
  - `fisherman/junk` poids **10**, qualité **−2** → `minecraft:gameplay/fishing/junk` ;
  - `fisherman/treasure` poids **5**, qualité **+2**, **condition** : `entity_in_biome_tag minecraft:is_ocean` (biome
    du bouchon, `MC/api/loot/EntityInBiomeTag.java:47-60`) **ou** `research_unlocked effects/fishingtreasure` (colonie
    du pêcheur, du bouchon ou du lieu, `MC/api/loot/ResearchUnlocked.java:80-106`) →
    `minecraft:gameplay/fishing/treasure` ;
  - `fisherman/fish` poids **85**, qualité **−1** → `minecraft:gameplay/fishing/fish`.
  - Écart de MC sur vanilla : la règle d'**eau libre** du trésor (zone 5×4×5 d'eau source ou d'air) est remplacée par
    « océan ou recherche ». Mêmes poids et qualités que vanilla.
- **Poids effectif** (vanilla) : `max(floor(poids + qualité × chance), 0)` (https://minecraft.wiki/w/Loot_table).
  Chance de la table principale = niveau de Chance de la mer seul (le joueur y ajoute l'attribut Chance, absent ici).
- **Bonus par niveau** (l. 102-135) : niveaux 1-2 vides ; niveau 3 : éclat de prismarine 25 (q +1), cristaux de
  prismarine 25 (q +1), rien 950 ; niveaux 4-5 : éponge 1 (q +1), éclat 25, cristaux 25, rien 949. Chance = Focus.
  Table du commentaire (l. 108-113) : à Focus 50, éponge 4,43 %, éclat 6,52 %, cristaux 6,52 %.
- **Sous-tables vanilla** (wiki https://minecraft.wiki/w/Fishing, non vérifié dans le code) : poisson (cabillaud 60,
  saumon 25, poisson-globe 13, poisson tropical 2) ; trésor (arc, livre enchanté, canne enchantée, étiquette, coquille
  de nautile, selle, poids égaux ; arc et canne 0-25 % de durabilité) ; déchets (nénuphar 17, os, bol, cuir, bottes en
  cuir, chair putréfiée, crochet, fiole d'eau 10, bâton et ficelle 5, canne 2, poche d'encre 1 ; bambou et fèves de
  cacao en jungle). Catégories sans enchantement : poisson 85 %, trésor 5 %, déchets 10 % ; Chance de la mer III :
  84,54 / 11,34 / 4,12 %.
- JEI : `MC/core/compatibility/jei/FishermanRecipeCategory.java:91-94` affiche la table principale et les bonus par
  niveau (`com.minecolonies.coremod.jei.fisherman`).

## 6. Requêtes et messages

- **Canne** : `checkForToolOrWeapon(fishing_rod)` (`AbstractEntityAIBasic.java:966-1005, 1084-1095`) : sans canne de
  niveau `[0, maxHutte]` dans l'inventaire, il va à la hutte en prendre une ; sinon une requête
  `Tool(fishing_rod, min 0, max = getMaxEquipmentLevel())` (une seule ouverte), statut `JobStatus.STUCK`
  (`AbstractEntityAIBasic.java:966-978`), délai `DELAY_RECHECK = 10` ajouté.
- **Niveau d'une canne** (`MC/api/equipment/ModEquipmentTypes.java:110-114, 216-224, 279-282`) :
  `min(maxDamage / maxDamage(canne vanilla), 5)` : 1 pour la canne vanilla ; plus `max(niveauEnchantMax − 1, 0)`
  (`MC/api/util/ItemStackUtils.java:264-300`). Hutte 1 : canne vanilla avec enchantements de niveau ≤ I ; hutte 2 :
  ≤ II ; hutte 3 : ≤ III ; hutte 4 : ≤ IV ; hutte 5 : tout.
- **Nourriture** : aucune requête propre ; faim générique (+0,2 par prise).
- **Messages** (`MC/api/util/constant/TranslationConstants.java:320, 322` ; textes
  `src/main/resources/assets/minecolonies/lang/manual_en_us.json:1444-1445`) :
  - `entity.fisherman.messagewatertoofar` (`StandardInteraction`, `IMPORTANT`) : « I can't find any suitable water
    for fishing! There should be an area of water at least 7 blocks long and wide, and 2 blocks deep nearby. » ;
    valide tant que la liste d'étangs est vide (`MC/apiimp/initializer/InteractionValidatorInitializer.java:181-182`) ;
  - `entity.fisherman.messagesuboptimalpond` (`PosBasedInteraction`, `IMPORTANT`, `%d` x, y, z) : « This pond at at
    position X:%d Y:%d Z:%d isn't great - the currents under the surface are too strong… » ; valide tant que le
    pêcheur est à 12 blocs au plus de l'étang (l. 184-188).
  - Autres textes : description du métier et des compétences (`manual_en_us.json:2405, 2454`), guide
    `com.minecolonies.coremod.info.fisherman.0..3` (l. 1586-1593 ; il parle de 7×7, profondeur 1 et 10 blocs, ce qui
    ne correspond pas au code : 5×5, profondeur 2, rive à moins de 10 blocs de l'eau).

## 7. Fenêtre

Rien de propre à la pêche. `EmptyView.getWindow` (hérité, `MC/core/colony/buildings/views/AbstractBuildingView.java:
391-398`) ouvre `WindowHutWorkerModulePlaceholder` (`gui/windowhutworkerplaceholder.xml`) puisque la hutte a un
`WorkerBuildingModuleView`. Onglets : ouvrier (`FISHER_WORK`), stock minimum (`layoutminimumstock.xml`),
statistiques (`WindowStatsModule`, `layoutstatsmodule.xml`, icône `textures/gui/modules/stats.png`,
`MC/core/colony/buildings/moduleviews/BuildingStatisticsModuleView.java:31-46`). Aucun fichier `gui/` ne contient
« fish » (grep).

## 8. Configuration

Aucune clé propre au pêcheur dans `MC/api/configuration/ServerConfiguration.java` (grep « fish »). Clés génériques
qui le touchent : `workersalwaysworkinrain` (défaut `false`, l. 132) et `pathNodeLimitMultiplier` (défaut 1, bornes
1-4, l. 199 ; taille de la recherche d'eau).

## 9. Contrat qu'un mod de pêche doit offrir pour un portage fidèle

Ce que l'IA, le métier et la hutte demandent au monde, aujourd'hui couvert par Minecraft :

1. **Étang valide** : `pondState(waterPos) → INVALID | SUBOPTIMAL | VALID` sur une zone 5×5 et 2 de profondeur
   (`Pond.checkPond`), avec la case fautive pour le débogage. Bloc par bloc : « eau sans collision, source ou
   courante » (`checkWaterForFishing`).
2. **Trouver un coin de pêche** depuis le pêcheur, asynchrone et borné (100 blocs de la hutte, ≥ 7 blocs de chaque
   étang connu, préférence pour l'eau proche de la hutte) : `findFishingSpot(start, hut, range, knownPonds) →
   Optional<(water, shore, pondState)>`, avec « en cours », « échoué », « annulé ». La rive : marchable, sol solide,
   hors de l'eau, à moins de 10 blocs de l'eau, avec une ligne de vue.
3. **Eau à portée** : « un bloc d'eau dans le cube `[p-3, p+3[` » (`isReadyToFish`).
4. **Sol sous la rive solide** et « le pêcheur se tient dans un liquide » (`tryDifferentAngles`).
5. **Reconnaître une canne** et son **niveau** (`isRod(item)`, `rodLevel(item)` = palier + enchantement max − 1), ses
   bonus **Chance** et **Appât** (`luck(rod)`, `lure(rod)`).
6. **Lancer** : `cast(angler, facing, luck, lureSpeed) → bobberId` (un projectile physique qui tombe dans l'eau ou à
   côté), son de lancer.
7. **État du bouchon** chaque seconde : `inWater`, `onGround`, `alive`, `readyToCatch`, et `keepAlive()` (sinon il
   disparaît en 100 ticks, ou si le pêcheur est à plus de 32 blocs ou sans canne).
8. **Temps de morsure** : `U[1060,1300] − 100 × lureSpeed` (≥ 5), pas de 1 / +1 (pluie, 25 %) / −1 (sans ciel,
   50 %), puis approche `U[20,80]` et fenêtre `U[20,40]`. Il lit **pluie au bloc** et **ciel visible**.
9. **Ramener** : `reel(bobberId) → (rodDamage, loot)` : si la fenêtre est ouverte, tirage de la table principale
   (chance = Chance de la mer, biome du bouchon, recherche de la colonie) ; usure 1 ou 0 ; objets lancés vers le
   pêcheur ; orbe de 2 XP par objet.
10. **Tables de butin** paramétrables : principale (poisson 85/−1, déchets 10/−2, trésor 5/+2 si océan ou recherche),
    bonus par niveau de hutte avec chance = compétence, formule `max(floor(w + q × luck), 0)`, et le prédicat
    « biome océan » au point du bouchon.
11. **User la canne** : `damageRod(citizen, amount)` avec la recherche de durabilité et la Solidité, retour « cassée ».
12. **Visuels** : se tourner vers l'eau, geste de lancer et de ferrage, sons de prise et d'équipement manquant,
    particules d'approche et d'éclaboussure (optionnel, monde Hytale).

Côté Hytale (assets 0.7.0-pre.5, simple listage `unzip -l`, sans étude) : il n'y a **pas de canne à pêche** ;
existent des poissons (`Server/Item/Items/Fish/*`, types de ressource `Server/Item/ResourceTypes/Fish*.json`), un
piège à poissons (`Server/Item/Items/Tool/Tool_Fishing_Trap.json`, posé sur `Water_Source` ou `Water`, recette au
`Farmingbench` palier 2) et sa table de butin pondérée (`Server/Drops/Traps/Drops_Fishing_Trap_Crude.json`).

## 10. Points d'ancrage dans HyColony (noms seulement)

- Métier et IA : `core/.../job/Job`, `JobAI`, `JobType`, `JobRegistry`, `WorkerModule` (primaire, secondaire,
  `maxWorkers`, `workingInRain`), `JobXp`, `WorkStops` (pluie), `kernel/ai/TickRateStateMachine`, `AITarget`,
  `AIEventTarget`.
- Briques partagées (`core/.../job/work/`) : `WorkerMachine` (cadence de 5 ticks, attente), `WorkDelay`
  (`setDelay`), `WorkerStock` (vidage, `keepX`), `WorkerHands` (`faceBlock`, `swing`, objet tenu), `ToolRequests`
  (`checkForToolOrWeapon`), `SyncRequests`. Exemple complet : `farming/job/FarmerAI` et son port
  `farming/FarmingAccess` (port dans le paquet du domaine).
- Monde : `kernel/port/WorldQuery` (`isRainingAt`, `biome`), `kernel/item/ToolType` (pas encore de canne), et
  `FeaturePack` pour un module ajouté par un autre mod.
- Absents aujourd'hui : modules de stock minimum et de statistiques de hutte ; la mention « fisher, not ported » de
  `app/view/BuildingViews.java:78`.
