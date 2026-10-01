# Update 7 (0.7.0-pre.4) : axe C, interface (pages, fenêtres, `.ui`, HUD, inventaires)

Recherche du 2026-09-29. Compare Hytale 0.6.8 (épinglé) et la pré-version Update 7 part 4.

Sources et abréviations :

- **A6** = `…/Hytale/install/release/package/game/latest/Assets.zip` (identique octet pour octet à `~/.gradle/caches/hytale-assets/release-0.6.8-Assets.zip`, même taille 3 476 312 291) ; **A7** = `…/install/pre-release/package/game/latest/Assets.zip`.
- **C6** / **C7** = dossier `Client/` à côté (`Client/Data/Game/Interface/**`, `HytaleClient.exe`).
- **S6** = `build/vineflower/hytale-server/com/hypixel/hytale/` ; **S7** = sources U7 décompilées (scratchpad `u7/src/com/hypixel/hytale/`).
- **N** = notes de version U7 (`notes.txt`, numéro de ligne entre crochets).
- Les diffs Java sont faits après normalisation (Javadoc injectée dans S6, `<>` et casts de décompilation retirés). « Inchangé » veut dire : plus aucune différence après cette normalisation.

## Bloquant

**Rien.** Aucune de nos fenêtres ne casse en U7, preuves ci-dessous.

1. **Arbre vanilla `Common/UI/**` (A6 → A7)** : 0 fichier retiré, 19 ajoutés, 14 modifiés (comparaison des CRC de `unzip -v`).
   - `Common/UI/Custom/Common.ui` ne fait que **grandir** : l'unique bloc du diff est `878a879,954`, soit `@RunicContainer`, `@RunicPanel`, `@RunicCheck*`, `@RunicArrow`, `@RunicGlow`, `@RunicTitleStyle`, `@RunicHeadlineStyle` et `@RunicBodyStyle`.
   - Les 18 définitions que nos `.ui` citent (`$C.@BackButton`, `@Container`, `@DecoratedContainer`, `@DefaultLabelStyle`, `@DefaultScrollbarStyle`, `@DefaultSpinner`, `@DefaultTextTooltipStyle`, `@HeaderSearch`, `@PageOverlay`, `@ProgressBar`, `@SecondaryButton`, `@SecondaryButtonStyle`, `@SecondaryTextButton`, `@SmallSecondaryTextButton`, `@TextButton`, `@TextField`, `@Title`, `@VerticalSeparator`) sont toutes présentes, au même endroit, dans le `Common.ui` de A7 (par exemple `@PageOverlay` l. 955, `@DecoratedContainer` l. 840, `@Title` l. 650).
   - `Common/UI/Custom/Sounds.ui` est identique (CRC `8ce3cc2f` dans A6 et A7). Les `$Sounds.@SaveSettings` et `@ButtonsCancel` sont toujours aux l. 134 et 35.
   - Les autres fichiers modifiés ne nous concernent pas : `Common/ActionButton.ui` (+`#BindingModifierIcon`, `#BindingModifierPlus`), `Hud/ReturnToHubButton.ui`, les pages Portal, TriggerVolume `NumberRow`/`Vec3Row`, la galerie `ContainersContent.ui` et six icônes `WorldMap/MapMarkers/*.png` (dont pas `Coordinate.png`, CRC `854b50f9` inchangé).
2. **Types d'éléments et propriétés.** Nos `.ui` utilisent 7 types d'éléments : `Group`, `Label`, `Button`, `ItemIcon`, `ItemGrid`, `ItemPreviewComponent` et `CharacterPreviewComponent`. Les propriétés propres aux grilles et aux styles qu'on emploie sont `SlotsPerRow`, `AreItemsDraggable`, `RenderItemQualityBackground`, `DisplayItemQuantity`, `SlotIconSize`, `SlotBackground`, `DurabilityBar*`, `TextTooltipStyle`, `ScrollbarStyle`, `HitTestVisible`, `FlexWeight`, `LeftCenterWrap`, `TopScrolling`, `InventorySectionId` et `RenderUppercase`.
   - Chacun de ces noms figure le même nombre de fois dans `C6/HytaleClient.exe` et dans `C7/HytaleClient.exe` (comptage ASCII et UTF-16).
   - Ce comptage prouve seulement que les noms existent encore, pas que le rendu est identique. **[in-game]**
