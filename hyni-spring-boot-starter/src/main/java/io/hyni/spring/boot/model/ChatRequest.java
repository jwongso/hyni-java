package io.hyni.spring.boot.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChatRequest {
    private String model;
    private String systemMessage;
    private List<Message> messages;
    private Map<String, Object> parameters;
    private boolean stream;

    private ChatRequest() {
        this.messages = new ArrayList<>();
        this.parameters = new HashMap<>();
    }

    public static Builder builder() {
        return new Builder();
    }

    // Getters
    public String getModel() { return model; }
    public String getSystemMessage() { return systemMessage; }
    public List<Message> getMessages() { return messages; }
    public Map<String, Object> getParameters() { return parameters; }
    public boolean isStream() { return stream; }

    public static class Message {
        private final String role;
        private final String content;

        public Message(String role, String content) {
            this.role = role;
            this.content = content;
        }

        public String getRole() { return role; }
        public String getContent() { return content; }
    }

    public static class Builder {
        private final ChatRequest request = new ChatRequest();

        public Builder model(String model) {
            request.model = model;
            return this;
        }

        public Builder systemMessage(String systemMessage) {
            request.systemMessage = systemMessage;
            return this;
        }

        public Builder addMessage(String role, String content) {
            request.messages.add(new Message(role, content));
            return this;
        }

        public Builder addUserMessage(String content) {
            return addMessage("user", content);
        }

        public Builder addAssistantMessage(String content) {
            return addMessage("assistant", content);
        }

        public Builder parameter(String key, Object value) {
            request.parameters.put(key, value);
            return this;
        }

        public Builder temperature(double temperature) {
            return parameter("temperature", temperature);
        }

        public Builder maxTokens(int maxTokens) {
            return parameter("max_tokens", maxTokens);
        }

        public Builder stream(boolean stream) {
            request.stream = stream;
            return this;
        }

        public ChatRequest build() {
            return request;
        }
    }
}
