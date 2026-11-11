package org.artifactory.sensitive.fields;

import org.artifactory.api.config.DynamicSensitiveValueHandler;
import org.artifactory.sensitive.DynamicSensitive;
import org.artifactory.sensitive.Sensitive;
import org.jfrog.common.config.diff.DiffElement;
import org.jfrog.common.config.diff.DiffUtils;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Direct coverage for {@link SensitiveFieldCollector} (also exercised via {@link CryptoServiceImpl} tests).
 */
class SensitiveFieldCollectorTest {

    private static final String D = DiffUtils.DELIMITER;

    @Test
    void collectFromObjectWithNullDoesNothing() {
        List<SensitiveFieldDescriptor> out = SensitiveFieldCollector.collect(null, 20, D);
        assertTrue(out.isEmpty());
    }

    @Test
    void collectFromObjectFindsSensitiveStringOnDeclaredClass() {
        WithSecret holder = new WithSecret();
        holder.secret = "value";
        List<SensitiveFieldDescriptor> out = SensitiveFieldCollector.collect(holder, 20, D);
        assertEquals(1, out.size());
        assertTrue(out.get(0) instanceof SensitiveFieldDescriptor.SensitiveStringDescriptor);
        assertEquals("secret", out.get(0).getPath());
    }

    @Test
    void collectBuildsNestedDiffPath() {
        Outer outer = new Outer();
        outer.inner = new WithSecret();
        outer.inner.secret = "nested";
        List<SensitiveFieldDescriptor> out = SensitiveFieldCollector.collect(outer, 20, D);
        assertEquals(1, out.size());
        assertEquals("inner" + D + "secret", out.get(0).getPath());
    }

    @Test
    void collectFromObjectRespectsMaxDepthForNestedGraph() {
        Outer outer = new Outer();
        outer.inner = new WithSecret();
        outer.inner.secret = "nested";
        List<SensitiveFieldDescriptor> out = SensitiveFieldCollector.collect(outer, 0, D);
        assertTrue(out.isEmpty());
    }

    @Test
    void collectBuildsDeeplyNestedDiffPath() {
        DeepOuter root = new DeepOuter();
        root.mid = new Outer();
        root.mid.inner = new WithSecret();
        root.mid.inner.secret = "deep";
        List<SensitiveFieldDescriptor> out = SensitiveFieldCollector.collect(root, 20, D);
        assertEquals(1, out.size());
        assertEquals("mid" + D + "inner" + D + "secret", out.get(0).getPath());
    }

    @Test
    void collectFindsInheritedSensitiveField() {
        Child child = new Child();
        child.secret = "inherited";
        List<SensitiveFieldDescriptor> out = SensitiveFieldCollector.collect(child, 20, D);
        assertEquals(1, out.size());
        assertEquals("secret", out.get(0).getPath());
    }

    @Test
    void collectBuildsDynamicSensitivePath() {
        WithDynamic holder = new WithDynamic();
        List<SensitiveFieldDescriptor> out = SensitiveFieldCollector.collect(holder, 20, D);
        assertEquals(1, out.size());
        assertTrue(out.get(0) instanceof SensitiveFieldDescriptor.DynamicSensitiveObjectDescriptor);
        assertEquals("headers", out.get(0).getPath());
    }

    @Test
    void collectBuildsNestedDynamicSensitivePath() {
        OuterWithDynamic root = new OuterWithDynamic();
        root.nested = new WithDynamic();
        List<SensitiveFieldDescriptor> out = SensitiveFieldCollector.collect(root, 20, D);
        assertEquals(1, out.size());
        assertEquals("nested" + D + "headers", out.get(0).getPath());
    }

    @SuppressWarnings("unused")
    private static class WithSecret {
        @Sensitive
        private String secret;
    }

    @SuppressWarnings("unused")
    private static class Outer {
        private WithSecret inner;
    }

    @SuppressWarnings("unused")
    private static class DeepOuter {
        private Outer mid;
    }

    @SuppressWarnings("unused")
    private static class Base {
        @Sensitive
        String secret;
    }

    private static class Child extends Base {
    }

    @SuppressWarnings("unused")
    private static class WithDynamic {
        @DynamicSensitive(handler = NoopHandler.class)
        private Object headers;
    }

    @SuppressWarnings("unused")
    private static class OuterWithDynamic {
        private WithDynamic nested;
    }

    private static class NoopHandler implements DynamicSensitiveValueHandler<Object> {
        @Override public void validate(Object v) {}
        @Override public void encrypt(Object v) {}
        @Override public void decrypt(Object v) {}
        @Override public Object mask(Object v) { return v; }
    }

    @Test
    void collectUsesJavaFieldNameIgnoringDiffElementAlias() {
        WithDiffElementAlias holder = new WithDiffElementAlias();
        holder.secret = "value";
        List<SensitiveFieldDescriptor> out = SensitiveFieldCollector.collect(holder, 20, D);
        assertEquals(1, out.size());
        assertEquals("secret", out.get(0).getPath()); // not "alias"
    }

    @SuppressWarnings("unused")
    private static class WithDiffElementAlias {
        @Sensitive
        @DiffElement(name = "alias")
        String secret;
    }
}
