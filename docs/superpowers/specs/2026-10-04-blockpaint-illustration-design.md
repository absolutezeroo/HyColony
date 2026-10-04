# Spec : la passe d'illustration de blockpaint

Date : 2026-10-04. Suite de `2026-10-03-blockpaint-surfaces-design.md` (le système en couches) et de
`2026-10-03-blockpaint-decals-design.md`. Outil seulement : `tools/blockpaint/` et les modules des modèles ; aucune
règle de jeu. Demandée par l'utilisateur le 2026-10-04.

## 1. Le problème

Le système en couches dit vrai : la matière, ses revêtements, son âge, son lieu, son usage, ses dégradations, ses
dépôts et son histoire. Mais il empile : chaque couche est juste, et l'ensemble se lit comme un excellent empilement
procédural plutôt que comme une texture peinte avec intention. Un peintre « hand-painted » ne s'arrête pas à la
matière : il compose. Il pose de grandes masses de valeur avant le détail, laisse des zones calmes, choisit quelques
arêtes à éclairer, appuie les jonctions, sépare deux bruns voisins et retire ce qui gêne la lecture.

Mesure de départ (critique statistique, 2026-10-04) : 273 de nos 641 îles sont signalées contre le mobilier de
Hytale, surtout pour du bruit fin (micro dominant, bruit uniforme).

## 2. Le principe

Une **passe d'illustration**, après le pipeline de surface et avant la lumière finale (`paint.texture` : peinture,
**illustration**, lumière, débord). Elle :

- ne change jamais la logique physique des couches : le pipeline Matière → Finition → Structure → Revêtements →
  Âge et condition → Lieu → Usage → Dégradations → Dépôts → Histoire reste la source de vérité ;
- agit seulement sur la composition finale : valeurs, contraste, sélection des détails, lisibilité ;
- n'ajoute aucun bruit décoratif et aucun effet : elle module, accentue ou retire ;
- raisonne sur les données déjà là : par texel, la position, la normale, l'arête, le bord (`rim`), l'occlusion, la
  hauteur, la lumière cuite (`bake.Texel`) ; par île, la matière, sa famille, ses revêtements, les zones de chaque
  effet et leurs signaux (`layers.Island`) ; par modèle, les rôles, l'usage, le point focal et l'histoire ;
