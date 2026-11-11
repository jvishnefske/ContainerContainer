package org.artifactory.aql.result;

import com.fasterxml.jackson.core.JsonToken;
import com.github.fge.jackson.JacksonUtils;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.artifactory.aql.model.AqlPermissionProvider;
import org.artifactory.aql.model.AqlRepoProvider;

import java.io.IOException;

import static org.artifactory.aql.util.AqlSecUtils.hideUserRelatedInfoIfNeeded;

@Slf4j
public class AqlJsonFederatedStreamer extends AqlJsonHttpResponseStreamer {

    private final String remoteUrl;

    public AqlJsonFederatedStreamer(@Nonnull CloseableHttpResponse response,
            @Nonnull AqlPermissionProvider permissionProvider,
            @Nonnull AqlRepoProvider repoProvider,
            @Nonnull String remoteUrl) {
        super(response, permissionProvider, repoProvider);
        this.remoteUrl = remoteUrl;
    }

    @Override
    @Nullable
    public Row readNext() { // Permissions must be handed by the caller. Returns all the info.
        if (!this.isFinished) {
            Row currRow = null;
            try {
                log.trace("Reading Row for the remote url {}", remoteUrl);
                currRow = this.readRow();
            } catch (IOException e) {
                log.error("Failed to read Aql result: {}", e.getMessage(), e);
            }

            if (currRow != null) {
                return currRow;
            }
            this.isFinished = true;
        }

        return null;
    }

    @Nullable
    Row readRow() throws IOException {
        Row currRow = null;
        if (this.parser.currentToken() == JsonToken.END_ARRAY) {
            return null;
        }

        if (this.parser.nextToken() != JsonToken.END_ARRAY) {
            currRow = JacksonUtils.getReader().readValue(this.parser, Row.class);
        }
        return hideUserRelatedInfoIfNeeded(currRow);
    }

}
