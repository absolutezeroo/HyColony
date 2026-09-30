# Inventaire de configuration : MineColonies vs HyColony

Objectif : ce que MineColonies rend configurable l'est aussi dans HyColony ; ce que MineColonies code en dur reste une constante.

Sources :
- MC : clone local `ldtteam/minecolonies`, branche `version/main`, commit `6b3916a1` (23/09/2026).
  - `src/main/java/com/minecolonies/api/configuration/ServerConfiguration.java` (noté `SC:ligne`)
  - `.../ClientConfiguration.java` (`CC:ligne`), `.../CommonConfiguration.java` (`CoC:ligne`)
  - descriptions : `src/main/resources/assets/minecolonies/lang/manual_en_us.json`, clés `minecolonies.config.<clé>.comment`
- Hytale : `build/vineflower/hytale-server/com/hypixel/hytale/` (chargement de config, § 5).
- HyColony : `core/src/main/java/dev/hycolony/core/…` (noté `core:`), `plugin/src/main/java/dev/hycolony/plugin/…` (noté `plugin:`).

MC n'a que 65 options (56 serveur, 7 client, 2 commun). Les bornes viennent de `defineInteger(builder, key, default, min, max)` (`AbstractConfiguration.java:59`).

Attention : le fichier de langue MC contient encore des descriptions d'options **supprimées** (`builderbuildblockdelay`, `blockminingdelaymodifier`, `citizenrespawninterval`, `maximalretries`, `delaybetweenretries`, `updaterate`, `maxkeptbackups`, `secondsbetweenpermissionmessages`, `sendenteringleavingmessages`, etc.). Elles ne sont plus déclarées dans les classes de configuration de `version/main` : ce sont aujourd'hui des constantes en dur (voir § 4). Il ne faut pas les exposer.

## 1. Options de MineColonies

Légende de la colonne « Statut » :
- **ACTUEL** : touche un système que HyColony a déjà (colonie, citoyens, huttes, bâtisseur, requêtes, ordres de travail, lunettes, protection, permissions, commandes existantes) ;
- **futur** : système pas encore porté ;
- **n/a** : sans objet sur Hytale (mod, débogage Forge, rendu client propre à Minecraft).

### 1.1 Serveur, section `gameplay` (SC:123-142)

| Clé MC | Défaut | Bornes | Effet (manual_en_us.json) | Statut |
|---|---|---|---|---|
| `initialcitizenamount` | 4 | 1-10 | Nombre de citoyens initiaux | ACTUEL |
| `allowinfinitesupplychests` | false | - | Camps/navires de ravitaillement illimités | futur (ravitaillement) |
| `allowinfinitecolonies` | false | - | Abandonner sa colonie pour en fonder une autre | futur (abandon) |
| `allowotherdimcolonies` | true | - | Colonies hors de l'Overworld | ACTUEL (création de colonie ; sur Hytale, « dimension » = monde) |
| `maxcitizenpercolony` | 250 | 25-500 (`CITIZEN_LIMIT_MAX`, `CitizenConstants.java:33`) | Plafond de citoyens par colonie | ACTUEL |
| `enableindevelopmentfeatures` | false | - | Fonctions en développement | n/a |
| `alwaysrendernametag` | true | - | Afficher le nom des citoyens (`EntityCitizen.java:267`) | ACTUEL |
| `workersalwaysworkinrain` | false | - | Les ouvriers travaillent sous la pluie (`CitizenAI.java:307`) | porté : `Gameplay.WorkersAlwaysWorkInRain` (tous les ouvriers, `CitizenAI`) |
| `luckyblockchance` | 1 | 0-100 | % de minerai trouvé par le mineur | futur (mineur) |
| `minthleveltoteleport` | 3 | 0-5 | Niveau d'hôtel de ville pour la téléportation alliée | futur |
| `foodmodifier` | 1.0 | 0.1-100 | Multiplicateur de consommation de nourriture (`CitizenData.java:1168`) | futur (nourriture) |
| `diseasemodifier` | 5 | 1-100 | Fréquence des maladies | futur |
| `forceloadcolony` | true | - | Garder la colonie chargée | futur (chargement de chunks) |
| `loadtime` | 20 | 1-1440 | Durée de maintien après départ du joueur | futur |
| `colonyloadstrictness` | 3 | 1-15 | Quantité de chunks chargés | futur |
| `maxtreesize` | 400 | 1-1000 | Troncs max examinés par le forestier | futur (bûcheron) |
| `nosupplyplacementrestrictions` | false | - | Pas de contraintes de pose du camp | futur |
| `skyraiders` | false | - | Raiders qui apparaissent dans le ciel | futur (raids) |

