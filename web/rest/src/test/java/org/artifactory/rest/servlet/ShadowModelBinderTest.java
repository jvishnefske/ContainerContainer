package org.artifactory.rest.servlet;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class ShadowModelBinderTest {

    ShadowModelBinder shadowModelBinder;
    ShadowModelProcessor shadowModelProcessor;

    @BeforeEach
    void setUp() {
        shadowModelProcessor = new ShadowModelProcessor();
        shadowModelBinder = spy(new ShadowModelBinder(shadowModelProcessor));
    }

    @Test
    void configure() {
        assertDoesNotThrow(() -> shadowModelBinder.configure());
        verify(shadowModelBinder, times(1)).bind(shadowModelProcessor);
    }
}