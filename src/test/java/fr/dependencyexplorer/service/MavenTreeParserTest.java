package fr.dependencyexplorer.service;

import fr.dependencyexplorer.model.DependencyNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MavenTreeParserTest {
    private final MavenTreeParser parser = new MavenTreeParser();

    @Test
    void parsesNestedTreeScopesAndClassifier() {
        DependencyNode root = parser.parse(List.of(
                "[INFO] org.demo:application:jar:1.0",
                "[INFO] +- org.springframework:spring-web:jar:6.2.3:compile",
                "[INFO] |  +- org.springframework:spring-core:jar:6.2.3:compile",
                "[INFO] |  |  \\- org.springframework:spring-jcl:jar:6.2.3:compile",
                "[INFO] |  \\- com.fasterxml:jackson-core:jar:2.18.0:runtime",
                "[INFO] \\- org.demo:fixtures:jar:tests:1.0:test"));

        assertEquals("org.demo:application:jar::1.0", root.coordinate());
        assertEquals(2, root.children().size());
        assertEquals("spring-web", root.children().get(0).artifactId());
        assertEquals(2, root.children().get(0).children().size());
        assertEquals("spring-jcl", root.children().get(0).children().get(0).children().get(0).artifactId());
        assertEquals("runtime", root.children().get(0).children().get(1).scope());
        DependencyNode testFixtures = root.children().get(1);
        assertEquals("tests", testFixtures.classifier());
        assertEquals("test", testFixtures.scope());
    }

    @Test
    void ignoresWarningsAndReturnsNullWhenOutputContainsNoTree() {
        assertNull(parser.parse(List.of("[WARNING] POM group:artifact:jar:1 is missing", "Build finished")));
    }
}
