package org.artifactory.devopsagent.errorcatalog.ingestion;

import org.artifactory.devopsagent.errorcatalog.ErrorCatalogEvent;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxy;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertNotNull;

public class SignatureExtractorTest {

    @Test
    public void testParameterizedEventUsesMessagePattern() {
        LoggingEvent ev = new LoggingEvent();
        ev.setLevel(Level.ERROR);
        ev.setLoggerName("com.example.Foo");
        ev.setMessage("User '{}' not found");
        ev.setArgumentArray(new Object[] {"alice"});
        ev.setTimeStamp(123L);

        ErrorCatalogEvent out = SignatureExtractor.extract(ev);
        assertEquals(out.templateMessage(), "User '{}' not found",
                "Parameterized log statements must use the SLF4J pattern as the template");
        assertEquals(out.rawMessage(), "User 'alice' not found");
        assertEquals(out.severity(), "ERROR");
        assertNotNull(out.signatureHash());
        assertEquals(out.signatureHash().length(), 64);
    }

    @Test
    public void testDifferentArgsProduceSameSignature() {
        LoggingEvent a = parameterizedEvent("alice");
        LoggingEvent b = parameterizedEvent("bob");
        assertEquals(SignatureExtractor.extract(a).signatureHash(),
                SignatureExtractor.extract(b).signatureHash(),
                "Parameterized log statements with different args must dedup");
    }

    @Test
    public void testConcatenatedEventsWithDifferentVariablesDedup() {
        // Two log statements that differ only in variable content (IP, duration) must
        // produce the same dedup signature — that's the whole point of template extraction.
        // The raw IPs must also not leak into the stored template.
        LoggingEvent a = concatenatedEvent("Connection to 192.168.1.5 took 423ms");
        LoggingEvent b = concatenatedEvent("Connection to 10.0.0.1 took 100ms");

        ErrorCatalogEvent outA = SignatureExtractor.extract(a);
        ErrorCatalogEvent outB = SignatureExtractor.extract(b);

        assertEquals(outA.signatureHash(), outB.signatureHash(),
                "Concatenated logs differing only in variable parts must dedup");
        assertFalse(outA.templateMessage().contains("192.168.1.5"),
                "Template must not leak the raw IP");
        assertFalse(outA.templateMessage().contains("423"),
                "Template must not leak the raw duration");
    }

    private static LoggingEvent concatenatedEvent(String message) {
        LoggingEvent ev = new LoggingEvent();
        ev.setLevel(Level.WARN);
        ev.setLoggerName("com.example.Bar");
        ev.setMessage(message);
        ev.setArgumentArray(null);
        ev.setTimeStamp(1L);
        return ev;
    }

    @Test
    public void testDifferentSeveritiesProduceDifferentSignatures() {
        LoggingEvent warnEvent = parameterizedEvent("alice", Level.WARN);
        LoggingEvent errorEvent = parameterizedEvent("alice", Level.ERROR);

        assertNotEquals(SignatureExtractor.extract(warnEvent).signatureHash(),
                SignatureExtractor.extract(errorEvent).signatureHash());
    }

    @Test
    public void testExceptionClassIsCapturedFromThrowable() {
        LoggingEvent ev = parameterizedEvent("alice");
        ev.setThrowableProxy(new ThrowableProxy(new IllegalArgumentException()));
        ErrorCatalogEvent out = SignatureExtractor.extract(ev);
        assertEquals(out.exceptionClass(), "java.lang.IllegalArgumentException");
    }

    private static LoggingEvent parameterizedEvent(String arg) {
        return parameterizedEvent(arg, Level.ERROR);
    }

    private static LoggingEvent parameterizedEvent(String arg, Level level) {
        LoggingEvent ev = new LoggingEvent();
        ev.setLevel(level);
        ev.setLoggerName("com.example.Foo");
        ev.setMessage("User '{}' not found");
        ev.setArgumentArray(new Object[] {arg});
        ev.setTimeStamp(123L);
        return ev;
    }
}
