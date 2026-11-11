package org.artifactory.aql.result;

import com.fasterxml.jackson.core.JsonToken;
import com.github.fge.jackson.JacksonUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.artifactory.aql.model.AqlDomainEnum;
import org.artifactory.aql.model.AqlPermissionProvider;
import org.artifactory.aql.model.AqlRepoProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import static org.artifactory.aql.util.AqlSecUtils.hideUserRelatedInfoIfNeeded;
import static org.artifactory.repo.RepoPath.REMOTE_CACHE_SUFFIX;

/**
 * This is the streamer for remote results received from a smart remote repo with the sent AqlQuery
 * The readNext() will read the next json object and return it
 *
 * @author Tomer Slutsky
 */
public class AqlJsonRemoteStreamer extends AqlJsonHttpResponseStreamer {
    private static final Logger log = LoggerFactory.getLogger(AqlJsonRemoteStreamer.class);

    private final String baseRepoName;

    public AqlJsonRemoteStreamer(CloseableHttpResponse response, AqlPermissionProvider permissionProvider,
            AqlRepoProvider repoProvider, String baseRepoName) {
        super(response, permissionProvider, repoProvider);
        this.baseRepoName = baseRepoName;
    }

    @Override
    public Row readNext() {
        if (!this.isFinished) {
            Row currRow = null;
            try {
                log.trace("Reading Row for the remote streamer for the repo: {}", this.baseRepoName);
                currRow = this.readRow();
                while (currRow != null &&
                        !this.canRead(AqlDomainEnum.items, currRow.itemRepo + REMOTE_CACHE_SUFFIX, currRow.itemPath,
                                currRow.itemName)) {
                    log.trace("The previous Row had no permissions for the following cache: {}, reading new Row",
                            currRow.itemRepo + REMOTE_CACHE_SUFFIX);
                    if (this.parser.currentToken() == JsonToken.END_ARRAY) {
                        currRow = null;
                        break;
                    }
                    currRow = this.readRow();
                }
            } catch (IOException e) {
                log.error("Failed to read Aql result: {}", e.getMessage(), e);
            }

            if (currRow != null) {
                return hideUserRelatedInfoIfNeeded(currRow);
            }
            log.trace("Read final row from remote streamer for the repo: {}", this.baseRepoName);
            this.isFinished = true;
        }

        return null;
    }

    private Row readRow() throws IOException {
        Row currRow = null;
        if (this.parser.currentToken() == JsonToken.END_ARRAY) {
            return null;
        }

        if (this.parser.nextToken() != JsonToken.END_ARRAY) {
            currRow = JacksonUtils.getReader().readValue(this.parser, Row.class);
            if (currRow != null) {
                currRow.itemRepo = this.baseRepoName;
            }
        }

        return currRow;
    }

    public String getBaseRepoName() {
        return baseRepoName;
    }

}
