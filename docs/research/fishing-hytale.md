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

## 5. API vérifiées pour le plan de P1 (2026-10-04)

Relu dans `HY/` (0.7.0-pre.5) et `zip:` le 2026-10-04, pour que le code du plan compile. Rien n'a été lancé en jeu. `REPO/` = racine du dépôt. Tout ce qui suit tourne **sur le fil du monde** (tick d'un système, interaction, commande `AbstractPlayerCommand`), sauf mention.

### 5.1 Projectile : lancer depuis Java

- `com.hypixel.hytale.server.core.modules.projectile.ProjectileModule`, instance `ProjectileModule.get()` (`ProjectileModule.java:89`), module de cœur (`PluginManifest.corePlugin`, l. 68) : aucune dépendance de manifeste.
- Surcharges de `spawnProjectile` (toutes `@Nonnull Ref<EntityStore>`, l. 138-202) : `(creatorRef, commandBuffer, config, position, direction)` ; `(…, float scaleMultiplier, float speedMultiplier)` ; `(UUID predictionId, Long scaleSeed, creatorRef, commandBuffer, config, position, direction, long despawnDelay)` ; `(predictionId, scaleSeed, creatorRef, commandBuffer, config, position, direction, scale, speed)` ; et la complète `(predictionId, scaleSeed, creatorRef, commandBuffer, config, position, direction, float scaleMultiplier, float speedMultiplier, long despawnDelay)`. Types : `org.joml.Vector3d`, `com.hypixel.hytale.component.CommandBuffer<EntityStore>`.
- `despawnDelay` est en **millisecondes** ; `<= 0` donne 5 min (l. 76, 244). `ProjectileInteraction` passe `(long)(lifetime * 1000)` (`interaction/ProjectileInteraction.java:138-147`).
- **Piège** : `spawnProjectile` **modifie ses arguments** : `position.add(offset)` (l. 211) et `PhysicsMath.vectorFromAngles(…, direction)` (l. 209). Passer des copies.
- La vitesse initiale vaut `direction × LaunchForce × speedMultiplier` (l. 239) : le palier de charge peut passer par `speedMultiplier`.
- Le projectile est **non sauvegardé** : `holder.ensureComponent(EntityStore.REGISTRY.getNonSerializedComponentType())` (l. 240), et une archétype qui le contient n'a rien à sauver (`component/Archetype.java:107-134`). Un bouchon ne revient donc jamais au chargement d'un tronçon ; il disparaît au déchargement (`RemoveReason.UNLOAD`).
- `commandBuffer.addEntity` met l'ajout en file : un `commandBuffer.addComponent(ref, type, comp)` fait juste après passe dans l'ordre et teste `ref.isValid()` à l'exécution (`component/CommandBuffer.java:301-308`). `ProjectileInteraction` fait de même par `commandBuffer.run(store -> store.putComponent(…))` (l. 152-160).
- Config : `ProjectileConfig.getAssetMap().getAsset(id)` (`config/ProjectileConfig.java:185`, `DefaultAssetMap`, `null` si absent). Magasin `ProjectileConfigs` (`server/core/asset/AssetRegistryLoader.java:924-933`, chargé après `Interaction`, `SoundEvent`, `ModelAsset`, `ParticleSystem`). Un pack ajoute `Server/ProjectileConfigs/<…>/<Id>.json`.
- **Créateur PNJ** :
  - `StandardPhysicsConfig.apply` lit le `UUIDComponent` du créateur sans test (`config/StandardPhysicsConfig.java:161-180`, `assert` puis `getUuid()`) : un créateur sans `UUIDComponent` lève une `NullPointerException` ;
  - l'interaction `ProjectileSpawn` n'est lancée que si le créateur a un `InteractionManager` **et** si la config déclare une racine `ProjectileSpawn` (l. 262-282) ; sans `Interactions` dans notre config, rien n'est lancé, pour un joueur comme pour un PNJ : la ligne se pose en Java (§ 5.3) ;
  - au contact d'un bloc ou d'une entité, `impactConsumer` **retire le projectile** si ni lui ni son créateur n'ont d'`InteractionManager`, ou si le créateur n'est plus valide (`config/StandardPhysicsProvider.java:149-189`). Avec un `InteractionManager` et sans racine `ProjectileMiss`/`ProjectileHit`, il ne se passe rien : le projectile reste. **[in-game]** pour un citoyen.
- **Ne pas hériter du grappin** : `Projectile_Config_Hookshot` a un `ProjectileSpawn` qui retire l'entité après 0,5 s (`zip:Server/ProjectileConfigs/Tools/Projectile_Config_Hookshot.json`, `Simple RunTime 0.5` → `RemoveEntity`). `Projectile_Config_Arrow_Base` n'a pas d'`Interactions`.
- `RotationMode` : `None`, `Velocity`, `VelocityDamped`, `VelocityRoll` (`protocol/RotationMode.java:5-9`).

Config minimale, d'après `Projectile_Config_Arrow_Base` et `Bomb_Base` (`Server/ProjectileConfigs/HyAngler/HyAngler_Bobber.json`) :

```json
{
  "Model": "HyAngler_Bobber",
  "Physics": {
    "Type": "Standard",
    "Density": 700,
    "Gravity": 15,
    "TerminalVelocityAir": 50,
    "TerminalVelocityWater": 15,
    "RotationMode": "None",
    "Bounciness": 0.0,
    "SticksVertically": false
  },
  "LaunchForce": 20,
  "SpawnOffset": { "X": 0.15, "Y": -0.25, "Z": 0 },
  "LaunchWorldSoundEventId": "SFX_Tool_Hookshot_Fire",
  "LaunchLocalSoundEventId": "SFX_Tool_Hookshot_Fire_Local"
}
```

Clés de `Physics` `Standard` et défauts : `StandardPhysicsConfig.java:23-154` (`Density` 700, `Gravity` 0 si absent, `TerminalVelocityAir`/`Water` 1, `DensityWater` 998, `HitWaterImpulseLoss` 0,2, `BounceCount` -1…). `Model` nomme un `ModelAsset` (`Server/Models/<…>/HyAngler_Bobber.json`), comme `Hookshot_Hook` (`zip:Server/Models/Projectiles/Tools/Hookshot_Hook.json` : `HitBox` ±0,075, `Model`, `Texture`, `MinScale`/`MaxScale` 1). La masse vaut volume de la `HitBox` × `Density` (§ 3.2). **[in-game]** : `LaunchForce` 20 et la flottaison.

