# HyColony : lunettes de constructeur (Build Goggles)

Spec validée avec l'utilisateur le 2026-09-26. Recherche : `docs/research/build-goggles-and-wand.md`.

## Objectif

Un joueur qui porte les lunettes voit, en fantôme, **ce qui reste à construire** sur chaque chantier en cours de sa colonie, dans un rayon de 50 blocs. Il sait ainsi où en est le constructeur et ce qu'il manque, sans ouvrir de fenêtre.

## Règles de jeu

- **Objet** : « Lunettes de constructeur » (`HyColony_Build_Goggles`), qui se porte en casque (emplacement `Head` de l'armure) et ne donne aucune protection. Il utilise le modèle cosmétique de lunettes vanilla (`Cosmetics/Head/Goggles.blockymodel`).
- **Recette** : à l'établi, avec des ingrédients vanilla (verre, cuir, fer). La recette exacte et le niveau d'établi sont vérifiés dans les assets. Quand la baguette existera, elle entrera dans la recette, comme dans MineColonies.
- **Chantiers affichés** : les ordres de travail **réclamés par un constructeur** (en cours) de la colonie où se trouve le joueur, ou de la plus proche, et dont la hutte est à 50 blocs ou moins du joueur (`buildgogglerange` de MineColonies). Tous les types d'ordre : construction, amélioration et réparation. Pour une démolition, les blocs qui restent à retirer.
- **Contenu du fantôme** : uniquement les **blocs qui restent à poser**, calculés en comparant le plan au monde.
- **Rafraîchissement** : par paliers, jamais à chaque bloc.
  - L'aperçu d'un chantier est recréé quand le joueur met ou retire les lunettes, quand il entre dans la zone, ou toutes les **100 ticks** (5 s) si des blocs ont été posés depuis.
  - Il disparaît quand l'ordre se termine ou est annulé.
- **Visibilité** : seul le porteur voit ses fantômes. Retirer les lunettes les efface.

## Écarts à MineColonies (à reporter dans la spec SP1+2 § 11)

- Blocs restants au lieu du plan complet.
- Chantiers en cours seulement. MC affiche tous les ordres de la colonie, la boîte pour une démolition et l'ancre des huttes de niveau 0.
- Pas de contours, pas de mode accroupi, pas de touche d'activation : porter les lunettes suffit.

## Architecture

- **Cœur** (`construction/goggles` ou un paquet équivalent, dans la limite de 15 fichiers) :
  - `GogglesView` calcule, pour un joueur et sa position, la liste des chantiers visibles : `(ordre, position de la hutte, blocs restants, BlockPos → BlockState)`. Il s'appuie sur `StructurePlan` et `WorldBlocks`, avec un parcours borné.
  - Un service de rafraîchissement suit, par joueur porteur, les aperçus affichés et décide quand les recréer (palier de 100 ticks, blocs posés, entrée et sortie de zone, fin d'ordre).
  - Un nouveau **port** `PreviewPort` (dans `kernel/port`) : `show(UUID player, String id, BlockPos origin, List<Block>)`, `hide(UUID player, String id)` et `hideAll(UUID player)`.
  - Tests TDD : chantiers visibles (rayon, en cours seulement, colonie), blocs restants, décisions de rafraîchissement.
- **Plugin** :
  - un système qui détecte le casque porté (`InventoryChangeEvent` sur `InventoryComponent.Armor`, emplacement 0, plus une lecture à l'entrée dans le monde) et prévient le cœur ;
  - `HytalePreviewPort` : une entité en mémoire (`NetworkId + Transform + PrefabPreview + NonSerialized`), construite à partir des `BlockChange` des blocs restants et rendue visible **uniquement** pour ce joueur, grâce à un système de visibilité sur le modèle de `HideEntitySystems`. Recréer un aperçu revient à supprimer l'entité puis la recréer ;
  - les assets : l'objet, sa recette, les traductions en-US et fr-FR, et `id-map.json`.

## À vérifier en jeu

Ces points sont marqués **[in-game]** dans la recherche.

1. Le client affiche une entité `PrefabPreview` créée sans `PersistentPrefabPreview`.
2. Le filtre de visibilité par joueur fonctionne.
3. Le modèle des lunettes s'affiche en casque.

Si le point 1 échoue, le repli consiste à écrire un prefab temporaire et à utiliser `PersistentPrefabPreview`.

## Tests en jeu (`docs/TESTING.md`)

1. Fabriquer les lunettes à l'établi.
2. Les porter près d'un chantier en cours : le fantôme des blocs restants apparaît. Il se réduit pendant que le constructeur pose des blocs, et disparaît à la fin du chantier.
3. Un second joueur sans lunettes ne voit rien.
4. Retirer les lunettes efface les fantômes.
