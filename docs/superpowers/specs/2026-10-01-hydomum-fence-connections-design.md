# HyDomum : clôtures et murets reliés comme dans Minecraft

Validé avec l'utilisateur le 2026-10-01 : la règle de Minecraft, en Java, pour les clôtures et murets HyDomum (« on fait comme Minecraft »), qui reconnaissent aussi les clôtures, murets et barreaux vanilla par leur famille. Suite du correctif « poteau seul et bout » (`HyDomum_FenceConnectedBlockTemplate`, `tools/domum/blocks/compat.py`). Les blocs vanilla gardent leurs règles : les leur changer par des patchs Hytalor a été essayé puis abandonné (« ça rend pas très bien » : poteaux et bouts découpés dans leurs modèles, liaisons à 180°).

## Objectif

Chaque clôture ou muret HyDomum choisit sa forme (poteau seul, bout, droit, angle, T, croix) d'après ses quatre voisins, avec la règle de Minecraft 1.20.1 (la version de DO), que DO hérite sans la changer (`DO: block/AbstractBlockFence.java`, `AbstractBlockWall.java`, `AbstractBlockPane.java` étendent les blocs de Minecraft ; aucun `connectsTo` dans DO) :

- `FenceBlock.connectsTo` : une face pleine (`isFaceSturdy`) d'un bloc qui n'est pas une exception, le côté d'un portillon (`FenceGateBlock.connectsToDirection`), une clôture de la même famille (`isSameFence` : en bois avec en bois, `WOODEN_FENCES`, les autres entre elles) ;
- `WallBlock.connectsTo` : un muret (`WALLS`), des barreaux ou une vitre (`IronBarsBlock`), le côté d'un portillon, une face pleine ;
- `IronBarsBlock.attachsTo` (les barreaux, ici seulement vus comme voisins) : des barreaux, un muret, une face pleine.

DO marque sa clôture `FENCES` et `WOODEN_FENCES` (`datagen/fence/FenceCompatibilityTagProvider.java`), son muret `WALLS` (`datagen/wall/vanilla/WallCompatibilityTagProvider.java`). Les règles ci-dessus viennent du code de Minecraft, absent de `sources/` : elles sont citées de mémoire (relecture `mc-fidelity-checker` du 2026-10-01).

## Faits vérifiés (Hytale 0.7.0-pre.4)

Détail et références : `docs/research/connected-blocks.md` § 5.

- Un type de règle à nous s'inscrit dans `ConnectedBlockRuleSet.CODEC` pendant `setup()`, avant le décodage des `BlockType`. Une sous-classe de `CustomTemplateConnectedBlockRuleSet` hérite des clés de son codec ; `DynamicBlockTypeFactory.copy` la recopie avec son type.
- Les règles `CustomTemplate` ne vont pas au client : rien n'est perdu côté client.
- Poser ou casser un bloc **par un joueur** réévalue les blocs connectés voisins (`BlockPlaceUtils.java:499`, `BlockHarvestUtils.java:1374`). Le constructeur, la baguette, les prefabs et la physique posent sans prévenir les voisins.
- Face pleine : `BlockType.getSupporting(rotation)`, type `"Full"`. Un cube solide les a toutes ; nos clôtures et murets ne déclarent que le haut et le bas.
- Les blocs vanilla du gabarit `WallConnectedBlockTemplate` dont le motif `Straight` est eux-mêmes : 23 clôtures (`*_Fence`, dont 11 `Wood_*_Fence`), 43 murets (`*_Wall`), 2 barreaux (`*_Bars`), plus les 2 blocs d'angle des barreaux (`*_Bars_Corner`, motif `Corner`). Les portillons n'ont que le motif `Gate`. Un motif d'id (`*_Wall`) attraperait aussi des torches et des lianes murales : on écrit la liste exacte.
- `"Parent": "super"` ne fait pas hériter le `BlockType` imbriqué d'un objet, et des patchs Hytalor des blocs vanilla rendaient mal (voir l'en-tête) : les blocs vanilla ne sont pas modifiés.

## Conception

### Cœur (`domum/core`, paquet `dev.hydomum.core.connect`, Java pur, TDD)

