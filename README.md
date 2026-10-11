# Dependency Explorer

Application web Spring Boot pour indexer et explorer les dépendances Maven de plusieurs projets Java.

## Prérequis

- Java 17 ou supérieur
- Maven 3.6.3 ou supérieur, accessible dans le `PATH`
- Une connexion réseau au premier démarrage pour récupérer les dépendances de l'application et celles des projets analysés

## Démarrer l'application

Depuis ce dossier :

```powershell
mvn spring-boot:run
```

Ouvrez ensuite [http://localhost:8080](http://localhost:8080). La base H2 persistante est créée dans `data/dependency-explorer.mv.db`. La console H2 se trouve sur `/h2-console` (JDBC URL `jdbc:h2:file:./data/dependency-explorer;AUTO_SERVER=TRUE`, utilisateur `sa`, mot de passe vide).

## Utilisation

1. Cliquez sur **Analyser un dossier** et saisissez un chemin absolu accessible depuis le processus Java.
2. L'application cherche les `pom.xml` dans les sous-dossiers, puis exécute `mvn dependency:tree` pour chacun. Les dossiers `target`, `.git`, `.idea` et `node_modules` sont ignorés.
3. Choisissez un projet dans le panneau gauche ou recherchez un projet/composant avec la barre en haut.
4. Utilisez **Dépendances** pour parcourir son arbre Maven et **Utilisé par** pour voir les projets et bibliothèques indexés qui en dépendent.

Les dépendances directes et transitives sont enregistrées pour chaque projet analysé. Les liens inverses sont construits à partir des arbres de l'index courant et des versions effectivement résolues. Relancez l'analyse après une modification des `pom.xml` pour actualiser les résultats.

Si la commande Maven n'est pas `mvn` (ou `mvn.cmd` sous Windows), définissez `DEPENDENCY_EXPLORER_MAVEN_EXECUTABLE` avec le nom de l'exécutable avant le démarrage. Le délai maximal est de 180 secondes par `pom.xml` par défaut et peut être modifié avec `DEPENDENCY_EXPLORER_MAVEN_TIMEOUT_SECONDS`.

## Vérifier et empaqueter

```powershell
mvn test
mvn package
```

Lancer le JAR produit avec :

```powershell
java -jar target/dependency-explorer-1.0.0.jar
```
