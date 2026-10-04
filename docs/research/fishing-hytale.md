# La pêche dans Hytale 0.7.0-pre.5 : inventaire pour un mod de pêche autonome

Recherche du 2026-10-04, avant toute conception d'un 6e mod (pêche jouable sans HyColony, avec une API que le pêcheur de MineColonies utilisera ensuite). Rien n'a été lancé en jeu. Ce qui n'est vérifié que dans les sources, et dont le résultat chez le client est inconnu, est marqué **[in-game]**.

Abréviations :

- `HY/` = `build/vineflower/hytale-server/com/hypixel/hytale/` (décompilé de 0.7.0-pre.5, épinglé dans `gradle.properties` : `hytale_version = 0.7.0-pre.5`) ;
- `zip:` = `%USERPROFILE%\.gradle\caches\hytale-assets\pre-release-0.7.0-pre.5-Assets.zip` ;
- `jar:` = `Server/HytaleServer.jar` contenu dans `%USERPROFILE%\.gradle\caches\hytale-assets\pre-release-0.7.0-pre.5.jar`.

Déjà documenté ailleurs (on ne le répète pas) : la nourriture de poisson, le feu de camp et les recettes du plan de cuisine (`sp4b-hytale-food.md` § 1, lignes 17, 82-99, 170-171), les fluides lus dans une section (`plugin-b-api.md` § 1, § 33-34), la nage des PNJ (`plugin-b-api.md` § 52).

## Résumé : ce que Hytale a, ce qui manque, les contraintes

**Ce que Hytale a déjà**

- **Aucune pêche à la canne.** Le serveur ne contient pas le mot `fish` (recherche insensible à la casse dans tout `HY/` : zéro fichier). Le modèle d'une canne existe (`zip:Common/Items/Tools/Fishing_Rod/FishingRod.blockymodel` et sa texture), mais **aucun asset serveur ne l'utilise** : pas d'objet, pas d'animation, pas de son.
- **La nasse** (`Tool_Fishing_Trap`) : un bloc posé sur l'eau, qui « pousse » comme une culture (`FarmingBlock`) et se récolte avec une liste de tirage. L'appât (`Tool_Trap_Bait`) change son jeu d'étapes. C'est la seule pêche vanilla (§ 1.3).
- **30 poissons vivants** `Fish_*_Item` avec qualité, états de rareté et ResourceTypes `Fish`, `Fish_Common`…`Fish_Legendary` ; **30 rôles de PNJ aquatiques** avec règles d'apparition par environnement, zone et heure (§ 1.1, § 1.4).
- **Une corde entre deux entités** : le plugin intégré `Hytale:Beam` (`BeamComponent`, `AttachedBeam`, interaction `AttachBeam`, asset `Entity/Beams/Rope.json`), utilisé par le grappin `Tool_Hookshot`, qui tend une corde de son crochet à la main du joueur (§ 3.4).
- **Des projectiles avec flottabilité** : la physique `Standard` calcule la poussée d'Archimède (densité 700 par défaut, eau 1000) ; un projectile peut donc flotter **[in-game]** (§ 3.2).
- Tout le reste d'un mod de pêche : interaction chargée (`Charging`, paliers de durée), interactions Java propres, objet qui vole vers le joueur, durabilité, métadonnées d'une pile, sons, particules, HUD, pages, ECS, météo, heure, lune, biome et environnement (§ 3).

**Ce qui manque**

- Canne, flotteur, ligne de pêche, touche, ferrage, mini-jeu, table de prises « à la canne » : tout est à faire.
- Animations de lancer et de moulinet : aucune (ni joueur ni PNJ). Le grappin a une animation `Shoot` (`zip:Server/Item/Animations/Hookshot.json`) ; le reste est à créer ou à emprunter (§ 1.6).
- Aucun enchantement (rappel : `plugin-b-api.md` l. 130) ; les modificateurs de stats existent (`StatModifiers` sur armes, armures, objets utilitaires), mais rien de propre à la pêche (§ 3.9).
- Pas d'interaction ni d'événement « le projectile entre dans l'eau » : il faut un système qui lit `StandardPhysicsProvider.isInFluid()` (§ 3.2).
- Pas de mesure de « plan d'eau » : un parcours borné des `FluidSection` est à écrire (§ 3.3).

**Contraintes**

- La corde (`Beam`) est un segment droit texturé entre deux points ; un affaissement (chaînette) n'apparaît nulle part dans le paquet (`BeamInstance` n'a ni courbure ni tension) **[in-game]** pour l'aspect réel.
- Les formes de débogage (`DebugUtils.addLine`) dessinent une ligne, mais pour tout le monde dans le monde, et ce sont des formes de débogage (`plugin-b-api.md` § 38).
- Un projectile disparaît après 5 minutes par défaut (`HY/server/core/modules/projectile/ProjectileModule.java:76`), sauf délai passé à `spawnProjectile`.
- La rareté `Epic` d'un poisson porte le ResourceType `Fish_Rare`, pas `Fish_Epic` (anomalie des assets, § 1.1).

## 1. Le contenu de pêche dans les assets

### 1.1 Poissons vivants (`Fish_*_Item`)

