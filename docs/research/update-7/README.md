# Update 7 (serveur 0.7.0) : synthèse de l'impact sur HyColony

Recherche du 2026-09-29 sur **0.7.0-pre.4** (Maven `pre-release`, assets du launcher `install/pre-release`), comparé à 0.6.8. Nos modules ont été compilés contre le jar U7 dans un worktree temporaire. Le détail et les preuves sont dans les rapports par axe :

- [a-java-api.md](a-java-api.md) : API Java (javap des 224 classes importées, `-Xlint`, `@RestrictedApi`)
- [b-assets.md](b-assets.md) : ids, chemins, schémas JSON, drops, langues
- [c-ui.md](c-ui.md) : `.ui`, fenêtres, HyBlockUI, contournements
- [d1-citizens.md](d1-citizens.md) : citoyens, PNJ, pathfinding, hiérarchie d'entités
- [d2-world-gameplay.md](d2-world-gameplay.md) : blocs, farming, protection, sauvegardes, carte

Source : [notes de version Update 7](https://hytale.com/news/2026/9/pre-release-patch-notes-update-7).

## Bloquant (à faire au passage à 0.7.0)

| # | Où | Problème | Correctif |
|---|----|----------|-----------|
| 1 | `HytaleWorldEffects.java:149-168` | `BlockHealthChunk` et `getBlockHealthChunkComponentType()` supprimés : ne compile pas | `BlockHealthSection` sur la ref de section (`getHealth`, `damage`, `markNeedsSaving`) ; garder `MIN_HEALTH` > 0 |
| 2 | `HytaleWorldQuery.java:48` | `ISpawnProvider.getSpawnPoint` remplacé par `getSpawnPointAsync` : ne compile pas | `getNow(null)`, sinon `Optional.empty()` ; jamais `join()` sur le thread du monde |
| 3 | `HytaleWorldQuery.java:72-80` | `BlockChunk.getEnvironment` est `@RestrictedApi` : Error Prone fait échouer le build | `EnvironmentSection.get` + `WeatherResource.getEffectiveWeatherIndex` |
| 4 | 3 `manifest.json` + `gradle.properties:68` | `>=0.6.8 <0.7.0` refuse `0.7.0-pre.4` (SEVERE et alerte aux opérateurs, mais le mod charge) | `>=0.7.0-pre.4 <0.8.0` au moment d'épingler U7 |
| 5 | jar actuel | chargé sur U7, il lève `NoSuchMethodError` (une `Error`, non attrapée) à la pose d'une hutte ou à un coup du bâtisseur | recompiler contre 0.7.0 ; ne pas tester le jar 0.6.8 sur U7 |

## À migrer (non bloquant)

- `HytaleItemCatalog.toolType` (l. 228-239) : associer les types de récolte `Metals` et `GoblinMetal` à la pioche.
- `BenchTiers.set` : appeler `BenchBlock.notifyTierUpgraded`, comme le fait le jeu vanilla.
- `FarmBlocks.java:96` : `markNeedsSaving` après `setTicking`.
- `HytaleWorldQuery.isLoaded` : tester la section plutôt que la colonne (ne compte qu'en monde cubique).
- `tools/decorations/generate.py:21` et `tools/domum/tags.py:18` : pointer sur les nouveaux assets et régénérer (pots de fleurs, tags de HyDomum).
- `docs/research/sp4-sleep-home.md:189` : les lits partagent désormais `Block_Bed` (tag `Type=Bed`).
- La Javadoc de `InventoryDrop` cite 0.6.8.

## Sauvegardes : à faire avant tout essai

Un monde ouvert par U7 n'est **plus lisible par 0.6.8** : les sections passent en version 7 et les colonnes en version 4. Il faut copier `run/universe` avant le premier lancement en U7. Le JSON des colonies n'est pas touché.

## Changements de comportement à vérifier en jeu

- Pathfinding : après 3 essais bloqués, pause de 3 à 5 s (`ThrottleDelayRange`). MC ne marque pas de pause : si c'est visible, mettre `[0,0]` dans le rôle.
- Un citoyen en eau peu profonde n'entre plus en eau profonde.
- Apparition : la recherche verticale est limitée à ±16 blocs.
- La hitbox du bloc lumineux de surbrillance (échelle 1.05) grandit avec son échelle.
- Pluie sur les cultures : décidée par la carte des hauteurs, un toit non opaque la laisse passer.
- Les fissures après migration (point 1).
- Le français est enfin proposé par le client : nos `fr-FR` s'affichent pour la première fois.

## Opportunités fidèles à MineColonies

- **Citoyens** : `"HiddenUIComponents": ["Healthbar","CombatText"]` dans le rôle, car MC n'affiche que le nom et l'icône d'état (U7 seulement).
- **Cisailles** : MC `WorkerUtil.getBestToolForBlock` et `USE_SHEARS`. Ajouter `SHEARS` à `ToolType` pour que le défrichage rende les feuilles.
- **Libellés trop longs** : `LabelStyle(ShrinkTextToFit, MinShrinkTextToFitFontSize)` existe **déjà en 0.6.8**, utilisable tout de suite. Les candidats par fenêtre sont dans `c-ui.md`.
- **HyBlockUI** : `IngredientSlot@2x` et `IngredientSlotValid@2x` sont maintenant vanilla (octet pour octet) ; supprimer nos copies.
- **Futurs raids** : le nom d'une barre de boss accepte le balisage (barre de progression MC).
- **Carte de colonie** (MC via JourneyMap) : marqueurs `Major`. C'est un ajout, à valider.

Écartés, faute d'équivalent MC (détail dans les rapports) : titres d'événement, styles runiques, `BlockLore`, rayons, sursaut `Hit_Interrupt`, `BlockBreakingBypassTag`, météo forcée, `StayOpenWhenEmpty`, `MergeRadius`, `TickProcedure`.

## Vérifié sans impact

- Pages, fenêtres, builders UI, `ItemContainer`, commandes, codecs, permissions, événements ECS, `BlockOperations.setBlock`, et la réflexion de HyDomum sur `CommonAssetModule.assets`.
- Tous les ids de l'id-map, les 4 538 chemins d'assets cités, les 38 prefabs de `styles.json` (CRC identique), les styles `$C.@…` de nos `.ui`.
- La hiérarchie d'entités : aucune entité n'a de parent.
- Aucune borne Y 0..319 dans le code.
- HyBlockUI et HyDomum compilent contre U7.

## Passage à 0.7.0-pre.5 (partie 5, 2026-10-01)

Vérifié sur le jar Maven `0.7.0-pre.5` (révision `70c9872b`), décompilé, et sur les assets `pre-release-0.7.0-pre.5-Assets.zip`, comparés à pre.4.

- **Seule casse** : `EntityEffect.getLocale()` est supprimé (la clé `Locale` est remplacée par `DeathMessageKey`). `CitizenFireImmunitySystems` reconnaît les braises par la clé `server.general.deathCause.block`. Seuls `Block_Damage` (cause `Physical`) et `Environmental_Block_Damage` (cause `Environmental`) la portent, et les pièges portent `…deathCause.spikes`/`…snapjaw` : le couple (cause, clé) reste propre aux braises.
- **Sans effet** : la suppression de `IChunkAccessorSync`, `IWorldChunks(Async)`, `ChunkAccessor`, `OverridableChunkAccessor`, `LocalCachedChunkAccessor` et `WorldChunk.getBlockComponentEntity` (nous passons déjà par les sections et `BlockModule.getBlockEntity`), et les changements de `TargetUtil` (`blocksToIgnore` passe en `Collection<Int3OpenHashSet>`, non utilisé) et de la qualité des piles (clé `QualityOverride`, `ItemStack.java:51-53, 77` ; `HytaleItemCatalog` lit la qualité de l'`ItemToolSpec`). La durabilité accrue des outils est lue dans les assets.
- **Assets** : aucun asset retiré n'est cité par nos sources ou nos outils ; tous les ids des id-maps existent ; les générateurs (`tools/vanilla`, `tools/domum`) ne changent rien.
- **Porter un bloc** (`CarryBlockInteraction`) passe par `BlockHarvestUtils.performBlockBreak`, qui déclenche `BreakBlockEvent` (`BlockHarvestUtils.java:454-456`) : notre protection l'annule. Mais l'annulation ne défait pas le portage : `takeBlockEntity` a déjà retiré l'entité de bloc et `CarriedBlock` est déjà sur le joueur (`CarryBlockInteraction.java:75-83`). Le bloc reste en place sans son conteneur, et le joueur en porte une copie : c'est une duplication. Le défaut existait déjà en pre.4 et reste limité aux deux blocs portables, `Furniture_Goblin_Pot_Loot` et `Throwable_Explosive_Barrel`. Pour le corriger, il faudrait refuser l'interaction avant `CarryBlock`. `BlockCarriedEvent`, nouveau, n'est envoyé qu'à l'entité de bloc portée et n'est pas annulable.
- **Comportement** : `SectionUnloadingSystem` applique désormais à toutes les sections, Y 0..319 compris, la règle « HOT » : `dx²+dy²+dz² ≤ maxHotLoadedRadiusSq`, en sections, soit une sphère de 8 sections par défaut (`ChunkTracker.java:635-653`, rayon réglable l. 155-156). Une section non HOT cesse de ticker après son délai, même dans une colonne chargée. Avant, elle suivait sa colonne (via `ChunkSystems.OnNonTicking`, supprimé), qui était HOT dans un cercle 2D `dx²+dz² ≤ 64` (`getChunkVisibility`). La zone qui tick n'est donc plus un cylindre mais une boule : au bord, l'écart vertical compte. Exemple : un joueur à 7 chunks à l'horizontale et 4 sections plus haut donne 49 + 16 = 65 > 64, donc un citoyen qui tickait en pre.4 s'arrête. `ChunkSection.addKeepLoaded` garde la section chargée, mais pas en tick : elle passe quand même `NonTicking` (`SectionUnloadingSystem.java:114-120`). `WorldChunk.addKeepLoaded` existait déjà en pre.4.
- **Opportunités** :
  - `SectionReader` (`universe/world/accessor`), avec son `SectionCursor`, pourrait remplacer le motif « référence de section + composant + test de nullité » de `HytaleGround`, `TargetCells`, `HytaleNeighbours`, `CitizenMantleSystem` et `HytaleWorldBlocks`. **Écarté** : chaque instance alloue un `Int3ObjectOpenHashMap`, ce qui fait des allocations par tick dans les chemins chauds (CLAUDE.md § 4), et ce cache n'est jamais vidé si l'instance est gardée. De plus, `getBlock` rend 0 pour une section non chargée : il faut `moveTo`/`hasStorageInMemory` pour distinguer « non chargé » de « vide ».
  - Le paquet `server/core/modules/ui` (`UIModule`, `UIPage`, `UIHudWidget`, modèles de vue XAML Noesis) est un futur système d'interface côté serveur. Il est désactivé (propriété `hytale.serverside_ui_preview`, sinon `IllegalStateException` « still in development »), donc inutilisable pour l'instant.
  - `ConfirmationPage` (`OpenCustomUI` `Confirmation`) : sans usage, nos fenêtres portent celles de MC.
