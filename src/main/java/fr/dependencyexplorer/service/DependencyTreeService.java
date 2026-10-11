package fr.dependencyexplorer.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.dependencyexplorer.model.DependencyNode;
import fr.dependencyexplorer.model.ProjectRecord;
import fr.dependencyexplorer.repository.ProjectRepository;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class DependencyTreeService {
    private static final Set<String> EXCLUDED_DIRECTORIES = Set.of(".git", ".idea", "target", "node_modules");
    private final ProjectRepository projects;
    private final MavenCli mavenCli;
    private final MavenTreeParser parser;
    private final ObjectMapper objectMapper;

    public DependencyTreeService(ProjectRepository projects, MavenCli mavenCli,
                                 MavenTreeParser parser, ObjectMapper objectMapper) {
        this.projects = projects;
        this.mavenCli = mavenCli;
        this.parser = parser;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> scanFolder(String folderPath) throws IOException {
        if (folderPath == null || folderPath.isBlank()) {
            throw new IllegalArgumentException("Saisissez le chemin d'un dossier.");
        }
        Path root = Path.of(folderPath).toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException("Le dossier indiqué n'existe pas ou n'est pas accessible.");
        }

        List<Path> pomFiles;
        try (var paths = Files.walk(root)) {
            pomFiles = paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().equalsIgnoreCase("pom.xml"))
                    .filter(path -> !containsExcludedDirectory(root, path))
                    .sorted()
                    .toList();
        }

        int updated = 0;
        List<Map<String, String>> failures = new ArrayList<>();
        for (Path pom : pomFiles) {
            try {
                DependencyNode tree = parser.parse(mavenCli.dependencyTree(pom));
                if (tree == null) {
                    throw new IOException("Aucun arbre Maven n'a été trouvé dans la sortie.");
                }
                String dependenciesJson = objectMapper.writeValueAsString(tree.children());
                String pomPath = pom.toString();
                ProjectRecord record = projects.findByPomPath(pomPath)
                        .orElseGet(() -> new ProjectRecord(pomPath, tree.groupId(), tree.artifactId(),
                                tree.version(), tree.type(), Instant.now(), dependenciesJson));
                record.replaceScan(tree.groupId(), tree.artifactId(), tree.version(), tree.type(), Instant.now(), dependenciesJson);
                projects.save(record);
                updated++;
            } catch (Exception exception) {
                failures.add(Map.of("pomPath", pom.toString(), "message", safeMessage(exception)));
            }
        }

        return Map.of("folder", root.toString(), "found", pomFiles.size(), "scanned", updated,
                "failed", failures.size(), "failures", failures);
    }

    public List<ProjectRecord> listProjects() {
        return projects.findAllByOrderByArtifactIdAsc();
    }

    public List<Map<String, Object>> projectSummaries() throws IOException {
        List<Map<String, Object>> summaries = new ArrayList<>();
        for (ProjectRecord project : listProjects()) {
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("id", project.getId());
            summary.put("pomPath", project.getPomPath());
            summary.put("groupId", project.getGroupId());
            summary.put("artifactId", project.getArtifactId());
            summary.put("version", project.getVersion());
            summary.put("packaging", project.getPackaging());
            summary.put("scannedAt", project.getScannedAt());
            summary.put("directDependencyCount", readDependencies(project).size());
            summaries.add(summary);
        }
        return summaries;
    }

    public List<Map<String, String>> search(String query) throws IOException {
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (needle.length() < 2) {
            return List.of();
        }

        Map<String, Map<String, String>> matches = new LinkedHashMap<>();
        for (ProjectRecord project : listProjects()) {
            DependencyNode root = projectRoot(project);
            addMatch(matches, root, "application", needle);
            for (DependencyNode node : readDependencies(project)) {
                collectMatches(matches, node, needle);
            }
        }
        return matches.values().stream().limit(30).toList();
    }

    public Map<String, Object> tree(String key, String direction) throws IOException {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Sélectionnez un projet ou une dépendance.");
        }
        boolean reverse = "dependents".equalsIgnoreCase(direction);
        if (!reverse && !"dependencies".equalsIgnoreCase(direction)) {
            throw new IllegalArgumentException("Le sens de l'arbre doit être dependencies ou dependents.");
        }

        List<ProjectRecord> allProjects = listProjects();
        Map<String, DependencyNode> nodeIndex = new LinkedHashMap<>();
        Map<String, LinkedHashSet<String>> reverseEdges = new LinkedHashMap<>();
        for (ProjectRecord project : allProjects) {
            DependencyNode root = projectRoot(project);
            nodeIndex.putIfAbsent(root.coordinate(), root);
            for (DependencyNode dependency : readDependencies(project)) {
                collectEdges(root.coordinate(), dependency, nodeIndex, reverseEdges);
            }
        }

        DependencyNode selected = nodeIndex.get(key);
        if (selected == null) {
            throw new IllegalArgumentException("Cet élément n'existe pas dans l'index. Relancez une recherche.");
        }
        DependencyNode result = reverse
                ? buildReverse(selected, reverseEdges, nodeIndex, new HashSet<>(), 0)
                : buildForward(selected, allProjects, new HashSet<>(), 0);
        return Map.of("key", key, "direction", reverse ? "dependents" : "dependencies", "tree", result);
    }

    private DependencyNode buildForward(DependencyNode selected, List<ProjectRecord> allProjects,
                                        Set<String> stack, int depth) throws IOException {
        if (depth >= 24 || !stack.add(selected.coordinate())) {
            return copyWithChildren(selected, List.of());
        }
        List<DependencyNode> children = new ArrayList<>();
        ProjectRecord matchingProject = allProjects.stream()
                .filter(project -> projectRoot(project).coordinate().equals(selected.coordinate()))
                .findFirst().orElse(null);
        Collection<DependencyNode> next = matchingProject == null ? selected.children() : readDependencies(matchingProject);
        for (DependencyNode child : next) {
            children.add(buildForward(child, allProjects, stack, depth + 1));
        }
        stack.remove(selected.coordinate());
        return copyWithChildren(selected, children);
    }

    private DependencyNode buildReverse(DependencyNode selected, Map<String, LinkedHashSet<String>> reverseEdges,
                                        Map<String, DependencyNode> nodeIndex, Set<String> stack, int depth) {
        if (depth >= 24 || !stack.add(selected.coordinate())) {
            return copyWithChildren(selected, List.of());
        }
        List<DependencyNode> parents = new ArrayList<>();
        for (String parentKey : reverseEdges.getOrDefault(selected.coordinate(), new LinkedHashSet<>())) {
            DependencyNode parent = nodeIndex.get(parentKey);
            if (parent != null) {
                parents.add(buildReverse(parent, reverseEdges, nodeIndex, stack, depth + 1));
            }
        }
        stack.remove(selected.coordinate());
        return copyWithChildren(selected, parents);
    }

    private void collectEdges(String parentKey, DependencyNode node,
                              Map<String, DependencyNode> nodeIndex,
                              Map<String, LinkedHashSet<String>> reverseEdges) {
        nodeIndex.putIfAbsent(node.coordinate(), node);
        reverseEdges.computeIfAbsent(node.coordinate(), ignored -> new LinkedHashSet<>()).add(parentKey);
        for (DependencyNode child : node.children()) {
            collectEdges(node.coordinate(), child, nodeIndex, reverseEdges);
        }
    }

    private void collectMatches(Map<String, Map<String, String>> matches, DependencyNode node, String needle) {
        addMatch(matches, node, "dependency", needle);
        for (DependencyNode child : node.children()) {
            collectMatches(matches, child, needle);
        }
    }

    private void addMatch(Map<String, Map<String, String>> matches,
                          DependencyNode node, String kind, String needle) {
        String haystack = String.join(" ", node.groupId(), node.artifactId(), node.version()).toLowerCase(Locale.ROOT);
        if (haystack.contains(needle)) {
            Map<String, String> result = new LinkedHashMap<>();
            result.put("key", node.coordinate());
            result.put("kind", kind);
            result.put("label", node.label());
            result.put("groupId", node.groupId());
            result.put("artifactId", node.artifactId());
            result.put("version", node.version());
            matches.putIfAbsent(node.coordinate(), result);
        }
    }

    private List<DependencyNode> readDependencies(ProjectRecord project) throws IOException {
        return objectMapper.readValue(project.getDependenciesJson(), new TypeReference<>() { });
    }

    private DependencyNode projectRoot(ProjectRecord project) {
        return new DependencyNode(project.getGroupId(), project.getArtifactId(), project.getPackaging(), "",
                project.getVersion(), "", List.of());
    }

    private DependencyNode copyWithChildren(DependencyNode node, List<DependencyNode> children) {
        return new DependencyNode(node.groupId(), node.artifactId(), node.type(), node.classifier(),
                node.version(), node.scope(), children);
    }

    private static boolean containsExcludedDirectory(Path root, Path file) {
        Path relative = root.relativize(file);
        for (Path part : relative) {
            if (EXCLUDED_DIRECTORIES.contains(part.toString().toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static String safeMessage(Exception exception) {
        return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
    }
}
