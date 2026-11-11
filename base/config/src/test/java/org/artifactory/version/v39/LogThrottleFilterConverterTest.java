package org.artifactory.version.v39;

import org.artifactory.convert.LogbackConverterTest;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

public class LogThrottleFilterConverterTest extends LogbackConverterTest {

    @ParameterizedTest
    @MethodSource("logbackPaths")
    void test(String logbackPath) throws Exception {
        Document doc = convertXml(logbackPath, new LogThrottleFilterConverter());
        Element root = doc.getRootElement();
        Namespace ns = root.getNamespace();

        assertPropertyExists(root, ns, LogThrottleFilterConverter.THROTTLE_EXPIRE_MILLIS_PROP_NAME);
        assertPropertyExists(root, ns, LogThrottleFilterConverter.THROTTLE_TOP_LIMIT);
        assertPropertyExists(root, ns, LogThrottleFilterConverter.THROTTLE_CACHE_SIZE);

        assertTurboFilterExists(root, ns, LogThrottleFilterConverter.THROTTLE_FILTER_CLASS);
    }

    private static Stream<String> logbackPaths() {
        return Stream.of(
                "/org/artifactory/logging/version/v39/logback_before_log_throttle.xml",
                "/logback-test.xml"
        );
    }
}
