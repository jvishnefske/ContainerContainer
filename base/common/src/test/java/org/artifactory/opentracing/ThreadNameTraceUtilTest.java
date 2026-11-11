package org.artifactory.opentracing;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.artifactory.opentracing.ArtifactoryTracer.UBER_TRACE_ID;
import static org.artifactory.opentracing.ThreadNameTraceUtil.enhanceThreadName;
import static org.artifactory.opentracing.ThreadNameTraceUtil.getOriginalThreadName;
import static org.fest.assertions.Assertions.assertThat;

/**
 * @author avishayh
 */
class ThreadNameTraceUtilTest {
    private static final String NAME = "Thread_XXX";

    private static String THREAD_NAME_BEFORE_TEST;

    @BeforeAll
    public static void before() {
        THREAD_NAME_BEFORE_TEST = Thread.currentThread().getName();
    }

    @AfterAll
    public static void after() {
        Thread.currentThread().setName(THREAD_NAME_BEFORE_TEST);
    }

    @BeforeEach
    public void setThreadName() {
        Thread thread = Thread.currentThread();
        thread.setName(NAME);
        assertThat(thread.getName()).isEqualTo(NAME);
        assertThat(getOriginalThreadName()).isEqualTo(NAME);
    }

    @Test
    public void checkContainTraceIdValue() {
        MDC.put(UBER_TRACE_ID, "5bceffe372f705cb");
        enhanceThreadName();
        Thread thread = Thread.currentThread();
        assertThat(thread.getName()).isNotEqualTo(NAME);
        assertThat(thread.getName()).contains("5bceffe372f705cb");
    }

    @Test
    public void enhanceThreadNameWithEmptyString() {
        enhanceThreadName("");
        Thread thread = Thread.currentThread();
        assertThat(thread.getName()).isNotEqualTo(NAME);
        assertThat(thread.getName()).endsWith(NAME);
        //expecting 3 parts in thread name (now,trace,original)
        assertThat(thread.getName().split("\\|").length).isEqualTo(3);
    }

    @Test
    public void enhanceThreadNameWithNull() {
        enhanceThreadName();
        Thread thread = Thread.currentThread();
        assertThat(thread.getName()).isNotEqualTo(NAME);
        assertThat(thread.getName()).endsWith(NAME);
        //expecting 3 parts in thread name (now,trace,original)
        assertThat(thread.getName().split("\\|").length).isEqualTo(3);
    }

    @Test
    public void enhanceThreadNameWithValue() {
        enhanceThreadName("real-value");
        Thread thread = Thread.currentThread();
        assertThat(thread.getName()).isNotEqualTo(NAME);
        assertThat(thread.getName()).endsWith(NAME);
        //expecting 4 parts in thread name (now,trace,passed-value,original)
        assertThat(thread.getName().split("\\|").length).isEqualTo(4);
    }

    @Test
    public void verifyExtractionOfThreadName() {
        //change name before enhancing
        final String testThreadName = "different-Thread-Name";
        Thread.currentThread().setName(testThreadName);
        //verify new name extraction
        assertThat(getOriginalThreadName()).isEqualTo(testThreadName);
        //enhance and re-verify
        enhanceThreadName();
        assertThat(getOriginalThreadName()).isEqualTo(testThreadName);
        assertThat(Thread.currentThread().getName()).isNotEqualTo(testThreadName);
    }

    @Test
    public void verifyExtractionOfThreadNameWithExtraInfo() {
        //verify new thread name
        enhanceThreadName("local-repo/path/to/file.gz");
        assertThat(Thread.currentThread().getName()).isNotEqualTo(NAME);
        assertThat(getOriginalThreadName()).isEqualTo(NAME);
    }

    @Test
    public void verifyExtractionOfThreadNameWithInfoWithSpecialCharacters() {
        //verify new thread name with special characters including a separator
        enhanceThreadName("123 |abc_-<>/\\!@#$%^&*()|def");
        assertThat(Thread.currentThread().getName()).isNotEqualTo(NAME);
        assertThat(getOriginalThreadName()).isEqualTo(NAME);
    }

}