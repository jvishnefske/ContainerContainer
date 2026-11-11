package org.artifactory.concurrent;

import lombok.AllArgsConstructor;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.opentracing.ArtifactoryTracer;
import org.artifactory.opentracing.ServerTracingSpan;
import org.artifactory.opentracing.TraceableCallableDecorator;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.concurrent.Callable;

/**
 * @author Uriah Levy
 */
@AllArgsConstructor
public class ArtifactoryCallable<V> implements Callable<V> {
    private final Callable<V> delegate;
    private ArtifactoryContext context;
    private final Authentication authentication;
    private ArtifactoryTracer tracer;
    private ServerTracingSpan currentSpan;


    public ArtifactoryCallable(Callable<V> delegate, ArtifactoryContext context, Authentication authentication) {
        this.delegate = delegate;
        this.context = context;
        this.authentication = authentication;
        this.tracer = context.beanForType(ArtifactoryTracer.class);
        this.currentSpan = tracer.getCurrentThreadActiveSpan();
    }

    @Override
    public V call() throws Exception {
        try {
            ArtifactoryContextThreadBinder.bind(context);
            ArtifactoryHome.bind(context.getArtifactoryHome());
            addAuthIfNotNull();
            return new TraceableCallableDecorator<>(delegate, tracer, currentSpan)
                    .call();
        } finally {
            if (context.isReady()) {
                SecurityContextHolder.clearContext();
            }
            ArtifactoryContextThreadBinder.unbind();
            ArtifactoryHome.unbind();
        }
    }

    private void addAuthIfNotNull() {
        if (authentication != null) {
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }
    }
}
