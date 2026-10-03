# Peindre une surface : l'état de l'art, pour blockpaint

Recherche du 2026-10-03, avant la spec du pipeline de surfaces
(`docs/superpowers/specs/2026-10-03-blockpaint-surfaces-design.md`). Mesures faites sur les assets de Hytale (version
épinglée dans `gradle.properties`) et sur nos huttes, par atlas entier (couleurs distinctes, écart moyen d'un texel
au suivant, part des sauts de plus de 12, teinte, saturation et valeur du cinquième le plus sombre et du cinquième le
plus clair), puis, pour la rampe des ombres, sur les seuls texels chauds (bois) de 52 textures de mobilier.

## 1. Ce que Hypixel demande

Guide officiel « An Introduction to Making Models for Hytale » (hytale.com, décembre 2025) :

- la texture est une **illustration** : ombres, occlusion ambiante et lumières y sont peintes ou cuites ;
- les ombres ont de la couleur (« a hint of purple in the shadows »), elles ne sont jamais seulement désaturées ;
- ni blanc pur ni noir pur (ils cassent l'éclairage du jeu et les valeurs) ;
- **éviter le bruit et trop de grain** (« avoid noise, too much grain »), mais aussi les surfaces parfaitement
  plates ; pinceau doux pour le modelé, crayon pour les détails ;
- 32 pixels par unité pour les blocs et accessoires, 64 pour les personnages, outils et armes.

## 2. Ce que mesurent les textures de Hytale

Par atlas entier (`Common/Blocks/Decorative_Sets/<jeu>/`, tous les `.png` du dossier ; fer : `BlockTextures/Metal_Iron`
à `Metal_Iron04` et `Metal_Iron_Smooth`) :

| Jeu de textures | Couleurs | Écart moyen | Sauts > 12 | Sombres (teinte, saturation) | Clairs |
|---|---|---|---|---|---|
| Mobilier Ancient (29) | 137–4 370 | 2,5–19,4 | 4–56 % | 14–61°, 0,02–0,74 | 31–62°, 0,03–0,70 |
| Mobilier Feran (25) | 302–5 722 | 6,6–20,1 | 12–52 % | 11–36°, 0,14–0,87 | 15–49°, 0,11–0,69 |
| Fer (bloc, 5) | 20–113 | 1–5 | 0–13 % | 167°, 0,06 | 167°, 0,06 |
| Nos huttes (14) | 1 535–4 124 | 7,2–17,1 | 16–33 % | 4–47° (Cook 270°), 0,06–0,56 | 31–85°, 0,14–0,55 |

Un atlas mêle plusieurs matériaux : ces chiffres comparent des matériaux entre eux autant que la rampe d'une couleur.
Ils donnent des ordres de grandeur, pas des budgets par matériau (à mesurer île par île, § 6).

- Hytale n'emploie **pas** de palette réduite sur ses accessoires : des milliers de tons, en dégradés doux. La palette
  réduite (« rampe » de pixel art) n'est donc pas le bon outil.
- Le calme dépend du matériau : un bloc de fer lisse a 20 couleurs et presque aucun saut ; le bois d'un coffre en a
  des milliers mais un écart moyen bas (Ancient Chest : 3,6, Coffin : 2,5).
- **Rampe des ombres, sur les seuls texels chauds (bois) de 52 textures** : les sombres ont une teinte plus basse que
  les clairs dans 50 cas sur 52, donc ils **tournent vers le rouge** (le violet vient du guide, § 1, que ces mesures
  ne voient pas sur le bois). Ils ne sont plus saturés que dans
  30 cas sur 52, souvent de peu, et les grandes pièces de bois disent l'inverse (Ancient Chest : 0,30 contre 0,41 ;
  Feran Table : 0,17 contre 0,55). La saturation n'est donc **pas** une règle. Nos ombres tournent vers le bleu
  (`shading.graded`, mode legacy), ce qui grise un brun (saturation des sombres de 0,06 à 0,33 sur les carrières et
  la résidence).
- Nos huttes les plus récentes sont plus bruitées que le mobilier calme de Hytale (carrières et mine : 28–33 % de
  sauts, écart 11–17).
