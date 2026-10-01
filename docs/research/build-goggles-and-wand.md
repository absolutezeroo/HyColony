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
- Collage créatif « Complete/Pretty », annuler/refaire, outils de scan et de formes : hors du besoin de survie, optionnels. Le créatif peut rester aux builder tools vanilla de Hytale. (Le collage créatif est détaillé plus bas, section « Collage créatif ».)

## Collage créatif

Recherche du 2026-09-27. Question : que se passe-t-il quand un joueur en **créatif** valide l'outil de construction ? Aujourd'hui, HyColony n'a pas de collage. Un joueur créatif passe par le chemin de survie, sans rien consommer (Javadoc de `WandPlacement` ; spec `2026-09-26-hycolony-build-tool-design.md`, l.17 et 65).

### C.1 Structurize : les options de placement

**Qui voit quoi** (`ST/client/gui/AbstractBlueprintManipulationWindow.java`) :
- `confirmClicked` (l.148-174). En survie, on cherche les `ISurvivalBlueprintHandler` compatibles. Aucun : son d'erreur. Un seul : il est appliqué directement. Plusieurs : une liste s'ouvre. **En créatif, la liste s'ouvre toujours.**
- `updatePlacementOptions` (l.206-247). Si `player.isCreative()`, deux entrées viennent en tête : `structurize.gui.buildtool.complete` (`HandlerType.Complete`) et `structurize.gui.buildtool.pretty` (`HandlerType.Pretty`). Viennent ensuite les gestionnaires de survie compatibles. Avec MineColonies, un joueur créatif a donc trois choix : Complete, Pretty, et le placement de survie de `SurvivalHandler` (hutte niveau 0, voir A.2).
- **Seule condition : le mode créatif**, testé **côté client**. Il n'y a ni permission d'opérateur ni option de configuration. Le serveur ne revérifie pas le mode : `BuildToolPlacementMessage.onExecute` appelle `BlueprintPlacementHandling.handlePlacement` sans aucun test (`ST/network/messages/BuildToolPlacementMessage.java`, `onExecute` ; `ST/storage/BlueprintPlacementHandling.java`, `process`, l.62-118).
- La fenêtre envoie `(type, handlerId, pack, chemin "…/nom.blueprint", pos, rotation, miroir)` (`ST/client/gui/WindowExtendedBuildTool.java:319-337`). Elle ne se ferme qu'après un placement de survie : `cancelClicked` n'est appelé que pour `Survival`.

**Traitement serveur** (`BlueprintPlacementHandling.process`) :
- `Survival` : le gestionnaire choisi est appliqué, et c'est tout.
- Sinon, un son de succès est joué et la rotation et le miroir sont appliqués au plan. Ensuite :
  - si l'ancre du plan est un `ISpecialCreativeHandlerAnchorBlock` (toutes les huttes MC, par `AbstractBlockHut`), on appelle d'abord `setup(player, world, pos, blueprint, settings, pretty, pack, path)`. S'il renvoie `false`, tout est annulé. Le gestionnaire de structure est alors celui que fournit le bloc ;
  - sinon, le gestionnaire est `new CreativeStructureHandler(world, pos, blueprint, settings, pretty)` ;
  - enfin, `Manager.addToQueue(new PlaceStructureOperation(new StructurePlacer(handler), player))`.

**Complete ou Pretty** (`fancyPlacement = (type == Pretty)`, `ST/placement/handlers/placement/PlacementHandlers.java`) :
- `SubstitutionPlacementHandler` (l.1262-1312) : Complete pose le bloc substitut tel quel. Pretty ne pose rien, et le monde garde ce qu'il a.
- `SolidSubstitutionPlacementHandler` (l.1205-1260) : Pretty le remplace par un bloc solide pris dans le terrain (`getSolidBlockForPos` → `BlockUtils.getSubstitutionBlockAtWorld`). Complete pose le substitut.
- `FluidSubstitutionPlacementHandler` (l.196-286) : Pretty pose le fluide de la dimension (ou met le bloc en `WATERLOGGED`). Complete pose le substitut.
- `GrassPlacementHandler`, `DoorPlacementHandler` et `BlockGrassPathPlacementHandler` diffèrent un peu, dans la comparaison ou dans les objets requis.
- Le wiki dit : « le bouton de gauche colle complètement, y compris les blocs substituts, utile pour concevoir des plans ; celui de droite colle exactement comme si un bâtisseur l'avait construit (sans substituts) » (https://minecolonies.com/wiki/items/sceptergold/).

