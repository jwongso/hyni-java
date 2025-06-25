package io.hyni.core;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Configuration structure for additional context options
 */
public class ContextConfig {
    private boolean enableStreamingSupport = false;
    private boolean enableValidation = true;
    private boolean enableCaching = true;
    private Optional<Integer> defaultMaxTokens = Optional.empty();
    private Optional<Double> defaultTemperature = Optional.empty();
    private Map<String, JsonNode> customParameters = new HashMap<>();

    // Getters and setters
    public boolean isEnableStreamingSupport() {
        return enableStreamingSupport;
    }

    public void setEnableStreamingSupport(boolean enableStreamingSupport) {
        this.enableStreamingSupport = enableStreamingSupport;
    }

    public boolean isEnableValidation() {
        return enableValidation;
    }

    public void setEnableValidation(boolean enableValidation) {
        this.enableValidation = enableValidation;
    }

    public boolean isEnableCaching() {
        return enableCaching;
    }

    public void setEnableCaching(boolean enableCaching) {
        this.enableCaching = enableCaching;
    }

    public Optional<Integer> getDefaultMaxTokens() {
        return defaultMaxTokens;
    }

    public void setDefaultMaxTokens(Integer defaultMaxTokens) {
        this.defaultMaxTokens = Optional.ofNullable(defaultMaxTokens);
    }

    public Optional<Double> getDefaultTemperature() {
        return defaultTemperature;
    }

    public void setDefaultTemperature(Double defaultTemperature) {
        this.defaultTemperature = Optional.ofNullable(defaultTemperature);
    }

    public Map<String, JsonNode> getCustomParameters() {
        return customParameters;
    }

    public void setCustomParameters(Map<String, JsonNode> customParameters) {
        this.customParameters = customParameters;
    }
}
