package io.hyni.core;

import io.hyni.core.exception.SchemaException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Factory for creating GeneralContext instances with caching and thread-local support
 */
public class ContextFactory {

    private static final Logger logger = LoggerFactory.getLogger(ContextFactory.class);

    private final SchemaRegistry registry;
    private final ContextConfig defaultConfig;
    private final Map<String, CacheStats> cacheStats;
    private final ThreadLocal<Map<String, GeneralContext>> threadLocalContexts;

    /**
     * Create a new ContextFactory with the given schema registry
     * @param registry The schema registry to use
     */
    public ContextFactory(SchemaRegistry registry) {
        this(registry, new ContextConfig());
    }

    /**
     * Create a new ContextFactory with the given schema registry and default configuration
     * @param registry The schema registry to use
     * @param defaultConfig The default configuration to use for new contexts
     */
    public ContextFactory(SchemaRegistry registry, ContextConfig defaultConfig) {
        if (registry == null) {
            throw new NullPointerException("Registry cannot be null");
        }
        this.registry = registry;
        this.defaultConfig = defaultConfig;
        this.cacheStats = new ConcurrentHashMap<>();
        this.threadLocalContexts = ThreadLocal.withInitial(ConcurrentHashMap::new);
    }

    /**
     * Create a new GeneralContext for the specified provider
     * @param provider The provider name
     * @return A new GeneralContext instance
     * @throws SchemaException If the provider is not found
     */
    public GeneralContext createContext(String provider) {
        return createContext(provider, defaultConfig);
    }

    /**
     * Create a new GeneralContext for the specified provider with custom configuration
     * @param provider The provider name
     * @param config The configuration to use
     * @return A new GeneralContext instance
     * @throws SchemaException If the provider is not found
     */
    public GeneralContext createContext(String provider, ContextConfig config) {
        if (provider == null || provider.trim().isEmpty()) {
            throw new IllegalArgumentException("Provider name cannot be null or empty");
        }
        if (config == null) {
            throw new IllegalArgumentException("Config cannot be null");
        }

        try {
            // Get schema path from registry
            Path schemaPath = registry.resolveSchemaPath(provider);

            // Update cache stats
            CacheStats stats = cacheStats.computeIfAbsent(provider, k -> new CacheStats());

            // Check if schema is already cached (for this simple implementation, we always count as hit after first load)
            if (stats.getTotalRequests() > 0) {
                stats.incrementHitCount();
            } else {
                stats.incrementMissCount();
            }

            logger.debug("Creating context for provider: {} using schema: {}", provider, schemaPath);

            // Use the string path constructor of GeneralContext
            return new GeneralContext(schemaPath.toString(), config);

        } catch (Exception e) {
            logger.error("Failed to create context for provider: {}", provider, e);
            Path schemaPath = registry.resolveSchemaPath(provider);
            throw new SchemaException("Schema file not found for provider: " + provider + " at " + schemaPath, e);
        }
    }

    /**
     * Get a thread-local context for the specified provider
     * @param provider The provider name
     * @return A thread-local GeneralContext instance
     */
    public GeneralContext getThreadLocalContext(String provider) {
        Map<String, GeneralContext> contexts = threadLocalContexts.get();
        return contexts.computeIfAbsent(provider, p -> createContext(p));
    }

    /**
     * Clear thread-local contexts
     */
    public void clearThreadLocalContexts() {
        threadLocalContexts.get().clear();
    }

    /**
     * Clear the cache
     */
    public void clearCache() {
        cacheStats.clear();
    }

    /**
     * Get cache statistics
     * @return Cache statistics
     */
    public CacheStats getCacheStats() {
        long totalHits = cacheStats.values().stream()
            .mapToLong(CacheStats::getHitCount)
            .sum();
        long totalMisses = cacheStats.values().stream()
            .mapToLong(CacheStats::getMissCount)
            .sum();

        CacheStats combined = new CacheStats();
        combined.hitCount = totalHits;
        combined.missCount = totalMisses;
        combined.cacheSize = cacheStats.size();
        return combined;
    }

    /**
     * Get available providers from the registry
     * @return List of available provider names
     */
    public List<String> getAvailableProviders() {
        return registry.getAvailableProviders();
    }

    /**
     * Check if a provider is available
     * @param provider The provider name
     * @return true if the provider is available, false otherwise
     */
    public boolean isProviderAvailable(String provider) {
        return registry.isProviderAvailable(provider);
    }

    /**
     * Get the schema registry
     * @return The schema registry used by this factory
     */
    public SchemaRegistry getRegistry() {
        return registry;
    }

    /**
     * Cache statistics
     */
    public static class CacheStats {
        private long hitCount = 0;
        private long missCount = 0;
        private long cacheSize = 0;

        public long getHitCount() {
            return hitCount;
        }

        public long getMissCount() {
            return missCount;
        }

        public long getCacheSize() {
            return cacheSize;
        }

        public long getTotalRequests() {
            return hitCount + missCount;
        }

        public double getHitRate() {
            long total = getTotalRequests();
            return total == 0 ? 0.0 : (double) hitCount / total;
        }

        void incrementHitCount() {
            hitCount++;
        }

        void incrementMissCount() {
            missCount++;
        }

        void setCacheSize(long size) {
            this.cacheSize = size;
        }
    }
}
