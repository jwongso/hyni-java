package io.hyni.spring.boot.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

@ConfigurationProperties(prefix = "hyni")
public class HyniProperties {

    /**
     * Enable Hyni auto-configuration
     */
    private boolean enabled = true;

    /**
     * Default provider to use if not specified
     */
    private String defaultProvider = "openai";

    /**
     * Schema directory path
     */
    private String schemaDirectory = "schemas";

    /**
     * Enable request/response logging
     */
    private boolean loggingEnabled = false;

    /**
     * Enable metrics collection
     */
    private boolean metricsEnabled = true;

    /**
     * Enable validation
     */
    private boolean validationEnabled = true;

    /**
     * Default configuration values
     */
    private DefaultConfig defaults = new DefaultConfig();

    /**
     * Provider-specific configurations
     */
    private Map<String, ProviderConfig> providers = new HashMap<>();

    /**
     * Cache configuration
     */
    private CacheConfig cache = new CacheConfig();

    // Getters and setters
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getDefaultProvider() {
        return defaultProvider;
    }

    public void setDefaultProvider(String defaultProvider) {
        this.defaultProvider = defaultProvider;
    }

    public String getSchemaDirectory() {
        return schemaDirectory;
    }

    public void setSchemaDirectory(String schemaDirectory) {
        this.schemaDirectory = schemaDirectory;
    }

    public boolean isLoggingEnabled() {
        return loggingEnabled;
    }

    public void setLoggingEnabled(boolean loggingEnabled) {
        this.loggingEnabled = loggingEnabled;
    }

    public boolean isMetricsEnabled() {
        return metricsEnabled;
    }

    public void setMetricsEnabled(boolean metricsEnabled) {
        this.metricsEnabled = metricsEnabled;
    }

    public boolean isValidationEnabled() {
        return validationEnabled;
    }

    public void setValidationEnabled(boolean validationEnabled) {
        this.validationEnabled = validationEnabled;
    }

    public DefaultConfig getDefaults() {
        return defaults;
    }

    public void setDefaults(DefaultConfig defaults) {
        this.defaults = defaults;
    }

    public Map<String, ProviderConfig> getProviders() {
        return providers;
    }

    public void setProviders(Map<String, ProviderConfig> providers) {
        this.providers = providers;
    }

    public CacheConfig getCache() {
        return cache;
    }

    public void setCache(CacheConfig cache) {
        this.cache = cache;
    }

    public static class DefaultConfig {
        private Integer maxTokens;
        private Double temperature;
        private Double topP;
        private Integer timeout = 30000; // 30 seconds default

        // Getters and setters
        public Integer getMaxTokens() {
            return maxTokens;
        }

        public void setMaxTokens(Integer maxTokens) {
            this.maxTokens = maxTokens;
        }

        public Double getTemperature() {
            return temperature;
        }

        public void setTemperature(Double temperature) {
            this.temperature = temperature;
        }

        public Double getTopP() {
            return topP;
        }

        public void setTopP(Double topP) {
            this.topP = topP;
        }

        public Integer getTimeout() {
            return timeout;
        }

        public void setTimeout(Integer timeout) {
            this.timeout = timeout;
        }
    }

    public static class ProviderConfig {
        private String apiKey;
        private String apiKeyEnvVar;
        private String endpoint;
        private String model;
        private Map<String, Object> parameters = new HashMap<>();

        // Getters and setters
        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getApiKeyEnvVar() {
            return apiKeyEnvVar;
        }

        public void setApiKeyEnvVar(String apiKeyEnvVar) {
            this.apiKeyEnvVar = apiKeyEnvVar;
        }

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public Map<String, Object> getParameters() {
            return parameters;
        }

        public void setParameters(Map<String, Object> parameters) {
            this.parameters = parameters;
        }
    }

    public static class CacheConfig {
        private boolean enabled = false;
        private int maxSize = 1000;
        private int ttlMinutes = 60;

        // Getters and setters
        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getMaxSize() {
            return maxSize;
        }

        public void setMaxSize(int maxSize) {
            this.maxSize = maxSize;
        }

        public int getTtlMinutes() {
            return ttlMinutes;
        }

        public void setTtlMinutes(int ttlMinutes) {
            this.ttlMinutes = ttlMinutes;
        }
    }
}