- Une mesure grossière de l'énergie par échelle (écarts aux moyennes floutées de rayon 1, 4 et 12) donne micro >
  meso > macro sur Hytale lui-même (Rock_Stone : 4,3 > 3,2 > 0,8 ; Table Ancient : 6,7 > 5,9) : la règle Macro >
  Meso > Micro est une règle de lecture, pas une mesure aussi simple. Sa vérification automatique demande une mesure
  à définir sur Hytale, île par île (§ 6).

### 2.1 Île par île : les budgets de `critique.py`

Mesure de chaque île d'au moins 6 x 6 texels (faces ni tournées ni miroir) des modèles de mobilier de Hytale
(`Decorative_Sets/` Ancient, Feran, Village, Crude, Human, Christmas, Lumberjack, Kweebec, Temple ; `X.blockymodel` et
`X_Texture.png`), 1 741 îles, classées par leur couleur moyenne : chaude (bois, cuir), grise (métal, pierre), colorée
(tissu, peinture). Échelles : micro = texel contre sa moyenne 3 x 3 ; meso = 3 x 3 contre 7 x 7 ; macro = 7 x 7 contre
la moyenne de l'île.

Centiles 10, 50 et 90 de l'écart moyen ; 50 et 90 des sauts de plus de 12 et de micro / (meso + macro) :

| Genre | Îles | Écart | Sauts | Micro |
|---|---|---|---|---|
| chaude (Hytale) | 1 364 | 2,9 ; 6,1 ; 15,3 | 16 % ; 42 % | 0,48 ; 1,03 |
| grise (Hytale) | 113 | 2,9 ; 15,3 ; 21,9 | 45 % ; 56 % | 0,48 ; 0,76 |
| colorée (Hytale) | 264 | 5,0 ; 9,2 ; 20,3 | 26 % ; 53 % | 0,52 ; 1,02 |
| chaude (nos huttes) | 226 | 4,5 ; 9,5 ; 17,3 | 29 % ; 49 % | 0,66 ; 1,25 |
| grise (nos huttes) | 122 | 4,4 ; 13,6 ; 18,5 | 42 % ; 53 % | 0,59 ; 1,03 |

- Le micro ne vaut en général que la moitié des grandes échelles réunies : c'est la mesure de Macro > Meso > Micro
  qui tient sur Hytale. « Micro dominant » : au-dessus de 1,03 (son 90e centile).
- Budgets de `critique.py` : « trop plat » sous le 10e centile de l'écart, « trop bruité » au-dessus du 90e (écart ou
  sauts) ; « bruit uniforme » : des sauts dans plus de 90 % des cases de 3 x 3 et un micro au-dessus de 0,7. Chacun de
  ces verdicts tombe sur 9 à 12 % des îles de Hytale, et « ombres sans virage » (les sombres d'une île chaude qui ne
  tournent pas vers le rouge, d'un quart de tour au plus) sur 5 %.
- 75e centile (écart ; sauts ; micro), le budget « medium » de `art.py`, entre le « low » (50e) et le « high »
  (90e) : chaude 9,3 ; 28 % ; 0,72, grise 20,0 ; 52 % ; 0,58, colorée 12,8 ; 39 % ; 0,72.
- Notre bois est nettement plus bruité que celui de Hytale (écart médian 9,5 contre 6,1 ; sauts 29 % contre 16 %).

## 3. Les outils du métier : Substance Painter

Aide d'Adobe (Masking and effects, Smart materials, Anchor points), lue par les résumés de la recherche web (les
pages refusent la lecture directe) :

- un **matériau intelligent** est une pile de calques sauvegardée, chaque calque avec un masque ;
- les masques sont produits par des **générateurs** qui lisent les cartes cuites du modèle : courbure (arêtes),
  occlusion, position dans le monde (hauteur, proximité du sol), normale dans le monde (dessus, dessous), épaisseur ;
- les **points d'ancrage** laissent un calque réagir à ce qu'ont fait les calques du dessous : la rouille autour des
  rayures, la saleté dans les creux d'un relief peint, un mélange par hauteur (la mousse dans les joints).

