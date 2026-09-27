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
