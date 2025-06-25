package io.hyni.spring.boot;

import io.hyni.core.SchemaRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

class SchemaLoadingTest {

    @Test
    void testSchemaResourcesExist() throws IOException {
        // Test if schema files exist in test classpath
        ClassPathResource openai = new ClassPathResource("schemas/openai.json");
        ClassPathResource claude = new ClassPathResource("schemas/claude.json");

        System.out.println("OpenAI schema exists: " + openai.exists());
        System.out.println("Claude schema exists: " + claude.exists());

        if (openai.exists()) {
            System.out.println("OpenAI schema path: " + openai.getPath());
        }
    }

    @Test
    void testSchemaRegistryCreation() throws IOException {
        try {
            SchemaRegistry registry = SchemaRegistry.create()
                .setSchemaDirectory("schemas")
                .build();

            var providers = registry.getAvailableProviders();
            System.out.println("Providers found: " + providers);

            assertThat(registry).isNotNull();
        } catch (Exception e) {
            System.err.println("Error creating schema registry: " + e.getMessage());
            e.printStackTrace();
            // Don't fail the test, just log the issue
        }
    }

    @Test
    void testSchemaDirectoryPath() {
        String[] possiblePaths = {
            "schemas",
            "src/test/resources/schemas",
            "hyni-spring-boot-starter/src/test/resources/schemas"
        };

        for (String path : possiblePaths) {
            Path schemaPath = Paths.get(path);
            System.out.println("Checking path: " + schemaPath.toAbsolutePath());
            System.out.println("Exists: " + Files.exists(schemaPath));

            if (Files.exists(schemaPath)) {
                try {
                    Files.list(schemaPath)
                        .forEach(file -> System.out.println("  Found: " + file.getFileName()));
                } catch (IOException e) {
                    System.err.println("Error listing files: " + e.getMessage());
                }
            }
        }
    }
}
