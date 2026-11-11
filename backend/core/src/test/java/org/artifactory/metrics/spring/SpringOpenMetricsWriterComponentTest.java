package org.artifactory.metrics.spring;

import org.artifactory.metrics.framework.spring.JerseyServerMetricsConfiguration;
import org.artifactory.metrics.framework.spring.JvmMetricsConfiguration;
import org.artifactory.metrics.framework.spring.LogbackMetricsConfiguration;
import org.artifactory.metrics.framework.spring.OpenMetricsBeansConfig;
import org.artifactory.metrics.framework.spring.SpringOpenMetricsWriter;
import org.artifactory.metrics.framework.spring.SystemMetricsConfiguration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.IOException;
import java.io.StringWriter;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith({ SpringExtension.class, MockitoExtension.class })
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ContextConfiguration(classes = {
        OpenMetricsBeansConfig.class,
        JvmMetricsConfiguration.class, LogbackMetricsConfiguration.class,
        JerseyServerMetricsConfiguration.class, SystemMetricsConfiguration.class }
)
@TestPropertySource(properties = {"artifactory.metrics.enabled=true"})
class SpringOpenMetricsWriterComponentTest {
    List<String> openMetricsLines;

    @BeforeAll
    void setUp(@Autowired SpringOpenMetricsWriter metricsService) throws IOException {
        String metricsText;
        try (StringWriter writer = new StringWriter()) {
            metricsService.write(writer, false);
            metricsText = writer.toString();
        }
        this.openMetricsLines = List.of(metricsText.split(System.lineSeparator()));
    }

    @ParameterizedTest
    @CsvSource({
            // JvmMetricsConfiguration
            "jvm_gc_max_data_size_bytes, gauge, JvmGcMetrics",
            "jvm_gc_overhead, gauge, JvmHeapPressureMetrics",
            "jvm_memory_used_bytes, gauge, JvmMemoryMetrics",
            "jvm_threads_live_threads, gauge, JvmThreadMetrics",
            "jvm_classes_loaded_classes, gauge, ClassLoaderMetrics",
            "jvm_info, gauge, JvmInfoMetrics",
            "jvm_compilation_time_ms, counter, JvmCompilationMetrics",
            // LogbackMetricsConfiguration
            "logback_events, counter, LogbackMetrics",
            // SystemMetricsConfiguration
            "process_uptime_seconds, gauge, UptimeMetrics",
            "system_cpu_count, gauge, ProcessorMetrics",
    })
    void springMetrics_shouldAppearInOpenMetrics(String metricName, String metricType, String beanType) {
        // Search the metric definition line, like "# TYPE jvm_info gauge"
        String expectedMetricDefinition   = "# TYPE " + metricName + " " + metricType;
        assertTrue(this.openMetricsLines.contains(expectedMetricDefinition),
                "The " + metricName + " metric definition is missing.");

        // Search the metric value line, which always starts from the metric name
        String metricValuePrefix = metricName + ("counter".equals(metricType) ? "_total" : "") + "{";
        assertTrue(this.openMetricsLines.stream().anyMatch(line -> line.startsWith(metricValuePrefix)),
                () -> "Cannot find the line with the " + metricName + " metric. Check the " + beanType + " bean.");
    }

    @Test
    void springMetrics_shouldHaveApplicationTag() {
        String metricLine = this.openMetricsLines.stream()
                .filter(line -> line.startsWith("jvm_info{")).findFirst().orElse("");
        assertTrue(metricLine.contains("application=\"Artifactory\""),
                "Spring metrics should have application=Artifactory tag");
    }

    @Test
    void write_onlyApplicationMetrics_shouldExcludeSystemMetrics(@Autowired SpringOpenMetricsWriter metricsService)
            throws IOException {
        String applicationMetricsText;
        try (StringWriter writer = new StringWriter()) {
            metricsService.write(writer, true);
            applicationMetricsText = writer.toString();
        }
        List<String> applicationMetricsLines = List.of(applicationMetricsText.split(System.lineSeparator()));

        List<String> systemMetricNames = List.of(
                "jvm_gc_max_data_size_bytes",
                "jvm_gc_overhead",
                "jvm_memory_used_bytes",
                "jvm_threads_live_threads",
                "jvm_classes_loaded_classes",
                "jvm_info",
                "jvm_compilation_time_ms",
                "logback_events",
                "process_uptime_seconds",
                "system_cpu_count"
        );

        for (String metricName : systemMetricNames) {
            String metricDefinition = "# TYPE " + metricName + " ";
            boolean metricFound = applicationMetricsLines.stream()
                    .anyMatch(line -> line.startsWith(metricDefinition));
            assertFalse(metricFound,
                    String.format("System metric '%s' should not appear when onlyApplicationMetrics=true. " +
                                    "This metric should only be in the primary registry, not the application registry.",
                            metricName));
        }
        assertFalse(applicationMetricsText.trim().isEmpty(), "Application metrics output should not be null or empty");
    }

    @Test
    void helpText_shouldNotContainEscapedQuotes(@Autowired SpringOpenMetricsWriter metricsService) throws IOException {
        // Arrange & Act
        String metricsText;
        try (StringWriter writer = new StringWriter()) {
            metricsService.write(writer, false);
            metricsText = writer.toString();
        }

        // Assert
        List<String> helpLines = this.openMetricsLines.stream()
                .filter(line -> line.startsWith("# HELP"))
                .toList();

        assertFalse(helpLines.isEmpty(), "Should have HELP lines in the output");

        for (String helpLine : helpLines) {
            assertFalse(helpLine.contains("\""),
                    "HELP line should not contain escaped quotes (\"): " + helpLine +
                            "\nThis violates the OpenMetrics spec. SpringOpenMetricsWriter should fix this.");
        }

        // Verify the full output also doesn't contain the pattern
        long escapedQuoteCount = metricsText.lines()
                .filter(line -> line.startsWith("# HELP") && line.contains("\""))
                .count();

        assertEquals(0, escapedQuoteCount, "Found " + escapedQuoteCount +
                " HELP lines with escaped quotes. All HELP lines should have unescaped quotes per OpenMetrics spec.");
    }
}
