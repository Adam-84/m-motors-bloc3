# Plan d'implémentation

Objectif : une application locale complète et un dossier PDF vérifié, puis publication une fois les comptes identifiés.

1. Écrire les tests de règles métier (options, pièces, transitions), constater l'échec, implémenter ces règles.
2. Ajouter la persistance versionnée et l'API, tester inscription, session/CSRF, catalogue, dossier, décision et contrôle de propriété avec H2.
3. Construire les écrans React pour ces parcours, ajouter le scénario Cypress et compiler le front.
4. Exécuter Maven/JaCoCo, tests navigateur si le runtime le permet, puis vérifier le lancement avec le front compilé.
5. Fournir conteneur, CI, procédure Git/branches, publication, sauvegarde et restauration. Initialiser le dépôt local sans publier sur un compte non identifié.
6. Rédiger le dossier selon C3.1/C3.2/C3.3, relier les cours et B1/B2, intégrer les mesures constatées et signaler les preuves de publication encore absentes. Générer le PDF et sa source Markdown éditable, puis contrôler le rendu.

Acceptation : aucun accès croisé aux pièces/dossiers, aucun rôle admin créé par inscription, rejet des fichiers invalides et transitions illégales, absence de secrets dans le dépôt, compilation et tests réellement exécutés. Les URLs publiques et la validation PostgreSQL distante restent conditionnées à l'hébergement.
