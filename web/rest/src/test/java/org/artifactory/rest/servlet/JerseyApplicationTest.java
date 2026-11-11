package org.artifactory.rest.servlet;

import org.artifactory.spring.customizers.ResourceConfigCustomizer;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.context.support.GenericApplicationContext;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class JerseyApplicationTest {
    @Test
    void shouldReduceJerseyMemoryConsumption_shouldReturnTrueByDefault() {
        JerseyApplication application = new JerseyApplication();
        System.clearProperty("artifactory.jersey.disable-memory-hooks");
        boolean actual = application.shouldReduceJerseyMemoryConsumption();
        assertTrue(actual, "JerseyApplication should install Java hooks by default.");
    }

    @Test
    void shouldReduceJerseyMemoryConsumption_shouldReturnFalseWhenDisabledViaSystemProp() {
        JerseyApplication application = new JerseyApplication();
        System.setProperty("artifactory.jersey.disable-memory-hooks", "true");
        boolean actual = application.shouldReduceJerseyMemoryConsumption();
        assertFalse(actual, "artifactory.jersey.disable-memory-hooks=true should disable Java hooks.");
    }

    @Test
    void isPackageHandlerShadowingEnabled_ShouldReturnFalseByDefault() {
        JerseyApplication application = new JerseyApplication();
        System.clearProperty("artifactory.shadowing.enabled");
        boolean actual = application.isPackageHandlerShadowingEnabled();
        assertFalse(actual, "JerseyApplication should not use shadowing by default.");
    }

    @Test
    void isPackageHandlerShadowingEnabled_ShouldReturnTrueWhenEnabledViaSystemProp() {
        JerseyApplication application = new JerseyApplication();
        System.setProperty("artifactory.shadowing.enabled", "true");
        boolean actual = application.isPackageHandlerShadowingEnabled();
        assertTrue(actual, "artifactory.shadowing.enabled=true should enable shadowing.");
    }

    @Test
    void jerseyApplication_shouldCallCustomizerBeans() {
        JerseyApplication application = new JerseyApplication();

        ResourceConfigCustomizer customizer1 = mock(ResourceConfigCustomizer.class);
        ResourceConfigCustomizer customizer2 = mock(ResourceConfigCustomizer.class);

        try (GenericApplicationContext context = new GenericApplicationContext()) {
            context.registerBean(ResourceConfigCustomizer.class, () -> customizer1);
            context.registerBean("c2", ResourceConfigCustomizer.class, () -> customizer2);
            context.refresh();

            // First test: no Spring context => no customization
            application.applyCustomizers();
            Mockito.verifyNoInteractions(customizer1, customizer2);

            // Second test: attach Spring context => should apply customizations
            application.context = context;
            application.applyCustomizers();

            verify(customizer1).customize(application);
            verify(customizer2).customize(application);
        }
    }
}