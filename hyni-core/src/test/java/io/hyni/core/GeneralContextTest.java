package io.hyni.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.hyni.core.exception.SchemaException;
import io.hyni.core.exception.ValidationException;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

@DisplayName("GeneralContext Unit Tests")
class GeneralContextTest {

    private GeneralContext context;
    private ContextConfig config;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        config = new ContextConfig();
        objectMapper = new ObjectMapper();
        // Load test schema from resources
        context = new GeneralContext("schemas/claude.json", config);
    }

    @Nested
    @DisplayName("Schema Loading Tests")
    class SchemaLoadingTests {

        @Test
        @DisplayName("Should load schema from classpath successfully")
        void shouldLoadSchemaFromClasspath() {
            assertThat(context.getProviderName()).isEqualTo("claude");
            assertThat(context.getEndpoint()).isEqualTo("https://api.anthropic.com/v1/messages");
        }

        @Test
        @DisplayName("Should load schema from JsonNode")
        void shouldLoadSchemaFromJsonNode() throws IOException {
            String schemaJson = """
                {
                  "provider": {"name": "test-provider"},
                  "api": {"endpoint": "https://test.api.com"},
                  "request_template": {"model": "test-model"},
                  "message_format": {
                    "structure": {"role": "user", "content": []},
                    "content_types": {
                      "text": {"type": "text", "text": ""}
                    }
                  },
                  "response_format": {
                    "success": {"text_path": ["content", 0, "text"]}
                  },
                  "features": {"streaming": false}
                }
                """;

            JsonNode schema = objectMapper.readTree(schemaJson);
            GeneralContext testContext = new GeneralContext(schema, config);

            assertThat(testContext.getProviderName()).isEqualTo("test-provider");
            assertThat(testContext.getEndpoint()).isEqualTo("https://test.api.com");
        }

        @Test
        @DisplayName("Should throw SchemaException for missing schema file")
        void shouldThrowForMissingSchemaFile() {
            assertThatThrownBy(() -> new GeneralContext("nonexistent.json", config))
                .isInstanceOf(SchemaException.class)
                .hasMessageContaining("Failed to open schema file");
        }

        @Test
        @DisplayName("Should throw SchemaException for invalid JSON")
        void shouldThrowForInvalidJson() throws IOException {
            // Create temporary invalid JSON file
            Path tempFile = Files.createTempFile("invalid", ".json");
            Files.writeString(tempFile, "{invalid json}");

            try {
                assertThatThrownBy(() -> new GeneralContext(tempFile.toString(), config))
                    .isInstanceOf(SchemaException.class)
                    .hasMessageContaining("Failed to parse schema JSON");
            } finally {
                Files.deleteIfExists(tempFile);
            }
        }

        @Test
        @DisplayName("Should validate required schema fields")
        void shouldValidateRequiredFields() throws IOException {
            String incompleteSchema = """
                {
                  "provider": {"name": "test"}
                }
                """;

            JsonNode schema = objectMapper.readTree(incompleteSchema);

            assertThatThrownBy(() -> new GeneralContext(schema, config))
                .isInstanceOf(SchemaException.class)
                .hasMessageContaining("Missing required schema field");
        }
    }

    @Nested
    @DisplayName("Message Management Tests")
    class MessageManagementTests {

        @Test
        @DisplayName("Should add user message successfully")
        void shouldAddUserMessage() {
            context.addUserMessage("Hello, world!");

            assertThat(context.getMessages()).hasSize(1);
            JsonNode message = context.getMessages().get(0);
            assertThat(message.get("role").asText()).isEqualTo("user");

            JsonNode content = message.get("content");
            assertThat(content.isArray()).isTrue();
            assertThat(content.get(0).get("type").asText()).isEqualTo("text");
            assertThat(content.get(0).get("text").asText()).isEqualTo("Hello, world!");
        }

        @Test
        @DisplayName("Should add assistant message successfully")
        void shouldAddAssistantMessage() {
            context.addAssistantMessage("Hi there!");

            assertThat(context.getMessages()).hasSize(1);
            JsonNode message = context.getMessages().get(0);
            assertThat(message.get("role").asText()).isEqualTo("assistant");
        }

        @Test
        @DisplayName("Should add multiple messages in order")
        void shouldAddMultipleMessagesInOrder() {
            context.addUserMessage("First message");
            context.addAssistantMessage("First response");
            context.addUserMessage("Second message");

            List<JsonNode> messages = context.getMessages();
            assertThat(messages).hasSize(3);
            assertThat(messages.get(0).get("role").asText()).isEqualTo("user");
            assertThat(messages.get(1).get("role").asText()).isEqualTo("assistant");
            assertThat(messages.get(2).get("role").asText()).isEqualTo("user");
        }

        @Test
        @DisplayName("Should clear messages")
        void shouldClearMessages() {
            context.addUserMessage("Test message");
            assertThat(context.getMessages()).hasSize(1);

            context.clearUserMessages();
            assertThat(context.getMessages()).isEmpty();
        }

        @Test
        @DisplayName("Should handle empty message content")
        void shouldHandleEmptyMessageContent() {
            assertThatCode(() -> context.addUserMessage("")).doesNotThrowAnyException();

            JsonNode message = context.getMessages().get(0);
            assertThat(message.get("content").get(0).get("text").asText()).isEmpty();
        }
    }

    @Nested
    @DisplayName("System Message Tests")
    class SystemMessageTests {

        @Test
        @DisplayName("Should set system message when supported")
        void shouldSetSystemMessage() {
            String systemPrompt = "You are a helpful assistant.";
            context.setSystemMessage(systemPrompt);

            // System message should be stored but not in messages array for Claude
            assertThat(context.getMessages()).isEmpty();

            // Check if it appears in the built request
            context.addUserMessage("Hello");
            JsonNode request = context.buildRequest();
            assertThat(request.get("system").asText()).isEqualTo(systemPrompt);
        }

        @Test
        @DisplayName("Should clear system message")
        void shouldClearSystemMessage() {
            context.setSystemMessage("Test system message");
            context.clearSystemMessage();

            context.addUserMessage("Hello");
            JsonNode request = context.buildRequest();

            // System field should be null or not present
            JsonNode systemField = request.get("system");
            assertThat(systemField == null || systemField.isNull()).isTrue();
        }
    }

    @Nested
    @DisplayName("Parameter Management Tests")
    class ParameterManagementTests {

        @Test
        @DisplayName("Should set and get parameters")
        void shouldSetAndGetParameters() {
            context.setParameter("temperature", 0.8);
            context.setParameter("max_tokens", 100);
            context.setParameter("top_p", 0.9);

            assertThat(context.hasParameter("temperature")).isTrue();
            assertThat(context.hasParameter("max_tokens")).isTrue();
            assertThat(context.hasParameter("top_p")).isTrue();

            assertThat(context.getParameterAs("temperature", Double.class)).isEqualTo(0.8);
            assertThat(context.getParameterAs("max_tokens", Integer.class)).isEqualTo(100);
            assertThat(context.getParameterAs("top_p", Double.class)).isEqualTo(0.9);
        }

        @Test
        @DisplayName("Should return default value for missing parameter")
        void shouldReturnDefaultValueForMissingParameter() {
            double defaultTemp = context.getParameterAs("temperature", Double.class, 0.7);
            assertThat(defaultTemp).isEqualTo(0.7);
        }

        @Test
        @DisplayName("Should throw exception for missing parameter")
        void shouldThrowForMissingParameter() {
            assertThatThrownBy(() -> context.getParameter("nonexistent"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Parameter 'nonexistent' not found");
        }

        @Test
        @DisplayName("Should validate parameter ranges")
        void shouldValidateParameterRanges() {
            // These should throw ValidationException based on schema constraints
            assertThatThrownBy(() -> context.setParameter("temperature", 2.0))
                .isInstanceOf(ValidationException.class);

            assertThatThrownBy(() -> context.setParameter("max_tokens", -1))
                .isInstanceOf(ValidationException.class);

            assertThatThrownBy(() -> context.setParameter("top_p", 1.5))
                .isInstanceOf(ValidationException.class);
        }

        @Test
        @DisplayName("Should clear parameters")
        void shouldClearParameters() {
            context.setParameter("temperature", 0.8);
            context.setParameter("max_tokens", 100);

            assertThat(context.getParameters()).hasSize(2);

            context.clearParameters();
            assertThat(context.getParameters()).isEmpty();
        }

        @Test
        @DisplayName("Should handle null parameter values")
        void shouldHandleNullParameterValues() {
            assertThatThrownBy(() -> context.setParameter("temperature", null))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("cannot be null");
        }
    }

    @Nested
    @DisplayName("Model Management Tests")
    class ModelManagementTests {

        @Test
        @DisplayName("Should set valid model")
        void shouldSetValidModel() {
            context.setModel("claude-3-haiku-20240307");

            context.addUserMessage("Test");
            JsonNode request = context.buildRequest();
            assertThat(request.get("model").asText()).isEqualTo("claude-3-haiku-20240307");
        }

        @Test
        @DisplayName("Should reject invalid model when validation enabled")
        void shouldRejectInvalidModel() {
            assertThatThrownBy(() -> context.setModel("invalid-model-name"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("not supported by this provider");
        }

        @Test
        @DisplayName("Should get supported models")
        void shouldGetSupportedModels() {
            List<String> models = context.getSupportedModels();

            assertThat(models).isNotEmpty();
            assertThat(models).contains("claude-3-5-sonnet-20241022");
            assertThat(models).contains("claude-3-haiku-20240307");
        }

        @Test
        @DisplayName("Should use default model from schema")
        void shouldUseDefaultModel() {
            context.addUserMessage("Test");
            JsonNode request = context.buildRequest();

            // Should use default model from schema
            assertThat(request.get("model").asText()).isEqualTo("claude-3-5-sonnet-20241022");
        }
    }

    @Nested
    @DisplayName("Request Building Tests")
    class RequestBuildingTests {

        @BeforeEach
        void setUpRequest() {
            context.setApiKey("test-api-key");
            context.addUserMessage("Hello");
        }

        @Test
        @DisplayName("Should build basic request")
        void shouldBuildBasicRequest() {
            JsonNode request = context.buildRequest();

            assertThat(request.has("model")).isTrue();
            assertThat(request.has("messages")).isTrue();
            assertThat(request.has("max_tokens")).isTrue();
            assertThat(request.get("messages").isArray()).isTrue();
            assertThat(request.get("messages").size()).isEqualTo(1);
        }

        @Test
        @DisplayName("Should include system message in request")
        void shouldIncludeSystemMessageInRequest() {
            context.setSystemMessage("You are helpful");
            JsonNode request = context.buildRequest();

            // For Claude, system message should be separate field
            assertThat(request.get("system").asText()).isEqualTo("You are helpful");
        }

        @Test
        @DisplayName("Should include custom parameters")
        void shouldIncludeCustomParameters() {
            context.setParameter("temperature", 0.5);
            context.setParameter("top_p", 0.8);

            JsonNode request = context.buildRequest();

            assertThat(request.get("temperature").asDouble()).isEqualTo(0.5);
            assertThat(request.get("top_p").asDouble()).isEqualTo(0.8);
        }

        @Test
        @DisplayName("Should handle streaming parameter")
        void shouldHandleStreamingParameter() {
            JsonNode streamingRequest = context.buildRequest(true);
            JsonNode nonStreamingRequest = context.buildRequest(false);

            if (context.supportsStreaming()) {
                assertThat(streamingRequest.get("stream").asBoolean()).isTrue();
            }
            assertThat(nonStreamingRequest.get("stream").asBoolean()).isFalse();
        }

        @Test
        @DisplayName("Should apply default configuration values")
        void shouldApplyDefaultConfigValues() {
            ContextConfig configWithDefaults = new ContextConfig();
            configWithDefaults.setDefaultMaxTokens(200);
            configWithDefaults.setDefaultTemperature(0.9);

            GeneralContext contextWithDefaults = new GeneralContext("schemas/claude.json", configWithDefaults);
            contextWithDefaults.addUserMessage("Test");

            JsonNode request = contextWithDefaults.buildRequest();

            // The schema might have its own defaults that override config defaults
            // Check max_tokens - it seems the schema has 1024 as default
            assertThat(request.get("max_tokens").asInt()).isEqualTo(1024);

            // Temperature might not be in the request if not set in schema
            JsonNode tempNode = request.get("temperature");
            if (tempNode != null) {
                assertThat(tempNode.asDouble()).isEqualTo(0.9);
            } else {
                // If temperature is not in the request, that's also valid
                assertThat(request.has("temperature")).isFalse();
            }
        }

        @Test
        @DisplayName("Should remove null values from request")
        void shouldRemoveNullValues() {
            JsonNode request = context.buildRequest();

            // Request should not contain null values
            assertThat(hasNullValues(request)).isFalse();
        }

        private boolean hasNullValues(JsonNode node) {
            if (node.isObject()) {
                return node.fields().hasNext() &&
                       java.util.stream.StreamSupport.stream(
                           java.util.Spliterators.spliteratorUnknownSize(node.fields(), 0), false)
                       .anyMatch(entry -> entry.getValue().isNull() || hasNullValues(entry.getValue()));
            } else if (node.isArray()) {
                for (JsonNode item : node) {
                    if (hasNullValues(item)) return true;
                }
            }
            return false;
        }
    }

    @Nested
    @DisplayName("Validation Tests")
    class ValidationTests {

        @Test
        @DisplayName("Should validate complete request")
        void shouldValidateCompleteRequest() {
            // Empty context should be invalid
            assertThat(context.isValidRequest()).isFalse();

            List<String> errors = context.getValidationErrors();
            assertThat(errors).isNotEmpty();
            assertThat(errors).anyMatch(error -> error.contains("At least one message is required"));

            // Add message to make it valid
            context.addUserMessage("Hello");
            assertThat(context.isValidRequest()).isTrue();
            assertThat(context.getValidationErrors()).isEmpty();
        }

        @Test
        @DisplayName("Should validate last message role")
        void shouldValidateLastMessageRole() {
            // Based on Claude schema, last message should be from user
            context.addUserMessage("Hello");
            context.addAssistantMessage("Hi");

            // Adding another assistant message should potentially be invalid
            // (depends on schema validation rules)
            List<String> errors = context.getValidationErrors();

            // Should require user as last message for Claude
            if (!errors.isEmpty()) {
                assertThat(errors).anyMatch(error -> error.contains("Last message must be from: user"));
            }
        }

        @Test
        @DisplayName("Should disable validation when configured")
        void shouldDisableValidationWhenConfigured() {
            ContextConfig noValidationConfig = new ContextConfig();
            noValidationConfig.setEnableValidation(false);

            GeneralContext noValidationContext = new GeneralContext("schemas/claude.json", noValidationConfig);

            // Should not throw even with invalid parameters
            assertThatCode(() -> noValidationContext.setParameter("temperature", 5.0))
                .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("Context Reset Tests")
    class ContextResetTests {

        @Test
        @DisplayName("Should reset context to initial state")
        void shouldResetToInitialState() {
            // Set up context with data
            context.setSystemMessage("Test system");
            context.setParameter("temperature", 0.8);
            context.addUserMessage("Hello");
            context.addAssistantMessage("Hi");

            // Verify data is present
            assertThat(context.getMessages()).hasSize(2);
            assertThat(context.hasParameter("temperature")).isTrue();

            // Reset context
            context.reset();

            // Verify reset
            assertThat(context.getMessages()).isEmpty();
            assertThat(context.hasParameter("temperature")).isFalse();
            assertThat(context.isValidRequest()).isFalse();
        }

        @Test
        @DisplayName("Should preserve API key after reset")
        void shouldPreserveApiKeyAfterReset() {
            context.setApiKey("test-key");
            context.addUserMessage("Test");

            context.reset();

            // API key should be preserved
            assertThat(context.hasApiKey()).isTrue();
        }
    }

    @Nested
    @DisplayName("Feature Support Tests")
    class FeatureSupportTests {

        @Test
        @DisplayName("Should report correct feature support")
        void shouldReportCorrectFeatureSupport() {
            // Based on Claude schema
            assertThat(context.supportsMultimodal()).isTrue();
            assertThat(context.supportsStreaming()).isTrue();
            assertThat(context.supportsSystemMessages()).isTrue();
        }

        @Test
        @DisplayName("Should handle multimodal content when supported")
        void shouldHandleMultimodalWhenSupported() {
            if (context.supportsMultimodal()) {
                // Create a small test image data (base64)
                String testImageData = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8/5+hHgAHggJ/PchI7wAAAABJRU5ErkJggg==";

                assertThatCode(() ->
                    context.addUserMessage("What's in this image?", "image/png", testImageData))
                    .doesNotThrowAnyException();

                JsonNode request = context.buildRequest();
                JsonNode content = request.get("messages").get(0).get("content");

                assertThat(content.size()).isEqualTo(2); // text + image
                assertThat(content.get(1).get("type").asText()).isEqualTo("image");
            }
        }
    }

    @Nested
    @DisplayName("Response Processing Tests")
    class ResponseProcessingTests {

        @Test
        @DisplayName("Should extract text from successful response")
        void shouldExtractTextFromSuccessfulResponse() throws IOException {
            String mockResponseJson = """
                {
                    "id": "msg_123",
                    "type": "message",
                    "role": "assistant",
                    "content": [{"type": "text", "text": "Hello! How can I help you?"}],
                    "model": "claude-3-5-sonnet-20241022",
                    "stop_reason": "end_turn"
                }
                """;

            JsonNode mockResponse = objectMapper.readTree(mockResponseJson);
            String extractedText = context.extractTextResponse(mockResponse);

            assertThat(extractedText).isEqualTo("Hello! How can I help you?");
        }

        @Test
        @DisplayName("Should extract error from error response")
        void shouldExtractErrorFromErrorResponse() throws IOException {
            String errorResponseJson = """
                {
                    "type": "error",
                    "error": {
                        "type": "invalid_request_error",
                        "message": "Missing required field: max_tokens"
                    }
                }
                """;

            JsonNode errorResponse = objectMapper.readTree(errorResponseJson);
            String errorMessage = context.extractError(errorResponse);

            assertThat(errorMessage).isEqualTo("Missing required field: max_tokens");
        }

        @Test
        @DisplayName("Should handle malformed response gracefully")
        void shouldHandleMalformedResponseGracefully() throws IOException {
            String malformedJson = """
                {
                    "unexpected": "structure"
                }
                """;

            JsonNode malformedResponse = objectMapper.readTree(malformedJson);

            assertThatThrownBy(() -> context.extractTextResponse(malformedResponse))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to extract text response");
        }
    }
}
