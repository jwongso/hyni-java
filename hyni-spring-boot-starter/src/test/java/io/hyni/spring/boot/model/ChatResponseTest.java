package io.hyni.spring.boot.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChatResponseTest {

    @Test
    void testBuilderPattern() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        var rawResponse = objectMapper.createObjectNode().put("test", "value");

        ChatResponse response = ChatResponse.builder()
            .provider("openai")
            .text("Hello worl!")
            .model("gpt-4")
            .duration(1500L)
            .rawResponse(rawResponse)
            .build();

        assertThat(response.getProvider()).isEqualTo("openai");
        assertThat(response.getText()).isEqualTo("Hello worl!");
        assertThat(response.getModel()).isEqualTo("gpt-4");
        assertThat(response.getDuration()).isEqualTo(1500L);
        assertThat(response.getRawResponse()).isEqualTo(rawResponse);
    }
}
