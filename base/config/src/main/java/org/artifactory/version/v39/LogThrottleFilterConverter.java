package org.artifactory.version.v39;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.version.converter.XmlConverter;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;

import static org.artifactory.logging.converter.LogbackConverterUtils.addProperty;
import static org.artifactory.logging.converter.LogbackConverterUtils.addTurboFilter;

@Slf4j
public class LogThrottleFilterConverter implements XmlConverter {

    public static final String THROTTLE_EXPIRE_MILLIS_PROP_NAME = "throttle-expire-after-write-millis";
    public static final String THROTTLE_TOP_LIMIT = "throttle-top-limit-counter";
    public static final String THROTTLE_CACHE_SIZE = "throttle-cache-size";

    public static final String THROTTLE_FILTER_CLASS = "org.artifactory.logging.throttle.LogThrottleFilter";

    private static final String THROTTLE_EXPIRE_TIME_CONTENT = "<property name=\""+ THROTTLE_EXPIRE_MILLIS_PROP_NAME
            +"\" value=\"${logging.throttle.expire.time.millis:-3600000}\"/>";

    private static final String THROTTLE_TOP_LIMIT_VALUE = "<property name=\""+ THROTTLE_TOP_LIMIT
            +"\" value=\"${logging.throttle.top.limit.counter:-100}\"/>";
    private static final String THROTTLE_CACHE_SIZE_VALUE = "<property name=\""+ THROTTLE_CACHE_SIZE
            +"\" value=\"${logging.throttle.cache.size:-500}\"/>";

    private static final String TURBO_FILTER_CONTENT = "    <turboFilter class=\""+ THROTTLE_FILTER_CLASS +"\">\n" +
            "        <expiryTimeFrameMillis>${" +THROTTLE_EXPIRE_MILLIS_PROP_NAME +"}</expiryTimeFrameMillis>\n" +
            "        <throttleTopLimit>${"+ THROTTLE_TOP_LIMIT +"}</throttleTopLimit>\n" +
            "        <cacheSize>${"+ THROTTLE_CACHE_SIZE +"}</cacheSize>\n" +
            "    </turboFilter>\n";

    @Override
    public void convert(Document doc) {
        log.debug("Starting logback conversion: adding log throttling filter.");

        Element root = doc.getRootElement();
        Namespace ns = root.getNamespace();

        addProperty(root, ns, THROTTLE_EXPIRE_MILLIS_PROP_NAME, THROTTLE_EXPIRE_TIME_CONTENT);
        addProperty(root, ns, THROTTLE_TOP_LIMIT, THROTTLE_TOP_LIMIT_VALUE);
        addProperty(root, ns, THROTTLE_CACHE_SIZE, THROTTLE_CACHE_SIZE_VALUE);

        addTurboFilter(root, ns, THROTTLE_FILTER_CLASS, TURBO_FILTER_CONTENT);

        log.debug("Logback conversion completed: log throttling filter was added successfully.");
    }
}
