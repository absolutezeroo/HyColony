# Fenêtre d'inventaire du citoyen (conteneur réel)

Question : comment laisser un joueur prendre et déposer des objets dans l'inventaire d'un citoyen par une vraie fenêtre de conteneur, comme l'onglet Inventaire de MineColonies (`OpenInventoryMessage` / `ContainerCitizenInventory`) ?

Sources : serveur décompilé `build/vineflower/hytale-server/com/hypixel/hytale/` (abrégé `hs/` ci-dessous), MineColonies `version/main` (abrégé `MC/` = `raw.githubusercontent.com/ldtteam/minecolonies/version/main/src/main/java/com/minecolonies/`). Vérifié le 2026-09-26.

## 1. Ce que fait MineColonies

- `MC/core/network/messages/server/colony/OpenInventoryMessage.java` l. 139-151 (`doCitizenInventory`) : récupère l'entité par son id dans le monde du joueur (`getEntity(entityID)`) ; **si l'entité n'est pas chargée, rien ne s'ouvre**. Sinon `NetworkHooks.openScreen(player, citizen, …)`.
- Permission : le message ne redéfinit pas `permissionNeeded()`, donc la valeur par défaut de `MC/core/network/messages/server/AbstractColonyServerMessage.java` l. 61-63 s'applique : **`Action.MANAGE_HUTS`**. Refus : message `TOOL_PERMISSION_SCEPTER_PERMISSION_DENY` (l. 122-130).
- `MC/api/inventory/container/ContainerCitizenInventory.java` :
  - les slots pointent **directement** sur `data.getInventory()` (l. 150-190, `SlotItemHandler(inventory, index, …)`) : le joueur et l'IA partagent le même objet, en direct ;
  - quand le joueur pose une pile dans un slot et que le citoyen a un bâtiment de travail, `set` appelle `building.overruleNextOpenRequestOfCitizenWithStack(citizenData, stack)` (l. 176-186) : la pile peut « satisfaire » la prochaine requête ouverte du citoyen (`MC/core/colony/buildings/AbstractBuilding.java` l. 1798 et suivantes) ;
  - 4 slots d'armure en plus (l. 193-196), hors sujet ici ;
  - `stillValid` renvoie toujours `true` (l. 337-340) : **aucune distance ni validité d'entité**, la fenêtre reste ouverte si le joueur s'éloigne ou si le citoyen bouge.

## 2. Ouvrir une fenêtre de conteneur sans bloc

- `hs/server/core/entity/entities/player/windows/ContainerWindow.java` : `public class ContainerWindow extends Window implements ItemContainerWindow`, constructeur `public ContainerWindow(@Nonnull ItemContainer itemContainer)` (type `WindowType.Container`), `onOpen0` renvoie `true`, `onClose0` vide. **Il enveloppe n'importe quel `ItemContainer`**, sans bloc ni distance. Classe publique non finale : on peut la sous-classer.
- Exemples vanilla (les seuls `new ContainerWindow(`) :
  - `hs/server/core/command/commands/player/inventory/InventorySeeCommand.java` l. 55-80 : `/inv see <joueur>` ouvre **l'inventaire d'une autre entité** : `InventoryComponent.getCombined(targetStore, targetRef, HOTBAR_FIRST)`, enveloppé en lecture seule dans un `DelegateItemContainer` avec `setGlobalFilter(FilterType.DENY_ALL)` sans la permission `invsee.modify`, puis `setPageWithWindows(ref, store, Page.Bench, true, new ContainerWindow(c))`. C'est le modèle le plus proche.
  - `hs/server/core/command/commands/player/inventory/InventoryItemCommand.java` l. 63 : même appel sur un sac à dos.
- `ItemStackContainerWindow` (`…/windows/ItemStackContainerWindow.java`) ne prend qu'un `ItemStackItemContainer` (conteneur rangé dans un objet, ex. sac) : pas adapté.
- `ContainerBlockWindow`/`BlockWindow` exigent un bloc (`BlockWindow.validate` l. 164-188 compare la distance au bloc, 7 blocs par défaut, et le type du bloc).
- `PageManager` (`hs/server/core/entity/entities/player/pages/PageManager.java`) :
  - `public boolean setPageWithWindows(Ref<EntityStore> ref, Store<EntityStore> store, Page page, boolean canCloseThroughInteraction, Window... windows)` (l. 165) : ouvre les fenêtres via `WindowManager.openWindows`, renvoie `false` si l'une refuse (`onOpen0` → `false`), sinon envoie `SetPage` puis les `OpenWindow` ;
  - `public boolean openCustomPageWithWindows(Ref<EntityStore>, Store<EntityStore>, CustomUIPage, Window...)` (l. 208) : pareil avec une page `.ui` à nous. **[in-game]** Disposition inconnue côté client (déjà noté dans `plugin-b-api.md` § 8).
  - `Page` : `None, Bench, Inventory, ToolsSettings, Map, MachinimaEditor, ContentCreation, Custom, Serverside` (`hs/protocol/packets/interface_/Page.java`). Vanilla utilise `Page.Bench` pour tout conteneur.
