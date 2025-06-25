package io.hyni.spring.boot.test;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

@SpringBootConfiguration
@EnableAutoConfiguration
@ComponentScan(
    basePackages = "io.hyni.spring.boot",
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.REGEX,
        pattern = "io\\.hyni\\.spring\\.boot\\.demo\\..*"
    )
)
public class TestConfiguration {
    // Test configuration for Spring Boot tests
}
