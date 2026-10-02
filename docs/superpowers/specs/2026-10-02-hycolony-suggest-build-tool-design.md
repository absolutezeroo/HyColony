# Pose d'une hutte à la main : la baguette suggérée

Date : 2026-10-02. Demandé par l'utilisateur : « dans MC quand on pose un bloc hutte town hall etc. ça demande le placement avec la build tool ». Conception validée dans la conversation (« Oui »).

Port de MineColonies : `EventHandler.onPlayerInteract` (le bloc de hutte en main), `WindowSuggestBuildTool`, `OpenSuggestionWindowMessage`, `SwitchBuildingWithToolMessage`.

## 1. Ce que fait MineColonies

- Un joueur pose un bloc de hutte (`AbstractBlockHut`, sauf les composants de stockage, `IRSComponentBlock`), hôtel de ville compris :
  - dans une colonie où il n'a pas `ACCESS_HUTS` : la pose est annulée, sans rien de plus ;
  - en créatif et accroupi : la pose se fait normalement ;
  - sinon : la pose est annulée et la fenêtre `WindowSuggestBuildTool` s'ouvre, avec la position visée (la case contre la face cliquée).
- La fenêtre (`windowsuggestbuildtool.xml`, 190 × 133) : le papier `colonist_paper`, une croix (`button_x`, 14 × 15 en 165, 5), le texte « We suggest using the build tool to place this schematic. This will allow you to adjust the position. » (150 × 45 en 15, 25) et le bouton « Use build tool » (`builder_button_medium`, 86 × 17 en 50, 100). Le bouton « Place anyway » existe dans le code et les textes de MC, mais n'est plus dans la fenêtre.
- « Use build tool » :
  - sans baguette dans l'inventaire : « Missing build-tool in player inventory! » et la fenêtre se ferme ;
  - sinon : la hutte (dernière case de la barre d'action qui la tient) et la baguette (dernière case de l'inventaire qui la tient) échangent leurs cases (`SwitchBuildingWithToolMessage`), puis la baguette s'ouvre à la position visée (`new WindowExtendedBuildTool(pos, …)` : le fantôme y va s'il n'en a pas déjà un ; aucune hutte n'est présélectionnée).

## 2. Cœur

- `app/wand/HutHandPlacement` :
  - `decide(player, target, colony access, creative, crouching)` → `DENIED` (colonie sans `ACCESS_HUTS`), `PLACE` (créatif accroupi), `SUGGEST` ;
  - `useBuildTool(player, pos, hutItem)` : sans baguette, le message de MC ; sinon l'échange par le port d'inventaire, puis `WandActions.open(player, Optional.of(pos))`.
- `PlayerInventory.swapIntoHotbar(player, hotbarItem, otherItem)` : la dernière case de la barre d'action qui tient `hotbarItem` et la dernière case de l'inventaire qui tient `otherItem` échangent leur contenu ; faux sans l'une des deux.
- `UiPort.showSuggestBuildTool(player, view)` avec la vue `SuggestBuildToolView(pos, hutItem)`.

## 3. Plugin

- `HutBlockSystems.Place` : avant les règles de pose, demande la décision au cœur ; l'état accroupi vient de `MovementStatesComponent` (`MovementStates.crouching`).
- La page `SuggestBuildToolPage` et son `.ui`, positions et tailles de MC ×2, textures déjà copiées dans `Pages/HyColony/Mc/`.
- `HytalePlayerInventory.swapIntoHotbar` sur le conteneur combiné (barre d'action d'abord).
- Textes en-US et fr-FR : le texte, le bouton, le message sans baguette.

## 4. Écarts

- Aucun prévu. L'échange se fait au clic du bouton côté serveur (MC le fait par un message du client) ; le résultat est le même.

## 5. Tests

- Cœur : les trois décisions ; sans baguette, le message et rien d'autre ; avec baguette, l'échange puis l'ouverture de la baguette à la position, le fantôme gardé s'il existe déjà.
- En jeu : nouveaux points de `docs/TESTING.md`.