- Gabarit `zip:Server/Item/Items/Fish/Template_Fish_Item.json` : catégorie `Fish`, tags `Type: SpawnNPC`, `Family: Fish`, ResourceType `Fish`, `ItemSoundSetId: ISS_Items_Splatty`. L'interaction `SpawnNPC_Entity` (parent `SpawnNPC_Entity_Default`) relâche le PNJ `EntityId` une case au-dessus, avec le son `SFX_Water_MoveOut`. Le modèle de l'objet est celui du PNJ (`NPC/Swimming_Wildlife/.../Model.blockymodel`), avec son animation de nage (`Animation`, ex. `Fish_Bluegill_Item.json`).
- 30 fichiers `zip:Server/Item/Items/Fish/Fish_*_Item.json` (qualité de base, nombre d'états de rareté) :
  - `Common` avec états `Uncommon`/`Rare`/`Epic`/`Legendary` : Bluegill, Catfish, Minnow ;
  - `Uncommon` avec états : Tang_Blue, Tang_Chevron, Tang_Lemon_Peel, Tang_Sailfin ;
  - `Rare` avec états : Clownfish, Pufferfish, Salmon, Trout_Rainbow ;
  - `Epic` avec état `Legendary` : Jellyfish_Blue/Cyan/Green/Red/Yellow ;
  - `Legendary` sans état (« monstres ») : Crab, Eel_Moray, Frostgill, Jellyfish_Man_Of_War, Lobster, Pike, Piranha, Piranha_Black, Shark_Hammerhead, Shellfish_Lava, Snapjaw, Trilobite, Trilobite_Black, Whale_Humpback.
- États de rareté (`Fish_Bluegill_Item.json`, bloc `State`) : chaque état est une `Variant` (cachée de la bibliothèque), avec sa `Quality`, la particule d'objet au sol `Drop_<Rareté>` et les ResourceTypes `Fish` + `Fish_<Rareté>`. L'id d'un état est `*<Objet>_State_<Rareté>` (ex. `*Fish_Bluegill_Item_State_Rare`, utilisé dans `zip:Server/Drops/Traps/Drops_Fishing_Trap_Crude_Baited_Wild.json`). **Anomalie** : l'état `Epic` porte `Fish_Rare`, pas `Fish_Epic` (même fichier). Rappel : `Item.isState()` est vrai pour ces états (`plugin-b-api.md` l. 687).
- ResourceTypes : `zip:Server/Item/ResourceTypes/Fish.json`, `Fish_Common.json`, `Fish_Uncommon.json`, `Fish_Rare.json`, `Fish_Epic.json`, `Fish_Legendary.json` (une icône chacun). Noms en-US : « Any Fish », « Any Common Fish »… (`zip:Server/Languages/en-US/server.lang`, clés `resourceType.Fish*.name`).
- Usage : le plan de cuisine les découpe en `Food_Fish_Raw` ×1 à ×16 selon la rareté (`sp4b-hytale-food.md` l. 91, 171), et le trophée `Deco_Trophy_Harvest` en demande 100 (`res:Fish`, `zip:Server/Item/Items/Deco/Deco_Trophy_Harvest.json:89`).

### 1.2 Autres objets liés

- `Food_Fish_Raw`, `Food_Fish_Grilled`, `Food_Fish_Raw_Uncommon/Rare/Epic/Legendary` : voir `sp4b-hytale-food.md` (§ 1, l. 82-99).
- `Ingredient_Fishbone` (« Fishbone », `zip:Server/Item/Items/Ingredient/Ingredient_Fishbone.json`) : ingrédient du plan de cuisine. Sa `Recipe` est une recette de sel (`Rock_Salt` → `Ingredient_Salt` ×5) : rien ne fabrique d'arête (autre anomalie).
- `Weapon_Spear_Fishbone` (« Fishbone Harpoon ») : lance de jet, projectile `zip:Server/Projectiles/Player/Spear/Spear_Fishbone.json` ; tirage légendaire de la nasse.
- Décor : `Dead_Fish`, `Deco_Trash_Eaten_Fish`, `Deco_Starfish` (`zip:Server/Item/Items/MISC/Dead_Fish.json`, `Deco/…`).
- Musique inutilisée : `zip:Common/Music/zUnused/FishermansShores.ogg`.

### 1.3 La nasse et l'appât (la pêche vanilla)

- `zip:Server/Item/Items/Tool/Tool_Fishing_Trap.json` (« Fishing Trap », « Set up above water to catch fish over time. ») :
  - recette au banc de ferme (`Farmingbench`, palier 2) : 10 `Wood_All`, 50 `Ingredient_Life_Essence`, 20 `Ingredient_Fibre`, 1 `Ingredient_Bar_Iron` ;
  - support : un fluide `Water_Source` ou `Water` dessous (`Support.Down`, l. 50, 53) ;
  - `Farming` (l. 148-189) : jeu d'étapes `Default` = `default` pendant 40 000 à 60 000 s de jeu (l. 153) puis `StageFinal` ; jeu `Baited` = `Baited` pendant 20 000 à 40 000 s (l. 166-169) puis `Baited_StageFinal` ; modificateurs actifs `Water` et `LightLevel` (l. 185) ; après récolte, retour à `Default` ;
  - récolte (`Use` → `HarvestCrop`) : liste `Drops_Fishing_Trap_Crude` (l. 100) ou, appâtée, `Drops_Fishing_Trap_Crude_Baited_Wild` (l. 142) ;
  - sons : `SFX_Water_MoveOut` au début d'une étape, `SFX_Water_MoveIn` à la fin.
- `zip:Server/Item/Items/Tool/Tool_Trap_Bait.json` (« Fish Bait (Wild) ») : 3 `res:Fruits` + 5 essences de vie → 2 appâts (banc de ferme palier 2). `Secondary` sur une nasse à l'état `default` : `ChangeFarmingStage` vers le jeu `Baited` (l. 70-72), puis `ModifyInventory` −1.
- `zip:Server/Drops/Traps/Drops_Fishing_Trap_Crude.json` (tirage `Choice` pondéré) : poissons communs (Bluegill, Catfish, Minnow) 100 ; sel 99 ; déchets communs 98 (gravats, bâtons, lentilles d'eau, `Deco_Trash`, crottin, coquillages) ; déchets peu communs 49 ; rares 9 (dont `Deco_Treasure`) ; épiques 4 ; légendaires 0,9 (harpon, `Ore_Gold`, `Deco_Treasure_Pile_Large`) ; « monstres » 0,01 (les 14 poissons légendaires).
- `Drops_Fishing_Trap_Crude_Baited_Wild.json` : uniquement des poissons, communs ×1-4 (100), peu communs ×1-3 (50), rares ×1-2 (10), épiques (5), légendaires (1), monstres (0,1), avec les états de rareté `*…_State_…`.
- Pour un mod de pêche : la nasse montre comment Hytale classe les prises (poissons, ressources, déchets, trésors) et donne des **pondérations de référence**. Ses durées sont celles d'une culture (secondes de jeu).

### 1.4 PNJ aquatiques et règles d'apparition

- Rôles : `zip:Server/NPC/Roles/Aquatic/Freshwater/` (Bluegill, Catfish, Frostgill, Minnow, Pike, Piranha, Piranha_Black, Salmon, Snapjaw, Trout_Rainbow), `Marine/` (Clownfish, Crab, Jellyfish ×6, Lobster, Pufferfish, Tang ×4), `Abyssal/` (Eel_Moray, Shark_Hammerhead, Shellfish_Lava, Trilobite, Trilobite_Black, Whale_Humpback). Ex. `Freshwater/Bluegill.json` : `Variant` de `Template_Swimming_Passive`, `MaxHealth` 29, `DropList: Drop_Bluegill`, `IsMemory`, `MemoriesCategory: Freshwater`.
- Comportement passif (`zip:Server/NPC/Roles/_Core/Templates/Template_Swimming_Passive.json`) : `BreathesInWater` (l. 68), contrôleur `Dive` (`MaxSwimSpeed` 10, `SwimDepth` 0,6, `MinDepthAboveGround` 1, `MinDepthBelowSurface` 1, l. 72-88), banc de poissons (capteur `Beacon` `Flock_Pos` → `SetLeashPosition`, l. 94-107), errance, fuite devant un joueur à 5 blocs en ligne de vue ou sur dégâts (l. 215-245, état `Flee`). Il existe aussi `Template_Swimming_Aggressive.json`.
- Butin à la mort : `Food_Fish_Raw` ×1 pour presque tous (×1-2 brochet et requin, ×2-4 snapjaw, ×2-3 méduses), jamais l'objet vivant (`zip:Server/Drops/NPCs/Swimming_*/Drop_*.json`).
- Apparitions (`zip:Server/NPC/Spawn/World/…`, `SpawnFluidTag: Water`, tranche `DayTimeRange [6, 24]` pour toutes les rivières et côtes ; aucune tranche pour les océans) :

| Fichier | Environnements | Poissons |
|---|---|---|
| `Zone1/Spawns_Zone1_Fish_Tier1` | `Env_Zone1_Plains` | Minnow, Bluegill |
| `Zone1/…_Fish_Tier2` | `Env_Zone1_Forests`, `_Mountains`, `_Autumn` | Trout_Rainbow, Catfish |
| `Zone1/…_Fish_Tier3` | `Env_Zone1_Swamps`, `_Azure` | Piranha (15), Piranha_Black (5), Pike (15) |
| `Zone1/…_Fish_Shores` | `Env_Zone1_Shores` | Clownfish, Tang ×4, Pufferfish, Lobster |
| `Zone2/…_Fish_Tier1` | `Env_Zone2_Savanna` | Catfish, Eel_Moray |
| `Zone2/…_Fish_Tier3` | `Env_Zone2_Deserts`, `_Oasis` | Piranha_Black, Trilobite_Black |
| `Zone2/…_Fish_Shores` | `Env_Zone2_Shores` | Jellyfish ×6, Tang_Blue, Clownfish, Pufferfish (+ Tortoise) |
| `Zone3/…_Fish_Tier1` | `Env_Zone3_Tundra` | Bluegill, Pike |
| `Zone3/…_Fish_Tier2` | `Env_Zone3_Forests`, `_Mountains` | Salmon |
| `Zone3/…_Fish_Tier3` | `Env_Zone3_Glacial` | Frostgill, Trilobite |
| `Zone3/…_Fish_Shores` | `Env_Zone3_Shores` | Frostgill, Trilobite, Snapjaw |
| `Zone0/Spawns_Zone0_Oceans_Cold` | `Env_Zone0_Cold` | Salmon, Frostgill |
| `Zone0/…_Oceans_Temperate` | `Env_Zone0_Temperate` | Whale_Humpback (2), Minnow, Bluegill |
| `Zone0/…_Oceans_Warm` | `Env_Zone0_Warm` | Shark_Hammerhead (2), Bluegill, Eel_Moray |

- Marqueur `zip:Server/NPC/Spawn/Markers/Ocean_Fish.json` (Bluegill, Clownfish, Minnow, Tang_Blue, Trout_Rainbow, après `PT15M` de jeu) ; autres marqueurs `Eel_Moray.json`, `Shark.json`. Des grottes de zone 3 font apparaître trilobites et assimilés (`Spawn/Beacons/Zone3/Zone3_Cave_Volcanic_*`).
- Pour un mod de pêche : les **environnements** (`Env_ZoneN_*`) sont la clé naturelle d'une table de prises fidèle au monde ; on lit l'environnement d'une case par `EnvironmentSection` (`plugin-b-api.md` § 30, l. 848).

### 1.5 PNJ « pêcheur »

- `zip:Server/NPC/Roles/Intelligent/Faction/Tuluk/Tuluk_Fisherman.json` : `Variant` de `Template_Placeholder` (aucun comportement), modèle `zip:Server/Models/Intelligent/Tuluk/Tuluk_Fisherman.json` (parent `Tuluk`, bonnet d'hiver, veste), butin vide (`zip:Server/Drops/NPCs/Intelligent/Tuluk/Drop_Tuluk_Fisherman.json` = `{}`). Pas de canne en main.

### 1.6 Sons, particules, animations

- Sons de poisson : `SFX_Fish_Death`, `SFX_Fish_Flee`, `SFX_Fish_Hurt` (`zip:Server/Audio/SoundEvents/SFX/NPC/Ocean/Fish/`), catégorie `AudioCat_NPC_Fish`.
- Sons d'eau : `SFX_Water_MoveIn`, `SFX_Water_MoveOut` (`zip:Server/Audio/SoundEvents/BlockSounds/Water/`), réutilisés par la nasse et le relâcher de poisson.
- Sons du grappin (utilisables pour un lancer et un moulinet) : `SFX_Tool_Hookshot_Fire`(`_Local`), `SFX_Tool_Hookshot_Attach`, `SFX_Tool_Hookshot_Reel`(`_Local`) (`zip:Server/Audio/SoundEvents/SFX/Tools/Hookshot/`, fichiers `Tool_Hookshot_Reel_0[1-4].ogg`…). Aucun son « fishing », « cast » ou « bobber ».
- Particules d'eau : jeu de bloc `zip:Server/Item/Block/Particles/Water.json` (`SoftLand`/`MoveOut`/`Hit` → `Water_Splash_Sofr_Ver4`, `HardLand`/`Break` → `Water_Splash`, `Run`, `Sprint`). Ces systèmes `Water_Splash*` sont rangés sous `zip:Server/Particles/_Test/WaterRnD/` (« test », mais référencés par le jeu). Autres : `Particles/Block/Water/Water_Bubble_Stream`, `Underwater_Effects`, et les émetteurs `Spawners/Fish_Spiral_Horizontal`, `Water_Small_Burst`, `Water_Circle_Run`, `Bubbles`. `Fish_Spiral_Horizontal` et `NPC/Emotions/Spawners/Fish_Bubbles` ne sont référencés par aucun système.
- Animations : aucune animation de pêche (rien sous `Common/Characters/Animations/` ne contient fish, cast ou reel). Le grappin a `Shoot` (3e personne, en mouvement, 1re personne ; `zip:Server/Item/Animations/Hookshot.json`, parent `Handgun`).

### 1.7 Recherche sur le web : pêche officielle

- Aucune annonce officielle trouvée sur hytale.com (recherche `site:hytale.com fishing` sans résultat du domaine). Les guides décrivent la nasse comme seule pêche vanilla et la canne comme « attendue », sans citation des développeurs : [nerdschalk](https://nerdschalk.com/how-to-fish-in-hytale-complete-fishing-guide/), [progameguides](https://progameguides.com/hytale/hytale-fishing-guide-how-to-catch-fish/), [thespike.gg](https://www.thespike.gg/hytale/beginner-guides/fishing-guide). Fiches d'objets : [isaacguru Tool_Fishing_Trap](https://hytale.isaacguru.com/wiki/hytale/item/Tool_Fishing_Trap), [Tool_Trap_Bait](https://hytale.isaacguru.com/wiki/hytale/item/Tool_Trap_Bait) (ajoutés à la sortie du 13 janvier 2026).
- Le modèle `FishingRod.blockymodel` inutilisé laisse penser que Hypixel prépare une canne ; rien ne le confirme.

### 1.8 Mods de pêche de la communauté

| Mod | Licence, source | Ce qu'il fait | Comment (lu dans le code quand il est public) |
|---|---|---|---|
| [Gone Fishing](https://curseforge.com/hytale/mods/gone-fishing) (Mrbysco, ShyNieke) | MIT, [github.com/Mrbysco/GoneFishing](https://github.com/Mrbysco/GoneFishing) | canne (2 bâtons + 3 lin), clic droit sur l'eau, flotteur, touche, ferrage ; n'importe quel poisson vanilla | interaction Java `GoneFishingFish` sur `Secondary` ; **réutilise `FishingRod.blockymodel`** ; rayon `TargetUtil.getTargetBlock` avec un prédicat `fluidId != 0` (10 blocs) ; flotteur = entité modèle posée sur la case d'eau (pas de projectile, pas de ligne) ; minuteries en ticks dans un `EntityTickingSystem` ; UUID du flotteur dans les métadonnées de la canne ; prise par `ItemUtils.interactivelyPickupItem` ; événements `FishingStartedEvent`, `FishCaughtEvent`, `FishingFailedEvent` (API) |
| [Tiny Fishing](https://www.curseforge.com/hytale/mods/tiny-fishing) (skyru97) | Apache 2.0, [github.com/skyru97/tiny-fishing](https://github.com/skyru97/tiny-fishing) | lancer, attente, éclaboussure et son, fenêtre de ferrage, codex par biome | `docs/dev/TECHNICAL.md` : `PlayerMouseButtonEvent`, `HudManager`, `PageManager`, `ParticleUtil`, `SoundUtil` ; données JSON ; composant ECS persistant pour le codex |
| [Angler's Almanac](https://www.curseforge.com/hytale/mods/anglers-almanac) (RM20Dev) | GPLv3, [github.com/rm20killer/Anglers-Almanac](https://github.com/rm20killer/Anglers-Almanac) | prises par biome, mini-jeu à barre de tension (à la Stardew), livre-journal, appâts et cannes à statistiques | module `api/` séparé : `AnglersAlmanacAPI` (fournisseurs statiques), `ILootProvider.getRandomFish(FishingContext, modifiers)`, `ICatchManager`, événements `PreFishRollEvent` (forcer la prise), `LootCaughtEvent`, `FishingRodCastEvent`… ; la ligne est dessinée en **formes de débogage** (courbe de Bézier découpée en segments, `Utils/LineRender/FishingLineRender.java`) ; système de physique propre |
| [Cozy Tales - Fishing](https://www.curseforge.com/hytale/mods/cozy-tales-fishing) (Hexvane) | tous droits réservés (source visible, [github.com/gchougland/CozyTalesFishing](https://github.com/gchougland/CozyTalesFishing)) : idées seulement | ombres de poissons, lancer chargé, combat au moulinet, flotteurs équipables, bateau, aquarium | flotteur projectile qui se « verrouille » à la surface quand `StandardPhysicsProvider.isInFluid()` devient vrai, puis oscille par un sinus (`fishing/FishingBobberFloatSystem.java`) ; ligne faite d'**entités segments** (`FishingLineSegmentComponent`) ; HUD d'endurance pendant le combat |
| [HyFishing](https://www.curseforge.com/hytale/mods/hyfishing) (TheRedlotus) | tous droits réservés, pas de source | canne, banc du pêcheur, sac, prises par biome, taille et poids, records et classements | non public ; le journal annonce des poissons qui « cherchent le flotteur » (0.6.8) |

Ces mods visent 0.5 à 0.6.8 : leurs appels peuvent avoir changé en pre.5 (ex. `TargetUtil.getTargetBlock` prend maintenant un `ChunkStore`, § 3.3). Aucun nom de fichier de leurs dépôts n'évoque `Beam` (le contenu des fichiers n'a pas été parcouru) : Angler's Almanac et Cozy Tales dessinent leur ligne autrement.

## 2. Une mécanique de pêche dans le serveur ?

Non. `grep -ril fish` sur tout `HY/` ne trouve rien ; `bobber`, `lure`, `fishing` non plus. Les interactions d'assets n'ont aucun type de pêche. Ce qui s'en rapproche :

- la nasse, entièrement en assets (`FarmingBlock`, `HarvestCrop`, `ChangeFarmingStage`, listes de tirage), § 1.3 ;
- le grappin (`zip:Server/Item/Items/Tool/Hookshot/Tool_Hookshot.json`) : `Primary` → animation `Shoot` en parallèle d'une interaction `Projectile` (`Config: Projectile_Config_Hookshot`, `Lifetime` 1,0) ; dans `zip:Server/ProjectileConfigs/Tools/Projectile_Config_Hookshot.json` : à l'apparition, `AttachBeam` (`BeamConfig: Rope`, `TargetEntity: Owner`, `Scale` 0,25, `TargetNodeName: R-Attachment`, l. 25-29) ; sur un bloc taggé `GadgetUse=Hookable` (l. 53), `ChangeState` puis `ApplyForce` vers le bloc (l. 74) et le son `Reel` ; sur une entité, `DamageEntity` à 0 avec un recul. C'est le modèle le plus proche d'un lancer avec ligne, en assets seuls ;
- l'aptitude `ChainHook` (`zip:Server/Item/Interactions/Abilities/ChainHook/`, effet `Ability_Hooked`, traînée `Entity/Trails/Abilities/Ability_ChainHook_Trail.json`) : un crochet qui tire une entité.

## 3. Ce qu'un plugin peut faire pour construire la pêche

### 3.1 Objet tenu : lancer chargé, ferrer, interactions propres

- Interaction chargée `Charging` (`HY/server/core/modules/interaction/interaction/config/client/ChargingInteraction.java`, clés `MinDelay`, `MaxDelay`, `MaxTotalDelay`, `AllowIndefiniteHold`, `DisplayProgress`, `CancelOnOtherClick`, `FailOnDamage`, `Next`, `Failed`…). Exemple vanilla : `zip:Server/Item/Interactions/Abilities/ChargedShot/Ability_ChargedShot_Cast.json`, où `Next` est une table **durée tenue → interaction** (`"0.0"`, `"0.6"`, `"1.35"`, `"2.1"`, `"3.0"`), avec barre de progression : un lancer à paliers de puissance tient en assets.
- Interaction Java propre : `getCodecRegistry(Interaction.CODEC).register("Nom", Classe.class, Classe.CODEC)` (`HY/builtin/abilities/AbilitiesPlugin.java:72`) ou `Interaction.CODEC.register(…)` (`HY/builtin/beam/BeamPlugin.java:58`). Une `SimpleInstantInteraction` reçoit le `InteractionContext` (`getHeldItem()`, `getHeldItemContainer()`, `getHeldItemSlot()`, `getCommandBuffer()`, `getEntity()`, `getOwningEntity()` ; `plugin-b-api.md` § 45, `HY/builtin/beam/interaction/AttachBeamInteraction.java:70-86`). Gone Fishing ferre ainsi sur `Secondary`.
- Événements bruts : `PlayerMouseButtonEvent` (annulable ; `getItemInHand`, `getTargetBlock`, `getTargetEntityRef`, `getMouseButton`, `HY/server/core/event/events/player/PlayerMouseButtonEvent.java:14-81`). `PlayerInteractEvent` est `@Deprecated` (l. 13).
- État par pile (flotteur lancé, appât, statistiques de canne) : métadonnées BSON de l'`ItemStack` (`withMetadata`, `getFromMetadataOrNull`, `replaceItemStackInSlot`, `plugin-b-api.md` § 45).

### 3.2 Projectile flotteur : lancer, physique, eau

- Lancer par asset : interaction `Projectile` (`Config`, `Lifetime`), comme le grappin. Lancer par Java : `ProjectileModule.get().spawnProjectile(creatorRef, commandBuffer, ProjectileConfig, position, direction)` et surcharges (échelle, vitesse, délai de disparition) (`HY/server/core/modules/projectile/ProjectileModule.java:138-266`). Le projectile reçoit `TransformComponent`, `ModelComponent` (modèle de la config), `BoundingBox`, `NetworkId`, `Projectile`, `Velocity`, la physique de la config, `DespawnComponent` (5 min par défaut, l. 76), le son de lancer ; puis l'interaction `ProjectileSpawn` du créateur (l. 262-283).
- Configs : `zip:Server/ProjectileConfigs/**` (`Projectile_Config_Arrow_Base`, `Tools/Projectile_Config_Hookshot`…), champs `Model`, `Physics`, `LaunchForce`, `SpawnOffset`, `SpawnRotationOffset`, `LaunchWorldSoundEventId`, `Interactions` (`ProjectileSpawn`, `ProjectileHit`, `ProjectileMiss`, `ProjectileBounce`).
- Physique `Standard` (`HY/server/core/modules/projectile/config/StandardPhysicsConfig.java`) : `Density` (défaut 700, l. 132), `Gravity`, `Bounciness`, `BounceLimit`, `BounceCount`, `SticksVertically`, `RotationMode`, `TerminalVelocityAir`/`Water`, `DensityAir` 1,2, `DensityWater` 998, `HitWaterImpulseLoss` 0,2, `SwimmingDampingFactor` 1,0, `AllowRolling`…
- **Flottabilité.** Dans un fluide, le volume immergé compte pour une densité 1000 (`StandardPhysicsProvider.java:265-285`) ; la masse vaut volume × `Density` (`HY/server/core/modules/physics/util/ForceProviderEntity.java:29-31`) ; la poussée ajoute `displacedMass × gravity` vers le haut (`ForceProviderStandard.java:48-49`). À 700, l'équilibre est à 70 % immergé : **le flotteur flotte seul** d'après le calcul. À l'entrée dans l'eau, `setInFluid(true)` et une perte d'impulsion `HitWaterImpulseLoss` (`HY/server/core/modules/projectile/system/StandardPhysicsTickSystem.java:234, 264`). **[in-game]** : oscillation, repos, dérive dans un courant.
- **Pas d'interaction à l'entrée dans l'eau.** `ProjectileHit`/`ProjectileMiss` naissent d'un contact solide ou d'une entité (`StandardPhysicsProvider.java:152-189`) ; le fluide n'est pas un contact (l. 265-285, `CONTINUE`). Un système ECS doit lire `StandardPhysicsProvider.isInFluid()` (l. 648) et `getState()` (`ACTIVE`/`RESTING`/`INACTIVE`, l. 111, 441-447, 696-700), comme Cozy Tales. Composant : `ProjectileModule.get().getStandardPhysicsProviderComponentType()` (l. 290).
- Alternative sans physique : poser une entité modèle sur la surface (Gone Fishing) et l'animer par `AnimationUtils.playAnimation` sur le créneau `Status`.

### 3.3 L'eau à une position, et un plan d'eau

- Lecture d'un fluide par case : `FluidSection.getFluidId`/`getFluidLevel`, `Fluid.getAssetMap()`, source si `MaxFluidLevel == 1` (`plugin-b-api.md` § 1 l. 37-59, l. 1109). Fluides d'eau : `Water_Source` (`MaxFluidLevel` 1, tag `Fluid: [Water]`), `Water` (parent `Water_Source`, `MaxFluidLevel` 8, ticker `SupportedBy: Water_Source`), `Water_Finite` (`zip:Server/Item/Block/Fluids/`). Le tag `Water` est celui des apparitions (`SpawnFluidTag`).
- Rayon visé : `TargetUtil.getTargetBlock(ChunkStore, BiIntPredicate(blockId, fluidId), origine xyz, direction xyz, maxDistance)` (`HY/server/core/util/TargetUtil.java:38-47`) ; `TargetUtil.getLook(ref, accessor)` donne l'œil et la direction (l. 465). En pre.5 le premier argument est un `ChunkStore` (Gone Fishing passait un `World`).
- Taille d'un plan d'eau : aucune API ; un parcours borné (BFS des cases `Water*` voisines, `SCAN_LIMIT`, CLAUDE.md § 4) est à écrire dans le cœur, derrière un port.

### 3.4 Ligne de pêche : `Beam`

- Plugin intégré `Hytale:Beam` (`jar:manifests.json:334`, dépendance `Hytale:EntityModule`). Un mod qui l'appelle en Java le déclare dans `manifest_dependencies`.
- Asset `Beam` (`Entity/Beams/*.json`, `HY/builtin/beam/BeamPlugin.java:45`) : un seul champ, `TexturePath`, « la texture répétée le long du faisceau » (`asset/Beam.java:26-30`). Vanilla : `Rope` (`Trails/Rope.png`), `Chain`, `Fire`, `Basic` (`zip:Server/Entity/Beams/`). Un pack peut ajouter le sien (fil de pêche fin).
- `BeamComponent` (`HY/builtin/beam/BeamComponent.java`) : jusqu'à 64 faisceaux par entité (l. 24), `add`, `remove`, `set`, `clear` ; `BeamComponent.spawn(store, position, [lifetime,] beams…)` crée une entité porteuse (l. 37-56). La source est l'entité porteuse (nœud `sourceNode`, décalage, échelle).
- `AttachedBeam` (`HY/builtin/beam/AttachedBeam.java`) : vers une entité (`toEntity`, nœud cible, décalages, l. 32-68), une position (`toPosition`, l. 71-90) ou un bloc (`toBlock`, l. 93-100) ; exactement une cible (l. 25-29). Un faisceau vers une entité disparue est retiré (`removeInvalid`, `BeamSystems.java:131`).
- Diffusion : `BeamSystems.Tracker` envoie `BeamsUpdate` aux joueurs qui voient l'entité (`BeamSystems.java:81-179`) ; un `BeamInstance` porte index, échelles, nœuds, décalages, `targetNetworkId` ou `targetPosition` (`HY/protocol/BeamInstance.java:19-32`) : **segment droit**, sans courbure.
- En assets : `AttachBeam` dans `ProjectileSpawn` (`TargetEntity: Owner`, `TargetNodeName: R-Attachment`) relie le projectile à la main du lanceur (`Projectile_Config_Hookshot.json:25-29`). Commande de test : `/beam` (`HY/builtin/beam/command/BeamCommand.java`).
- **[in-game]** : épaisseur à `Scale` 0,25, rendu à 20-30 blocs, nœud `R-Attachment` sur un PNJ citoyen (modèle `PlayerTestModel_V`, parent `Player`), mise à jour fluide quand le flotteur bouge.
- Autres moyens, moins bons : formes de débogage (`DebugUtils.addLine`, cylindres, envoyées à tout le monde, `plugin-b-api.md` § 38 ; Angler's Almanac), entités segments (Cozy Tales), traînées `Trails`.

### 3.5 Prises : listes de tirage, objet qui vole vers le joueur

- Listes de tirage : `ItemDropList` (`zip:Server/Drops/**`), conteneurs `Choice` pondérés, `Multiple`, `Single` avec `QuantityMin/Max` ; lecture `ItemDropList.getAssetMap().getAsset(id)` (`plugin-b-api.md` l. 1101). Un mod peut livrer ses propres listes, ou ses propres types d'assets (`plugin-b-api.md` § 53).
- Donner avec animation : `ItemUtils.interactivelyPickupItem(ref, stack, origin, accessor)` (`HY/server/core/entity/ItemUtils.java:43-84`) : événement `InteractivelyPickupItemEvent` annulable, `Player.giveItem`, notification, et si `origin` n'est pas nul, un objet visuel qui **vole de `origin` vers le joueur** (`ItemComponent.generatePickedUpItem`, `HY/server/core/modules/entity/item/ItemComponent.java:307-315`, `PickupItemComponent`) ; le reste tombe au sol. Pour un non-joueur, ajout au conteneur combiné ou chute.
- Lancer un vrai objet au sol avec vitesse : `ItemUtils.throwItem(ref, store, stack, direction, vitesse)` (l. 109-150, délai de ramassage 1,5 s) ou `ItemComponent.generateItemDrop(accessor, stack, position, rotation, vx, vy, vz)` (l. 242).

### 3.6 PNJ pêcheur (pour le citoyen)

- Objet en main et animation : `InventoryHelper.setHotbarItem`, `AnimationUtils.playAnimation(ref, slot, itemAnimationsId, animationId, …)` (`plugin-b-api.md` § 6). Aucun jeu d'animations d'objet ne contient de lancer : il faut un jeu propre (`Server/Item/Animations/<Id>.json` + `.blockyanim`) ou emprunter `Hookshot`/`Shoot`. **[in-game]**
- Lancer par un PNJ : `spawnProjectile(creatorRef = PNJ, …)` ; l'interaction `ProjectileSpawn` exige un `InteractionManager` sur le créateur (`ProjectileModule.java:269-282`), sinon rien ne s'exécute. Le plus sûr pour la ligne : ajouter soi-même `BeamComponent` au flotteur (`toEntity(PNJ, "R-Attachment")`). **[in-game]**

### 3.7 Météo, heure, lune, biome, zone

- Météo : `WeatherResource` (`plugin-b-api.md` § 18, l. 537-541 ; `getEffectiveWeatherIndex(env)`, l. 848).
- Heure : `WorldTimeResource`, jour = 60 % des 24 h (`plugin-b-api.md` § 31, § 41).
- Lune : `WorldTimeResource.getMoonPhase()` (`HY/server/core/modules/time/WorldTimeResource.java:130`), événement `MoonPhaseChangeEvent` (l. 136), `WorldConfig.TotalMoonPhases` (défaut 5, `HY/server/core/asset/type/gameplay/WorldConfig.java:75, 113`).
- Biome d'une colonne : `ChunkGenerator.getZoneBiomeResultAt(seed, x, z)` (`plugin-b-api.md` § 45). Environnement d'une case (zone + biome, clé des apparitions) : `EnvironmentSection.get(x, y, z)` (`plugin-b-api.md` l. 848).

### 3.8 Sons, particules, HUD, pages, ECS

- Sons : `SoundUtil.playSoundEvent3d(index, catégorie, x, y, z, accessor)` et surcharge volume/hauteur ; `SoundUtil.playSoundEvent2dToPlayer` ; index par `SoundEvent.getAssetMap().getIndex(id)` (`plugin-b-api.md` § 19, l. 558).
- Particules : `ParticleUtil.spawnParticleEffect(nom, position, accessor)` (75 blocs, `plugin-b-api.md` § 11). Une config de modèle peut porter des particules sur un nœud (`zip:Server/Models/Projectiles/Tools/Hookshot_Hook.json`, `Particles`).
- HUD (barre de mini-jeu) : `HudManager.addCustomHud`, `CustomUIHud.update` (`plugin-b-api.md` § 37). Pages : `plugin-b-api.md` § 9.
- ECS : `getEntityStoreRegistry().registerComponent(Classe, fournisseur)` ou `(Classe, id, BuilderCodec)` pour un composant sauvegardé (`HY/component/ComponentRegistryProxy.java:29-39`), `registerSystem` (l. 94), `registerResource` (l. 51). Exemple : `BeamPlugin.java:53-56`.

### 3.9 Statistiques d'objet, modificateurs, durabilité

- Pas d'enchantement. `StatModifiers` existe sur `ItemWeapon`, `ItemArmor` et `ItemUtility` (`HY/server/core/asset/type/item/config/`), pour les stats d'entité (`EntityStatMap`). Une « canne +chance » se fait par métadonnées de pile ou par un type d'asset du mod.
- Durabilité : `MaxDurability`, `DurabilityLossOnHit` sur l'objet (le grappin : 80 et 0,58) ; `ItemUtils.decreaseItemStackDurability` (`ItemUtils.java:199`) et `plugin-b-api.md` § 20.
- Qualité d'objet (`Common`…`Legendary`) et états `Variant` : le mécanisme des raretés de poisson (§ 1.1) est réutilisable pour des prises de qualité.

## 4. Ce qui bloque ou contraint

1. **Ligne droite.** `Beam` ne courbe pas (§ 3.4). Une ligne qui pend demande des segments (entités ou débogage), plus coûteux. **[in-game]** : rendu réel.
2. **Pas de signal « entre dans l'eau »** pour un projectile : un système qui sonde `isInFluid()` à chaque tick sur les seuls flotteurs (requête ECS sur notre composant).
3. **Flottaison non vue en jeu.** Le calcul dit oui (densité 700 < 1000) ; un courant (`Water` qui coule) peut le déplacer. Repli : verrouiller la hauteur à la surface (Cozy Tales).
4. **Durée de vie** : 5 min par défaut, à fixer par `despawnDelay` (§ 3.2).
5. **Aucune animation de pêche** (joueur comme PNJ), aucun son de pêche propre ; le modèle de canne vanilla existe mais n'est relié à rien, et pourrait changer si Hypixel l'utilise.
6. **Les poissons « relâchables »** (`Fish_*_Item`) sont des objets vivants, pas de la nourriture : la cuisine les découpe (`sp4b-hytale-food.md`). Une prise fidèle au monde donne ces objets, avec leurs états de rareté.
7. **Anomalies d'assets** : `Epic` → `Fish_Rare` ; `Ingredient_Fishbone` sans recette propre (§ 1.1, § 1.2).
8. **Licences des mods tiers** : Gone Fishing (MIT), Tiny Fishing (Apache 2.0), Angler's Almanac (GPLv3, compatible avec notre GPL-3.0) ; Cozy Tales et HyFishing sont tous droits réservés (idées seulement, comme Aetherhaven, `plugin-b-api.md` § 21.6).

## Questions qui demandent un essai en jeu

- Un projectile `Standard` de densité 700 flotte-t-il, se stabilise-t-il, dérive-t-il dans l'eau qui coule ? Passe-t-il en `RESTING` à la surface ?
- `AttachBeam`/`BeamComponent` : aspect de `Rope` à l'échelle 0,25, lisibilité à 20-30 blocs, suivi d'un flotteur qui bouge, nœud `R-Attachment` sur un joueur en 1re personne et sur un citoyen.
- Une interaction `Charging` sur un objet sans `PlayerAnimationsId` de lancer : quelle animation joue le client ?
- `interactivelyPickupItem` avec `origin` au flotteur : l'objet vole-t-il visiblement jusqu'au joueur ?
- `Fish_*_Item` d'un état (`*…_State_Rare`) obtenu par une liste de tirage d'un autre pack : nom, icône et particule corrects ?
- `ProjectileModule.spawnProjectile` avec un PNJ citoyen comme créateur : collisions, son, `ProjectileSpawn`.