### 5.2 Le bouchon à chaque tick : physique, figer à la surface, notre composant

- `StandardPhysicsProvider` (`server.core.modules.projectile.config`), type par `StandardPhysicsProvider.getComponentType()` (l. 123, délègue à `ProjectileModule.get().getStandardPhysicsProviderComponentType()`). Méthodes : `boolean isInFluid()` (l. 648), `STATE getState()` / `setState(STATE)` (l. 441-447, `ACTIVE`, `RESTING`, `INACTIVE`, l. 696-700), `boolean isOnGround()` (l. 418), `UUID getCreatorUuid()` (l. 518).
- `isInFluid` est posé par `StandardPhysicsTickSystem` (`system/StandardPhysicsTickSystem.java:234, 239`). Le toucher d'une entité met `INACTIVE` (l. 290).
- **Figer** : en `INACTIVE`, le système de physique met seulement la vitesse à zéro et ne touche plus à la position (l. 109-110) ; en `RESTING`, il ne repart que sur une force externe ou un bloc changé (l. 114-121). Il relit la position dans `TransformComponent` à chaque tick actif (l. 138) et l'y réécrit (`finishTick`, `StandardPhysicsProvider.java:344-349`). D'où : à l'entrée dans l'eau, `physics.setState(INACTIVE)`, puis notre système écrit la hauteur.
- `TransformComponent` (`server.core.modules.entity.component`) : `getComponentType()`, `Vector3d getPosition()` (référence vivante), `setPosition(Vector3dc)` (l. 57-63). `Velocity` (`server.core.modules.physics.component`) : `setZero()`, `set(double, double, double)` (l. 47-63).
- Ordre : `StandardPhysicsTickSystem` tourne avant `TransformSystems.EntityTrackerUpdate` (l. 69-72). Notre système se place après lui et avant le suivi réseau.
- Notre composant : `getEntityStoreRegistry().registerComponent(Class, Supplier)` (`component/ComponentRegistryProxy.java:29-31`) le laisse sans codec, donc non sauvegardé ; `(Class, String id, BuilderCodec)` (l. 35-39) pour un composant sauvegardé. Modèle du dépôt : `REPO/plugin/src/main/java/dev/hycolony/plugin/npc/HyColonyComponents.java` (types statiques, enregistrés dans `setup()`), `MoveTarget.java` (composant à `clone()`).
- **Cadence** : un `EntityTickingSystem` tourne au rythme du monde, 30 ticks/s par défaut (`server/core/util/thread/TickingThread.java:17`). Les délais de vanilla (20 ticks/s) demandent un accumulateur de `dt` à 0,05 s, comme `REPO/plugin/src/main/java/dev/hycolony/plugin/ColonyTickSystem.java:31-46`.

Squelette (modèle : `REPO/plugin/src/main/java/dev/hycolony/plugin/npc/motion/CitizenSwimSystem.java:57-104`) :

```java
public final class BobberSystem extends EntityTickingSystem<EntityStore> {
    private final Set<Dependency<EntityStore>> dependencies = Set.of(
            new SystemDependency<>(Order.AFTER, StandardPhysicsTickSystem.class),
            new SystemDependency<>(Order.BEFORE, TransformSystems.EntityTrackerUpdate.class));

    @Override
    public Query<EntityStore> getQuery() {
        return Query.and(AnglerComponents.bobber(), StandardPhysicsProvider.getComponentType(),
                TransformComponent.getComponentType());
    }

    @Nonnull
    @Override
    public Set<Dependency<EntityStore>> getDependencies() {
        return dependencies;
    }

    @Override
    public void tick(float dt, int index, @Nonnull ArchetypeChunk<EntityStore> chunk,
            @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> buffer) {
        try {
            Bobber bobber = chunk.getComponent(index, AnglerComponents.bobber());
            StandardPhysicsProvider physics = chunk.getComponent(index, StandardPhysicsProvider.getComponentType());
            TransformComponent transform = chunk.getComponent(index, TransformComponent.getComponentType());
            if (!bobber.floating && physics.isInFluid()) {
                physics.setState(StandardPhysicsProvider.STATE.INACTIVE); // velocity zeroed, position ours
                bobber.floating = true;
            }
            // … position via transform.setPosition(…); removal via buffer.removeEntity(chunk.getReferenceTo(index), RemoveReason.REMOVE)
        } catch (RuntimeException e) { // out of a TickingSystem, an exception would stop the world's thread
            LOG.at(failed ? Level.FINE : Level.SEVERE).withCause(e).log("HyAngler: bobber tick failed");
            failed = true;
        }
    }
}
```

Imports : `com.hypixel.hytale.component.{ArchetypeChunk, CommandBuffer, Store, RemoveReason}`, `component.dependency.{Dependency, Order, SystemDependency}`, `component.query.Query`, `component.system.tick.EntityTickingSystem`, `server.core.modules.entity.system.TransformSystems`, `server.core.modules.projectile.system.StandardPhysicsTickSystem`. `CommandBuffer.removeEntity(Ref, RemoveReason)` (`CommandBuffer.java:230`). Enregistrement : `getEntityStoreRegistry().registerSystem(new BobberSystem())` (`ComponentRegistryProxy.java:94`). **[in-game]** : le client suit-il une position écrite à la main sur un projectile `INACTIVE` ?

### 5.3 La ligne : `BeamComponent`, `AttachedBeam`, asset `Beam`

