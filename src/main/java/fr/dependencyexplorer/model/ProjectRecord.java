package fr.dependencyexplorer.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

@Entity
@Table(name = "scanned_projects", uniqueConstraints = @UniqueConstraint(columnNames = "pom_path"))
public class ProjectRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "pom_path", nullable = false, length = 2048)
    private String pomPath;

    @Column(name = "group_id", nullable = false)
    private String groupId;

    @Column(name = "artifact_id", nullable = false)
    private String artifactId;

    @Column(nullable = false)
    private String version;

    @Column(nullable = false)
    private String packaging;

    @Column(nullable = false)
    private Instant scannedAt;

    @Lob
    @Column(name = "dependencies_json", nullable = false)
    @JsonIgnore
    private String dependenciesJson;

    protected ProjectRecord() {
    }

    public ProjectRecord(String pomPath, String groupId, String artifactId, String version,
                         Instant scannedAt, String dependenciesJson) {
        this(pomPath, groupId, artifactId, version, "jar", scannedAt, dependenciesJson);
    }

    public ProjectRecord(String pomPath, String groupId, String artifactId, String version, String packaging,
                         Instant scannedAt, String dependenciesJson) {
        this.pomPath = pomPath;
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
        this.packaging = packaging;
        this.scannedAt = scannedAt;
        this.dependenciesJson = dependenciesJson;
    }

    public Long getId() {
        return id;
    }

    public String getPomPath() {
        return pomPath;
    }

    public String getGroupId() {
        return groupId;
    }

    public String getArtifactId() {
        return artifactId;
    }

    public String getVersion() {
        return version;
    }

    public String getPackaging() {
        return packaging;
    }

    public Instant getScannedAt() {
        return scannedAt;
    }

    @JsonIgnore
    public String getDependenciesJson() {
        return dependenciesJson;
    }

    public void replaceScan(String groupId, String artifactId, String version, String packaging,
                            Instant scannedAt, String dependenciesJson) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
        this.packaging = packaging;
        this.scannedAt = scannedAt;
        this.dependenciesJson = dependenciesJson;
    }
}