Material Maker (libre, MIT, sur Godot) fait des matériaux procéduraux par un graphe de nœuds, comme Substance
Designer, et peint en 3D. Il sert de catalogue d'idées, pas de dépendance.

## 4. La méthode des peintres « stylisés »

Bouclier viking de Marie Lazar (blog Sketchfab, textures stylisées à partir de cartes cuites), dans cet ordre :

1. les matières en **niveaux de gris** d'abord ; « la texture doit être bien composée avant la couleur » ;
2. l'occlusion en multiplication ;
3. la lumière directionnelle, tirée des canaux bleu et vert de la normale courbée (« bent normal ») en lumière douce,
   20 à 40 % ;
4. les creux et les arêtes, en incrustation légère ;
5. la couleur posée ensuite par des **dégradés** (gradient map) par matière, « plus froids dans les valeurs sombres et
   plus chauds dans les claires » ;
6. usure et crasse ;
7. les reflets peints à la main en dernier.

Pixel art (rampes de teinte) : en assombrissant, la teinte tourne vers le froid ; en éclaircissant, vers le chaud.
Hytale va dans le même sens à sa façon : sur le bois, ses sombres tournent vers le rouge et le violet (§ 2), c'est le
chemin le plus court d'un brun vers le violet ; ils ne vont pas vers un bleu gris.

## 5. La recherche sur le vieillissement

- **Context-Aware Textures** (Lu, Georghiades, Glaser, Wu, Wei, Guo, Dorsey, Rushmeier, ACM TOG 26(1), 2007) : une
  usure (rouille, peinture qui craque, moisissure) dépend de **paramètres de contexte** de la géométrie, dont
  l'accessibilité (occlusion) et la courbure. Une carte de **degré** la fait progresser : elle gagne d'abord les
  endroits les plus exposés. Pour nous, l'intensité d'un applicateur doit étendre l'effet dans cet ordre, et non
  rendre tout l'effet plus ou moins opaque.
- **γ-ton tracing** (Chen, Xia, Wong, Tong, Bao, Guo, Shum, SIGGRAPH 2005) : le vieillissement part de **sources**
  ponctuelles (un tuyau qui fuit → une coulure de rouille), étendues ou ambiantes (la pollution), et se déplace (la
  rouille qui coule sur la pierre). Pour nous, une histoire déclare des sources sur le modèle (un clou, un foyer, un
  bord de toit), et l'effet coule vers le bas d'une face à la suivante.
- Les études classent ces effets en **chimiques** (rouille, patine), **mécaniques** (rayures, éclats, fissures) et
  **biologiques** (mousse, moisissure) (enquête de Mérillou et Ghazanfarpour, Computers & Graphics, 2008).

## 6. Ce qu'on en tire pour blockpaint

1. Ombrer en tournant la teinte, par le plus court chemin, vers le violet dans les sombres et vers le jaune dans les
   clairs, au lieu de teinter la lumière en bleu ; la saturation n'est pas une règle et reste au matériau.
2. Un **budget de calme** par matériau, à mesurer sur Hytale île par île (écart moyen, part des sauts) ; de même pour
   une mesure de Macro > Meso > Micro qui tienne sur Hytale.
3. Des **cartes** cuites (arête, creux, hauteur, dessus, dessous) et déclarées (contact, impact, point focal), lues
   par chaque couche, comme les générateurs de Substance.
