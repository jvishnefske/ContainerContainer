package org.artifactory.ui.rest.service.artifacts.browse.treebrowser.tree;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.common.ConstantValues;
import org.artifactory.rest.common.model.RestModel;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.RestTreeNode;
import org.jfrog.commons.continues.ContinueResult;
import org.springframework.stereotype.Component;

import jakarta.ws.rs.HttpMethod;
import jakarta.ws.rs.core.StreamingOutput;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;

/**
 * @author Alshqerat Hussein
 */
@Component
@Slf4j
public class BrowseTreeNodesV2Service implements RestService<RestTreeNode> {
    @Override
    public void execute(ArtifactoryRestRequest<RestTreeNode> request, RestResponse response) {
        boolean isCompact = Boolean.parseBoolean(request.getQueryParamByKey("compacted")) &&
                ConstantValues.treebrowserFolderCompact.getBoolean();
        boolean isArchiveRequest = isArchiveRequest(request);
        ContinueResult<? extends RestModel> items = request.getImodel().fetchItemTypeData(isCompact, isArchiveRequest);
        if (isArchiveRequest) {
            StreamingOutput streamingOutput = os -> write(items, os);
            response.iModel(streamingOutput);
            return;
        }
        response.iModel(items);
    }

    void write(ContinueResult<? extends RestModel> items, OutputStream os) throws IOException {
        Writer writer = new BufferedWriter(new OutputStreamWriter(os));
        ObjectWriter jsonObjectWriter = new ObjectMapper()
                .setSerializationInclusion(JsonInclude.Include.NON_NULL)
                .setAnnotationIntrospector(new JacksonAnnotationIntrospector())
                .writer();
        jsonObjectWriter.writeValue(writer, items);
    }

    boolean isArchiveRequest(ArtifactoryRestRequest<RestTreeNode> request) {
        return request.getRequest().getMethod().equalsIgnoreCase(HttpMethod.POST) &&
                request.getImodel().isArchiveExpendRequest();
    }
}
