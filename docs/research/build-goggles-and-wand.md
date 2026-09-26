# Lunettes de construction et outil de construction (« baguette »)

Recherche pour deux systèmes à venir : les **Build Goggles** de MineColonies (en premier) et le **Build Tool** de Structurize (la « baguette »).

Versions de référence :
- MineColonies `version/main` cible Minecraft 1.20.1 et Structurize `1.20.1-1.0.818` (`gradle.properties`, clé `structurize_version`).
- Structurize `version/main` est en 1.20.1 aussi (`gradle.properties` : `exactMinecraftVersion=1.20.1`).
- Hytale : serveur 0.6.8 décompilé (`build/vineflower/hytale-server/com/hypixel/hytale/`) et `release-0.6.8-Assets.zip`.

Abréviations : `MC/` = `raw.githubusercontent.com/ldtteam/minecolonies/version/main/src/main/java/com/minecolonies/`, `ST/` = `raw.githubusercontent.com/ldtteam/Structurize/version/main/src/main/java/com/ldtteam/structurize/`, `HY/` = `build/vineflower/hytale-server/com/hypixel/hytale/`.

## Partie A : les originaux

### A.1 Build Goggles (MineColonies)

**Objet.** `ItemBuildGoggles extends ArmorItem`, type `HELMET`. Armure à 0 partout, pas réparable, rareté `UNCOMMON` (`MC/core/items/ItemBuildGoggles.java:17-37`). L'infobulle affiche la lore « They do nothing! » et l'état « Blueprint previews enabled/disabled » (`ItemBuildGoggles.java:40-55` ; textes dans `src/main/resources/assets/minecolonies/lang/manual_en_us.json`). Il existe une texture `build_goggles_disabled` et un modèle `models/item/build_goggles_disabled.json` : l'icône change quand l'aperçu est coupé.

**Obtention.**
- Recette en forme (`src/datagen/generated/minecolonies/data/minecolonies/recipes/build_goggles.json`) : `NIN / GTG / L L`, avec N = pépite d'or, I = lingot de fer, G = vitre, **T = `structurize:sceptergold` (le Build Tool)**, L = cuir.
- Quête « The Builder's Glasses » (`src/main/resources/data/minecolonies/colony/quests/guides/buildergoggles.json`). Elle se déclenche avec une Builder's Hut niveau 2 et un bâtisseur. Il faut livrer une paire de lunettes, et la récompense est une paire de lunettes (`"rewards": [{"type":"minecolonies:item","item":"minecolonies:build_goggles","qty":1}]`).

**Ce qu'elles affichent** (`MC/core/client/render/worldevent/ColonyBlueprintRenderer.java`) :
- La règle `BuildGoggles` est active si le casque porté est les lunettes (`ctx.clientPlayer.getItemBySlot(EquipmentSlot.HEAD).is(ModItems.buildGoggles)`, l.449-452).
- **Tous les ordres de travail de la colonie la plus proche** dans le rayon (l.462-475), pas seulement ceux qui sont en cours. Cela inclut les ordres non réclamés, les décorations et les réparations. Chaque ordre est rendu avec son plan (pack, chemin, rotation, miroir) à `workOrder.getLocation()`.
- Les ordres `REMOVE` n'affichent **que la boîte**, sans le plan (`boxOnly = type == REMOVE`, l.472).
- En plus, **l'ancre seule** des huttes non construites (niveau 0, niveau max > 0) qui n'ont pas d'ordre, « pour retrouver les huttes perdues » (l.477-489).
- Le rendu du plan est le **plan complet** : `BlueprintHandler.drawAtListOfPositions(data, positions, …)` (l.172-176). Ce ne sont ni les blocs restants ni les couches. La transparence est celle du réglage client Structurize `transparency`.
- Les boîtes sont tracées par `renderBoxes` (l.185-223) :
  - le contour du plan en bleu (`0xFF0000FF`, épaisseur 0.08) ;
  - si le joueur est accroupi (`isShiftKeyDown`), l'ancre en rouge ;
  - si le joueur est accroupi, la position de statut du bâtisseur qui a réclamé l'ordre, en vert.

