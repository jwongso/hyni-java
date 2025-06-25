package io.hyni.spring.boot.integration;

import io.hyni.spring.boot.HyniTemplate;
import io.hyni.spring.boot.model.ChatRequest;
import io.hyni.spring.boot.model.ChatResponse;
import io.hyni.spring.boot.test.TestConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = TestConfiguration.class) // Use TestConfiguration instead of HyniDemoApplication
@TestPropertySource(properties = {
    "hyni.enabled=true",
    "hyni.default-provider=openai",
    "hyni.providers.openai.api-key-env-var=OA_API_KEY",
    "hyni.providers.claude.api-key-env-var=CL_API_KEY",
    "hyni.providers.deepseek.api-key-env-var=DS_API_KEY",
    "hyni.providers.mistral.api-key-env-var=MS_API_KEY"
})
class RealApiIntegrationTest {

    @Autowired
    private HyniTemplate hyniTemplate;

    @BeforeEach
    void setUp() {
        // Configure API keys from environment variables
        configureProviderFromEnv("openai", "OA_API_KEY");
        configureProviderFromEnv("claude", "CL_API_KEY");
        configureProviderFromEnv("deepseek", "DS_API_KEY");
        configureProviderFromEnv("mistral", "MS_API_KEY");

        // Also try loading from .hynirc file
        loadFromHynirc();
    }

    private void configureProviderFromEnv(String provider, String envVar) {
        String apiKey = System.getenv(envVar);
        if (apiKey != null && !apiKey.isEmpty()) {
            hyniTemplate.configureProvider(provider, apiKey);
            System.out.println("Configured " + provider + " from environment variable " + envVar);
        }
    }

    private void loadFromHynirc() {
        try {
            Path rcPath = Paths.get(System.getProperty("user.home"), ".hynirc");
            if (Files.exists(rcPath)) {
                Map<String, String> config = parseHynirc(rcPath);

                if (config.containsKey("OA_API_KEY")) {
                    hyniTemplate.configureProvider("openai", config.get("OA_API_KEY"));
                    System.out.println("Configured OpenAI from .hynirc");
                }
                if (config.containsKey("CL_API_KEY")) {
                    hyniTemplate.configureProvider("claude", config.get("CL_API_KEY"));
                    System.out.println("Configured Claude from .hynirc");
                }
                if (config.containsKey("DS_API_KEY")) {
                    hyniTemplate.configureProvider("deepseek", config.get("DS_API_KEY"));
                    System.out.println("Configured DeepSeek from .hynirc");
                }
                if (config.containsKey("MS_API_KEY")) {
                    hyniTemplate.configureProvider("mistral", config.get("MS_API_KEY"));
                    System.out.println("Configured Mistral from .hynirc");
                }
            }
        } catch (Exception e) {
            System.err.println("Error reading .hynirc: " + e.getMessage());
        }
    }