### 1.2 Serveur, section `research` (SC:144-147)

| Clé | Défaut | Effet | Statut |
|---|---|---|---|
| `researchcreativecompletion` | true | Recherche instantanée en créatif | futur |
| `researchdebuglog` | false | Journal détaillé des datapacks de recherche | futur |
| `researchresetcost` | `["minecolonies:ancienttome:1"]` | Coût d'annulation d'une recherche | futur |

### 1.3 Serveur, section `commands` (SC:149-160)

Chaque option est vérifiée seulement si le joueur n'est pas opérateur (ex. `CommandDeleteColony.java:150`).

| Clé | Défaut | Commande MC | Statut |
|---|---|---|---|
| `canplayerusertpcommand` | false | `/mc rtp` | futur |
| `canplayerusecolonytpcommand` | false | `/mc colony teleport` | futur |
| `canplayeruseallytownhallteleport` | true | téléportation alliée | futur |
| `canplayerusehometpcommand` | false | `/mc home` | futur |
| `canplayeruseshowcolonyinfocommand` | true | `/mc colony info` | ACTUEL (`/hycolony info`) |
| `canplayerusekillcitizenscommand` | false | `/mc citizens kill` | futur |
| `canplayerusemodifycitizenscommand` | false | `/mc citizens modify` | futur |
| `canplayeruseaddofficercommand` | true | `/mc colony addOfficer` | ACTUEL (`/hycolony rank`) |
| `canplayerusedeletecolonycommand` | false | `/mc colony delete` | ACTUEL (`/hycolony delete`) |
| `canplayeruseresetcommand` | false | `/mc colony requestsystem-reset` | futur |

### 1.4 Serveur, section `claims` (SC:162-168)

| Clé | Défaut | Bornes | Effet | Statut |
|---|---|---|---|---|
| `maxColonySize` | 20 | 1-250 | Rayon de revendication max, en chunks | ACTUEL |
| `minColonyDistance` | 8 | 1-200 | Distance min entre colonies, en chunks | ACTUEL |
| `initialColonySize` | 4 | 1-15 | Rayon initial, en chunks | ACTUEL |
| `maxdistancefromworldspawn` | 30000 | 1000-`Integer.MAX_VALUE` | Distance max au spawn, en blocs (`CreateColonyMessage.java:155`) | ACTUEL |
| `mindistancefromworldspawn` | 0 | 0-1000 | Distance min au spawn, en blocs (`CreateColonyMessage.java:147`) | ACTUEL |

### 1.5 Serveur, section `combat` (SC:170-182) : tout est futur

`enablecolonyraids` (true), `raidDifficulty` (5, 0-10), `maxRaiders` (80, 6-400), `raidersbreakblocks` (true), `averagenumberofnightsbetweenraids` (14, 1-50), `minimumnumberofnightsbetweenraids` (10, 1-30), `mobattackcitizens` (true), `raidersbreakdoors` (true), `guardDamageMultiplier` (1.0, 0.1-15), `guardhealthmult` (1.0, 0.1-5), `pvp_mode` (false). Les bornes des raids viennent de `Constants.java:24-28`.

### 1.6 Serveur, section `permissions` (SC:184-188)

