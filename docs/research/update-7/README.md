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