- `Side` : nord, ouest, sud, est, dans l'ordre où un pas de lacet Hytale les tourne.
- `Joiner` : la famille du bloc évalué : `WOODEN_FENCE`, `FENCE`, `WALL`, `PANE`.
- `Neighbour(NeighbourKind kind, boolean fullFace, int yaw)` : ce que le plugin a lu d'un voisin. `NeighbourKind` : les quatre familles, `GATE`, `OTHER`.
- `Connections.joins(Joiner, Side, Neighbour)` : la règle de Minecraft ci-dessus. Les côtés d'un portillon sont à l'est et à l'ouest au lacet 0, comme la forme `Gate` du gabarit vanilla.
- `ConnectedShape.of(Set<Side>)` : la forme et le plus petit lacet du gabarit HyDomum (bout vers le nord, droit est-ouest, angle ouest-sud, T est-ouest-sud).

### Plugin (`domum/plugin`, paquet `dev.hydomum.plugin.connect`)

- `HytaleFenceRules extends CustomTemplateConnectedBlockRuleSet`, type `"HyDomum_Fence"`, clé `"Joins"` (`WoodenFence`, `Wall`). Son `getConnectedBlockType` lit les quatre voisins, demande au cœur les côtés reliés puis la forme, et rend le bloc du motif de cette forme avec ce lacet. Un voisin dans une section non chargée : `Optional.empty()` (la forme ne change pas). Une exception est journalisée (SEVERE la première fois, FINE ensuite) et rend `Optional.empty()`.
- `HytaleNeighbours` lit un voisin : notre famille par sa règle `HytaleFenceRules`, un portillon par sa forme `Gate` (`getShapesForBlockType`), un bloc vanilla par la famille que l'id-map donne à son id (celui du bloc d'un état `*X_State_Definitions_Y`), son lacet, sa face tournée vers nous. Une case de remplissage n'a pas de face pleine.
- `HyDomumPlugin.setup()` inscrit le type, avec les familles vanilla lues dans l'id-map (`DomumIds.connections`).

### Générateur (`tools/domum`)

- Clôture et muret HyDomum : `"Type": "HyDomum_Fence"`, `"Joins"` `WoodenFence` et `Wall` (`compat.py`).
- Familles vanilla (`blocks/vanilla_fences.py`) : l'id-map reçoit `connections` (`WOODEN_FENCE`, `FENCE`, `WALL`, `PANE` → ids exacts), lus dans les assets : chaque bloc droit du gabarit vanilla et les blocs que ses motifs nomment (angles des barreaux), portillons à part. La famille vient de l'id (`Wood_*_Fence`, `*_Fence`, `*_Wall`, `*_Bars`).

## Écarts avec Minecraft

- Les exceptions de Minecraft (`isExceptionForConnection` : feuilles, barrière, citrouilles, pastèque, boîtes de Shulker) n'ont pas de face pleine dans Hytale (modèles, ou bloc vide pour la barrière) : seule la face pleine décide.
- La forme ne suit que les poses et cassages d'un joueur (Hytale), pas ceux du constructeur, de la baguette ou de la physique.
- Les clôtures, murets et barreaux vanilla gardent les règles vanilla : ils se relient aux nôtres par nos tags de face, quelle que soit la famille, jamais à une face pleine, et n'ont ni poteau seul ni bout.
- Une case de remplissage (bloc de plusieurs cases) n'a pas de face pleine.
- Les murets ne se relient pas au mur de papier HyDomum, qui garde son gabarit (Minecraft relie un muret à une vitre).
- Inchangés : pas de côté haut (`tall`) ni de poteau levé par le bloc du dessus pour les murets ; pas de portillon abaissé (`in_wall`) ; le mur de papier ne se relie qu'aux murs de papier.

## Hors périmètre

Le mur de papier (une vitre) pourra passer à la même règle (`PANE`) ; il faudrait alors tourner ses formes comme le gabarit des clôtures.

## Tests

- Cœur : `ConnectionsTest`, `ConnectedShapeTest`.
- Générateur : `tools/domum/check_connected.py` (`fence_and_wall_shape_by_their_neighbours`, `vanilla_fences_are_named_by_family`).
- En jeu (`docs/TESTING.md`, étapes 140 et 140 bis).