3. **API serveur d'interface.** Les classes suivantes sont inchangées de S6 à S7 :
   - pages : `CustomUIPage`, `InteractiveCustomUIPage`, `BasicCustomUIPage`, `PageManager` ;
   - construction : `UICommandBuilder`, `UIEventBuilder`, `EventData` ;
   - fenêtres : `Window`, `ContainerWindow`, `ContainerBlockWindow`, `ValidatedWindow`, `ItemContainerWindow`, `BlockWindow` ;
   - HUD : `HudManager`, `CustomUIHud` ;
   - interaction et paquets : `OpenCustomUIInteraction`, `CustomPage`, `CustomUICommand`, `CustomUIEventBindingType`, `CustomPageLifetime`.
   
   Seules des différences de décompilation restent dans `WindowManager`, `Message`, `LocalizableString` et `ValueCodec`.
   - Les enums gagnent une valeur **en fin de liste** : `Page.Chapters(9)` (`S7/protocol/packets/interface_/Page.java`) et `WindowType.AbilityBench(7)`. Les valeurs existantes gardent leur numéro, donc nos `Page.None` (`plugin/.../adapter/HytaleUiPort.java:194`) et `Page.Bench` (`plugin/.../ui/HutStorage.java:94`) ne changent pas.
4. **Glisser-déposer des inventaires (HyBlockUI).**
   - Les clés `SlotIndex`, `SourceInventorySectionId`, `SourceSlotId` et `ItemStackQuantity` lues par `blockui/src/main/java/dev/hyblockui/api/InventoryDrop.java:25-50` sont présentes dans C6 comme dans C7 (2 occurrences UTF-16 chacune).
   - Les identifiants de section qu'utilise `PlayerSection.java:15-17` (stockage, barre rapide, armure) ne bougent pas. S7 n'ajoute que `ABILITIES_SECTION_ID = -11` et `RUNE_BAG_SECTION_ID = -12` (`S7/server/core/inventory/InventoryComponent.java`).

## À migrer

Rien n'est obligatoire. Trois retouches suivent le passage à U7 :

1. **Éditeur `.ui` de l'utilisateur.** Il signale déjà `LabelStyle.ShrinkTextToFit` et `MinShrinkTextToFitFontSize` comme « écarts ignorés connus » (`.superpowers/sdd/2026-09-26-hycolony-build-tool/task-6-report.md:94`, `…/2026-09-27-hycolony-sp3a-warehouse-courier/task-12-report.md:93`). Pour valider nos `.ui`, son corpus devra connaître le `Common.ui` de A7 (les `@Runic*`) et ces deux propriétés.
2. **Javadoc datée.** `InventoryDrop.java:25` écrit « the keys Hytale 0.6.8 sends ». Les clés n'ont pas changé : il suffira de mettre la version à jour.
3. **Langue du joueur (effet de bord, sans casse).**
   - `SeedPickerPage.java:78` appelle `I18nModule.get().getMessage(playerRef.getLanguage(), key)`. En U7, `GamePacketHandler` enregistre la langue **résolue** (`resolveLanguage` : langue connue, sinon son repli, sinon `en-US`). Il n'envoie les traductions que si elle change, et par lots, au plus une fois par seconde (`S7/server/core/modules/i18n/I18nModule.java`, `LANGUAGE_FLUSH_INTERVAL_MILLIS` = 1 s, `queueTranslations`, `flushPendingLanguages` ; `S7/.../GamePacketHandler.java`, bloc `UpdateLanguage` ; N[751]).
   - Pour nous, le filtre de recherche compare donc les noms dans la langue de repli (par exemple fr-FR pour une variante française), ce qui va mieux.
   - Juste après un changement de langue, une fenêtre ouverte dans la seconde peut encore afficher les anciennes traductions. **[in-game]**

## Contournements devenus inutiles

