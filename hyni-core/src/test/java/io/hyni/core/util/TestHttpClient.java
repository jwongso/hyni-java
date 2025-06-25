package io.hyni.core.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * HTTP client utility for testing, similar to the C++ CURL functionality
 */
public class TestHttpClient {
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public TestHttpClient() {
        this.httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .connectTimeout(Duration.ofSeconds(30))
            .build();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Make a regular API call (non-streaming)
     */
    public ApiResponse makeApiCall(String url, String apiKey, String payload, boolean isAnthropic)
            throws IOException, InterruptedException {
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .POST(HttpRequest.BodyPublishers.ofString(payload))
            .header("Content-Type", "application/json")
            .timeout(Duration.ofSeconds(60));

        // Add authentication headers based on provider
        if (isAnthropic) {
            requestBuilder.header("x-api-key", apiKey);
            requestBuilder.header("anthropic-version", "2023-06-01");
        } else {
            requestBuilder.header("Authorization", "Bearer " + apiKey);
        }

        HttpRequest request = requestBuilder.build();

        HttpResponse<String> response = httpClient.send(request,
            HttpResponse.BodyHandlers.ofString());

        return new ApiResponse(response.statusCode(), response.body());
    }

    /**
     * Make a streaming API call
     */
    public StreamingResponse makeStreamingApiCall(String url, String apiKey, String payload,
            boolean isAnthropic) throws IOException, InterruptedException {

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .POST(HttpRequest.BodyPublishers.ofString(payload))
            .header("Content-Type", "application/json")
            .header("Accept", "text/event-stream")
            .timeout(Duration.ofSeconds(60));

        // Add authentication headers
        if (isAnthropic) {
            requestBuilder.header("x-api-key", apiKey);
            requestBuilder.header("anthropic-version", "2023-06-01");
        } else {
            requestBuilder.header("Authorization", "Bearer " + apiKey);
        }

        HttpRequest request = requestBuilder.build();

        // For simplicity, use regular response and process as streaming
        HttpResponse<String> response = httpClient.send(request,
            HttpResponse.BodyHandlers.ofString());

        StreamingResponse streamingResponse = new StreamingResponse();
        streamingResponse.setStatusCode(response.statusCode());

        // Process the response body for streaming events
        processStreamingResponse(response.body(), streamingResponse);

        return streamingResponse;
    }

    private void processStreamingResponse(String responseBody, StreamingResponse streamingResponse) {
        String[] lines = responseBody.split("\n");

        for (String line : lines) {
            if (line.startsWith("data: ")) {
                String jsonData = line.substring(6);

                if ("[DONE]".equals(jsonData)) {
                    streamingResponse.setFinished(true);
                    continue;
                }

                try {
                    JsonNode event = objectMapper.readTree(jsonData);
                    streamingResponse.addEvent(jsonData);

                    // Extract content from delta (Anthropic style)
                    if (event.has("delta") && event.get("delta").has("text")) {
                        String content = event.get("delta").get("text").asText();
                        streamingResponse.appendContent(content);
                    }

                    // Extract content from delta (OpenAI style)
                    if (event.has("choices") && event.get("choices").isArray() &&
                        event.get("choices").size() > 0) {
                        JsonNode choice = event.get("choices").get(0);
                        if (choice.has("delta") && choice.get("delta").has("content")) {
                            String content = choice.get("delta").get("content").asText();
                            streamingResponse.appendContent(content);
                        }
                    }

                    // Check for errors
                    if (event.has("error")) {
                        streamingResponse.setError(true);
                        streamingResponse.setErrorMessage(event.get("error").get("message").asText());
                    }
                } catch (Exception e) {
                    System.err.println("Error parsing JSON: " + e.getMessage());
                }
            }
        }
    }

    /**
     * Response wrapper
     */
    public static class ApiResponse {
        private final int statusCode;
        private final String body;

        public ApiResponse(int statusCode, String body) {
            this.statusCode = statusCode;
            this.body = body;
        }

        public int getStatusCode() { return statusCode; }
        public String getBody() { return body; }

        public JsonNode getJsonBody() throws IOException {
            return new ObjectMapper().readTree(body);
        }
    }

    /**
     * Streaming response data structure
     */
    public static class StreamingResponse {
        private final List<String> events = new ArrayList<>();
        private final StringBuilder completeContent = new StringBuilder();
        private boolean finished = false;
        private boolean error = false;
        private String errorMessage = "";
        private int statusCode = 200;

        public void addEvent(String event) {
            events.add(event);
        }

        public void appendContent(String content) {
            completeContent.append(content);
        }

        public void setFinished(boolean finished) { this.finished = finished; }
        public void setError(boolean error) { this.error = error; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
        public void setStatusCode(int statusCode) { this.statusCode = statusCode; }

        // Getters
        public List<String> getEvents() { return events; }
        public String getCompleteContent() { return completeContent.toString(); }
        public boolean isFinished() { return finished; }
        public boolean isError() { return error; }
        public String getErrorMessage() { return errorMessage; }
        public int getStatusCode() { return statusCode; }
    }
}
