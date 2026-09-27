# Outils du bâtisseur : usure et animation de casse

Recherche du 2026-09-27, suite au test en jeu : (1) les outils du bâtisseur ne perdent jamais de durabilité ; (2) quand il mine, il frappe puis le bloc disparaît, sans fissures progressives.

Sources : MineColonies `version/main` (archive `codeload.github.com/ldtteam/minecolonies/tar.gz/refs/heads/version/main`, chemins sous `src/main/java/com/minecolonies/`), serveur Hytale 0.6.8 décompilé (`build/vineflower/hytale-server/com/hypixel/hytale/`), assets `release-0.6.8-Assets.zip`, et notre code.

## A. Usure des outils

### A.1 MineColonies

- **Quand** : une fois par bloc cassé, jamais pendant l'attente. `AbstractEntityAIInteract.mineBlock` (l. 151) appelle `CitizenItemUtils.breakBlockWithToolInHand` (l. 223), qui passe par `hitBlockWithToolInHand(citizen, pos, true)` (`core/util/citizenutils/CitizenItemUtils.java:153`). Celui-ci enlève le bloc, puis appelle `damageItemInHand(citizen, getUsedItemHand(), 1)` (l. 183). **1 point de dégât par bloc**, quels que soient le bloc et sa dureté. Le builder passe `damageTool = true` (`EntityAIStructureBuilder.mineBlock`, l. 190-192).
- **Main vide** : `damageItemInHand` (l. 211) ne fait rien si l'objet tenu est vide. Quand le bloc ne demande pas d'outil, `holdEfficientTool` vide la main (`AbstractEntityAIBasic.java:1345-1349`, `NO_TOOL`) : rien ne s'use.
- **Chance d'épargne (recherche)** : si l'effet de recherche `TOOL_DURABILITY` (`api/research/util/ResearchConstants.java:139`) a une force `s > 0`, le dégât est sauté quand `random > 1 / (1 + s)` (`CitizenItemUtils.java:221-229`). Niveaux : 0.05, 0.1, 0.25, 0.5, 0.9 (`DefaultResearchProvider.java:106`). **Aucune option de configuration** : `ServerConfiguration` n'a rien sur l'usure des outils. HyColony n'a pas de recherche, donc la force vaut 0 et chaque bloc use l'outil.
- **Enchantements** : `InventoryCitizen.damageInventoryItem` (`api/inventory/InventoryCitizen.java:366-382`) appelle `stack.hurtAndBreak(item.damageItem(...))`. C'est le chemin vanilla de Minecraft, où Unbreaking agit. Hytale n'a pas d'enchantements, donc rien à porter.
- **Casse** : quand la pile est vide après `hurtAndBreak`, le slot est libéré (`freeSlots++`). `damageItemInHand` vide alors la main (`setItemSlot(MAINHAND, EMPTY)`), et le callback `broadcastBreakEvent(hand)` joue le son et les particules vanilla de casse d'objet. **Aucun message de chat.** L'outil est **détruit** : il ne reste pas en inventaire à 0.
- **Après la casse** : au bloc suivant, `checkMiningLocation` → `holdEfficientTool` ne trouve plus d'outil. `requestTool` (`AbstractEntityAIBasic.java:1351-1361`) passe par `checkForToolOrWeapon` (l. 980-1004) : outil de la hutte, sinon requête `Tool` si aucune n'est ouverte, et statut `STUCK`. La seule « annonce » est donc la requête d'outil habituelle.
- Détail sans effet : après la casse du bloc, `mineBlock` appelle aussi `tool.getItem().inventoryTick(...)` (l. 232). Ce n'est pas un dégât.

### A.2 Hytale 0.6.8 (joueur)

