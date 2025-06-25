package io.hyni.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.hyni.core.exception.SchemaException;
import io.hyni.core.exception.ValidationException;
import io.hyni.core.util.Base64Utils;
import io.hyni.core.util.JsonPathResolver;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Main class for handling LLM context and API interactions
 *
 * This class manages the context for interacting with language model APIs,
 * including message handling, parameter configuration, and request/response processing.
 *
 * @note This class is NOT thread-safe. In multi-threaded environments, each thread
 *       should maintain its own instance. Consider using ThreadLocal storage:
 *       {@code ThreadLocal<GeneralContext> context = ThreadLocal.withInitial(() -> new GeneralContext("schema.json"));}
 */
public class GeneralContext {

    private final ObjectMapper objectMapper;
    private final JsonNode schema;
    private final ContextConfig config;

    // Schema elements
    private String providerName;
    private String endpoint;
    private Map<String, String> headers;
    private String modelName;
    private Optional<String> systemMessage;
    private List<JsonNode> messages;
    private Map<String, JsonNode> parameters;
    private String apiKey;
    private Set<String> validRoles;

    // Cached schema paths
    private List<String> textPath;
    private List<String> errorPath;
    private JsonNode messageStructure;
    private JsonNode textContentFormat;
    private JsonNode imageContentFormat;
    private JsonNode requestTemplate;

    /**
     * Constructs a general context with the given schema path and configuration
     * @param schemaPath Path to the schema file
     * @param config Configuration options
     * @throws SchemaException If the schema is invalid or cannot be loaded
     */
    public GeneralContext(String schemaPath, ContextConfig config) {
        this.objectMapper = new ObjectMapper();
        this.config = config;
        this.schema = loadSchema(schemaPath);
        initialize();
    }

    /**
     * Constructs a general context with the given pre-loaded schema and configuration
     * @param schema the pre-loaded JSON schema
     * @param config Configuration options
     * @throws SchemaException If the schema is invalid
     */
    public GeneralContext(JsonNode schema, ContextConfig config) {
        this.objectMapper = new ObjectMapper();
        this.config = config;
        this.schema = schema;
        initialize();
    }

    /**
     * Constructs a general context with default configuration
     * @param schemaPath Path to the schema file
     * @throws SchemaException If the schema is invalid or cannot be loaded
     */
    public GeneralContext(String schemaPath) {
        this(schemaPath, new ContextConfig());
    }

    private void initialize() {
        this.headers = new HashMap<>();
        this.messages = new ArrayList<>();
        this.parameters = new HashMap<>();
        this.validRoles = new HashSet<>();
        this.systemMessage = Optional.empty();
        this.apiKey = "";

        validateSchema();
        cacheSchemaElements();
        applyDefaults();
        buildHeaders();
    }

    private JsonNode loadSchema(String schemaPath) {
        try {
            Path path = Paths.get(schemaPath);

            if (Files.exists(path)) {
                // Load from file system
                return objectMapper.readTree(Files.newInputStream(path));
            } else {
                // Try loading from classpath
                InputStream resourceStream = getClass().getClassLoader()
                    .getResourceAsStream(schemaPath);

                if (resourceStream == null) {
                    // Try with leading slash
                    resourceStream = getClass().getClassLoader()
                        .getResourceAsStream("/" + schemaPath);
                }

                if (resourceStream == null) {
                    throw new SchemaException("Failed to open schema file: " + schemaPath);
                }

                return objectMapper.readTree(resourceStream);
            }
        } catch (IOException e) {
            throw new SchemaException("Failed to parse schema JSON: " + e.getMessage(), e);
        }
    }

    private void validateSchema() {
        // Check required top-level fields
        List<String> requiredFields = Arrays.asList(
            "provider", "api", "request_template", "message_format", "response_format"
        );

        for (String field : requiredFields) {
            if (!schema.has(field)) {
                throw new SchemaException("Missing required schema field: " + field);
            }
        }

        // Validate API configuration
        if (!schema.get("api").has("endpoint")) {
            throw new SchemaException("Missing API endpoint in schema");
        }

        // Validate message format
        JsonNode messageFormat = schema.get("message_format");
        if (!messageFormat.has("structure") || !messageFormat.has("content_types")) {
            throw new SchemaException("Invalid message format in schema");
        }

        // Validate response format
        JsonNode responseFormat = schema.get("response_format");
        if (!responseFormat.has("success") ||
            !responseFormat.get("success").has("text_path")) {
            throw new SchemaException("Invalid response format in schema");
        }
    }

