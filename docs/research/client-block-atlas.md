# Client Hytale : atlas des blocs et scintillement (analyse du binaire)

Question : existe-t-il, côté client, un chemin qui ajoute une texture de bloc **sans** scintillement, que le serveur pourrait déclencher ? Réponse : **non**. Le scintillement à chaque nouvelle paire de HyDomum (`domum-ornamentum.md`, « Séquencement des PNG neuves ») est une limite du client. On le contourne en n'ajoutant plus de texture : voir « Contournement vérifié : la planche ».

Analyse faite le 2026-09-30, à but d'interopérabilité (comprendre quels paquets le client accepte). Lecture seule : rien n'est modifié ni redistribué.

## Binaire étudié

- `HytaleClient.exe` du canal pre-release (71 571 296 octets, daté du 2026-09-29, SHA-256 `df8f131432518a4…`).
- C# compilé en **NativeAOT** (.NET 10.0.12) : pas d'IL, donc pas de décompilation en C#. Désassemblage x64 seulement.
- Les métadonnées de réflexion sont embarquées (section RTR 313, en-tête `0xDEADDFFD`) : les noms de types et de méthodes sont lisibles. Les métadonnées de pile sont vides (section 327 de 4 octets).
- Pour relier un nom à une adresse, on passe par la table `InvokeMap` (section 306) et les références externes (section 308, pointeurs relatifs de 32 bits). On n'y trouve que les méthodes appelables par réflexion : surtout les lambdas. Une méthode se retrouve par le relais de délégué de sa lambda (`lea r8, lambda ; jmp ctor`), puis par l'appelant de ce relais.
- Les littéraux C# sont des chaînes UTF-16 figées (objet = table des méthodes + longueur + caractères), repérables par leurs `lea [rip+…]`.
- Outils : Python, `pefile`, `capstone`, `numpy`. Scripts non versionnés (scratchpad de la session).

## Chaîne de traitement de `UpdateBlockTypes`

Adresses virtuelles relatives (RVA) du binaire ci-dessus. Champs du paquet côté client : `+0x14` type, `+0x15` `updateBlockTextures`, `+0x16` `updateModelTextures`, `+0x17` `updateModels`, `+0x18` `updateMapGeometry`.

1. **`ProcessUpdateBlockTypesPacket`** (`0x2c0c80`, thread réseau/fond) appelle `PrepareBlockTypes` (`0x50d460`) avec `updateBlockTextures || updateModelTextures`. Il prépare ensuite une fermeture pour le thread principal avec `textures||modelTextures`, `models`, `mapGeometry` et le type.
2. **`PrepareBlockTypes`** :
   - **drapeau de textures à faux** : il saute la reconstruction et cherche chaque nom de texture dans l'atlas existant (`0x1af5140`, dictionnaire de l'atlas). Un nom absent donne un emplacement par défaut : c'est l'« autre région de l'atlas » vue en jeu le 2026-09-28 ;
   - **drapeau à vrai** : il recollecte **toutes** les textures de **tous** les types de bloc (`Failed to get PNG dimensions for:`, `Failed to load block texture:`), puis appelle la reconstruction de l'atlas (`0x1af5210` → `0x1af5250` → `0x1af5ae0`, avec forçage).
3. **Reconstruction de l'atlas** (classe générique « dynamic-atlas », résultat `AtlasRebuildResult<T>` { `Replaced`, `RejectedKeys`… }) :
   - pour chaque taille de page candidate, `0x1af63a0` place **toutes** les entrées, triées au préalable (`<SortEntries>b__64_0`), et garde la meilleure disposition ;
   - `0x1af6fa0` valide la disposition. La liste des entrées est vidée puis remplie avec la nouvelle, et `Replaced` passe à vrai.
   - **Aucune insertion incrémentale.** Une texture ajoutée peut déplacer toutes les autres (tri, puis rangement en étagères, `ShelfAtlasLayout`).
