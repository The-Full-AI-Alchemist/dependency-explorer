package fr.dependencyexplorer.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProjectRecordTest {
    @Test
    void storesAndReplacesScanMetadata() {
        Instant firstScan = Instant.parse("2026-01-01T10:00:00Z");
        Instant secondScan = Instant.parse("2026-01-02T10:00:00Z");
        ProjectRecord project = new ProjectRecord("C:/demo/pom.xml", "org.demo", "demo", "1.0", firstScan, "[]");

        assertEquals("C:/demo/pom.xml", project.getPomPath());
        assertEquals("org.demo", project.getGroupId());
        assertEquals("demo", project.getArtifactId());
        assertEquals("1.0", project.getVersion());
        assertEquals("jar", project.getPackaging());
        assertEquals(firstScan, project.getScannedAt());

        project.replaceScan("org.demo", "demo", "1.1", "war", secondScan, "[{}]");

        assertEquals("1.1", project.getVersion());
        assertEquals("war", project.getPackaging());
        assertEquals(secondScan, project.getScannedAt());
        assertEquals("[{}]", project.getDependenciesJson());
    }
}
