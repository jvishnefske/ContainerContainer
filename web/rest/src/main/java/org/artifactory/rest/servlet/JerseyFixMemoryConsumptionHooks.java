package org.artifactory.rest.servlet;

import lombok.extern.slf4j.Slf4j;
import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.implementation.bind.annotation.Argument;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import net.bytebuddy.implementation.bind.annotation.SuperCall;

import jakarta.ws.rs.core.MediaType;
import java.lang.instrument.Instrumentation;
import java.util.Set;
import java.util.concurrent.Callable;

import static net.bytebuddy.matcher.ElementMatchers.isChildOf;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

/**
 * The code of the Jersey server has a built-in problem, which leads to creation of 5 million needless
 * objects (routes metadata) that waste around 128 MB pf memory. This class fixes this problem.
 * <p>The problem occurs when the internal
 * {@link org.glassfish.jersey.server.internal.routing.MethodSelectingRouter#addAllConsumesProducesCombinations}
 * method tries to build all possible combinations of the input (request body) MIME types and the
 * output (response body) MIME types - for each Resource (we have about 260 of them) * number of routes (usually 3).
 * One could say that we support only one or two MIME types, but unfortunately, the MethodSelectingRouter thinks
 * differently.  For any method that is not annotated with corresponding <code>@Consumes</code> /
 * <code>@Produces</code> annotation, it takes all MIME types available in the Jersey (40 input MIME types,
 * 63 output MIME types). This is relevant for all OPTIONS handlers that Jersey automatically injects for every
 * resource. It also happens for all REST APIs that are not supposed to receive any request body (GET, DELETE, etc)
 * and for all APIs that are not supposed to return any response body (DELETE).
 * </p>
 * <p>
 * The fix is not very clean - we dynamically hook the
 * {@link org.glassfish.jersey.server.internal.routing.MethodSelectingRouter#fillMediaTypes} function, which is a
 * private method within a package-private class :(
 * </p>
 */
@SuppressWarnings("JavadocReference")
@Slf4j
public class JerseyFixMemoryConsumptionHooks {
    private JerseyFixMemoryConsumptionHooks() {
    }

    public static void install() {
        try {
            log.info("Install the ByteBuddy Java agent");
            Instrumentation instrumentation = ByteBuddyAgent.install();

            log.info("Hook the Jersey server initialization, improving memory consumption");
            new AgentBuilder.Default()
                    .type(
                            named("org.glassfish.jersey.server.internal.routing.AbstractMethodSelectingRouter"),
                            // We had troubles when Artifactory shared Tomcat with the MissionControl - sometimes
                            // org.apache.catalina.core failed to load the org.jfrog.mc.JerseyConfig, saying that
                            // it cannot find our target class (JerseyFixMemoryConsumptionHooks). See RTDEV-35367.
                            //
                            // We are going to solve this problem by intercepting only classes, coming from the
                            // current class loader. We also moved fillMediaTypes() proxy to this class and eliminated
                            // needless nested class, hoping that it simplify the class resolving/
                            isChildOf(JerseyFixMemoryConsumptionHooks.class.getClassLoader())
                    )
                    .transform((builder, typeDescription, classLoader, javaModule, protectionDomain) ->
                            builder.method(named("fillMediaTypes")
                                            // Minimize our chances to fail if Jersey changes the signature of
                                            // this private function within its package-private class.
                                            // The following filters ensure that our hook will not break anything.
                                            // In the worst case it will simply be ignored (and then the memory
                                            // consumption will grow again.
                                            .and(returns(boolean.class))
                                            .and(takesArgument(0, Set.class))
                                    )
                                    .intercept(MethodDelegation.to(JerseyFixMemoryConsumptionHooks.class))
                    )
                    .installOn(instrumentation);
            log.info("Registered the fillMediaTypes hook");
        } catch (Exception error) {
            log.error("Failed installing the fillMediaTypes hook for the Jersey server", error);
        }
    }

    /**
     * The ByteBuddy calls this interceptor method when somebody calls the
     * {@link org.glassfish.jersey.server.internal.routing.MethodSelectingRouter#fillMediaTypes} method.
     * We call the original implementation and inspect the result.  If the original implementation returns true,
     * then it exploded the effectiveTypes set with all MIME types it is aware of.  In this case we will
     * simply replace that long list with a single wildcard.  Otherwise, if the original implementation returns
     * list of the MIME types, it read from the <code>@Consumes</code> / <code>@Produces</code> annotations,
     * we will not interfere and return immediately.
     *
     * @param effectiveTypes - The set of media types relevant for the requested resource handler
     * @param superCall      - The callable function, which allows executing the original fillMediaTypes method.
     * @return A boolean, specifying whether the set of media types was explicitly defined by annotations (false)
     * or it was populated from all MIME types that Jersey server is aware of.
     */
    @RuntimeType
    public static Object fillMediaTypes(
            @Argument(0) Set<MediaType> effectiveTypes,
            @SuperCall Callable<Object> superCall) throws Exception {

        // Call the default implementation first:
        boolean mediaTypesFromWorkers = (boolean) superCall.call();

        // If the list of media types was populated with all MIME types that Jersey server is aware of,
        // replace them with a single wildcard (which is still required to avoid 415 error if handler has
        // no entity parameters and avoid 415 error when handler returns void).
        if (mediaTypesFromWorkers) {
            effectiveTypes.clear();
            effectiveTypes.add(MediaType.WILDCARD_TYPE);
        }
        return mediaTypesFromWorkers;
    }
}
