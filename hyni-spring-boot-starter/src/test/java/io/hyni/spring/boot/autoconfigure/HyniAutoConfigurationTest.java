package io.hyni.spring.boot.autoconfigure;

import io.hyni.core.ContextFactory;
import io.hyni.core.SchemaRegistry;
import io.hyni.spring.boot.HyniTemplate;
import io.hyni.spring.boot.interceptor.LoggingInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class HyniAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(HyniAutoConfiguration.class));

    @Test
    void contextLoadsWithDefaults() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(SchemaRegistry.class);
            assertThat(context).hasSingleBean(ContextFactory.class);
            assertThat(context).hasSingleBean(HyniTemplate.class);
            assertThat(context).doesNotHaveBean(LoggingInterceptor.class); // Disabled by default
        });
    }

    @Test
    void contextLoadsWithCustomProperties() {
        contextRunner
            .withPropertyValues(
                "hyni.enabled=true",
                "hyni.default-provider=claude",
                "hyni.schema-directory=schemas",
                "hyni.logging-enabled=true",
                "hyni.validation-enabled=false"
            )
            .run(context -> {
                assertThat(context).hasSingleBean(HyniTemplate.class);
                assertThat(context).hasSingleBean(LoggingInterceptor.class);

                HyniProperties properties = context.getBean(HyniProperties.class);
                assertThat(properties.getDefaultProvider()).isEqualTo("claude");
                assertThat(properties.getSchemaDirectory()).isEqualTo("schemas");
                assertThat(properties.isLoggingEnabled()).isTrue();
                assertThat(properties.isValidationEnabled()).isFalse();
            });
    }

    @Test
    void contextDoesNotLoadWhenDisabled() {
        contextRunner
            .withPropertyValues("hyni.enabled=false")
            .run(context -> {
                assertThat(context).doesNotHaveBean(HyniTemplate.class);
                assertThat(context).doesNotHaveBean(ContextFactory.class);
            });
    }

    @Test
    void providerConfigurationLoaded() {
        contextRunner
            .withPropertyValues(
                "hyni.providers.openai.api-key=test-key",
                "hyni.providers.openai.model=gpt-4",
                "hyni.providers.claude.api-key-env-var=CLAUDE_KEY"
            )
            .run(context -> {
                HyniProperties properties = context.getBean(HyniProperties.class);

                assertThat(properties.getProviders()).containsKey("openai");
                assertThat(properties.getProviders().get("openai").getApiKey()).isEqualTo("test-key");
                assertThat(properties.getProviders().get("openai").getModel()).isEqualTo("gpt-4");

                assertThat(properties.getProviders()).containsKey("claude");
                assertThat(properties.getProviders().get("claude").getApiKeyEnvVar()).isEqualTo("CLAUDE_KEY");
            });
    }
}
