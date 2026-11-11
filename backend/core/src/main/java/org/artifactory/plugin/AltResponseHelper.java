package org.artifactory.plugin;

import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.plugin.PluginsAddon;
import org.artifactory.addon.plugin.ResponseCtx;
import org.artifactory.addon.plugin.download.AltAllResponsesAction;
import org.artifactory.addon.plugin.download.AltResponseAction;
import org.artifactory.addon.worker.WorkersAddon;
import org.artifactory.api.request.ArtifactoryResponse;
import org.artifactory.io.SimpleResourceStreamHandle;
import org.artifactory.io.StringResourceStreamHandle;
import org.artifactory.repo.RepoPath;
import org.artifactory.request.RepoRequests;
import org.artifactory.request.Request;
import org.artifactory.request.RequestContext;
import org.artifactory.resource.ResourceStreamHandle;
import org.artifactory.unifiedevent.constants.SupportedUnifiedEvents;
import org.jfrog.worker.proto.execution.Action;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import static org.artifactory.util.BasicHttpUtils.isRedirectionResponseCode;
import static org.artifactory.util.BasicHttpUtils.isSuccessfulResponseCode;

public class AltResponseHelper {
    private static final Logger log = LoggerFactory.getLogger(AltResponseHelper.class);

    private final AddonsManager addonsManager;

    public AltResponseHelper(AddonsManager addonsManager) {
        this.addonsManager = addonsManager;
    }

    /**
     * Executes any subscribing user plugin routines and returns an alternate resource handle if given
     *
     * @param requestContext   Context
     * @param response         Response to return
     * @param responseRepoPath Actual repo path of the requested artifact
     * @return Stream handle if return by plugins. Null if not.
     * @deprecated use alternateResponse(RequestContext, ArtifactoryResponse, RepoPath, int status) instead, which
     * calls AltAllResponsesAction
     */
    @Deprecated
    public ResourceStreamHandle getAlternateHandle(RequestContext requestContext, ArtifactoryResponse response,
            RepoPath responseRepoPath) throws IOException {
        //See if we need to return an alternate response

        RepoRequests.logToContext("Executing any AltResponse user plugins that may exist");
        PluginsAddon pluginAddon = addonsManager.addonByType(PluginsAddon.class);
        ResponseCtx responseCtx = new ResponseCtx();
        pluginAddon.execPluginActions(AltResponseAction.class, responseCtx, requestContext.getRequest(),
                responseRepoPath);
        runWorkers(Action.ALT_RESPONSE, responseCtx, requestContext.getRequest());
        int status = responseCtx.getStatus();
        String message = responseCtx.getMessage();
        RepoRequests.logToContext("Alternative response status is set to %s and message to '%s'", status, message);
        if (status != ResponseCtx.UNSET_STATUS) {
            setResponseHeaders(response, responseCtx.getHeaders());

            if (isSuccessfulResponseCode(status) || isRedirectionResponseCode(status)) {
                RepoRequests.logToContext("Setting response status to %s", status);
                response.setStatus(status);
                if (message != null) {
                    RepoRequests.logToContext("Found non-null alternative response message - " +
                            "returning as content handle");
                    return new StringResourceStreamHandle(message);
                }
            } else {
                RepoRequests.logToContext("Sending error response with alternative status and message");
                response.sendError(status, message, log);
                return null;
            }
        }
        InputStream is = responseCtx.getInputStream();
        if (is != null) {
            RepoRequests.logToContext("Found non-null alternative response content stream - " +
                    "returning as content handle");
            return new SimpleResourceStreamHandle(is, responseCtx.getSize());
        }
        RepoRequests.logToContext("Found no alternative content handles");
        return null;
    }

    /**
     * Executes any subscribing user plugin routines and returns an alternate resource handle if given, for a found
     * resource
     *
     * @param requestContext   Context
     * @param response         Response to return
     * @param responseRepoPath Actual repo path of the requested artifact
     * @return Stream handle if returned by plugins, else null.
     */
    public ResourceStreamHandle alternateResponse(RequestContext requestContext, ArtifactoryResponse response,
            RepoPath responseRepoPath, int intendedStatus) throws IOException {

        RepoRequests.logToContext("Executing any AltAllResponses user plugins that may exist");
        PluginsAddon pluginAddon = addonsManager.addonByType(PluginsAddon.class);
        ResponseCtx responseCtx = new ResponseCtx();
        responseCtx.setStatus(intendedStatus);
        pluginAddon.execPluginActions(AltAllResponsesAction.class, responseCtx, requestContext.getRequest(),
                responseRepoPath);
        addonsManager.addonByType(WorkersAddon.class).run(
                SupportedUnifiedEvents.ALT_ALL_RESPONSES, responseCtx, requestContext.getRequest());
        setResponseHeaders(response, responseCtx.getHeaders());

        int status = responseCtx.getStatus();
        String message = responseCtx.getMessage();
        RepoRequests.logToContext("Alternative response status is set to %s and message to '%s'", status, message);
        if (status != intendedStatus) {
            if (isSuccessfulResponseCode(status) || isRedirectionResponseCode(status)) {
                RepoRequests.logToContext("Setting response status to %s", status);
                response.setStatus(status);
            } else {
                RepoRequests.logToContext("Sending error response with alternative status and message");
                response.sendError(status, message, log);
                return null;
            }
        }
        if (message != null) {
            RepoRequests.logToContext("Found non-null alternative response message - " +
                    "returning as content handle");
            return new StringResourceStreamHandle(message);
        }
        if (responseCtx.getInputStream() != null) {
            RepoRequests.logToContext("Found non-null alternative response content stream - " +
                    "returning as content handle");
            return new SimpleResourceStreamHandle(responseCtx.getInputStream(), responseCtx.getSize());
        }

        RepoRequests.logToContext("Found no alternative content handles");
        return null;
    }

    private void setResponseHeaders(ArtifactoryResponse response, Map<String, String> headers) {
        if (headers != null && !headers.isEmpty()) {
            RepoRequests.logToContext("Found non-null alternative response headers");
            for (Map.Entry<String, String> headerEntry : headers.entrySet()) {
                response.setHeader(headerEntry.getKey(), headerEntry.getValue());
            }
        }
    }

    void runWorkers(Action action, ResponseCtx ctx, Request request) {
        addonsManager.addonByType(WorkersAddon.class)
                .runWorkerAutoInjectedSecurityAndRepoConfig(action, ctx, request);
    }
}
