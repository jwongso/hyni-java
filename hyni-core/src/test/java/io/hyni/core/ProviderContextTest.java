package io.hyni.core;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

@DisplayName("ProviderContext Tests")
class ProviderContextTest {

    @TempDir
    Path tempDir;

    private ContextFactory factory;
    private ProviderContext providerContext;

    @BeforeEach
    void setUp() throws IOException {
        // Create test schema
        String schemaContent = """
            {
              "provider": {"name": "test-provider"},
              "api": {"endpoint": "https://test.api.com"},
              "models": {"available": ["model1"], "default": "model1"},
              "request_template": {"model": "model1"},
              "message_format": {
                "structure": {"role": "user", "content": []},
                "content_types": {"text": {"type": "text", "text": ""}}
              },
              "response_format": {
                "success": {"text_path": ["content", 0, "text"]}
              },
              "features": {"streaming": false},
              "headers": {"required": {}},
              "message_roles": ["user", "assistant"]
            }
            """;

        Path schemaFile = tempDir.resolve("test-provider.json");
        Files.writeString(schemaFile, schemaContent);

        SchemaRegistry registry = SchemaRegistry.create()
            .setSchemaDirectory(tempDir.toString())
            .build();

        factory = new ContextFactory(registry);
        providerContext = new ProviderContext(factory, "test-provider");
    }

    @Nested
    @DisplayName("Constructor Tests")
    class ConstructorTests {

        @Test
        @DisplayName("Should create with factory and provider name")
        void shouldCreateWithFactoryAndProviderName() {
            ProviderContext context = new ProviderContext(factory, "test-provider");

            assertThat(context.getProviderName()).isEqualTo("test-provider");
            assertThat(context.getConfig()).isNotNull();
        }

        @Test
        @DisplayName("Should create with custom config")
        void shouldCreateWithCustomConfig() {
            ContextConfig config = new ContextConfig();
            config.setEnableValidation(false);

            ProviderContext context = new ProviderContext(factory, "test-provider", config);

            assertThat(context.getProviderName()).isEqualTo("test-provider");
            assertThat(context.getConfig()).isSameAs(config);
        }

        @Test
        @DisplayName("Should throw exception for null factory")
        void shouldThrowForNullFactory() {
            assertThatThrownBy(() -> new ProviderContext(null, "test-provider"))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("Factory cannot be null");
        }

