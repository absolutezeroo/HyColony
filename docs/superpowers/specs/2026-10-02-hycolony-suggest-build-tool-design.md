# Pose d'une hutte à la main : la baguette suggérée

Date : 2026-10-02. Demandé par l'utilisateur : « dans MC quand on pose un bloc hutte town hall etc. ça demande le placement avec la build tool ». Conception validée dans la conversation (« Oui »).

Port de MineColonies : `EventHandler.onPlayerInteract` (le bloc de hutte en main), `WindowSuggestBuildTool`, `OpenSuggestionWindowMessage`, `SwitchBuildingWithToolMessage`.

## 1. Ce que fait MineColonies

- Un joueur pose un bloc de hutte (`AbstractBlockHut`, sauf les composants de stockage, `IRSComponentBlock`), hôtel de ville compris, dans cet ordre :
  - les règles de pose de la hutte (`handleEventCancellation` → `onBlockHutPlaced` : `PLACE_HUTS` dans une colonie, hôtel de ville déjà là ; hors colonie, une hutte autre que l'hôtel de ville est refusée, « pas d'hôtel de ville » ou « hors de votre colonie ») : un refus annule la pose avec son message, sans fenêtre. Un hôtel de ville hors colonie passe toujours : les règles de fondation (déjà propriétaire, distance au point d'apparition, autre colonie trop proche) n'arrivent qu'à la création de la colonie ;
  - dans une colonie où il n'a pas `ACCESS_HUTS` : la pose est annulée, sans message ni fenêtre ;
  - en créatif et accroupi : la pose se fait normalement ;
  - sinon : la pose est annulée et la fenêtre `WindowSuggestBuildTool` s'ouvre, avec la position visée (la case contre la face cliquée).
- La fenêtre (`windowsuggestbuildtool.xml`, 190 × 133) : le papier `colonist_paper`, une croix (`button_x`, 14 × 15 en 165, 5), le texte « We suggest using the build tool to place this schematic. This will allow you to adjust the position. » (150 × 45 en 15, 25) et le bouton « Use build tool » (`builder_button_medium`, 86 × 17 en 50, 100). Le bouton « Place anyway » existe dans le code et les textes de MC, mais n'est plus dans la fenêtre.
- « Use build tool » :
  - sans baguette dans l'inventaire : « Missing build-tool in player inventory! » et la fenêtre se ferme ;
  - sinon : la hutte (dernière case de la barre d'action qui la tient) et la baguette (dernière case de l'inventaire qui la tient) échangent leurs cases (`SwitchBuildingWithToolMessage`), puis la baguette s'ouvre à la position visée (`new WindowExtendedBuildTool(pos, …)` : le fantôme y va s'il n'en a pas déjà un ; aucune hutte n'est présélectionnée).

## 2. Cœur

- `app/wand/HutHandPlacement` :
  - `handPlaced(player, target, buildingTypeId, hutItem, crouching)` → `Optional<HutPlacement>` : le refus des règles (`HutActions.checkPlacement`) avec sa raison, sauf pour un hôtel de ville hors colonie, qui va à la suite ; vide (annuler sans rien dire) pour une colonie sans `ACCESS_HUTS` ou quand la fenêtre de suggestion s'ouvre ; la pose permise par les règles pour un joueur créatif accroupi ;
  - `useBuildTool(player, pos, hutItem)` : sans baguette, le message de MC ; sinon l'échange par le port d'inventaire, puis `WandActions.open(player, Optional.of(pos))`.
- `PlayerInventory.swapIntoHotbar(player, hotbarItem, otherItem)` : la dernière case de la barre d'action qui tient `hotbarItem` et la dernière case de l'inventaire qui tient `otherItem` échangent leur contenu ; faux sans l'une des deux.
- `UiPort.showSuggestBuildTool(player, view)` avec la vue `SuggestBuildToolView(pos, hutItem)`.

## 3. Plugin

- `HutPlaceSystem` (sorti de `HutBlockSystems`) : demande la décision au cœur et l'applique (annuler, dire le refus, commencer la fondation ou enregistrer la hutte) ; l'état accroupi vient de `MovementStatesComponent` (`MovementStates.crouching`).
- La page `SuggestBuildToolPage` et son `.ui`, positions et tailles de MC ×2, textures déjà copiées dans `Pages/HyColony/Mc/`.
- `HytalePlayerInventory.swapIntoHotbar` sur le conteneur combiné (barre d'action d'abord).
- Textes en-US et fr-FR : le texte, le bouton, le message sans baguette.

## 4. Écarts

- L'échange se fait au clic du bouton côté serveur (MC le fait par un message du client) ; le résultat est le même.
- Le créatif garde les règles de pose : MC (`onBlockHutPlaced`) laisse un joueur créatif poser une hutte hors colonie ou un second hôtel de ville (accroupi), et lui montre la fenêtre (debout), après le message hors colonie ; `HutActions.checkPlacement`, port de `AbstractBlockHut.canPaste`, n'a pas cette exception : le message seul (audit M-26, déjà en place avant ce changement).
- Un hôtel de ville posé tel quel (créatif accroupi) hors colonie rencontre aussitôt les règles de fondation, avec leur message ; MC les applique à la création de la colonie, juste après.
- La colonie est lue à la case posée (MC la lit au bloc cliqué pour `ACCESS_HUTS`, que `PlaceBlockEvent` ne donne pas) : les deux ne diffèrent qu'à la bordure du territoire.
- L'inventaire de l'échange est la barre d'action plus le sac (le conteneur combiné de Hytale) ; l'armure et la main secondaire, que MC compte aussi, n'y sont pas.
- Les composants de stockage (`IRSComponentBlock`) sont exclus chez MC ; HyColony n'en a pas encore : à exclure quand ils arriveront.

## 5. Tests

- Cœur : le refus des règles d'abord (trop loin, hôtel de ville déjà là, étranger sans `PLACE_HUTS`), sans fenêtre ; l'hôtel de ville hors colonie suggéré, même pour un propriétaire ; le refus muet sans `ACCESS_HUTS` ; la suggestion ; la pose en créatif accroupi ; sans baguette, le message et rien d'autre ; avec baguette, l'échange puis l'ouverture de la baguette à la position, le fantôme gardé s'il existe déjà.
- En jeu : nouveaux points de `docs/TESTING.md`.
