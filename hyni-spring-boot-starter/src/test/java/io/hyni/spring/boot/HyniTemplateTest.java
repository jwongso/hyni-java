package io.hyni.spring.boot;

import io.hyni.core.ContextFactory;
import io.hyni.core.GeneralContext;
import io.hyni.spring.boot.autoconfigure.HyniProperties;
import io.hyni.spring.boot.exception.HyniException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.HashMap;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HyniTemplateTest {

    @Mock
    private ContextFactory contextFactory;

    @Mock
    private GeneralContext generalContext;

    private HyniProperties properties;
    private HyniTemplate hyniTemplate;

    @BeforeEach
    void setUp() {
        properties = new HyniProperties();
        properties.setDefaultProvider("openai");

        hyniTemplate = new HyniTemplate(contextFactory, properties);
    }

    @Test
    void testConfigureProvider() {
        // Execute
        hyniTemplate.configureProvider("openai", "test-api-key");

        // This should not throw any exception
        assertThatCode(() -> hyniTemplate.configureProvider("openai", "test-api-key"))
            .doesNotThrowAnyException();
    }

    @Test
    void testGetAvailableProviders() {
        // Setup
        when(contextFactory.getAvailableProviders()).thenReturn(Arrays.asList("openai", "claude", "mistral"));

        // Execute
        var providers = hyniTemplate.getAvailableProviders();

        // Verify
        assertThat(providers).containsExactly("openai", "claude", "mistral");
    }

    @Test
    void testIsProviderAvailable() {
        // Setup
        when(contextFactory.isProviderAvailable("openai")).thenReturn(true);
        when(contextFactory.isProviderAvailable("unknown")).thenReturn(false);

        // Execute & Verify
        assertThat(hyniTemplate.isProviderAvailable("openai")).isTrue();
        assertThat(hyniTemplate.isProviderAvailable("unknown")).isFalse();
    }

    @Test
    void testGetContext() {
        // Setup
        when(contextFactory.createContext("openai")).thenReturn(generalContext);

        // Configure provider
        hyniTemplate.configureProvider("openai", "test-api-key");

        // Execute
        GeneralContext context = hyniTemplate.getContext("openai");

        // Verify
        assertThat(context).isNotNull();
        verify(contextFactory).createContext("openai");
        verify(generalContext).setApiKey("test-api-key");
    }

    @Test
    void testMissingApiKeyThrowsException() {
        // Setup
        when(contextFactory.createContext("openai")).thenReturn(generalContext);

        // Execute & Verify
        assertThatThrownBy(() -> hyniTemplate.getContext("openai"))
            .isInstanceOf(HyniException.class)
            .hasMessageContaining("No API key configured");
    }

    @Test
    void testProviderConfigurationFromProperties() {
        // Setup properties
        HyniProperties.ProviderConfig providerConfig = new HyniProperties.ProviderConfig();
        providerConfig.setApiKey("props-api-key");
        providerConfig.setModel("gpt-4");

        properties.getProviders().put("openai", providerConfig);

        when(contextFactory.createContext("openai")).thenReturn(generalContext);

        // Execute
        GeneralContext context = hyniTemplate.getContext("openai");

        // Verify
        verify(generalContext).setApiKey("props-api-key");
        verify(generalContext).setModel("gpt-4");
    }
}
