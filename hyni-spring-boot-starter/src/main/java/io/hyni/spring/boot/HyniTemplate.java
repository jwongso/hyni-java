package io.hyni.spring.boot;

import com.fasterxml.jackson.databind.JsonNode;
import io.hyni.core.ContextFactory;
import io.hyni.core.GeneralContext;
import io.hyni.spring.boot.autoconfigure.HyniProperties;
import io.hyni.spring.boot.exception.HyniException;
import io.hyni.spring.boot.model.ChatRequest;
import io.hyni.spring.boot.model.ChatResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Main template class for interacting with LLM providers in Spring applications
 */
public class HyniTemplate {

    private static final Logger logger = LoggerFactory.getLogger(HyniTemplate.class);

    private final ContextFactory contextFactory;
    private final HyniProperties properties;
    private final RestTemplate restTemplate;
    private final Map<String, String> providerApiKeys;

    public HyniTemplate(ContextFactory contextFactory, HyniProperties properties) {
        this.contextFactory = contextFactory;
        this.properties = properties;
        this.restTemplate = new RestTemplate();
        this.providerApiKeys = new HashMap<>();
    }

    /**
     * Configure API key for a provider
     */
    public void configureProvider(String provider, String apiKey) {
        providerApiKeys.put(provider, apiKey);
    }

    /**
     * Send a simple chat message using the default provider
     */
    public ChatResponse chat(String message) {
        return chat(properties.getDefaultProvider(), message);
    }

    /**
     * Send a simple chat message to a specific provider
     */
    public ChatResponse chat(String provider, String message) {
        ChatRequest request = ChatRequest.builder()
            .addMessage("user", message)
            .build();

        return chat(provider, request);
    }

    /**
     * Send a chat request to a specific provider
     */
    public ChatResponse chat(String provider, ChatRequest request) {
        try {
            GeneralContext context = createContext(provider);

            // Apply request configuration
            if (request.getModel() != null) {
                context.setModel(request.getModel());
            }

            if (request.getSystemMessage() != null) {
                context.setSystemMessage(request.getSystemMessage());
            }

            if (request.getParameters() != null) {
                request.getParameters().forEach(context::setParameter);
            }

            // Add messages
            request.getMessages().forEach(msg -> {
                if ("user".equals(msg.getRole())) {
                    context.addUserMessage(msg.getContent());
                } else if ("assistant".equals(msg.getRole())) {
                    context.addAssistantMessage(msg.getContent());
                }
            });

            // Build request
            JsonNode requestBody = context.buildRequest(request.isStream());

            // Make API call
            long startTime = System.currentTimeMillis();
            ResponseEntity<JsonNode> response = callApi(context, requestBody);
            long duration = System.currentTimeMillis() - startTime;

            // Extract response
            String responseText = context.extractTextResponse(response.getBody());

            // Get the actual model used
            String actualModel = extractModelFromResponse(response.getBody(), provider);
            if (actualModel == null) {
                actualModel = request.getModel() != null ? request.getModel() : getDefaultModel(provider);
            }

            return ChatResponse.builder()
                .provider(provider)
                .text(responseText)
                .model(actualModel)
                .duration(duration)
                .rawResponse(response.getBody())
                .build();

        } catch (Exception e) {
            logger.error("Error calling provider {}: {}", provider, e.getMessage(), e);
            throw new HyniException("Failed to call provider: " + provider, e);
        }
    }

    /**
     * Send a chat request asynchronously
     */
    public CompletableFuture<ChatResponse> chatAsync(String provider, ChatRequest request) {
        return CompletableFuture.supplyAsync(() -> chat(provider, request));
    }

    /**
     * Get a configured context for manual use
     */
    public GeneralContext getContext(String provider) {
        return createContext(provider);
    }

    /**
     * Get available providers
     */
    public List<String> getAvailableProviders() {
        return contextFactory.getAvailableProviders();
    }

    /**
     * Check if a provider is available
     */
    public boolean isProviderAvailable(String provider) {
        return contextFactory.isProviderAvailable(provider);
    }

    private GeneralContext createContext(String provider) {
        GeneralContext context = contextFactory.createContext(provider);

        // Set API key
        String apiKey = providerApiKeys.get(provider);
        if (apiKey == null) {
            // Try to get from properties
            HyniProperties.ProviderConfig providerConfig = properties.getProviders().get(provider);
            if (providerConfig != null) {
                apiKey = resolveApiKey(providerConfig);
            }
        }

        if (apiKey == null) {
            throw new HyniException("No API key configured for provider: " + provider);
        }

        context.setApiKey(apiKey);

        // Apply provider-specific configuration
        HyniProperties.ProviderConfig providerConfig = properties.getProviders().get(provider);
        if (providerConfig != null) {
            if (providerConfig.getModel() != null) {
                context.setModel(providerConfig.getModel());
            }

            if (providerConfig.getParameters() != null) {
                providerConfig.getParameters().forEach(context::setParameter);
            }
        }

        return context;
    }

    private ResponseEntity<JsonNode> callApi(GeneralContext context, JsonNode requestBody) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // Add provider headers
        context.getHeaders().forEach(headers::add);

        HttpEntity<JsonNode> entity = new HttpEntity<>(requestBody, headers);

        return restTemplate.exchange(
            context.getEndpoint(),
            HttpMethod.POST,
            entity,
            JsonNode.class
        );
    }

    private String resolveApiKey(HyniProperties.ProviderConfig config) {
        if (config.getApiKey() != null && !config.getApiKey().isEmpty()) {
            return config.getApiKey();
        }

        if (config.getApiKeyEnvVar() != null && !config.getApiKeyEnvVar().isEmpty()) {
            return System.getenv(config.getApiKeyEnvVar());
        }

        return null;
    }

    private String extractModelFromResponse(JsonNode response, String provider) {
        // Try to extract model from response based on provider
        if (response != null && response.has("model")) {
            return response.get("model").asText();
        }
        return null;
    }

    private String getDefaultModel(String provider) {
        HyniProperties.ProviderConfig config = properties.getProviders().get(provider);
        if (config != null && config.getModel() != null) {
            return config.getModel();
        }

        // Try to get from context's supported models
        try {
            GeneralContext tempContext = contextFactory.createContext(provider);
            List<String> models = tempContext.getSupportedModels();
            if (!models.isEmpty()) {
                return models.get(0);
            }
        } catch (Exception e) {
            logger.debug("Could not get default model for provider: {}", provider);
        }

        return "unknown";
    }
}