- Aucun code vanilla n'ouvre l'inventaire d'un **PNJ** pour un joueur (recherche de `new ContainerWindow(`, `ItemContainerWindow`, `getCombined` dans `server/npc` et `builtin`). `BarterPage` n'affiche que l'inventaire du joueur.

## 3. Mécanique des fenêtres (vérifiée)

- `WindowManager.setWindow0` (`…/windows/WindowManager.java` l. 240-253) : pour un `ItemContainerWindow`, s'abonne à `itemContainer.registerChangeEvent(EventPriority.LAST, e -> markWindowChanged(id))`. Tout changement du conteneur, **y compris programmatique**, marque la fenêtre sale ; `updateWindows()` (appelé par `PlayerSendInventorySystem` l. 120) renvoie alors tout le contenu au client (`UpdateWindow` avec `getItemContainer().toPacket()`).
- `public void markWindowChanged(int id)` (l. 400) est public : on peut forcer le renvoi quand le contenu change sans passer par `sendUpdate`.
- `ItemContainer.sendUpdate` (`hs/server/core/inventory/container/ItemContainer.java` l. 1409-1415) déclenche **synchronement** les abonnés `registerChangeEvent` à chaque transaction réussie, quelle qu'en soit l'origine (joueur ou code).
- Déplacements d'objets : `InventoryPacketHandler.handle(MoveItemStack)` (`hs/server/core/io/handlers/game/InventoryPacketHandler.java` l. 390-398) exécute `InventoryUtils.moveItem` dans `world.execute(...)` : **thread du monde**. `InventoryUtils.getSectionById` (`hs/server/core/inventory/InventoryUtils.java` l. 560-585) résout un id ≥ 0 comme id de fenêtre ouverte et, si la fenêtre est un `ValidatedWindow` qui échoue, **la ferme et refuse le déplacement**.
- `ValidatedWindow` (`boolean validate(Ref<EntityStore>, ComponentAccessor<EntityStore>)`) n'est testé que : à chaque déplacement d'objet (ci-dessus), à chaque `SendWindowAction` (`GamePacketHandler` l. 690-700), et sur téléportation (`Player.moveTo` l. 321, `TeleportSystems` l. 350). **Pas de test périodique** : un joueur qui s'éloigne sans toucher la fenêtre la garde ouverte.
- Fermeture :
  - client (Échap) : `GamePacketHandler.handleCloseWindow` l. 786-791 → `WindowManager.closeWindow` ;
  - joueur retiré du store (déconnexion, changement de monde) : `PlayerSystems.PlayerAddedSystem.onEntityRemove` l. 463-469 → `closeAllWindows` ;
  - code : `Window.close(ref, accessor)` → `manager.closeWindow(ref, id, accessor)`. **Piège** : `closeWindow` lève `IllegalStateException("Window id … is invalid!")` si la fenêtre est déjà fermée (l. 346-367). Vérifier `playerComponent.getWindowManager().getWindow(w.getId()) == w` avant de fermer soi-même.
  - dans tous les cas `Window.onClose` appelle `onClose0` puis les abonnés `registerCloseEvent(Consumer<Window.WindowCloseEvent>)` (`Window.java` l. 95-101).

## 4. Le PNJ peut-il porter l'inventaire ?

