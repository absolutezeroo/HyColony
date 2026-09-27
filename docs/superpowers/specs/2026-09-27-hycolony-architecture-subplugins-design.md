# HyColony : assainissement de l'architecture et sous-plugins

Validé avec l'utilisateur le 2026-09-27 (« faisons ça »). Sources : `docs/research/architecture/` (audits A et B, bonnes pratiques, duplication), dépôt Aetherhaven pour l'idée des sous-plugins (**tous droits réservés : idée seulement, aucun code repris**).

## Objectif

Rendre l'ajout d'un métier, d'un bâtiment ou d'un pack de contenu **local** : de nouveaux fichiers plus un enregistrement, sans modifier à la main une vingtaine de fichiers partagés, avant la suite de SP3. Puis livrer le mécanisme de sous-plugins, validé en sortant les styles en premier pack.

## Portée

1. **Sauvegardes tolérantes.**
   - Une requête dont le type ou l'état est inconnu est ignorée, avec une ligne de journal ; la colonie ne se verrouille plus.
   - Un métier inconnu est conservé brut et réécrit tel quel, comme les bâtiments et modules inconnus ; le citoyen garde son affectation.
2. **Nettoyage de l'API des bâtiments.**
   - `TickingModule` n'a plus qu'une méthode, `onColonyTick(Colony, Building)`.
   - `BuildingEventsModule` est réellement distribué au retrait (avant l'annulation des requêtes) et à la fin d'amélioration (hausse de niveau ou reconstruction après déconstruction, jamais une réparation), comme MC `AbstractBuilding`. MC n'a pas d'événement de pose pour les modules : `onPlaced` est supprimé. Le test `instanceof WorkerModule` de `ColonyBuildingListener` disparaît.
   - Le code mort est supprimé : `ConstructionPorts.unavailable()` et les lignes PMD périmées de `BuildingPage`.
3. **Socle commun des ouvriers.** Les parties génériques de MC `AbstractEntityAIBasic` enfermées dans le constructeur sortent en composants partagés : `WorkerStock` (dépôt, `keepX`, outil le plus efficace) et `ToolRequests` (`checkForToolOrWeapon`). Le constructeur les utilise, sans changer de comportement. Pas de classe abstraite nouvelle.
4. **Enregistrement unique.** `CoreFeatures.register(...)` déclare en un seul endroit les types de bâtiments et de métiers du cœur. Côté plugin, les huttes (`HutBlockSystems`), les objets et le câblage lisent ces registres au lieu de listes écrites à la main. Chaque fonctionnalité optionnelle a un drapeau dans `config.json`, section `HyColony`.
5. **Onglets de hutte fournis par les modules.**
   - `BuildingView` porte une liste scellée d'onglets de module, à la place des `Optional<*Tabs>`.
   - Le plugin associe un rendu à chaque type d'onglet par un `switch` exhaustif.
   - Chaque onglet a son propre fichier `.ui`, au lieu d'un `Building.ui` qui contient tout.
6. **Règles.** ArchUnit fige les frontières vraies aujourd'hui :
   - `building` ne dépend ni de `job` ni de `logistics` ;
   - aucune fonctionnalité ne dépend de `colony.action`, `colony.view` ou `colony.persistence` ;
   - personne ne dépend de `construction.builder` ;
   - pas de cycle entre les sous-paquets de `logistics` ;
   - héritage de profondeur 1 au plus sous `Job` et `JobAI`.

   Les règles qui touchent des garde-fous (PMD `NcssCount`, échec sur une exception PMD inutilisée) attendent une session déverrouillée.
7. **Sous-plugins.**
   - Un dossier `subplugins/<Nom>/` dans les ressources contient :
     - un manifeste (nom, version, activé par défaut) ;
     - ses assets `Common/` et `Server/` ;
     - ses traductions ;
     - ses fragments d'`id-map.json` et de `styles.json` ;
     - le cas échéant, une classe qui s'enregistre auprès de `CoreFeatures`.
   - Au démarrage, les packs activés dans `config.json` sont enregistrés auprès de Hytale (`AssetModule.registerPack`, à vérifier dans vineflower) et du cœur.
   - Un pack désactivé laisse les sauvegardes lisibles (point 1).
8. **Premier pack.** Les styles Outlander et Kweebec sortent en sous-plugins d'assets seuls (`Styles_Outlander`, `Styles_Kweebec`), activés par défaut. Le comportement en jeu est inchangé.

Hors portée : la défense, la recherche, les autres métiers de SP3, et l'accès multi-module (`modules(Class)`), prévu avant la défense et l'école.

## Principes

- Composition, pas de nouvelle hiérarchie abstraite. Records et types scellés là où ils rendent les cas explicites.
- Aucun changement de comportement en jeu, sauf ceux listés ; les tests existants restent verts.
- Toute règle MC reste citée ; aucun écart nouveau non documenté.

## À vérifier en jeu

- Démarrage, `/hycolony selftest`, chargement d'une colonie existante : rien ne change.
- Désactiver un pack de style dans `config.json` : ses plans disparaissent de la baguette, et une colonie qui l'utilise se charge quand même.
- Onglets du constructeur, de l'entrepôt et du livreur identiques à avant.