| Clé | Défaut | Bornes | Effet | Statut |
|---|---|---|---|---|
| `enablecolonyprotection` | true | - | Protection des blocs de la colonie | ACTUEL |
| `turnoffexplosionsincolonies` | `DAMAGE_ENTITIES` | enum `DAMAGE_NOTHING`, `DAMAGE_PLAYERS`, `DAMAGE_ENTITIES`, `DAMAGE_EVERYTHING` | Dégâts des explosions dans une colonie, indépendamment de la protection (`ColonyPermissionEventHandler.java:279-324`) | ACTUEL (protection) |
| `permissioneventbypassminpermlevel` | 2 | 0-4 | Niveau d'opérateur qui ignore les permissions, **en créatif** (`Permissions.java:700`) | ACTUEL (protection) |

### 1.7 Serveur, sections `compatibility`, `pathfinding`, `requestSystem` (SC:190-203)

| Clé | Défaut | Bornes | Effet | Statut |
|---|---|---|---|---|
| `auditcraftingtags` | false | - | CSV d'audit des recettes | futur (artisanat) |
| `debuginventories` | false | - | Débogage des inventaires | n/a |
| `blueprintbuildmode` | false | - | Monde réservé à la création de plans | n/a |
| `minimumrailstopath` | 8 | 5-100 | Rails consécutifs pour que les citoyens les prennent | futur |
| `pathNodeLimitMultiplier` | 1 | 1-4 | Multiplie la limite de nœuds du pathfinding MC (`AbstractPathJob.java:208`) | n/a (HyColony utilise la navigation Hytale) |
| `creativeresolve` | false | - | Le résolveur joueur se résout tout seul en créatif (`StandardPlayerRequestResolver.java:109`), outil de débogage | ACTUEL (requêtes) |

### 1.8 Client, section `gameplay` (CC:25-32)

| Clé | Défaut | Bornes | Effet | Statut |
|---|---|---|---|---|
| `enablecitizenvoices` | true | - | Voix des citoyens | futur (sons) |
| `neighborbuildingrendering` | true | - | Afficher les bâtiments voisins pendant la pose d'un plan | futur (outil de construction) |
| `neighborbuildingrange` | 4 | -2-16 | Distance de voisinage, en blocs | futur |
| `buildgogglerange` | 50 | 1-250 | Distance (blocs) à laquelle les lunettes montrent les ordres (`ColonyBlueprintRenderer.java:458`, comparé au carré) | ACTUEL (lunettes) |
| `colonyteamborders` | true | - | Couleur d'équipe pour les frontières | futur (frontières) |
| `holidayfeatures` | true | - | Contenu de fêtes | futur |
| `showdyetooltips` | true | - | Infobulle des cuirs teints | n/a |

La section client `pathfinding` est vide (CC:34).

### 1.9 Commun (CoC:17-24)

| Clé | Section | Défaut | Effet | Statut |
|---|---|---|---|---|
| `generatesupplyloot` | gameplay | true | Camps/navires dans les coffres de butin | futur |
| `enabledebuglogging` | requestsystem | false | Journal de débogage du système de requêtes | ACTUEL (requêtes) |

**Bilan : 18 options touchent les systèmes actuels**, 47 sont futures ou sans objet.

## 2. Ce que HyColony a déjà

`plugin:HyColonyConfig.java` (codec du fichier `mods/<group>_HyColony/config.json`) et `core:kernel/config/ColonyConfig.java` (record, bornes rappliquées dans le constructeur compact) :

