# Pêche : banc d'essai des systèmes et des API (2026-10-04)

But : préparer la conception d'un **6e mod de pêche autonome** pour Hytale, doté d'une vraie API publique, que le Pêcheur de HyColony (MineColonies `EntityAIWorkFisherman`) utilisera plus tard. Ce document recense ce qui existe (Minecraft vanilla, mods Minecraft, autres jeux, Hytale et ses mods) et en tire des patrons. Il ne fixe aucune conception.

Version épinglée : `gradle.properties` donne `hytale_version = 0.7.0-pre.5` (et non pre.4) ; les assets lus sont donc `pre-release-0.7.0-pre.5-Assets.zip`.

Limites de la recherche : reddit.com est inaccessible à l'outil de recherche (refus du domaine), donc les avis de joueurs viennent des pages de mods, des tests de presse et des wikis, pas de Reddit. Plusieurs pages du wiki de Tide se disent elles-mêmes « outdated as of Tide 2.0 » : là où c'était possible, le code source de Tide a été lu à la place.

## 1. Ce qu'a Hytale aujourd'hui (pre.5)

### 1.1 Pas de canne à pêche, mais un piège à poissons

- **Aucun objet canne** côté serveur : aucun fichier de `Server/` ne cite `FishingRod` ou `Fishing_Rod` (grep sur l'extraction du zip). Seul un modèle client existe : `zip:Common/Items/Tools/Fishing_Rod/FishingRod.blockymodel` et `FishingRod_Texture.png`. Le serveur décompilé n'a aucune classe de pêche (grep `fishing` sur `build/vineflower/hytale-server/com/hypixel/hytale` : rien).
- Les développeurs auraient dit, lors d'une FAQ de décembre 2025, qu'« un système de pêche complet et définitif est toujours en cours de conception » (rapporté par https://gamewave.fr/hytale/hytale-comment-pecher-et-attraper-des-poissons/ ; source primaire non trouvée). **Risque** : Hytale ajoutera sans doute un jour sa propre pêche. Le mod doit pouvoir s'y adosser (§ 6 de CLAUDE.md : le monde suit Hytale).
- **Piège à poissons** `Tool_Fishing_Trap` (`zip:Server/Item/Items/Tool/Tool_Fishing_Trap.json`) :
  - recette : 10 `Wood_All`, 50 `Ingredient_Life_Essence`, 20 `Ingredient_Fibre`, 1 `Ingredient_Bar_Iron`, au `Farmingbench` palier 2, 5 s ;
  - bloc posé sur de l'eau (`Support.Down` : `FluidId` `Water_Source` ou `Water`) ;
  - c'est un **bloc de culture** (`BlockEntity.Components.FarmingBlock`, `Farming.Stages`) : jeu d'étapes `Default` (40 000 à 60 000, puis état `StageFinal`) et `Baited` (20 000 à 40 000, puis `Baited_StageFinal`), modificateurs `Water` et `LightLevel` ; l'unité de `Duration` n'est pas lue ici **[in-game]** ;
  - la récolte (`HarvestCrop`) tire `Drops_Fishing_Trap_Crude` ou, appâté, `Drops_Fishing_Trap_Crude_Baited_Wild`, puis repasse à `Default` (`StageSetAfterHarvest`).
- **Appât** `Tool_Trap_Bait` (`zip:Server/Item/Items/Tool/Tool_Trap_Bait.json`) : 3 `Fruits` + 5 `Ingredient_Life_Essence` → 2 appâts, `Farmingbench` palier 2. Son interaction secondaire (`ChangeFarmingStage`, `StageSet: Baited`) ne vise que `Tool_Fishing_Trap` à l'état `default`, puis retire 1 appât.

### 1.2 Tables de butin du piège (choix pondérés)

`zip:Server/Drops/Traps/Drops_Fishing_Trap_Crude.json` (non appâté) : conteneur `Choice` dont les poids de catégorie sont

| Catégorie (`$Comment`) | Poids | Contenu |
|---|---|---|
| Common Fish | 100 | Bluegill, Catfish, Minnow ×1 |
| Common Resource | 99 | `Rock_Salt` 1-4 |
| Common Junk | 98 | `Rubble_Stone`, `Ingredient_Stick`, `Plant_Flower_Water_Duckweed` (1-5), `Deco_Trash`, `Ingredient_Poop`, 4 coquillages |
| Uncommon Junk | 49 | `Plant_Flower_Water_Green`, `Deco_Trash_Pile_Small`, `Ingredient_Water_Essence`, `Ingredient_Fabric_Scrap_Linen` 1-5 |
| Rare Junk | 9 | `Plant_Flower_Water_Purple`, `Deco_Trash_Pile_Large`, `Deco_Starfish`, `Deco_Treasure` |
| Epic Junk | 4 | fleurs d'eau rouge et bleue, `Deco_Treasure_Pile_Small` |
| Legendary Junk | 0,9 | `Weapon_Spear_Fishbone`, `Plant_Flower_Water_White`, `Ore_Gold` 1-4, `Deco_Treasure_Pile_Large` |
| Monster Fish | 0,01 | requin, Snapjaw, Frostgill, piranhas, trilobites, crabe, homard, murène, brochet, coquillage de lave, galère portugaise, baleine |

`Drops_Fishing_Trap_Crude_Baited_Wild.json` (appâté) ne contient que des poissons : Common 100 (1-4), Uncommon 50, Rare 10, Epic 5, Legendary 1, Monster 0,1. Les paliers Uncommon à Legendary contiennent les **états de rareté** des poissons communs (`*Fish_Bluegill_Item_State_Rare`…) en plus des espèces de ce palier.

### 1.3 Les 30 poissons et leur rareté

- 30 objets `Fish_*_Item` (`zip:Server/Item/Items/Fish/`), parent `Template_Fish_Item` : `Tags.Type: SpawnNPC`, `Family: Fish`, catégorie `Fish`. Utilisés, ils **font apparaître le PNJ vivant** (`InteractionVars.SpawnNPC_Entity`, `EntityId` ; `Template_Fish_Item.json`). Ils ne se mangent pas : le plan de cuisine les découpe en `Food_Fish_Raw` ×1/2/4/8/16 selon la rareté (`sp4b-hytale-food.md` § 3).
- Qualité de base : Common (Bluegill, Catfish, Minnow), Uncommon (4 Tang), Rare (Clownfish, Pufferfish, Salmon, Trout_Rainbow), Epic (5 méduses), Legendary (les 16 autres, dont crabe, homard, brochet, baleine).
- Chaque poisson a des **états** `Uncommon`, `Rare`, `Epic`, `Legendary` (bloc `State`, par exemple `Fish_Bluegill_Item.json` l. 41-104), chacun avec sa `Quality` et un ResourceType `Fish_<rareté>` (ResourceTypes `Fish`, `Fish_Common`, `Fish_Uncommon`, `Fish_Rare`, `Fish_Epic`, `Fish_Legendary` dans `zip:Server/Item/ResourceTypes/`). Anomalie vue dans les assets : l'état `Epic` du Bluegill porte `Fish_Rare` et non `Fish_Epic` (l. 86).
- Conséquence : **la rareté d'une prise est déjà un concept du monde Hytale** (état d'objet + ResourceType), que le mod doit réutiliser plutôt qu'inventer.

### 1.4 Où vivent les poissons (règles d'apparition des PNJ)

`zip:Server/NPC/Spawn/World/Zone*/Spawns_Zone*_Fish_*.json` lient espèces et **environnements** (`Environments`), avec `SpawnFluidTag: Water` et `DayTimeRange [6, 24]` :

| Fichier | Environnements | Espèces |
|---|---|---|
| Zone1 Shores | `Env_Zone1_Shores` | Clownfish, 4 Tang, Pufferfish, Lobster |
| Zone1 Tier1 | `Env_Zone1_Plains` | Minnow, Bluegill |
| Zone1 Tier2 | `Env_Zone1_Forests`, `_Mountains`, `_Autumn` | Trout_Rainbow, Catfish |
| Zone1 Tier3 | `Env_Zone1_Swamps`, `_Azure` | Piranha, Piranha_Black, Pike |
| Zone2 Shores | `Env_Zone2_Shores` | tortue, 6 méduses, Tang_Blue, Clownfish, Pufferfish |
| Zone2 Tier1 | `Env_Zone2_Savanna` | Catfish, Eel_Moray |
| Zone2 Tier3 | `Env_Zone2_Deserts`, `_Oasis` | Piranha_Black, Trilobite_Black |
| Zone3 Shores | `Env_Zone3_Shores` | Frostgill, Trilobite, Snapjaw |
| Zone3 Tier1 | `Env_Zone3_Tundra` | Bluegill, Pike |
| Zone3 Tier2 | `Env_Zone3_Forests`, `_Mountains` | Salmon |
| Zone3 Tier3 | `Env_Zone3_Glacial` | Frostgill, Trilobite |

C'est la donnée « monde » qui doit nourrir les conditions de prise (zone, environnement, eau douce ou rivage). Le piège, lui, ignore l'environnement (une seule table).

### 1.5 Mods de pêche Hytale existants (CurseForge)

| Mod | Licence | Ce qu'il fait | API |
|---|---|---|---|
| Gone Fishing (Mrbysco, ShyNieke) https://curseforge.com/hytale/mods/gone-fishing, https://github.com/Mrbysco/GoneFishing | MIT | Canne (2 bâtons + 3 chutes de lin), flotteur, attente 100 à 600 ticks (`FishHelper.minFishingTime/maxFishingTime`), table pondérée des 30 poissons vanilla dans la config (`FishingConfig.fishTable`), relâcher le poisson. « fairly bare bones », pas de mini-jeu. 148 710 téléchargements, 0.5.1 du 2026-06-07. | **Oui** : `api/event/FishingStartedEvent`, `FishCaughtEvent` (annulable, `setCaughtItem`), `FishingFailedEvent`, tous `CancellableEcsEvent` portant un `Ref<EntityStore>` du joueur. Composants ECS `BobberComponent`, `BoundBobberComponent`, systèmes `BobberSystem`, `BobberDespawnSystem`, interaction `FishingInteraction`. |
| HyFishing (TheRedlotus) https://www.curseforge.com/hytale/mods/hyfishing | All Rights Reserved | Lancer, touche, ferrer au bon moment ; prises selon eau douce ou salée ; « attraper un poisson vu à proximité » ou tirer dans une table locale ; taille et longueur aléatoires ; records personnels et mondiaux, `/hfleaders` ; Angler's Bench ; sac à poissons ; poisson laissé à terre → os ; panneau d'admin des tables. 23 213 téléchargements, mis à jour le 2026-09-20. | Aucune documentée. Idées seulement. |
| Cozy Tales – Fishing (Hexvane) https://www.curseforge.com/hytale/mods/cozy-tales-fishing, https://github.com/gchougland/CozyTalesFishing | All Rights Reserved | Ombres de poissons visibles, lancer chargé, combat ; cannes bois à adamantite, flotteurs équipables ; étangs, rivières, océans, **lave** ; déchets et trésors (coffres, bouteilles) ; détecteur de poissons ; journal, records, classements ; banc de pêche, transformateur, aquariums, bateau de pêche ; intégration Aetherhaven. 2 334 téléchargements. | Pas d'API publique repérée (paquets internes `bobber`, `fish`, `aquarium`…). Idées seulement. |
| Tiny Fishing (skyru97) https://www.curseforge.com/hytale/mods/tiny-fishing, https://github.com/skyru97/tiny-fishing | Apache-2.0 | Lancer, touche (éclaboussure et son), ferrer ; Codex à cases cachées ; poissons par biome ; « jackpots » (gemmes, minerais). 163 téléchargements. | Données JSON (`FishDefinition`, `FishingRegionDefinition`, `RodDefinition`, `WeightedFishingEntry`), `FishingContextResolver`, `FishingCastContext`. Pas d'événements publics repérés. |

Aucun de ces mods ne prévoit un pêcheur non joueur. Gone Fishing est le seul à exposer des événements, mais liés au joueur (`playerRef`).

## 2. Minecraft vanilla

Sources : https://minecraft.wiki/w/Fishing, https://minecraft.wiki/w/Fishing_Bobber, et la copie du flotteur vanilla dans MineColonies (`sources/minecolonies/src/main/java/com/minecolonies/core/entity/other/NewBobberEntity.java`, adaptée de `FishingHook`).

- **États du flotteur** (`FishingHook`) : `FLYING`, `HOOKED_IN_ENTITY`, `BOBBING` ; type d'eau `OpenWaterType` `ABOVE_WATER`, `INSIDE_WATER`, `INVALID` (https://mappings.dev/1.20.4/net/minecraft/world/entity/projectile/FishingHook$OpenWaterType.html).
- **Attente** : 100 à 600 ticks (5 à 30 s). **Appât (Lure)** : −5 s (100 ticks) au minimum et au maximum par niveau ; depuis Java 1.9 il n'agit plus sur la catégorie. **Pas de ciel** : chaque tick a 50 % de chances de ne pas décompter (attente ×2 environ). **Pluie** : chaque tick a 25 % de chances de décompter 2 (−20 % environ). Les mêmes tirages figurent dans `NewBobberEntity.catchingFish` l. 376 (`nextFloat() < 0.25F && isRainingAt`) et l. 381 (`nextFloat() < 0.5F && !canSeeSky`).
- **Approche puis touche** : le poisson (traînée de particules) arrive en 20 à 80 ticks (`NewBobberEntity` l. 487, `Mth.nextInt(random, 20, 80)`), puis la fenêtre de ferrage dure 20 à 40 ticks (l. 451) en Java, environ 0,5 s en Bedrock.
- **Eau libre** : zone 5×4×5 autour du flotteur (2 blocs de chaque côté, 2 au-dessus de la surface, 2 en dessous), faite seulement d'air, de nénuphars, d'eau source ou de blocs inondés sans collision. Sans eau libre, **plus de trésor**.
- **Tirage** : catégories poissons 85 (qualité −1), déchets 10 (−2), trésors 5 (+2) ; poids effectif `weight + quality × luck` (MineColonies reprend exactement ces poids et qualités, `DefaultFishermanLootProvider.java` l. 77-83). Trésor : 5 % → 7,07 / 9,18 / 11,34 % avec Chance de la mer I/II/III.
  - poissons : morue 60, saumon 25, poisson-globe 13, poisson tropical 2 ;
  - déchets : nénuphar 17 ; os, bol, cuir, bottes de cuir, chair putréfiée, crochet, fiole d'eau 10 ; bâton, ficelle 5 ; canne abîmée 2 ; poche d'encre ×10 1 ; bambou en jungle ;
  - trésors (1 chacun) : arc et canne enchantés (niveau 22-30, 0-25 % d'usure), livre enchanté (niveau 30), étiquette, coquille de nautile, selle.
- **XP** 1 à 6 par prise. **Usure** : 1 par prise, 2 accroché au sol, 3 pour un objet ramené, 5 pour une entité. Distance max 33 blocs ; le flotteur au sol disparaît après 1200 ticks.
- **Événements des plateformes** : NeoForge `ItemFishedEvent` (annulable, `getDrops()` en lecture, `damageRodBy(int)`, `getHookEntity()` ; la Javadoc renvoie aux tables de butin pour modifier les prises : https://github.com/neoforged/NeoForge/blob/1.21.x/src/main/java/net/neoforged/neoforge/event/entity/player/ItemFishedEvent.java). Paper `PlayerFishEvent.State` : `FISHING`, `LURED`, `BITE`, `CAUGHT_FISH`, `CAUGHT_ENTITY`, `IN_GROUND`, `FAILED_ATTEMPT`, `REEL_IN` (https://jd.papermc.io/paper/1.21.4/org/bukkit/event/player/PlayerFishEvent.State.html), plus réglage du temps d'attente et `resetFishingState()` (https://ayakael.net/mirrors/papermc/commit/2786ee1e8f166f30512a78621b9efaf996525c7c).

## 3. Le Pêcheur de MineColonies (ce que l'API devra servir)

- Le citoyen lance son propre flotteur `NewBobberEntity` (copie de `FishingHook`), avec `setAngler(citizen, luck, lureSpeed)` (l. 100). Chance = `EnchantmentHelper.getFishingLuckBonus(rod)` ; vitesse = `5 + primarySkill / LURE_SPEED_DIVIDER (25) + getFishingSpeedBonus(rod)` (`EntityAIWorkFisherman.java` l. 561-563).
- Écart notable avec le vanilla : l'attente du citoyen vaut `nextInt(1060, 1300) − lureSpeed × 100` ticks (`NewBobberEntity` l. 492-493), bien plus longue que 100 à 600.
- Le butin passe par la table `minecolonies:fisherman` qui **délègue aux tables vanilla** `fishing/fish`, `junk`, `treasure` (poids 85/10/5, qualités −1/−2/+2), le trésor étant soumis à la recherche `FISH_TREASURE` ; des tables bonus par niveau de hutte (`fisherman/bonus1..5`, éponge, éclats et cristaux de prismarine dès le niveau 3) s'y ajoutent (`DefaultFishermanLootProvider.java` l. 75-134 ; PR https://github.com/ldtteam/minecolonies/pull/7744). Le PR explique que déléguer aux tables vanilla garde les **poissons des autres mods** ; le flotteur maison empêche en revanche la condition vanilla d'eau libre, remplacée par une détection par biome.
- Recherche d'étang : `Pond.checkPond`, largeur 5 (`WATER_POOL_WIDTH_REQUIREMENT`) et profondeur 2 (`WATER_DEPTH_REQUIREMENT`) (`sources/minecolonies/src/main/java/com/minecolonies/api/util/Pond.java` l. 33-34, 44, 87).
- Compatibilité avec les mods de pêche : aucune intégration de Tide ou d'Aquaculture dans MineColonies (recherche des tickets `ldtteam/minecolonies` sur « tide » et « aquaculture » : rien de pertinent). Ancien ticket : les pêcheurs acceptaient les cannes d'Aquaculture sans s'en servir (https://github.com/ldtteam/minecolonies/issues/3707, 2019). La compatibilité passe donc **uniquement par les tables de butin**. Exemple d'un pêcheur PNJ branché sur Tide : TideMaid, qui donne aux servantes de Touhou Little Maid une tâche « Tide Fishing » (https://modrinth.com/mod/tidemaid).

## 4. Mods de pêche Minecraft

### 4.1 Tide 2 (Lightning-64) : la référence moderne

https://modrinth.com/mod/tide, https://github.com/Lightning-64/Tide-2 (MPL-2.0, Fabric/Forge/NeoForge, 1.20.1 à 1.21.5, 1,5 M téléchargements), wiki https://lightning-64.github.io/tide-wiki/.

Jeu :
- plus de 100 poissons ; mini-jeu de **timing** : une barre et un curseur, cliquer quand il est dans la zone colorée. Notes de Perfect (10 % central, 100 %) à Trash (0 %) (https://lightning-64.github.io/tide-wiki/mechanics/fishing-minigame/) ;
- **Établi de pêche** (Angling Table) : crochet, ligne et flotteur interchangeables, appâts cumulables, accessoires pour pêcher dans la **lave** et le **vide** ;
- caisses de butin (selon le milieu et la profondeur : `crates/surface_freshwater`, `underground`, `deep_lava`, `nether`, `end`…), « fishy notes » qui indiquent où trouver un poisson ;
- **journal de pêche** : progression, plus grosses prises, habitats ; la fiche affiche profondeur, lieu, chance, phase de lune, saisons, température, heure, météo, silhouette (`client/gui/screens/journal/components/*Component.java`) ;
- configuration (https://lightning-64.github.io/tide-wiki/config/mod-config/) : **mini-jeu désactivable** (« Fish will be caught like they are in the base game »), multiplicateur de difficulté, poids et qualité des caisses, usure des cannes.

Architecture, lue dans le code (`src/main/java/com/li64/tide/data/fishing/`) :
- `FishingContext` (record) : `level`, `@Nullable hook`, `@Nullable rod`, `rng`, `pos`, `blockPos`, `luck`, `medium`, `exactBiome`, `nearestBiome`, `dimension`, `temperature`, `moonPhase`, `season`. Flotteur et canne **peuvent être nuls** : un tirage peut se faire sans flotteur ;
- `TideFishingManager.selectCatch(context)` : tirage pondéré entre les entrées de butin des données, `FishSelector` (poids `85 + (−1) × luck`) et `CrateSelector` ; `test(context, type)` rend la carte des poids effectifs (commande `/fishing test`) ;
- `FishingEntry` : `weight(context)`, `shouldKeep(context)`, `getResult(context)` ; poids = `base + quality × luck`, puis chaque `FishingModifier.apply(weight, ctx)` ; poids ≤ 0 → exclu ;
- `CatchResult(List<ItemStack> items, Optional<FishingEntry> entry)` ;
- **conditions** (`FishingCondition.test(ctx)`), dans un **registre** (`TideRegistries.FISHING_CONDITIONS`) avec codecs dispatchés par `type` : `either`, `not`, `freshwater`, `saltwater`, `dimension`, `found_in` (biomes), `time_of_day`, `fluid`, `above`, `below`, `depth_range`, `found_in_structures`, `luck`, `moon_phase`, `weather`, `open_water`, `block_nearby`, `seasons`, `has_enchantments` (`FishingConditionType.java`) ;
- **modificateurs** de poids : `conditional` (`if` + `multiplier`), `temperature` (`preferred_temperature`, `temperature_tolerance`) ;
- **milieux** `FishingMedium` (eau, lave, vide) : `canFishIn`, `isAt`, effets visuels, `biteTimeMultiplier()` ;
- **taille** (`FishSizeModel`) : loi log-normale calée sur `typical_low_cm`/`typical_high_cm` (quantiles 10 % et 90 %), bornée par `record_high_cm` et `0,6 × typical_low` par défaut, avec une chance de « trophée » tirée au-dessus de `typical_high` ;
- **mini-jeu** : `strength` (part ratée de la barre, 0,3 par défaut), `speed`, `behavior` (`sine`, `plateau`, `jitter`, `darts`, `linear`, `linear_wrap`, fonctions de `t` dans `MinigameBehavior.java`) ; les lignes réduisent force ou vitesse (`FishCatchMinigame.java`) ;
- appât en données : `{item, speed_bonus, luck_bonus}` dans `data/tide/bait/` ; effets spéciaux (aimant → caisses) codés en dur (https://lightning-64.github.io/tide-wiki/config/datapacks/bait-items/).

Exemple réel, `src/generated-1.21.1/resources/data/tide/fishing/fish/freshwater/rainbow_trout.json` : `conditions` = saisons (printemps, été, hiver), `freshwater`, `dimension` overworld, `fluid` water, `above y 40` ; `modifiers` = température préférée −0,5, tolérance 0,65 ; `selection_weight 36` ; taille 50-75 cm, record 110 ; `speed 0.75`.

Ouverture aux autres mods : données (poissons, appâts, accessoires, caisses, butin) et registres de conditions et modificateurs. **Pas d'API Java publique ni d'événements à soi** (pas de paquet `api`, les gestionnaires `events/` sont internes). Compatibilités intégrées : Fish of Thieves, Fishing Real, Hybrid Aquatic, Nether Depths, Stardew Fishing, Starcatcher, saisons (`compat/`). Un pack de données externe ajoute les poissons de plus de 25 mods (https://www.curseforge.com/minecraft/data-packs/tide-extra-compatibility).

### 4.2 Aquaculture 2 (Team Metallurgy)

https://modrinth.com/mod/aquaculture, https://github.com/TeamMetallurgy/Aquaculture (All Rights Reserved, NeoForge, 1,1 M téléchargements).

- Jeu : plus de 30 poissons pêchés ou nageant ; cannes modulaires (crochet, appât, ligne, flotteur) ; boîte à pêche (tackle box) ; vers tirés de la terre ; **poids** des poissons ; couteau à filets (nombre de filets selon le poids) ; poissons naturalisés ; boîtes à verrou, coffres au trésor, bouteilles à message ; outils et armure en Neptunium. Avis rapporté : bois flotté et roseaux appréciés, mais « traverser les cours d'eau devient pénible » et l'armure « détonne » (https://sportskeeda.com/minecraft/5-best-minecraft-fishing-mods-2022).
- API (`api/`) :
  - `AquacultureAPI.FISH_DATA.add(item, minWeight, maxWeight, filletAmount)` ; `createBait(durability, lureSpeedModifier, props)` ; `registerHook(Hook)` ; tags `fishing_line`, `bobber`, `tackle_box`… ;
  - `Hook` (builder) : `minCatchable`/`maxCatchable` (fenêtre de ferrage), `weight` (vecteur de lest), `durabilityChance`, `luckModifier`, `doubleCatchChance`, son de prise, **fluides** permis. Exemples dans `Hooks.java` : fer (20 % d'usure épargnée), or (+1 chance), double (10 % de prise double), redstone (fenêtre 35-70), étoile du Nether (eau + lave) ;
  - `IBaitItem.getLureSpeedModifier()` ;
  - poids posé à la prise par un abonnement à `ItemFishedEvent` (`loot/FishWeightHandler.java`) : uniforme entre min et max, classe de taille `juvenile` ≤ 10 % du max, `small` ≤ 20 %, `large` ≥ 80 %, `massive` ≥ 90 % ; biomes par condition de butin `BiomeTagCheck` dans des tables de pêche.
- Aquaculture garde le butin dans les tables de Minecraft, d'où sa compatibilité implicite avec tout ce qui lit ces tables, dont le Pêcheur de MineColonies.
- Tide et Aquaculture se recouvrent ; un pont, « Fishing Rod Compat », remplace les cannes d'Aquaculture par celles de Tide (https://www.curseforge.com/minecraft/mc-mods/fishing-rod-compat).

### 4.3 Autres

- **Fish of Thieves** (MIT, https://modrinth.com/mod/fish-of-thieves) : les 10 espèces de Sea of Thieves, 5 variantes chacune, choisies par biome, jour ou nuit, vue du ciel, pluie, phase de lune, blocs voisins (ruche à miel à 12 blocs, 12 blocs d'améthyste à 2 blocs, 16 blocs de corail), structures (avant-postes, monuments, épaves), profondeur (< Y 0), ou au hasard (10 %). 15 % de chances d'une taille **trophée** (configurable). Appâts par espèce (vers de terre, larves, sangsues), bancs de poissons pêchables, plaques murales. Mode « Simple Spawning Condition » (https://raw.githubusercontent.com/wiki/SteveKunG/FishOfThieves/Spawning.md).
- **Fishing Real** (https://www.curseforge.com/minecraft/mc-mods/fishing-real) : la prise d'un poisson fait apparaître **l'entité vivante** au lieu de l'objet, à capturer au seau ; conversions objet → entité en pack de données. Hytale fait déjà l'inverse : ses objets poissons font apparaître le PNJ.
- **Stardew Fishing** (MIT, https://modrinth.com/mod/stardew-fishing) : le mini-jeu de Stardew (barre à garder sur le poisson) ; vitesse max, accélération, distance de déplacement par poisson en pack de données ; qualité de la prise selon la précision (avec Quality Food).
- **Starcatcher** (https://github.com/wdiscute/starcatcher, licence maison, assets ARR) : plus de 100 poissons restreints par biome, météo, heure, altitude ; mini-jeu circulaire à zones et gimmicks par poisson ; trophées, tournois, secrets ; reprend automatiquement les poissons de Tide mais impose ses cannes (https://www.modpackindex.com/mod/87587/starcatcher).

## 5. Autres jeux

- **Stardew Valley** (https://stardewvalleywiki.com/Fishing) : touche en 0,6 à 30 s (le niveau, les appâts et le spinner raccourcissent) ; mini-jeu : barre verte de 96 px (+8 par niveau, sur 568), à garder sur le poisson jusqu'à remplir la jauge ; difficulté du poisson 5 à 110 et comportement (mixed, dart, smooth, sinker, floater) ; prise **parfaite** (jamais sorti de la barre) : qualité +1, XP ×2,4 ; `XP = (qualité + 1) × 3 + difficulté / 3` ; coffre au trésor 15 % (+15 % aimant…) ; qualité selon la distance de lancer et le niveau ; poissons par saison, météo, plan d'eau, heure ; **casiers à crabes** passifs (5 XP par relève, professions sans déchets ou sans appât) ; étangs à poissons.
- **Terraria** (https://terraria.wiki.gg/wiki/Fishing) : **puissance de pêche** = canne + appât + équipement + potions, ×1,3 à l'aube et au crépuscule, ×1,2 sous la pluie, ×0,9 à ×1,1 selon la lune ; plan d'eau de 75 tuiles au minimum, 300 pour l'optimum (1001 en océan) ; liquides eau, lave, miel ; cinq paliers de rareté testés indépendamment (⌊150/FP⌋ … ⌊4500/FP⌋) ; caisse 10 % (25 % avec potion) ; table selon le biome (avec priorités) et la couche de hauteur (espace, surface, souterrain, caverne, enfers) ; déchets si FP < 49 et plan d'eau < 300 ; **quêtes du Pêcheur** quotidiennes (poisson de quête par biome et couche).
- **Animal Crossing NH** (https://nookipedia.com/wiki/Fishing) : ombres dont la taille annonce l'espèce ; lieux (mer, rivière, étang, cascade, embouchure, ponton) ; heures et mois ; au quatrième « grignotage » la touche est sûre ; appât qui fait apparaître une ombre ; Critterpedia et musée ; tournoi de C.J.
- **Dredge** (https://dredge.wiki.gg/wiki/Minigames, https://dredge.wiki.gg/wiki/Equipment) : six mini-jeux (radial, attrape-balles, losange, pendule, spirale, dragage) liés au type de poisson ; l'équipement **ouvre des types d'eau** (côtier, peu profond, océanique, abyssal/hadal, mangrove, volcanique, glace), avec un bonus de vitesse en % ; **filets de chalut** passifs pendant la navigation (1 à 3 jours, 8 à 18 poissons par jour) et **casiers** (3 à 9 jours, 2 à 3 par jour) ; poissons aberrants. Selon les développeurs, le mini-jeu est **facultatif** : sans appuyer, la prise vient juste plus lentement (https://gamasutra.com/design/trawling-in-the-deep-how-black-salt-games-made-spooky-fishing-rpg-i-dredge-i-).
- **Sea of Thieves** (https://seaofthieves.wiki.gg/wiki/Fishing) : tirer à l'opposé de la nage du poisson, sinon la ligne casse ; 10 espèces × 5 variantes, variantes rares selon le lieu, l'heure ou la tempête ; environ un tiers de trophées ; un appât par famille ; cuisson (cru, pas assez cuit, cuit, brûlé) qui fixe la valeur ; revente à la Hunter's Call.
- **World of Warcraft** (https://warcraft.wiki.gg/wiki/Fishing) : lancer canalisé de 21 s, cliquer le flotteur à l'éclaboussure ; compétence par région ; tables par zone, eau douce ou salée ; **bancs** (pools) d'environ 5 poissons d'un type ; cannes, lignes, leurres, tenues et buffs en bonus de compétence ; tournois (Stranglethorn Fishing Extravaganza).
- **Final Fantasy XIV** (https://ffxiv.consolegameswiki.com/wiki/Fishing) : appât choisi ; trois forces de touche (léger, moyen, lourd) à ferrer avec le bon ferrage (précision ou puissance) ; **mooching** (un poisson pris sert d'appât au suivant) ; chum (touche plus rapide) ; patience (gros poissons) ; fenêtres de météo, d'heure et de **météo précédente** ; poissons légendaires ; journal ; pêche océanique collective ; harponnage.
- **Zelda Ocarina of Time** (https://zeldadungeon.net/wiki/Fishing_Pond) : étang payant, record de poids à battre, leurre plombant interdit (prise non homologuée), Loche hylienne légendaire présente une visite sur quatre.
- **Valheim** (https://xgamingserver.com/blog/valheim-fishing-guide/) : un appât par biome et poisson ; ramener coûte de l'endurance (la prise s'échappe à 0) ; compétence 0 à 100 (+2 % de vitesse, −0,8 % d'endurance par niveau).
- **Webfishing** (https://webfishing.wiki.gg/wiki/Fishing) : qualité en étoiles (Normal à Alpha) tirée selon l'appât ; taille en loi normale ; leurres orientant la prise (petits, gros, rares) ; mini-jeu de difficulté 1 à 250 ; sac de pièces 15 %, double prise 15 %, trésor 2 à 4 %.
- **Cast n Chill** (https://www.shacknews.com/article/144913/cast-n-chill-review-score) : **mode idle** où le personnage rame, lance, pêche et vend tout seul ; permis par zone, 13 lieux, 50 poissons. Preuve que la même simulation sert le joueur actif et un pêcheur automatique.

## 6. Patrons d'API (synthèse)

1. **Définitions de poissons en données**, une par fichier, avec : objet, poids et qualité (sensibilité à la chance), conditions, modificateurs, taille (bornes typiques et record), difficulté et comportement du mini-jeu, fiche de journal, mods requis (`associated_mods`). Modèle : Tide `FishData`.
2. **Conditions et modificateurs en registre extensible** : un type par clé, un codec par type, composables (`either`, `not`, `conditional`). Un autre mod ajoute un type de condition sans toucher au cœur.
3. **Contexte de pêche découplé du flotteur** : record immuable (position, milieu, environnement, heure, météo, phase de lune, chance, vitesse, outil, appât), flotteur facultatif. C'est la clé d'un tirage serveur sans joueur.
4. **Tirage pur et testable** : `selectCatch(context) → CatchResult` ; `test(context)` qui rend les poids effectifs (diagnostic, journal « où pêcher », vue JEI du Pêcheur MC).
5. **Équipement par propriétés**, pas par classes : crochet, ligne, flotteur, appât portent des valeurs (vitesse, chance, fenêtre de ferrage, prise double, fluides permis, réduction de difficulté). Modèles : Aquaculture `Hook`, Tide `BaitData`.
6. **Milieux** (eau, lave…) comme extension : `canFishIn`, multiplicateur de touche, effets. Tide `FishingMedium`.
7. **Événements en cycle de vie** : lancer, approche, touche, ferrage (ou échec), prise (annulable, prise modifiable), ramené sans prise. Modèles : Paper `PlayerFishEvent.State`, NeoForge `ItemFishedEvent`, Gone Fishing (`FishingStartedEvent`, `FishCaughtEvent`, `FishingFailedEvent`). À l'inverse de ces trois, l'acteur doit être un **pêcheur abstrait** (joueur ou PNJ), pas forcément un joueur.
8. **Le butin passe par les tables du monde** et le mod les enrichit ; ne pas les court-circuiter. C'est ce qui garde la compatibilité du Pêcheur MC avec Aquaculture (§ 3).
9. **Mini-jeu séparé de la prise** : le tirage choisit le poisson et sa difficulté, le mini-jeu ne fait que valider (réussite, parfait) ; il est désactivable (Tide, Dredge) et absent pour un PNJ.

## 7. Patrons retenus pour Hytale

Classés par palier. Chaque idée cite ses sources ci-dessus.

### V1

1. **Simulation de lancer côté serveur, sans joueur ni flotteur** : `FishingContext` immuable + `selectCatch` pur, appelés par la canne du joueur comme par un PNJ (Tide § 4.1, Cast n Chill § 5, besoin du Pêcheur MC § 3). Pour le Pêcheur, la boucle d'attente et de touche reste celle de MC (`NewBobberEntity`) ; seul le tirage passe par l'API.
2. **Règles de prise tirées du monde Hytale** : zone et environnement (`Spawns_Zone*_Fish_*`, § 1.4), eau douce ou rivage, heure (`DayTimeRange`), fluide ; pas de biomes inventés.
3. **Rareté = états de rareté de Hytale** (`Fish_*_Item` × `Uncommon`…`Legendary`, ResourceTypes `Fish_<rareté>`, § 1.3), tirée selon la chance ; c'est elle qui fixe le rendement en `Food_Fish_Raw` au plan de cuisine.
4. **Poids `base + qualité × chance` et catégories poissons, déchets, trésors** (85/10/5, −1/−2/+2, Minecraft § 2, repris par MC § 3), avec déchets et trésors pris dans les objets de Hytale (tables du piège § 1.2).
5. **Définitions de poissons en données**, ouvertes aux autres mods (comme `Server/HyColony/Foods/`) : conditions composables en registre, modificateurs, taille. Modèle Tide `FishData`.
6. **Événements de cycle de vie à pêcheur abstrait** : lancer, touche, prise (annulable, modifiable), échec (§ 6 point 7).
7. **Canne et timing vanilla** : attente 100-600 ticks, approche 20-80, fenêtre 20-40 ; pluie plus rapide, pas de ciel plus lent ; eau libre requise pour les trésors (Minecraft § 2). Ce sont des règles de système (pas une mesure du monde Minecraft) à garder telles quelles ; l'eau libre se vérifie en blocs et fluides de Hytale.

### V2

8. **Taille et poids** par loi log-normale avec record et trophée (Tide `FishSizeModel`, Fish of Thieves 15 %, Aquaculture classes de taille), et **records** personnels et de serveur (HyFishing, Cozy Tales).
9. **Journal ou Codex** : espèces vues, plus grosses prises, habitats et conditions connus, alimenté par la fonction de test du tirage (Tide journal, Tiny Fishing Codex, Animal Crossing).
10. **Équipement modulaire par propriétés** : crochet, ligne, flotteur, appât (vitesse, chance, fenêtre, double prise, fluide permis) à un établi (Aquaculture `Hook`, Tide Angling Table) ; recettes aux bancs de Hytale.
11. **Mini-jeu facultatif et désactivable**, réglé par la difficulté et le comportement du poisson (Tide, Stardew, Dredge) ; un PNJ ne le joue jamais.
12. **Pêche passive** : s'appuyer sur le piège de Hytale (`Tool_Fishing_Trap`, bloc de culture) plutôt que de recréer casiers et filets ; l'API peut exposer une table de piège (Stardew casiers, Dredge filets).

### Plus tard

13. **Autres milieux** (lave, avec `Fish_Shellfish_Lava_Item` ; vide) comme extension de milieu (Tide `FishingMedium`, Cozy Tales lave).
14. **Variantes conditionnelles riches** : phase de lune, blocs voisins, structures, météo précédente (Fish of Thieves, FFXIV) ; et **mooching** (un poisson comme appât).
15. **Quêtes et tournois** : poisson du jour d'un PNJ, concours chronométrés (Terraria Angler, WoW, Animal Crossing, Starcatcher), aquariums (Cozy Tales).

À éviter, d'après les avis et les choix des développeurs : un mini-jeu obligatoire et punitif (Dredge l'a rendu facultatif, Tide le rend désactivable) ; dupliquer cannes et objets d'un autre mod (Tide et Aquaculture se recouvrent et il faut un pont) ; ajouter une armure ou un palier d'outils qui détonne (avis sur Aquaculture).

## 8. Incertitudes

- Unité de `Farming.Stages[].Duration` du piège (millisecondes ou ticks) : non vérifiée dans le code **[in-game]**.
- La déclaration des développeurs de Hytale sur la pêche est de seconde main (gamewave.fr, thespike.gg) ; source primaire non trouvée.
- Avis de joueurs sur Reddit non consultables (domaine refusé) ; les avis cités viennent de la presse et des pages de mods.
- Le wiki de Tide est en partie périmé pour Tide 2.0 ; les faits d'architecture viennent du code (branche par défaut, 2026-10-04).
