package org.artifactory.sensitive.fields;

import org.artifactory.sensitive.DynamicSensitive;
import org.artifactory.sensitive.Sensitive;

import java.lang.reflect.Field;

/**
 * Descriptor for a single sensitive field discovered on an object graph.
 * Either a {@link Sensitive} String field or a {@link DynamicSensitive} object field.
 *
 * @author Fawaz Atwa
 */
public sealed interface SensitiveFieldDescriptor permits SensitiveFieldDescriptor.SensitiveStringDescriptor,
        SensitiveFieldDescriptor.DynamicSensitiveObjectDescriptor {

    Field getField();

    Object getParent();

    /**
     * Diff attribute path of this field from the scanned root, with segments joined by the delimiter passed to
     * {@link SensitiveFieldCollector#collect(Object, String)}. Used to match field-level diff entries for masking;
     * crypto operations ignore it.
     */
    String getPath();

    /**
     * A String field annotated with {@link Sensitive}.
     */
    record SensitiveStringDescriptor(Object parent, Field field, Sensitive annotation, String path)
            implements SensitiveFieldDescriptor {

        @Override
        public Field getField() {
            return field;
        }

        @Override
        public Object getParent() {
            return parent;
        }

        @Override
        public String getPath() {
            return path;
        }
    }

    /**
     * An object field annotated with {@link DynamicSensitive}.
     */
    record DynamicSensitiveObjectDescriptor(Object parent, Field field, DynamicSensitive annotation, String path)
            implements SensitiveFieldDescriptor {

        @Override
        public Field getField() {
            return field;
        }

        @Override
        public Object getParent() {
            return parent;
        }

        @Override
        public String getPath() {
            return path;
        }
    }
}
