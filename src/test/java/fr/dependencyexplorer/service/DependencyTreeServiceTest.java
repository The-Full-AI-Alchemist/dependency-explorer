package fr.dependencyexplorer.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import fr.dependencyexplorer.model.DependencyNode;
import fr.dependencyexplorer.model.ProjectRecord;
import fr.dependencyexplorer.repository.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DependencyTreeServiceTest {
    @TempDir
    Path temporaryDirectory;

    private ProjectRepository repository;
    private MavenCli mavenCli;
    private DependencyTreeService service;

    @BeforeEach
    void setUp() {
        repository = mock(ProjectRepository.class);
        mavenCli = mock(MavenCli.class);
        service = new DependencyTreeService(repository, mavenCli, new MavenTreeParser(), new ObjectMapper());
    }

    @Test
    void scansNestedPomFilesAndStoresResolvedDependencies() throws Exception {
        Path module = Files.createDirectories(temporaryDirectory.resolve("module"));
        Path pom = Files.writeString(module.resolve("pom.xml"), "<project/>");
        when(mavenCli.dependencyTree(pom)).thenReturn(List.of(
                "[INFO] org.demo:module:jar:1.2",
                "[INFO] \\- org.demo:shared:jar:2.0:compile"));
        when(repository.findByPomPath(pom.toString())).thenReturn(Optional.empty());

        var summary = service.scanFolder(temporaryDirectory.toString());

        assertEquals(1, summary.get("found"));
        assertEquals(1, summary.get("scanned"));
        verify(repository).save(any(ProjectRecord.class));
    }

    @Test
    void searchFindsAppsAndTransitiveLibraries() throws Exception {
        DependencyNode common = node("org.demo", "common", "3.0", List.of());
        DependencyNode shared = node("org.demo", "shared", "2.0", List.of(common));
        ProjectRecord project = project("org.demo", "application", "1.0", List.of(shared));
        when(repository.findAllByOrderByArtifactIdAsc()).thenReturn(List.of(project));

        var results = service.search("common");

        assertEquals(1, results.size());
        assertEquals("org.demo:common:jar::3.0", results.get(0).get("key"));
        assertEquals("dependency", results.get(0).get("kind"));
    }

    @Test
    void projectSummariesIncludeDirectDependencyCounts() throws Exception {
        ProjectRecord project = project("org.demo", "application", "1.0", List.of(
                node("org.demo", "first", "1", List.of()), node("org.demo", "second", "1", List.of())));
        when(repository.findAllByOrderByArtifactIdAsc()).thenReturn(List.of(project));

        var summaries = service.projectSummaries();

        assertEquals(2, summaries.get(0).get("directDependencyCount"));
        assertEquals("application", summaries.get(0).get("artifactId"));
    }

    @Test
    void returnsForwardAndReverseDependencyTrees() throws Exception {
        DependencyNode shared = node("org.demo", "shared", "2.0", List.of());
        ProjectRecord firstApp = project("org.demo", "first-app", "1.0", List.of(shared));
        ProjectRecord secondApp = project("org.demo", "second-app", "1.0", List.of(shared));
        when(repository.findAllByOrderByArtifactIdAsc()).thenReturn(List.of(firstApp, secondApp));

        var forward = service.tree("org.demo:first-app:jar::1.0", "dependencies");
        var reverse = service.tree("org.demo:shared:jar::2.0", "dependents");

        assertEquals("shared", ((DependencyNode) forward.get("tree")).children().get(0).artifactId());
        assertEquals("dependents", reverse.get("direction"));
        assertEquals(2, ((DependencyNode) reverse.get("tree")).children().size());
    }

    @Test
    void validatesFolderBeforeLookingForPoms() {
        assertThrows(IllegalArgumentException.class, () -> service.scanFolder("  "));
        verify(repository, never()).save(any());
    }

    private ProjectRecord project(String group, String artifact, String version, List<DependencyNode> dependencies)
            throws Exception {
        return new ProjectRecord("/repo/" + artifact + "/pom.xml", group, artifact, version,
                Instant.now(), new ObjectMapper().writeValueAsString(dependencies));
    }

    private DependencyNode node(String group, String artifact, String version, List<DependencyNode> children) {
        return new DependencyNode(group, artifact, "jar", "", version, "compile", children);
    }
}
