# Checklist de test en jeu — SP0, SP1+2

Serveur de dev : `./gradlew :plugin:runServer`. Il faut deux comptes : A (propriétaire) et B (étranger).
Pour obtenir les objets : `/give HyColony_TownHall`, `/give HyColony_Hut_Builder`, `/give HyColony_Hut_Residence`.

1. **Fondation.** A pose l'hôtel de ville, la fenêtre « Fonder une colonie » s'ouvre, A saisit le nom « Test » puis clique sur Fonder.
   Attendu : un message de fondation. Un double-clic ne crée qu'une colonie (vérifier avec `/hycolony info`).
2. **Annulation.** A pose un hôtel de ville dans un autre monde, puis clique sur Annuler.
   Attendu : le bloc disparaît et l'objet tombe au sol.
3. **Citoyens.** Dans les 2 minutes, 4 citoyens nommés apparaissent un par un et errent autour de l'hôtel de ville.
   S'éloigner loin pour décharger la zone, puis revenir : toujours 4 citoyens, aucun doublon.
4. **Redémarrage.** Arrêter le serveur puis le relancer.
   Attendu : la colonie et les 4 citoyens sont là, avec les mêmes noms et sans doublon. Les logs affichent `(1 colonies loaded)`.
5. **Protection.** B essaie de casser ou de poser un bloc dans la colonie, et d'ouvrir un coffre : refusé, avec un message.
   A lance `/hycolony rank B officer` : B peut alors le faire.
6. **Fenêtre.** Un clic droit sur l'hôtel de ville affiche le nom, le propriétaire, le jour et les citoyens. A renomme la colonie : le nouveau nom s'affiche.
7. **Jour/nuit.** Lancer `/hycolony info` avant et après une aube : le jour augmente de 1.
   Si l'aube réelle ne correspond pas, ajuster `HytaleGameClock.DAY_START_HOUR` et `NIGHT_START_HOUR`.
8. **Selftest.** En opérateur, `/hycolony selftest`, debout avec de l'air au-dessus de la tête : toutes les lignes sont `[OK]` (ids, stockage, `blueprint`, `place`, `container`, `break`, `spawn`, `move`).
9. **Fichiers.** `<sauvegarde du monde>/hycolony/colony-1.json` existe et contient `"schemaVersion":2` (une sauvegarde SP0 en version 1 est migrée au chargement).
10. **Échap.** A pose un hôtel de ville hors colonie, puis ferme la fenêtre « Fonder une colonie » avec Échap.
    Attendu : le bloc disparaît, l'objet tombe au sol, et le serveur ne plante pas.
11. **Commandes réservées.** B, non opérateur, lance `/hycolony delete 1` puis `/hycolony selftest` : les deux sont refusées.
12. **Citoyens orphelins.** Placer des citoyens dans des chunks, s'en éloigner pour les décharger, supprimer la colonie avec `/hycolony delete`, puis redémarrer et revenir.
    Attendu : aucun plantage, et les PNJ orphelins sont retirés.

## SP1+2 : requêtes et construction

Avant de commencer : un hôtel de ville posé **avant** cette version n'a pas de conteneur. Casser et reposer l'hôtel de ville, ou fonder une nouvelle colonie.

13. **Hutte du constructeur.** A pose `HyColony_Hut_Builder` dans sa colonie. B (étranger) ne peut pas en poser.
    Clic droit : la fenêtre « Bâtiment » s'ouvre (niveau 0 / 5, « Pas encore construit »), sans plantage du client.
14. **Embauche.** Dans les 30 secondes (tick lent), un citoyen apparaît dans « Travailleurs ». Renvoyer puis Embaucher fonctionne ; le bouton du mode d'embauche fait défiler les modes.
15. **Commande.** Bouton « Construire » sur la hutte du constructeur elle-même.
    Attendu : un message de création, la ligne d'ordre (Construire au niveau 1) et le bouton « Annuler l'ordre ». La fenêtre « Ordres » (depuis l'hôtel de ville) liste l'ordre.
16. **Chantier.** Le constructeur marche jusqu'à la hutte, dégage le terrain (haut en bas), puis pose la structure (bas en haut) et la décoration. Il tient un outil ou un bloc, les animations se jouent (sinon, le noter : repli prévu).
    Les blocs qu'il casse vont dans son inventaire, puis dans la hutte (« Stockage »).
17. **Matériaux.** La fenêtre « Ressources » liste les objets avec les couleurs rouge / orange / vert / gris. « Ajouter » déplace les objets de l'inventaire de A vers la hutte et le constructeur reprend.
    La fenêtre « Requêtes » (hôtel de ville) liste ses demandes ; « Fournir » les satisfait.
18. **Outils.** Sans pioche, le constructeur demande une pioche dès qu'il doit casser de la roche. En fournir une : il reprend. Une pioche finit par se casser (environ 150 blocs pour une rudimentaire).
19. **Fin de chantier.** Le bâtiment correspond au prefab Outlander niveau 1 autour de la hutte, tourné selon la pose de la hutte (tester les 4 orientations).
    Attendu : niveau 1, message de fin, le territoire s'agrandit (`/hycolony info`), les coffres du plan servent de stockage.
20. **Redémarrage en plein chantier.** Arrêter le serveur pendant la structure, relancer.
    Attendu : le chantier reprend là où il en était, sans reposer les blocs déjà posés ni redemander les matériaux déjà livrés.
21. **Bloc cassé.** Pendant le chantier, A casse un bloc déjà posé : il est reposé avant la fin.
22. **Amélioration puis démolition.** « Améliorer » vers le niveau 2 (plan plus grand), puis « Déconstruire » : les blocs sont retirés, le niveau est conservé et la fenêtre affiche « Déconstruit ». « Ramasser » rend la hutte (inventaire plein : refus avec un message, la hutte reste).
23. **Résidence et style.** Poser une résidence, choisir le style `kweebec` puis « Construire » : la maison Kweebec est construite.
24. **Stockage.** « Stockage » ouvre le coffre de la hutte ; le fermer puis le rouvrir fonctionne. Y déposer un objet demandé débloque la requête.
25. **Permissions.** B, sans rang, ne peut ni ouvrir une hutte de la colonie ni utiliser les boutons.

