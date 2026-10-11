package fr.dependencyexplorer.repository;

import fr.dependencyexplorer.model.ProjectRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = "spring.datasource.url=jdbc:h2:mem:repository-test;DB_CLOSE_DELAY=-1")
class ProjectRepositoryTest {
    @Autowired
    private ProjectRepository repository;

    @Test
    void savesProjectsAndFindsThemByPomPathInArtifactOrder() {
        repository.save(new ProjectRecord("/repo/z/pom.xml", "org.demo", "zeta", "1", Instant.now(), "[]"));
        repository.save(new ProjectRecord("/repo/a/pom.xml", "org.demo", "alpha", "1", Instant.now(), "[]"));

        assertThat(repository.findByPomPath("/repo/z/pom.xml")).isPresent();
        assertThat(repository.findAllByOrderByArtifactIdAsc())
                .extracting(ProjectRecord::getArtifactId)
                .containsExactly("alpha", "zeta");
    }
}
