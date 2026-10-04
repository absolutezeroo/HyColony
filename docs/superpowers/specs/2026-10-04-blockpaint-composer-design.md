# Spec : le compositeur de valeurs et d'accents de blockpaint (Value & Focus Composer)

Date : 2026-10-04. Suite de `2026-10-04-blockpaint-illustration-design.md` (la passe d'illustration). Outil seulement :
`tools/blockpaint/` et les modules des modèles ; aucune règle de jeu. Demandé par l'utilisateur le 2026-10-04.

## 1. Le problème

Le moteur peint bien chaque surface, et la passe d'illustration compose chaque forme (repos, modelé, cadre, contacts,
reflets). Mais personne ne regarde le modèle entier : chaque pièce reste dans des valeurs moyennes, toutes réclament
l'attention, le fond du rayonnage pèse autant que le registre. Un illustrateur décide d'abord ce qui avance et ce qui
recule, où vont les contrastes les plus forts, quelles zones restent calmes, combien d'accents le modèle supporte.

## 2. Le principe

Le pipeline devient : surface → passe d'illustration → **compositeur** → lumière (bake) → critique.

- Le compositeur raisonne sur **le modèle entier** : ses pièces (une pièce et une famille de matière forment une
  **unité** : le registre en cuir et ses pages en papier sont deux unités), leurs voisines, leurs valeurs, leur
  importance, leur taille.
- Il n'ajoute ni matière, ni pinceau, ni effet, ni bruit : il déplace des valeurs, du contraste, de la saturation, et
  en retire.
- Il ne change aucune couche : comme la passe, il agit sur la texture d'avant la lumière, île enregistrée par île
  enregistrée, mais ses décisions viennent du modèle entier.
- La lumière cuite vient après et multiplie : elle garde les rapports de valeur qu'il a posés (un plus clair reste plus
  clair) ; il ne peint donc pas d'ombre de forme, seulement la hiérarchie.
- **Désactivé par défaut** : sans `COMPOSER`, rien ne change à l'octet.
- Il rend un **rapport**, une ligne par décision, en français.

### 2.1 Partage avec la passe d'illustration

La passe garde le travail **local** d'une forme : repos, modelé, cadre des faces, contacts, reflets sélectifs. Les
décisions **globales** passent au compositeur : la séparation des matières (devenue le contraste des matières, § 3.8)
et les accents focaux (devenus le renfort focal et le recul du fond, § 3.2 et 3.3). Sans compositeur, la passe ne fait
donc plus ces deux-là.

### 2.2 L'importance visuelle

La même échelle que la passe (0 calme, 1 normale, 2 importante, 3 accent majeur ; une pièce `FOCUS` sans valeur
nommée vaut 2), déclarée une fois par le module (`IMPORTANCE`), lue par la passe et par le compositeur.

## 3. Version 1 (l'entrepôt)

Le budget d'accents passe d'abord (il décide quelles unités restent importantes), puis les opérations de
`composer.STEPS`, dans cet ordre, chacune lisant la texture que la précédente a laissée.

1. **Budget d'accents** (`composer_budgets.accents`). Le modèle a une taille (petit jusqu'à `SMALL` bloc, moyen
   jusqu'à `MEDIUM`, grand au-delà, selon le volume de ses bornes) et un budget d'accents (`ACCENTS` : 2, 4, 7). Les
   unités d'importance 2 et 3 forment des accents : les unités d'une même pièce (le cuir et les pages du registre) et
   les unités voisines d'une même famille (le socle, le pied et le fléau de la balance) n'en font qu'un. Les plus
   importants, puis les plus grands, sont gardés ; au-delà du budget, les autres retombent à l'importance 1.
