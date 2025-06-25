package io.hyni.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Immutable configuration for schema paths
 * Thread-safe after construction. Create once and share across threads.
 */
public class SchemaRegistry {

    private final Path schemaDirectory;
    private final Map<String, Path> providerPaths;

    private SchemaRegistry(Builder builder) {
        this.schemaDirectory = builder.schemaDirectory;
        this.providerPaths = Map.copyOf(builder.providerPaths);
    }

    public static Builder create() {
        return new Builder();
    }

    public static class Builder {
        private Path schemaDirectory = Paths.get("./schemas");
        private Map<String, Path> providerPaths = new HashMap<>();

        public Builder setSchemaDirectory(String directory) {
            if (directory == null || directory.trim().isEmpty()) {
                throw new IllegalArgumentException("Schema directory cannot be null or empty");
            }
            this.schemaDirectory = Paths.get(directory);
            return this;
        }

        public Builder registerSchema(String providerName, String schemaPath) {
            if (providerName == null || providerName.trim().isEmpty()) {
                throw new IllegalArgumentException("Provider name cannot be null or empty");
            }
            if (schemaPath == null || schemaPath.trim().isEmpty()) {
                throw new IllegalArgumentException("Schema path cannot be null or empty");
            }
            this.providerPaths.put(providerName, Paths.get(schemaPath));
            return this;
        }

        public Builder registerSchemas(Map<String, String> schemas) {
            if (schemas == null) {
                throw new IllegalArgumentException("Schemas map cannot be null");
            }
            for (Map.Entry<String, String> entry : schemas.entrySet()) {
                registerSchema(entry.getKey(), entry.getValue());
            }
            return this;
        }

        public SchemaRegistry build() {
            return new SchemaRegistry(this);
        }
    }

    public Path resolveSchemaPath(String providerName) {
        if (providerName == null || providerName.trim().isEmpty()) {
            throw new IllegalArgumentException("Provider name cannot be null or empty");
        }

        Path path = providerPaths.get(providerName);
        if (path != null) {
            return path.toAbsolutePath();
        }

        return schemaDirectory.resolve(providerName + ".json").toAbsolutePath();
    }

    public List<String> getAvailableProviders() {
        Set<String> providers = new HashSet<>();

        // From registered paths
        for (Map.Entry<String, Path> entry : providerPaths.entrySet()) {
            if (Files.exists(entry.getValue())) {
                providers.add(entry.getKey());
            }
        }

        // From schema directory
        if (Files.exists(schemaDirectory) && Files.isDirectory(schemaDirectory)) {
            try {
                providers.addAll(
                    Files.list(schemaDirectory)
                        .filter(Files::isRegularFile)
                        .filter(path -> path.toString().endsWith(".json"))
                        .map(path -> path.getFileName().toString())
                        .map(name -> name.substring(0, name.lastIndexOf('.')))
                        .collect(Collectors.toSet())
                );
            } catch (IOException e) {
                // Log warning but don't fail - directory might be inaccessible
                System.err.println("Warning: Could not list schema directory: " + e.getMessage());
            }
        }

        return new ArrayList<>(providers);
    }

    public boolean isProviderAvailable(String providerName) {
        if (providerName == null || providerName.trim().isEmpty()) {
            return false;
        }
        return Files.exists(resolveSchemaPath(providerName));
    }

    // Getters for testing
    public Path getSchemaDirectory() {
        return schemaDirectory;
    }

    public Map<String, Path> getProviderPaths() {
        return Collections.unmodifiableMap(providerPaths);
    }
}
