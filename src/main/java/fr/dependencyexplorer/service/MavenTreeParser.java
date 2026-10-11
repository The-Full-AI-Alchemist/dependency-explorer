package fr.dependencyexplorer.service;

import fr.dependencyexplorer.model.DependencyNode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

@Component
public class MavenTreeParser {
    private static final Pattern ANSI = Pattern.compile("\\u001B\\[[;\\d]*m");

    public DependencyNode parse(List<String> output) {
        MutableNode root = null;
        Map<Integer, MutableNode> nodesAtDepth = new TreeMap<>();

        for (String rawLine : output) {
            String line = ANSI.matcher(rawLine).replaceAll("").strip();
            if (!line.startsWith("[INFO]")) continue;
            line = line.substring("[INFO]".length()).stripLeading();
            if (line.isBlank()) {
                continue;
            }

            int depth = 0;
            while (line.startsWith("|  ") || line.startsWith("   ")) {
                line = line.substring(3);
                depth++;
            }
            boolean childLine = line.startsWith("+- ") || line.startsWith("\\- ");
            if (childLine) {
                line = line.substring(3);
            } else if (root != null) {
                continue;
            }

            DependencyNode coordinate = parseCoordinate(line);
            if (coordinate == null) {
                continue;
            }
            MutableNode current = new MutableNode(coordinate);
            if (root == null) {
                root = current;
                nodesAtDepth.put(-1, root);
            } else if (childLine) {
                MutableNode parent = nodesAtDepth.get(depth - 1);
                if (parent == null) {
                    continue;
                }
                parent.children.add(current);
                nodesAtDepth.put(depth, current);
                var levels = nodesAtDepth.keySet().iterator();
                while (levels.hasNext()) {
                    if (levels.next() > depth) levels.remove();
                }
            }
        }

        return root == null ? null : root.toDependencyNode();
    }

    private DependencyNode parseCoordinate(String line) {
        int details = line.indexOf(" (");
        String coordinate = details >= 0 ? line.substring(0, details) : line;
        String[] parts = coordinate.trim().split(":", -1);
        if (parts.length < 4 || parts[0].isBlank() || parts[1].isBlank()) {
            return null;
        }

        if (parts.length >= 6) {
            return new DependencyNode(parts[0], parts[1], parts[2], parts[3], parts[4], parts[5], List.of());
        }
        String scope = parts.length >= 5 ? parts[4] : "";
        return new DependencyNode(parts[0], parts[1], parts[2], "", parts[3], scope, List.of());
    }

    private static final class MutableNode {
        private final DependencyNode value;
        private final List<MutableNode> children = new ArrayList<>();

        private MutableNode(DependencyNode value) {
            this.value = value;
        }

        private DependencyNode toDependencyNode() {
            return new DependencyNode(value.groupId(), value.artifactId(), value.type(), value.classifier(),
                    value.version(), value.scope(), children.stream().map(MutableNode::toDependencyNode).toList());
        }
    }
}