2. **Groupes de valeur** (`groups`). Le module peut nommer le groupe d'une pièce (`Composer.groups` : sombre profond,
   sombre, moyen, clair, accent) ; sinon : importance 3 → accent ; papier ou importance 2 → clair ; importance 0 →
   sombre. La cible d'un groupe est un centile de la valeur du modèle lui-même (`GROUP_CENTILES`). Une unité
   **normale** n'a pas de groupe : elle vise son propre centile, écarté du milieu de `SPREAD` fois (ce qui est déjà
   plus sombre que la plupart s'assombrit, ce qui est plus clair s'éclaircit ; un grand bâti sombre s'assombrit donc
   sans règle de taille, qui assombrissait aussi un grand drap blanc). Chaque unité est tirée de `VALUE_PULL` vers sa
   cible, son contraste interne et sa teinte gardés, son facteur borné (`PULL_LIMIT`, de 0,75 à 4/3 : au-delà, on lit
   une autre matière, comme la pierre claire du sol de la grande carrière, divisée par deux, lue comme du charbon) et
   **à sens unique** pour un groupe : une unité claire ou d'accent ne s'assombrit jamais, une sombre ne s'éclaircit
   jamais. Le modèle s'étale ainsi sur sa gamme ; si son étendue (du 10e au 90e centile) rétrécissait quand même,
   l'étape est annulée et le rapport le dit.