| Contournement | Chez nous | Verdict U7 | Preuve |
|---|---|---|---|
| Fermer la fenêtre d'emplacements d'une page **une tâche plus tard** (sinon, sur Échap, « Window id … is invalid » en SEVERE) | `blockui/src/main/java/dev/hyblockui/api/HeldWindows.java:47` (`closeLater`), appelé par `ReturningContainerWindow`, `domum/.../cutter/CutterPage.java`, `plugin/.../ui/citizen/CitizenInventoryPanel.java`, `CitizenInventoryWindows.java` (commit `65c68e4a`) | **À garder** | `handleCloseWindow` fait toujours `getWindowManager().closeWindow(ref, packet.id, store)` (`S7/.../GamePacketHandler.java:629-634`), et `WindowManager.closeWindow` lève toujours `IllegalStateException("Window id " + id + " is invalid!")` (`S7/.../windows/WindowManager.java:202`). `PageManager` est inchangé. |
| `DefaultItemIcon` sur `ItemGrid` | **Aucun** : nos 5 `ItemGrid` n'en ont pas (`PlayerCharacterPanel.ui:42`, `PlayerStoragePanel.ui:36,49`, `Citizen.ui:84`, `Cutter.ui:115`). L'inventaire natif, lui, en met un (`C6/.../InGame/Common.ui:18`, `UnknownItemIcon.png`). | **Rien à retirer.** U7 supprime un risque qui restait en 0.6.8 : un objet dont l'icône ne se résout pas s'affiche maintenant avec l'icône « objet inconnu », au lieu d'un comportement non défini. | N[748], N[591]. Ce qu'on voyait en 0.6.8 dans une grille sans `DefaultItemIcon` n'a jamais été observé. **[in-game]** |
| Aperçu du coupeur : montrer le modèle tant que la variante n'est pas créée | `domum/.../cutter/CutterDrawing.java:137-139` (`shownItem`) | **À garder** | Ce n'est pas un contournement d'icône : sans lui, U7 montrerait l'icône « objet inconnu » à la place de l'aperçu. |
| Copies des textures `IngredientSlot` et `IngredientSlotValid` dans le pack HyBlockUI | `blockui/.../HyBlockUI/Native/IngredientSlot@2x.png` et `IngredientSlotValid@2x.png`, utilisées par `domum/.../HyDomum/Cutter.ui:81,93,99,111`. Motif : une page ne charge que les textures du pack serveur (`docs/native-ui-textures.md`). | **Inutiles en U7** : A7 livre ces deux textures, **identiques octet pour octet** (md5 `344645e8…` et `de64066d…`), dans `Common/UI/Custom/Common/`. Le `.ui` vanilla les cite par `"../Common/IngredientSlot.png"` depuis `Pages/` (`A7:Common/UI/Custom/Pages/AugmentCostSlot.ui`). Depuis `Pages/HyDomum/`, le chemin serait `"../../Common/IngredientSlot.png"` **[in-game]**. | Ça ferait deux copies © Hypixel de moins et deux lignes de moins dans le tableau de `docs/native-ui-textures.md`. Les autres copies (`Slot`, `CharacterBackground`, `ArmorSlotIcon*`, `DurabilityBar*`, `BlockIcon*`, `AltDropdownCaret*`, `DiagramCraftingSlotInvalid`, `ItemSlotSelectorEmptyIcon`, `SlotInputBindingBackground`) ne sont toujours **pas** dans `Common/UI/Custom` de A7 : il faut les garder. |
| Clics de souris envoyés derrière une page | Aucun contournement : on n'utilise ni caméra à curseur (`ServerCameraSettings.displayCursor`), ni `MouseInteraction`, ni `PlayerMouseButtonEvent` (grep vide dans `plugin/`, `blockui/`, `domum/`) | **Sans objet** | Le correctif ne touche que la caméra à curseur (N[745-747]). |
| Texte trop long dans un libellé | Aucun contournement dans nos `.ui` (grep `Shrink` vide) | **Sans objet**, mais voir Opportunités : `LabelStyle.ShrinkTextToFit` existait **déjà en 0.6.8** | A6 `Common/UI/Custom/Common.ui:71-72` (`@DefaultButtonLabelStyle`), C6 `Interface/Common.ui:68-69`, `Common/Container.ui:21-22`. |

Les textures copiées de l'inventaire natif restent valables : dans `C6 → C7/Data/Game/Interface/InGame/Pages/Inventory/`, seul `CursedSpiral.png` est remplacé par `EphemeralSpiral.png` (`diff -rq`), et nous ne copions ni l'un ni l'autre.