    private Map<String, String> parseHynirc(Path path) throws Exception {
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

    @Test
    @EnabledIfEnvironmentVariable(named = "OA_API_KEY", matches = ".+")
    void testRealOpenAICall() {
        System.out.println("\n=== Testing OpenAI ===");

        ChatResponse response = hyniTemplate.chat("openai", "Say 'Hello from Hyni OpenAI!' and nothing else.");

        assertThat(response).isNotNull();
        assertThat(response.getText()).isNotEmpty();
        assertThat(response.getProvider()).isEqualTo("openai");
        assertThat(response.getDuration()).isGreaterThan(0);

        System.out.println("Response: " + response.getText());
        System.out.println("Model: " + response.getModel());
        System.out.println("Duration: " + response.getDuration() + "ms");
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "CL_API_KEY", matches = ".+")
    void testRealClaudeCall() {
        System.out.println("\n=== Testing Claude ===");

        ChatResponse response = hyniTemplate.chat("claude", "Say 'Hello from Hyni Claude!' and nothing else.");

        assertThat(response).isNotNull();
        assertThat(response.getText()).isNotEmpty();
        assertThat(response.getProvider()).isEqualTo("claude");
        assertThat(response.getDuration()).isGreaterThan(0);

        System.out.println("Response: " + response.getText());
        System.out.println("Model: " + response.getModel());
        System.out.println("Duration: " + response.getDuration() + "ms");
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "DS_API_KEY", matches = ".+")
    void testRealDeepSeekCall() {
        System.out.println("\n=== Testing DeepSeek ===");

        ChatResponse response = hyniTemplate.chat("deepseek", "Say 'Hello from Hyni DeepSeek!' and nothing else.");

        assertThat(response).isNotNull();
        assertThat(response.getText()).isNotEmpty();
        assertThat(response.getProvider()).isEqualTo("deepseek");
        assertThat(response.getDuration()).isGreaterThan(0);

        System.out.println("Response: " + response.getText());
        System.out.println("Model: " + response.getModel());
        System.out.println("Duration: " + response.getDuration() + "ms");
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "MS_API_KEY", matches = ".+")
    void testRealMistralCall() {
        System.out.println("\n=== Testing Mistral ===");

        ChatResponse response = hyniTemplate.chat("mistral", "Say 'Hello from Hyni Mistral!' and nothing else.");

        assertThat(response).isNotNull();
        assertThat(response.getText()).isNotEmpty();
        assertThat(response.getProvider()).isEqualTo("mistral");
        assertThat(response.getDuration()).isGreaterThan(0);

        System.out.println("Response: " + response.getText());
        System.out.println("Model: " + response.getModel());
        System.out.println("Duration: " + response.getDuration() + "ms");
    }

    @Test
    void testProviderComparison() {
        System.out.println("\n=== Provider Comparison Test ===");

        String prompt = "Explain what artificial intelligence is in exactly one sentence.";
        String[] providers = {"openai", "claude", "deepseek", "mistral"};
        String[] envVars = {"OA_API_KEY", "CL_API_KEY", "DS_API_KEY", "MS_API_KEY"};

        for (int i = 0; i < providers.length; i++) {
            String provider = providers[i];
            String envVar = envVars[i];

            if (System.getenv(envVar) != null || hasKeyInHynirc(envVar)) {
                try {
                    System.out.println("\n--- " + provider.toUpperCase() + " ---");

                    long startTime = System.currentTimeMillis();
                    ChatResponse response = hyniTemplate.chat(provider, prompt);
                    long totalTime = System.currentTimeMillis() - startTime;

                    System.out.println("Response: " + response.getText());
                    System.out.println("Model: " + response.getModel());
                    System.out.println("API Duration: " + response.getDuration() + "ms");
                    System.out.println("Total Duration: " + totalTime + "ms");

                    assertThat(response.getText()).isNotEmpty();

                } catch (Exception e) {
                    System.err.println("Error with " + provider + ": " + e.getMessage());
                }
            } else {
                System.out.println("Skipping " + provider + " - no API key found");
            }
        }
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "OA_API_KEY", matches = ".+")
    void testConversationFlow() {
        System.out.println("\n=== Testing Conversation Flow ===");

        // Multi-turn conversation
        ChatRequest request = ChatRequest.builder()
            .systemMessage("You are a helpful assistant. Keep responses brief.")
            .addUserMessage("My favorite color is blue. What's 5 + 3?")
            .temperature(0.3)
            .maxTokens(100)
            .build();

        ChatResponse response1 = hyniTemplate.chat("openai", request);
        System.out.println("First response: " + response1.getText());

        // Continue conversation
        ChatRequest request2 = ChatRequest.builder()
            .systemMessage("You are a helpful assistant. Keep responses brief.")
            .addUserMessage("My favorite color is blue. What's 5 + 3?")
            .addAssistantMessage(response1.getText())
            .addUserMessage("What's my favorite color?")
            .temperature(0.3)
            .maxTokens(50)
            .build();

        ChatResponse response2 = hyniTemplate.chat("openai", request2);
        System.out.println("Second response: " + response2.getText());

        assertThat(response2.getText().toLowerCase()).contains("blue");
    }

    private boolean hasKeyInHynirc(String keyName) {
        try {
            Path rcPath = Paths.get(System.getProperty("user.home"), ".hynirc");
            if (Files.exists(rcPath)) {
                Map<String, String> config = parseHynirc(rcPath);
                return config.containsKey(keyName) && !config.get(keyName).isEmpty();
            }
        } catch (Exception e) {
            // Ignore
        }
        return false;
    }
}