| Clé HyColony (plate) | Défaut | Bornes | Origine | Utilisée ? |
|---|---|---|---|---|
| `InitialCitizenAmount` | 4 | 1-10 | MC `initialcitizenamount` | oui, `core:citizen/CitizenManager.java:100,139` |
| `MaxCitizenPerColony` | 250 | 25-500 | MC `maxcitizenpercolony` | **non** : aucun lecteur dans `core/` ni `plugin/` (le logement n'est pas porté) |
| `InitialColonySize` | 4 | 1-15 | MC `initialColonySize` | oui, `core:app/action/HutActions.java:68`, `core:app/ColonyManager.java:166` |
| `MinColonyDistance` | 8 | 1-200 | MC `minColonyDistance` | oui, `HutActions.java:68` |
| `MaxColonySize` | 20 | 1-250 | MC `maxColonySize` | oui, `core:colony/Colony.java:158`, `core:app/ColonyPersistence.java:108` |
| `EnableColonyProtection` | true | - | MC `enablecolonyprotection` | oui, `core:app/ColonyProtection.enabled` |
| `AutosaveIntervalMinutes` | 5 | 1-60 | **ajout HyColony** (MC sauvegarde avec le monde Minecraft) | oui, `plugin:WorldRuntime.java:98` |
| `BuilderInfiniteResources` | false | - | voir remarque | oui, `core:construction/workorder/WorkManager.java:118` |
| `CreativeOperatorFreeBuilds` | true | - | **ajout HyColony**, écart documenté (`WorkManager.java:110-114`) | oui, `WorkManager.java:119` |
| `CutterCraftSeconds` (section `HyDomum` de la config **de HyDomum** depuis la séparation en mods) | 0.5 | 0-10 | **ajout HyDomum** : durée d'une fabrication à l'établi de l'architecte (Domum Ornamentum fabrique d'un coup ; 0 retrouve ce comportement) | oui, `domum/plugin:cutter/CutterCraftQueue.java` (lu par `HyDomumConfig`) |

Remarque sur `BuilderInfiniteResources` : sur `version/main`, ce n'est **pas** une option de configuration MC. C'est un champ statique non final `Constants.BUILDER_INF_RESOURECES = false` (`api/util/constant/Constants.java:189`), lu par `AbstractEntityAIStructureWithWorkOrder.java:198` et `BuildingStructureHandler.java:290`, et jamais affecté ailleurs dans `src/`. La Javadoc HyColony qui dit « MC builderInfiniteResources » (`ColonyConfig.java:12`, `WorkManager.java:110`) désigne une ancienne option ; l'exposer est donc un écart à signaler (`Deviation from MC:`).

## 3. Options pertinentes : état et proposition

Les clés Hytale doivent commencer par une majuscule (`codec/KeyedCodec.java:32` lève `IllegalArgumentException` sinon) : on garde le nom MC en PascalCase. Bornes = celles de MC.

| MC (section, clé, défaut) | HyColony aujourd'hui | Clé proposée | Bornes |
|---|---|---|---|
| gameplay `initialcitizenamount` = 4 | config plate `InitialCitizenAmount` = 4 | `Gameplay.InitialCitizenAmount` | 1-10 |
| gameplay `maxcitizenpercolony` = 250 | config plate, **non lue** | `Gameplay.MaxCitizenPerColony` | 25-500 |
| gameplay `alwaysrendernametag` = true | **absente** ; le nom est toujours affiché (`core:colony/CitizenNameplates.java:21`, `nameFor`) | `Gameplay.AlwaysRenderNameTag` | bool. API Hytale pour masquer le nom d'un PNJ non vérifiée **[in-game]** |
| gameplay `allowotherdimcolonies` = true | **absente** ; chaque monde reçoit un runtime (`plugin:WorldRuntimes.java:33`) | `Gameplay.AllowOtherDimColonies` | bool. Équivalent Hytale de « Overworld » (monde par défaut) à définir |
| claims `maxColonySize` = 20 | config plate | `Claims.MaxColonySize` | 1-250 |
| claims `minColonyDistance` = 8 | config plate | `Claims.MinColonyDistance` | 1-200 |
| claims `initialColonySize` = 4 | config plate | `Claims.InitialColonySize` | 1-15 |
| claims `maxdistancefromworldspawn` = 30000 | **absente** (aucune vérification de spawn ; le contrôle irait à côté de `HutActions.java:68`) | `Claims.MaxDistanceFromWorldSpawn` | 1000-2147483647 |
| claims `mindistancefromworldspawn` = 0 | **absente** | `Claims.MinDistanceFromWorldSpawn` | 0-1000 |
| permissions `enablecolonyprotection` = true | config plate | `Permissions.EnableColonyProtection` | bool |
| permissions `turnoffexplosionsincolonies` = `DAMAGE_ENTITIES` | **absente** ; aucune gestion des explosions | `Permissions.TurnOffExplosionsInColonies` | enum (4 valeurs MC) ; événement d'explosion Hytale non vérifié |
| permissions `permissioneventbypassminpermlevel` = 2 | **absente** ; aucun contournement opérateur (`plugin:block/ProtectionSystems.java:40`) | `Permissions.PermissionEventBypassMinPermLevel` | 0-4. Hytale n'a pas de niveaux d'opérateur : proposer 0 = tout joueur en créatif, 1-4 = opérateur en créatif (`PlayerDirectory.isCreativeOperator`), avec `Deviation from MC:` |
| commands `canplayeruseshowcolonyinfocommand` = true | en dur : `info` ouvert aux joueurs (`plugin:command/HyColonyCommand.java:73`) | `Commands.CanPlayerUseShowColonyInfoCommand` | bool |
| commands `canplayeruseaddofficercommand` = true | en dur : `rank` ouvert aux joueurs (`HyColonyCommand.java:113`) | `Commands.CanPlayerUseAddOfficerCommand` | bool |
| commands `canplayerusedeletecolonycommand` = false | en dur : `delete` réservé aux opérateurs (`HyColonyCommand.java:155`) | `Commands.CanPlayerUseDeleteColonyCommand` | bool |
| requestSystem `creativeresolve` = false | **absente** (`core:request/resolver/PlayerResolver.java`) | `RequestSystem.CreativeResolve` | bool |
| requestsystem (commun) `enabledebuglogging` = false | **absente** | `RequestSystem.EnableDebugLogging` | bool |
| client `buildgogglerange` = 50 | **en dur** : `core:app/goggles/GogglesView.java:31`, `RANGE_SQ = 50L * 50L` | `Client.BuildGoggleRange` | 1-250. Écart : option client chez MC, réglage serveur chez nous (pas de config cliente pour un plugin Hytale) |

