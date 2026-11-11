package org.artifactory.addon.debian;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * @author Shashank Taliwal
 */
public class DebianInfoTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    public void architectureVariantRoundTrips() {
        DebianInfo info = new DebianInfo();
        assertNull("variant defaults to null", info.getArchitectureVariant());

        info.setArchitectureVariant("amd64v3");
        assertEquals("amd64v3", info.getArchitectureVariant());
    }

    @Test
    public void architectureVariantSerializedAsKebabCaseWhenSet() throws JsonProcessingException {
        DebianInfo info = new DebianInfo();
        info.setArchitecture("amd64");
        info.setArchitectureVariant("amd64v3");

        String json = OBJECT_MAPPER.writeValueAsString(info);

        assertTrue("base architecture must remain unchanged: " + json,
                json.contains("\"architecture\":\"amd64\""));
        assertTrue("variant must be serialized in kebab-case: " + json,
                json.contains("\"architecture-variant\":\"amd64v3\""));
    }

    @Test
    public void architectureVariantOmittedWhenNull() throws JsonProcessingException {
        DebianInfo info = new DebianInfo();
        info.setArchitecture("amd64");

        String json = OBJECT_MAPPER.writeValueAsString(info);

        assertFalse("variant must not appear when null: " + json,
                json.contains("architecture-variant"));
    }

    @Test
    public void architectureVariantOmittedWhenEmpty() throws JsonProcessingException {
        DebianInfo info = new DebianInfo();
        info.setArchitecture("amd64");
        info.setArchitectureVariant("");

        String json = OBJECT_MAPPER.writeValueAsString(info);

        assertFalse("variant must not appear when empty: " + json,
                json.contains("architecture-variant"));
    }
}
