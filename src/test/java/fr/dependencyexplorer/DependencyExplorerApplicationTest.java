package fr.dependencyexplorer;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:application-test;DB_CLOSE_DELAY=-1")
class DependencyExplorerApplicationTest {
    @Test
    void applicationContextStarts() {
    }
}