- `ItemStack` porte `durability` et `maxDurability` (double). API vérifiée dans `server/core/inventory/ItemStack.java` :
  - `ItemStack(String, int)` (l. 134) : durabilité au maximum de l'objet ;
  - `ItemStack(String id, int qty, double durability, double maxDurability, BsonDocument metadata)` (l. 119) ;
  - `getDurability()` (l. 232), `getMaxDurability()` (l. 220), `isUnbreakable()` (`maxDurability <= 0`, l. 192), `isBroken()` (`durability == 0`, l. 208) ;
  - `withDurability(double)` (l. 369, bornée à `[0, max]`), `withIncreasedDurability(double)` (l. 434).
- **Perte par coup** : `BlockHarvestUtils.calculateDurabilityUse` (`server/core/modules/interaction/BlockHarvestUtils.java:888-935`) prend l'entrée `DurabilityLossBlockTypes` qui contient le bloc ou son set, sinon `Item.getDurabilityLossOnHit()`. Un bloc « soft » ne coûte rien. Elle est appliquée à chaque coup par `applyItemDurabilityLoss` (l. 1299-1314) → `ItemUtils.updateItemStackDurability` (`server/core/entity/ItemUtils.java:303-322`).
- **Casse chez Hytale** : l'objet **reste** à durabilité 0. Le joueur reçoit le message `server.general.repair.itemBroken` et le son 2D `SFX_Item_Break` (ItemUtils l. 317-321). Un outil cassé mine avec une pénalité de puissance (`BrokenPenalties`, BlockHarvestUtils l. 1068-1071). **Écart avec MC**, qui détruit l'outil. Le port garde la règle de MC : l'outil disparaît.
- Chiffres vanilla (assets) : `Tool_Pickaxe_Crude` a `MaxDurability` 150, `DurabilityLossOnHit` 0.25 sur les sets Stone/Rock/Ores/Soil/Wood et `Power` 0.25 sur Rocks. `Tool_Pickaxe_Iron` a 250, 0.25 et 0.5. Un bloc de pierre coûte donc au joueur `ceil(1/power)` coups × 0.25 : 1.0 pour Crude (150 blocs), 0.5 pour Iron (500 blocs).

### A.3 HyColony aujourd'hui

- **L'usure existe déjà, mais elle est invisible.** `BuilderBlockWork.breakBlock` (`core/.../construction/builder/BuilderBlockWork.java:150-153`) appelle `BuilderJob.wear(tool, catalog.durability(tool))`. Ce compteur est **par ItemKey**, pas par outil : `toolUses`, sauvegardé dans le job (`BuilderJob.java:23, 38-47, 53-66`). À `durability` usages, il retire l'outil (`inventory().extract(tool, 1)`). `HytaleItemCatalog.durability` (`plugin/.../adapter/HytaleItemCatalog.java:265-280`) vaut `max / (lossPerHit × ceil(1/power))`, soit 150 blocs pour Crude et 500 pour Iron. C'est l'équivalent fidèle du « 1 dégât par bloc » de MC, en unités Hytale.
- **Pourquoi le joueur ne voit rien** :
  1. `Inventory` ne stocke que `ItemAmount(item, count)` (`kernel/item/Inventory.java:12-15`) ;
  2. la fenêtre du citoyen recrée `new ItemStack(id, count)`, donc à durabilité pleine (`plugin/.../ui/citizen/CitizenItemContainer.java:55`) ;
  3. il faut 150 blocs de pierre pour casser une pioche Crude.
- **Réparation gratuite** : chaque transfert recrée l'objet neuf, et le compteur `toolUses` n'est pas lié à l'objet (il continue sur l'outil suivant du même id). Chemins concernés :
  - `HytaleContainerAccess.takeBySlot` prend par id, sans regarder la durabilité (l. 151-165), et `insert` fait `new ItemStack(id, n)` (l. 168) ;
  - `HytalePlayerInventory.take` (même `takeBySlot`) ;
  - `HytaleBlocks.drop` (l. 67) ;
  - `HytaleWorldBlocks.breakBlock` convertit en `ItemAmount` le contenu d'un conteneur cassé (l. 233-237).
