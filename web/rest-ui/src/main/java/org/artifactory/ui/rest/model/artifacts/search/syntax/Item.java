package org.artifactory.ui.rest.model.artifacts.search.syntax;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.artifactory.api.util.LongFromDateDeserializer;
import org.artifactory.api.util.LongToDateUtcSerializer;

import java.util.List;

/**
 * @author Igor Usenko
 */
public record Item(
        String repoKey,
        String path,
        String text,
        String repoPkgType,
        String repoType,
        String downloadUrl,
        String fullPath,
        long size,
        @JsonSerialize(using = LongToDateUtcSerializer.class)
        @JsonDeserialize(using = LongFromDateDeserializer.class)
        Long modified,
        @JsonSerialize(using = LongToDateUtcSerializer.class)
        @JsonDeserialize(using = LongFromDateDeserializer.class)
        Long created,
        Type itemType,
        List<String> actions
) {
}