Les trois commandes gardent aujourd'hui les défauts de MC ; les exposer ne change rien tant que l'opérateur ne touche pas au fichier.

Les constantes suivantes ressemblent à des options mais **ne correspondent à aucune option MC** : `WorkManager.MAX_DISTANCE_SQ` (100², MC `WorkOrderBuilding.java:30`), les délais du `StuckHandler`, les délais du bâtisseur. Elles restent des constantes (§ 4).

## 4. Constantes que MC code en dur aussi (à ne pas exposer)

| HyColony | Valeur | Source MC (en dur) |
|---|---|---|
| `core:construction/builder/BuilderTimings.java:5` `BUILD_BLOCK_DELAY` | 15 | `AbstractEntityAIStructure.java:79` (ancienne option `builderbuildblockdelay`, supprimée) |
| `BuilderTimings.java:7` `BLOCK_MINING_DELAY` | 500 | `AbstractEntityAIInteract.java:100` (ancienne option `blockminingdelaymodifier`) |
| `BuilderTimings.java:6` `PROGRESS_MULTIPLIER` | 10 | `CitizenConstants.java:261` |
| `BuilderTimings.java:8` `LEVEL_MODIFIER` | 0.85 | `AbstractEntityAIInteract.java:54` |
| `core:construction/workorder/WorkManager.java:30` `MAX_DISTANCE_SQ` | 100² | `WorkOrderBuilding.java:30` |
| `core:request/resolver/RetryingResolver.java:27` `MAX_TRIES`, `DELAY_UPDATES` | 3, 1200 (mises à jour de 11 ticks) | `StandardRetryingRequestResolver.java:31-32` (anciennes options `maximalretries`, `delaybetweenretries`) |
| `core:citizen/CitizenManager.java:19-20` `INITIAL_SPAWN_FIRST`, `INITIAL_SPAWN_RESET` | 30 s, 60 s | `CitizenManager.java:95,600` (ancienne option `citizenrespawninterval`) |
| `core:citizen/Experience.java:5` `EXPERIENCE_MULTIPLIER` | 1 | `ExperienceUtils.java:11` |
| `core:citizen/CitizenData.java:11` `MAX_SATURATION` | 60 | `ICitizenData.java:30` |
| `core:citizen/Skills.java:9` `MAX_CITIZEN_LEVEL` | 99 | `CitizenConstants.java:181` |
| `Skills.java:10`, `core:construction/shared/BuilderHut.java:14` niveau max | 5 | `Constants.java:20`, `BuildingConstants.java:11` |
| `core:kernel/nav/StuckHandler.java:41,42,44` `MIN_TARGET_DIST`, `MIN_TP_DELAY`, `MIN_DIST_FOR_TP` | 3, 2400, 10 | `PathingStuckHandler.java:40,45,46` |
| `core:kernel/ai/TickingTransition.java:9` `MAX_TICKRATE_VARIANT` | 50 | `TickRateConstants.java:21` |
| borne haute de `MaxCitizenPerColony` | 500 | `CitizenConstants.CITIZEN_LIMIT_MAX` |

