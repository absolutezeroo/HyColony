# Outil de débogage pour HyColony : recherche (2026-09-29)

Question : faut-il un outil (ou un mod) de débogage pour trouver les bugs et les incohérences de HyColony ?
- Quoi vérifier, et quoi **montrer** en jeu, le plus visuellement possible ?
- Peut-on suivre un citoyen en temps réel, avec une caméra qui « entre » dans le PNJ ?
- Peut-on l'envoyer à un endroit choisi, sur la carte ou ailleurs ?

Ce document liste **tout** ce qui est possible, pour que l'utilisateur choisisse dans un menu (§ 7).

Deux bugs récents servent de mesure :
- **A. Toit** : un livreur marche jusque sur le toit d'une hutte et « dépose » à travers. `BodyWalker.walkTo` tient pour arrivée toute fin de navigation (`ARRIVED`, `BLOCKED`, `FAILED`) et tout abandon de `StuckHandler`, sans regarder la distance à la cible (`core/.../kernel/nav/BodyWalker.java:83-87, 99-101`).
- **B. Livreur bloqué** : un livreur reste en `WORKING` pour toujours, sans rien à livrer.

Sources :
- MineColonies (MC) : `version/main`, fichiers bruts du 2026-09-29, notés `MC:`. La mention « clone » renvoie au clone du 23/09/2026 (commit `6b3916a1`).
- Hytale : sources décompilées 0.7.0-pre.4, chemins relatifs à `build/vineflower/hytale-server/com/hypixel/hytale/`. Assets : `pre-release-0.7.0-pre.4-Assets.zip`, noté `zip:`. Fichiers du client : `%APPDATA%/Hytale/install/pre-release/package/game/latest/Client/Data/`, noté `client:`.

Marques :
- **vérifié** : lu dans les sources ;
- **[in-game]** : le code le permet, mais le résultat en jeu reste à voir ;
- **impossible** : avec la preuve.

## 1. Ce que font les meilleurs outils ailleurs