- La fenêtre du citoyen **refuse** un outil entamé (`CitizenItemContainer.cantAddToSlot`, l. 100-108 ; `docs/TESTING.md` étape 78) pour ne pas le réparer.
- Choix de l'outil : `BuilderStock.toolInInventory(type)` (l. 152-169) prend l'outil de plus bas niveau dans la limite de la hutte (MC `getMostEfficientTool`), par ItemKey. `toolInHut` fait de même. `KeepToolsModule` et `HutKeep` raisonnent par ItemKey et par type (`HutKeep.java:73-75` : « better equipment » non porté).

### A.4 Changement minimal du modèle (proposition)

1. **`ItemAmount`** gagne un champ `int damage` (≥ 0, en « usages » MC : 1 par bloc), avec un constructeur à 2 arguments qui met 0. C'est l'équivalent de `ItemStack.getDamageValue` dans MC. Les outils ont `maxStack` 1 (plugin-b-api § 2), donc un outil occupe toujours un slot à lui. `Inventory.merge` ne fusionne que des piles de `damage` égal (sans effet sur les outils, mais cela reste correct).
2. **Catalogue** : `ItemCatalog.durability(item)` existe déjà (usages avant casse ; 0 = incassable). Rien à ajouter.
3. **Usure** : `Inventory.damage(int slot, int amount, int durability)` renvoie `true` si l'outil casse (slot vidé), comme MC `damageInventoryItem`. `BuilderBlockWork` use le slot de l'outil tenu, qui doit donc devenir un slot et plus une ItemKey (MC tient un slot : `setHeldItem(hand, slot)`, `CitizenItemUtils.java:104-116`). `BuilderJob.toolUses` est supprimé.
4. **Persistance** : `Inventory.write` n'écrit `"damage"` que s'il est > 0, et `read` prend 0 si la clé manque. La lecture reste tolérante (§ 5). Pour suivre la lettre du § 5 (« toute évolution passe par `MigrationChain` »), une migration v2→v3 peut reporter `job.toolUses[id]` sur le premier slot qui contient cet outil, puis supprimer `toolUses`, avec une fixture v2. Sans migration, on perd seulement l'usure accumulée avant la mise à jour.
5. **Aller-retour Hytale (plugin)** : `perUse = maxDurability / catalog.durability(id)`.
   - Vers Hytale : `new ItemStack(id, n).withDurability(max - damage × perUse)`.
   - Depuis Hytale : `damage = ceil((max - getDurability()) / perUse)`. On arrondit vers le haut, pour ne jamais réparer. Une pile incassable (`isUnbreakable()`) donne 0. (ponytail : conversion avec arrondi. Pour Crude, `perUse` = 1 et le résultat est exact. Pour Iron, un seul coup de joueur (0.25) compte comme un usage entier.)
   - Un outil Hytale **cassé** (`isBroken()`, durabilité 0) donne `damage == durability`. Le cœur ne doit pas le choisir comme outil : filtre dans `toolInInventory` / `toolInHut`.
6. **Ports à changer** (seule la durabilité doit traverser ; les compteurs `count`, `contents` et `freeSlots` ne changent pas) :
   - `ContainerAccess.extract(..., item, max)` renvoie un `int`, ce qui perd la pile. Il faut une variante qui renvoie les `List<ItemAmount>` réellement prises. `insert(ItemAmount)` et `stacks(container)` portent déjà une `ItemAmount` : il suffit de remplir `damage` ;
   - `PlayerInventory.take` : même problème (`int`) ;
   - `WorldBlocks.breakBlock`, `WorldBlocks.drop` : déjà en `ItemAmount`.
7. **Fenêtre du citoyen** : `internal_getSlot` construit la pile avec sa durabilité, et le cache compare aussi `damage`. `internal_setSlot` lit `damage` depuis la pile. Le refus des outils entamés dans `cantAddToSlot` disparaît (les métadonnées restent refusées), et `docs/TESTING.md` étape 78 change.

### A.5 Endroits touchés (appels qui déplacent des objets, `grep` du cœur)