Constantes propres à Hytale (pas d'équivalent MC, ne pas exposer non plus) : `plugin:ColonyTickSystem.java:15,17`, `plugin:adapter/HytaleCitizenBodies.java:46,48`, `plugin:adapter/HytaleGameClock.java:10`, `plugin:adapter/HytaleItemCatalog.java:47-48`.

Vérifié (clone MC `6b3916a1`) : ce ne sont pas des écarts. `BuilderStock.ACTIONS_UNTIL_DUMP = 4096` est la constante propre du bâtisseur, `EntityAIStructureBuilder.java:50` (le 32 de `CitizenConstants.java:166` vaut pour les autres métiers, `AbstractEntityAIBasic.java:427`). `TickingTransition.MAX_AI_TICKRATE = 12000` est `TickRateConstants.MAX_AI_TICKRATE = 20*60*10` (`MAX_TICKRATE = 500` est le tick lent de la colonie, une autre constante).

## 5. Disposition proposée de `config.json`

```json
{
  "Gameplay": {
    "InitialCitizenAmount": 4,
    "MaxCitizenPerColony": 250,
    "AlwaysRenderNameTag": true,
    "AllowOtherDimColonies": true
  },
  "Claims": {
    "MaxColonySize": 20,
    "MinColonyDistance": 8,
    "InitialColonySize": 4,
    "MaxDistanceFromWorldSpawn": 30000,
    "MinDistanceFromWorldSpawn": 0
  },
  "Permissions": {
    "EnableColonyProtection": true,
    "TurnOffExplosionsInColonies": "DAMAGE_ENTITIES",
    "PermissionEventBypassMinPermLevel": 2
  },
  "Commands": {
    "CanPlayerUseShowColonyInfoCommand": true,
    "CanPlayerUseAddOfficerCommand": true,
    "CanPlayerUseDeleteColonyCommand": false
  },
  "RequestSystem": {
    "CreativeResolve": false,
    "EnableDebugLogging": false
  },
  "Client": {
    "BuildGoggleRange": 50
  },
  "HyColony": {
    "AutosaveIntervalMinutes": 5,
    "BuilderInfiniteResources": false,
    "CreativeOperatorFreeBuilds": true
  }
}
```

Les sections reprennent les catégories MC (`gameplay`, `claims`, `permissions`, `commands`, `requestSystem`, client `gameplay` → `Client`). Nos ajouts vont dans `HyColony`, pour ne pas les confondre avec les options MC. Le mod HyDomum a sa propre config (`mods/HyColony_hydomum/config.json`), avec une section `HyDomum` : `{"HyDomum": {"CutterCraftSeconds": 0.5}}`. Les futures options (raids, recherche…) iront dans `Combat`, `Research`, `Pathfinding`, `Compatibility` le moment venu.

### Comportement du chargeur Hytale (vérifié dans les sources)

- `server/core/util/Config.java:80-82` : pas de fichier → `codec.getDefaultValue()`. Sinon `RawJsonReader.readSync` (`codec/util/RawJsonReader.java:1528`).
- `codec/builder/BuilderCodec.java:334-339` : l'objet est créé par le fournisseur (valeurs par défaut des champs), puis seules les clés présentes sont écrites. **Une clé absente garde son défaut.**
- `BuilderCodec.java:383-386,427-432` : une clé inconnue est enregistrée dans `ExtraInfo` puis sautée, sans échec du chargement.
- Une section est un `KeyedCodec` vers un autre `BuilderCodec` (modèle vanilla : `builtin/adventure/camera/asset/camerashake/CameraShake.java:25`). Une section partielle est complétée par les défauts de son propre fournisseur.
- `codec/builder/BuilderField.java:147-149` : à l'encodage, un champ dont le getter renvoie `null` n'est pas écrit.
- `plugin:HyColonyPlugin.java:39` appelle `config.save()` au `setup()`. Le fichier est donc réécrit à chaque démarrage : les nouvelles clés y apparaissent avec leur défaut, et **les clés inconnues disparaissent**.

### Règle de migration

1. Toute nouvelle clé a un défaut égal au défaut MC dans le champ Java ; un ancien fichier sans la clé fonctionne donc tel quel.
2. Passage des clés plates actuelles aux sections : sans précaution, le `save()` du démarrage effacerait les valeurs réglées par l'opérateur (clés plates devenues inconnues). On garde donc les 9 clés plates actuelles dans le codec racine comme **alias de lecture** : leur setter écrit dans le champ de la section, leur getter renvoie `null` (donc non réécrites, `BuilderField.java:149`). Au premier démarrage, la valeur plate passe dans la section et le fichier est réécrit au nouveau format.
3. Si un fichier contient à la fois la clé plate et la section, l'ordre de lecture décide (le setter de section remplace l'objet entier). Cela n'arrive pas avec un fichier produit par le plugin ; on le documente sans le gérer.
4. Les bornes restent appliquées au passage vers le cœur (`toCore()` et le constructeur compact de `ColonyConfig`), jamais au décodage : une valeur hors bornes est ramenée dans les bornes, sans échec.
5. Une valeur d'enum inconnue (`TurnOffExplosionsInColonies`) doit prendre le défaut MC. Le comportement d'un codec d'enum Hytale sur une valeur inconnue n'a pas été vérifié : proposition, décoder une `Codec.STRING` et la convertir soi-même avec repli.

