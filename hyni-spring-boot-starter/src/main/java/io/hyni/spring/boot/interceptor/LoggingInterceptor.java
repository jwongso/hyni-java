package io.hyni.spring.boot.interceptor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Interceptor for logging Hyni requests and responses
 */
@Component
public class LoggingInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(LoggingInterceptor.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Log request details
     */
    public void logRequest(String provider, JsonNode request) {
        if (logger.isDebugEnabled()) {
            try {
                String requestJson = objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(request);
                logger.debug("Hyni Request to provider [{}]:\n{}", provider, requestJson);
            } catch (Exception e) {
                logger.error("Failed to log request", e);
            }
        }
    }

    /**
     * Log response details
     */
    public void logResponse(String provider, JsonNode response, long duration) {
        if (logger.isDebugEnabled()) {
            try {
                // Mask sensitive data if needed
                JsonNode maskedResponse = maskSensitiveData(response);
                String responseJson = objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(maskedResponse);
                logger.debug("Hyni Response from provider [{}] ({}ms):\n{}",
                    provider, duration, responseJson);
            } catch (Exception e) {
                logger.error("Failed to log response", e);
            }
        }
    }

    /**
     * Log error details
     */
    public void logError(String provider, String error, Exception exception) {
        logger.error("Hyni Error from provider [{}]: {}", provider, error, exception);
    }

    private JsonNode maskSensitiveData(JsonNode node) {
        // In a real implementation, you might want to mask API keys, personal info, etc.
        // For now, just return the node as-is
        return node;
    }
}
