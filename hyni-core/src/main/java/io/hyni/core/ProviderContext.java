package io.hyni.core;

import java.util.Objects;

/**
 * Convenience wrapper for a specific provider
 * Provides a simplified interface for working with a single provider
 */
public class ProviderContext {

    private final ContextFactory factory;
    private final String providerName;
    private final ContextConfig config;
    private final ThreadLocal<GeneralContext> threadLocalContext = new ThreadLocal<>();

    public ProviderContext(ContextFactory factory, String providerName) {
        this(factory, providerName, new ContextConfig());
    }

    public ProviderContext(ContextFactory factory, String providerName, ContextConfig config) {
        this.factory = Objects.requireNonNull(factory, "Factory cannot be null");
        this.providerName = Objects.requireNonNull(providerName, "Provider name cannot be null");
        this.config = Objects.requireNonNull(config, "Config cannot be null");
    }

    /**
     * Gets the thread-local context for this provider
     * Creates a new context on first access per thread
     */
    public GeneralContext get() {
        GeneralContext context = threadLocalContext.get();
        if (context == null) {
            context = factory.createContext(providerName, config);
            threadLocalContext.set(context);
        }
        return context;
    }

    /**
     * Resets the current thread's context
     */
    public void reset() {
        GeneralContext context = threadLocalContext.get();
        if (context != null) {
            context.reset();
        }
    }

    /**
     * Clears the thread-local context, forcing creation of a new one on next access
     */
    public void clear() {
        threadLocalContext.remove();
    }

    public String getProviderName() {
        return providerName;
    }

    public ContextConfig getConfig() {
        return config;
    }
}