**Portée et rafraîchissement.**
- Réglage client `buildgogglerange`, **50 blocs par défaut** (min 1, max 250), comparé au carré de la distance entre `workOrder.getLocation()` et le joueur (`MC/api/configuration/ClientConfiguration.java:29` ; `ColonyBlueprintRenderer.java:458,464`).
- Le cache se reconstruit quand le joueur a bougé de plus de `CACHE_RESET_RANGE = 12.5` blocs, ou sur `invalidateCache()` (nouvel ordre…) (l.59, 159-163).
- Les plans en chargement sont réessayés toutes les 20 ticks (l.165-168).
- Hors colonie, rien n'est affiché et les caches sont vidés (l.133-139).

**Interrupteur.** Touche `key.minecolonies.toggle_goggles` (« Toggle Goggles Preview »), **sans touche par défaut** (`InputConstants.UNKNOWN`, `MC/api/client/ModKeyMappings.java:20-21`). Elle bascule `shouldRenderBlueprints` et joue un son de note aiguë ou grave (`ColonyBlueprintRenderer.java:126-131`). Le réglage n'est pas persisté : c'est un champ statique client.

**Règles voisines du même moteur** (`renderRules`, l.95-100) :
- `NearBuildPreview` : quand on tient le Build Tool avec un aperçu, les huttes voisines sont rendues **à leur niveau max** si leur emprise touche l'aperçu gonflé de `2 + neighborbuildingrange`. Défaut 4, réglage `neighborbuildingrendering` à true (l.378-441). Une hutte déjà au niveau max n'affiche que sa boîte.
- `AssistantHammerPreview` : les ordres du bâtisseur rendus en transparence 0.4 quand on tient un marteau d'assistant (l.522-585). Hors sujet ici.

### A.2 Build Tool (Structurize) : fonctions

**Objet.** `ItemBuildTool` (id `sceptergold`), pile de 1, rendu intact après une recette (`ST/items/ItemBuildTool.java`).
- Clic droit sur un bloc : la fenêtre s'ouvre à la position du bloc + la face visée (`useOn`, l.33-40).
- Clic droit dans le vide : la fenêtre s'ouvre sans position (`use`), et l'aperçu garde sa position précédente. S'il n'en a aucune, la fenêtre refuse avec `structurize.gui.missing.pos` (`AbstractBlueprintManipulationWindow.onOpened`, l.129-139).