**Gestionnaire créatif** (`ST/placement/structure/CreativeStructureHandler.java:56-145`) :
- pas d'inventaire ;
- `isCreative() = true` : aucun objet n'est requis ni consommé (`StructurePlacer.handleBlockPlacement`, l.329-374) ;
- `allowReplace() = true` : il remplace ce qui est déjà là ;
- `shouldBlocksBeConsideredEqual = false`.

**La pose est étalée sur plusieurs ticks, pas instantanée.**
- `Manager.onWorldTick` ne traite que **la tête** d'une file globale : `scanToolOperationPool.peek()`, retirée quand `apply` renvoie `true`. Les collages passent donc l'un après l'autre (`ST/management/Manager.java:66-90`).
- `PlaceStructureOperation.apply` (`ST/operations/PlaceStructureOperation.java`) avance en 5 phases :
  - 0 : les blocs qui ne flottent pas ;
  - 1 : les solides « faibles » ;
  - 2 : le retrait de l'eau ;
  - 3 : les non solides ;
  - 4 : les entités.

  À la fin, il appelle `handler.onCompletion()`.
- Chaque appel s'arrête après `getStepsPerCall()` pas (`StructurePlacer.java:127, 193`). Cette valeur vient de la config serveur Structurize **`maxOperationsPerTick` : 1000 par défaut, bornes 0 à 100000** (`ST/config/ServerConfiguration.java:74` ; `CreativeStructureHandler.java:70-73, 111-115`).
- Chaque opération est aussi gardée pour annuler et refaire (`ChangeStorage`, `maxCachedChanges` = 50).

### C.2 MineColonies : collage créatif d'une hutte

Il n'existe ni `CreativeRawStructureHandler` ni gestionnaire créatif dans `core/placementhandlers/main/`, qui ne contient que `SurvivalHandler` et `SuppliesHandler`. Le créatif passe par :
- `MC/api/blocks/AbstractBlockHut.java` : `setup` (l.214-289), `canPaste` (l.296-335) et `getStructureHandler` (l.208-212), qui renvoie un `CreativeBuildingStructureHandler` ;
- `MC/api/util/CreativeBuildingStructureHandler.java` ;
- `MC/core/placementhandlers/HutPlacementHandler.java`, pour le bloc de hutte lui-même.

**Complete.** `setup` sort tout de suite : si `!fancyPlacement && player.isCreative()`, il renvoie `true` (l.226-229). Il n'y a aucune vérification et aucun bâtiment n'est créé : les blocs sont collés bruts, bloc de hutte compris. `HutPlacementHandler.handle` n'appelle `setPlacedBy` qu'en Pretty (`if (placementContext.fancyPlacement())`), donc la hutte collée n'est pas enregistrée. Seule exception : si un bâtiment existait déjà à cette position, `CreativeBuildingStructureHandler.setupBuilding` le retrouve et lui enregistre les blocs.