- Oui techniquement : `NPCSystems.OnNPCAdded.onEntityAdd` (`hs/server/npc/systems/NPCSystems.java` l. 509-531) ajoute à **tout** PNJ `InventoryComponent.Storage` (capacité 0), `Armor`, `Hotbar` (3) et `Utility`. `Role.initialiseInventories` (`hs/server/npc/role/Role.java` l. 1507-1560) les agrandit selon les clés de rôle `InventorySize` et `HotbarSize` (`hs/server/npc/role/builders/BuilderRole.java` l. 324, 334).
- Mais c'est **déconseillé** comme stockage de vérité :
  - le cœur (`CitizenData.inventory()`, `core/.../kernel/item/Inventory.java`, 27 slots) est persisté par HyColony ; le corps PNJ est jetable (`HytaleCitizenBodies.despawn` → `removeEntity`, respawn à la demande) : deux vérités, perte ou duplication au respawn ;
  - à la mort, `NPCDamageSystems` l. 205-225 vide `Storage` au sol si `PickupDropOnDeath` est vrai (`BuilderRole` l. 726) : comportement vanilla qui court-circuite le cœur ;
  - l'inventaire n'existe que si le chunk du citoyen est chargé.
- Conclusion : garder le cœur comme unique vérité et exposer un `ItemContainer` **adossé** au cœur, créé à l'ouverture.

## 5. Approche recommandée : un conteneur qui lit et écrit directement le cœur

Pour reproduire MC (« même conteneur, en direct »), le plus sûr est un `SimpleItemContainer` dont les accès de slot passent par l'inventaire du cœur, plutôt qu'une copie synchronisée.

Pourquoi pas une copie `SimpleItemContainer` remplie à l'ouverture et recopiée à la fermeture ou à chaque changement : entre deux synchronisations, l'IA peut retirer des objets d'un slot que le client voit encore plein ; le déplacement du joueur (traité sur le même thread mais **après** le changement de l'IA) s'appliquerait à la copie périmée et **dupliquerait** des objets. Une copie ne marche qu'avec une fusion par slot à trois voies, plus complexe et plus fragile.

`SimpleItemContainer` (`hs/server/core/inventory/container/SimpleItemContainer.java`) : classe publique, constructeur `public SimpleItemContainer(short capacity)` (l. 87), méthodes protégées surchargeables `internal_getSlot(short)`, `internal_setSlot(short, ItemStack)`, `internal_removeSlot(short)`, `internal_clear()`, `cantAddToSlot(short, ItemStack, ItemStack)` ; `isEmpty()` s'appuie sur le compteur privé `itemsCount` (à surcharger). `ItemContainer.getItemStack`, `forEach`, `toPacket` et `isEmpty` passent tous par `internal_getSlot` (`ItemContainer.java` l. 110-126, 201-204, 1226-1235, 1267-1274).

Esquisse (plugin, à adapter aux règles du projet) :

```java
/** A citizen's core inventory seen as a Hytale container; every slot read and write goes to the core. */
final class CitizenItemContainer extends SimpleItemContainer {
    private final Supplier<Inventory> core;      // CitizenData::inventory, re-read: setInventory may replace it
    CitizenItemContainer(Supplier<Inventory> core) { super((short) core.get().size()); this.core = core; }

    @Override protected ItemStack internal_getSlot(short slot) {
        ItemStack cached = items[slot];
        Optional<ItemAmount> a = core.get().slot(slot);
        if (a.isEmpty()) { return null; }
        if (cached == null || !cached.getItemId().equals(a.get().item().id()) || cached.getQuantity() != a.get().count()) {
            cached = new ItemStack(a.get().item().id(), a.get().count());
            items[slot] = cached;                 // cache only, to keep reads cheap
        }
        return cached;
    }
    @Override protected ItemStack internal_setSlot(short slot, ItemStack s) {
        ItemStack prev = internal_getSlot(slot);
        core.get().set(slot, ItemStack.isEmpty(s) ? null : new ItemAmount(ItemKey.of(s.getItemId()), s.getQuantity())); // core API to add
        items[slot] = ItemStack.isEmpty(s) ? null : s;
        return prev;
    }
    @Override protected ItemStack internal_removeSlot(short slot) { return internal_setSlot(slot, null); }
    // internal_clear(): same for every slot; isEmpty(): core.get().contents().isEmpty()
    @Override protected boolean cantAddToSlot(short slot, ItemStack s, ItemStack cur) {
        // Deviation from MC: the core keeps no item metadata, so a worn tool or an item with metadata is refused.
        return s.getMetadata() != null || (!s.isUnbreakable() && s.getDurability() < s.getMaxDurability())
                || super.cantAddToSlot(slot, s, cur);
    }
}
```

Ouverture (même motif que `HutStorage` et `InventorySeeCommand`) :