**Fenêtre de sélection** (`ST/client/gui/WindowExtendedBuildTool.java`) :
- Le **pack de styles** est choisi dans `WindowSwitchPack` (bouton « switch style »). S'il n'y a aucun pack, cette fenêtre s'ouvre d'abord (l.281-291, 296-303).
- Navigation en **arbre** dans le pack. Quatre listes : `subcategories` (dossiers), `blueprints`, `alternatives` (variantes d'un même nom) et `levels` (niveaux). Le fil d'Ariane `pack/profondeur/fichier` est affiché dans `tree` (l.72-127, 244-262). La correspondance est « profondeur → nom → niveaux ».
- Filtre `BLOCK_BLUEPRINT_REQUIREMENT` : l'ancre peut imposer des conditions (`IRequirementsBlueprintAnchorBlock`, l.56-70). D'après le wiki, les huttes n'apparaissent que si le **bloc de hutte est dans l'inventaire**, et une hutte posée démarre au niveau 1 quel que soit le niveau choisi (https://minecolonies.com/wiki/items/sceptergold/).
- Le panneau `manipulator` n'apparaît qu'une fois un plan choisi (l.264).

**Manipulation de l'aperçu** (`ST/client/gui/AbstractBlueprintManipulationWindow.java`) :
- Boutons (l.107-118) : confirmer, annuler, gauche, droite, avant, arrière, haut, bas, tourner à droite, tourner à gauche, miroir, réglages.
- Les déplacements se font d'**un bloc** et sont **relatifs à la direction du joueur** : avant = `player.getDirection()`, gauche = sens antihoraire, etc. (l.541-571). Haut et bas se font sur Y (l.525-539).
- Rotation par quarts de tour (`Rotation.CLOCKWISE_90` / `COUNTERCLOCKWISE_90`) et miroir `FRONT_BACK` (l.516-590). L'indicateur de rotation (flèche verte) et l'état du miroir sont montrés (l.595-610).
- **Ancre au niveau du sol** (`adjustToGroundOffset`, l.612-637) : le décalage vient des tags du plan (`BlueprintTagUtils.getGroundAnchorOffset`), puis `--groundOffset` pour compenser le clic sur la face du dessus du sol. Deux styles hérités existent : camp (1 niveau) et bateau (3 niveaux).
- Réglages de rendu (`initSettings`) : `render_placeholders_nice`, `share_previews`, `see_shared_previews`, `light_level`, `transparency` (`ST/config/ClientConfiguration.java:40-44`).
- Une astuce s'affiche et disparaît après 10 s (`openTicks >= 200`, l.259-266).
- Échap garde l'aperçu 3D visible. Un nouveau clic droit rouvre la fenêtre pour « ajuster » (wiki sceptergold).

**Raccourcis clavier** (`ST/client/ModKeyMappings.java:48-67`). Ils sont actifs **seulement quand la fenêtre est ouverte** (contexte `BLUEPRINT_WINDOW`, l.20-37 ; traitement dans `onUnhandledKeyTyped`, l.268-322) :

| Action | Touche par défaut |
|---|---|
| avant / arrière / gauche / droite | flèches ↑ ↓ ← → |
| haut / bas | pavé num. `+` / `-` |
| tourner horaire / antihoraire | Maj + → / Maj + ← |
| miroir | M |
| placer | Entrée |

`key.structurize.teleport` (outil de scan) n'a pas de touche par défaut.

**Confirmation** (`confirmClicked`, l.149-177 ; `updatePlacementOptions`, l.207-257) :
- **Survie** : on cherche les `ISurvivalBlueprintHandler` compatibles. S'il n'y en a aucun, son d'erreur (et message si aucun gestionnaire n'est enregistré). S'il y en a un seul, il est appliqué directement. Sinon, une liste de choix s'affiche.
- **Créatif** : deux choix en plus.
  - « Complete » colle tout, y compris les blocs substituts (placeholders).
  - « Pretty » colle comme le ferait un bâtisseur.
  - Côté serveur, `PlaceStructureOperation` passe dans la file `Manager` (`ST/storage/BlueprintPlacementHandling.process`).
- Annuler supprime l'aperçu et le synchronise vide vers le serveur (l.306-317).

**Gestionnaire survie de MineColonies** (`MC/core/placementhandlers/main/SurvivalHandler.java`) :
- `canHandle` : si `blueprintBuildMode` est actif, il faut une colonie proche (l.25-34).
- Dans une colonie, il faut la permission **`MANAGE_HUTS`**, sinon `BP_NO_PERM` (l.58-65).
- Hôtel de ville : il faut être dans la colonie ou `isFarEnoughFromColonies`, sinon `TOWNHALL_TOO_CLOSE` (l.67-80).
- Tout le plan doit tenir dans la colonie : chaque chunk couvert par l'emprise appartient à la colonie, testé tous les 16 blocs de `min+1` à `max`. Sinon `BP_OUTSIDE_COLONY` (l.82-87, 251-279).
- **Hutte** (ancre `AbstractBlockHut`), l.95-220 :
  - pack serveur obligatoire, et pas de `scans/` ;
  - le bloc de hutte est pris dans l'inventaire (sauf en créatif) ;
  - l'identifiant de colonie du bloc doit correspondre (`WRONG_COLONY`) ;
  - la hutte est posée à la position de l'ancre, puis `onBlockPlacedByBuildTool(… mirror, pack, path)` ;
  - le bâtiment reçoit pack, chemin, niveau (tag `TAG_OTHER_LEVEL` du bloc, sinon 0) et miroir, et le **ruban de chantier** est posé ;
  - avec `TAG_PASTEABLE`, le niveau est collé directement.
  - **Aucun ordre de travail n'est créé** : le joueur le lance ensuite depuis la fenêtre de la hutte (« Build Options » puis « Build Building », wiki sceptergold).
- **Décoration** (autre ancre) : le serveur ouvre `WindowBuildDecoration` sur le client (`OpenDecoBuildWindowMessage`, l.221-239). Pour une ancre à niveaux, on part du niveau 1.
  - La fenêtre montre les ressources nécessaires et une liste pour choisir le bâtisseur (`MC/core/client/gui/WindowBuildDecoration.java`).
  - « Build » envoie `DecorationBuildRequestMessage`, qui vérifie `MANAGE_HUTS`.
  - **Si un ordre de décoration existe déjà à cette position, il est supprimé** : c'est une bascule.
  - Sinon, un `WorkOrderDecoration` est créé, de type `BUILD` si l'ancre n'est pas un `BlockDecorationController`, et réclamé par le bâtisseur choisi le cas échéant (`MC/core/network/messages/server/DecorationBuildRequestMessage.java`, `onExecute`).
- Champ de plantation : fenêtre dédiée (`OpenPlantationFieldBuildWindowMessage`, l.89-94).

**Aperçus multiples et partage.**
- Un seul aperçu local par joueur (clé `"blueprint"` du `RenderingCache`).
- L'aperçu est envoyé au serveur (`SyncPreviewCacheToServer`), qui le redistribue aux joueurs à moins de 128 blocs **qui ont activé `see_shared_previews`** (`ST/storage/rendering/ServerPreviewDistributor.java`). Côté client, il est rangé sous la clé `SHARED_PREFIX + uuid` (`SyncPreviewCacheToClient`).
- `share_previews` et `see_shared_previews` valent **false par défaut** : l'aperçu est donc **privé par défaut**.

**Annuler / refaire** (`ST/client/gui/WindowUndoRedo.java`) : fenêtre qui liste les dernières opérations (`OperationHistoryMessage`, `UndoRedoMessage`). Elle s'ouvre depuis le bouton `BUTTON_UNDOREDO` de l'outil de formes (`WindowShapeTool`, l.159). Ce sont des opérations de collage et de formes, un usage créatif.

**Outils liés (optionnels).**
- **Scan Tool** (`sceptersteel`, `ST/items/ItemScanTool.java`, `WindowScan.java`) :
  - on sélectionne deux coins, puis clic droit dans le vide pour ouvrir la fenêtre ;
  - on enregistre le plan (`saveStructure`) ;
  - la fenêtre liste les ressources et entités, avec « remplacer bloc », « retirer bloc/entité » et « remplir les substituts » ;
  - emplacements multiples à la molette, et téléportation (touche sans défaut).
- **Shape Tool** (`ItemShapeTool`, `WindowShapeTool`) :
  - formes cube, sphère, demi-sphère, cylindre, cône, pyramide, onde…, pleines ou creuses ;
  - largeur, longueur, hauteur et fréquence, bloc principal et bloc de remplissage, équation libre ;
  - se colle comme un aperçu.
- **Tag Tool** : il pose des tags (dont le niveau du sol) dans un plan.

**Ce dont les joueurs se servent le plus.** Ceci est une synthèse du wiki et du code, pas une mesure :
1. Choisir une hutte et un style, prévisualiser, tourner, décaler, confirmer : c'est le seul moyen de poser une hutte avec son plan aligné.
2. Les décorations en ordre de travail (murs, routes, déco).
3. Les huttes voisines au niveau max pendant qu'on place (`NearBuildPreview`), pour éviter les chevauchements.
4. Les lunettes pour suivre les chantiers et retrouver les huttes non construites.

Collage créatif, annuler/refaire, scan et formes servent aux créateurs de plans, pas au joueur de survie.

## Partie B : ce que Hytale 0.6.8 permet

### B.1 `PersistentPrefabPreview` / `PrefabPreview`

Sources : `HY/server/core/modules/entity/component/PersistentPrefabPreview.java`, `PrefabPreview.java`, `HY/server/core/modules/entity/prefabpreview/PrefabPreviewSystems.java`, enregistrement dans `HY/server/core/modules/entity/EntityModule.java:495-497, 552-554`.

**API.** Toutes ces méthodes s'appellent sur le thread du monde. `remove` fait `store.removeEntity(ref, RemoveReason.REMOVE)`.
- `spawn(Store, Vector3d position, Rotation3f rotation, String prefabKey, int visibleLayerCount)` → `Ref<EntityStore>` ;
- une variante avec `biomeTint` et `waterTint` ;
- `updateLayers(Store, Ref, int)` ;
- `remove(Store, Ref)`.

L'entité créée porte `NetworkId`, `TransformComponent(position, rotation)`, `PersistentPrefabPreview` et `UUIDComponent` (l.111-121). Elle est **sauvegardée** : le codec est enregistré sous l'identifiant `"PrefabPreview"`.

**Comment les blocs arrivent au client.**
- `PrefabPreviewSetup` (`HolderSystem`, requête `Persistent ∧ ¬PrefabPreview`) résout la clé par `PrefabStore.findBrowsablePrefabPath(key)`, charge le `BlockSelection` et appelle `selection.toPacket()`. Il pose ensuite un `PrefabPreview(blocks, fluids, layers, biomeTint, waterTint)` à l'exécution, non persisté (l.118-159).
- `PrefabPreviewTracker` envoie la **liste complète** de blocs une fois à chaque joueur qui commence à voir l'entité (`newlyVisibleTo`). Ensuite, un changement de couches ne renvoie que le compte (l.192-235).
- Les blocs sont immuables une fois résolus (Javadoc de `PrefabPreview`).

**Clé de prefab.**
- `findBrowsablePrefabPath` retire le suffixe, puis essaie `.lpf` et `.prefab.json` : d'abord `findAssetPrefabPath` (tous les packs d'assets, donc le nôtre), puis le dossier des prefabs serveur (`HY/server/core/prefab/PrefabStore.java:346-363`).
- Nos plans vivent dans le pack du plugin et sont déjà résolus par `findAssetPrefabPath` (`plugin/.../adapter/HytaleBlueprintSource.java:89,127`). Ils sont donc utilisables comme clé, sans suffixe.
- Une clé introuvable donne un simple avertissement : l'entité existe mais n'affiche rien (l.128-129).