Tout ce comportement est vérifié dans les sources seulement **[in-game]** : à confirmer avec un vieux `config.json` plat au redémarrage.

## 6. État après la restructuration (2026-09-26)

`config.json` suit la disposition du § 5, sauf `RequestSystem` et les clés non branchées (`AlwaysRenderNameTag`, `AllowOtherDimColonies`) : aucune n'est encore lue, elles viendront avec leur système. Code : `plugin:config/` (une classe par section) et `core:kernel/config/ColonyConfig.java` (un record par section).

| Option | Branchement |
|---|---|
| `Client.BuildGoggleRange` | `core:app/goggles/GogglesView.java` (remplace `RANGE_SQ`) |
| `Gameplay.MaxCitizenPerColony` | **non lue**, conforme à MC : MC ne plafonne que l'immigration et les naissances (`CitizenManager.spawnCitizenOnPosition`, `force = false`) ; l'apparition initiale, seule apparition de HyColony, passe `force = true` (`CitizenManager.java:630`) et est bornée par `InitialCitizenAmount` (≤ 10 < 25) |
| `Permissions.PermissionEventBypassMinPermLevel` | `core:app/ColonyProtection.isAllowed` (protection) ; 0 = tout joueur en créatif, 1-4 = opérateur en créatif |
| `Permissions.TurnOffExplosionsInColonies` | `core:app/ColonyProtection.explosionSparesBlock` et `plugin:block/ExplosionProtectionSystem` ; blocs seulement (voir `plugin-b-api.md` § 13) |
| `Claims.Min/MaxDistanceFromWorldSpawn` | `core:app/action/HutActions.spawnDistanceRefusal` |
| `Commands.*` | `plugin:command/HyColonyCommand` (groupe de permission) ; `delete` passe par `ColonyAdministration.delete` (opérateur ou gestionnaire de la colonie) |