    private void cacheSchemaElements() {
        // Cache provider info
        providerName = schema.get("provider").get("name").asText();
        endpoint = schema.get("api").get("endpoint").asText();

        // Cache valid roles
        if (schema.has("message_roles")) {
            for (JsonNode role : schema.get("message_roles")) {
                validRoles.add(role.asText());
            }
        }

        // Cache request template
        requestTemplate = schema.get("request_template").deepCopy();

        // Cache response paths
        textPath = JsonPathResolver.parseJsonPath(
            schema.get("response_format").get("success").get("text_path")
        );

        if (schema.get("response_format").has("error") &&
            schema.get("response_format").get("error").has("error_path")) {
            errorPath = JsonPathResolver.parseJsonPath(
                schema.get("response_format").get("error").get("error_path")
            );
        } else {
            errorPath = new ArrayList<>();
        }

        // Cache message formats
        messageStructure = schema.get("message_format").get("structure").deepCopy();

        JsonNode contentTypes = schema.get("message_format").get("content_types");
        if (contentTypes.has("text")) {
            textContentFormat = contentTypes.get("text").deepCopy();
        }
        if (contentTypes.has("image")) {
            imageContentFormat = contentTypes.get("image").deepCopy();
        }
    }

    private void buildHeaders() {
        headers.clear();

        // Process required headers
        if (schema.has("headers") && schema.get("headers").has("required")) {
            JsonNode requiredHeaders = schema.get("headers").get("required");
            Iterator<Map.Entry<String, JsonNode>> fields = requiredHeaders.fields();

            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                String key = entry.getKey();
                String value = entry.getValue().asText();

                // Replace API key placeholder if needed
                if (schema.has("authentication") &&
                    schema.get("authentication").has("key_placeholder")) {

                    String placeholder = schema.get("authentication")
                        .get("key_placeholder").asText();
                    value = value.replace(placeholder, apiKey);
                }

                headers.put(key, value);
            }
        }

