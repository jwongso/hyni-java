package io.hyni.spring.boot.autoconfigure;

import io.hyni.core.ContextConfig;
import io.hyni.core.ContextFactory;
import io.hyni.core.SchemaRegistry;
import io.hyni.spring.boot.HyniTemplate;
import io.hyni.spring.boot.interceptor.LoggingInterceptor;
import io.hyni.spring.boot.metrics.HyniMetricsRecorder;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

@AutoConfiguration
@ConditionalOnClass({ContextFactory.class, SchemaRegistry.class})
@ConditionalOnProperty(prefix = "hyni", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(HyniProperties.class)
public class HyniAutoConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(HyniAutoConfiguration.class);

    @Autowired(required = false)
    private MeterRegistry meterRegistry;

    @Bean
    @ConditionalOnMissingBean
    public SchemaRegistry schemaRegistry(HyniProperties properties) throws IOException {
        logger.info("Creating SchemaRegistry with directory: {}", properties.getSchemaDirectory());

        // Just use the existing SchemaRegistry without the non-existent method
        return SchemaRegistry.create()
            .setSchemaDirectory(properties.getSchemaDirectory())
            .build();
    }

    @Bean
    @ConditionalOnMissingBean
    public ContextConfig contextConfig(HyniProperties properties) {
        ContextConfig config = new ContextConfig();
        config.setEnableValidation(properties.isValidationEnabled());

        HyniProperties.DefaultConfig defaults = properties.getDefaults();
        if (defaults.getMaxTokens() != null) {
            config.setDefaultMaxTokens(defaults.getMaxTokens());
        }
        if (defaults.getTemperature() != null) {
            config.setDefaultTemperature(defaults.getTemperature());
        }

        return config;
    }

    @Bean
    @ConditionalOnMissingBean
    public ContextFactory contextFactory(SchemaRegistry schemaRegistry) {
        logger.info("Creating ContextFactory");
        // Use the constructor that takes only SchemaRegistry
        return new ContextFactory(schemaRegistry);
    }

    @Bean
    @ConditionalOnMissingBean
    public HyniTemplate hyniTemplate(ContextFactory contextFactory, HyniProperties properties) {
        logger.info("Creating HyniTemplate with default provider: {}", properties.getDefaultProvider());

        HyniTemplate template = new HyniTemplate(contextFactory, properties);

        // Configure providers with API keys
        properties.getProviders().forEach((provider, config) -> {
            String apiKey = resolveApiKey(config);
            if (apiKey != null) {
                template.configureProvider(provider, apiKey);
            }
        });

        return template;
    }

    @Bean
    @ConditionalOnProperty(prefix = "hyni", name = "logging-enabled", havingValue = "true")
    public LoggingInterceptor loggingInterceptor() {
        logger.info("Enabling Hyni request/response logging");
        return new LoggingInterceptor();
    }

    @Bean
    @ConditionalOnProperty(prefix = "hyni", name = "metrics-enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnClass(MeterRegistry.class)
    public HyniMetricsRecorder hyniMetricsRecorder() {
        if (meterRegistry != null) {
            logger.info("Enabling Hyni metrics collection");
            return new HyniMetricsRecorder(meterRegistry);
        }
        return null;
    }

    private String resolveApiKey(HyniProperties.ProviderConfig config) {
        // First check direct API key
        if (config.getApiKey() != null && !config.getApiKey().isEmpty()) {
            return config.getApiKey();
        }

        // Then check environment variable
        if (config.getApiKeyEnvVar() != null && !config.getApiKeyEnvVar().isEmpty()) {
            String apiKey = System.getenv(config.getApiKeyEnvVar());
            if (apiKey != null && !apiKey.isEmpty()) {
                return apiKey;
            }
        }

        return null;
    }
}