- **Bâtisseur** : `BuilderStock` (l. 72-80 prise dans la hutte, 134-151 dépôt et gouttes), `BuilderBlockWork` (usure), `BuilderGathering`.
- **Coursier** :
  - `PickupRound` (l. 98-115, `stacks` puis `extract` par id) ;
  - `DeliveryPreparation` (l. 115-119) ;
  - `DeliveryDrop` (l. 82) ;
  - `ForcedInsert` (l. 25-56, sortie et remise d'une pile locale) ;
  - `CourierContext` (l. 113).
- **Entrepôt** : `WarehouseStorage` (l. 45, insertion de la pile du coursier : `damage` suit), `WarehouseStockResolver` (compte seulement).
- **Fourniture par le joueur** : `RequestActions` (l. 58, 95-98, 115-119, 163 : `playerInventory().take` → `int`).
- **Ramassage (`HutKeep` / `KeepToolsModule`)** : les règles de garde restent par ItemKey. MC ne regarde pas l'usure pour garder un outil (`keepX` par niveau d'équipement), et « better equipment » n'est pas porté (`HutKeep.java:73-75`). Un effet de bord suffit : si le coursier enlève un outil entamé, il doit le transporter avec son `damage`.
- **Vues** : `CitizenViews`, `BuilderResourcesViews`, `RequestViews` comptent seulement, rien à changer.
- **Divers** : `WandActions` et `WandPlacement` (objets posés, non outils), la commande `HyColonyCommand:292`, et les gouttes à la mort (`WorldBlocks.drop`).

## B. Animation de casse

### B.1 MineColonies

- **Délai** : `AbstractEntityAIInteract.calculateWorkerMiningDelay` (l. 334-341) donne `(int)((500 × 0.85^(skill/2) × hardness / toolDestroySpeed) × (1 - rechercheBLOCK_BREAK_SPEED))`, avec `BLOCK_MINING_DELAY = 500` (l. 100) et `LEVEL_MODIFIER = 0.85` (l. 54). L'option serveur `pvp_mode` donne `BLOCK_MINING_DELAY / 2` (l. 322-324). Le builder multiplie par `SPEED_BUFF_0 = 0.5` (`EntityAIStructureBuilder.java:45, 279-282`). Notre `BuilderTimings.breakDelay` reprend tout cela, sauf la recherche (absente) et `pvp_mode` (non vérifié ici).
- **Pendant le délai, pas de fissures** : MC n'appelle **jamais** `level.destroyBlockProgress` pour les ouvriers. Le seul appel du dépôt est `EntityAIBreakDoor.java:80` (mobs de raid, remise à -1). À chaque tick d'IA (`ENTITY_AI_TICKRATE = 5`, `AbstractEntityCitizen.java:64`), `waitingForSomething` (`AbstractEntityAIBasic.java:497-516`) appelle `hitBlockWithToolInHand(worker, currentWorkingLocation)` si le citoyen est à 4 blocs au plus (`DEFAULT_RANGE_FOR_DELAY = 4`, `CitizenConstants.java:161`). Cet appel :
  - fait regarder le bloc et balancer le bras ;
  - envoie des **particules de coup** : `BlockParticleEffectMessage` avec la face, rendu `particleEngine.crack(pos, face)`, les petits éclats, **pas** la texture de fissure (`BlockParticleEffectMessage.java:76-82`), à 16 blocs (`BLOCK_BREAK_PARTICLE_RANGE`, `CitizenConstants.java:76`) ;
  - joue le **son de coup** du bloc, volume `(v + 1) × 0.125`, hauteur `p × 0.5` (`CitizenItemUtils.java:199-202`).
- **À la casse** : particules de destruction (`BREAK_BLOCK` → `particleEngine.destroy`) à 16 blocs (`BLOCK_BREAK_SOUND_RANGE`, `CitizenConstants.java:72`), son de casse `(v + 1) × 0.5`, `p × 0.8` (l. 171-180), `WorldUtil.removeBlock`, puis l'usure.
- **Conclusion** : dans MC, le citoyen ne montre pas de fissures progressives, seulement coups, éclats et sons. Le « rendu comme un joueur » serait donc un **ajout** (`Deviation from MC:` à documenter), rendu possible par Hytale.