```java
CitizenItemContainer c = new CitizenItemContainer(() -> citizen.inventory());
ContainerWindow w = new CitizenInventoryWindow(c, colonyId, citizenId);   // extends ContainerWindow implements ValidatedWindow
if (player.getPageManager().setPageWithWindows(ref, store, Page.Bench, true, w)) {
    w.registerCloseEvent(e -> openWindows.remove(playerUuid, w));
}
```

Points à tenir :

1. **API du cœur à ajouter** : `Inventory` n'a pas de setter de slot (seulement `insert`, `extract`, `slot`) ; il faut un `set(int slot, ItemAmount|null)` (test d'abord). Vérifier aussi `ItemKey`/`ItemAmount` : `ItemAmount` refuse-t-il un compte ≤ 0 ?
2. **Renvoi au client quand l'IA change l'inventaire** : aucune transaction Hytale n'a lieu, donc la fenêtre n'est pas marquée sale. Soit un compteur de version dans `Inventory` lu à chaque tick de colonie pour chaque fenêtre ouverte, soit un rappel ; puis `player.getWindowManager().markWindowChanged(w.getId())`. Le serveur, lui, n'est jamais périmé (lecture directe du cœur) : au pire le client voit un état vieux d'un tick et le déplacement est refusé ou partiel, comme dans MC.
3. **Règle MC au dépôt** : reproduire `overruleNextOpenRequestOfCitizenWithStack` en appelant une action du cœur depuis un abonné `registerChangeEvent` du conteneur (le joueur a ajouté une pile), pas depuis le plugin : décision de jeu = cœur.
4. **Permission** : `MANAGE_HUTS` sur la colonie du citoyen (MC `AbstractColonyServerMessage.permissionNeeded`), revérifiée à l'ouverture.
5. **Condition d'ouverture** : MC exige l'entité chargée ; ici le conteneur ne dépend que des données, on peut donc l'ouvrir même si le corps est absent (écart à documenter si retenu).
6. **Validité** : MC laisse la fenêtre ouverte sans limite (`stillValid` → `true`). Implémenter `ValidatedWindow.validate` qui renvoie `false` seulement si le citoyen n'existe plus dans la colonie (mort, renvoyé) : Hytale fermera la fenêtre au prochain déplacement. Pour fermer tout de suite à la mort, garder une table `citizen → fenêtres ouvertes` et appeler `w.close(ref, store)` (garde `getWindow(id) == w`). Pas de limite de distance, comme MC.
7. **Thread** : ouvertures, déplacements, fermetures et tick de colonie tournent sur le thread du monde : pas de verrou à ajouter. Les verrous `ReentrantReadWriteLock` hérités de `SimpleItemContainer` restent en place.

## 6. Risques

- Sous-classer `SimpleItemContainer` et surcharger ses méthodes `internal_*` dépend d'une API interne (protégée) ; acceptable avec la version épinglée 0.6.8, à revérifier à chaque mise à jour.
- Hors `internal_*`, `SimpleItemContainer` lit `items[]` directement dans `CODEC`, le constructeur de copie, `clone()`, `equals` et `hashCode` (l. 42-103, 329-355) : notre conteneur n'est jamais sérialisé, mais `clone()` doit être surchargé. Le tri (`ItemContainer.internal_sortItems` l. 1341-1350), les déplacements (l. 374, 576-627, 783-790) et `getItemStack` (`SimpleItemContainer` l. 304-310) passent bien par `internal_*`.
- Perte de métadonnées : refusée à l'entrée par `cantAddToSlot` ; un objet sorti du citoyen est toujours « neuf » (limite déjà connue du cœur, cf. Javadoc de `Inventory`).
- Taille maximale de pile : le cœur utilise `maxStack` du catalogue ; Hytale applique la sienne dans `internal_*`. Les deux viennent de l'asset `MaxStack`, donc cohérentes, **[in-game]** à confirmer.
- **[in-game]** Rendu de `Page.Bench` avec un seul `ContainerWindow` de 27 slots (grille, titre) : le modèle `/inv see` suggère que ça marche, non vérifié en jeu.
- **[in-game]** Comportement MC exact à la mort du citoyen pendant que la fenêtre est ouverte : non vérifié dans les sources MC.

## 7. Mise en œuvre retenue (2026-09-27)

