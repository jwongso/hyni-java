package io.hyni.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.hyni.core.exception.ValidationException;
import io.hyni.core.util.TestHttpClient;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIf;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.*;

@DisplayName("General Context Functional Tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class GeneralContextFunctionalTest {

    private String apiKey;
    private SchemaRegistry registry;
    private ContextFactory factory;
    private GeneralContext context;
    private TestHttpClient httpClient;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() throws IOException {
        // Get API key from environment or config file
        apiKey = getApiKey();

        // Setup test environment
        String testSchemaDir = "src/test/resources/schemas";
        registry = SchemaRegistry.create()
            .setSchemaDirectory(testSchemaDir)
            .build();

        factory = new ContextFactory(registry);

        ContextConfig config = new ContextConfig();
        config.setEnableValidation(true);
        config.setDefaultMaxTokens(100);
        config.setDefaultTemperature(0.3);

        context = factory.createContext("claude", config);
        if (apiKey != null && !apiKey.isEmpty()) {
            context.setApiKey(apiKey);
        }

        httpClient = new TestHttpClient();
        objectMapper = new ObjectMapper();
    }

    @AfterEach
    void tearDown() throws IOException {
        // Clean up test files
        Path testImage = Paths.get("test_image.png");
        if (Files.exists(testImage)) {
            Files.delete(testImage);
        }
    }

    private String getApiKey() {
        // Try environment variable first
        String key = System.getenv("CL_API_KEY");
        if (key != null && !key.isEmpty()) {
            return key;
        }

        // Try reading from ~/.hynirc file
        try {
            Path rcPath = Paths.get(System.getProperty("user.home"), ".hynirc");
            if (Files.exists(rcPath)) {
                Map<String, String> config = parseHynirc(rcPath);
                return config.get("CL_API_KEY");
            }
        } catch (Exception e) {
            System.err.println("Error reading .hynirc file: " + e.getMessage());
        }

        return null;
    }

    private static Map<String, String> parseHynirc(Path path) throws IOException {
        Map<String, String> config = new HashMap<>();
        List<String> lines = Files.readAllLines(path);

        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;

            int equalIndex = line.indexOf('=');
            if (equalIndex > 0) {
                String key = line.substring(0, equalIndex).trim();
                String value = line.substring(equalIndex + 1).trim();
                config.put(key, value);
            }
        }

        return config;
    }

    private void createTestImage() throws IOException {
        // Create a small test image (1x1 pixel PNG)
        byte[] pngData = {
            (byte)0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
            0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
            0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
            0x08, 0x02, 0x00, 0x00, 0x00, (byte)0x90, 0x77, 0x53,
            (byte)0xDE, 0x00, 0x00, 0x00, 0x0C, 0x49, 0x44, 0x41,
            0x54, 0x08, (byte)0x99, 0x01, 0x01, 0x00, 0x00, 0x00,
            (byte)0xFF, (byte)0xFF, 0x00, 0x00, 0x00, 0x02, 0x00, 0x01,
            (byte)0xE2, 0x21, (byte)0xBC, 0x33, 0x00, 0x00, 0x00, 0x00,
            0x49, 0x45, 0x4E, 0x44, (byte)0xAE, 0x42, 0x60, (byte)0x82
        };

        Files.write(Paths.get("test_image.png"), pngData);
    }

    @Nested
    @DisplayName("Schema Registry Basic Functionality")
    class SchemaRegistryTests {

        @Test
        @Order(1)
        @DisplayName("Should load providers and create contexts")
        void schemaRegistryBasicFunctionality() {
            // Test provider availability
            assertThat(registry.isProviderAvailable("claude")).isTrue();

            // Test available providers list
            List<String> providers = registry.getAvailableProviders();
            assertThat(providers).isNotEmpty().contains("claude");

            // Test context creation
            assertThat(context).isNotNull();
            assertThat(context.supportsMultimodal()).isTrue();
            assertThat(context.supportsSystemMessages()).isTrue();
            assertThat(context.supportsStreaming()).isTrue();
        }

        @Test
        @Order(2)
        @DisplayName("Should handle context factory caching")
        void contextFactoryFunctionality() {
            // Test cache stats after first context creation
            ContextFactory.CacheStats stats = factory.getCacheStats();
            assertThat(stats.getCacheSize()).isGreaterThanOrEqualTo(1);

            // Create another context for the same provider - should use cache
            GeneralContext context2 = factory.createContext("claude");
            ContextFactory.CacheStats stats2 = factory.getCacheStats();
            assertThat(stats2.getHitCount()).isEqualTo(stats.getHitCount() + 1);

            // Verify context is properly initialized
            assertThat(context2.getProviderName()).isEqualTo("claude");
            assertThat(context2.getEndpoint()).isEqualTo(context.getEndpoint());
        }

        @Test
        @Order(3)
        @DisplayName("Should support thread-local contexts")
        void threadLocalContext() throws InterruptedException {
            // Get thread-local context
            GeneralContext tlContext = factory.getThreadLocalContext("claude");
            tlContext.setApiKey(apiKey);
            tlContext.addUserMessage("Thread-local test");

            // Verify the context works properly
            assertThat(tlContext.getProviderName()).isEqualTo("claude");
            assertThat(tlContext.getMessages()).hasSize(1);

            JsonNode message = tlContext.getMessages().get(0);
            assertThat(message.get("content").get(0).get("text").asText())
                .isEqualTo("Thread-local test");

            // Test in another thread
            Thread thread = new Thread(() -> {
                GeneralContext threadContext = factory.getThreadLocalContext("claude");
                threadContext.setApiKey(apiKey);

                // This context should be different from main thread's context
                assertThat(threadContext.getMessages()).isEmpty();

                threadContext.addUserMessage("Different thread test");
                JsonNode threadMessage = threadContext.getMessages().get(0);
                assertThat(threadMessage.get("content").get(0).get("text").asText())
                    .isEqualTo("Different thread test");
            });

            thread.start();
            thread.join();

            // Main thread's context should be unchanged
            JsonNode mainMessage = tlContext.getMessages().get(0);
            assertThat(mainMessage.get("content").get(0).get("text").asText())
                .isEqualTo("Thread-local test");
        }
    }

    @Nested
    @DisplayName("Provider Context Helper")
    class ProviderContextTests {

        @Test
        @Order(10)
        @DisplayName("Should work with provider context helper")
        void providerContextHelper() {
            ProviderContext claudeCtx = new ProviderContext(factory, "claude");
            GeneralContext ctx = claudeCtx.get();
            ctx.setApiKey(apiKey);

            // Test basic functionality
            ctx.addUserMessage("Hello from provider_context");
            JsonNode request = ctx.buildRequest();

            assertThat(request.get("model").asText()).isEqualTo("claude-3-5-sonnet-20241022");
            assertThat(request.get("messages")).isNotEmpty();
            assertThat(request.get("messages").get(0).get("content").get(0).get("text").asText())
                .isEqualTo("Hello from provider_context");

            // Test reset
            claudeCtx.reset();
            assertThat(ctx.getMessages()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Basic API Request Tests")
    class BasicApiTests {

        @Test
        @Order(20)
        @DisplayName("Should build valid API request")
        void simpleAPIRequest() {
            context.setModel("claude-3-haiku-20240307")
                  .setSystemMessage("You are a helpful assistant.")
                  .addUserMessage("What is the capital of France?")
                  .setParameter("temperature", 0.0)
                  .setParameter("max_tokens", 50);

            JsonNode request = context.buildRequest();

            assertThat(request.get("model").asText()).isEqualTo("claude-3-haiku-20240307");
            assertThat(request.get("system").asText()).isEqualTo("You are a helpful assistant.");
            assertThat(request.get("messages")).isNotEmpty();
            assertThat(request.get("temperature").asDouble()).isEqualTo(0.0);
            assertThat(request.get("max_tokens").asInt()).isEqualTo(50);
        }

        @Test
        @Order(21)
        @DisplayName("Should handle multimodal requests")
        void multimodalRequest() throws IOException {
            assumeTrue(context.supportsMultimodal(), "Provider must support multimodal");

            createTestImage();

            context.addUserMessage("What's in this image?", "image/png", "test_image.png");
            JsonNode request = context.buildRequest();

            // Verify the request contains the image
            assertThat(request.get("messages")).isNotEmpty();
            JsonNode content = request.get("messages").get(0).get("content");
            assertThat(content.size()).isGreaterThanOrEqualTo(2); // Text + image
            assertThat(content.get(1).get("type").asText()).isEqualTo("image");
            assertThat(content.get(1).get("source").get("media_type").asText()).isEqualTo("image/png");
            assertThat(content.get(1).get("source").get("data").asText()).isNotEmpty();
        }

        @Test
        @Order(22)
        @EnabledIf("io.hyni.core.GeneralContextFunctionalTest#hasValidApiKey")
        @DisplayName("Should make successful API call")
        void basicSingleMessage() throws IOException, InterruptedException {
            assumeTrue(hasValidApiKey(), "API key required for this test");

            context.addUserMessage("Hello, please respond with exactly 'Hi there!'");

            assertThat(context.isValidRequest()).isTrue();

            JsonNode request = context.buildRequest();
            String payload = request.toString();

            TestHttpClient.ApiResponse response = httpClient.makeApiCall(
                context.getEndpoint(), apiKey, payload, true);

            assertThat(response.getStatusCode()).isEqualTo(200);

            JsonNode responseJson = response.getJsonBody();
            String text = context.extractTextResponse(responseJson);

            assertThat(text).isNotEmpty();
            System.out.println("Response: " + text);
        }
    }

    @Nested
    @DisplayName("Multi-turn Conversation Tests")
    class ConversationTests {

        @Test
        @Order(30)
        @DisplayName("Should handle multi-turn conversation structure")
        void multiTurnConversation() {
            // First exchange
            context.addUserMessage("What's 2+2?");
            JsonNode request1 = context.buildRequest();
            assertThat(request1.get("messages").size()).isEqualTo(1);

            // Simulate assistant response
            context.addAssistantMessage("2+2 equals 4.");

            // Second user message
            context.addUserMessage("What about 3+3?");
            JsonNode request2 = context.buildRequest();
            assertThat(request2.get("messages").size()).isEqualTo(3);

            // Verify message order and roles
            assertThat(request2.get("messages").get(0).get("role").asText()).isEqualTo("user");
            assertThat(request2.get("messages").get(1).get("role").asText()).isEqualTo("assistant");
            assertThat(request2.get("messages").get(2).get("role").asText()).isEqualTo("user");

            assertThat(context.isValidRequest()).isTrue();
        }

        @Test
        @Order(31)
        @EnabledIf("io.hyni.core.GeneralContextFunctionalTest#hasValidApiKey")
        @DisplayName("Should handle real multi-turn conversation")
        void realMultiTurnConversation() throws IOException, InterruptedException {
            assumeTrue(hasValidApiKey(), "API key required for this test");

            // First turn
            context.addUserMessage("What's the capital of France?");
            JsonNode request1 = context.buildRequest();

            TestHttpClient.ApiResponse response1 = httpClient.makeApiCall(
                context.getEndpoint(), apiKey, request1.toString(), true);

            assertThat(response1.getStatusCode()).isEqualTo(200);

            JsonNode responseJson1 = response1.getJsonBody();
            String text1 = context.extractTextResponse(responseJson1);
            assertThat(text1).isNotEmpty();

            context.addAssistantMessage(text1);

            // Second turn
            context.addUserMessage("What's the population of that city?");
            JsonNode request2 = context.buildRequest();

            TestHttpClient.ApiResponse response2 = httpClient.makeApiCall(
                context.getEndpoint(), apiKey, request2.toString(), true);

            assertThat(response2.getStatusCode()).isEqualTo(200);

            JsonNode responseJson2 = response2.getJsonBody();
            String text2 = context.extractTextResponse(responseJson2);
            assertThat(text2).isNotEmpty();

            // Verify the response mentions Paris and population
            String lowerText = text2.toLowerCase();
            assertThat(lowerText).satisfiesAnyOf(
                text -> assertThat(text).contains("paris"),
                text -> assertThat(text).contains("million"),
                text -> assertThat(text).contains("population")
            );
        }
    }

    @Nested
    @DisplayName("Parameter and Model Tests")
    class ParameterTests {

        @Test
        @Order(40)
        @DisplayName("Should handle system messages")
        void systemMessage() {
            String systemPrompt = "You are a helpful assistant that responds concisely.";
            context.setSystemMessage(systemPrompt);
            context.addUserMessage("Hello");

            JsonNode request = context.buildRequest();

            // Claude API uses separate "system" field
            if (request.has("system")) {
                assertThat(request.get("system").asText()).isEqualTo(systemPrompt);
                assertThat(request.get("messages").size()).isEqualTo(1);
                assertThat(request.get("messages").get(0).get("role").asText()).isEqualTo("user");
            } else {
                // OpenAI style - system message as first message
                assertThat(request.get("messages").size()).isEqualTo(2);
                assertThat(request.get("messages").get(0).get("role").asText()).isEqualTo("system");
                assertThat(request.get("messages").get(1).get("role").asText()).isEqualTo("user");
            }

            assertThat(context.isValidRequest()).isTrue();
        }

        @Test
        @Order(41)
        @DisplayName("Should handle parameter validation")
        void parameterHandling() {
            // Test valid parameters
            context.setParameter("temperature", 0.7)
                  .setParameter("max_tokens", 150)
                  .setParameter("top_p", 0.9)
                  .addUserMessage("Test message");

            JsonNode request = context.buildRequest();

            assertThat(request.get("temperature").asDouble()).isEqualTo(0.7);
            assertThat(request.get("max_tokens").asInt()).isEqualTo(150);
            assertThat(request.get("top_p").asDouble()).isEqualTo(0.9);

            // Test parameter validation (with validation enabled)
            assertThatThrownBy(() -> context.setParameter("temperature", 2.0))
                .isInstanceOf(ValidationException.class);
            assertThatThrownBy(() -> context.setParameter("max_tokens", -1))
                .isInstanceOf(ValidationException.class);
            assertThatThrownBy(() -> context.setParameter("top_p", 1.5))
                .isInstanceOf(ValidationException.class);
        }

        @Test
        @Order(42)
        @DisplayName("Should handle different models")
        void modelSelection() {
            // Test valid model
            context.setModel("claude-3-haiku-20240307");
            context.addUserMessage("Hello");

            JsonNode request = context.buildRequest();
            assertThat(request.get("model").asText()).isEqualTo("claude-3-haiku-20240307");

            // Test invalid model (should throw with validation enabled)
            assertThatThrownBy(() -> context.setModel("invalid-model"))
                .isInstanceOf(ValidationException.class);

            // Test supported models list
            List<String> models = context.getSupportedModels();
            assertThat(models).isNotEmpty().contains("claude-3-5-sonnet-20241022");
        }
    }

    @Nested
    @DisplayName("Streaming Tests")
    @EnabledIf("io.hyni.core.GeneralContextFunctionalTest#hasValidApiKey")
    class StreamingTests {

        @Test
        @Order(50)
        @DisplayName("Should handle streaming parameter")
        void streamingParameterTest() {
            assumeTrue(context.supportsStreaming(), "Provider must support streaming");

            // Test streaming parameter is set correctly
            context.setParameter("stream", true);
            context.addUserMessage("Count from 1 to 5, explaining each number.");

            JsonNode request = context.buildRequest();
            assertThat(request.has("stream")).isTrue();
            assertThat(request.get("stream").asBoolean()).isTrue();
        }

        @Test
        @Order(51)
        @DisplayName("Should handle actual streaming response")
        void actualStreamingTest() throws IOException, InterruptedException {
            assumeTrue(context.supportsStreaming() && hasValidApiKey(),
                "Provider must support streaming and API key required");

            context.setParameter("stream", true);
            context.addUserMessage("Write a short story about a robot learning to paint.");

            JsonNode request = context.buildRequest();

            TestHttpClient.StreamingResponse streamingResponse = httpClient.makeStreamingApiCall(
                context.getEndpoint(), apiKey, request.toString(), true);

            // Note: This is a simplified test - real streaming would require SSE parsing
            assertThat(streamingResponse.getStatusCode()).isEqualTo(200);
            System.out.println("Streaming response status: " + streamingResponse.getStatusCode());
        }
    }

    @Nested
    @DisplayName("Response Processing Tests")
    class ResponseProcessingTests {

        @Test
        @Order(60)
        @DisplayName("Should parse successful responses")
        void responseParsing() throws IOException {
            // Create mock successful response
            String mockResponseJson = """
                {
                    "id": "msg_123",
                    "type": "message",
                    "role": "assistant",
                    "content": [{"type": "text", "text": "Hello! How can I help you?"}],
                    "model": "claude-3-5-sonnet-20241022",
                    "stop_reason": "end_turn",
                    "usage": {"input_tokens": 15, "output_tokens": 8}
                }
                """;

            JsonNode mockResponse = objectMapper.readTree(mockResponseJson);

            // Test text extraction
            String text = context.extractTextResponse(mockResponse);
            assertThat(text).isEqualTo("Hello! How can I help you?");

            // Test full response extraction
            JsonNode content = context.extractFullResponse(mockResponse);
            assertThat(content.isArray()).isTrue();
            assertThat(content.size()).isEqualTo(1);
        }

        @Test
        @Order(61)
        @DisplayName("Should parse error responses")
        void errorResponseParsing() throws IOException {
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
            String errorMsg = context.extractError(errorResponse);
            assertThat(errorMsg).isEqualTo("Missing required field: max_tokens");
        }
    }

    // Helper method for conditional test execution
    public static boolean hasValidApiKey() {
        // Try environment variable first
        String key = System.getenv("CL_API_KEY");
        if (key != null && !key.isEmpty()) {
            return true;
        }

        // Try reading from ~/.hynirc file
        try {
            Path rcPath = Paths.get(System.getProperty("user.home"), ".hynirc");
            if (Files.exists(rcPath)) {
                Map<String, String> config = parseHynirc(rcPath);
                return config.get("CL_API_KEY") != null && !config.get("CL_API_KEY").isEmpty();
            }
        } catch (Exception e) {
            System.err.println("Error reading .hynirc file: " + e.getMessage());
        }

        return false;
    }
}