### B.2 Hytale 0.6.8

- **Santé des blocs côté serveur** : le composant `BlockHealthChunk` (par colonne de chunk, `server/core/modules/blockhealth/`) garde une `BlockHealth` de 1.0 (intact) à 0 (détruit).
  - `damageBlock(Instant now, World world, Vector3i pos, float amount)` (`BlockHealthChunk.java:67-82`) retire `amount` et envoie `UpdateBlockDamage(pos, health, -amount)` **à tous les joueurs** qui ont le chunk (`WorldNotificationHandler.updateBlockDamage`, l. 92-98). Il ne casse pas le bloc.
  - `removeBlock(world, pos)` (l. 95-98) remet l'affichage à 1.0.
- **Paquet** : `protocol/packets/world/UpdateBlockDamage` (id 144, `BlockPosition`, `damage`, `delta`). Le client dessine les décalques `Server/Item/Block/BreakingDecals/Breaking_Decals_{Rock,Soil,Wood}.json` (7 textures `BlockTextures/Cracks/T_Crack_*_0N.png`). Le rendu est donc piloté par le **serveur**. Côté client, seules la prédiction de l'interaction et les effets de l'outil (`Pickaxe_Mine_Effect`) restent locaux. **[in-game]** : la correspondance santé → étape et le rôle de `delta` (interpolation ?) ne sont pas visibles dans le serveur.
- **Chemin du joueur** : `BlockHarvestUtils.damageSingleBlock`. Dégât = `specPower` de l'outil (l. 1079), sinon 1.0 (l. 1123). Puis `blockHealthComponent.damageBlock(timeResource.getNow(), ...)` (l. 1176). S'il reste de la santé, `playBlockSound(type, BlockSoundEvent.Hit, centre)` (l. 1249) : le son de coup est **serveur**. Aucune particule `Hit` n'est envoyée par le serveur pour un coup de joueur (le seul `sendBlockParticle` est dans `BlockOperations` et `BreakFallingBlockImpact`). Les éclats de coup du joueur sont donc locaux au client.
- **Régénération** : `BlockHealthModule` (l. 230-232) soigne un bloc **5 s** après son dernier dégât, à **0.1/s**, et renvoie `UpdateBlockDamage` à chaque pas. Un chantier abandonné ne laisse donc pas de fissure pour toujours.
- **Particules et son manuels** : `world.getNotificationHandler().sendBlockParticle(x, y, z, blockId, BlockParticleEvent.Hit)` (l. 84 ; `protocol/BlockParticleEvent` : `Hit(6)`, `Break(7)`, `Build(8)`). Pour le son de coup : `BlockSoundSet.getAssetMap().getAsset(type.getBlockSoundSetIndex()).getSoundEventIndices().getOrDefault(BlockSoundEvent.Hit, 0)`, puis `SoundUtil.playSoundEvent3d(index, SoundCategory.SFX, Vector3d, ComponentAccessor<EntityStore>)` (`universe/world/SoundUtil.java:272`), le même motif que `BlockHarvestUtils.playBlockSound` (l. 1356-1365). **[in-game]** : que le client affiche bien `Hit` quand le serveur l'envoie.
- **À la casse, c'est déjà fait** : notre `HytaleWorldBlocks.breakBlock` (`plugin/.../adapter/HytaleWorldBlocks.java:222-232`) appelle `naturallyRemoveBlock` avec `SetBlockSettings.NO_DROP_ITEMS` (2048) seul.
  - Le bit `NO_SEND_AUDIO` (1024) est absent : **son de casse** joué (`BlockHarvestUtils.java:636-646`) ;
  - le bit `NO_SEND_PARTICLES` (4) est absent : `BlockOperations.setBlock` → `spawnBlockParticles(..., Break)` (`BlockOperations.java:71-73, 369-379`) : **particules de casse** envoyées ;
  - `removeBlock` appelle `BlockHealthChunk.removeBlock` (`BlockHarvestUtils.java:1317-1330`) : **fissure effacée**.

  **[in-game]** : confirmer que le son et les particules de casse se voient déjà.
