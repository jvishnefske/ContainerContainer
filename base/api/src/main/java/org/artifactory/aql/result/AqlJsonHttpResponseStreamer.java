package org.artifactory.aql.result;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.artifactory.aql.model.AqlPermissionProvider;
import org.artifactory.aql.model.AqlRepoProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.HttpURLConnection;

/**
 * This is an abstract streamer for http calls results received from a remote server*
 *
 * @author Omri Ziv
 */
public abstract class AqlJsonHttpResponseStreamer extends AqlRestResult implements AqlJsonStreamer {
    private static final Logger log = LoggerFactory.getLogger(AqlJsonHttpResponseStreamer.class);

    private final CloseableHttpResponse response;
    protected JsonParser parser;
    protected boolean isFinished;

    protected AqlJsonHttpResponseStreamer(CloseableHttpResponse response, AqlPermissionProvider permissionProvider,
            AqlRepoProvider repoProvider) {
        super(permissionProvider, repoProvider, null);
        this.response = response;
        this.isFinished = response.getStatusLine().getStatusCode() != HttpURLConnection.HTTP_OK;
        if (!this.isFinished) {
            this.initJsonParser();
            this.skipResultWrapper();
        }
    }

    public CloseableHttpResponse getResponse() {
        return this.response;
    }

    public void initJsonParser() {
        try {
            log.trace("The status code for the remote response is: {}", this.response.getStatusLine().getStatusCode());
            this.parser = factory.createParser(this.response.getEntity().getContent());
        } catch (IOException e) {
            log.error("Failed to initiate Aql JsonParser: {}", e.getMessage());
        }
    }

    @Override
    public void close() throws IOException {
        if (this.parser != null) {
            this.parser.close();
        } else {
            this.response.close();
        }
    }

    private void skipResultWrapper() {
        JsonToken token;
        try {
            token = this.parser.nextToken();

            while (token != JsonToken.START_ARRAY) {
                token = this.parser.nextToken();
            }

            this.parser.nextToken();
        } catch (IOException e) {
            log.error("Failed to skip Aql result wrapper: {}", e.getMessage(), e);
        }
    }

    @Override
    public byte[] read() {
        return null;
    }

    @Override
    public byte[] readCompact() {
        return null;
    }

    @Override
    public long getRowsCount() {
        // Not implemented
        return 0;
    }
}
