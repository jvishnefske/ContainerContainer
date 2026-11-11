package org.artifactory.schedule.aop;

import lombok.SneakyThrows;
import org.aopalliance.intercept.MethodInvocation;
import org.artifactory.activity.ActivityConstants;
import org.artifactory.activity.ActivityDetails;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.security.util.SimpleMethodInvocation;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;


class TraceableMethodInvocationTest {
    @Test
    @SuppressWarnings("unchecked")
    void proceed() throws Throwable {
        MethodInvocation inner = new TestInvocation(this, getClass().getMethod("sampleMethod"));

        TraceableMethodInvocation invocation = new TraceableMethodInvocation(inner, "test");
        Map<String,String> actual = (Map<String,String>)invocation.proceed();
        assertEquals("async", actual.get(ActivityConstants.ACTIVITY_PROP_KIND));
        assertEquals("test", actual.get(ActivityConstants.ACTIVITY_PROP_GROUP));
        assertEquals("TraceableMethodInvocationTest.sampleMethod", actual.get(ActivityConstants.ACTIVITY_PROP_NAME));
    }

    @Test
    @SuppressWarnings("unchecked")
    void invoke() throws InvocationTargetException, IllegalAccessException, NoSuchMethodException {
        MethodInvocation inner = new TestInvocation(this, getClass().getMethod("sampleMethod"));

        TraceableMethodInvocation invocation = new TraceableMethodInvocation(inner, "test");
        Map<String,String> actual = (Map<String,String>)invocation.invoke();
        assertEquals("async", actual.get(ActivityConstants.ACTIVITY_PROP_KIND));
        assertEquals("test", actual.get(ActivityConstants.ACTIVITY_PROP_GROUP));
        assertEquals("TraceableMethodInvocationTest.sampleMethod", actual.get(ActivityConstants.ACTIVITY_PROP_NAME));
    }

    @ActivityDetails(group = "test")
    public Map<String,String> sampleMethod() {
        return MDC.getCopyOfContextMap();
    }

    static class TestInvocation extends SimpleMethodInvocation {
        public TestInvocation(Object targetObject, Method method, Object... arguments) {
            super(targetObject, method, arguments);
        }

        @SneakyThrows
        @Override
        public Object proceed() {
            return this.getMethod().invoke(this.getThis(), this.getArguments());
        }
    }
}