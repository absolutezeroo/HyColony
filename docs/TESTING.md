# Checklist de test en jeu — SP0

Serveur de dev : `./gradlew :plugin:runServer`. Il faut deux comptes : A (propriétaire) et B (étranger).
Pour obtenir l'objet : `/give HyColony_TownHall`.

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
8. **Selftest.** En opérateur, `/hycolony selftest` : toutes les lignes sont `[OK]`.
9. **Fichiers.** `<sauvegarde du monde>/hycolony/colony-1.json` existe et contient `"schemaVersion":1`.
