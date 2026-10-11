package fr.dependencyexplorer.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MavenCliTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void rejectsNonPomFilesBeforeStartingAProcess() throws Exception {
        Path other = Files.writeString(temporaryDirectory.resolve("build.xml"), "<project/>");
        MavenCli cli = new MavenCli("mvn", 30);

        assertThrows(IllegalArgumentException.class, () -> cli.dependencyTree(other));
    }

    @Test
    void rejectsNonPositiveTimeout() {
        assertThrows(IllegalArgumentException.class, () -> new MavenCli("mvn", 0));
    }

    @Test
    void buildsACommandThatRequestsTheTextDependencyTree() {
        MavenCli cli = new MavenCli("mvn", 30);

        assertTrue(cli.command().stream().anyMatch(value -> value.contains("dependency:tree")));
        assertTrue(cli.command().stream().anyMatch(value -> value.contains("outputType=text")));
    }
}
