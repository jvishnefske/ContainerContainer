package org.artifactory.addon.curation;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import org.artifactory.common.repo.PackageType;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Deserializes {@code forceCVSPackages} from JSON string arrays, ignoring unknown values.
 */
public class ForceCVSPackagesDeserializer extends JsonDeserializer<Set<PackageType>> {

    @Override
    public Set<PackageType> deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        JsonNode node = parser.getCodec().readTree(parser);
        if (node == null || node.isNull()) {
            return Set.of();
        }
        if (!node.isArray()) {
            return Set.of();
        }
        List<String> rawValues = new ArrayList<>();
        for (JsonNode element : node) {
            if (element != null && element.isTextual()) {
                rawValues.add(element.asText());
            }
        }
        return ForceCVSPackagesParser.fromStrings(rawValues);
    }
}