## Opportunités (par fenêtre)

Règle de tri : on ne retient que ce qui rapproche de MineColonies ou améliore le rendu sans changer une règle de jeu. `hy:ShrinkTextToFit` (N[339]) est un décorateur **Noesis** (pages XAML du client) : il ne s'applique pas à nos pages `.ui`. L'équivalent `.ui` est `Style: (… ShrinkTextToFit: true, MinShrinkTextToFitFontSize: n)`, **disponible dès 0.6.8** (voir ci-dessus), et U7 s'en sert dans `@RunicTitleStyle` (A7 `Common.ui:934-944`) et dans `BlockLorePage.ui` (`...$C.@TitleStyle, ShrinkTextToFit: true, MinShrinkTextToFitFontSize: 11`).

1. **Hôtel de ville** (MC `WindowMainPage`, titre = nom de la colonie, `ui-vs-minecolonies.md` § 1). `TownHall.ui:28` `#ColonyName` (22 px, majuscules) déborde avec un nom long. On peut lui ajouter `ShrinkTextToFit: true, MinShrinkTextToFitFontSize: 12`. Même chose pour les lignes de citoyen `CitizenRow.ui:5` `#Name` (FlexWeight). **[in-game]** : la largeur finie qu'exige la réduction, et le rendu sans elle (coupé ou débordant).
2. **Fenêtre de hutte** (MC `layouthuts/*`, § 2). `Building.ui:28` `#TypeName` (22 px, majuscules : « ENTREPÔT », « CABANE DU BÛCHERON »…) et `Building.ui:145` `#StyleName` sont les deux libellés les plus exposés en français. Ajouter aussi `BuilderOrderRow.ui:19`, `OrderRow.ui:11`, `TaskRow.ui:19`, `RecipeRow.ui:24` (`#Title`), et `ResourceRow.ui:11`, `StockRow.ui:11`, `WorkerRow.ui:7` (`#Name`).
3. **Citoyen** (MC `WindowCitizen`, § 4). `Citizen.ui:32` `#Name` (22 px) : les noms prénom + nom de MC sont longs. `JobSkillRow.ui:9` et `SkillRow.ui:26` aussi.
   - `HiddenUIComponents` (N[171], `S7/server/npc/role/builders/BuilderRole.java:280-295`, `Role.java:228-248`) ne s'écrit que dans le rôle d'un PNJ. Il masque des composants de `Server/Entity/UI/`, qui n'en contient que deux : `Healthbar` (type `EntityStat`, stat `Health`) et `CombatText`.
   - MC n'affiche pas de barre de vie au-dessus des citoyens (non vérifié dans le code MC ; le rendu MC de nom et de bulles d'état n'a pas d'équivalent Hytale : il n'existe que les types `EntityStat` et `CombatText`, `S7/server/core/modules/entityui/EntityUIModule.java:60-61`).
   - Nos citoyens sont `Invulnerable` (`plugin/src/main/resources/Server/NPC/Roles/HyColony/HyColony_Citizen.json`). Ajouter `"HiddenUIComponents": ["Healthbar", "CombatText"]` est donc sans risque, et n'a d'effet que si la barre s'affiche aujourd'hui. **[in-game]** : voit-on une barre de vie sur un citoyen en 0.6.8 ? La clé n'existe pas en 0.6.8 (S6 `Role.java` n'a pas `UIComponentList`), donc c'est U7 seulement.
   - Le correctif « `UIComponentList` ignorait `Components` » (N[218]) ne change rien pour nous sans cette clé : `null` veut toujours dire « tous » (`S7/.../entityui/UIComponentList.java:18-20,49`).
   - `CharacterPreviewComponent` (`PlayerCharacterPanel.ui`) profite peut-être du correctif de fuite mémoire à la fermeture d'un menu qui montre le personnage (N[613]). **[in-game]** : on ne sait pas s'il couvre les pages serveur.
