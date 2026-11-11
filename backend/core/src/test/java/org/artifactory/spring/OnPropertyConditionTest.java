package org.artifactory.spring;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = OnPropertyConditionTest.TestConfiguration.class)
@TestPropertySource(properties = { "test.condition.explicit=true" })
class OnPropertyConditionTest {
    @Autowired
    ApplicationContext context;

    @Configuration
    static class TestConfiguration {
        @Bean(name = "explicit_match")
        @ConditionalOnProperty(prefix = "test", name="condition.explicit", havingValue = "true")
        public String explicitMatch() {
            return "explicit_match";
        }

        @Bean(name = "explicit_no_match")
        @ConditionalOnProperty(prefix = "test", name="condition.explicit", havingValue = "false")
        public String explicitNoMatch() {
            return "explicit_no_match";
        }

        @Bean(name = "implicit_match")
        @ConditionalOnProperty(name="test.condition.implicit", havingValue = "true", matchIfMissing = true)
        public String implicitMatch() {
            return "implicit_match";
        }

        @Bean(name = "implicit_no_match")
        @ConditionalOnProperty(name="test.condition.implicit", havingValue = "true", matchIfMissing = false)
        public String implicitNoMatch() {
            return "implicit_no_match";
        }
    }

    @Test
    void conditionalOnProperty_shouldWorkWhenPropertyMatches() {
        String actual = context.getBean("explicit_match", String.class);
        assertEquals("explicit_match", actual);
    }

    @Test
    void conditionalOnProperty_shouldWorkWhenPropertyIsMissing() {
        String actual = context.getBean("implicit_match", String.class);
        assertEquals("implicit_match", actual);
    }

    @Test
    void conditionalOnProperty_shouldNotMatchIfPropertyHasWrongValue() {
        assertThrows(NoSuchBeanDefinitionException.class, () -> {
            context.getBean("explicit_no_match", String.class);
        });
    }

    @Test
    void conditionalOnProperty_shouldNotMatchIfPropertyIsMissing() {
        assertThrows(NoSuchBeanDefinitionException.class, () -> {
            context.getBean("implicit_no_match", String.class);
        });
    }
}