- Son de casse d'outil (MC `broadcastBreakEvent`) : l'événement `SFX_Item_Break` existe (`Server/Audio/SoundEvents/SFX/UI/SFX_Item_Break.json`), via `TempAssetIdUtil.getSoundEventIndex(String)` (`server/core/util/TempAssetIdUtil.java:19`). C'est un son d'interface (2D). **[in-game]** : son rendu en 3D.

### B.3 HyColony aujourd'hui

- `BuilderBlockWork.mine` fait un premier passage `startBreaking` (regard, outil en main, `startDelay(breakDelay, MINE)`) et un second `breakBlock` (l. 69-100, 121-162).
- `BuilderGestures.waiting` (`construction/builder/BuilderGestures.java`) rejoue le coup `Pickaxe/Mine` tous les `MINE_ANIMATION_TICKS = 6` ticks (écart documenté, MC frappe tous les 5 ticks d'IA) et décompte `delay` par `MACHINE_RATE = 5`.
- Il manque donc, par coup : le son de coup et les éclats (MC), et, en ajout, la fissure.
- Le port `WorldEffects` (`kernel/port/WorldEffects.java`) n'a que `celebrate`. `HytaleWorldEffects` n'utilise que `ParticleUtil`.

### B.4 Conception minimale proposée

- Nouvelle méthode du port visuel : `WorldEffects.blockHit(BlockPos pos, float progress)`, où `progress` va de 0 à 1 et vaut `1 - delay restant / délai total`. Elle ne lève jamais d'exception (§ 4). Sa Javadoc cite MC `CitizenItemUtils.hitBlockWithToolInHand(citizen, pos, false)`.
- `BuilderGestures` garde la cible et le délai total d'un minage, et appelle `blockHit` à chaque coup rejoué (tous les 6 ticks, sur le coup déjà émis). Placement : rien, MC ne frappe pas pendant la pose.
- `HytaleWorldEffects.blockHit` :
  1. son `BlockSoundEvent.Hit` du bloc, joué en 3D au centre ;
  2. `sendBlockParticle(..., BlockParticleEvent.Hit)` ;
  3. fissure (ajout, `Deviation from MC:`). Via `BlockHealthChunk.damageBlock(now, world, pos, courant - cible)`, avec cible `= max(ε, 1 - progress)` pour ne jamais atteindre 0 : c'est `breakBlock` qui casse, au délai de MC. Le bloc cassé passe par `naturallyRemoveBlock`, qui efface la fissure. Un minage interrompu guérit tout seul en 5 à 15 s. Obtenir le composant : `ChunkSection.getChunkColumnReference()` de la section, puis `BlockHealthModule.get().getBlockHealthChunkComponentType()`. Le temps : `world.getEntityStore().getStore().getResource(TimeResource.getResourceType()).getNow()`.
- Autre solution, sans état : `world.getNotificationHandler().updateBlockDamage(x, y, z, 1 - progress, delta)` seul. Mais `naturallyRemoveBlock` n'efface la fissure que si `BlockHealthChunk` a une entrée (`removeBlock`, l. 95-98). Il faudrait donc un reset explicite (santé 1.0) à la casse et à chaque abandon, et un joueur qui arrive pendant le minage ne verrait rien. Déconseillée.
- Effet de bord de `damageBlock` : la santé est partagée, donc un joueur qui frappe le même bloc le finit plus vite. C'est acceptable, et cela reste purement cosmétique pour le cœur.
- Casse de l'outil (§ A) : son `SFX_Item_Break` au citoyen, facultatif. MC n'envoie aucun message.