4. **Requêtes / presse-papiers** (MC `WindowClipBoard`, § 5) et **ordres de travail** (§ 6) : `RequestRow.ui`, `OrderRow.ui:11`, la même réduction sur les titres. Rien d'autre dans U7.
5. **Champ et sélecteur de graines** (MC `WindowField` / `WindowSelectRes`). `Field.ui:32` `#SeedName` et `FieldSeedRow.ui:11` `#Name` : réduction. Le changement de langue résolue aide la recherche (voir « À migrer » 3).
6. **Baguette / style** (Structurize `WindowBuildTool`). `WandPage.ui:32` `#Tree` (fil « style / hutte », hauteur 24, pleine largeur de 540) et `WandHutRow.ui:19` `#Name` : réduction. *Ces fichiers ont été remplacés le 2026-10-01 par `Structurize/BuildTool.ui` et `GridRow.ui`.*
7. **Coupeur (HyDomum, sans équivalent MC direct)**. `Cutter.ui:46` `#GroupName` et `Cutter.ui:65` `#ShapeName` (15 px, colonne de 330) : réduction. Textures d'emplacements vanilla, voir la ligne `IngredientSlot` plus haut.
8. **Localiser / surbrillance** (`plugin/.../ui/highlight/HighlightMarkers.java:28-31`).
   - `MapMarkerBuilder` gagne `withIconSize(MapMarkerIconSize)` (`Default`/`Major`, 64 px) et `withCompassImage(String)` (`S7/.../worldmap/markers/MapMarkerBuilder.java:40-48`). Le constructeur et `withName` sont inchangés.
   - `BlockMapMarker` précise que l'icône vaut « sur la carte et la boussole », et que `CompassIcon` absent veut dire « même icône » (`S7/server/core/universe/world/meta/state/BlockMapMarker.java`, documentation de `IconSize` et `CompassIcon`).
   - MC n'a pas de marqueur de carte pour la surbrillance (c'est déjà un ajout HyColony). Passer en `Major` ne rendrait pas le port plus fidèle : on ne le retient pas.
   - **[in-game]** : notre marqueur apparaît-il déjà sur la boussole ?