- Renvoi au client : pas de compteur lu au tick de colonie, mais `CitizenInventoryWindow.consumeIsDirty` surchargée, lue par Hytale à chaque tick (`plugin-b-api.md` § 16).
- Règle MC au dépôt : pas d'abonné `registerChangeEvent` ; `CitizenItemContainer` surcharge `writeAction`, copie l'inventaire avant l'écriture la plus externe, puis le cœur (`CitizenInventoryActions.onPlayerEdit`) compare avant/après et traite chaque case vide qui reçoit une pile, ou dont l'objet change, comme un `Slot.set` (une pile complétée ne compte pas).
- Fermeture : un citoyen ne disparaît aujourd'hui qu'avec sa colonie ; `CitizenInventoryWindows` ferme les fenêtres sur `ColonyEvents.ColonyDeleted`, et `validate` refuse tout déplacement si le citoyen n'existe plus.

## 8. Panneau de droite, aperçu du citoyen et armure (vérifié le 2026-09-27)

Question : afficher le modèle du citoyen dans le panneau de droite (vide aujourd'hui) et prévoir 4 slots d'armure comme MC. Sources : `hs/` (serveur décompilé), zip d'assets `release-0.6.8-Assets.zip` (abrégé `zip:`), éditeur `.ui` de l'utilisateur (`C:\Users\Ctuto\Desktop\Hytale UI Editor\`, abrégé `ed/`).

### 8.1 Ce que MineColonies affiche

- `MC/core/client/gui/containers/WindowCitizenInventory.java` l. 112-134 (`renderBg`) : grille du citoyen, grille du joueur, un cadre 49×72 (l. 127) où est dessinée **l'entité du citoyen, qui suit la souris** (`renderEntityInInventoryFollowsMouse(stack, i + 197, j + 88, 30, …, this.menu.getEntity())`, l. 134), et 4 cases d'armure à droite du cadre (l. 129-131). Titre = nom du citoyen (l. 104). Le rendu est celui de l'entité vivante (`EntityRenderDispatcher.render`, l. 170-187), donc avec son armure portée.
- `MC/api/inventory/container/ContainerCitizenInventory.java` : l'entité vient de `data.getEntity()` (serveur, l. 133) ou `level.getEntity(data.getEntityId())` (client, l. 112). Slots d'armure l. 193-196 : `HEAD`, `CHEST`, `LEGS`, `FEET`, dans cet ordre. `createArmorSlot` (l. 227-284) : slot adossé à `inventory.getArmorInSlot(slot)` ; `mayPlace` n'accepte qu'un `ArmorItem` du bon `EquipmentSlot` **et** autorisé par le `GuardGear` du niveau du bâtiment de travail (l. 255-268 ; niveaux l. 169-177 ; sans bâtiment, liste vide : rien n'est accepté) ; `set` appelle aussi `overruleNextOpenRequestOfCitizenWithStack` puis `forceArmorStackToSlot` (l. 236-244) ; icône de slot vide par type (l. 271-282).

### 8.2 Le panneau de droite de `Page.Bench`

- `Page.Bench` et `Page.Inventory` sont des écrans **natifs du client** : aucun `.ui` d'inventaire ni d'établi dans le zip (les 161 `.ui` sont tous sous `zip:Common/UI/Custom/` : pages de commandes, troc, mémoires…). Le serveur n'envoie que `SetPage { page, canCloseThroughInteraction }` (`hs/protocol/packets/interface_/SetPage.java` l. 22-24) et, par fenêtre, `OpenWindow { id, windowType, windowData (JSON), inventory, extraResources }` (`hs/protocol/packets/window/OpenWindow.java` l. 25-34). `InventorySection` ne porte que `items` et `capacity` (`hs/protocol/InventorySection.java` l. 22-23) : **ni filtre, ni type de slot, ni entité**.
- Le panneau de droite de `Page.Bench` est celui de l'établi : `BenchWindow` le décrit par son `windowData` (`type`, `id`, `name`, `blockItemId`, `tierLevel`…, `hs/builtin/crafting/window/BenchWindow.java` l. 52-56) et ses `WindowType` `BasicCrafting`/`DiagramCrafting`/`StructuralCrafting`/`Processing` (`hs/protocol/packets/window/WindowType.java`). Un `ContainerWindow` a un `windowData` vide (`ContainerWindow.java` l. 26-30, 42-44) : le client n'a rien à y mettre, d'où la boîte vide. C'est aussi le cas de `/inv see` et `/inv item`.
- Aucun code vanilla n'ouvre `Page.Inventory` avec des fenêtres. Seuls usages de `setPageWithWindows`, tous en `Page.Bench` : `OpenContainerInteraction` l. 96, `OpenBenchPageInteraction` l. 130, `OpenProcessingBenchInteraction` l. 89, `OpenTreasureContainerInteraction` l. 73, `OpenItemStackContainerInteraction` l. 51, `InventorySeeCommand` l. 75, `InventoryItemCommand` l. 63. L'artisanat de poche de l'inventaire joueur est une fenêtre **demandée par le client** (`Window.CLIENT_REQUESTABLE_WINDOW_TYPES`, `PocketCrafting → FieldCraftingWindow`, `hs/builtin/crafting/CraftingPlugin.java` l. 148). Le personnage et les slots d'armure de `Page.Inventory` sont donc dessinés par le client pour **le joueur local**. **[in-game]** Ce que donne `setPageWithWindows(…, Page.Inventory, …, containerWindow)` : jamais utilisé en vanilla, inconnu.

### 8.3 Aperçu d'une entité dans l'UI

- Éléments `.ui` (`ed/src/core/schema.json`, `ed/reference/official-docs/type-documentation/elements/`) : seuls `CharacterPreviewComponent` et `ItemPreviewComponent` rendent du 3D. Rien nommé `EntityPreview`, `ModelPreview` ou viewport.
  - `CharacterPreviewComponent` (`characterpreviewcomponent.md` l. 18-36, `schema.json` l. 791) n'a **que les propriétés génériques** (Visible, Anchor, Padding, Background, Tooltip…) : **aucune propriété pour choisir une entité, un modèle ou un skin**. Il affiche donc un personnage fourni par le client (vraisemblablement le joueur local). Aucun `.ui` du zip ne l'utilise ; aucune classe serveur ne le cite.
  - `ItemPreviewComponent` : `ItemId`, `ItemScale` : aperçu d'un **objet**, pas d'une entité.
- `PrefabPreview` / `PersistentPrefabPreview` (`hs/server/core/modules/entity/component/PrefabPreview.java`, `ComponentUpdateType.PrefabPreview(28)`) : composant d'**entité du monde** (blocs d'un prefab), pas de l'UI.
- Le seul « aperçu de modèle » vanilla, `EntitySpawnPage` (`hs/server/npc/pages/EntitySpawnPage.java` l. 806-826), **fait apparaître une vraie entité dans le monde** devant le joueur (`NetworkId`, `NonSerialized`, `TransformComponent`, `ModelComponent`, `store.addEntity`), pas dans la page.
- `UICommandBuilder` (`hs/server/core/ui/builder/UICommandBuilder.java` l. 38-178) ne sait que `clear/remove/append/insertBefore/set/setObject` sur des propriétés d'éléments : aucune commande pour lier une entité à un élément.
- Conclusion : **impossible** de montrer le modèle d'un PNJ donné dans une page ou une fenêtre avec l'API 0.6.8. Une page custom peut contenir un `CharacterPreviewComponent`, mais on ne peut pas lui dire quelle entité afficher. **[in-game]** Ce qu'il montre dans une page custom (joueur local ? rien ?), et s'il est accepté dans un `.ui` envoyé par le serveur.
- Fenêtres dans une page custom : `openCustomPageWithWindows(ref, store, CustomUIPage, Window...)` (`PageManager.java` l. 208-226) existe, sans aucun appel vanilla. `ItemGrid` a `InventorySectionId` (`itemgrid.md` l. 33), `AreItemsDraggable` (l. 32) et `Slots: ItemGridSlot[]` (l. 34 ; `ItemGridSlot` a `InventorySlotIndex`, `Icon`, `Overlay`) : c'est vraisemblablement le moyen de lier une grille de notre `.ui` à une fenêtre ouverte (une section d'id ≥ 0 est un id de fenêtre, § 3). Aucun `.ui` vanilla ne s'en sert : **[in-game]** entièrement.

### 8.4 Armure : filtrer un slot par type

- Conteneur d'armure du joueur et des PNJ : `InventoryComponent.Armor` (`hs/server/core/inventory/InventoryComponent.java` l. 404-445), capacité `DEFAULT_ARMOR_CAPACITY = ItemArmorSlot.VALUES.length` (l. 54), section `ARMOR_SECTION_ID = -3` (l. 60). À la création et au décodage : `ItemContainerUtil.trySetArmorFilters(inventory)` (l. 431-439).
- `ItemContainerUtil.trySetArmorFilters` (`hs/server/core/inventory/container/ItemContainerUtil.java` l. 10-28) : pour un `SimpleItemContainer`, slot `i` → `setSlotFilter(FilterActionType.ADD, i, new ArmorSlotAddFilter(ItemArmorSlot.VALUES[i]))`, slots en trop → `SlotFilter.DENY`. Méthode `public static` : **réutilisable telle quelle** sur un `SimpleItemContainer` de 4 slots à nous.
- `ArmorSlotAddFilter.test(Item)` (`…/container/filter/ArmorSlotAddFilter.java` l. 15-17) : `item == null || item.getArmor() != null && item.getArmor().getArmorSlot() == slot`. `ItemArmor.getArmorSlot()` : `hs/server/core/asset/type/item/config/ItemArmor.java` l. 249. `SimpleItemContainer.setSlotFilter(FilterActionType, short, SlotFilter)` l. 282 ; `FilterActionType { ADD, REMOVE, DROP }`.
- **Écart de slots** : `ItemArmorSlot { Head, Chest, Hands, Legs }` (`hs/protocol/ItemArmorSlot.java`), MC a `HEAD, CHEST, LEGS, FEET`. Hytale n'a pas de bottes mais des gants (voir aussi l'asset du § 8.5) : `Deviation from MC` à documenter.
- Le filtre est **côté serveur seulement** (le paquet ne le transporte pas, § 8.2) : le client affiche 4 cases génériques sans silhouette et laisse tenter le dépôt ; le serveur refuse. La règle `GuardGear` (niveau du bâtiment) est une décision de jeu : elle va dans le cœur, appelée par un `SlotFilter` à nous (`ItemSlotFilter` est une interface, `…/filter/ItemSlotFilter.java` l. 9) qui combine le type Hytale et la réponse du cœur.
- Deux fenêtres sur une page : `setPageWithWindows(…, Window... windows)` accepte plusieurs fenêtres (`PageManager.java` l. 165-183, un `OpenWindow` par fenêtre) ; `WindowManager.openWindows` renvoie `null` et la page ne s'ouvre pas si l'une refuse. **Aucun usage vanilla n'ouvre deux `ContainerWindow`** : **[in-game]** comment le client dispose une 2ᵉ grille de 4 cases sur `Page.Bench` (empilée ? ignorée ? dans le panneau de droite ?). Repli sans risque de disposition : un seul conteneur de 31 slots (27 + 4 d'armure filtrés), au prix de cases d'armure non distinguées visuellement.

### 8.5 Armure portée visible sur le corps du PNJ

- Tout PNJ reçoit `InventoryComponent.Armor(DEFAULT_ARMOR_CAPACITY)` (`hs/server/npc/systems/NPCSystems.java` l. 519-520).
- Un changement du conteneur d'armure passe par `LegacyArmorChangeStatSystem` (`hs/server/core/inventory/InventorySystems.java` l. 286-305) : `setOutdatedEquipment(true)` et recalcul des modificateurs de stats (l'armure compte donc aussi pour la défense du PNJ). `SyncEquipmentSystem` (l. 502-590) interroge **toute entité visible** ayant `Armor`/`Hotbar`/`Utility`, pas seulement les joueurs, et envoie `EquipmentUpdate` aux spectateurs ; `InventoryUtils.createEquipmentUpdate` (`hs/server/core/inventory/InventoryUtils.java` l. 817-858) remplit `armorIds` avec l'id d'objet de chaque slot.
- Vanilla s'en sert pour des PNJ : clé de rôle `Armor` (`BuilderRole.java` l. 398-404 → `Role.initialiseItemsAndArmor` l. 1638-1645 via `InventoryHelper.useArmor`), commande `/npc give <armure>` (`hs/server/npc/commands/NPCGiveCommand.java` l. 46-47 → `RoleUtils.setArmor` l. 58-65). Asset : `zip:Server/NPC/Roles/_Core/Tests_Development/Test_Combat_Knight.json` l. 3 et 9 : `"Appearance": "Player"`, `"Armor": [ "Armor_Iron_Head", "Armor_Iron_Chest", "Armor_Iron_Hands", "Armor_Iron_Legs" ]`.
- Notre citoyen (`plugin/src/main/resources/Server/NPC/Roles/HyColony/HyColony_Citizen.json` l. 3) a `"Appearance": "PlayerTestModel_V"`, dont le modèle a `"Parent": "Player"` (`zip:Server/Models/Human/PlayerTestModel_V.json` l. 2) : même squelette que le chevalier de test. Écrire l'armure du cœur dans `InventoryComponent.Armor` du corps suffit donc pour que le serveur l'envoie aux clients. **[in-game]** Rendu effectif sur `PlayerTestModel_V` (armure par-dessus ses `DefaultAttachments` de vêtements).
- À la mort, `NPCDamageSystems` (l. 205-225) ne fait tomber que `InventoryComponent.Storage` (si `PickupDropOnDeath`) et la liste de drops du rôle : **pas** l'armure.

### 8.6 Approche recommandée

1. **Panneau de droite** : aucun aperçu du PNJ n'est possible (§ 8.3). Garder `Page.Bench` + `ContainerWindow` ; le panneau vide est le comportement vanilla de `/inv see`. Ne pas passer à une page custom pour ça : `CharacterPreviewComponent` ne peut pas viser le citoyen. Écart à documenter : « Deviation from MC: Hytale 0.6.8 cannot render a given entity in a UI, so the citizen is not drawn in its inventory window. »
2. **Armure (plus tard, avec les gardes)** :
   - le cœur garde 4 slots d'armure dans `CitizenData` (vérité unique, comme les 27 slots) et la règle `GuardGear` ;
   - le plugin expose un conteneur d'armure adossé au cœur (`SimpleItemContainer` de 4 slots dans l'ordre `ItemArmorSlot`, `ItemContainerUtil.trySetArmorFilters` + un filtre qui interroge le cœur) ;
   - essayer d'abord deux fenêtres sur `Page.Bench` ; si le client les dispose mal, repli sur un seul conteneur de 31 slots ;
   - recopier l'armure du cœur dans `InventoryComponent.Armor` du corps PNJ (au spawn et à chaque changement), pour l'affichage.
3. Risques :
   - **[in-game]** disposition de deux `ContainerWindow` sur `Page.Bench` ;
   - **[in-game]** rendu de l'armure sur `PlayerTestModel_V` ;
   - cases d'armure sans silhouette ni libellé : le filtre est invisible pour le client ;
   - l'armure du corps n'est qu'une **copie d'affichage**, jamais relue comme vérité (§ 4) ;
   - l'armure du corps change les stats Hytale du PNJ (défense) : voulu pour les gardes, mais c'est alors Hytale qui réduit les dégâts, pas la formule MC. À trancher dans la spec des gardes.

## 9. Aperçu du citoyen par la caméra du serveur (essai en jeu, 2026-10-02, Hytale 0.7.0)

Question : remplacer le dessin de l'entité de MC (`WindowCitizenInventory.renderEntityInInventoryFollowsMouse`, cadre 49 × 72 en (172, 22)) par la caméra du serveur posée sur le citoyen pendant que sa fenêtre est ouverte. Essai jetable `/hycolony camprobe`, retiré après le test.

- **Caméra** : `SetServerCamera(ClientCameraView.Custom, true, settings)` avec `ServerCameraSettings.attachedToType = EntityId`, `attachedToEntityId = NetworkId` du corps, `followAttachedEntity`, `isFirstPerson = false`, `positionDistanceOffsetType = DistanceOffset`, `rotationType = Custom`, `applyLookType = Rotation`, `rotation = lacet du corps + π` (réglages copiés de `SpectatorSystems.applyFollowCamera`, l. 254-288). Retour : `SetServerCamera(Custom, false, null)`, comme `PlayerCameraResetCommand`.
- **Sans page** : la caméra se fixe sur le citoyen, face à lui **[in-game]**.
- **Avec `Page.Bench`** (la fenêtre de conteneur actuelle) : la page impose sa caméra. Envoyer la nôtre avant l'ouverture, juste après, ou 100, 500 et 1 500 ms après ne change rien **[in-game]**. Le décor reste visible autour des panneaux, sans assombrissement, mais les panneaux couvrent presque tout l'écran et celui de droite est opaque.
- **Avec une page personnalisée** (la fenêtre du citoyen, `CitizenPage`) : la caméra reste fixée sur le citoyen **[in-game]**. C'est la voie retenue : notre propre fenêtre d'inventaire, avec les grilles de HyBlockUI (`InventoryGrids`, § 325-333 de `plugin-b-api.md`) et un cadre transparent pour l'aperçu.
- La caméra garde le citoyen au centre de l'écran. Décaler le citoyen dans un cadre (`positionOffset`, `rotationOffset`) reste à régler sur la vraie fenêtre **[in-game]**.
