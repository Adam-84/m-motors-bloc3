# Publication et exploitation

## Git et branches

Compte cible indiqué : https://github.com/Adam-84. Nom prévu : `m-motors-bloc3`. Monorepo avec deux composants séparés. `main` porte la version stable, `feat/<story>` porte une évolution courte, `fix/<incident>` un correctif. Une branche est intégrée après tests et revue ; les protections de branche se configurent dans GitHub. L'historique local ne prétend pas reproduire des sprints passés.

## Render

1. Créer un compte Render avec GitHub et connecter le dépôt public.
2. Dans Render, choisir **New > Blueprint**, sélectionner le dépôt et lire le plan proposé par `render.yaml`.
3. Vérifier que les deux ressources sont bien gratuites : application Docker et PostgreSQL à Francfort. Renseigner ADMIN_PASSWORD et USER_PASSWORD (12 caractères minimum). Les mots de passe de démo du README peuvent servir uniquement à un environnement public contenant exclusivement des données fictives.
4. Lancer le déploiement ; attendre les logs Spring/Flyway et un contrôle `/actuator/health` renvoyant `UP`. L'URL JDBC est construite avec les propriétés privées de PostgreSQL ; aucun secret n'est dans Git.
5. Ouvrir l'URL HTTPS sans compte : catalogue visible. Tester inscription, pièces fictives, suivi, connexion admin et décision. Vérifier l'impossibilité d'accéder au dossier d'un autre utilisateur.
6. Reporter l'URL réelle et les deux comptes d'évaluation dans le tableau de début du dossier. Conserver le lien du pipeline vert et le hash du commit déployé.

La formule gratuite met le service en veille après 15 minutes sans trafic et la base gratuite expire après 30 jours. Choisir la date de création selon la période de correction. Le déploiement gratuit ne remplit pas les objectifs de disponibilité de l'entreprise du B1. Aucun abonnement payant ne doit être souscrit automatiquement.

Sources officielles consultées le 5 novembre 2026 : https://render.com/docs/free et https://render.com/docs/blueprint-spec.

## CI/CD

Le workflow construit React, exécute JUnit/JaCoCo sur H2, puis les mêmes contrats API sur PostgreSQL 17. Il démarre le JAR et exécute Cypress. Les rapports et le JAR sont conservés comme artefacts. Render est configuré pour attendre les contrôles Git avant de redéployer. Cette configuration nécessite de vérifier dans les deux interfaces que le lien CI/CD est bien actif.

Pour la première user story DONE, déployer une branche ou un commit de catalogue sur une instance de préproduction et enregistrer la preuve. Aucun déploiement précoce antérieur n'est revendiqué dans le dossier si l'application complète a été construite d'un seul tenant.

## Sauvegarde et retour arrière

Avant une évolution de schéma, exporter une sauvegarde PostgreSQL avec `pg_dump --format=custom` en utilisant un compte et un canal autorisés. Sauvegarder hors du service, chiffrer le support et limiter les accès. Les pièces sont incluses dans la base. Vérifier régulièrement la restauration sur une base distincte avec `pg_restore`, les nombres de clients/dossiers/pièces, les contraintes référentielles et le téléchargement d'un échantillon fictif.

En cas de régression sans changement incompatible de schéma, redéployer l'image du commit précédent. Pour une migration incompatible : fenêtre planifiée, arrêt des écritures, restauration de la sauvegarde vers une base distincte, vérification puis bascule. Ne pas lancer de restauration destructive sur la base active pour un simple test.

RPO proposé pour une cible entreprise : 15 minutes ; RTO proposé : 1 heure. Ce sont des hypothèses à arbitrer avec le PO. La mesure réelle exige sauvegardes/PITR adaptés, exercice chronométré de restauration et surveillance des sauvegardes. Une sauvegarde quotidienne seule ne garantit pas un RPO de 15 minutes.

## Surveillance

`/actuator/health` contrôle l'état de l'application et la connexion à la BDD sans divulguer les détails. Les événements de création de dossier, dépôt de pièce, soumission, décision et changement d'offre sont journalisés avec un identifiant technique. Les erreurs inattendues retournent une référence de corrélation au client. Les exceptions de validation produisent un 400, les conflits métier un 409 et les accès aux ressources non possédées un 404.

Pour un service réellement exploité, raccorder les logs à une rétention limitée, des alertes sur indisponibilité/5xx et un suivi des sauvegardes. Les métriques de débit et latence ne sont pas encore exposées publiquement. Ajouter une limitation de débit et un contrôle antivirus avant de traiter des pièces réelles.