4. L'intensité qui **gagne du terrain** dans l'ordre du contexte (carte de degré), pas qui fond l'effet partout.
5. Des couches qui **lisent les couches du dessous** (points d'ancrage) : la rouille autour des éclats, la crasse
   dans les creux peints.
6. Des **sources** pour l'histoire, avec des coulures vers le bas.

## 7. Les palettes des matériaux

Couleur moyenne des pixels opaques des textures de blocs de Hytale (`Common/BlockTextures/`, Assets.zip de la version
épinglée), mesurée le 2026-10-03, et la couleur de base que lui donnent `woods.py`, `metalwork.py`, `stones.py` et
`textiles.py`. Un bloc reçoit sa lumière du moteur ; nos accessoires cuisent la leur (`bake.py`), qui assombrit
d'environ 15 à 40 % : leur base est donc éclaircie, à teinte égale.

| Matériau | Texture de Hytale | Moyenne de Hytale | Notre base |
|---|---|---|---|
| chêne | `Wood_Hardwood_Planks` | 90, 55, 33 | 150, 92, 54 (× 1,67) |
| pin | `Wood_Lightwood_Planks` | 193, 170, 141 | 198, 172, 138 (déjà clair) |
| noyer | `Wood_Darkwood_Planks` | 64, 37, 26 | 116, 68, 46 (× 1,8) |
| acajou | `Wood_Redwood_Planks` | 99, 50, 29 | 158, 82, 48 (× 1,6) |
| fer | `Metal_Iron` | 134, 142, 141 | 134, 142, 141 (tel quel) |
| laiton | `Metal_Bronze` | 225, 174, 91 | 212, 168, 86 |
| marbre | `Rock_Marble` | 186, 185, 173 | 214, 212, 200 (× 1,15) |
| céramique | `Clay_Smooth_White`, `Clay_White` | 207, 207, 207 ; 202, 200, 195 | 222, 216, 206 (réchauffée) |
| coton | `Cloth_White` | 239, 239, 239 | 236, 234, 228 |
| soie (rouge) | `Cloth_Red` | 135, 57, 48 | 186, 60, 72 (plus vive) |
| seau (douelles) | `Village/Bucket_Texture.png` | 69, 39, 21 | 112, 62, 34 (× 1,6) |

Le seau se mesure sur son flanc de 21 x 23 en (0, 33), rangées 33 à 48, au-dessus du cerclage de fer (rangées 49 à
53) ; flanc entier, cerclage compris : 64, 41, 28. Sur la rangée 40, ses douelles vont de 61 à 105 en rouge et ses
joints de 43 à 53.

Sans équivalent chez Hytale, nos choix : l'acier (150, 158, 166, plus clair et plus bleu que le fer), la fonte
(96, 99, 101, comme `Metal_Iron_Decorative`, 98, 106, 106), l'argent (200, 204, 210), le granit (152, 144, 138 :
Hytale n'a que `Rock_Stone`, 121, 120, 90, et le basalte, 72, 69, 51), le lin, la laine et la toile (écrus, entre le
coton et `Cloth_Orange_Light`, 191, 148, 103). Le ton moyen de l'or, du bronze et du cuivre de `metals.py` suit
`Rock_Gold_Brick_Side` (238, 174, 68 contre 222, 176, 68), `Metal_Bronze_Ornate` (178, 128, 64 contre 176, 126, 66)
et `Metal_Copper` (205, 109, 53 contre 196, 110, 72).

## Sources

- [An Introduction to Making Models for Hytale](https://hytale.com/news/2025/12/an-introduction-to-making-models-for-hytale)
- [Substance 3D Painter : Masking and effects](https://helpx.adobe.com/substance-3d-painter/interface/layer-stack/masking-and-effects.html)
- [Substance 3D Painter : Materials and Smart Materials](https://helpx.adobe.com/substance-3d-painter/using/materials-smart-materials.html)
- [Substance 3D Painter : Anchor point](https://helpx.adobe.com/substance-3d-painter/features/effects/anchor-point.html)
- [Viking Shield: Creating Stylized Textures Using Baked Maps](https://sketchfab.com/blogs/community/viking-shield-creating-stylized-textures-using-baked-maps)
- [Context-Aware Textures (Yale)](https://graphics.cs.yale.edu/publications/context-aware-textures)
- [Visual Simulation of Weathering by γ-ton Tracing](https://ttwong12.github.io/papers/gammaton/gammaton.pdf)
- [A survey of aging and weathering phenomena in computer graphics](https://www.researchgate.net/publication/223371710_Technical_section_A_survey_of_aging_and_weathering_phenomena_in_computer_graphics)
- [Material Maker](https://www.cgchannel.com/?p=138115)
- [Color & Palettes for Pixel Art (hue shifting)](https://www.wayline.io/learn/color-palettes/2)
