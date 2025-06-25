package io.hyni.core;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

@DisplayName("SchemaRegistry Tests")
class SchemaRegistryTest {

    @TempDir
    Path tempDir;

    private Path createTestSchema(String name, String content) throws IOException {
        Path schemaFile = tempDir.resolve(name + ".json");
        Files.writeString(schemaFile, content);
        return schemaFile;
    }

    private String getBasicSchemaContent() {
        return """
            {
              "provider": {"name": "test"},
              "api": {"endpoint": "https://test.com"},
              "request_template": {},
              "message_format": {"structure": {}, "content_types": {}},
              "response_format": {"success": {"text_path": ["text"]}},
              "features": {}
            }
            """;
    }

    @Nested
    @DisplayName("Builder Tests")
    class BuilderTests {

        @Test
        @DisplayName("Should create registry with default schema directory")
        void shouldCreateWithDefaultDirectory() {
            SchemaRegistry registry = SchemaRegistry.create().build();

            assertThat(registry.getSchemaDirectory().toString()).contains("schemas");
        }

        @Test
        @DisplayName("Should set custom schema directory")
        void shouldSetCustomSchemaDirectory() {
            SchemaRegistry registry = SchemaRegistry.create()
                .setSchemaDirectory(tempDir.toString())
                .build();

            assertThat(registry.getSchemaDirectory()).isEqualTo(tempDir);
        }

        @Test
        @DisplayName("Should register individual schema")
        void shouldRegisterIndividualSchema() throws IOException {
            Path schemaFile = createTestSchema("custom-provider", getBasicSchemaContent());

            SchemaRegistry registry = SchemaRegistry.create()
                .registerSchema("custom-provider", schemaFile.toString())
                .build();

            assertThat(registry.getProviderPaths()).containsKey("custom-provider");
            assertThat(registry.isProviderAvailable("custom-provider")).isTrue();
        }

        @Test
        @DisplayName("Should register multiple schemas")
        void shouldRegisterMultipleSchemas() throws IOException {
            Path schema1 = createTestSchema("provider1", getBasicSchemaContent());
            Path schema2 = createTestSchema("provider2", getBasicSchemaContent());

            Map<String, String> schemas = Map.of(
                "provider1", schema1.toString(),
                "provider2", schema2.toString()
            );

            SchemaRegistry registry = SchemaRegistry.create()
                .registerSchemas(schemas)
                .build();

            assertThat(registry.getProviderPaths()).hasSize(2);
            assertThat(registry.isProviderAvailable("provider1")).isTrue();
            assertThat(registry.isProviderAvailable("provider2")).isTrue();
        }