- `com.hypixel.hytale.builtin.beam.BeamComponent` : `static ComponentType<EntityStore, BeamComponent> getComponentType()` (l. 32), constructeurs `BeamComponent()`, `BeamComponent(AttachedBeam...)` (l. 58-63), `void add(AttachedBeam)`, `boolean remove(AttachedBeam)`, `void set(List<AttachedBeam>)`, `void clear()`, `List<AttachedBeam> getBeams()` (non modifiable) (l. 69-108) ; `static Ref<EntityStore> spawn(Store, Vector3d, [float lifetime,] AttachedBeam...)` crée une entité porteuse (l. 37-56).
- `com.hypixel.hytale.builtin.beam.AttachedBeam` (record, `AttachedBeam.java:12-22`) : `toEntity(int beamIndex, float scale, @Nullable String sourceNode, Ref<EntityStore> target, @Nullable String targetNode)` (l. 32) ; `toEntity(int, float sourceScale, float targetScale, String sourceNode, Ref, String targetNode)` (l. 37) ; variantes avec `Vector3fc sourceOffset`/`targetOffset` (l. 44, 57) ; `toPosition(int, float, String, Vector3dc)` (l. 71) ; `toBlock(int, float, String, int, int, int)` (l. 93). Exactement une cible, sinon `IllegalArgumentException` (l. 25-29). L'**échelle** est `scale` (ou `sourceScale`/`targetScale`) : le grappin met 0,25 (`Projectile_Config_Hookshot.json`).
- Index de l'asset : `Beam.getAssetMap().getIndex("HyAngler_Line")` (`builtin/beam/asset/Beam.java:52`), `Integer.MIN_VALUE` si inconnu (`assetstore/map/IndexedLookupTableAssetMap.java:30, 33`), comme `AttachBeamInteraction.processConfig` (l. 63-67).
- Diffusion : `BeamSystems.Tracker` retire chaque tick les faisceaux vers une entité disparue (`BeamSystems.java:131`) et n'envoie qu'en cas de changement ou de nouveau spectateur ; retirer le composant envoie la suppression (`BeamSystems.Remove.onComponentRemoved`, l. 69-77). L'entité porteuse doit avoir un `NetworkId` (un projectile en a un, `ProjectileModule.java:223`).
- Asset : magasin `Entity/Beams` (`BeamPlugin.java:40-52`), un seul champ `TexturePath`, validé par `CommonAssetValidator.TEXTURE_BEAM` : `.png` sous `Common/Beams/` ou `Common/Trails/` (`server/core/asset/common/CommonAssetValidator.java:21`, l. 80-100). `Rope.json` = `{"TexturePath": "Trails/Rope.png"}`, texture 32 × 32 (en-tête PNG de `zip:Common/Trails/Rope.png`). Le nôtre : `Server/Entity/Beams/HyAngler_Line.json` → `{"TexturePath": "Beams/HyAngler/Line.png"}` et `Common/Beams/HyAngler/Line.png`.
- Manifeste : `Hytale:Beam` (dépend de `Hytale:EntityModule`, `jar:manifests.json:332-342`).

```java
int line = Beam.getAssetMap().getIndex(ids.beam()); // "HyAngler_Line"
if (line != Integer.MIN_VALUE) {
    buffer.addComponent(bobberRef, BeamComponent.getComponentType(),
            new BeamComponent(AttachedBeam.toEntity(line, 0.25f, null, playerRef, "R-Attachment")));
}
```

La cible est un **nœud du modèle de l'entité** (le joueur), pas de l'objet tenu : un nœud `Tip` de la canne n'est pas adressable par `AttachedBeam` (rien ne vise un objet tenu dans `BeamInstance`, `protocol/BeamInstance.java:19-32`). **[in-game]** : départ de la ligne depuis `R-Attachment`.

### 5.4 Lancer chargé `Charging` et interaction Java propre

- `ChargingInteraction` (`server.core.modules.interaction.interaction.config.client`) : clés `MinDelay`, `MaxDelay`, `MaxTotalDelay`, `FailOnDamage`, `CancelOnOtherClick` (défaut vrai), `Forks`, `Failed`, `AllowIndefiniteHold`, `DisplayProgress` (défaut vrai), `Next` (table `float → interaction`), `MouseSensitivityAdjustmentTarget`/`Duration`, `Delay` (l. 55-191, défauts l. 204-208). Choix dans `Next` : la plus grande clé `<=` la charge (`jumpToChargeValue`, l. 312-330).
- Les entrées de `Next` peuvent être des objets en ligne (`zip:Server/Item/Items/Weapon/Spellbook/Weapon_Spellbook_Rekindle_Embers.json:39-106`, clés `"0"`, `"0.8"`) : chaque palier est une instance de notre type, avec son propre champ de force. Pas besoin de lire la charge en Java.
- Enregistrer un type : `getCodecRegistry(Interaction.CODEC).register("HyAngler_Cast", CastInteraction.class, CastInteraction.CODEC)` dans `setup()` (`server/core/plugin/PluginBase.java:222`, `CodecMapRegistry.Assets.register(String, Class, BuilderCodec)`, l. 62 ; exemple `builtin/abilities/AbilitiesPlugin.java:72-76`). `Interaction.CODEC` est un `AssetCodecMapCodec<String, Interaction>` (`config/Interaction.java:83`). Aucune interaction Java dans le dépôt à ce jour.
- Base `SimpleInstantInteraction` (`server.core.modules.interaction.interaction.config`) : `protected abstract void firstRun(@Nonnull InteractionType, @Nonnull InteractionContext, @Nonnull CooldownHandler)` (l. 48). **Redéfinir `simulateFirstRun` vide** : par défaut il rappelle `firstRun` (l. 50-52), et une entité sans client distant (PNJ) passe par la simulation **et** par le tick (`server/core/entity/InteractionManager.java:509-530, 710-735`). Exemple : `builtin/abilities/interaction/LaunchAbilityProjectileInteraction.java:36-53`. Le client reçoit un `SimpleInteraction` (`SimpleInteraction.generatePacket`, l. 121), attente `WaitForDataFrom.None` (l. 61).
- `InteractionContext` (`server.core.entity`) : `@Nonnull Ref<EntityStore> getEntity()` (l. 205), `@Nullable Ref<EntityStore> getOwningEntity()` (l. 210), `@Nullable ItemStack getHeldItem()` (l. 264), `@Nullable ItemContainer getHeldItemContainer()` (l. 255), `byte getHeldItemSlot()` (l. 259), `int getHeldItemSectionId()` (l. 250), `@Nullable CommandBuffer<EntityStore> getCommandBuffer()` (l. 384).
- Le second clic droit (ramener) relance le même `Charging` : notre interaction regarde si un lancer est actif (ramener) ou non (lancer). **[in-game]** : un clic bref donne l'entrée `"0"` ; animation jouée sans `ItemAnimationId` de lancer.

