package org.artifactory.storage.db.aql.itest.service;

import static org.testng.Assert.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import lombok.experimental.UtilityClass;
import org.artifactory.aql.result.AqlRestResult;
import org.jfrog.common.ResourceUtils;
import org.joda.time.format.ISODateTimeFormat;

/**
 * @author William Arustamyan
 */
@UtilityClass
public class AqlJsonHelper {

    public static String readCompact(AqlRestResult aqlStreamer) {
        StringBuilder builder = new StringBuilder();
        byte[] tempResult;
        while ((tempResult = aqlStreamer.readCompact()) != null) {
            builder.append(new String(tempResult));
        }
        return builder.toString();
    }

    public static String load(String fileName) {
        return ResourceUtils.getResourceAsString(fileName);
    }

    public static void compareJsons(String result, String expected) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        Map resultMap = mapper.readValue(result, Map.class);
        Map expectedMap = mapper.readValue(expected, Map.class);
        assertTrue(areEquals(resultMap, expectedMap),
                "Results are not equal - expected:" + expected + " but got:" + result);
    }

    public static boolean areEquals(Object result, Object expected) {
        if ((result == null && expected != null) || (result != null && expected == null)) {
            return false;
        }
        if (result == null) {
            return true;
        }
        if (result instanceof Map) {
            if (!(expected instanceof Map)) {
                return false;
            }
            if (((Map) result).size() != ((Map) expected).size()) {
                Object resultValue = ((Map) result).get("value");
                if ("".equals(resultValue)) {
                    ((Map) result).remove("value");
                    return areEquals(result, expected);
                }
                Object expectedValue = ((Map) expected).get("value");
                if ("".equals(expectedValue)) {
                    ((Map) expected).remove("value");
                    return areEquals(result, expected);
                }
                return false;
            }
            for (Object o : ((Map) result).keySet()) {
                if (!areEquals(((Map) result).get(o), ((Map) expected).get(o))) {
                    return false;
                }
            }
            return true;
        } else if (result instanceof List) {
            if (!(expected instanceof List) || ((List) result).size() != ((List) expected).size()) {
                return false;
            }
            Iterator resultIt = ((List) result).iterator();
            while (resultIt.hasNext()) {
                boolean foundMatch = false;
                Object nextResult = resultIt.next();
                Iterator expectedIt = ((List) expected).iterator();
                while (expectedIt.hasNext()) {
                    Object nextExpected = expectedIt.next();
                    if (areEquals(nextResult, nextExpected)) {
                        resultIt.remove();
                        expectedIt.remove();
                        foundMatch = true;
                        break;
                    }
                }
                if (!foundMatch) {
                    return false;
                }
            }
            return ((List) result).isEmpty() && ((List) expected).isEmpty();
        } else if (result instanceof String) {
            if (!(expected instanceof String)) {
                return false;
            }
            try {
                if (ISODateTimeFormat.dateOptionalTimeParser().parseMillis((String) result) ==
                        ISODateTimeFormat.dateOptionalTimeParser().parseMillis((String) expected)) {
                    return true;
                }
            } catch (Exception ignored) {
            }
            return ((String) result).trim().equals(((String) expected).trim());
        } else {
            return result.equals(expected);
        }
    }
}
