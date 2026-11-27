package com.hackathon.backend;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * The policy and approval-chain engines must stay framework-free so they can be unit-tested
 * on their own (CLAUDE.md §9.2, backend/SPEC.md §2).
 */
class EnginePurityTest {

	private static final List<String> ENGINE_PACKAGES = List.of("policy", "chain");

	private static final List<String> FORBIDDEN = List.of("import org.springframework", "import jakarta.",
			"import java.sql", "import javax.");

	@Test
	void enginesImportNoFrameworkOrDatabaseCode() throws IOException {
		List<String> violations = new ArrayList<>();
		for (String pkg : ENGINE_PACKAGES) {
			Path dir = Path.of("src/main/java/com/hackathon/backend", pkg);
			if (!Files.isDirectory(dir)) {
				continue;
			}
			try (Stream<Path> files = Files.walk(dir)) {
				for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
					for (String line : Files.readAllLines(file)) {
						if (FORBIDDEN.stream().anyMatch(line.trim()::startsWith)) {
							violations.add(file.getFileName() + ": " + line.trim());
						}
					}
				}
			}
		}
		assertThat(violations).as("framework imports in engine packages").isEmpty();
	}

}
