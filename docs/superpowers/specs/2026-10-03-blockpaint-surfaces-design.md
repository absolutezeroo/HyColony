# blockpaint : construire une surface en couches

Date : 2026-10-03. Demandé par l'utilisateur, dans la conversation du 2026-10-03 :

- les applicateurs (« il faudrait un nouveau système aussi : l'applicateur », liste de 55) ;
- les revêtements (« qu'est-ce qui a été ajouté par-dessus ? », liste de 19) ;
- le pipeline en 13 étapes (« BlockPaint — Pipeline avancé de génération de surfaces ») ;
- « je veux le meilleur » ;
- ses 13 renforts : bruit, rôles, compatibilité, dépendances, degré, macro, art, âge, histoire, validation
  sémantique, cartes, rétrocompatibilité, composition.

Recherche : `docs/research/blockpaint-surfaces.md`. Fait suite aux pinceaux de matériaux
(`2026-10-03-blockpaint-design.md`).

## 1. Principe

blockpaint ne doit pas être un catalogue de pinceaux tout faits (`old_rusty_dirty_painted_steel`). Il **compose** une
surface couche après couche :

```text
Material + Finish + Structure + Coats + Age/Condition + Environment + Usage + Degradations + Deposits + History
+ Art Pass + Bake
```

Il ne doit pas seulement savoir produire un effet, mais savoir :

| Question | Mécanisme |
|---|---|
| pourquoi l'effet est là | l'âge, la condition, l'environnement et l'histoire le demandent (§ 3.3, 3.6) |
| où il est plausible | un masque bâti sur les cartes et le rôle de la pièce, jamais un placement au hasard (§ 3.4) |
| quelle quantité | un degré qui étend la zone, pas une opacité (§ 3.5) |
| avec quoi il interagit | la compatibilité avec le matériau et les dépendances entre effets (§ 3.5) |
| dans quel ordre | les phases, le graphe des dépendances et l'âge des événements (§ 3.5, 3.6) |
| quand le réduire ou le supprimer | la passe artistique (§ 3.7) |

Le but : des surfaces stylisées, cohérentes, lisibles, contextuelles et intentionnelles, proches du travail d'un
artiste, **sans bruit procédural**.

| Étape | Question | Dans blockpaint |
|---|---|---|
| 1. Substrat | De quoi est-ce fait ? | le pinceau du matériau et sa famille |
| 2. Finition | Comment l'a-t-on travaillé ? | un paramètre du pinceau (`finish`) |
| 3. Structure | Comment la surface est-elle organisée ? | le motif du pinceau selon l'axe de la pièce ; le macro |
| 4. Revêtements | Qu'a-t-on posé dessus ? | une pile de couches, chacune avec son épaisseur |
| 5. Âge et condition | Depuis quand, et dans quel état ? | les degrés des étapes 8 à 10 |
| 6. Environnement | Où a-t-il vécu ? | ce qui est plausible, et dans quelle mesure |
| 7. Cartes | Où est-il touché, exposé, protégé ? | cuites (géométrie) et déclarées (rôle, usage, point focal) |
| 8. Dégradations | Qu'est-ce qui s'est abîmé ? | des effets qui enlèvent ou transforment |
| 9. Dépôts | Qu'est-ce qui s'est posé dessus ? | des effets qui recouvrent |
| 10. Histoire | Que lui est-il arrivé ? | quelques événements placés, chacun avec sa source |
| 11. Art | Comment le rendre lisible et beau ? | ajouter, déplacer, réduire, supprimer ; repos, focal, contraste |
| 12. Cuisson | Comment l'intégrer à la géométrie ? | la lumière de `bake.py` |
| 13. Validation | Est-ce que ça marche à l'écran ? | mesures statistiques et sémantiques, vues dans Blockbench |

Exemple (le bouclier de chevalier de l'utilisateur) :

- matériaux : chêne raboté, lasure sombre et vernis ; fer forgé, apprêt et peinture bleue ;
- état : mûr et usé, dehors en climat tempéré ;
- usage : bord du bouclier (`shield_rim`) très exposé aux coups, poignée (`grip`) très touchée ;
- dégradations et dépôts : usure des arêtes, éclats, rayures, bosses, un peu de rouille là où le fer est à nu ; terre,
  crasse, un peu de boue en bas ;
- histoire : trois coups de bataille lisibles ;
- art : centre calme, le plus gros impact un peu décentré.

## 2. Règles de style

### 2.1 Pas de bruit sans fonction

- **Interdit** : le bruit décoratif uniforme, le bruit aléatoire pixel par pixel réparti partout.
- **Permis**, seulement quand la variation a une fonction :
  - la structure du matériau (fibres, pores, trame, grains) ;
  - la variation macro (§ 2.2) ;
  - le bord d'un masque d'effet ;
  - les amas (de corrosion, de crasse, de mousse) ;
  - une variation chromatique contrôlée (le bois qui change de teinte d'une planche à l'autre).
- Toujours des **formes cohérentes et des amas** plutôt qu'un bruit réparti partout.
- `critique.py` signale un bruit uniforme (§ 3.9). Les pinceaux de matériaux y passent au lot « art » et perdent leurs
  termes de bruit décoratif.

### 2.2 Macro > Meso > Micro

- **Micro** : grain, fibres, petites rayures ;
- **Meso** : plis, nœuds, plaques de rouille, gros éclats ;
- **Macro** : grandes variations lentes de teinte, de valeur ou de saturation sur toute une pièce.

La règle principale : Macro > Meso > Micro. Le micro ne domine jamais. C'est une règle de lecture : une mesure
simple (écarts aux moyennes floutées) la dément sur Hytale lui-même (recherche § 2). `critique.py` la vérifiera avec
une mesure d'abord éprouvée sur les textures de Hytale.

### 2.3 Ce que disent Hytale et ses textures

Le guide de Hypixel et les mesures des textures de Hytale (recherche § 1 et 2) fixent le cap :

- chaque matériau a un **budget de calme** (écart moyen d'un texel au suivant, part des sauts), tiré des textures
  de Hytale du même genre, mesurées île par île ;
- **ombres colorées** : en s'assombrissant, une couleur tourne vers le violet par le plus court chemin (un brun passe
  par le rouge, comme le bois de Hytale dans 50 textures sur 52 ; un vert passe par le bleu) ; en s'éclaircissant,
  vers le jaune. La saturation n'est pas une règle : elle reste celle du matériau. Le mode legacy de `shading.graded`
  pousse les ombres vers le bleu, ce qui grise un brun ;
- ni noir ni blanc purs (déjà fait : `LOW`, `HIGH`).

## 3. Les étapes

### 3.1 Substrat, finition, structure (1 à 3)

- Le pinceau peint le matériau sain et déclare sa **famille** (`@family("wood")` sur sa fabrique ; `material` peut
  la remplacer) :
  - métaux : ferreux, cuivreux, précieux ;
  - organique : bois, textile, cuir, cheveux et fourrure, os et corne ;
  - minéral : pierre, céramique, verre ;
  - autres : cire, caoutchouc ;
  - vivant et liquide : plante, liquide. Ces deux familles ne s'usent ni ne vieillissent (`compat.SETTLED_ONLY`) :
    comme sur un dessin, seuls les dépôts les atteignent (une feuille n'a ni arête usée ni éclat, l'eau ne rouille
    pas).
- Un matériau en couches a toujours une famille : sans elle, tout effet l'atteindrait (la rouille sur une corde). Le
  module la nomme dans `FAMILY` (`{matériau: famille}`) pour une tuile image ou un pinceau qui n'en porte pas, ou pas la
  bonne (le tissu qui peint la tête d'un roseau) ; `catalog` refuse un matériau en couches sans famille.
- La finition est un paramètre du pinceau (`metal(STEEL, finish="forged")`). À 32 pixels par bloc, la finition est le
  grain lui-même : stries brossées, facettes martelées, traces de scie. Chaque matériau vient avec ses finitions, dans
  les lots de contenu (§ 6).
- La **structure suit l'axe de la pièce**, pas le grand côté de l'île. Le module peut déclarer l'axe d'un nœud dans
  le repère de sa boîte (`AXES = {"Plank": "y"}`) ; sans déclaration, c'est le grand côté, comme aujourd'hui. L'axe
  est donné au pinceau (le sens du grain sur l'île : u, v, ou aucun pour le bois de bout, que le pinceau peint alors
  selon le grand côté de l'île) ; l'image n'est jamais
  tournée, pour que ce que le pinceau règle selon le haut et le bas (coulures, mousse en bas, racine) reste juste.
- La **variation macro** est posée sur la pièce entière : de grandes variations lentes de teinte, de valeur et de
  saturation, en position dans le monde, pour que les faces d'une même pièce se raccordent.
- Les pinceaux trop composés (`painted_metal`, `rusty_metal`, `mossy_stone`, `charred_wood`) seront retirés quand
  leurs couches existeront (acier + peinture + usure, acier + rouille, pierre + mousse, bois + brûlure).

### 3.2 Revêtements (4)

- Les revêtements forment une pile, du bas vers le haut (`Steel → Primer → Red Paint → Varnish`). Chacun a une
  **épaisseur de départ** (une dorure, mince, part avant une peinture épaisse) et une façon de se poser :
  - **couvrant** : Primer, Paint, Lacquer, Enamel, Plating, Gilding, Silvering, Tar / Pitch ;
  - **transparent**, avec son opacité : Thin Paint, Varnish, Glaze, Resin, Protective Coat, Wax ;
  - **teinture** (change la couleur, garde le grain) : Wood Stain, Oil Finish, Fabric Dye, Leather Dye, Blackening,
    Bluing, Browning, Heat Treatment ;
  - **lustre** en plus pour les couches lisses (Lacquer, Varnish, Wax, Enamel, Glaze, Resin, Plating, Gilding,
    Silvering) : un reflet clair aux arêtes et vers la lumière, peint dans la couleur (pas de matière brillante sur
    ces blocs dans Hytale).
- Une rayure creuse une **profondeur** :
  - légère, elle n'enlève que la couche du dessus et montre l'apprêt ;
  - profonde, elle traverse la pile jusqu'à l'acier ;
  - plus profonde encore, elle entame le matériau (le creux d'un éclat).
- Les revêtements se nomment en `*_coat` (`paint_coat`, `wax_coat`) : `paint` est déjà un module, et `misc.wax` un
  pinceau (une bougie).

### 3.3 Âge, condition, environnement (5, 6)

- L'**âge** dit depuis quand l'objet existe : New, Mature, Old, Ancient. Il règle les effets du temps : décoloration,
  patine, oxydation, mousse, érosion, revêtements qui se ternissent.
- La **condition** dit comment on l'a utilisé et entretenu : Pristine, Maintained, Used, Worn, Neglected, Ruined.
  Elle règle :
  - les effets de l'usage : usure, rayures, éclats, arêtes polies par les mains ;
  - les effets du manque de soin : crasse, corrosion non traitée, réparations.
- Les deux se combinent : un objet ancien bien entretenu (`Ancient` + `Maintained`) ou un objet récent très usé
  (`New` + `Worn`). Les préréglages de la liste de l'utilisateur sont des couples : Factory New = New + Pristine,
  Abandoned = Old + Neglected, Ruined = Ancient + Ruined.
- L'âge est **facultatif** : sans lui, la condition en donne un par défaut (Used → Mature, Neglected → Old…). Une
  première version peut ne déclarer que la condition.
- L'**environnement** dit ce qui est plausible et pondère les degrés : Dry Interior, Humid Interior, Forest, Swamp,
  Desert, Coastal, Snow / Cold, Industrial, Temperate Outdoor.
- Une table de poids croise âge, condition et environnement pour donner la liste des effets et leurs degrés. Le
  matériau (§ 3.5, compatibilité) et le rôle des pièces les ajustent.
- `conditions.plan` : le degré d'un effet vaut celui de la condition, plus ceux de l'âge et de l'environnement
  (ce que le lieu pose lui-même : neige, sable, mousse, suie…) retenus par le soin (`care`), pondérés par
  l'environnement puis par le rôle, au plus 1.
- Les effets d'un lieu (`PLACED` : boue, graisse, huile, suie, cendre, sable, neige, mousse, moisissure) n'existent
  que là où l'environnement les nomme : une mousse ancienne ne pousse pas dans un intérieur sec. Un matériau peut
  toujours en déclarer un comme le sien (l'essieu graissé).
- Chaque environnement suit son climat : le bord de mer corrode le plus vite (ISO 9223 : C4 à C5, contre C1 pour un
  intérieur sec) et cloque la peinture, le désert décolore, farine et fend, le froid fend ce qui retient l'eau, la
  forge pose suie, cendre et graisse.
- L'usure claire des arêtes et la crasse du pied que la cuisson pose aujourd'hui passent sous la condition
  (`Condition(wear, grime)`) :
  - à 1 dans le préréglage par défaut (`DEFAULT`, le rendu actuel à l'octet) ;
  - à 0 dans le nouveau système, qui les remplace par ses effets.

### 3.4 Cartes (7)

Toutes les dégradations et tous les dépôts bâtissent leur masque à partir des cartes, **jamais d'un placement au
hasard**. Le bruit en amas ne fait que découper le bord d'un masque.

- **Cuites** (`bake.survey`, pour chaque pixel) :
  - arête : l'anneau du biseau (`edge`) et la distance au bord ouvert le plus proche (`rim`, en texels), une jointure
    ne comptant pas ;
  - creux : occlusion ;
  - hauteur : 0 au pied, 1 au sommet ; 0 pour un modèle sans épaisseur ;
  - position dans le monde ;
  - normale et orientation : dessus, côtés, dessous ;
  - lumière : `dot(normale, LIGHT)` ;
  - face : nœud et côté.
- **Déduites** des cartes cuites :
  - ciel : la part de la normale vers le haut, réduite par l'occlusion ;
  - exposition : le plus fort du ciel et de la proximité d'un bord ouvert ;
  - proximité du sol ;
  - eau qui stagne : dessus et creux.
- **Déclarées** par le module, par nœud :
  - le **rôle sémantique** (§ 3.4.1) ;
  - l'**usage** : `USAGE = {"Grip": contact(1.0), "Rim": impact(1.0)}` ;
  - le **point focal** : `FOCUS`.

#### 3.4.1 Rôles

Le rôle dit **ce qu'est** la pièce ; l'usage dit **ce qui lui arrive**. Les deux ensemble rendent les effets plus
justes : une semelle (`sole`) s'use par abrasion et prend la boue ; un toit (`roof`) mouille son bord et laisse couler
l'eau ; le visage (`face`) reste propre et attire le point focal.

- `ROLES = {"Blade": "blade", "Grip": "grip", "Rim": "shield_rim"}`.
- Vocabulaire de départ : blade, handle, grip, guard, boot, sole, face, hair, armor_plate, cloth_panel, strap,
  shield_rim, shield_face, roof, wall, floor, step, wheel, axle, door, hinge, lid, leg, rope, pot, rim.
- Chaque rôle donne :
  - des valeurs par défaut aux cartes déclarées (`grip` : contact fort ; `shield_rim` : impact fort ; `sole` :
    abrasion et sol) ;
  - des poids d'effets (`roof` : eau, mousse, coulures ; `floor` : terre, éraflures ; `face` : presque rien).
- `USAGE` et `FOCUS` complètent ou corrigent ce que donne le rôle.

### 3.5 Dégradations et dépôts (8, 9)

#### 3.5.1 Degré, jamais opacité

- Un effet **gagne du terrain dans l'ordre de son masque** (Context-Aware Textures) :
  - à degré faible, il ne touche que les pixels au meilleur score de plausibilité ;
  - un degré plus fort l'étend pas à pas aux suivants ;
  - ce qui est atteint à un degré l'est encore à tout degré plus fort.
- Là où il est, l'effet est **plein**. Un effet faible ne s'obtient **jamais** en posant le même effet partout avec
  une faible transparence. Seul le bord d'une zone peut se fondre, sur un ou deux pixels.
- Une tache de moins de 4 texels qui se touchent (`MIN_PATCH`, un carré de 2 x 2) n'est pas gardée : c'est un point
  de bruit, pas une zone, et elle disparaîtrait de loin. Une île de moins de 4 texels ne reçoit donc aucun effet.
- Le bord d'une zone suit des amas lisses : un bruit en 3D sur la position dans le monde, pour que deux faces
  voisines se raccordent.

#### 3.5.2 Catalogue

- **Dégradations** (elles enlèvent ou transforment) :
  - Edge Wear, Scuffs, Chips ;
  - Scratches : micro, longues, profondes, croisées ;
  - Cracks : fines, structurelles, craquelure ;
  - Rust : en film, en points, profonde, piqûres, coulures, autour des dégâts ;
  - Oxidation, Verdigris ;
  - dégradation de la peinture : Fading, Chalking, Peeling, Flaking, Chipping, Cracking, Blistering ;
  - Burn, Scorch ;
  - Fraying (textile : tissu, corde).
- **Dépôts** (ils recouvrent) : Dust, Dirt, Grime, Mud, Grease, Oil, Soot, Ash, Sand, Snow, Moss, Mold.

#### 3.5.3 Compatibilité entre matériaux et effets

Une matrice dit, pour chaque famille de matériau (§ 3.1) et de revêtement, si un effet est :

- **compatible** : poids plein ;
- **possible** : poids réduit ;
- **impossible** : jamais posé. Une demande explicite arrête la peinture avec un message clair.

| Effet | Compatible | Possible |
|---|---|---|
| Rust, Rust Spots, Deep Rust, Rust Pits | ferreux | |
| Verdigris | cuivreux | |
| Oxidation | ferreux, cuivreux, film métallique | précieux |
| Fraying | textile | cuir, poil, papier |
| Cracks | pierre, céramique, films de peinture et de vernis | bois, os, cuir, verre, cire |
| Craquelure | films de peinture et de vernis | céramique (glaçure) |
| Chalking | film de peinture | |
| Peeling | film de peinture | films de vernis et métallique |
| Blistering | film de peinture | film de vernis |
| Flaking | films de peinture, de vernis et métallique | pierre, céramique |
| Fading | film de peinture, textile, bois, cuir, papier | film de vernis, poil, os, caoutchouc |
| Micro Scratches | métaux, films, verre, céramique | bois, pierre, cuir, os, caoutchouc, cire |
| Burn | bois, textile, papier, cuir, poil, plante | films, os, cire, caoutchouc, pierre, céramique, ferreux |
| Moss | pierre, bois, céramique | verre, textile |
| Mold | bois, textile, cuir, papier | pierre, céramique, films de peinture et de vernis, plante |

Pour un effet de la matrice, toute famille qui n'est ni compatible ni possible est **impossible** ; un effet absent de
la matrice (usure, éclats, rayures, dépôts hors mousse et moisissure, marques de l'histoire) convient à toutes. Les
revêtements ont leur famille de film (`paint_film`, `varnish_film`, `metal_film`) : l'effet s'applique à la couche
visible, et le fer sous une peinture intacte ne rouille pas.

Un matériau peut changer un poids ou interdire un effet pour lui-même (`material(..., weights={"rust": 0.0})` : un
acier inoxydable interdit Rust).

#### 3.5.4 Dépendances entre effets

Les effets ne sont pas indépendants. Chaque effet peut **produire des signaux**, c'est-à-dire des masques ou des
points d'ancrage nommés, et **lire** ceux des effets précédents pour renforcer ou affaiblir son propre masque :

- Chips, Edge Wear, Scratches, Peeling, Flaking → `bare` (le substrat mis à nu) → Rust ;
- Rust, Rust Spots, Deep Rust → `rust` → Rust Pits, Rust Streaks (la rouille qui coule) ;
- Cracks → `water_retention` → Mold, Moss ;
- Grease → `sticky` → Dust (la poussière colle à la graisse) ;
- Chips, Scratches, Dent → `damage` → Rust Around Damage.

La pile des revêtements se lit à tout moment : le substrat à nu (`bare(famille)`) et la couche visible de chaque
pixel. Les signaux écrits par les effets sont gardés sur l'île. Un signal lu doit être écrit par un effet du passage,
un signal écrit doit être lu par l'un d'eux, sauf ceux du vocabulaire commun (`bare`, `damage`, `rust`,
`water_retention`, `sticky`) : une faute de frappe arrête la peinture.

#### 3.5.5 Une seule suite d'opérations

Chaque île se peint par **une seule suite d'opérations**, puis une composition finale :

1. le substrat et sa variation macro, puis la pile des revêtements ;
2. les effets, triés par moment (dégradations, puis dépôts ; au lot 3, les événements de l'histoire s'y glissent selon
   leur âge), et dans chaque moment selon le graphe de leurs signaux (un cycle arrête la peinture avec un message) ;
3. la composition, qui pose de bas en haut le substrat, ce qui reste des revêtements, le lustre et les dépôts.

Chaque action dit la couche qu'elle change : creuser la profondeur (et retirer les dépôts déjà posés là),
transformer la couleur du substrat, d'un revêtement nommé ou de la couche visible, poser un dépôt. Ainsi un coup
récent tranche la crasse, et la rouille, une dégradation, lit le fer qu'un éclat vient de mettre à nu.

### 3.6 Histoire (10)

- Quelques événements **lisibles** plutôt que cent marques : Battle Damage, Fire Exposure, Water Damage, Dropped,
  Impact, Blood Stained, Chemical Spill, Magic Corruption, Repaired (pièce, patch, couture, différence de teinte ; des
  rivets en relief relèvent du modèle).
- Chaque événement a :
  - une **source** : un nœud et un point, une arête de face, ou tout un côté (le dessus pour la pluie) ;
  - une **direction** : un vecteur, ou la gravité pour ce qui coule ;
  - un **rayon** et une **atténuation** ;
  - une **gravité** (severity) ;
  - un **âge**.
- Il se propage **dans le monde**, donc d'une face à l'autre :
  - l'eau part d'un bord de toit et coule vers le bas ;
  - la rouille part d'un rivet ;
  - la brûlure vient d'un foyer ;
  - l'impact vient d'un bord ;
  - le sang vient d'une blessure.

  Une face qui tourne le dos à la source (sa normale dans le sens de la source vers elle) n'est pas atteinte, sauf
  tout contre la source : γ-ton tracing en simple, sans rayon vers la source (une autre pièce entre les deux ne fait
  pas écran). Une coulée quitte les faces tournées dans son sens : l'eau ne mouille pas un dessous, la fumée ne
  noircit pas un dessus mais se ramasse sous un linteau.
- Une source ponctuelle se pose au centre d'une **face** de la pièce (`front` par défaut), décalée dans le monde :
  au centre du volume, elle serait dans la boîte et toutes les faces lui tourneraient le dos. Les coups de
  `battle_damage` se répartissent dans le plan de cette face.
- Une atténuation nulle marque tout le rayon, sans rien au-delà ; une pièce (`repaired`) est carrée dans le plan de
  la face, avec une couture plus sombre.
- Chaque marque garde sa forme propre, que la portée de l'événement pondère : rayures en lignes, sang en une tache
  et des gouttes, magie en veines, bosse en creux. Le creux se lit par son relief, comme on le peint : la paroi
  tournée à l'opposé de la lumière de la cuisson dans l'ombre, la lèvre tournée vers elle claire. Ce relief
  (`Island.press`) s'applique sur ce qui est visible, revêtement compris, sous les dépôts.
- Les zones de repos de la passe artistique n'amortissent pas les marques d'un événement : il marque là où il a eu
  lieu.
- Un effet qui revient (trois coups posent chacun une bosse) ajoute sa zone à celle de la fois d'avant.
- L'âge ordonne les événements entre eux et par rapport aux dépôts :
  - un incendie ancien a sa suie sous la poussière ;
  - un coup récent tranche la crasse.
- `HISTORY` (dans le module) liste les événements ; `semantics.py` vérifie que chaque marque reste à la portée de
  l'événement qui la pose.

### 3.7 Passe artistique (11)

Elle agit en deux temps : sur le **plan** des effets, avant la peinture, puis sur le **résultat**.

- Elle peut **ajouter, déplacer, réduire ou supprimer** un effet :
  - ajouter : les effets propres d'un matériau (`material(..., effects=...)`) et ceux de l'histoire ;
  - déplacer : le plus gros coup de `battle_damage` tombe hors du centre ;
  - réduire : les zones de repos et le calme du substrat ;
  - supprimer : au-delà du nombre d'effets visibles, et le dépôt qui se bat avec un plus utile.
- Elle préserve des **zones de repos**, loin des arêtes, des zones de contact et des sources : là, les effets
  s'éteignent.
- Elle évite que tout soit visible partout : chaque pièce a un **nombre d'effets visibles** borné, selon son rôle,
  le point focal et le budget de détail.
- Elle tient un **budget de détail** (Low, Medium, High) et le budget de calme de chaque matériau (§ 2.3) : au-delà,
  une couche est adoucie vers sa moyenne locale.
- Elle contrôle :
  - le **point focal** : `FOCUS = ("Face", "Boss")`, des poids par nœud qui y concentrent le détail ;
  - l'**asymétrie** : la graine par face et par modèle ;
  - le **contraste entre matériaux** voisins : cuir, métal et tissu restent distincts en valeur, en teinte ou en
    structure. La critique sémantique le vérifie (§ 3.9) ; le corriger reste au choix des matériaux du modèle ;
  - la **cohérence de palette** : les teintes du modèle restent dans une même famille. Aucune mesure de Hytale ne la
    chiffre (ses modèles mêlent tissu rouge, bois et métal) : elle reste à l'œil, dans la planche Blockbench.
- Quand plusieurs effets se **battent** sur une même zone, elle garde les plus utiles et supprime les autres. Un
  effet est d'autant plus utile qu'il est plausible et qu'il sert l'histoire, le rôle de la pièce ou le point focal.

### 3.8 Cuisson (12)

- `bake.py` reste l'intégration à la géométrie : occlusion, ombre portée, lumière d'en haut, biseau des arêtes,
  variation douce. Il ne décide plus de l'âge (§ 3.3).
- Le nouveau système colore la lumière à la manière de Hytale (§ 2.3) : des ombres qui tournent vers le violet et des
  lumières vers le jaune, par le plus court chemin, à saturation gardée.

### 3.9 Validation (13)

`tools/blockpaint/critique.py` (statistique) et `semantics.py` (sémantique) rendent un rapport par île et par
question, avec un verdict à chaque ligne.

- **Statistique** :
  - écart moyen et part des sauts, comparés aux budgets tirés de Hytale (mesurés île par île) ;
  - bruit uniforme (§ 2.1) ;
  - ordre Macro > Meso > Micro, par une mesure éprouvée sur Hytale (§ 2.2) ;
  - teinte des sombres et des clairs (« ombres grises »).
- **Sémantique**, à partir des cartes et des masques des effets :
  - la boue est-elle surtout en bas ?
  - la poignée est-elle plus usée que le reste ?
  - la rouille est-elle surtout là où le métal est à nu et exposé ?
  - la mousse est-elle dans des zones plausibles (creux, dessus, humidité) ?
  - les fibres suivent-elles l'axe de la pièce ?
  - le point focal reste-t-il dominant en détail (écart moyen) ?
  - la texture reste-t-elle lisible à la distance de jeu (réduite au quart, chaque bloc de 2 x 2 texels en un seul) ?
  - les matériaux voisins restent-ils différenciables ? Seulement deux matières (familles) différentes, chacune
    lisible de loin : deux noms d'une même famille sont une matière peinte deux fois (douelles et couvercle d'un
    tonneau), et un bout d'un texel ne se lit pas de toute façon ;
  - chaque marque d'un événement reste-t-elle à sa portée ?
- **À l'œil** : une planche dans Blockbench (six vues, perspective, atlas) et la grille de critiques de
  l'utilisateur (métal trop propre, rouille trop uniforme, éclats trop réguliers…). L'utilisateur valide chaque lot
  avant le commit.

## 4. Emploi dans un modèle

```python
SEED = 7                                         # facultatif : chaque face tire son propre motif
AGE, CONDITION = MATURE, WORN                    # 5 (AGE facultatif)
ENVIRONMENT = TEMPERATE_OUTDOOR                  # 6
ROLES = {"Rim": "shield_rim", "Grip": "grip", "Face": "shield_face"}   # 7
USAGE = {"Grip": contact(1.0)}                   # 7, complète les rôles
HISTORY = (battle_damage(3, "Face", side="right"),)   # 10 : trois coups dans le plan de la face
FOCUS = ("Boss",)                                # 7, 11
ART = Art(detail="medium")                       # 11
AXES = {"Plank": "y"}                            # 3

def tiles(assets):
    return {
        "wood": material(timber("oak", "planed"), coats=(stain_coat(DARK), varnish_coat())),
        "iron": material(metal("iron", "forged"), coats=(primer_coat(), paint_coat(BLUE))),
    }
```

## 5. Rétrocompatibilité

- Tout le nouveau système est **opt-in**. Un modèle qui n'en déclare rien, ou qui déclare `CONDITION = DEFAULT`, garde
  **exactement** son rendu actuel : graine 0, pinceaux et lumière d'aujourd'hui.
- Aucun modèle existant ne change sans une **migration explicite**, décidée par l'utilisateur modèle par modèle et
  montrée avant.
- Chaque lot vérifie que `python tools/blockpaint` régénère tous les fichiers à l'identique.

## 6. Ordre de travail

Les lots construisent d'abord l'architecture entière, puis le contenu selon les modèles qui en ont besoin (huttes,
armure, citoyens) :

1. **Socle** :
   - les cartes cuites, déduites et déclarées (rôles, usage), les axes, la graine par face ;
   - l'île en couches (familles, substrat, variation macro, revêtements, profondeur) ;
   - les effets : une seule suite d'opérations, degré par masque, signaux et dépendances, matrice de compatibilité ;
   - l'âge, la condition et l'environnement ;
   - la lumière colorée à la manière de Hytale ;
   - `critique.py` statistique, ses budgets tirés d'une mesure de Hytale île par île.
   - Démonstration : une caisse, avec son contenu et ses tests.
2. **Art et validation** :
   - la passe artistique (ajouter, déplacer, réduire, supprimer ; repos, nombre d'effets visibles, focal, contraste,
     palette) ;
   - `critique.py` sémantique ;
   - le nettoyage du bruit décoratif des pinceaux.
3. **Histoire** : les événements avec leur source, leur direction, leur rayon, leur atténuation, leur gravité et leur
   âge, propagés d'une face à l'autre.
4. **Le bouclier de chevalier**, complet (§ 1), et le contenu qu'il demande.
5. **Le reste du catalogue**, un lot à la fois :
   - métaux : fer, acier, fonte, argent, laiton ;
   - bois : chêne, pin, noyer, acajou ;
   - textiles : lin, coton, laine, soie, toile ;
   - minéraux : marbre, granit, céramique ;
   - puis les revêtements, dégradations, dépôts, histoires, environnements et rôles restants.

Chaque effet a ses tests : déterministe ; degré 0 sans effet ; une zone qui grandit avec le degré sans jamais
reculer ; effet plein là où il est ; rien hors de son masque ni sur un matériau incompatible ; l'alpha ne change
jamais. Un plan (`docs/superpowers/plans/`) précède le code de chaque lot.