- est **désactivée par défaut** : un modèle qui ne la déclare pas se peint à l'octet comme avant ;
- rend un **rapport** de ses décisions, une ligne par décision, en français (« Lid top : reflet sélectif sur 30 % de
  l'arête »).

### 2.1 Retirer sans recalculer

Pour qu'elle puisse réduire un effet déjà posé, chaque île garde l'image de ses couches **avant les effets**
(`island.base` : substrat calmé et revêtements, composés et gonflés par la variation macro comme l'image finale).
Réduire un effet de `k` sur sa zone, c'est ramener chaque texel de cette zone vers `base` de `k` :
`final = base + (final - base) × (1 - k)`. Plusieurs réductions sur un texel se multiplient. Rien n'est recalculé,
aucune couche n'est touchée.

### 2.2 L'importance visuelle

Une carte d'importance par pièce, distincte de `USAGE` et de `FOCUS` : 0 calme, 1 normal (par défaut), 2 important,
3 accent majeur. Elle décide où la passe concentre le contraste, les reflets et le détail, et où elle simplifie.

## 3. Version 1 (la caisse de démonstration)

Chaque opération est une fonction des îles enregistrées, de leurs texels et de l'importance de leur pièce, qui change
la texture et rend ses lignes de rapport. Elles s'enchaînent dans cet ordre (`illustration.STEPS`), chacune lisant la
texture que la précédente a laissée ; le repos vient d'abord, car il compare le texel à l'image d'avant les effets.
La passe ne fait que le travail **local** d'une forme : la séparation des matières et les accents focaux, décisions
globales, sont passés au compositeur (spec 2026-10-04 blockpaint composer § 2.1). L'importance d'une pièce est celle
du modèle (`IMPORTANCE`, `illustration.importance_of` : une pièce `FOCUS` sans valeur nommée vaut 2).

1. **Zones de repos et retrait du détail** (`rest`). Loin des arêtes (`rim` d'au moins `REST_RIM` texels), hors
   contact et sur une pièce d'importance 0 ou 1, les effets **secondaires** (ceux qui ne se lisent que de près :
   micro-rayures, rayures, éraflures, poussière, craquelure, farinage) sont réduits de `REST_CUT` (le double à
   l'importance 0), divisé par 1 + `show` de la condition (spec surfaces § 3.3 : un objet négligé garde son histoire
   jusqu'au milieu de ses faces). Un texel qu'un autre effet a touché (rouille, éclats, marque de l'histoire) garde
   tout : ces effets racontent, et l'image d'avant les effets ne les a pas.
2. **Modelé** (`form`). À l'échelle de la pièce, pas de la face : la hauteur du texel dans la pièce (de son pied à son
   sommet) éclaire le haut et assombrit le bas de `FORM`, cassé par un bruit lent du monde (`bake.value_noise`) pour
   ne pas tomber en dégradé vertical uniforme. Le dessus d'une pièce est donc éclairci comme son haut.
3. **Cadre des faces** (`planes`, la séparation des plans). Chaque face s'assombrit vers son bord ouvert, de `FRAME`
   au plus sur `FRAME_RINGS` texels, comme les panneaux des coffres de Hytale (recherche § 8) ; une jointure entre deux
   boîtes qui continuent une surface n'est pas un bord. Une face tournée vers la lumière l'est `FRAME_LIT` de moins :
   la séparation dépend de l'orientation, ce n'est pas un contour uniforme.
4. **Accents de contact** (`contact`). Là où une pièce en touche une autre (forte occlusion, `CONTACT_OCCLUSION` :
   une jonction peut tomber au milieu d'une face, sous une boîte posée), une séparation sombre légère (`CONTACT`),
   comme le trait d'un peintre entre la planche et le montant.
5. **Reflets d'arête sélectifs** (`edges`). Seule la bague extérieure des arêtes tournées vers la lumière (`FACING`)
   en reçoit, et seulement sur des tronçons : un bruit du monde choisit les tronçons, plus longs sur une pièce
   importante (`RUNS`), aucun sur une pièce calme. Jamais toutes les arêtes de la même façon.

Chaque constante est une première valeur, calée à l'œil sur l'entrepôt (§ 5) : premières valeurs sur la caisse,
doublées après la première planche de l'entrepôt, jugée trop discrète.

## 4. Lots suivants

Après la V1 validée par l'utilisateur, chacun avec ses tests et montré dans Blockbench :

- **séparation des plans par la température** : le cadre (§ 3.4) n'agit que sur la valeur ; deux faces voisines
  pourraient aussi se distinguer par la température selon leur orientation ;
- **hiérarchie des valeurs** et **Macro > Meso > Micro** : grandes masses claires, moyennes et sombres d'abord ; le
  grain fin réduit là où il domine (mesure de `critique.py`) ;
- **exagération des matières** : quelques reflets nets sur le métal, une variation douce sur le cuir, plus de
  diffusion sur le tissu, une direction claire sur le bois ;
- **asymétrie contrôlée** : accents et usure décentrés, jamais en miroir ;
- **harmonisation de palette** : les matières d'un objet rapprochées d'un même univers, sans perdre leur identité ;
- **silhouette** : quelques parties de silhouette appuyées, sans contour uniforme ;
- **lisibilité de loin** : chaque décision jugée aussi en miniature (réduction au quart) ; un détail qui n'y survit
  pas et n'apporte rien est réduit.

## 5. Calage et validation

- `critique.py` mesure la texture avant et après la passe, pour repérer du bruit réel : son compte d'îles signalées
  ne juge pas la ressemblance avec Hytale (recherche § 8 : il signale aussi un tiers des fenêtres de Hytale) ;
  `semantics.py` juge les couches, qu'elle ne change pas ;
- planche Blockbench avant/après de la caisse de démonstration, puis d'une hutte ; l'utilisateur valide avant tout
  emploi dans un modèle du jeu.

## 6. Emploi dans un modèle

Un module déclare `IMPORTANCE = {pièce: 0..3}` et `ILLUSTRATION = illustration.Illustration()`, avec une `CONDITION`
(la passe lit les îles en couches). `catalog` la passe à la peinture et affiche son rapport pendant la génération (rien
n'est écrit dans le pack).

## 7. Tests

- sans `ILLUSTRATION`, toutes les ressources régénérées sont identiques à l'octet ;
- une zone de repos perd une part de ses effets secondaires, une arête non ; le rapport le dit ;
- le haut d'une pièce est plus clair que son pied après la passe, davantage qu'avant ;
- les reflets d'arête couvrent une part bornée de l'arête, en plusieurs tronçons ;
- une jonction s'assombrit ;
- une face tournée de la lumière est cadrée nettement plus qu'une face qui la regarde ;
- une pièce importante garde ses effets secondaires, une calme en perd deux fois plus ; un texel qu'un autre effet a
  touché garde tout ;
- la passe ne change aucune couche (substrat, image d'avant les effets, zones, signaux, profondeur, relief, dépôts,
  revêtements identiques avant et après).
