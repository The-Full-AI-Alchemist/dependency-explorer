package fr.dependencyexplorer.model;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DependencyNodeTest {
    @Test
    void createsStableCoordinateAndLabel() {
        DependencyNode node = new DependencyNode("org.example", "demo", "jar", "tests", "1.2", "test", List.of());

        assertEquals("org.example:demo:jar:tests:1.2", node.coordinate());
        assertEquals("demo", node.label());
    }

    @Test
    void childrenAreCopiedAndNullBecomesEmptyList() {
        List<DependencyNode> children = new ArrayList<>();
        DependencyNode node = new DependencyNode("g", "a", "jar", "", "1", "", children);
        children.add(new DependencyNode("g", "b", "jar", "", "1", "compile", List.of()));

        assertEquals(List.of(), node.children());
        assertEquals(List.of(), new DependencyNode("g", "a", "jar", "", "1", "", null).children());
        assertThrows(UnsupportedOperationException.class, () -> node.children().add(null));
    }
}
