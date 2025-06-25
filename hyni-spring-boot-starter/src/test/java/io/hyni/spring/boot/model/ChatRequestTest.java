package io.hyni.spring.boot.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChatRequestTest {

    @Test
    void testBuilderPattern() {
        ChatRequest request = ChatRequest.builder()
            .model("gpt-4")
            .systemMessage("You are helpful")
            .addUserMessage("Hello")
            .addAssistantMessage("Hi there!")
            .temperature(0.7)
            .maxTokens(100)
            .stream(true)
            .build();

        assertThat(request.getModel()).isEqualTo("gpt-4");
        assertThat(request.getSystemMessage()).isEqualTo("You are helpful");
        assertThat(request.getMessages()).hasSize(2);
        assertThat(request.getMessages().get(0).getRole()).isEqualTo("user");
        assertThat(request.getMessages().get(0).getContent()).isEqualTo("Hello");
        assertThat(request.getMessages().get(1).getRole()).isEqualTo("assistant");
        assertThat(request.getMessages().get(1).getContent()).isEqualTo("Hi there!");
        assertThat(request.getParameters()).containsEntry("temperature", 0.7);
        assertThat(request.getParameters()).containsEntry("max_tokens", 100);
        assertThat(request.isStream()).isTrue();
    }

    @Test
    void testEmptyBuilder() {
        ChatRequest request = ChatRequest.builder().build();

        assertThat(request.getModel()).isNull();
        assertThat(request.getSystemMessage()).isNull();
        assertThat(request.getMessages()).isEmpty();
        assertThat(request.getParameters()).isEmpty();
        assertThat(request.isStream()).isFalse();
    }
}