9. **Annonces de colonie : non retenu.** Pour « vous entrez dans la colonie X » et les notifications aux gestionnaires, MC écrit dans le **chat** : `MessageUtils.format(ENTERING_COLONY_MESSAGE, …).sendTo(player)` (`Colony.java`, `addVisitingPlayer` / `removeVisitingPlayer`), qui finit en `player.displayClientMessage(…, false)` (`api/util/MessageUtils.java:285,300,404`, `false` = chat, pas la barre d'action). Un titre d'événement (`EventTitleUtil.showEventTitleToPlayer(…, EventTitleStyle)`, N[653,683,691]) serait donc un écart à MC.
10. **Raids (futur).** MC montre la progression d'un raid dans une barre en haut de l'écran (wiki `minecolonies.com/wiki/systems/raid/`). L'équivalent Hytale est la barre de boss d'une rencontre, dont le nom accepte le balisage `<color>`/`<s>` en U7 si c'est une traduction (N[356]). À noter pour le jour des gardes et des raids.
11. **Styles runiques (`@RunicPanel`…) : non retenu.** C'est l'habillage des consoles gobelin et de l'établi d'augmentation (`A7 Pages/AugmentUpgradePage.ui`, `GoblinLabConsolePage.ui`). Nos fenêtres copient `@DecoratedContainer`/`@Container`, comme les pages vanilla standard (CLAUDE.md § 7), et rien dans MC n'appelle un habillage runique.
12. **`BlockLore` : non retenu pour les huttes.** La page se branche sur l'interaction `Use` du bloc (`"Page": { "Id": "BlockLore" }`, `S7/server/core/modules/interaction/InteractionModule.java:345`, `suppliers/BlockLorePageSupplier.java:42-87`), qui ouvre déjà la fenêtre de hutte (MC `AbstractBlockHut.use`). Elle lit le nom et la description dans `Item.TranslationProperties`, et un bloc sans description n'ouvre rien.
    - Motif utile à copier : `BlockLorePage.java:35-41` pose `#Message.TextSpans` (Message) et `#Message.Style.HorizontalAlignment`, ce qui confirme notre règle TextSpans.

## Changements du rendu client qui pourraient se voir

- **Aucun changement du moteur `.ui` classique n'est annoncé pour les pages serveur.**
  - Le seul point de N sur l'`Element` classique, rendu nullable (`HitTest`, `Find<T>`, parent du constructeur), précise « Runtime behavior is the same » (N[487]). Il ne vise que le code C# du client.
  - `hy:ShrinkTextToFit` et l'affirmation « ne réécrit jamais FontSize » ne concernent que Noesis (N[339]).
- **Sprites** : `Color` et `KeepLastFrame` en plus (N[654]). La chaîne `KeepLastFrame` n'apparaît que dans C7. Notre seul sprite est `$C.@DefaultSpinner`, dont la définition ne change pas.
- **HUD** : « les parties transparentes du HUD ne sont plus assombries » (N[598]). Nous n'avons pas de HUD (`HudManager`/`CustomUIHud` introuvables dans notre code).
- **Icône « objet inconnu »** pour tout objet sans icône (N[591], N[748]). C'est visible si une vue affiche un `ItemId` qui n'est pas un objet (fil `#Icon.ItemId`, par exemple `WandPage.java:138`, `BuilderResourcesTab.java:65`, `RecipesTab.java:93,109`). En 0.6.8 la case restait vide ou non définie ; en U7 un point d'interrogation apparaît. **[in-game]**
- **Infobulle d'objet** : `C7/.../Tooltips/ItemTooltip.ui` change (Cursed devient Ephemeral). Ça touche les infobulles de nos `ItemGrid`/`ItemIcon`, sans action de notre part.

## Vérifié sans impact

- **Limite d'imbrication à 32** (N[606,742]).
  - `ReadCursor.MAX_NESTING_DEPTH = 32` (`S7/protocol/io/ReadCursor.java:6-12`) est appliquée au décodage de `FormattedMessage` et de `WindowAction` (`enterNested`).
  - Nos messages ont 2 niveaux au plus : `Texts.translated` (`blockui/.../api/Texts.java:14-21`) met un `Message.translation` en paramètre d'un autre, jamais plus.
- **Serveur Noesis (paquets `UpdateServersideUIPage`, `ExecuteServersidePageCommand`, nouveaux `InitViewModelTypes` et `ViewModel*Definition`).** Aucun code serveur hors `PacketRegistry` ne les envoie (grep S7), et il n'y a pas d'API de plugin. Ce n'est pas une voie pour nos fenêtres.
- **`PreventDebugScreens`** (N[338]), **`GamepadCursor` / `PlayerMouseButtonEvent` / caméra à curseur** (N[688,745-747]), **annonces plein écran uniques** (N[551], client), **fil de mort** (N[357]), **`MapMarkerOverride.DisplayName`** (N[649], surcharge des marqueurs existants) : aucun usage chez nous.
- **`Player.getPlayerRef()`**, utilisé par `HighlightMarkers.java:24`, était déjà `@Deprecated(forRemoval = true)` en 0.6.8 (S6 `Player.java:1148`) et l'est toujours (S7 `Player.java:737`). `WorldMapManager.MarkerProvider` et `MarkersCollector` sont inchangés.
- **`ItemContainerBlock.StayOpenWhenEmpty`** (N[184]) : nouvelle option ; son absence garde le comportement 0.6.8.
- **Motifs vanilla qu'on cite** : `TriggerVolumeInspectorTabButton.ui` et `TriggerVolumeInspectorPage.ui` sont inchangés dans A7 (seules les lignes `NumberRow`/`Vec3Row` changent). `TriggerVolumeInspectorPage.java` n'évolue que sur les règles des volumes, pas sur `buildTabs`. `RespawnPage` et `ItemRepairPage` sont inchangés.
- **Inventaire natif du client** (`C7/.../InGame/Pages/Inventory/*.ui`) : aucun `.ui` ne change. La nouvelle page `InGame/Pages/Abilities` et les sections -11 et -12 ne sont pas dans nos panneaux, qui n'imitent que stockage, barre rapide et armure.

Reste incertain (**[in-game]**) :

- la largeur minimale qu'exige `ShrinkTextToFit` dans un `Label` à `FlexWeight` ;
- le chemin `../../Common/IngredientSlot.png` depuis `Pages/HyDomum/` ;
- la barre de vie des citoyens en 0.6.8 ;
- le marqueur de surbrillance sur la boussole ;
- l'icône « objet inconnu » dans nos `ItemIcon` ;
- l'effet du correctif de fuite de `CharacterPreviewComponent` sur nos pages.
