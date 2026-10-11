package fr.dependencyexplorer.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class MavenCli {
    private final String executable;
    private final Duration timeout;

    public MavenCli(@Value("${dependency-explorer.maven-executable:mvn}") String executable,
                    @Value("${dependency-explorer.maven-timeout-seconds:180}") long timeoutSeconds) {
        if (timeoutSeconds < 1) {
            throw new IllegalArgumentException("Le délai d'exécution Maven doit être positif.");
        }
        this.executable = executable;
        this.timeout = Duration.ofSeconds(timeoutSeconds);
    }

    public List<String> dependencyTree(Path pomFile) throws IOException, InterruptedException {
        Path pom = pomFile.toAbsolutePath().normalize();
        if (!Files.isRegularFile(pom) || !pom.getFileName().toString().equalsIgnoreCase("pom.xml")) {
            throw new IllegalArgumentException("Le chemin doit désigner un fichier pom.xml existant.");
        }

        Path logFile = Files.createTempFile("dependency-explorer-maven-", ".log");
        Process process = null;
        try {
            ProcessBuilder builder = new ProcessBuilder(command());
            builder.directory(pom.getParent().toFile());
            builder.redirectErrorStream(true);
            builder.redirectOutput(logFile.toFile());
            process = builder.start();
            boolean completed = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!completed) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
                throw new IOException("L'analyse Maven a dépassé le délai de " + timeout.toSeconds() + " secondes.");
            }

            List<String> output = Files.readAllLines(logFile, StandardCharsets.UTF_8);
            if (process.exitValue() != 0) {
                String details = output.stream().filter(line -> !line.isBlank()).reduce((a, b) -> b).orElse("Aucun détail fourni.");
                throw new IOException("Maven a échoué pour " + pom + " : " + details);
            }
            return output;
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
            Files.deleteIfExists(logFile);
        }
    }

    List<String> command() {
        List<String> args = new ArrayList<>();
        if (System.getProperty("os.name", "").toLowerCase().contains("win")) {
            args.addAll(List.of("cmd.exe", "/d", "/s", "/c"));
            String windowsExecutable = executable.toLowerCase().endsWith(".cmd") ? executable : executable + ".cmd";
            args.add("\"" + windowsExecutable + "\" dependency:tree -DoutputType=text -Dstyle.color=never");
        } else {
            args.addAll(List.of(executable, "dependency:tree", "-DoutputType=text", "-Dstyle.color=never"));
        }
        return args;
    }
}
