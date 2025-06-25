package io.hyni.core;

import io.hyni.core.exception.SchemaException;
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

@DisplayName("ContextFactory Tests")
class ContextFactoryTest {

    @TempDir
    Path tempDir;

    private SchemaRegistry registry;
    private ContextFactory factory;

    @BeforeEach
    void setUp() throws IOException {
        // Create test schema
        String schemaContent = """
            {
              "provider": {"name": "test-provider"},
              "api": {"endpoint": "https://test.api.com"},
              "models": {
                "available": ["model1", "model2"],
                "default": "model1"
              },
              "request_template": {"model": "model1"},
              "message_format": {
                "structure": {"role": "user", "content": []},
                "content_types": {
                  "text": {"type": "text", "text": ""}
                }
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

        registry = SchemaRegistry.create()
            .setSchemaDirectory(tempDir.toString())
            .build();

        factory = new ContextFactory(registry);
    }

    @Nested
    @DisplayName("Constructor Tests")
    class ConstructorTests {

        @Test
        @DisplayName("Should create factory with valid registry")
        void shouldCreateFactoryWithValidRegistry() {
            assertThatCode(() -> new ContextFactory(registry))
                .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should throw exception for null registry")
        void shouldThrowForNullRegistry() {
            assertThatThrownBy(() -> new ContextFactory(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("Registry cannot be null");
        }
    }

    @Nested
    @DisplayName("Context Creation Tests")
    class ContextCreationTests {

        @Test
        @DisplayName("Should create context for valid provider")
        void shouldCreateContextForValidProvider() {
            GeneralContext context = factory.createContext("test-provider");

            assertThat(context).isNotNull();
            assertThat(context.getProviderName()).isEqualTo("test-provider");
            assertThat(context.getEndpoint()).isEqualTo("https://test.api.com");
        }

        @Test
        @DisplayName("Should create context with custom config")
        void shouldCreateContextWithCustomConfig() {
            ContextConfig config = new ContextConfig();
            config.setEnableValidation(false);
            config.setDefaultMaxTokens(500);

            GeneralContext context = factory.createContext("test-provider", config);

            assertThat(context).isNotNull();
            assertThat(context.getProviderName()).isEqualTo("test-provider");
        }

        @Test
        @DisplayName("Should throw exception for non-existent provider")
        void shouldThrowForNonExistentProvider() {
            assertThatThrownBy(() -> factory.createContext("non-existent"))
                .isInstanceOf(SchemaException.class)
                .hasMessageContaining("Schema file not found for provider: non-existent");
        }

        @Test
        @DisplayName("Should throw exception for null/empty provider name")
        void shouldThrowForInvalidProviderName() {
            assertThatThrownBy(() -> factory.createContext(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Provider name cannot be null or empty");

            assertThatThrownBy(() -> factory.createContext(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Provider name cannot be null or empty");

            assertThatThrownBy(() -> factory.createContext("  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Provider name cannot be null or empty");
        }

        @Test
        @DisplayName("Should throw exception for null config")
        void shouldThrowForNullConfig() {
            assertThatThrownBy(() -> factory.createContext("test-provider", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Config cannot be null");
        }

        @Test
        @DisplayName("Should create independent context instances")
        void shouldCreateIndependentContextInstances() {
            GeneralContext context1 = factory.createContext("test-provider");
            GeneralContext context2 = factory.createContext("test-provider");

            assertThat(context1).isNotSameAs(context2);

            // Modify one context
            context1.addUserMessage("Test message");

            // Other context should be unaffected
            assertThat(context1.getMessages()).hasSize(1);
            assertThat(context2.getMessages()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Schema Caching Tests")
    class SchemaCachingTests {

        @Test
        @DisplayName("Should cache schema after first load")
        void shouldCacheSchemaAfterFirstLoad() {
            // First creation - cache miss
            ContextFactory.CacheStats initialStats = factory.getCacheStats();
            factory.createContext("test-provider");

            ContextFactory.CacheStats afterFirstLoad = factory.getCacheStats();
            assertThat(afterFirstLoad.getMissCount()).isEqualTo(initialStats.getMissCount() + 1);
            assertThat(afterFirstLoad.getCacheSize()).isEqualTo(initialStats.getCacheSize() + 1);

            // Second creation - cache hit
            factory.createContext("test-provider");

            ContextFactory.CacheStats afterSecondLoad = factory.getCacheStats();
            assertThat(afterSecondLoad.getHitCount()).isEqualTo(afterFirstLoad.getHitCount() + 1);
            assertThat(afterSecondLoad.getMissCount()).isEqualTo(afterFirstLoad.getMissCount());
        }

        @Test
        @DisplayName("Should clear cache when requested")
        void shouldClearCacheWhenRequested() {
            // Load schema to populate cache
            factory.createContext("test-provider");

            ContextFactory.CacheStats beforeClear = factory.getCacheStats();
            assertThat(beforeClear.getCacheSize()).isGreaterThan(0);

            // Clear cache
            factory.clearCache();

            ContextFactory.CacheStats afterClear = factory.getCacheStats();
            assertThat(afterClear.getCacheSize()).isEqualTo(0);
            assertThat(afterClear.getHitCount()).isEqualTo(0);
            assertThat(afterClear.getMissCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("Should calculate hit rate correctly")
        void shouldCalculateHitRateCorrectly() {
            // Initial state - no hits or misses
            ContextFactory.CacheStats initialStats = factory.getCacheStats();
            assertThat(initialStats.getHitRate()).isEqualTo(0.0);

            // First load - miss
            factory.createContext("test-provider");

            // Second load - hit
            factory.createContext("test-provider");

            ContextFactory.CacheStats finalStats = factory.getCacheStats();
            assertThat(finalStats.getHitRate()).isEqualTo(0.5); // 1 hit out of 2 total
        }
    }

    @Nested
    @DisplayName("Thread-Local Context Tests")
    class ThreadLocalContextTests {

        @Test
        @DisplayName("Should create thread-local context")
        void shouldCreateThreadLocalContext() {
            GeneralContext context = factory.getThreadLocalContext("test-provider");

            assertThat(context).isNotNull();
            assertThat(context.getProviderName()).isEqualTo("test-provider");
        }

        @Test
        @DisplayName("Should reuse thread-local context within same thread")
        void shouldReuseThreadLocalContextWithinSameThread() {
            GeneralContext context1 = factory.getThreadLocalContext("test-provider");
            GeneralContext context2 = factory.getThreadLocalContext("test-provider");

            // Should be the same instance within the same thread
            assertThat(context1).isSameAs(context2);
        }

        @Test
        @DisplayName("Should create separate contexts for different threads")
        void shouldCreateSeparateContextsForDifferentThreads() throws Exception {
            ExecutorService executor = Executors.newFixedThreadPool(2);

            try {
                CompletableFuture<GeneralContext> future1 = CompletableFuture.supplyAsync(
                    () -> factory.getThreadLocalContext("test-provider"), executor);

                CompletableFuture<GeneralContext> future2 = CompletableFuture.supplyAsync(
                    () -> factory.getThreadLocalContext("test-provider"), executor);

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
        @DisplayName("Should isolate thread-local context state")
        void shouldIsolateThreadLocalContextState() throws Exception {
            ExecutorService executor = Executors.newFixedThreadPool(2);

            try {
                // Main thread context
                GeneralContext mainContext = factory.getThreadLocalContext("test-provider");
                mainContext.addUserMessage("Main thread message");

                // Other thread context
                CompletableFuture<Integer> future = CompletableFuture.supplyAsync(() -> {
                    GeneralContext threadContext = factory.getThreadLocalContext("test-provider");
                    threadContext.addUserMessage("Other thread message");
                    return threadContext.getMessages().size();
                }, executor);

                int otherThreadMessageCount = future.get(5, TimeUnit.SECONDS);

                // Each thread should have its own state
                assertThat(mainContext.getMessages()).hasSize(1);
                assertThat(otherThreadMessageCount).isEqualTo(1);

                // Messages should be different
                String mainMessage = mainContext.getMessages().get(0)
                    .get("content").get(0).get("text").asText();
                assertThat(mainMessage).isEqualTo("Main thread message");

            } finally {
                executor.shutdown();
                executor.awaitTermination(5, TimeUnit.SECONDS);
            }
        }

        @Test
        @DisplayName("Should clear thread-local contexts")
        void shouldClearThreadLocalContexts() {
            GeneralContext context = factory.getThreadLocalContext("test-provider");
            context.addUserMessage("Test message");

            assertThat(context.getMessages()).hasSize(1);

            factory.clearThreadLocalContexts();

            // Getting context again should create a new one
            GeneralContext newContext = factory.getThreadLocalContext("test-provider");
            assertThat(newContext.getMessages()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Thread Safety Tests")
    class ThreadSafetyTests {

        @Test
        @DisplayName("Should handle concurrent context creation")
        void shouldHandleConcurrentContextCreation() throws Exception {
            ExecutorService executor = Executors.newFixedThreadPool(10);

            try {
                CompletableFuture<?>[] futures = new CompletableFuture[100];

                for (int i = 0; i < 100; i++) {
                    futures[i] = CompletableFuture.runAsync(() -> {
                        GeneralContext context = factory.createContext("test-provider");
                        assertThat(context).isNotNull();
                        assertThat(context.getProviderName()).isEqualTo("test-provider");
                    }, executor);
                }

                CompletableFuture.allOf(futures).get(10, TimeUnit.SECONDS);

                // All should complete without exceptions
                for (CompletableFuture<?> future : futures) {
                    assertThat(future).isCompleted();
                    assertThat(future.isCompletedExceptionally()).isFalse();
                }

            } finally {
                executor.shutdown();
                executor.awaitTermination(5, TimeUnit.SECONDS);
            }
        }

        @Test
        @DisplayName("Should handle concurrent cache operations")
        void shouldHandleConcurrentCacheOperations() throws Exception {
            ExecutorService executor = Executors.newFixedThreadPool(5);

            try {
                CompletableFuture<?>[] futures = new CompletableFuture[50];

                for (int i = 0; i < 50; i++) {
                    final int index = i;
                    futures[i] = CompletableFuture.runAsync(() -> {
                        if (index % 10 == 0) {
                            factory.clearCache(); // Occasional cache clears
                        }
                        GeneralContext context = factory.createContext("test-provider");
                        assertThat(context).isNotNull();
                    }, executor);
                }

                CompletableFuture.allOf(futures).get(10, TimeUnit.SECONDS);

                // Cache should still be functional
                ContextFactory.CacheStats stats = factory.getCacheStats();
                assertThat(stats.getCacheSize()).isGreaterThanOrEqualTo(0);

            } finally {
                executor.shutdown();
                executor.awaitTermination(5, TimeUnit.SECONDS);
            }
        }
    }
}
