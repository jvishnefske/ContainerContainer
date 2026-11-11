package org.artifactory.rest.common.provider;

import com.fasterxml.jackson.core.PrettyPrinter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class JsonProviderTest {

    /**
     * The canonical Jackson singleton obtained from SerializationConfig.DEFAULT_PRETTY_PRINTER
     * (a protected static final field, accessible via getDefaultPrettyPrinter()).
     */
    private static final PrettyPrinter JACKSON_DEFAULT_PRETTY_PRINTER =
            new ObjectMapper().getSerializationConfig().getDefaultPrettyPrinter();

    @Test
    void getContextShouldReturnJacksonDefaultPrettyPrinterSingleton() {
        JsonProvider jsonProvider = new JsonProvider();
        ObjectMapper mapper = jsonProvider.getContext(Object.class);

        PrettyPrinter prettyPrinter = mapper.getSerializationConfig().getDefaultPrettyPrinter();
        assertNotNull(prettyPrinter, "PrettyPrinter should not be null when INDENT_OUTPUT is enabled");
        assertInstanceOf(DefaultPrettyPrinter.class, prettyPrinter,
                "PrettyPrinter should be Jackson's DefaultPrettyPrinter, not a stub or wrapper");
        assertSame(JACKSON_DEFAULT_PRETTY_PRINTER, prettyPrinter,
                "PrettyPrinter should be the same singleton instance as SerializationConfig.DEFAULT_PRETTY_PRINTER");
    }
}