**Pretty**, dans l'ordre :
1. **Vérifications** (`canPaste`). Elles sont sautées si la config serveur `blueprintbuildmode` (défaut `false`) est active.
   - S'il n'y a pas de colonie à la position, seul l'**hôtel de ville** passe. Les autres huttes sont refusées avec `MESSAGE_WARNING_TOWN_HALL_NOT_PRESENT` si le joueur n'a pas de colonie, sinon avec `MESSAGE_WARNING_TOWN_HALL_TOO_FAR_AWAY`.
   - Dans une colonie, il faut la permission **`PLACE_HUTS`** (et non `MANAGE_HUTS`), sinon `PERMISSION_OPEN_HUT`.
   - Vient ensuite `canPlaceAt` : pour l'hôtel de ville, c'est un refus s'il y en a déjà un (`WARNING_DUPLICATE_TOWN_HALL`, `BlockHutTownHall.java:218-233`).
   - Il n'y a **pas** de test d'emprise dans la colonie, **pas** de test de distance pour l'hôtel de ville, et le bloc de hutte n'est **ni requis ni pris**.
2. **Pose de la hutte.** `world.destroyBlock(pos, true)` casse ce qui est là, avec ses objets. `setBlockAndUpdate(pos, anchor)` pose la hutte. Puis `onBlockPlacedByBuildTool` donne le miroir, le pack et le chemin à l'entité de bloc, et appelle `setPlacedBy`. Celui-ci appelle `addNewBuilding` si une colonie couvre la position (`AbstractColonyBlock.java:249-276`). Le bâtiment reçoit alors le pack et le chemin (`RegisteredStructureManager.addNewBuilding`, l.561-600).
3. **Bâtiment** (l.249-287) :
   - hôtel de ville sans colonie : aucun bâtiment n'est créé, `setup` renvoie `true` et le collage continue ;
   - autre hutte sans bâtiment : son d'erreur et `false`, donc rien n'est collé ;
   - sinon : `setStructurePack`, `setBlueprintPath`, le niveau lu dans le chemin, `setIsMirrored`, puis **`onUpgradeComplete(blueprint, niveau)`**. Cette méthode (`AbstractBuilding.java:940-975`) :
     - réclame les chunks (`claimBuildingChunks` avec `getClaimRadius(niveau)`) ;
     - retire le ruban et recalcule les coins ;
     - passe `isBuilt` à `true` ;
     - annule les requêtes d'outil bloquées ;
     - prévient les modules et relance la recherche automatique ;
     - recalcule le nombre maximal de citoyens (`onBuildingUpgradeComplete`) et le prestige.
4. **Collage** des blocs, étalé sur plusieurs ticks (voir C.1). Pour chaque bloc posé, `triggerSuccess` appelle `building.registerBlockPosition` (`AbstractBuildingContainer.java:148-180` ; `AbstractBuilding.java:1415-1419`) :
   - **les racks deviennent des conteneurs du bâtiment** (`addContainerPosition`, puis `setBuildingPos`) ;
   - une hutte enfant reçoit son parent et le pack ;
   - les modules qui gèrent des blocs externes sont prévenus.
5. **Fin** : `onCompletion` retire le ruban de chantier (`CreativeBuildingStructureHandler.java:176-184`).

**Aucun ordre de travail** n'est créé, et il n'y a ni message de fin ni entrée de journal : `onUpgradeComplete` n'en émet pas. Le message « construit » et le journal viennent de la fin d'un ordre, pas d'un collage.