        @Test
        @DisplayName("Should throw exception for null/empty provider name")
        void shouldThrowForInvalidProviderName() {
            SchemaRegistry.Builder builder = SchemaRegistry.create();

            assertThatThrownBy(() -> builder.registerSchema(null, "path"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Provider name cannot be null or empty");

            assertThatThrownBy(() -> builder.registerSchema("", "path"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Provider name cannot be null or empty");

            assertThatThrownBy(() -> builder.registerSchema("  ", "path"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Provider name cannot be null or empty");
        }

        @Test
        @DisplayName("Should throw exception for null/empty schema path")
        void shouldThrowForInvalidSchemaPath() {
            SchemaRegistry.Builder builder = SchemaRegistry.create();

            assertThatThrownBy(() -> builder.registerSchema("provider", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Schema path cannot be null or empty");

            assertThatThrownBy(() -> builder.registerSchema("provider", ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Schema path cannot be null or empty");
        }

        @Test
        @DisplayName("Should throw exception for null schemas map")
        void shouldThrowForNullSchemasMap() {
            SchemaRegistry.Builder builder = SchemaRegistry.create();

            assertThatThrownBy(() -> builder.registerSchemas(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Schemas map cannot be null");
        }
    }

    @Nested
    @DisplayName("Path Resolution Tests")
    class PathResolutionTests {

        @Test
        @DisplayName("Should resolve registered schema path")
        void shouldResolveRegisteredSchemaPath() throws IOException {
            Path schemaFile = createTestSchema("registered", getBasicSchemaContent());

            SchemaRegistry registry = SchemaRegistry.create()
                .registerSchema("registered", schemaFile.toString())
                .build();

            Path resolvedPath = registry.resolveSchemaPath("registered");
            assertThat(resolvedPath).isEqualTo(schemaFile.toAbsolutePath());
        }

        @Test
        @DisplayName("Should resolve schema from directory")
        void shouldResolveSchemaFromDirectory() {
            SchemaRegistry registry = SchemaRegistry.create()
                .setSchemaDirectory(tempDir.toString())
                .build();

            Path resolvedPath = registry.resolveSchemaPath("claude");
            Path expectedPath = tempDir.resolve("claude.json").toAbsolutePath();

            assertThat(resolvedPath).isEqualTo(expectedPath);
        }

        @Test
        @DisplayName("Should prefer registered path over directory")
        void shouldPreferRegisteredPathOverDirectory() throws IOException {
            // Create schema in directory
            createTestSchema("provider", getBasicSchemaContent());

            // Register different path for same provider
            Path customSchema = createTestSchema("custom", getBasicSchemaContent());

            SchemaRegistry registry = SchemaRegistry.create()
                .setSchemaDirectory(tempDir.toString())
                .registerSchema("provider", customSchema.toString())
                .build();

            Path resolvedPath = registry.resolveSchemaPath("provider");
            assertThat(resolvedPath).isEqualTo(customSchema.toAbsolutePath());
        }

        @Test
        @DisplayName("Should throw exception for null/empty provider name")
        void shouldThrowForInvalidProviderNameInResolve() {
            SchemaRegistry registry = SchemaRegistry.create().build();

            assertThatThrownBy(() -> registry.resolveSchemaPath(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Provider name cannot be null or empty");

            assertThatThrownBy(() -> registry.resolveSchemaPath(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Provider name cannot be null or empty");
        }
    }

    @Nested
    @DisplayName("Provider Discovery Tests")
    class ProviderDiscoveryTests {

        @Test
        @DisplayName("Should discover providers from directory")
        void shouldDiscoverProvidersFromDirectory() throws IOException {
            createTestSchema("provider1", getBasicSchemaContent());
            createTestSchema("provider2", getBasicSchemaContent());

            SchemaRegistry registry = SchemaRegistry.create()
                .setSchemaDirectory(tempDir.toString())
                .build();

            List<String> providers = registry.getAvailableProviders();

            assertThat(providers).containsExactlyInAnyOrder("provider1", "provider2");
        }

        @Test
        @DisplayName("Should discover registered providers")
        void shouldDiscoverRegisteredProviders() throws IOException {
            Path schema1 = createTestSchema("registered1", getBasicSchemaContent());
            Path schema2 = createTestSchema("registered2", getBasicSchemaContent());

            SchemaRegistry registry = SchemaRegistry.create()
                .registerSchema("registered1", schema1.toString())
                .registerSchema("registered2", schema2.toString())
                .build();

            List<String> providers = registry.getAvailableProviders();

            assertThat(providers).containsExactlyInAnyOrder("registered1", "registered2");
        }

        @Test
        @DisplayName("Should combine directory and registered providers")
        void shouldCombineDirectoryAndRegisteredProviders() throws IOException {
            // Create schemas in directory
            createTestSchema("dir-provider", getBasicSchemaContent());

            // Register additional schema
            Path registeredSchema = createTestSchema("registered-provider", getBasicSchemaContent());

            SchemaRegistry registry = SchemaRegistry.create()
                .setSchemaDirectory(tempDir.toString())
                .registerSchema("registered-provider", registeredSchema.toString())
                .build();

            List<String> providers = registry.getAvailableProviders();

            assertThat(providers).containsExactlyInAnyOrder("dir-provider", "registered-provider");
        }

        @Test
        @DisplayName("Should ignore non-JSON files in directory")
        void shouldIgnoreNonJsonFiles() throws IOException {
            createTestSchema("valid-provider", getBasicSchemaContent());

            // Create non-JSON files
            Files.writeString(tempDir.resolve("readme.txt"), "Not a schema");
            Files.writeString(tempDir.resolve("config.yaml"), "Not a JSON schema");

            SchemaRegistry registry = SchemaRegistry.create()
                .setSchemaDirectory(tempDir.toString())
                .build();

            List<String> providers = registry.getAvailableProviders();

            assertThat(providers).containsExactly("valid-provider");
        }

        @Test
        @DisplayName("Should handle non-existent directory gracefully")
        void shouldHandleNonExistentDirectoryGracefully() {
            Path nonExistentDir = tempDir.resolve("non-existent");

            SchemaRegistry registry = SchemaRegistry.create()
                .setSchemaDirectory(nonExistentDir.toString())
                .build();

            List<String> providers = registry.getAvailableProviders();

            assertThat(providers).isEmpty();
        }

        @Test
        @DisplayName("Should filter out non-existent registered schemas")
        void shouldFilterOutNonExistentRegisteredSchemas() {
            SchemaRegistry registry = SchemaRegistry.create()
                .registerSchema("non-existent", "/path/to/nowhere.json")
                .build();

            List<String> providers = registry.getAvailableProviders();

            assertThat(providers).isEmpty();
        }
    }

    @Nested
    @DisplayName("Provider Availability Tests")
    class ProviderAvailabilityTests {

        @Test
        @DisplayName("Should return true for available provider")
        void shouldReturnTrueForAvailableProvider() throws IOException {
            createTestSchema("available", getBasicSchemaContent());

            SchemaRegistry registry = SchemaRegistry.create()
                .setSchemaDirectory(tempDir.toString())
                .build();

            assertThat(registry.isProviderAvailable("available")).isTrue();
        }

        @Test
        @DisplayName("Should return false for unavailable provider")
        void shouldReturnFalseForUnavailableProvider() {
            SchemaRegistry registry = SchemaRegistry.create()
                .setSchemaDirectory(tempDir.toString())
                .build();

            assertThat(registry.isProviderAvailable("unavailable")).isFalse();
        }

        @Test
        @DisplayName("Should return false for null/empty provider name")
        void shouldReturnFalseForInvalidProviderName() {
            SchemaRegistry registry = SchemaRegistry.create().build();

            assertThat(registry.isProviderAvailable(null)).isFalse();
            assertThat(registry.isProviderAvailable("")).isFalse();
            assertThat(registry.isProviderAvailable("  ")).isFalse();
        }

        @Test
        @DisplayName("Should check registered provider availability")
        void shouldCheckRegisteredProviderAvailability() throws IOException {
            Path existingSchema = createTestSchema("existing", getBasicSchemaContent());

            SchemaRegistry registry = SchemaRegistry.create()
                .registerSchema("existing", existingSchema.toString())
                .registerSchema("missing", "/path/to/missing.json")
                .build();

            assertThat(registry.isProviderAvailable("existing")).isTrue();
            assertThat(registry.isProviderAvailable("missing")).isFalse();
        }
    }
}