| Outil | Ce qu'il offre | À reprendre |
|---|---|---|
| **MineColonies** (§ 2) | Mode débogage par joueur, onglet Debug du citoyen (historique de l'IA, suivi du pathfinding), `/mc citizens info/walk/trackPath`, `requestsystem-reset`, journal du système de requêtes | Tout : c'est la référence du portage |
| **RimWorld**, mode dev (https://rimworldwiki.com/wiki/Development_mode) | Inspecteur en direct, menu « debug actions » avec recherche (faire apparaître, blesser, téléporter, régler les besoins), dessin des chemins, des régions, de l'accessibilité et des « duties » des pions, god mode, ×4 et pas à pas | Menu d'actions, dessins de chemin, pas à pas |
| **Unreal Gameplay Debugger** (https://dev.epicgames.com/documentation/en-us/unreal-engine/using-the-gameplay-debugger-in-unreal-engine) | On vise un agent (touche `'`). Catégories Navmesh, Basic, Behavior Tree, EQS, Perception, activées au pavé numérique. Données collectées côté serveur, affichées par-dessus la vue ; seules les catégories actives sont répliquées | Viser pour choisir, couches activables, calcul serveur puis affichage |
| **Unreal Visual Logger** (https://dev.epicgames.com/documentation/en-us/unreal-engine/visual-logger-in-unreal-engine) | Enregistre par acteur des instantanés d'état, du texte et des formes (segments, sphères, boîtes…). Une frise se parcourt en avant et en arrière, pour les bugs « rares ou difficiles à reproduire » | Historique enregistré et relisible |
| **Unity AI Navigation** (https://docs.unity3d.com/Packages/com.unity.ai.navigation@2.0/manual/NavigationOverlay.html) | Superposition du navmesh, des agents sélectionnés et de leur chemin | Chemin et cible de l'agent choisi |
| **Factorio**, F4/F5 (https://wiki.factorio.com/Debug_mode) | `show-paths`, `show-target`, `show-next-waypoint-bb`, `show-unit-group-info`, `show-active-state`… Deux jeux de réglages (« always », « debug ») | Couches nommées, cible en rouge |
| **Oxygen Not Included** (https://oxygennotincluded.wiki.gg/wiki/Debug_Commands) | Mode debug (Retour arrière), construction instantanée, peinture de matière | Actions « god mode » pour préparer un cas |
| **Minecraft** vanilla | Paquets de débogage (chemin, cerveau, sélecteur de buts, POI) dont les rendus sont cachés, et que des mods réactivent (https://modrinth.com/mod/mojangdevtools, F3+F6) | Cerveau et chemin au-dessus du mob |
| **Carpet**, **spark** | `/log` en direct (Carpet, par mixins) ; profileur de la JVM (spark, séparé, générique) | spark tel quel pour la performance |
| **Hytale : Alec's NPC Debug UI** (https://github.com/Alechilles/AlecsNpcDebugInspector) | Objet ou `/npcdebug` sur le PNJ visé. Page d'inspection repliable (IA et état, capteurs, pathing, minuteries, composants), liste des PNJ liés, page des drapeaux de débogage, **surimpression épinglée** qui reste affichée en jouant, rafraîchissement de 150 à 2000 ms | La preuve qu'une page et un HUD en direct marchent sur Hytale (idées seulement : licence non lue) |
| **Hytale : Lait's Entity Inspector** (https://www.curseforge.com/hytale/mods/laits-entity-inspector) | Interface **web** : entités et composants en direct, arbre d'instructions d'un PNJ avec le journal de chaque capteur, caméra cinématique sans collision. « local development servers only » | Journal des décisions |
| **Dwarf Fortress / DFHack** (https://docs.dfhack.org/en/stable/docs/Tools.html) | `gui/unit-info-viewer`, `jobutils`, `pathable` (où l'on peut aller), `troubleshoot-item`. Seuls les noms ont été lus | Explication d'un travail ou d'un objet inaccessible |

Ce qui revient partout :
1. on **désigne** un agent en le regardant ;
2. on affiche des **couches** au choix, par-dessus le monde ;
3. un **inspecteur** en direct est épinglé à l'écran ;
4. un **historique** se relit ;
5. un **menu d'actions** permet de provoquer une situation ;
6. on **contrôle le temps** (pause, pas à pas, accéléré).

## 2. MineColonies en détail

Tout le débogage est **dans le mod** (`core/debug/`). Le wiki le dit : « should only be used when debugging » (https://minecolonies.com/wiki/misc/command/).

**Mode débogage**
- `/mc toggleDebugging <joueur>`, opérateurs (`MC:core/debug/command/CommandToggleDebug.java:23-73`).
- `hasDebugEnabled` vaut **toujours vrai hors production** (`core/debug/DebugPlayerManager.java:27-30`).

**Onglet Debug** du citoyen (clone `AbstractWindowCitizen.java:61-66`)
- Bouton « query AI History » : l'historique de `CitizenAI` et celui de l'IA du métier (`QueryCitizenAIHistoryMessage.java:51-76`).
- Bouton « enable Pathfinding tracking » (`DebugEnablePathfindingMessage.java:56-78`).
- Chaque transition est notée avec l'heure et l'état (clone `BasicStateMachine.java:232-238, 281-310`). L'historique s'active quand un joueur en mode débogage interagit avec le citoyen (clone `EntityCitizen.java:407-413`).

**`/mc citizens …`** (`EntryPoint.java:71-81`)
- `info` : position, santé, maison, travail, historique et **niveau d'anti-blocage** (`CommandCitizenInfo.java:40-126`).
- `walk <pos>` (`CommandCitizenTriggerWalkTo.java:69-110`). Pour un ouvrier :
  - ajoute à `CitizenAI` une transition unique qui le fait marcher vers la position, à 4 blocs près, pendant **3 minutes** au plus (`20 * 60 * 3` ticks) ;
  - puis met la navigation en pause 100 ticks.
  - Sans métier, c'est un simple `moveTo`.
- `trackPath`, `trackPathType` : rendu client des nœuds A* visités (rouge), non visités (bleu) et du chemin (jaune ou orange), avec coût et heuristique (`PathfindingDebugRenderer.java:44-147`).
- `reload`, `list`, `teleport`, `kill`, `spawnNew`, `modify`.

**`/mc colony …`** : `requestsystem-reset <id>` (`colony.getRequestManager().reset()`, `CommandRSReset.java:33`), `-all`, `info`, `printStats`.

**Configuration**
- `enabledebuglogging` : journal `minecolonies.requestsystem.<id>` (clone `StandardRequestManager.java:120-127, 583-585`).
- `debuginventories` : vérifie qu'aucun objet n'est perdu ou dupliqué lors d'un transfert entre joueur et hutte (clone `TransferItemsRequestMessage.java:111, 169-172`).
- `researchdebuglog` : journal des recherches.

**BlockUI** : Ctrl+Maj+Alt dessine le contour et l'id de chaque élément de fenêtre (`BlockUI:views/BOWindow.java:114`, `Pane.java:359-369`). **Structurize** : aucun fichier « debug ».

## 3. Hytale 0.7.0-pre.4 : inventaire des moyens visuels et interactifs

| Moyen | API (source) | Pour un seul joueur ? | Statut |
|---|---|---|---|
| Formes 3D : ligne, sphère, cube, cylindre, cône, flèche, disque | `DisplayDebug(shape, matrix, color, time, flags, frustum, opacity)` (`protocol/packets/player/DisplayDebug.java:52-68`) écrit sur `playerRef.getPacketHandler()` ; `DebugUtils` l'envoie à **tout** le monde (`server/core/modules/debug/DebugUtils.java:98-113`). Modèle vanilla pour un seul joueur, rafraîchi chaque seconde : `builtin/adventure/wilderness/debug/WildernessDebugShapeSystem.java:51-84` | oui | vérifié ; vu en jeu, **caché par les blocs** (`plugin-b-api.md` § 29) |
| Débogage PNJ intégré | `/npc debug set VisPath Pathfinder DisplayCustom …` (`server/npc/commands/NPCDebugCommand.java:36-44`) ; `NPCEntity.setRoleDebugFlags` (`server/npc/entities/NPCEntity.java:482-490`) ; `DebugSupport.setDisplayCustomString` (`server/npc/role/support/DebugSupport.java:108-117`) | non (formes pour tous, `RoleSystems.java:810-870`) | vérifié ; marche sur nos citoyens (rôle `Seek` avec A*, `NPCPlugin.java:902`) |
| Journal A* en ASCII | clé `"Debug": "Path,Maps,Status,Profile,Motion"` du mouvement `Seek`, dans l'asset du rôle (`corecomponents/movement/builders/BuilderBodyMotionFindBase.java:224-236`, drapeaux dans `BodyMotionFindBase.java:851-860`, dessin dans `navigation/AStarDebugBase.java`) | journal | vérifié ; il faudrait un rôle « debug » à part |
| Particules | `ParticleUtil.spawnParticleEffect(name, position, List<Ref> playerRefs, accessor)` (`server/core/universe/world/ParticleUtil.java:74`) | oui | vérifié |
| Texte au-dessus d'une entité | `Nameplate.setText` (`server/core/entity/nameplate/Nameplate.java:88`) | non | vérifié ; en conflit avec nos noms (§ 3.4) |
| Lueur d'une entité | `EntityEffect` à `ModelVFX` (`HighlightColor`…) (`plugin-b-api.md` § 29) | non | vu en jeu : **ne traverse pas les murs** |
| Visibilité par joueur | filtre dans `EntityTrackerSystems.FIND_VISIBLE_ENTITIES_GROUP` (`server/core/modules/entity/tracker/EntityTrackerSystems.java:173`), modèle `HideEntitySystems` (`build-goggles-and-wand.md` B.1) | oui | vérifié |
| Aperçu de blocs, entité-bloc | `PrefabPreview`, `BlockEntity` (`build-goggles-and-wand.md`, `plugin/ui/highlight/GlowingBlock`) | oui, avec le filtre | vérifié ; déjà utilisé |
| HUD | `Player.getHudManager().addCustomHud(playerRef, hud)` et `removeCustomHud` (`server/core/entity/entities/player/hud/HudManager.java:129-162`). `CustomUIHud.build`, puis `update(clear, builder)` envoie un paquet `CustomHud` (`CustomUIHud.java:57-80`). Exemple vanilla : `SpectatingHud` (`modules/entity/spectator/SpectatingHud.java`) | oui | vérifié ; mise à jour périodique **[in-game]** (Alec's le fait de 150 à 2000 ms) |
| Page (menu) | `InteractiveCustomUIPage`, rafraîchie en direct par `LiveWindows` (`plugin/.../adapter/LiveWindows.java`) | oui | vérifié ; déjà utilisé |
| Chat | `Message`, avec seulement `link(url)` (`server/core/Message.java:326`) | oui | vérifié ; **aucun clic qui lance une commande** trouvé |
| Notification, titre, son | `NotificationUtil.sendNotification(PacketHandler, …)` (`server/core/util/NotificationUtil.java:158-194`) ; `EventTitleUtil.showEventTitleToPlayer` (`server/core/util/EventTitleUtil.java:219-241`) ; `SoundUtil.playSoundEvent2dToPlayer` / `3dToPlayer` (`server/core/universe/world/SoundUtil.java:106-118, 434-473`) | oui | vérifié |
| Caméra | § 3.1 | oui | vérifié, détails **[in-game]** |
| Carte du monde | § 3.2 | oui | vérifié, détails **[in-game]** |
| Désigner ce qu'on vise | `TargetUtil.getTargetEntity(ref, rayon, accessor)` (`server/core/util/TargetUtil.java:646-651`) ; `PlayerMouseButtonEvent.getTargetEntityRef()` / `getTargetBlock()` (`server/core/event/events/player/PlayerMouseButtonEvent.java:85-105`, émis par `modules/interaction/InteractionModule.java:470-490`) | oui | vérifié ; le clic au curseur est **[in-game]** |
| Temps | § 3.3 | monde entier | vérifié |
| Intercepter un paquet du client | `PacketAdapters.registerInbound(PlayerPacketFilter)`, où `true` avale le paquet (`server/core/io/adapter/PacketAdapters.java:45-50, 98-114`). Appelé sur le **thread réseau** (`server/core/io/netty/PlayerChannelHandler.java:27-33`) : il faut repasser par `world.execute` | oui | vérifié |
| Journal | `/log <journal> <niveau> [--save]` (`server/core/command/commands/debug/LogCommand.java:54-110`) | console | vérifié ; nom des journaux du cœur **[in-game]** |

### 3.1 Caméra : « entrer dans le PNJ »

**Le vanilla le fait déjà sur un PNJ**
- `/spectate target` vise **n'importe quelle entité** à 32 blocs, par `TargetUtil.getTargetEntity(ref, 32, true, store)` (`server/core/command/commands/player/SpectateCommand.java:226-250`). Permission `spectate.watch`, groupe `hytale:Builder` (l. 230-231).
- **Test sans code, dès maintenant** : viser un citoyen et taper `/spectate target`, puis `/spectate exit` **[in-game]**.

**Le mécanisme**
- `GameModeTypes.enter(ref, store, "Spectator")`, puis `putComponent(Spectating(targetRef))` (`SpectateCommand.java:86-107`).
- `OnSpectatingChange.applyFollowCamera` (`modules/entity/spectator/SpectatorSystems.java:262-296`) :
  - téléporte le joueur sur la cible ;
  - envoie `SetServerCamera(Custom, verrouillée, réglages)`, avec `attachedToType = EntityId`, `attachedToEntityId` = le `NetworkId` de la cible, `followAttachedEntity`, `eyeOffset`, `isFirstPerson = false`, une distance de 4 avec rayon anti-mur, et la rotation de la cible.
  - Seul le `NetworkId` est exigé : **aucune vérification « joueur »**. Le test « joueur caché » ne concerne que les joueurs (l. 103-106).
- `FollowTarget` (l. 70-127) :
  - retéléporte le corps du spectateur quand il est à plus de 32 blocs, ce qui garde la cible chargée pour son client ;
  - le détache si la cible meurt ou disparaît.
- `HideFromNonSpectators` (l. 138-199) rend le spectateur **invisible** aux autres joueurs.
- Asset `zip:Server/Entity/GameMode/Spectator.json` : `Flying`, `NoClip`, `Invulnerable`, `Intangible`, `PreventInventoryAccess`, `OverrideAllInteractions`, `LockedCameraView: FirstPerson`, effet translucide, HUD réduit. Variante `SpectatorInteractive`, qui garde la barre d'objets et les interactions.
- `PreventInventoryAccess` ne bloque que les paquets d'inventaire (`server/core/io/handlers/game/InventoryPacketHandler.java:95`). Une page ouverte par commande devrait s'afficher **[in-game]**.
- Sortie : `/spectate exit` → `GameModeTypes.exit`, qui remet la caméra. Le joueur est reposé derrière la cible (`SpectatorSystems.java:248-260, 315-346`).

**Changer de cible**
- `SpectateControl Next/Prev` ne fait défiler que les **joueurs** (`world.getPlayerRefs()`, `SpectateControlInteraction.java:82-86`). Pour passer d'un citoyen à l'autre, il faut notre propre interaction ou un bouton de page.
- `Detach` passe en caméra libre (l. 61).

**Notre mode caméra**
- Un asset `Server/Entity/GameMode/HyColony_DebugWatch.json` dans notre pack (chemin `Entity/GameMode`, `server/core/asset/AssetRegistryLoader.java:726`), avec :
  - `Parent: Spectator` ;
  - `InteractionOverrides` : clic gauche = citoyen suivant, clic droit = ouvrir le menu (`OpenCustomUI`), capacité 1 = `SpectateControl Detach`. C'est le modèle de `zip:HardcoreSpectator.json`.
- **[in-game]** : chargement de ce type depuis un pack de plugin.

**Réglages de la caméra** (`protocol/ServerCameraSettings.java:19-72`)
- **Troisième personne en orbite** : c'est exactement `/spectate`. **vérifié**
- **Première personne « dans les yeux »** : `isFirstPerson = true`, `eyeOffset`, `positionOffset`, `rotationType = AttachedToPlusOffset`, `hideHeldItem`, `baseFov`, `depthOfField`, `allowPitchControls`.
  - Aucun exemple vanilla d'une première personne attachée à une autre entité.
  - **[in-game]** : le client montre peut-être l'intérieur de la tête du PNJ. Si c'est le cas, décaler vers l'avant avec `positionOffset`.
- **Vue du dessus avec curseur** : `displayCursor`, `mouseInputType` (`LookAtTarget`, `LookAtTargetBlock`, `LookAtTargetEntity`, `LookAtPlane`), `sendMouseMotion`, comme `/camera topdown` (`command/commands/player/camera/PlayerCameraTopdownCommand.java:57-69`).
  - `CameraDemo` y lit les clics par `PlayerMouseButtonEvent` : bouton, bloc visé, entité visée (`CameraDemo.java:67-120, 157-169`).
  - On pourrait donc **cliquer un citoyen pour le choisir**, ou cliquer un bloc pour l'y envoyer **[in-game]** (quand le client envoie ces clics : `build-goggles-and-wand.md` B.3).
- Le type Spectator ajoute sa propre étiquette de HUD, `SpectatingHud` (`gamemode/GameModeTypeState.java:96`). Notre HUD doit prendre une autre clé.

### 3.2 Carte du monde : envoyer un citoyen quelque part

MC le fait par commande : `/mc citizens walk <pos>` (§ 2).

**Ce que le client envoie depuis la carte** (enregistrement dans `server/core/io/handlers/game/GamePacketHandler.java:284-288`)

| Paquet | Contenu | Quand |
|---|---|---|
| `UpdateWorldMapVisible` (243) | `visible` | Carte ouverte ou fermée ; le gestionnaire ne fait rien |
| `TeleportToWorldMapPosition` (245) | `x`, `y`, qui sont le **X et le Z** du bloc, sans hauteur | Menu contextuel « Teleport » (`client:Shared/Language/en-US/client.lang`, `map.contextMenu.teleport`). Le vanilla téléporte à hauteur de carte + 2 (`GamePacketHandler.java:1041-1066`) |
| `CreateUserMarker` (246) | `x`, `z`, nom de 24 caractères au plus, icône, teinte, partagé | « Create Map Marker » |
| `TeleportToWorldMapMarker` (244), `RemoveMapMarker` (119) | id du repère | Repères |

- « Select Location » (`map.contextMenu.selectLocation`) n'envoie **aucun** paquet connu du serveur : c'est une action côté client.
- « Teleport » n'est proposé que si `allowTeleportToCoordinates` : permission `WORLD_MAP_COORDINATE_TELEPORT` et mode autre qu'Aventure (`server/core/universe/world/WorldMapTracker.java:586, 740-742`). C'est le cas d'un opérateur en Créatif.

**Envoyer ici depuis la carte**
- Un filtre `PacketAdapters` avale `TeleportToWorldMapPosition` **seulement quand le mode « envoyer ici » est armé** pour ce joueur. Il renvoie `true`, donc pas de téléportation.
- Puis, sur le thread du monde, le citoyen suivi marche vers `(x, sol, z)`.
- Hors de ce mode, le vanilla reste intact.
- **vérifié dans les sources, [in-game]**.
- La carte ne donne pas la hauteur. La colonne donne le **toit** : il faut notre propre recherche du sol, ou marcher vers le toit exprès pour reproduire le bug A.

**Solutions de repli**
- Le **dernier repère personnel** : aucun événement, mais les repères se relisent à tout moment par `player.getPlayerConfigData().getPerWorldData(world.getName()).getUserMapMarkers()` (`PlayerWorldData implements UserMapMarkersStore`, `worldmap/markers/user/UserMarkerValidator.java:32-36`). **vérifié**
- Le **bloc visé** (`TargetUtil`). **vérifié**
- Le clic au curseur en vue du dessus (§ 3.1). **[in-game]**
- Des coordonnées tapées dans la page. **vérifié**

**Nos repères sur la carte**
- `world.getWorldMapManager().addMarkerProvider(clé, fournisseur)` (`WorldMapManager.java:304`).
- `MarkerProvider.update(World, Player, MarkersCollector)` est appelé **par joueur** (l. 617-619). Seul l'opérateur verrait donc les citoyens, la cible suivie et les huttes. Modèle vanilla : `builtin/adventure/objectives/ObjectivePlugin.java:842`.
- `MapMarkerBuilder(id, image, transform).withContextMenuItem(new ContextMenuItem(nom, commande))` (`MapMarkerBuilder.java:24-74`, `protocol/packets/worldmap/ContextMenuItem.java:26`).
  - Un repère de citoyen pourrait porter « Suivre » → `/hycolony debug watch 12`.
  - **[in-game]** : aucun usage vanilla des menus contextuels, donc rien ne dit que le client exécute bien la commande.
- **[in-game]** : fréquence de mise à jour d'un repère qui bouge (les fournisseurs sont interrogés quand la boussole se met à jour, `plugin-b-api.md` § 29).

### 3.3 Temps : pause, pas à pas, accéléré

| Moyen | Source | Effet |
|---|---|---|
| Dilatation du temps, de 0,01 à 4 | `World.setTimeDilation` (`server/core/universe/world/World.java:491-496`) : le `dt` du monde est multiplié (l. 378-379), les clients sont prévenus. Commande `/time dilation` (`hytale:Admin`, `modules/time/commands/TimeCommand.java:112-130`) | Ralenti ou accéléré ×4 pour tout le monde. Notre `ColonyTickSystem` cumule `dt`, donc la colonie suit **[in-game]** |
| Pause du monde | `World.setPaused` (`World.java:658-664`) : le magasin d'entités passe en `pausedTick` (l. 384-394). Commande `/world pause` | Tout s'arrête, nos systèmes aussi **[in-game]** |
| Cadence | `World.setTps` (`World.java:458-469`), 30 par défaut (`WorldTpsResetCommand.java:31`) | Hytale seulement : le cœur reste à 20/s |
| Un seul PNJ | composant `Frozen` (`/npc freeze`, `server/npc/commands/NPCFreezeCommand.java:53-75`), `StepComponent` (`/npc step`) | Gèle ou fait avancer le corps |
| La colonie seule | un drapeau dans `ColonyTickSystem` pour sauter les ticks, et « pas × N » = N appels à `rt.tickCore()` (`plugin/.../ColonyTickSystem.java`), plus `Frozen`/`StepComponent` sur les corps | Pause et pas à pas de l'IA, sans figer le monde |

### 3.4 Pièges

- **Noms des citoyens**
  - `RoleDebugDisplay` réécrit le `Nameplate` (`RoleDebugDisplay.java:250-258`) ;
  - `/npc debug` le retire à chaque changement de drapeaux (`NPCDebugCommand.java:61-66`) ;
  - `CitizenNameplates` ne renomme un citoyen que quand son nom change. Le nom peut donc disparaître **[in-game]**.
- **Voir à travers les murs** : **impossible**. Aucun drapeau de profondeur n'existe côté serveur (`plugin-b-api.md` § 29).
- **Nœuds A* visités dessinés dans le monde** (le rendu MC) :
  - `AStarBase.getVisitedBlocks()` est public (`server/npc/navigation/AStarBase.java:130`) ;
  - mais l'A* d'un PNJ est un champ `protected` de `BodyMotionFindBase` : hors d'atteinte sans réflexion.
  - Reste `VisPath`, qui montre les points de passage et la cible, et le journal ASCII.

## 4. Ce que HyColony a déjà

- **Commandes** : `/hycolony info|rank|delete|selftest` (`plugin/.../command/HyColonyCommand.java:57-60`). `selftest` teste les **ports** contre le serveur, pas l'état vivant d'une colonie.
- **Tests en jeu** : `docs/TESTING.md`, à dérouler à la main.
- **Journaux** : `System.Logger` dans le cœur (20 fichiers), `HytaleLogger` dans le plugin.
- **Réparation au chargement** : `ColonySerializer.heal` ne renvoie qu'un booléen (`core/.../app/persistence/ColonySerializer.java:101-110, 194-221`).
- **Crochets déjà là, mais non exposés** :
  - `TickRateStateMachine.history()` : 20 transitions sans horodatage, jamais appelée (`kernel/ai/TickRateStateMachine.java:18, 136-139, 165-167`) ;
  - `WorkerMachine.lastError()` ;
  - `RequestManager.setCreationListener` (tests seulement) ;
  - `CitizenManager.aiState`, `jobActivity`, `bodyOf` ;
  - le niveau privé de `StuckHandler`.
- **Pas d'onglet Debug** dans la fenêtre du citoyen : écart documenté (`plugin/.../ui/citizen/CitizenPage.java:26`).
- **Aucune API** : `checkModApis` déclare `"dev.hycolony." to emptyList()` (`build-logic/src/main/kotlin/hy.java-checks.gradle.kts:103`).
- **Simulations** : `FakeBodies` n'a que les modes `instant` et `frozen` (`core/src/test/.../testing/FakeBodies.java:41-44, 89-99`). Il n'a **pas de mode « la navigation finit ailleurs »**, donc le bug A ne pouvait pas y apparaître.

## 5. Quoi vérifier : les invariants

Chez MC aussi, un livreur sans tâche **reste** en `WORK` : `canGoIdle()` vaut `false` par défaut et `EntityAIWorkDeliveryman` ne le surcharge pas (clone `AbstractEntityAIBasic.java:1921-1924`, `CitizenAI.java:250-256`). Pour le bug B, il faut donc surveiller **l'état du métier et la file**, pas `WORKING`.

| # | Invariant | Données du cœur | Bug |
|---|---|---|---|
| 1 | Marche déclarée finie avec le corps à plus de `ARRIVAL_RANGE` de la cible, ou à un étage différent (dy ≥ 1) | `BodyWalker.settled`, position, `NavStatus` | **A** |
| 2 | État du métier inchangé depuis N ticks, hors attente légitime | `TickRateStateMachine`, avec l'horodatage à ajouter | **B** |
| 3 | Livreur en `PREPARE_DELIVERY`, `DELIVERY` ou `PICKUP` avec une file vide, ou une tête de file absente ou pas `IN_PROGRESS` | `CourierContext`, `RequestManager.get` | **B** |
| 4 | Requête `ASSIGNED` ou `IN_PROGRESS` sans résolveur, ou absente de toute file de livreur | `resolverOf`, `assignedTo` | B |
| 5 | Requête racine orpheline en cours de partie | `RequestCanceller.cancelOrphans` (`request/RequestCanceller.java:61-70`) | - |
| 6 | Métier sans hutte, ou liste d'ouvriers fausse | règles de `ColonySerializer.heal` | - |
| 7 | Citoyen vivant sans corps depuis plus de `RESPAWN_CHECK_TICKS`, ou corps sans citoyen | `CitizenManager.bodyOf` | - |
| 8 | Ordre de travail réclamé par une hutte absente ; ordre sans preneur alors qu'un bâtisseur est libre | `WorkOrder.claimedBy` | - |
| 9 | Exceptions d'IA à répétition | `WorkerMachine.lastError`, `CitizenAI.onException` | - |
| 10 | Anti-blocage qui monte à la téléportation ou à l'abandon | `StuckHandler.level` | A |
| 11 | Objets perdus ou dupliqués lors d'un transfert (`debuginventories` de MC) | totaux avant et après | - |
| 12 | Ce que `heal` a réparé | valeur de retour de `heal` | - |

## 6. Au-delà du visuel : les autres fonctions de débogage

| Fonction | Inspiration | Comment chez nous | Statut |
|---|---|---|---|
| Pause, pas à pas, accéléré de la colonie | RimWorld, Factorio | § 3.3 | vérifié |
| **Historique horodaté** de chaque citoyen (transitions, marches avec départ, cible, fin, raison, distance, requêtes) | MC AI History, Unreal Visual Logger | anneau borné dans le cœur | vérifié (cœur) |
| **Relecture** : frise dans le menu, avec les traces de marche dessinées pour l'instant choisi | Visual Logger | historique + `DisplayDebug` | vérifié ; coût élevé |
| Envoyer à une position | MC `walk`, RimWorld | transition unique dans `CitizenAI`, comme MC | vérifié |
| Forcer un état ou une pause | RimWorld | transition unique ; MC n'a pas l'équivalent | vérifié |
| Donner ou retirer des objets, remplir une requête | RimWorld, ONI | ports de conteneurs ; `RequestManager.overrule` (`request/RequestManager.java:122-130`) | vérifié |
| Déclencher un loisir, téléporter, tuer ou refaire le corps | RimWorld | `CitizenData.leisureTime`, `CitizenBodies.teleport`, `despawn` puis réapparition | vérifié |
| Remise à zéro du système de requêtes | MC `requestsystem-reset` | à porter (il n'existe pas de `reset` chez nous) | vérifié |
| **Graphe des requêtes** | Unreal, MC | des lignes dans le monde entre la hutte qui demande, l'entrepôt et le livreur, plus une liste dans le menu | vérifié |
| Couches par bâtiment : zone de travail, étagères, champs, ordres | RimWorld, Factorio | cubes `DisplayDebug` pour un seul joueur | vérifié |
| **Carte de chaleur des blocages** | outils de navigation | positions des relances, téléportations et abandons de `StuckHandler`, en cubes colorés ou en repères de carte | vérifié |
| Performance : temps d'IA par tick, par colonie et par citoyen | spark, Unreal | `System.nanoTime` autour de `tickCore`, affiché dans le HUD ; spark pour la JVM | vérifié |
| Contrôleur d'invariants (§ 5), aussi dans les simulations | - | cœur | vérifié |

## 7. Mod séparé ou intégré ?

Recommandation : **intégré à HyColony**, comme MC et HyDomum (`dev.hydomum.plugin.debug`).
- **Deux paquets** : `diagnostics` dans le cœur (Java pur, testé et branché dans les simulations) et `debug` dans le plugin (caméra, HUD, menu, dessins, carte). Accès réservé aux opérateurs, plus le mode débogage par joueur de MC.
- **Ce qu'exigerait un mod séparé** :
  - une API publique large, qui figerait des états aujourd'hui package-private comme `CourierState` ;
  - la modification de garde-fous : la table `modApis` de `build-logic/` et CLAUDE.md § 1, soumis à l'accord de l'utilisateur (§ 10) ;
  - des règles de `heal` dupliquées ou exposées ;
  - des tests avec les jars de production.
- **Pourquoi Alec's NPC Debug UI peut être séparé** : il lit les composants de Hytale, alors que l'état de nos citoyens vit dans notre cœur, hors des composants.
- **spark** reste l'outil séparé pour la performance.

## 8. Proposition : le menu, par paliers

Valeur : ★★★ aurait trouvé A ou B tout de suite ; ★★ aide beaucoup ; ★ confort.
Coût : faible (moins d'un jour), moyen (quelques jours), élevé.

### V1 : couvre les bugs A et B

| # | Fonction | Valeur | Coût | Faisabilité |
|---|---|---|---|---|
| 1 | **Marche finie loin de la cible** : événement et journal (citoyen, cible, position, `NavStatus`, raison), plus un mode « finit ailleurs » dans `FakeBodies` | ★★★ A | faible | vérifié (cœur) |
| 2 | **Historique horodaté** par citoyen : transitions en ticks, marches, raisons | ★★★ A, B | faible | vérifié (cœur) |
| 3 | **`/hycolony debug watch <citoyen \| visé>`** : caméra à la troisième personne qui suit le citoyen, par le mécanisme de `/spectate` ; sortie par `/hycolony debug watch` ou la capacité 1 | ★★★ | moyen | vérifié ; `/spectate target` est testable dès maintenant **[in-game]** |
| 4 | **HUD « ce qu'il pense »** pendant le suivi, rafraîchi toutes les 10 ticks : état, étape du métier, cible, `NavStatus`, niveau d'anti-blocage, file et état des requêtes, loisir, 5 dernières transitions, alerte d'invariant | ★★★ A, B | moyen | vérifié ; coût du rafraîchissement **[in-game]** |
| 5 | **Dessins du citoyen suivi**, pour l'opérateur seul : ligne corps → cible (rouge si la marche s'est finie loin), sphère sur la cible, cube sur la zone de travail. Plus `VisPath` pour le chemin A* | ★★★ A | faible | vérifié ; cachés par les blocs |
| 6 | **Contrôleur d'invariants** : `/hycolony debug check`, un contrôle périodique en option et les simulations | ★★ B | moyen | vérifié (cœur) |
| 7 | **Pause, pas × N et accéléré** de la colonie : drapeau dans `ColonyTickSystem`, plus `Frozen` et `StepComponent` sur les corps ; `/time dilation` pour le ralenti | ★★ | faible | vérifié ; effet sur les corps **[in-game]** |

### V2 : le vrai menu et l'interaction

| # | Fonction | Valeur | Coût | Faisabilité |
|---|---|---|---|---|
| 8 | **Menu de débogage** (page) : liste des citoyens avec état et alertes ; boutons Suivre, couches (chemin, cible, zone, requêtes), actions, pause et pas | ★★ | moyen | vérifié |
| 9 | **« Envoyer ici »** : le bloc visé, le dernier repère de carte, ou « Teleport » de la carte intercepté en mode armé | ★★ A (reproduire) | moyen | regard et repère vérifiés ; interception vérifiée dans les sources **[in-game]** |
| 10 | **Mode de jeu `HyColony_DebugWatch`** : clic gauche = citoyen suivant, clic droit = menu, capacité 1 = caméra libre | ★★ | faible | **[in-game]** (asset de mode de jeu dans notre pack) |
| 11 | **Première personne** dans les yeux du citoyen, en bascule | ★ (plus spectaculaire qu'utile) | faible | **[in-game]** |
| 12 | **Actions de débogage** : forcer un loisir, remplir une requête, donner un objet, téléporter, refaire le corps, remise à zéro des requêtes (MC) | ★★ | moyen | vérifié |
| 13 | **Graphe des requêtes** en lignes dans le monde, plus une liste dans le menu | ★★ B | moyen | vérifié |

### Plus tard

| # | Fonction | Valeur | Coût | Faisabilité |
|---|---|---|---|---|
| 14 | Relecture : frise, traces de marche passées | ★★ | élevé | vérifié |
| 15 | Carte de chaleur des blocages | ★★ | moyen | vérifié |
| 16 | Repères de carte des citoyens, de la cible suivie et des huttes, avec « Suivre » dans le menu contextuel | ★ | moyen | repères vérifiés ; commande du menu **[in-game]** |
| 17 | Vue du dessus avec curseur : cliquer un citoyen pour le suivre, un bloc pour l'y envoyer | ★ | moyen | **[in-game]** |
| 18 | Couches par bâtiment (zone, étagères, champs) | ★ | faible | vérifié |
| 19 | Temps d'IA par tick dans le HUD | ★ | faible | vérifié |
| 20 | Rôle « debug » avec le journal A* ASCII | ★ | faible | vérifié |

### Impossible ou introuvable

- Voir les formes ou les lueurs à travers les murs (§ 3.4).
- Dessiner dans le monde les nœuds A* visités, sans réflexion (§ 3.4).
- Un chat cliquable qui lance une commande : seul `link(url)` existe.
- `SpectateControl Next/Prev` sur des PNJ : il ne fait défiler que les joueurs.
- Recevoir « Select Location » de la carte : aucun paquet vers le serveur.

## 9. Non vérifié

- **[in-game]** Tout ce qui est marqué ainsi aux § 3 et 8, surtout :
  - la première personne attachée à un PNJ ;
  - le chargement d'un mode de jeu depuis notre pack ;
  - les commandes des menus contextuels de la carte ;
  - le coût d'un HUD rafraîchi souvent ;
  - l'effet de la pause du monde sur nos systèmes.
- Les permissions de `/npc`, `/debug` et `/log`.
- La compatibilité de spark avec la 0.7.0-pre.4.
- La licence d'Alec's NPC Debug UI : on n'en reprend que les idées.
- Cities: Skylines (ModTools) : le README ne décrit pas ses fonctions, donc il n'a pas été retenu.
