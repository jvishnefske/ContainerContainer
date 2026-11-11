package org.artifactory.rest.servlet;

import com.fasterxml.jackson.core.PrettyPrinter;
import com.fasterxml.jackson.core.util.Instantiatable;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;

import java.lang.instrument.Instrumentation;

import static net.bytebuddy.matcher.ElementMatchers.isChildOf;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

/**
 * Fixes a concurrency bug in Jackson's DefaultPrettyPrinter when used through Jersey 4.0.2's
 * repackaged Jackson JAX-RS provider.
 * <p>
 * Jersey 4.0.2 bundles jackson-jakarta-rs-providers 2.19.1 code (repackaged under
 * {@code org.glassfish.jersey.jackson.internal.jackson.jaxrs}). That version has a bug
 * where {@code ProviderBase.writeTo()} passes the static {@code DEFAULT_PRETTY_PRINTER}
 * singleton directly to {@code JsonGenerator.setPrettyPrinter()} without cloning it.
 * Under concurrent requests, this corrupts the singleton's nesting depth, producing
 * inconsistent JSON indentation.
 * </p>
 * <p>
 * The fix was shipped in jackson-jakarta-rs-providers 2.19.2
 * (<a href="https://github.com/FasterXML/jackson-jakarta-rs-providers/issues/54">#54</a>),
 * but Jersey's repackaging prevents Artifactory's BOM from overriding it.
 * </p>
 * <p>
 * This hook intercepts {@code JsonGenerator.setPrettyPrinter(PrettyPrinter)} and detects
 * when the caller passes the default {@code DefaultPrettyPrinter} singleton — the same
 * instance that Jersey's {@code ProviderBase.DEFAULT_PRETTY_PRINTER} holds. We capture
 * an identical reference at class-load time in {@link #DEFAULT_PRETTYPRINTER} (obtained
 * from {@link ObjectMapper#getSerializationConfig()}) and use identity comparison to
 * recognise it. When matched, the advice clones it via
 * {@link Instantiatable#createInstance()} before it is stored, ensuring each
 * {@code JsonGenerator} gets its own mutable copy. Other PrettyPrinter instances are
 * passed through unchanged.
 * </p>
 *
 * @see <a href="https://github.com/FasterXML/jackson-jakarta-rs-providers/commit/7c157899">upstream fix</a>
 */
@Slf4j
public class JerseyJacksonPrettyPrinterFix {

    public static final PrettyPrinter DEFAULT_PRETTYPRINTER = new ObjectMapper().getSerializationConfig()
            .getDefaultPrettyPrinter();

    private JerseyJacksonPrettyPrinterFix() {
    }

    public static void install() {
        try {
            log.info("Installing fix for Jackson DefaultPrettyPrinter concurrency bug "
                    + "(jackson-jakarta-rs-providers#54)");
            Instrumentation instrumentation = ByteBuddyAgent.install();

            new AgentBuilder.Default()
                    .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
                    .disableClassFormatChanges()
                    .type(
                            named("com.fasterxml.jackson.core.JsonGenerator"),
                            isChildOf(JerseyJacksonPrettyPrinterFix.class.getClassLoader())
                    )
                    .transform((builder, typeDescription, classLoader, javaModule, protectionDomain) ->
                            builder.visit(
                                    Advice.to(SetPrettyPrinterAdvice.class)
                                            .on(named("setPrettyPrinter").and(takesArguments(1)))
                            )
                    )
                    .installOn(instrumentation);

            log.info("Registered the DefaultPrettyPrinter clone-on-set hook");
        } catch (Exception error) {
            log.error("Failed installing the DefaultPrettyPrinter fix hook", error);
        }
    }

    @SuppressWarnings("unused")
    static class SetPrettyPrinterAdvice {
        @Advice.OnMethodEnter
        static void onEnter(
                @Advice.Argument(value = 0, readOnly = false) PrettyPrinter pp) {
            if (pp == DEFAULT_PRETTYPRINTER && pp instanceof Instantiatable) {
                pp = (PrettyPrinter) ((Instantiatable<?>) pp).createInstance();
            }
        }
    }
}