```java
public final class CastInteraction extends SimpleInstantInteraction {
    public static final BuilderCodec<CastInteraction> CODEC = BuilderCodec.builder(
                    CastInteraction.class, CastInteraction::new, SimpleInstantInteraction.CODEC)
            .appendInherited(new KeyedCodec<>("Power", Codec.DOUBLE),
                    (o, v) -> o.power = v, o -> o.power, (o, p) -> o.power = p.power)
            .add()
            .build();
    private double power = 1.0;

    @Override
    protected void firstRun(@Nonnull InteractionType type, @Nonnull InteractionContext ctx, @Nonnull CooldownHandler cd) {
        CommandBuffer<EntityStore> buffer = ctx.getCommandBuffer();
        Ref<EntityStore> user = ctx.getEntity();
        ItemStack rod = ctx.getHeldItem();
        if (buffer == null || !user.isValid() || rod == null) {
            return;
        }
        // … cast or reel
    }

    @Override
    protected void simulateFirstRun(@Nonnull InteractionType type, @Nonnull InteractionContext ctx, @Nonnull CooldownHandler cd) {}
}
```

Objet (`Server/Item/Items/HyAngler/HyAngler_Rod_Crude.json`, bloc `Interactions`) :

```json
"Interactions": {
  "Secondary": {
    "Interactions": [
      {
        "Type": "Charging",
        "AllowIndefiniteHold": true,
        "DisplayProgress": true,
        "OnItemChangeBehavior": "Cancel",
        "Next": {
          "0":   { "Type": "HyAngler_Cast", "Power": 0.5 },
          "0.5": { "Type": "HyAngler_Cast", "Power": 0.75 },
          "1":   { "Type": "HyAngler_Cast", "Power": 1.0 }
        }
      }
    ]
  }
}
```

### 5.5 Donner la prise, états de rareté, usure

- `ItemUtils.interactivelyPickupItem(@Nonnull Ref<EntityStore> ref, @Nonnull ItemStack itemStack, @Nullable org.joml.Vector3d origin, @Nonnull ComponentAccessor<EntityStore> accessor)` : `static void` (`server/core/entity/ItemUtils.java:43-84`). Pour un non-joueur, ajout au conteneur combiné ou chute (l. 78-81).
- `new ItemStack(String itemId, int quantity)` (`server/core/inventory/ItemStack.java:113`) : lève `IllegalArgumentException` si `quantity <= 0` ou id `"Empty"` (l. 76-87) ; durabilité = `MaxDurability` de l'objet.
- État de rareté : `Item.getAssetMap().getAsset("Fish_Bluegill_Item").getItemIdForState("Rare")` (`asset/type/item/config/Item.java:906-909`, `@Nullable`) rend l'id de l'état (`*Fish_Bluegill_Item_State_Rare`, forme des listes de la nasse) ; les clés sont celles du bloc `State` (`Item.java:1314-1321`). Ou `stack.withState("Rare")`, qui lève `IllegalArgumentException` sur un état inconnu (`ItemStack.java:219-226`). `Rarities` vrai par défaut = `getItemIdForState("Uncommon") != null`.
- **`decreaseItemStackDurability` ne sert pas pour une canne** : il ne retire de durabilité qu'à une armure ou une arme, et de `DurabilityLossOnHit` (`ItemUtils.java:199-240`). Pour la canne : `static ItemStackSlotTransaction updateItemStackDurability(Ref<EntityStore> ref, ItemStack itemStack, ItemContainer container, int slotId, double durabilityChange, ComponentAccessor<EntityStore> accessor)` (l. 169-196) : `replaceItemStackInSlot`, puis message « objet cassé » et son `SFX_Item_Break` si la pile vient de casser. Le mode créatif est à tester avant : `ItemUtils.canDecreaseItemStackDurability(ref, accessor)` (l. 158-161). Une pile à 0 reste dans l'emplacement, `isBroken()` vrai (`ItemStack.java:139-141`).
- Pile en main hors interaction : `InventoryComponent.getItemInHand(ComponentAccessor, Ref)` (`server/core/inventory/InventoryComponent.java:291-299`, `@Nullable`).

```java
ItemStack rod = ctx.getHeldItem();
ItemContainer hand = ctx.getHeldItemContainer();
if (rod != null && hand != null && ItemUtils.canDecreaseItemStackDurability(user, buffer)) {
    ItemUtils.updateItemStackDurability(user, rod, hand, ctx.getHeldItemSlot(), -wear, buffer);
}
ItemUtils.interactivelyPickupItem(user, new ItemStack(catchId, count), new Vector3d(bobberPos), buffer);
```

### 5.6 Le monde à une case : heure, lune, météo, ciel, environnement, zone

