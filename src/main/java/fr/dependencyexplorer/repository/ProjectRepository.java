package fr.dependencyexplorer.repository;

import fr.dependencyexplorer.model.ProjectRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProjectRepository extends JpaRepository<ProjectRecord, Long> {
    Optional<ProjectRecord> findByPomPath(String pomPath);

    List<ProjectRecord> findAllByOrderByArtifactIdAsc();
}