4. **Thread principal**, lambda `<ProcessUpdateBlockTypesPacket>b__0` (`0xa96ae0`) :
   - si l'atlas a une remplaçante en attente (`0x1af4e60`), `0x1af5300` alloue un **nouveau stockage de texture GPU** et y envoie les pages (`Could not allocate atlas texture storage …`). `0x49c890` met ensuite à jour les tailles d'atlas des shaders ;
   - `0x5122e0` a deux branches :
     - **si `textures || modelTextures || models`** : `0x517940` jette le maillage de **tous les tronçons affichés** (`All rendered chunks were discarded.`). Ils sont refaits sur les images suivantes, **et c'est ce qu'on voit comme scintillement** ;
     - **sinon, si `updateMapGeometry`** : seuls les tronçons qui contiennent un des ids reçus sont marqués à refaire (pas de scintillement global).

`ProcessRequestCommonAssetsRebuildPacket` (`0x2b98f0`) relance tout, en plus large.

## Conséquences pour HyDomum

- Une texture qui n'est pas dans l'atlas **n'y entre que** par `updateBlockTextures` (ou `updateModelTextures`), donc par :
  - une reconstruction complète de l'atlas ;
  - un nouvel envoi GPU ;
  - l'abandon de tous les tronçons affichés.

  Aucune combinaison de drapeaux ni aucun ordre de paquets ne l'évite. Le serveur ne peut rien y changer.
- Ce qu'on fait déjà est le minimum : un seul paquet avec le drapeau par lot, et les créations regroupées.
- Un `BlockType` neuf qui ne référence **que des textures déjà dans l'atlas** n'a besoin d'aucun drapeau de textures. Il s'affiche sans scintillement, avec `updateMapGeometry` pour ne refaire que les tronçons concernés. C'est la seule voie sans scintillement, mais une texture composée (cadre + remplissage) n'en fait pas partie.
- Seul moyen d'éviter le scintillement pour les paires composées : que leurs textures soient dans l'atlas à la connexion, c'est-à-dire générées avant.

## Contournement vérifié : la planche (2026-09-30)

- Au démarrage, HyDomum dessine une seule texture, la planche : la face de chaque matériau DO dans une case de 32 px. Un bloc caché la lit, donc chaque client la met dans son atlas dès la connexion.
- Pour une paire neuve, on n'envoie que le **modèle** du gabarit, remappé : ses faces visent la case du 1ᵉʳ ou du 2ᵉ matériau dans la planche (`PaletteModel`). On l'envoie avant le bloc, puis on inscrit le bloc **sans aucun drapeau de reconstruction**.
- **Vérifié en jeu (2026-09-30)**, avec `/hydomum palette` sur 10 colombages aux paires jamais utilisées, dont plusieurs en rafale : **aucun scintillement**, rendu juste à l'œil. Le remappage du prototype plaçait pourtant quelques faces retournées ou tournées sur le mauvais matériau (bandes de 2 texels) ; c'est corrigé par `FaceRect` (spec `2026-09-30-hydomum-palette-design.md`). Un modèle envoyé en cours de partie est donc lu sans `updateModels`, comme le laissait prévoir `PrepareBlockTypes`.
- **Intégration vérifiée en jeu (2026-09-30)** : toutes les formes à deux matériaux (colombages, bardeaux avec coins, portes et trappes ouvragées, murs de papier), l'établi et le redémarrage, **sans aucun scintillement**, icônes justes. La planche fait environ 540 ko (576×544 px, 296 matériaux).
- Il reste l'icône, qui est celle du gabarit dans le test. Les icônes ont leur propre atlas, et une icône générée arrive déjà sans scintillement (`domum-ornamentum.md`, 2026-09-28).

## Adresses (pour refaire l'analyse sur une autre version)

| Rôle | RVA |
|---|---|
| `ProcessUpdateBlockTypesPacket` | `0x2c0c80` |
| sa lambda du thread principal | `0xa96ae0` |
| `PrepareBlockTypes` | `0x50d460` |
| atlas : recherche d'un nom | `0x1af5140` |
| atlas : reconstruction (entrée, forcée, principale) | `0x1af5210`, `0x1af5250`, `0x1af5ae0` |
| atlas : placement, validation | `0x1af63a0`, `0x1af6fa0` |
| atlas : remplaçante en attente ?, application GPU | `0x1af4e60`, `0x1af5300` |
| mise à jour des blocs côté rendu | `0x5122e0` |
| abandon de tous les tronçons affichés | `0x517940` |
| `ProcessRequestCommonAssetsRebuildPacket` | `0x2b98f0` |

Ces adresses changent à chaque build du client. Les chaînes citées et les noms de lambdas permettent de les retrouver.