**Contenu en mémoire (reste des blocs, sous-ensemble) : oui, en contournant `PersistentPrefabPreview`.**
- `PrefabPreview` a un **constructeur public** `PrefabPreview(BlockChange[] blocks, FluidChange[] fluids, int visibleLayerCount, int biomeTint, int waterTint)`.
- Le tracker ne demande que `Visible ∧ PrefabPreview`, pas le composant persistant (l.169-173).
- On peut donc créer une entité `NetworkId + TransformComponent + PrefabPreview + NonSerialized`, sur le modèle de l'aperçu de `ChangeModelPage`, qui utilise `EntityStore.REGISTRY.getNonSerializedComponentType()` (`HY/builtin/model/pages/ChangeModelPage.java:211-217`). `PrefabPreviewSetup` ne la touche pas, car sa requête exige le persistant.
- Le `BlockChange[]` se construit :
  - depuis un `BlockSelection` en mémoire, avec `selection.toPacket().blocksChange` (offsets relatifs à l'ancre, les fillers sont exclus : `BlockSelection.java:2110-2131`) ;
  - ou à la main, avec `new BlockChange(int x, int y, int z, int blockId, byte rotation)` (`HY/protocol/packets/interface_/BlockChange.java:26`). L'identifiant vient de `BlockType.getAssetMap()`.
- Ainsi « seulement les blocs restants » = le plan moins les cellules déjà correctes, calculé par nous. **[in-game]** Il reste à confirmer que le client affiche une entité qui n'a jamais eu de `PersistentPrefabPreview`.

**Couches.** `visibleLayerCount` montre « les N couches du bas » (Javadoc de `spawn`). Le découpage se fait côté client ; la référence (Y minimal du prefab ?) reste **[in-game]**. `updateLayers` ne renvoie que le compte : c'est très peu coûteux.

**Changer les blocs après coup.** Il n'existe aucune mise à jour partielle de la liste. Il faut supprimer l'entité et en recréer une : nouvelle ref, donc renvoi complet. Poser un nouveau `PrefabPreview` sur la même entité n'enverrait rien aux joueurs qui la voient déjà, car le tracker n'envoie les blocs qu'aux `newlyVisibleTo`.

**Déplacer et tourner.**
- `TransformSystems.EntityTrackerUpdate` envoie un `TransformUpdate` à tous les observateurs dès que la position ou la rotation du `TransformComponent` change (`HY/server/core/modules/entity/system/TransformSystems.java:45-127`). `TransformComponent.setPosition` et `setRotation` existent (l.107, 165).
- `PrefabPreviewUpdate` ne porte ni position ni rotation (`HY/protocol/PrefabPreviewUpdate.java:21-26`). **[in-game]** Il reste à vérifier que le client déplace ou tourne le maillage de l'aperçu selon la transformation de l'entité.
- Solution sûre pour la rotation et le miroir : les appliquer au plan côté serveur, puis recréer l'entité. `BlockSelection.rotate(Axis, int)` et `flip(Axis)` existent (`BlockSelection.java:1510, 1868`) ; notre `HytaleBlueprintSource` produit déjà les 4 rotations.

**Visibilité : pour tous par défaut, filtrable par joueur.**
- `CollectVisible` met dans `EntityViewer.visible` toute entité `Transform + NetworkId` dans le rayon de vue du joueur, sans exception (`HY/server/core/modules/entity/tracker/EntityTrackerSystems.java:402-456` ; `NetworkSendableSpatialSystem` : `Archetype.of(TransformComponent, NetworkId)`).
- Le rayon est `viewRadius * 32` blocs (`HY/server/core/universe/Universe.java:1555-1557`).
- Pas de `BoundingBox` : pas d'élimination par niveau de détail (`LODCull`, l.951-1028).
- Le pipeline est prévu pour des filtres : « Other systems can run to filter visible entities in EntityViewer#visible » (l.99-101). Deux exemples vanilla le font :
  - `HideEntitySystems.AdventurePlayerSystem`, un `EntityTickingSystem` du groupe `EntityTrackerSystems.FIND_VISIBLE_ENTITIES_GROUP` (public), avec `SystemDependency(AFTER, CollectVisible.class)`, qui retire des refs de `visible` et incrémente `hiddenCount` (`HY/server/core/modules/entity/system/HideEntitySystems.java:36-103`) ;
  - `SpectatorSystems` (l.188).
- Un système du plugin peut faire de même pour ne garder un aperçu que pour son propriétaire, ou que pour les porteurs de lunettes. Pour éviter de parcourir tout `visible`, il vaut mieux parcourir notre petite table d'aperçus et appeler `visible.remove(ref)`.
- Le composant `HiddenFromAdventurePlayers` cache une entité aux joueurs en mode Aventure seulement : il ne sert pas ici.

**Coût.**
- Réseau : la liste complète de `BlockChange` (5 champs) est envoyée à chaque joueur qui entre dans le rayon. Il n'y a pas de plafond dans le composant, mais la page vanilla des prefabs coupe au-delà de 4 000 000 blocs (`HY/builtin/buildertools/prefablist/PrefabPage.java:242-245`).
- Serveur : un tick par aperçu visible (`PrefabPreviewTracker`, parallélisable).
- Une hutte fait quelques milliers de blocs : c'est raisonnable.
- **[in-game]** Le coût de maillage côté client reste à mesurer, pour décider combien d'aperçus garder visibles en même temps.

**Autres aperçus vanilla (pour la fenêtre).**
- L'élément UI **`PrefabPreviewComponent`** (options `AllowDragYaw`, `AllowDragPitch`, `AllowZoom`, `AllowPan`, `ShowAnchor`) affiche un prefab en 3D dans une page (`Common/UI/Custom/Pages/PrefabListPage.ui`, `#PrefabPreview`).
- Il est alimenté par le paquet `BuilderToolPrefabPreview` (`blocksChange`, `fluidsChange`, `tilt`, `spinSpeed`, `previewScale`, teintes), envoyé directement au joueur (`PrefabPage.sendPreviewPacket`, l.238-257).
- Il pourrait montrer la hutte choisie dans notre fenêtre de sélection. **[in-game]** Il reste à vérifier que le client accepte ce paquet hors des pages de builder tools.

**Boîtes (contour et ancre, comme les lunettes MC).**
- `DebugUtils.add…` envoie un paquet `DisplayDebug` (forme, matrice, couleur, opacité, durée) à **tous** les joueurs du monde (`HY/server/core/modules/debug/DebugUtils.java:88-110`).
- Le constructeur `DisplayDebug` est public. On peut donc l'écrire sur le seul `playerRef.getPacketHandler()`.
- **[in-game]** Il reste à vérifier que les formes de debug s'affichent pour un joueur ordinaire.

### B.2 Détecter le casque porté

- Emplacement : `InventoryComponent.Armor` (`EntityModule.getArmorInventoryComponentType()`), de capacité `ItemArmorSlot.VALUES.length` = 4. L'indice est `ItemArmorSlot.Head` = 0 (`HY/protocol/ItemArmorSlot.java:6-9` ; `HY/server/core/inventory/InventoryComponent.java:54, 404-459`).
- Lecture : `store.getComponent(ref, InventoryComponent.Armor.getComponentType()).getInventory().getItemStack((short) 0)` (`getInventory()`, l.140).
- Événement de changement : `InventorySystems.ArmorChangeEventSystem` publie chaque tick un **`InventoryChangeEvent`** ECS par changement du conteneur d'armure (`commandBuffer.invoke(ref, event)`). `event.getComponentType()` vaut alors `InventoryComponent.Armor.getComponentType()` (`HY/server/core/inventory/InventorySystems.java:233-278` ; `HY/server/core/event/events/ecs/InventoryChangeEvent.java`).
- Écoute : un `EntityEventSystem<EntityStore, InventoryChangeEvent>`, sur le modèle de `HY/builtin/adventure/objectives/systems/ObjectiveInventoryChangeSystem.java`. On compare le type de composant, puis on relit l'emplacement 0.
- À la connexion, aucun événement n'est émis : il faut lire l'emplacement quand le joueur entre dans le monde.
- `InteractionType.Equipped` ne sert qu'aux objets tenus des PNJ (`HY/server/npc/systems/NPCInteractionSystems.java:94`) : ce n'est pas un signal d'équipement d'armure.
- **Asset** : un objet avec `"Armor": {"ArmorSlot": "Head", …}` (clé `ArmorSlot`, `HY/server/core/asset/type/item/config/ItemArmor.java`). Modèle vanilla : `Server/Item/Items/Armor/Cloth_Cotton/Armor_Cloth_Cotton_Head.json` (`Model`, `Texture`, `Recipe`, `CosmeticsToHide`, `StatModifiers`).
- Le zip contient des modèles de lunettes réutilisables : `Common/Cosmetics/Head/Goggles.blockymodel` et `Goggles_Texture.png`, `Common/Cosmetics/Head/WideGoggles.blockymodel`. **[in-game]** Il reste à vérifier qu'un modèle de cosmétique s'attache correctement comme modèle d'armure de tête.

### B.3 Interactions de la baguette

- Types d'interaction : `Primary(0)`, `Secondary(1)`, `Ability1-3(2-4)`, `Use(5)`, `Pick(6)`… (`HY/protocol/InteractionType.java`).
- **Ouvrir une page depuis un objet** : interaction `OpenCustomUI` (`HY/server/core/modules/interaction/interaction/config/server/OpenCustomUIInteraction.java`, enregistrée dans `InteractionModule.java:321`).
  - `OpenCustomUIInteraction.registerSimple(plugin, Class, "Id", playerRef -> page)` enregistre la page.
  - `registerCustomPageSupplier` donne accès au `InteractionContext`, donc à `context.getTargetBlock()`, qui peut être nul.
  - La page ne s'ouvre que si aucune page personnalisée n'est déjà ouverte (l.62).
- **Usage dans le vide** : le kit de réparation vanilla ouvre sa page sur `Primary` **et** `Secondary` sans cibler de bloc (`Server/Item/Items/Tool/Repair_Kit/Tool_Repair_Kit_Crude.json`, `"Type": "OpenCustomUI", "Page": {"Id": "ItemRepair"}`). Le vide est donc bien géré. Le bloc visé, quand il y en a un, sert d'ancre, comme `useOn` de Structurize ; `targetBlock == null` correspond au `use` dans le vide.
- Alternative : `PlayerMouseButtonEvent` (annulable, porte `getItemInHand`, `getTargetBlock`, `getMouseButton`) est émis depuis `InteractionModule.doMouseInteraction` (l.470-489). **[in-game]** On ne sait pas quand le client envoie `MouseInteraction` ; on reste sur les interactions d'objet.
- **Builder tools vanilla** :
  - L'aperçu de collage est **côté client**. Le serveur envoie le presse-papiers comme `EditorBlocksChange` au seul joueur (`BuilderToolsPlugin.java:4189-4193`, `selection.toPacket()`), et le client le fait suivre au curseur.
  - La rotation (`BuilderToolRotateClipboard`, paquet 406) et le collage (`BuilderToolPasteClipboard`, paquet 407) sont des paquets gardés par `hasPermission(… EDITOR_SELECTION_CLIPBOARD)` ou `BUILDER_TOOLS_EDITOR` (`HY/builtin/buildertools/BuilderToolsPacketHandler.java:148-163, 193-251`).
  - Certains outils exigent en plus le mode Créatif (`isSurvivalAllowed`, l.180-185).
  - **Inutilisable pour des joueurs de survie** sans leur donner des pouvoirs d'éditeur de monde.

### B.4 Touches

- Aucun événement serveur de touche brute. Le client envoie `ClientMovement` (`movementStates` : `crouching`, `jumping`, `sprinting`… ; `wishMovement`) (`HY/protocol/packets/player/ClientMovement.java:29-46`, `HY/protocol/MovementStates.java:17-40`).
- Les flèches et les touches de déplacement **déplacent le joueur**. Les détourner rendrait le joueur immobile ou incohérent : ce n'est pas viable.
- L'accroupissement (`crouching`) est lisible et peut servir de modificateur, comme `isShiftKeyDown` des lunettes MC.
- Les pages ont un type de liaison `CustomUIEventBindingType.KeyDown(11)` (`HY/protocol/packets/interface_/CustomUIEventBindingType.java`), mais **aucun usage vanilla**. **[in-game]** On ne sait pas s'il transmet la touche pressée, ni sur quel élément il se déclenche.
- **Solution de repli : les boutons de la page**, comme les boutons de Structurize (flèches bleues, +/−, rotation, miroir, valider, annuler).

## Implications pour HyColony

**Faisable maintenant**
- Lunettes :
  - un objet de casque (`ArmorSlot: Head`, modèle `Cosmetics/Head/Goggles`), avec la recette MC adaptée (le Build Tool comme ingrédient central) ;
  - la détection par `InventoryChangeEvent` sur l'armure, plus une lecture à l'entrée dans le monde ;
  - pour chaque ordre de travail de la colonie la plus proche à moins de **50 blocs** (constante MC `buildgogglerange`), une entité d'aperçu `PrefabPreview` non sauvegardée, visible **seulement des porteurs**, via un filtre dans `FIND_VISIBLE_ENTITIES_GROUP` ;
  - le plan complet, comme MC, construit en mémoire depuis notre plan déjà tourné. Les ordres de démolition et les huttes niveau 0 sans ordre n'ont pas de plan (boîte ou ancre seulement ; voir « À adapter ») ;
  - le rafraîchissement quand le joueur a bougé de plus de 12,5 blocs ou quand la liste des ordres change ;
  - un interrupteur par commande ou par bouton de page, puisque la touche MC n'a pas de défaut.
- Baguette :
  - un objet avec `OpenCustomUI` sur `Primary`/`Secondary`. Sur un bloc, l'ancre est ce bloc + 1 en Y ; dans le vide, on reprend l'aperçu courant ;
  - une page de sélection style → hutte ou décoration → niveau ;
  - des boutons pour déplacer (relatif à la direction du joueur, d'un bloc), monter et descendre, tourner d'un quart de tour, valider et annuler ;
  - les vérifications de `SurvivalHandler` au moment de valider, dans le cœur : `MANAGE_HUTS`, plan entièrement dans la colonie, hôtel de ville assez loin, bloc de hutte pris dans l'inventaire ;
  - pour une hutte : poser le bloc et le bâtiment sans ordre de travail ;
  - pour une décoration : ordre de décoration, en bascule s'il en existe déjà un à cette position.

**À adapter**
- Rotation et miroir : recréer l'aperçu avec le plan transformé côté serveur. On ne compte pas sur la rotation de l'entité avant la vérification **[in-game]**. Le déplacement peut d'abord passer par `setPosition` ; si le client ne suit pas, on recrée l'entité.
- Miroir : le cœur n'a pas de miroir aujourd'hui. `BlockSelection.flip` existe côté Hytale, mais c'est un ajout au modèle de plan.
- Boîtes des lunettes (contour bleu, ancre rouge, bâtisseur vert en étant accroupi) : paquet `DisplayDebug` envoyé au seul joueur **[in-game]**. Sinon, pas de boîte : écart documenté.
- Aperçu privé : c'est le défaut de Structurize (`share_previews=false`), reproduit par notre filtre de visibilité. Sans ce filtre, tout le monde verrait tout.
- Ancre au sol : il n'y a pas de tags de niveau du sol dans nos prefabs Hytale. Il faudra une valeur dans `styles.json`, ou prendre le Y du bloc visé + 1.
- Huttes voisines au niveau max pendant le placement (`NearBuildPreview`) : c'est faisable avec le même mécanisme d'aperçu, mais cela coûte des entités en plus. À faire en second temps.
- Aperçu 3D dans la fenêtre (`PrefabPreviewComponent`) : un bonus possible, sans équivalent MC, **[in-game]**.

**Pas faisable (ou pas souhaitable)**
- Raccourcis clavier de Structurize (flèches, Maj+flèches, M, Entrée, pavé numérique) : pas de capture de touche côté serveur. On passe par les boutons de page, et `KeyDown` reste à tester.
- Réutiliser l'aperçu de collage client des builder tools vanilla : il est réservé aux permissions d'éditeur et au mode Créatif.
- Mise à jour incrémentale des blocs d'un aperçu : il n'existe que le renvoi complet. Pour « seulement le reste », on recrée l'entité par paliers (par étape de construction, par exemple) et jamais à chaque bloc.
- Collage créatif « Complete/Pretty », annuler/refaire, outils de scan et de formes : hors du besoin de survie, optionnels. Le créatif peut rester aux builder tools vanilla de Hytale.