3. **Recul du fond** (`background`). Une unité d'importance 0 perd `BACKGROUND_CONTRAST` de son contraste autour de
   sa clarté moyenne (en valeur seule : chaque texel monte ou descend d'autant sur r, g et b, sa couleur gardée) et
   `BACKGROUND_SATURATION` de sa saturation : elle recule sans être seulement assombrie.
4. **Renfort focal** (`focal`). Une unité d'importance 2 gagne `FOCAL_CONTRAST[2]` de contraste, une d'importance 3
   `FOCAL_CONTRAST[3]` et `FOCAL_SATURATION` de saturation.
5. **Budget de sombres** (`darks`). Les `DARK_SHARE` texels les plus enfermés du modèle (occlusion d'au moins
   `DARK_OCCLUSION` : sous les étagères, aux jonctions, au fond des creux) s'assombrissent de `DEEP` ; jamais sous
   `FLOOR` en valeur, la teinte gardée (la lumière cuite tourne ensuite les ombres vers le violet, comme partout).
6. **Budget de clairs** (`lights`). Reflet primaire : les arêtes éclairées (bague extérieure tournée vers la lumière,
   `illustration.lit_edge`) d'une unité d'importance 3 s'éclaircissent de `PRIMARY` ; secondaire : celles d'une unité
   d'importance 2, de `SECONDARY` ; une unité normale garde les reflets de la passe ; les clairs d'arête d'une unité
   calme sont ramenés de `DIMMED` vers sa moyenne.
7. **Routage du contraste** (`routing`). Deux unités voisines (à `TOUCH` près) dont les valeurs moyennes se
   confondent (moins de `MERGED`, mesuré une fois avant tout déplacement) et dont l'importance diffère : chaque unité
   ne bouge qu'une fois, de `ROUTE`, plus claire si elle est devant dans plus de ces paires qu'elle n'est derrière,
   plus sombre si elle est derrière dans plus, pas du tout à égalité. Une pièce entourée de voisines n'est pas
   poussée encore et encore.
8. **Contraste des matières** (`materials`). Deux unités voisines de familles différentes et de couleurs trop proches
   (moins de `DISTINCT_COLOUR`, le seuil que `semantics` vérifie aussi) s'écartent sur **un seul axe** : d'abord celui
   où elles diffèrent déjà le plus (valeur, saturation, ou température quand les deux sont assez saturées,
   `HUE_SATURATION` : sur un gris, la teinte est du bruit), sinon le suivant qui atteint le seuil, sinon le plus
   écarté au plus permis. Chaque essai part des couleurs d'origine, par `SEPARATE_STEPS` pas jusqu'à `SEPARATE`
   (`SEPARATE_HUE` degrés pour la température) : un pas recalculé depuis un pixel déjà arrondi se perdait sur les
   couleurs sombres. Chaque unité ne bouge qu'une fois, et une unité de plus de `LARGER` fois les texels de sa
   voisine est le décor dont la petite s'écarte : elle ne bouge pas (un bougeoir de laiton tournait tout le plan de
   travail de l'apothicaire au rose). Le rapport dit si le seuil est atteint. Bois brun et cuir brun : le bois tourné
   vers le jaune, ou le cuir plus sombre, jamais les deux.
9. **Vérification à distance** (`distance`). À 25 % du côté (chaque bloc de `AFAR` x `AFAR` texels en un), chaque
   accent, ses unités ensemble, se lit-il encore ? Il le faut : un écart de valeur moyenne d'au moins `MERGED` avec les
   unités qui l'entourent hors de l'accent, et au moins un bloc entier de ses texels. Le verdict va au rapport.

Chaque constante est une première valeur, calée à l'œil sur l'entrepôt : premières valeurs prudentes, puis poussées
après la première planche, où la différence ne se voyait pas.

## 4. Lots suivants

- **silhouette** : quelques portions de silhouette des unités importantes appuyées, jamais de contour uniforme ;
- **équilibrage de palette** : les teintes trop proches ou trop dispersées regroupées, les matières, accents et
  couleurs narratives gardés ;
- **solveur de distance complet** : 100, 50, 25 et 12,5 % ; un détail qui ne survit pas et n'aide ni la matière ni
  la silhouette réduit ;
- **zones de repos globales** : une grande face sans repos voit ses variations secondaires réduites ;
- **asymétrie** : accents et usure décentrés.

## 5. Emploi dans un modèle

Un module déclare `IMPORTANCE = {pièce: 0..3}` et `COMPOSER = composer.Composer()` (et `ILLUSTRATION`), avec une
`CONDITION`. `catalog` les passe à la peinture et affiche le rapport.

## 6. Validation

Sur l'entrepôt, trois rendus côte à côte dans Blockbench : sans passe, avec la passe, avec la passe et le
compositeur. Le troisième doit montrer un fond plus calme, des objets importants mieux séparés, des valeurs
hiérarchisées, quelques sombres plus profonds, quelques clairs plus francs, des accents limités mais visibles, une
meilleure lecture à distance, et aucun bruit ajouté. L'utilisateur valide avant tout emploi dans un modèle du jeu.

## 7. Tests

- sans `COMPOSER`, toutes les ressources régénérées sont identiques à l'octet ;
- les groupes : une unité d'importance 0 finit plus sombre, une d'importance 2 plus claire, qu'avant ; l'écart entre
  la plus claire et la plus sombre du modèle ne rétrécit jamais (l'étape est annulée, et le rapport le dit, quand
  elle le ferait) ; un groupe ne change pas une matière en une autre (`PULL_LIMIT`) ;
- le fond perd du contraste et de la saturation, un accent en gagne ; le contraste joue sur la valeur seule (la
  rouille d'un fer sombre s'éclaircit sans devenir un orange plus saturé) ;
- un accent qui se confond avec ce qui l'entoure ne se lit pas de loin, même entier dans un bloc ;
- au-delà du budget, les accents les moins importants perdent leur renfort ;
- les sombres profonds restent rares (au plus `DARK_SHARE` des texels) et jamais sous `FLOOR` ;
- les clairs primaires vont aux accents, aucun clair d'arête aux unités calmes ;
- deux unités confondues se séparent, la plus importante plus claire ;
- deux matières trop proches s'écartent sur un seul axe, chaque essai depuis leurs couleurs d'origine, les gris
  jamais par la température, la teinte la plus avancée sur le cercle tournée plus loin et l'autre ramenée ; chaque
  unité ne bouge qu'une fois ; une unité de plus de `LARGER` fois sa voisine ne bouge pas (à `LARGER` fois
  exactement, les deux bougent) ; quand aucun axe ne suffit, le plus écarté sert au plus permis et le rapport le dit ;
- le rapport dit chaque décision ; le compositeur ne change aucune couche.