**Niveau obtenu : incertain à la lecture du code.** Deux mécanismes existent :
- `setup` lit le niveau dans le chemin avec `adjusted.substring(L - 2, L - 1)`, où `adjusted` est le chemin sans `.blueprint` et `L` sa longueur. C'est donc **l'avant-dernier caractère**. Si ce n'est pas un chiffre, le niveau vaut **1** (repli sur `NumberFormatException`). Les fichiers MC s'appellent par exemple `…/fundamentals/townhall3.blueprint` (arbre `src/main/resources/blueprints/minecolonies/…`). L'avant-dernier caractère de `townhall3` est `l` : à la lecture, on obtient donc le niveau 1.
- La synchronisation `upgradeBuildingLevelToSchematicData` (`AbstractSchematicProvider.java:523-565`) prend le **dernier** caractère du nom de plan de l'entité de bloc (`townhall3` → 3). Elle monte le niveau s'il est plus haut, ou le fixe si le bâtiment est « déconstruit ». Elle lance un feu d'artifice si le niveau monte, puis appelle `onUpgradeComplete(null, niveau)`. Elle est déclenchée par `readSchematicDataFromNBT` de l'entité de hutte (`AbstractTileEntityColonyBuilding.java:276-292`), que `CreativeBuildingStructureHandler.triggerSuccess` appelle après `setDeconstructed()` (l.94-129).
- Mais `StructurePlacer.handleBlockPlacement` renvoie `SUCCESS` **sans** appeler `triggerSuccess` quand le monde correspond déjà au plan (l.318-321). De plus, `HutPlacementHandler.doesWorldStateMatchBlueprintState` ne compare que l'état de bloc. Comme `setup` a déjà posé la hutte avec l'état tourné du plan, la synchronisation du bloc ancre semble **ne pas** avoir lieu.
- Conclusion de lecture : le niveau final serait 1 pour la hutte ancre, et le niveau choisi pour les huttes enfants. **[in-game MC]** C'est à confirmer dans MineColonies. L'intention évidente du code est d'appliquer le niveau choisi.

**Hôtel de ville hors colonie.** La hutte est posée et le plan collé, mais aucune colonie n'est créée et rien n'est enregistré : pas de bâtiment, donc pas de conteneurs. Un clic droit sur la hutte ouvre ensuite la création de colonie (`BlockHutTownHall.use` → `GetColonyInfoMessage`). À la création de la colonie, `addNewBuilding` appelle `upgradeBuildingLevelToSchematicData` : l'hôtel de ville prend alors **le niveau du plan collé**, lu dans l'entité de bloc (`RegisteredStructureManager.java:561-575`). Les racks collés avant la fondation ne sont pas enregistrés.

### C.3 HyColony aujourd'hui (lu dans le code)

- `WandActions.confirm` → `WandPlacement.confirm` suit le chemin de survie. Il vérifie `MANAGE_HUTS`, l'emprise, la distance de l'hôtel de ville et les règles de hutte, et prend le bloc de hutte sauf en créatif. La hutte est posée au **niveau 0, sans ordre**, avec son style et sa rotation (`manager.huts().place(colony, type, pos, rotation)`, puis `building.setStyle`). Un hôtel de ville hors colonie lance `manager.foundation().begin(player, name, pos, rotation, style)`.
- En créatif, `WandActions.offered` propose toutes les huttes, comme `AbstractBlockHut.areRequirementsMet`, qui renvoie `true` en créatif.
- Le bâtisseur pose les blocs un à un avec `WorldBlocks.place(pos, state, hasContainer)` (`BuilderBlockWork.place`, l.157-185). Un bloc `hasContainer` est enregistré par `building.addContainer(pos)`, comme les racks de MC.
- `HytaleWorldBlocks.place` (`plugin/.../adapter/HytaleWorldBlocks.java:112-172`) :
  - appelle `testPlaceBlock`, qui refuse de remplacer une hutte ;
  - pose avec `BlockOperations.setBlock(…, SetBlockSettings.NONE)` ;
  - pose les fluides par `FluidSection` ;
  - renvoie `false` si la section n'est pas chargée.
- Les formes connectées (escaliers, coins de toit, barrières) sont posées **telles que le plan les porte** (`blockKey`, l.289-311 ; `docs/research/connected-blocks.md`). `PrefabUtil.paste` fait de même, sans cascade de voisinage.
- Les plans excluent déjà les blocs d'éditeur et l'air sans fluide (`PrefabCells`, l.35-66). Nos prefabs n'ont pas d'équivalent des substituts de Structurize : **Complete et Pretty donneraient les mêmes blocs**.
- Fin d'un ordre (`BuildCompletion.apply`) :
  - `setLevel(cible)`, `setBuilt(true)`, `setDeconstructed(false)` ;
  - `claimAround(pos, ClaimRadius.of(type, niveau))` ;
  - une entrée de journal, et `effects().celebrate` si le niveau monte ;
  - un message aux membres, puis `ColonyEvents.BuildingLevelChanged` ;
  - le retrait de l'ordre.
