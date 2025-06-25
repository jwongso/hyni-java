package io.hyni.spring.boot.test;

import io.hyni.spring.boot.HyniTemplate;
import io.hyni.spring.boot.model.ChatRequest;
import io.hyni.spring.boot.model.ChatResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.*;

@SpringBootApplication
@RestController
@RequestMapping("/test")
public class HyniSpringBootTestApplication {

    @Autowired
    private HyniTemplate hyniTemplate;

    public static void main(String[] args) {
        SpringApplication.run(HyniSpringBootTestApplication.class, args);
    }

    @GetMapping("/providers")
    public Object getProviders() {
        return hyniTemplate.getAvailableProviders();
    }

    @PostMapping("/chat")
    public ChatResponse chat(@RequestBody String message) {
        return hyniTemplate.chat(message);
    }

    @PostMapping("/chat/{provider}")
    public ChatResponse chatWithProvider(@PathVariable String provider, @RequestBody String message) {
        return hyniTemplate.chat(provider, message);
    }

    @Bean
    CommandLineRunner testRunner() {
        return args -> {
            System.out.println("Available providers: " + hyniTemplate.getAvailableProviders());
            System.out.println("OpenAI available: " + hyniTemplate.isProviderAvailable("openai"));
        };
    }
}
