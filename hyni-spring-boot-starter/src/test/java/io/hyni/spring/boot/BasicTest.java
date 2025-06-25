package io.hyni.spring.boot;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BasicTest {

    @Test
    void simpleTest() {
        assertThat(1 + 1).isEqualTo(2);
    }
}