- **Heure** : `WorldTimeResource` (`server.core.modules.time`), `world.getEntityStore().getStore().getResource(WorldTimeResource.getResourceType())` (modèle `REPO/plugin/src/main/java/dev/hycolony/plugin/adapter/HytaleGameClock.java:89-91`). Heure en double : `time.getGameDateTime().toLocalTime().toNanoOfDay() / 3.6e12` (méthode de `HytaleGameClock.dayTime`, l. 55) ou `time.getDayProgress() * 24` (`WorldTimeResource.java:333-335`, précision d'une seconde). Lever 4 h 48, coucher 19 h 12 (`SUNRISE_SECONDS`, `DAYTIME_SECONDS`, l. 37-40).
- **Lune** : `int getMoonPhase()` (l. 130) ; nombre de phases `world.getGameplayConfig().getWorldConfig().getTotalMoonPhases()` (l. 312).
- **Météo à une case** : `WeatherResource` (`builtin.weather.resources`), `getForcedWeatherIndex()` (0 sans météo forcée), `getWeatherIndexForEnvironment(int)` (`Integer.MIN_VALUE` avant le premier tirage), `getEffectiveWeatherIndex(int)` (`WeatherResource.java:35-46`). Id : `Weather.getAssetMap().getAsset(index).getId()` (`getAsset` rend `null` hors bornes). Helper à copier : `REPO/plugin/src/main/java/dev/hycolony/plugin/adapter/HytaleWorldQuery.java:84-104` (`isRainingAt` : environnement de la case, météo, puis particule de la météo dans une liste de l'id-map). Manifeste : `Hytale:Weather` (`jar:manifests.json:375-385`) ; HyColony ne le déclare pas.
- **Ids de météo** (nom de fichier = id, `zip:Server/Weathers/**`), classés par la particule `Particle.SystemId` :
  - pluie : `Zone1_Rain` (`Rain`), `Zone1_Rain_Light` (`Rain_Light`), `Zone1_Swamp_Rain` (`Rain_Heavy`), `Zone3_Rain` (`Rain_Light`), `Zone4_GhostForest_Rain`, `Zone4_Wastes_Rain` (`Rain`), `Zone4_Wastes_Rain_Heavy` (`Rain_Heavy`), `Dungeon_Cursed_Crypt_Graveyard` (`Rain`) ;
  - orage : `Zone1_Storm`, `Zone2_Thunder_Storm`, `Zone4_Storm`, `Zone4_Swamp_Storm`, `Skylands_Rapid_Marsh_Stormy` (tous `Rain_Heavy`) ;
  - neige : `Zone3_Snow` (`Snow_Light`), `Zone3_Snow_Heavy` (`Snow_Heavy`), `Zone3_Snow_Storm` (`Snow_Storm`) ;
  - autres tempêtes, sans eau : `Zone2_Sand_Storm` (`Sand_Storm`), `Zone4_AshWastes_Storm` (`Ash_Storm`).
  
  Aucun drapeau « orage » dans l'asset : l'orage est une liste d'ids (id-map).
- **Ciel visible** : pas de `canSeeSky`. Équivalent vanilla : le modificateur de croissance par la pluie teste `heightmapColumn.getHeight(x, z) <= worldY` (`builtin/adventure/farming/config/modifiers/WaterGrowthModifierAsset.java:232-248`). Accès : `Ref<ChunkStore> col = world.getChunkStore().getChunkReference(ChunkUtil.indexChunkFromBlock(x, z))` (`null` si non chargé), puis `world.getChunkStore().getStore().getComponent(col, HeightmapColumn.getComponentType())` et `int getHeight(int x, int z)` (`server/core/universe/world/chunk/heightmap/HeightmapColumn.java:40, 80` ; `NO_HEIGHT = Integer.MIN_VALUE`, l. 28 ; coordonnées monde acceptées, `LocationRadiusProvider.java:60-67`). La hauteur ignore les blocs transparents (`classify`, l. 349-351), donc l'eau (`Water_Source` : `"Opacity": "Transparent"`). Un appel, aucun parcours. **[in-game]** : feuillage.
- **Environnement d'une case** : `Ref<ChunkStore> sec = world.getChunkStore().getChunkSectionReferenceAtBlock(x, y, z)` (`ChunkGrid.java:928`, `@Nullable`), `EnvironmentSection env = world.getChunkStore().getStore().getComponent(sec, EnvironmentSection.getComponentType())`, `int envIndex = env.get(x, y, z)` (`EnvironmentSection.java:63, 88`) ; id : `Environment.getAssetMap().getAsset(envIndex).getId()` (`server/core/asset/type/environment/config/Environment.java:74, 92`), ex. `Env_Zone1_Plains`. `BlockChunk.getEnvironment` est `@RestrictedApi` (`plugin-b-api.md` l. 848).
- **Zone** : `Environment` n'a pas de champ de zone, et son `data` est `protected` (l. 56). La zone est le **tag** `ZoneN` : `Env_ZoneN.json` porte `"Tags": {"ZoneN": []}` et chaque `Env_ZoneN_*` en hérite par `Parent` (les tags du parent sont fusionnés, `assetstore/codec/AssetBuilderCodec.java:173-180`). Test : `Environment.getAssetMap().getIndexesForTag(AssetRegistry.getOrCreateTagIndex("Zone1")).contains(envIndex)` (`assetstore/map/AssetMapWithIndexes.java:25-27`, ensemble vide pour un tag inconnu). Le nom de zone du générateur (`Zone.name()`, `server/worldgen/zone/Zone.java:11-13`, via `ZoneBiomeResult.getZoneResult().getZone()`) existe aussi, par colonne, comme `HytaleWorldQuery.biome`.

### 5.7 Fluide et bloc à une case

- Helper à copier : `REPO/plugin/src/main/java/dev/hycolony/plugin/npc/motion/MotionCells.java` (`solid` l. 27-40 : `BlockSection` puis `BlockType.getMaterial() == BlockMaterial.Solid` ; `water` l. 42-61 : `FluidSection.getFluidId(x, y, z)` en coordonnées monde, `Fluid.EMPTY_ID` = 0, tag `Fluid=Water` par `AssetRegistry.getOrCreateTagIndex`). Ne charge jamais de tronçon.
- Fluides d'eau : `Water_Source` (`MaxFluidLevel` 1, `"Tags": {"Fluid": ["Water"]}`, `Opacity` `Transparent`), `Water` et `Water_Finite` (`Parent: Water_Source`, `MaxFluidLevel` 8) (`zip:Server/Item/Block/Fluids/`). Niveau : `FluidSection.getFluidLevel(x, y, z)` (`FluidSection.java:207`), `Fluid.getMaxFluidLevel()` (`Fluid.java:308`).
- Air pour l'eau libre : pas de fluide et `BlockType.getMaterial() == BlockMaterial.Empty` (`HeightmapColumn.classify`, l. 350, qui lit le même champ).

### 5.8 La canne : objet et recettes

Recettes des haches (`zip:Server/Item/Items/Tool/Hatchet/Tool_Hatchet_*.json`) :

| Palier | Entrées | Banc | `TimeSeconds` | `MaxDurability` |
|---|---|---|---|---|
| Crude | `res:Rubble` ×2, `Ingredient_Fibre` ×2, `Ingredient_Stick` ×2 | `Fieldcraft` (`Tools`) **et** `Workbench` (`Workbench_Tools`), `KnowledgeRequired: false` | 0 | 200 |
| Copper | `Ingredient_Bar_Copper` ×3, `res:Wood_Trunk` ×6, `Ingredient_Fibre` ×3 | `Workbench` (`Workbench_Tools`) | 3 | 300 |
| Iron | `Ingredient_Bar_Iron` ×5, `Ingredient_Leather_Light` ×2, `Ingredient_Fabric_Scrap_Linen` ×2 | idem | 3,5 | 500 |
| Thorium | `Ingredient_Bar_Thorium` ×6, `Ingredient_Leather_Medium` ×2, `Ingredient_Fabric_Scrap_Linen` ×3 | idem, `"RequiredTierLevel": 2` | 4 | 700 |
| Cobalt | `Ingredient_Bar_Cobalt` ×7, `Ingredient_Leather_Heavy` ×2, `Ingredient_Fabric_Scrap_Shadoweave` ×4 | idem, `"RequiredTierLevel": 2` | 4 | 700 |

Le palier d'établi est `RequiredTierLevel` dans l'entrée de `BenchRequirement` (absent = palier 1). Bloc exact de Crude :

```json
"Recipe": {
  "TimeSeconds": 0,
  "KnowledgeRequired": false,
  "Input": [
    { "ResourceTypeId": "Rubble", "Quantity": 2 },
    { "ItemId": "Ingredient_Fibre", "Quantity": 2 },
    { "ItemId": "Ingredient_Stick", "Quantity": 2 }
  ],
  "BenchRequirement": [
    { "Id": "Fieldcraft", "Type": "Crafting", "Categories": [ "Tools" ] },
    { "Id": "Workbench", "Type": "Crafting", "Categories": [ "Workbench_Tools" ] }
  ]
}
```

Thorium et Cobalt : `{ "Type": "Crafting", "Categories": [ "Workbench_Tools" ], "Id": "Workbench", "RequiredTierLevel": 2 }`. Les haches Copper à Cobalt ont `"Parent": "Tool_Hatchet_Crude"` ; catégorie `Items.Tools`, `Tags.Type: ["Tool"]`.

Chemins validés : icône `.png` sous `Icons/ItemsGenerated/` ou `Icons/Items/` (`CommonAssetValidator.java:26`), modèle `.blockymodel` sous `Items/`, `NPC/`… (l. 36), texture sous `Items/`, `NPC/`… (l. 15). Objet minimal, sur le modèle de `REPO/plugin/src/main/resources/Server/Item/Items/HyColony/HyColony_Build_Goggles.json` :

```json
{
  "TranslationProperties": { "Name": "hyangler.item.rod_crude.name", "Description": "hyangler.item.rod_crude.description" },
  "Categories": [ "Items.Tools" ],
  "Quality": "Common",
  "MaxStack": 1,
  "MaxDurability": 200,
  "Icon": "Icons/Items/HyAngler/Rod_Crude.png",
  "Model": "Items/HyAngler/Rod_Crude.blockymodel",
  "Texture": "Items/HyAngler/Rod_Crude.png",
  "PlayerAnimationsId": "Item",
  "Tags": { "Type": [ "Tool" ] },
  "Recipe": { "…": "bloc de Tool_Hatchet_Crude, entrées de la spec" },
  "Interactions": { "Secondary": { "…": "§ 5.4" } }
}
```

Le modèle vanilla de référence : `zip:Common/Items/Tools/Fishing_Rod/FishingRod.blockymodel` et `FishingRod_Texture.png`.

### 5.9 Une espèce ajoutée par un pack : la perche

Objet `Server/Item/Items/HyAngler/Fish_Perch_Item.json`, calqué sur `Fish_Bluegill_Item.json` (parent `Template_Fish_Item` : catégorie `Fish`, tags `SpawnNPC`/`Fish`, `PlayerAnimationsId: Item`, `ItemSoundSetId: ISS_Items_Splatty`, particule au sol `Item`) :

```json
{
  "Parent": "Template_Fish_Item",
  "Quality": "Common",
  "TranslationProperties": { "Name": "hyangler.npcRoles.Perch.name" },
  "Model": "NPC/Swimming_Wildlife/Perch/Models/Model.blockymodel",
  "Texture": "NPC/Swimming_Wildlife/Perch/Models/Texture.png",
  "Animation": "NPC/Swimming_Wildlife/Bluegill/Animations/Swim/Swim.blockyanim",
  "Icon": "Icons/Items/HyAngler/Fish_Perch_Item.png",
  "InteractionVars": {
    "SpawnNPC_Entity": {
      "Interactions": [
        { "Parent": "SpawnNPC_Entity_Default", "EntityId": "Perch", "SpawnOffset": { "X": 0, "Y": 1, "Z": 0 },
          "Effects": { "WorldSoundEventId": "SFX_Water_MoveOut" } }
      ]
    }
  },
  "State": {
    "Uncommon":  { "Variant": true, "Quality": "Uncommon",  "ItemEntity": { "ParticleSystemId": "Drop_Uncommon" },
                   "ResourceTypes": [ { "Id": "Fish", "Quantity": 1 }, { "Id": "Fish_Uncommon" } ] },
    "Rare":      { "Variant": true, "Quality": "Rare",      "ItemEntity": { "ParticleSystemId": "Drop_Rare" },
                   "ResourceTypes": [ { "Id": "Fish", "Quantity": 1 }, { "Id": "Fish_Rare" } ] },
    "Epic":      { "Variant": true, "Quality": "Epic",      "ItemEntity": { "ParticleSystemId": "Drop_Epic" },
                   "ResourceTypes": [ { "Id": "Fish", "Quantity": 1 }, { "Id": "Fish_Epic" } ] },
    "Legendary": { "Variant": true, "Quality": "Legendary", "ItemEntity": { "ParticleSystemId": "Drop_Legendary" },
                   "ResourceTypes": [ { "Id": "Fish", "Quantity": 1 }, { "Id": "Fish_Legendary" } ] }
  },
  "ResourceTypes": [ { "Id": "Fish" }, { "Id": "Fish_Common" } ]
}
```

(`IconProperties` du Bluegill : `Scale` 0,4, `Rotation` [22,5, 45, 22,5], `Translation` [4, -13,5].) Notre état `Epic` porte `Fish_Epic`, pas l'anomalie `Fish_Rare` du Bluegill (§ 1.1).

Rôle `Server/NPC/Roles/HyAngler/Perch.json` (calqué sur `zip:Server/NPC/Roles/Aquatic/Freshwater/Bluegill.json`) :

```json
{
  "Type": "Variant",
  "Reference": "Template_Swimming_Passive",
  "Modify": {
    "Appearance": "Perch",
    "FlockArray": [ "Perch" ],
    "DropList": "Drop_Perch",
    "MaxHealth": 29,
    "IsMemory": true,
    "MemoriesCategory": "Freshwater",
    "NameTranslationKey": { "Compute": "NameTranslationKey" }
  },
  "Parameters": {
    "NameTranslationKey": { "Value": "hyangler.npcRoles.Perch.name", "Description": "Translation key for NPC name display" }
  }
}
```

Modèle `Server/Models/HyAngler/Perch.json` : un `Parent` hérite de tout, y compris `AnimationSets` (même procédé que `Bear_Polar.json` → `Bear_Grizzly`, `Tuluk_Fisherman.json` → `Tuluk`) :

```json
{
  "Parent": "Bluegill",
  "Model": "NPC/Swimming_Wildlife/Perch/Models/Model.blockymodel",
  "Texture": "NPC/Swimming_Wildlife/Perch/Models/Texture.png",
  "Icon": "Icons/ModelsGenerated/Bluegill.png"
}
```

Les animations du Bluegill ne conviennent que si la perche garde ses os (`zip:Server/Models/Swimming_Wildlife/Bluegill.json`, `HitBox` ±0,3 × 0,5, `EyeHeight` 0,25). Apparition `Server/NPC/Spawn/World/HyAngler/Spawns_HyAngler_Perch.json` (format de `Spawns_Zone1_Fish_Tier1.json` ; chaque fichier est un asset à part, il s'ajoute aux vanilla) :

```json
{
  "Environments": [ "Env_Zone1_Plains", "Env_Zone1_Forests", "Env_Zone1_Swamps", "Env_Zone3_Tundra" ],
  "NPCs": [ { "Weight": 10, "Id": "Perch", "Flock": "Group_Small", "SpawnFluidTag": "Water" } ],
  "DayTimeRange": [ 6, 24 ]
}
```

Butin `Server/Drops/NPCs/HyAngler/Drop_Perch.json`, copie de `zip:Server/Drops/NPCs/Swimming_Wildlife/Drop_Bluegill.json` (`Multiple` → `Choice` 100 → `Single` `Food_Fish_Raw` ×1). Le texte : `npcRoles.Perch.name = Perch` dans `hyangler.lang`, qui préfixe la clé du nom du fichier (`hycolony.lang:169` donne `hycolony.item.build_goggles.name`).

### 5.10 Commandes et selftest

- `AbstractCommandCollection(String name, String description)`, `addSubCommand(AbstractCommand)` (`server/core/command/system/AbstractCommand.java:416`) ; `AbstractPlayerCommand(String name, String description)`, `protected abstract void execute(CommandContext, Store<EntityStore>, Ref<EntityStore>, PlayerRef, World)` (`basecommands/AbstractPlayerCommand.java:19, 55-57`), lancé **sur le fil du monde** (`runAsync(…, world)`, l. 32-52). Arguments : `withRequiredArg(String, String, ArgumentType<D>)` (`AbstractCommand.java:941`), `ctx.get(arg)` ; `ArgTypes.ITEM_ASSET` rend un `Item` (`arguments/types/ArgTypes.java:478`), `ArgTypes.STRING` (l. 107). `setPermissionGroups()` sans groupe = opérateurs seuls (`REPO/plugin/src/main/java/dev/hycolony/plugin/command/HyColonyCommand.java:63-65`).
- Modèle à copier : `HyColonyCommand.java` (collection l. 51-61, sous-commande à arguments `Rank` l. 116-157, `SelfTest` l. 191-227, `report` l. 326-332) et `SelfTestReport.java` (interface `line(step, ok, detail)`) ; un fichier `*SelfTest.java` par domaine (`FoodSelfTest`, `ApiSelfTest`…). Enregistrement : `getCommandRegistry().registerCommand(…)` (`HyColonyPlugin.java:68`).

```java
static final class Give extends AbstractPlayerCommand {
    private final RequiredArg<Item> item;

    Give() {
        super("give", "Give a rod or a fish (operators)");
        this.item = withRequiredArg("item", "Item id", ArgTypes.ITEM_ASSET);
        setPermissionGroups();
    }

    @Override
    protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef player, @Nonnull World world) {
        Player.giveItem(new ItemStack(ctx.get(item).getId(), 1), ref, store); // Player.java:792
    }
}
```

### 5.11 Configuration du plugin

- `protected final <T> Config<T> withConfig(String name, BuilderCodec<T> codec)` (`server/core/plugin/PluginBase.java:116-127`) : **dans le constructeur**, sinon `IllegalStateException("Must be called before setup")`. `Config.get()` / `save()` (`server/core/util/Config.java:54-70`) ; fichier `<dataDirectory>/config.json` ; un fichier absent donne `codec.getDefaultValue()`.
- Modèle : `REPO/plugin/src/main/java/dev/hycolony/plugin/HyColonyPlugin.java:38-53` (constructeur, `save()` dont l'échec est journalisé), `config/HyColonyConfig.java:20-62` (sections), `config/CombatSection.java` (section d'une clé).
- **`ConfigQuarantine` est dans `dev.hyblockui.api`** (`REPO/blockui/src/main/java/dev/hyblockui/api/ConfigQuarantine.java`) : HyAngler, qui ne dépend d'aucun mod, ne peut pas l'appeler. Il lui faut sa propre copie, sinon un `config.json` malformé arrête le démarrage (`Config.load` décode le fichier dans `PluginBase.preLoad`, Javadoc de `ConfigQuarantine`, l. 15-19 ; `Config.java:39-51`).

```java
final class AnglerSection {
    static final BuilderCodec<AnglerSection> CODEC = BuilderCodec.builder(AnglerSection.class, AnglerSection::new)
            .append(new KeyedCodec<>("BiteTimeMultiplier", Codec.DOUBLE), (s, v) -> s.biteTimeMultiplier = v, s -> s.biteTimeMultiplier)
            .add()
            .append(new KeyedCodec<>("RodWear", Codec.BOOLEAN), (s, v) -> s.rodWear = v, s -> s.rodWear)
            .add()
            .append(new KeyedCodec<>("MaxLineDefault", Codec.INTEGER), (s, v) -> s.maxLineDefault = v, s -> s.maxLineDefault)
            .add()
            .append(new KeyedCodec<>("TestCommandOpOnly", Codec.BOOLEAN), (s, v) -> s.testCommandOpOnly = v, s -> s.testCommandOpOnly)
            .add()
            .build();
    double biteTimeMultiplier = 1.0;
    boolean rodWear = true;
    int maxLineDefault = 32;
    boolean testCommandOpOnly = true;
}
// HyAnglerConfig: .append(new KeyedCodec<>("HyAngler", AnglerSection.CODEC), (c, v) -> c.angler = v == null ? new AnglerSection() : v, c -> c.angler).add()
// constructor: this.config = withConfig("config", HyAnglerConfig.CODEC);
```

Les bornes (0,1 à 10 ; 8 à 64) sont rappliquées par le cœur, comme `ColonyConfig`.

### 5.12 Particules et sons

- `ParticleUtil.spawnParticleEffect(@Nonnull String name, @Nonnull Vector3dc position, @Nonnull ComponentAccessor<EntityStore> accessor)` (`server/core/universe/world/ParticleUtil.java:26`) ; surcharge avec une liste de joueurs (l. 49-50).
- `SoundUtil.playSoundEvent3d(int soundEventIndex, SoundCategory category, double x, double y, double z, ComponentAccessor<EntityStore> accessor)` (`server/core/universe/world/SoundUtil.java:147-149`) ; surcharge `(int, SoundCategory, Vector3d, accessor)` (l. 185) ; `playSoundEvent2dToPlayer(PlayerRef, int, SoundCategory)` (l. 57). Index : `SoundEvent.getAssetMap().getIndex(id)` (`server.core.asset.type.soundevent.config.SoundEvent`), `Integer.MIN_VALUE` si inconnu. `SoundCategory` est `com.hypixel.hytale.protocol.SoundCategory`.
- Helper à copier : `REPO/plugin/src/main/java/dev/hycolony/plugin/adapter/HytaleWorldEffects.java` (`tilled`, l. 165-185 : index, test `MIN_VALUE`, son 3D ; `particles`, l. 227-237).
- Ids confirmés dans `zip:` : particules `Water_Splash` (`Server/Particles/_Test/WaterRnD/Water_Splash.particlesystem`), `Water_Splash_Sofr_Ver4` (même dossier, celui des petits contacts d'eau), `Water_Bubble_Stream` (`Server/Particles/Block/Water/Water_Bubble_Stream.particlesystem`) ; sons `SFX_Water_MoveIn`, `SFX_Water_MoveOut` (`Server/Audio/SoundEvents/BlockSounds/Water/`), `SFX_Tool_Hookshot_Fire`, `SFX_Tool_Hookshot_Fire_Local`, `SFX_Tool_Hookshot_Reel`, `SFX_Tool_Hookshot_Reel_Local` (`Server/Audio/SoundEvents/SFX/Tools/Hookshot/`).

### 5.13 Codecs de nos types d'assets

- `Codec.BSON_DOCUMENT` (`BsonDocumentCodec implements Codec<BsonDocument>`, `codec/Codec.java:42-43`) est **`@Deprecated`** : précédent du dépôt, `@SuppressWarnings("deprecation")` sur le champ (`REPO/plugin/src/main/java/dev/hycolony/plugin/config/HyColonySection.java:14-32`). Usage vanilla dans un codec : `builtin/blocktick/procedure/SplitChanceBlockGrowthProcedure.java:22`. `new ArrayCodec<>(Codec.BSON_DOCUMENT, BsonDocument[]::new)` compile (`codec/codecs/array/ArrayCodec.java:34`, `(Codec<T>, IntFunction<T[]>)`).
- **Clé absente** : `BuilderCodec.decodeAndInherit0` ne parcourt que les clés présentes (`codec/builder/BuilderCodec.java:463-479`) : le champ garde sa valeur initiale (ou celle du `Parent`).
- **Clé inconnue** : `extraInfo.addUnknownKey` (l. 476), puis `AssetStore.logUnusedKeys` en WARNING seulement (`assetstore/AssetStore.java:1677-1692`).
- **Erreur de décodage** (type faux, JSON invalide) : `CodecException` (`BuilderCodec.java:418-420`), le fichier est noté en échec (`AssetStore.java:1448-1456`, `recordFailedToLoad`) ; pour un pack **immuable** (zip, jar), `shouldFail` (`server/core/asset/AssetRegistryLoader.java:246-247`) et `event.failed(…)` (l. 316-321) font **arrêter le serveur** (`server/core/HytaleServer.java:350-353`, « Asset validation FAILED »). Confirme `plugin-b-api.md` § 23 (lignes décalées en pre.5). Seule une règle vérifiée **après** le décodage (dans le cœur) écarte un fichier sans arrêter le serveur.

### 5.14 Écarts avec la spec du 2026-10-04 (à reporter dans le plan)

1. § 4 et § 7.3 : `ItemUtils.decreaseItemStackDurability` ne fait rien pour une canne (armures et armes seulement) → `updateItemStackDurability` (§ 5.5).
2. § 10 : « un bouchon trouvé au chargement d'un chunk est retiré » ne peut pas arriver : un projectile n'est jamais sauvegardé (§ 5.1). En revanche, le déchargement du tronçon retire le bouchon : le lancer doit s'annuler quand sa `Ref` n'est plus valide.
3. § 5 et § 7.3 : les délais de vanilla sont en ticks de 20/s, le monde tourne à 30/s : un accumulateur (§ 5.2).
4. § 6 et § 10 (« un fichier invalide est écarté… jamais le serveur ») : vrai pour une règle du cœur (objet inconnu, borne, condition inconnue), faux pour une erreur de type JSON dans un pack zip ou jar, qui arrête le serveur (§ 5.13). Lire les champs à risque en BSON brut, ou l'écrire tel quel dans la spec.
5. § 7.1 : le nœud `Tip` de la canne ne peut pas porter la ligne ; elle part du nœud `R-Attachment` du joueur (§ 5.3). **[in-game]**
6. § 4 : `WeatherResource` et `BeamComponent` sont des plugins intégrés : déclarer `Hytale:Beam` **et** `Hytale:Weather` dans le manifeste.
7. § 11 (config) : pas de `ConfigQuarantine` (HyBlockUI) pour HyAngler → copie propre (§ 5.11).
8. § 6.4 `Zone` : pas de champ de zone ; c'est le tag `ZoneN` de l'environnement (§ 5.6).
9. § 7.1 : la hache Crude se fabrique aussi à l'établi (`Workbench_Tools`) ; Thorium et Cobalt demandent l'établi de palier 2.
