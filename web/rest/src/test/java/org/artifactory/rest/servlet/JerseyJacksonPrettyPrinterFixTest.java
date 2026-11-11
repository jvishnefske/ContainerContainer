package org.artifactory.rest.servlet;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.PrettyPrinter;
import com.fasterxml.jackson.core.util.MinimalPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.agent.builder.ResettableClassFileTransformer;
import net.bytebuddy.asm.Advice;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.lang.instrument.Instrumentation;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

class JerseyJacksonPrettyPrinterFixTest {

    private static ResettableClassFileTransformer transformer;
    private static Instrumentation instrumentation;

    @BeforeAll
    static void installHook() {
        instrumentation = ByteBuddyAgent.install();

        transformer = new AgentBuilder.Default()
                .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
                .type(named("com.fasterxml.jackson.core.JsonGenerator"))
                .transform((builder, typeDescription, classLoader, module, protectionDomain) ->
                        builder.visit(
                                Advice.to(JerseyJacksonPrettyPrinterFix.SetPrettyPrinterAdvice.class)
                                        .on(named("setPrettyPrinter").and(takesArguments(1)))
                        )
                )
                .installOn(instrumentation);
    }

    @AfterAll
    static void uninstallHook() {
        if (transformer != null) {
            transformer.reset(
                    instrumentation,
                    AgentBuilder.RedefinitionStrategy.RETRANSFORMATION);
        }
    }

    @Test
    void setPrettyPrinter_shouldCloneSingleton() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);

        PrettyPrinter singleton = mapper.getSerializationConfig()
                .getDefaultPrettyPrinter();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        JsonGenerator gen = mapper.getFactory()
                .createGenerator(out, JsonEncoding.UTF8);
        gen.setPrettyPrinter(singleton);

        PrettyPrinter actual = gen.getPrettyPrinter();
        assertNotSame(singleton, actual,
                "setPrettyPrinter should store a clone, not the singleton");
        gen.close();
    }

    @Test
    void setPrettyPrinter_shouldPassThroughNonInstantiatable()
            throws Exception {
        PrettyPrinter custom = new MinimalPrettyPrinter();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ObjectMapper mapper = new ObjectMapper();
        JsonGenerator gen = mapper.getFactory()
                .createGenerator(out, JsonEncoding.UTF8);
        gen.setPrettyPrinter(custom);

        PrettyPrinter actual = gen.getPrettyPrinter();
        assertSame(custom, actual,
                "MinimalPrettyPrinter is not the singleton, "
                        + "so it should be passed through unchanged");
        gen.close();
    }
}
