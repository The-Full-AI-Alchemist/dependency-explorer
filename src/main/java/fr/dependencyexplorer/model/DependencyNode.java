package fr.dependencyexplorer.model;

import java.util.List;

public record DependencyNode(
        String groupId,
        String artifactId,
        String type,
        String classifier,
        String version,
        String scope,
        List<DependencyNode> children) {

    public DependencyNode {
        children = children == null ? List.of() : List.copyOf(children);
    }

    public String coordinate() {
        return String.join(":", clean(groupId), clean(artifactId), clean(type), clean(classifier), clean(version));
    }

    public String label() {
        return clean(artifactId).isBlank() ? coordinate() : artifactId;
    }

    private static String clean(String value) {
        return value == null ? "" : value;
    }
}
