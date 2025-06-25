package io.hyni.spring.boot.model;

import com.fasterxml.jackson.databind.JsonNode;

public class ChatResponse {
    private final String provider;
    private final String text;
    private final String model;
    private final long duration;
    private final JsonNode rawResponse;

    private ChatResponse(Builder builder) {
        this.provider = builder.provider;
        this.text = builder.text;
        this.model = builder.model;
        this.duration = builder.duration;
        this.rawResponse = builder.rawResponse;
    }

    public static Builder builder() {
        return new Builder();
    }

    // Getters
    public String getProvider() { return provider; }
    public String getText() { return text; }
    public String getModel() { return model; }
    public long getDuration() { return duration; }
    public JsonNode getRawResponse() { return rawResponse; }

    public static class Builder {
        private String provider;
        private String text;
        private String model;
        private long duration;
        private JsonNode rawResponse;

        public Builder provider(String provider) {
            this.provider = provider;
            return this;
        }

        public Builder text(String text) {
            this.text = text;
            return this;
        }

        public Builder model(String model) {
            this.model = model;
            return this;
        }

        public Builder duration(long duration) {
            this.duration = duration;
            return this;
        }

        public Builder rawResponse(JsonNode rawResponse) {
            this.rawResponse = rawResponse;
            return this;
        }

        public ChatResponse build() {
            return new ChatResponse(this);
        }
    }
}