        // Process optional headers
        if (schema.has("headers") && schema.get("headers").has("optional")) {
            JsonNode optionalHeaders = schema.get("headers").get("optional");
            Iterator<Map.Entry<String, JsonNode>> fields = optionalHeaders.fields();

            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                if (!entry.getValue().isNull() && entry.getValue().isTextual()) {
                    String value = entry.getValue().asText();
                    if (!value.isEmpty()) {
                        headers.put(entry.getKey(), value);
                    }
                }
            }
        }
    }

    private void applyDefaults() {
        if (schema.has("models") && schema.get("models").has("default")) {
            modelName = schema.get("models").get("default").asText();
        }
    }

    /**
     * Sets the model to use for requests
     * @param model The model name
     * @return Reference to this context for method chaining
     * @throws ValidationException If the model is not supported and validation is enabled
     */
    public GeneralContext setModel(String model) {
        // Validate model if available models are specified
        if (schema.has("models") && schema.get("models").has("available")) {
            JsonNode availableModels = schema.get("models").get("available");
            boolean found = false;

            for (JsonNode availableModel : availableModels) {
                if (availableModel.asText().equals(model)) {
                    found = true;
                    break;
                }
            }

            if (!found && config.isEnableValidation()) {
                throw new ValidationException("Model '" + model +
                    "' is not supported by this provider");
            }
        }

        this.modelName = model;
        return this;
    }

    /**
     * Sets the system message for the conversation
     * @param systemText The system message text
     * @return Reference to this context for method chaining
     * @throws ValidationException If system messages are not supported and validation is enabled
     */
    public GeneralContext setSystemMessage(String systemText) {
        if (!supportsSystemMessages() && config.isEnableValidation()) {
            throw new ValidationException("Provider '" + providerName +
                "' does not support system messages");
        }
        this.systemMessage = Optional.of(systemText);
        return this;
    }

    /**
     * Sets a single parameter for the request
     * @param key The parameter key
     * @param value The parameter value
     * @return Reference to this context for method chaining
     * @throws ValidationException If the parameter is invalid and validation is enabled
     */
    public GeneralContext setParameter(String key, Object value) {
        JsonNode jsonValue = objectMapper.valueToTree(value);

        if (config.isEnableValidation()) {
            validateParameter(key, jsonValue);
        }

        parameters.put(key, jsonValue);
        return this;
    }

    /**
     * Sets multiple parameters for the request
     * @param params Map of parameter keys and values
     * @return Reference to this context for method chaining
     * @throws ValidationException If any parameter is invalid and validation is enabled
     */
    public GeneralContext setParameters(Map<String, Object> params) {
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            setParameter(entry.getKey(), entry.getValue());
        }
        return this;
    }

    /**
     * Sets the API key for authentication
     * @param apiKey The API key
     * @return Reference to this context for method chaining
     * @throws ValidationException If the API key is empty
     */
    public GeneralContext setApiKey(String apiKey) {
        if (apiKey == null || apiKey.isEmpty()) {
            throw new ValidationException("API key cannot be empty");
        }
        this.apiKey = apiKey;
        buildHeaders(); // Rebuild headers with new API key
        return this;
    }

    /**
     * Adds a user message to the conversation
     * @param content The message content
     * @return Reference to this context for method chaining
     */
    public GeneralContext addUserMessage(String content) {
        return addMessage("user", content, null, null);
    }

    /**
     * Adds a user message with media to the conversation
     * @param content The message content
     * @param mediaType Media type for multimodal content
     * @param mediaData Media data for multimodal content
     * @return Reference to this context for method chaining
     * @throws ValidationException If the message is invalid and validation is enabled
     */
    public GeneralContext addUserMessage(String content, String mediaType, String mediaData) {
        return addMessage("user", content, mediaType, mediaData);
    }

    /**
     * Adds an assistant message to the conversation
     * @param content The message content
     * @return Reference to this context for method chaining
     * @throws ValidationException If the message is invalid and validation is enabled
     */
    public GeneralContext addAssistantMessage(String content) {
        return addMessage("assistant", content, null, null);
    }

    /**
     * Adds a message with the specified role to the conversation
     * @param role The message role (e.g., "user", "assistant", "system")
     * @param content The message content
     * @param mediaType Optional media type for multimodal content
     * @param mediaData Optional media data for multimodal content
     * @return Reference to this context for method chaining
     * @throws ValidationException If the message is invalid and validation is enabled
     */
    public GeneralContext addMessage(String role, String content,
                                    String mediaType, String mediaData) {
        JsonNode message = createMessage(role, content, mediaType, mediaData);

        if (config.isEnableValidation()) {
            validateMessage(message);
        }

        messages.add(message);
        return this;
    }

    private JsonNode createMessage(String role, String content,
                                  String mediaType, String mediaData) {
        ObjectNode message = messageStructure.deepCopy();
        message.put("role", role);

        // Create content array
        ArrayNode contentArray = objectMapper.createArrayNode();
        contentArray.add(createTextContent(content));

        // Add image if provided
        if (mediaType != null && mediaData != null) {
            if (!supportsMultimodal() && config.isEnableValidation()) {
                throw new ValidationException("Provider '" + providerName +
                    "' does not support multimodal content");
            }
            contentArray.add(createImageContent(mediaType, mediaData));
        }

        message.set("content", contentArray);
        return message;
    }

    private JsonNode createTextContent(String text) {
        ObjectNode content = textContentFormat.deepCopy();
        content.put("text", text);
        return content;
    }

    private JsonNode createImageContent(String mediaType, String data) {
        ObjectNode content = imageContentFormat.deepCopy();

        try {
            // Handle both base64 data and file paths
            String base64Data;
            if (Base64Utils.isBase64Encoded(data)) {
                base64Data = data;
            } else {
                // Assume it's a file path and encode it
                base64Data = Base64Utils.encodeImageToBase64(data);
            }

            // Update the content based on the schema structure
            if (content.has("source")) {
                ObjectNode source = (ObjectNode) content.get("source");
                source.put("media_type", mediaType);
                source.put("data", base64Data);
            } else if (content.has("image_url")) {
                ObjectNode imageUrl = (ObjectNode) content.get("image_url");
                imageUrl.put("url", "data:" + mediaType + ";base64," + base64Data);
            }

        } catch (IOException e) {
            throw new RuntimeException("Failed to process image: " + e.getMessage(), e);
        }

        return content;
    }

    /**
     * Builds a request object based on the current context
     * @return JSON object representing the request
     */
    public JsonNode buildRequest() {
        return buildRequest(false);
    }

    /**
     * Builds a request object based on the current context
     * @param streaming Whether to enable streaming for this request
     * @return JSON object representing the request
     */
    public JsonNode buildRequest(boolean streaming) {
        ObjectNode request = requestTemplate.deepCopy();

        // Build messages array
        ArrayNode messagesArray = objectMapper.createArrayNode();
        for (JsonNode msg : messages) {
            messagesArray.add(msg);
        }

        // Set model
        if (modelName != null && !modelName.isEmpty()) {
            request.put("model", modelName);
        }

        // Set system message if supported
        if (systemMessage.isPresent() && supportsSystemMessages()) {
            boolean systemInRoles = validRoles.contains("system");

            if (systemInRoles) {
                // Insert system message at beginning
                ObjectNode sysMsg = objectMapper.createObjectNode();
                sysMsg.put("role", "system");
                sysMsg.put("content", systemMessage.get());
                messagesArray.insert(0, sysMsg);
            } else {
                // Claude style - use separate system field
                request.put("system", systemMessage.get());
            }
        }

        // Set messages
        request.set("messages", messagesArray);

        // Apply custom parameters FIRST (so they take precedence)
        for (Map.Entry<String, JsonNode> entry : parameters.entrySet()) {
            request.set(entry.getKey(), entry.getValue());
        }

        // Apply config defaults only if not already set
        if (config.getDefaultMaxTokens().isPresent() && !request.has("max_tokens")) {
            request.put("max_tokens", config.getDefaultMaxTokens().get());
        }
        if (config.getDefaultTemperature().isPresent() && !request.has("temperature")) {
            request.put("temperature", config.getDefaultTemperature().get());
        }

        // Set streaming: user parameter takes precedence over function parameter
        if (!parameters.containsKey("stream")) {
            // User hasn't explicitly set stream parameter, use function parameter
            if (streaming && schema.get("features").get("streaming").asBoolean()) {
                request.put("stream", true);
            } else {
                request.put("stream", false);
            }
        }

        removeNullsRecursive(request);

        return request;
    }

    private void removeNullsRecursive(JsonNode node) {
        if (node.isObject()) {
            ObjectNode objNode = (ObjectNode) node;
            Iterator<Map.Entry<String, JsonNode>> fields = objNode.fields();
            List<String> toRemove = new ArrayList<>();

            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                if (entry.getValue().isNull()) {
                    toRemove.add(entry.getKey());
                } else {
                    removeNullsRecursive(entry.getValue());
                }
            }

            for (String key : toRemove) {
                objNode.remove(key);
            }
        } else if (node.isArray()) {
            for (JsonNode item : node) {
                removeNullsRecursive(item);
            }
        }
    }

    /**
     * Extracts the text response from a JSON response
     * @param response The JSON response from the API
     * @return The extracted text
     * @throws RuntimeException If the text cannot be extracted
     */
    public String extractTextResponse(JsonNode response) {
        try {
            JsonNode textNode = JsonPathResolver.resolvePath(response, textPath);
            return textNode.asText();
        } catch (Exception e) {
            throw new RuntimeException("Failed to extract text response: " + e.getMessage(), e);
        }
    }

    /**
     * Extracts the full response content from a JSON response
     * @param response The JSON response from the API
     * @return The extracted content as JSON
     * @throws RuntimeException If the content cannot be extracted
     */
    public JsonNode extractFullResponse(JsonNode response) {
        try {
            List<String> contentPath = JsonPathResolver.parseJsonPath(
                schema.get("response_format").get("success").get("content_path")
            );
            return JsonPathResolver.resolvePath(response, contentPath);
        } catch (Exception e) {
            throw new RuntimeException("Failed to extract full response: " + e.getMessage(), e);
        }
    }

    /**
     * Extracts an error message from a JSON response
     * @param response The JSON response from the API
     * @return The extracted error message
     */
    public String extractError(JsonNode response) {
        if (errorPath.isEmpty()) {
            return "Unknown error";
        }

        try {
            JsonNode errorNode = JsonPathResolver.resolvePath(response, errorPath);
            return errorNode.asText();
        } catch (Exception e) {
            return "Failed to parse error message";
        }
    }

    /**
     * Resets the context to its initial state
     */
    public void reset() {
        clearUserMessages();
        clearSystemMessage();
        clearParameters();
        modelName = "";
        applyDefaults();
    }

    /**
     * Clears all messages in the context
     */
    public void clearUserMessages() {
        messages.clear();
    }

    /**
     * Clears system message in the context
     */
    public void clearSystemMessage() {
        systemMessage = Optional.empty();
    }

    /**
     * Clears all parameters in the context
     */
    public void clearParameters() {
        parameters.clear();
    }

    /**
     * Checks if an API key has been set
     * @return True if an API key is set, false otherwise
     */
    public boolean hasApiKey() {
        return !apiKey.isEmpty();
    }

    /**
     * Gets the schema used by this context
     * @return The schema as JSON
     */
    public JsonNode getSchema() {
        return schema;
    }

    /**
     * Gets the provider name
     * @return The provider name
     */
    public String getProviderName() {
        return providerName;
    }

    /**
     * Gets the API endpoint
     * @return The API endpoint URL
     */
    public String getEndpoint() {
        return endpoint;
    }

    /**
     * Gets the HTTP headers for API requests
     * @return Map of header names to values
     */
    public Map<String, String> getHeaders() {
        return Collections.unmodifiableMap(headers);
    }

    /**
     * Gets the list of models supported by the provider
     * @return List of supported model names
     */
    public List<String> getSupportedModels() {
        List<String> models = new ArrayList<>();
        if (schema.has("models") && schema.get("models").has("available")) {
            for (JsonNode model : schema.get("models").get("available")) {
                models.add(model.asText());
            }
        }
        return models;
    }

    /**
     * Checks if the provider supports multimodal content
     * @return True if multi modal content is supported, false otherwise
     */
    public boolean supportsMultimodal() {
        if (schema.has("multimodal") && schema.get("multimodal").has("supported")) {
            return schema.get("multimodal").get("supported").asBoolean();
        }
        return false;
    }

    /**
     * Checks if the provider supports streaming
     * @return True if streaming is supported, false otherwise
     */
    public boolean supportsStreaming() {
        if (schema.has("features") && schema.get("features").has("streaming")) {
            return schema.get("features").get("streaming").asBoolean();
        }
        return false;
    }

    /**
     * Checks if the provider supports system messages
     * @return True if system messages are supported, false otherwise
     */
    public boolean supportsSystemMessages() {
        if (schema.has("system_message") && schema.get("system_message").has("supported")) {
            return schema.get("system_message").get("supported").asBoolean();
        }
        return false;
    }

    /**
     * Checks if the current context would produce a valid request
     * @return True if the request would be valid, false otherwise
     */
    public boolean isValidRequest() {
        return getValidationErrors().isEmpty();
    }

    /**
     * Gets a list of validation errors for the current context
     * @return List of error messages, empty if valid
     */
    public List<String> getValidationErrors() {
        List<String> errors = new ArrayList<>();

        // Check required fields
        if (modelName == null || modelName.isEmpty()) {
            errors.add("Model name is required");
        }

        if (messages.isEmpty()) {
            errors.add("At least one message is required");
        }

        // Validate message roles
        if (schema.has("validation") && schema.get("validation").has("message_validation")) {
            JsonNode validation = schema.get("validation").get("message_validation");

            if (validation.has("last_message_role")) {
                String requiredRole = validation.get("last_message_role").asText();
                if (!messages.isEmpty()) {
                    String lastRole = messages.get(messages.size() - 1).get("role").asText();
                    if (!lastRole.equals(requiredRole)) {
                        errors.add("Last message must be from: " + requiredRole);
                    }
                }
            }
        }

        return errors;
    }

    /**
     * Gets all parameters in the context
     * @return Map of parameter names to values
     */
    public Map<String, JsonNode> getParameters() {
        return Collections.unmodifiableMap(parameters);
    }

    /**
     * Gets a parameter value by key
     * @param key The parameter key
     * @return The parameter value
     * @throws ValidationException If the parameter does not exist
     */
    public JsonNode getParameter(String key) {
        JsonNode value = parameters.get(key);
        if (value == null) {
            throw new ValidationException("Parameter '" + key + "' not found");
        }
        return value;
    }

    /**
     * Gets a parameter value converted to a specific type
     * @param key The parameter key
     * @param type The type to convert the parameter to
     * @return The parameter value converted to type T
     * @throws ValidationException If the parameter does not exist or cannot be converted
     */
    public <T> T getParameterAs(String key, Class<T> type) {
        JsonNode param = getParameter(key);
        try {
            return objectMapper.treeToValue(param, type);
        } catch (Exception e) {
            throw new ValidationException("Parameter '" + key +
                "' cannot be converted to requested type: " + e.getMessage());
        }
    }

    /**
     * Gets a parameter value converted to a specific type, with a default value
     * @param key The parameter key
     * @param type The type to convert the parameter to
     * @param defaultValue The default value to return if the parameter does not exist
     * @return The parameter value converted to type T, or the default value
     * @throws ValidationException If the parameter exists but cannot be converted
     */
    public <T> T getParameterAs(String key, Class<T> type, T defaultValue) {
        if (!hasParameter(key)) {
            return defaultValue;
        }
        return getParameterAs(key, type);
    }

    /**
     * Checks if a parameter exists
     * @param key The parameter key
     * @return True if the parameter exists, false otherwise
     */
    public boolean hasParameter(String key) {
        return parameters.containsKey(key);
    }

    /**
     * Gets all messages in the context
     * @return List of message objects
     */
    public List<JsonNode> getMessages() {
        return Collections.unmodifiableList(messages);
    }

    private void validateMessage(JsonNode message) {
        if (!message.has("role") || !message.has("content")) {
            throw new ValidationException("Message must contain 'role' and 'content' fields");
        }

        String role = message.get("role").asText();
        if (!validRoles.isEmpty() && !validRoles.contains(role)) {
            throw new ValidationException("Invalid message role: " + role);
        }
    }

    private void validateParameter(String key, JsonNode value) {
        if (value.isNull()) {
            throw new ValidationException("Parameter '" + key + "' cannot be null");
        }

        if (!schema.has("parameters") || !schema.get("parameters").has(key)) {
            return; // Parameter not defined in schema
        }

        JsonNode paramDef = schema.get("parameters").get(key);

        // String length validation
        if (value.isTextual() && paramDef.has("max_length")) {
            int maxLen = paramDef.get("max_length").asInt();
            if (value.asText().length() > maxLen) {
                throw new ValidationException("Parameter '" + key +
                    "' exceeds maximum length of " + maxLen);
            }
        }

        // Enum validation
        if (paramDef.has("enum")) {
            boolean found = false;
            for (JsonNode allowed : paramDef.get("enum")) {
                if (value.equals(allowed)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                throw new ValidationException("Parameter '" + key + "' has invalid value");
            }
        }

        // Type validation
        if (paramDef.has("type")) {
            String expectedType = paramDef.get("type").asText();
            validateType(key, value, expectedType);
        }

        // Range validation for numbers
        if (value.isNumber()) {
            if (paramDef.has("min")) {
                double minVal = paramDef.get("min").asDouble();
                if (value.asDouble() < minVal) {
                    throw new ValidationException("Parameter '" + key +
                        "' must be >= " + minVal);
                }
            }

            if (paramDef.has("max")) {
                double maxVal = paramDef.get("max").asDouble();
                if (value.asDouble() > maxVal) {
                    throw new ValidationException("Parameter '" + key +
                        "' must be <= " + maxVal);
                }
            }
        }
    }

    private void validateType(String key, JsonNode value, String expectedType) {
        boolean valid = switch (expectedType) {
            case "integer" -> value.isIntegralNumber();
            case "float", "number" -> value.isNumber();
            case "string" -> value.isTextual();
            case "boolean" -> value.isBoolean();
            case "array" -> value.isArray();
            case "object" -> value.isObject();
            default -> true;
        };

        if (!valid) {
            throw new ValidationException("Parameter '" + key +
                "' must be of type " + expectedType);
        }
    }
}