        @Test
        @DisplayName("Should throw exception for null provider name")
        void shouldThrowForNullProviderName() {
            assertThatThrownBy(() -> new ProviderContext(factory, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("Provider name cannot be null");
        }

        @Test
        @DisplayName("Should throw exception for null config")
        void shouldThrowForNullConfig() {
            assertThatThrownBy(() -> new ProviderContext(factory, "test-provider", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("Config cannot be null");
        }
    }

    @Nested
    @DisplayName("Context Management Tests")
    class ContextManagementTests {

        @Test
        @DisplayName("Should get context instance")
        void shouldGetContextInstance() {
            GeneralContext context = providerContext.get();

            assertThat(context).isNotNull();
            assertThat(context.getProviderName()).isEqualTo("test-provider");
        }

        @Test
        @DisplayName("Should reuse same context instance within thread")
        void shouldReuseSameContextInstanceWithinThread() {
            GeneralContext context1 = providerContext.get();
            GeneralContext context2 = providerContext.get();

            assertThat(context1).isSameAs(context2);
        }

        @Test
        @DisplayName("Should maintain context state")
        void shouldMaintainContextState() {
            GeneralContext context = providerContext.get();
            context.addUserMessage("Test message");

            // Getting context again should return same instance with state
            GeneralContext sameContext = providerContext.get();
            assertThat(sameContext.getMessages()).hasSize(1);
        }

        @Test
        @DisplayName("Should reset context state")
        void shouldResetContextState() {
            GeneralContext context = providerContext.get();
            context.addUserMessage("Test message");
            context.setParameter("temperature", 0.8);

            assertThat(context.getMessages()).hasSize(1);
            assertThat(context.hasParameter("temperature")).isTrue();

            providerContext.reset();

            // Context should be reset
            assertThat(context.getMessages()).isEmpty();
            assertThat(context.hasParameter("temperature")).isFalse();
        }

        @Test
        @DisplayName("Should clear context and create new one")
        void shouldClearContextAndCreateNewOne() {
            GeneralContext context1 = providerContext.get();
            context1.addUserMessage("Test message");

            providerContext.clear();

            GeneralContext context2 = providerContext.get();

            // Should be a different instance
            assertThat(context2).isNotSameAs(context1);
            assertThat(context2.getMessages()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Thread Isolation Tests")
    class ThreadIsolationTests {

        @Test
        @DisplayName("Should create separate contexts for different threads")
        void shouldCreateSeparateContextsForDifferentThreads() throws Exception {
            ExecutorService executor = Executors.newFixedThreadPool(2);

            try {
                CompletableFuture<GeneralContext> future1 = CompletableFuture.supplyAsync(
                    () -> providerContext.get(), executor);

                CompletableFuture<GeneralContext> future2 = CompletableFuture.supplyAsync(
                    () -> providerContext.get(), executor);

                GeneralContext context1 = future1.get(5, TimeUnit.SECONDS);
                GeneralContext context2 = future2.get(5, TimeUnit.SECONDS);

                // Should be different instances from different threads
                assertThat(context1).isNotSameAs(context2);

            } finally {
                executor.shutdown();
                executor.awaitTermination(5, TimeUnit.SECONDS);
            }
        }

        @Test
        @DisplayName("Should isolate context state between threads")
        void shouldIsolateContextStateBetweenThreads() throws Exception {
            ExecutorService executor = Executors.newFixedThreadPool(2);

            try {
                // Main thread context
                GeneralContext mainContext = providerContext.get();
                mainContext.addUserMessage("Main thread message");

                // Other thread context
                CompletableFuture<Integer> future = CompletableFuture.supplyAsync(() -> {
                    GeneralContext threadContext = providerContext.get();
                    threadContext.addUserMessage("Other thread message");
                    return threadContext.getMessages().size();
                }, executor);

                int otherThreadMessageCount = future.get(5, TimeUnit.SECONDS);

                // Each thread should have its own state
                assertThat(mainContext.getMessages()).hasSize(1);
                assertThat(otherThreadMessageCount).isEqualTo(1);

            } finally {
                executor.shutdown();
                executor.awaitTermination(5, TimeUnit.SECONDS);
            }
        }

        @Test
        @DisplayName("Should reset only current thread context")
        void shouldResetOnlyCurrentThreadContext() throws Exception {
            ExecutorService executor = Executors.newSingleThreadExecutor();

            try {
                // Main thread context
                GeneralContext mainContext = providerContext.get();
                mainContext.addUserMessage("Main thread message");

                // Other thread context and reset
                CompletableFuture<Integer> future = CompletableFuture.supplyAsync(() -> {
                    GeneralContext threadContext = providerContext.get();
                    threadContext.addUserMessage("Other thread message");

                    providerContext.reset(); // Reset only this thread's context

                    return threadContext.getMessages().size();
                }, executor);

                int otherThreadMessageCount = future.get(5, TimeUnit.SECONDS);

                // Other thread context should be reset
                assertThat(otherThreadMessageCount).isEqualTo(0);

                // Main thread context should be unaffected
                assertThat(mainContext.getMessages()).hasSize(1);

            } finally {
                executor.shutdown();
                executor.awaitTermination(5, TimeUnit.SECONDS);
            }
        }
    }

    @Nested
    @DisplayName("Configuration Tests")
    class ConfigurationTests {

        @Test
        @DisplayName("Should use provided configuration")
        void shouldUseProvidedConfiguration() {
            ContextConfig config = new ContextConfig();
            config.setEnableValidation(false);
            config.setDefaultMaxTokens(200);

            ProviderContext contextWithConfig = new ProviderContext(factory, "test-provider", config);
            GeneralContext context = contextWithConfig.get();

            // Should be able to set invalid parameters when validation is disabled
            assertThatCode(() -> context.setParameter("temperature", 5.0))
                .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should maintain configuration across context resets")
        void shouldMaintainConfigurationAcrossResets() {
            ContextConfig config = new ContextConfig();
            config.setDefaultMaxTokens(300);

            ProviderContext contextWithConfig = new ProviderContext(factory, "test-provider", config);

            GeneralContext context1 = contextWithConfig.get();
            contextWithConfig.reset();
            GeneralContext context2 = contextWithConfig.get();

            // Configuration should be maintained
            assertThat(contextWithConfig.getConfig().getDefaultMaxTokens().orElse(0)).isEqualTo(300);
        }
    }

    @Nested
    @DisplayName("Error Handling Tests")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Should handle invalid provider name gracefully")
        void shouldHandleInvalidProviderNameGracefully() {
            ProviderContext invalidContext = new ProviderContext(factory, "non-existent-provider");

            assertThatThrownBy(() -> invalidContext.get())
                .isInstanceOf(io.hyni.core.exception.SchemaException.class)
                .hasMessageContaining("non-existent-provider");
        }

        @Test
        @DisplayName("Should handle reset on non-existent context gracefully")
        void shouldHandleResetOnNonExistentContextGracefully() {
            ProviderContext newContext = new ProviderContext(factory, "test-provider");

            // Reset before getting context should not throw
            assertThatCode(() -> newContext.reset()).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should handle clear on non-existent context gracefully")
        void shouldHandleClearOnNonExistentContextGracefully() {
            ProviderContext newContext = new ProviderContext(factory, "test-provider");

            // Clear before getting context should not throw
            assertThatCode(() -> newContext.clear()).doesNotThrowAnyException();
        }
    }
}
