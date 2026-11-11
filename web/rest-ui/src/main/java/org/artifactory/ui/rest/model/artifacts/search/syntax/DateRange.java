package org.artifactory.ui.rest.model.artifacts.search.syntax;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.artifactory.api.util.LongFromDateDeserializer;
import org.artifactory.api.util.LongToDateUtcSerializer;

/**
 * @author Amir Avidan
 */
public record DateRange(
        @JsonSerialize(using = LongToDateUtcSerializer.class)
        @JsonDeserialize(using = LongFromDateDeserializer.class)
        Long from,
        @JsonSerialize(using = LongToDateUtcSerializer.class)
        @JsonDeserialize(using = LongFromDateDeserializer.class)
        Long to
) {
    public boolean isEmpty() {
        return !hasFrom() && !hasTo();
    }

    public boolean hasFrom() {
        return from != null && from > 0;
    }

    public boolean hasTo() {
        return to != null && to > 0;
    }
}