- Tick : `WorldRuntime.tickCore` appelle `manager.tick()` à chaque tick du cœur (1/20 s).

### C.4 Conception minimale fidèle (proposition)

**Choix à faire valider par l'utilisateur** :
1. Un seul bouton « Coller » (Pretty). Complete est omis, car nos prefabs n'ont pas de substituts : c'est un **écart**, à documenter. Complete porté à l'identique poserait seulement des blocs, sans enregistrer la hutte : on obtiendrait une hutte morte.
2. On applique le **niveau choisi**, qui est l'intention de MC (voir l'incertitude en C.2).

**Cœur** (`app/wand`, 6 fichiers aujourd'hui, 15 au plus) :
- `WandView` reçoit un champ `boolean creative`, que `WandActions.show` remplit avec `players().isCreative(player)`.
- `WandActions.paste(UUID player, String playerName)` refuse (renvoie `false`, rien ne change) si le joueur n'est pas en créatif au moment du clic. Chez MC, la liste est filtrée côté client ; notre fenêtre est côté serveur, donc le test s'y fait. Sinon, il délègue à une nouvelle classe `WandPaste` (package-private), qui suit `AbstractBlockHut.setup` et `canPaste` :
  - si une colonie couvre l'ancre, il faut la permission **`PLACE_HUTS`** (`Action.PLACE_HUTS` existe, niveau 2), sinon c'est un refus ;
  - `manager.huts().checkHutRules(player, pos, type)` : hors colonie, seul l'hôtel de ville passe (`noTownHall`/`tooFar`), et un second hôtel de ville est refusé. Ce contrôle ajoute le test de distance, que MC ne fait qu'à la création de la colonie : c'est un **écart** mineur, à signaler ;
  - pas de test d'emprise, pas de bloc de hutte consommé ;
  - `breakAnchor` et la pose du bloc de hutte (code de `WandPlacement.place`, à factoriser), puis `huts().place(colony, type, pos, rotation)` et `setStyle(style)` ;
  - puis, comme `onUpgradeComplete` : `setLevel(niveau)`, `setBuilt(true)`, `setDeconstructed(false)`, `claimAround(pos, ClaimRadius.of(type, niveau))`, `celebrate` si le niveau monte (le feu d'artifice de `upgradeBuildingLevelToSchematicData` chez MC), `bus().post(BuildingLevelChanged)` (nombre maximal de citoyens…) et `markDirty`. **Ni ordre, ni journal, ni message de fin.** Le plus simple est d'extraire de `BuildCompletion` une méthode publique « le bâtiment atteint le niveau N », partagée par les deux chemins ;
  - hôtel de ville hors colonie : les blocs sont collés, puis `foundation().begin(…)` est appelé. `ColonyFoundation.Pending` doit alors porter le niveau collé, pour que l'hôtel de ville fondé naisse à ce niveau, déjà bâti (chez MC : `addNewBuilding` → `upgradeBuildingLevelToSchematicData`).
- **File de collage** (nouvelle classe, par exemple `PasteQueue`) : l'équivalent de `Manager` et de `PlaceStructureOperation`.
  - Une file FIFO globale, dont seule la tête avance, gardée en mémoire comme chez MC.
  - À chaque tick, au plus **`maxOperationsPerTick`** pas.
  - Ordre de pose :
    1. vider la boîte `StructurePlan.clearList()` des blocs que le plan ne veut pas, car MC remplace tout (`allowReplace`, et le plan contient ses blocs d'air). On utilise `breakBlock` **sans** `drop` : les objets renvoyés sont ignorés, puisqu'on est en créatif ;
    2. poser `solidList` ;
    3. poser `decoList`, qui contient aussi les fluides.

    Chaque liste va du bas vers le haut, comme les phases 0-1 puis 3 de MC.
  - Pour chaque entrée `hasContainer` posée : `building.addContainer(pos)`, si le bâtiment existe.
  - À la fin : rien, car nous n'avons pas de ruban de chantier.
  - Réglage : `maxOperationsPerTick` vient de la config **serveur de Structurize**. Il va dans `config.json`, section `Structurize`, avec 1000 par défaut. Le minimum est 1 et non 0, car avec 0 la file n'avancerait jamais (CLAUDE.md § 4) : c'est un **écart**.
  - Le tick part de `ColonyManager.tick()` ou d'un `WandActions.tick()` appelé par `WorldRuntime.tickCore`.
  - Si la section n'est pas chargée, `place` échoue : on saute le bloc, sans jamais boucler. Le premier échec est journalisé en WARNING, les suivants en FINE.
- Tests (TDD, avec les `Fake*`) :
  - `creativePastePlacesHutAtChosenLevelWithoutWorkOrder` ;
  - `survivalPlayerCannotPaste` ;
  - `pasteNeedsPlaceHutsPermission` ;
  - `pasteRegistersPlacedContainers` ;
  - `pastePlacesAtMostMaxOperationsPerTick` ;
  - `pastedTownHallOutsideColonyFoundsAtPastedLevel`.

**Plugin** :
- `WandPage` : un bouton `#PasteButton`, visible si `view.creative() && view.manipulate()`. Son action `"paste"` appelle `wand.paste(player, playerRef.getUsername())`. Dans `WandPage.ui`, il copie le bouton de validation. *Remplacé le 2026-10-01 : en créatif, Valider ouvre la liste de placement de Structurize, dont « Construit » appelle `wand.paste`.*
- Une clé `hycolony.ui.wand.paste` (en-US et fr-FR). Aucune autre clé n'est nécessaire si l'on réutilise `hycolony.hut.*` et `hycolony.wand.noPermission`.
- `HyColonyConfig` lit `Structurize.maxOperationsPerTick`, et `ColonyConfig` applique les bornes.
- Aucune nouvelle API Hytale : `WorldBlocks.place` et `breakBlock` suffisent.

**Côté Hytale, à vérifier [in-game]** :
- **Coût de 1000 `setBlock` par tick.** `BlockOperations.setBlock` est appelé bloc par bloc, avec `SetBlockSettings.NONE`. Le coût réseau et serveur d'une hutte de quelques milliers de blocs, posée en 2 à 5 ticks, n'est pas mesuré. Repli possible : baisser la valeur par défaut, ce qui serait un écart.
- **Alternative vanilla, non retenue** : `PrefabUtil.paste(IPrefabBuffer, World, Vector3i, Rotation, Random, flags, setBlockSettings, …)` (`HY/server/core/util/PrefabUtil.java:315-500`).
  - Elle colle tout un prefab **en un seul appel synchrone**.
  - Elle émet `PrefabPasteEvent`, restaure les entités de bloc et les entités du prefab, et appelle `testPlaceBlock` pour chaque bloc.
  - Mais elle ne passe pas par notre plan, qui filtre les blocs d'éditeur et les coffres. Elle ne permet ni budget par tick ni enregistrement des conteneurs.
- **Conteneurs.** `place` pose le bloc avec son entité de bloc : le coffre a son conteneur (commentaire l.149-150). Mais les coffres collés sont vides, car `PrefabCells` remplace les coffres des prefabs par un coffre vide. MC, lui, collerait leur contenu (`ContainerPlacementHandler`, `handleTileEntityPlacement`) : c'est un **écart** assumé.
- **Formes connectées** : le collage prend le même chemin que le bâtisseur, donc elles se comportent en jeu comme chez lui.
- **Chunks** : la zone collée est près du joueur, donc chargée. Si l'emprise déborde sur une section non chargée, ces blocs sont perdus **[in-game]**.
