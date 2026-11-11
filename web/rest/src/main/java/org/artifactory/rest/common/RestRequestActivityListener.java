package org.artifactory.rest.common;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.activity.ActivityDetails;
import org.artifactory.activity.CurrentActivity;
import org.glassfish.jersey.server.ExtendedUriInfo;
import org.glassfish.jersey.server.model.ResourceMethod;
import org.glassfish.jersey.server.monitoring.ApplicationEvent;
import org.glassfish.jersey.server.monitoring.ApplicationEventListener;
import org.glassfish.jersey.server.monitoring.RequestEvent;
import org.glassfish.jersey.server.monitoring.RequestEventListener;
import org.glassfish.jersey.uri.UriTemplate;
import org.springframework.stereotype.Component;

import jakarta.ws.rs.ext.Provider;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Tracks HTTP requests, processed by Jersey framework, and updates the current activity details, extracting them
 * from the {@link ActivityDetails} annotations.
 */
@Component
@Provider
@Slf4j
public class RestRequestActivityListener implements ApplicationEventListener, RequestEventListener {
    private final ConcurrentMap<Method, ConcurrentMap<Class<?>, Collection<ActivityDetails>>> detailsCache
            = new ConcurrentHashMap<>();

    @Override
    public void onEvent(ApplicationEvent applicationEvent) {
        // We do not really plan to handle application initialization / termination requests
        // We implemented the ApplicationEventListener interface just to override the onRequest().
    }

    @Override
    public RequestEventListener onRequest(RequestEvent requestEvent) {
        return this; // Instructs Jersey server to notify us about HTTP requests being processed
    }

    @Override
    public void onEvent(RequestEvent requestEvent) {
        try {
            switch (requestEvent.getType()) {
                case LOCATOR_MATCHED ->
                        updateActivityForLocator(requestEvent.getUriInfo().getMatchedResourceLocators());
                case RESOURCE_METHOD_START ->
                        updateActivityForResource(requestEvent.getUriInfo());
                default -> {  /* We are not interested in other events */ }
            }
        } catch (Exception e) {
            // Swallow this exception. Errors, related to observability, should never interrupt the business login
            log.warn("Failed inspecting Jersey resource (handler)", e);
        }
    }

    protected void updateActivityForLocator(List<ResourceMethod> locators) {
        locators.forEach(this::updateActivityForResourceMethod);
    }

    protected void updateActivityForResource(ExtendedUriInfo uriInfo) {
        CurrentActivity.NAME.put(getMatchingPattern(uriInfo));
        updateActivityForResourceMethod(uriInfo.getMatchedResourceMethod());
    }

    protected void updateActivityForResourceMethod(ResourceMethod method) {
        getDetailsFor(method).forEach(CurrentActivity::applyOnCurrentActivity);
    }

    protected Collection<ActivityDetails> getDetailsFor(ResourceMethod method) {
        Method handler = method == null ? null : method.getInvocable().getHandlingMethod();
        if (handler == null) {
            return List.of(); // We should never get here
        }
        Class<?> type = method.getInvocable().getHandler().getHandlerClass();
        return this.detailsCache
                .computeIfAbsent(handler, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(type, k -> collectClassAndMethodDetails(type, handler));
    }

    private static Collection<ActivityDetails> collectClassAndMethodDetails(Class<?> type, Method method) {
        Set<ActivityDetails> classDetails = CurrentActivity.findActivityDetails(type);
        Set<ActivityDetails> handlerDetails = CurrentActivity.findActivityDetails(method);

        if (classDetails.isEmpty()) {
            return handlerDetails;
        } else if (handlerDetails.isEmpty()) {
            return classDetails;
        }
        List<ActivityDetails> result = new ArrayList<>(classDetails.size() + handlerDetails.size());
        result.addAll(classDetails);
        result.addAll(handlerDetails);
        return result;
    }

    static String getMatchingPattern(ExtendedUriInfo uriInfo) {
        StringBuilder builder = new StringBuilder();
        builder.append(uriInfo.getBaseUri().getPath());

        List<UriTemplate> templates = uriInfo.getMatchedTemplates();
        for (int i = templates.size() - 1; i >= 0; i--) {
            String template = templates.get(i).getTemplate();
            // Prevent duplicate slashes
            if (template.startsWith("/")) {
                removeTrailingSlash(builder);
            }
            builder.append(template);
        }

        // Remove the trailing slash
        if (builder.length() > 1) {
            removeTrailingSlash(builder);
        }

        return builder.toString();
    }

    static void removeTrailingSlash(StringBuilder builder) {
        if (!builder.isEmpty() && builder.charAt(builder.length() - 1) == '/') {
            builder.setLength(builder.length() - 1);
        }
    }
}
