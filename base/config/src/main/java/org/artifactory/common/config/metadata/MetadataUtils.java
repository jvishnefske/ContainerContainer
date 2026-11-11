package org.artifactory.common.config.metadata;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import com.google.common.collect.Sets;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.UtilityClass;
import org.jfrog.common.JsonSensitiveDataException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Library for converting a given object to metadata json
 * The library will deep dive into the object fields (internal and inherited)
 * check the metadata annotation and create a json.
 * The json will include version field of the metadata and the info regarding each field.
 *
 * {
 * "version":1,
 * "info":{     will include the internal and inherited fields.
 * },
 * "configs":{  For complex object that holds another complex object that annotated with MetadataVersion then the
 * internal field with the annotation of the metadata version will be under configs
 * }
 * }
 *
 * @author Alexei Vainshtein
 */
@UtilityClass
public class MetadataUtils {
    private static final Logger log = LoggerFactory.getLogger(MetadataUtils.class);

    private static final Set<Class<?>> WRAPPER_TYPES = getWrapperTypes();
    private static final ObjectMapper objectMapper = initObjectMapper();

    static ObjectMapper initObjectMapper() {
        ObjectMapper objectMapper = new ObjectMapper() {
            {
                setSerializationInclusion(JsonInclude.Include.NON_EMPTY);
                configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
                registerModule(new ParameterNamesModule());
            }
        };

        objectMapper.findAndRegisterModules();

        return objectMapper;
    }

    /**
     * Returns a json that represents the metadata of the given object
     *
     * @param object - The object to deep dive and extract the metadata from.
     * @return - The json
     */
    public static String objectToJson(Object object) {
        try {
            if (object == null) {
                log.warn("Passed a null object..");
                return null;
            }
            MetadataVersionInfo metadataVersion = extractObjectMetadata(object, object.getClass());
            return objectMapper.writeValueAsString(metadataVersion);
        } catch (ReflectiveOperationException | JsonProcessingException e) {
            String errorMessage = String.format("Error occurred while creating the metadata json: %s", e.getMessage());
            log.error(errorMessage, e);
        }
        return null;
    }

    /**
     * The top layer. This will extract the class metadata version annotation and deep dive to extract the other
     * fields.
     *
     * @param object - The object to perform the extraction on.
     * @param aClass - Class of the object. Need to be passed in case the object is null.
     * @return the {@link MetadataVersionInfo} that hold the extracted info on this object.
     * @throws ReflectiveOperationException - Upon exception for accessing a field.
     */
    static MetadataVersionInfo extractObjectMetadata(Object object, Class<?> aClass)
            throws ReflectiveOperationException {
        // The map that will hold the annotations per field name.
        Map<String /*The internal field generated name*/, Annotation> jsonElementsMap = new HashMap<>();
        MetadataVersionInfo metadataVersion = createMetadataVersion(aClass);
        populateByObjectFields(object, metadataVersion, jsonElementsMap, aClass);
        Class<?> superclass = aClass.getSuperclass();
        while (superclass != null && superclass.getDeclaredFields().length > 0) { //goes till reach the object class
            log.debug("Found super class of type {}", superclass.getSimpleName());
            populateByObjectFields(object, metadataVersion, jsonElementsMap, superclass);
            superclass = superclass.getSuperclass();
        }
        metadataVersion.setInfo(jsonElementsMap);
        return metadataVersion;
    }

    /**
     * Extracting the object internal fields and adding to the map of annotations.
     *
     * @param object          - The object to work on
     * @param jsonElementsMap - The map that holds the annotations.
     * @param metadataVersion - The higher level of the metadata version that an internal metadata version, if
     *                        exists, would be added to.
     * @param classObject     - The class that is working on. Could be the object class or its super class (the extended
     *                        from)
     * @throws ReflectiveOperationException - The exception that is thrown upon error accessing the field
     */
    static void populateByObjectFields(Object object, MetadataVersionInfo metadataVersion,
            Map<String, Annotation> jsonElementsMap, Class<?> classObject) throws ReflectiveOperationException {
        for (Field field : classObject.getDeclaredFields()) {
            field.setAccessible(true);
            Object fieldObject = null;
            if (object != null) {
                fieldObject = field.get(object);
            }
            Class<?> classMember = field.getType();
            String name = field.getName();
            log.debug("Running on field {} of type {}", name, field.getName());
            if (classMember.isAnnotationPresent(MetadataVersion.class)) {
                // open a new bucket
                log.debug("Field {} annotated with metadata version annotation", name);
                MetadataVersionInfo internalConfigVersion = extractObjectMetadata(fieldObject, classMember);
                metadataVersion.addMetadataVersion(name, internalConfigVersion);
                continue;
            }
            extractAndPopulate(jsonElementsMap, name, field, fieldObject);
        }
    }

    /**
     * Creates the {@link MetadataVersionInfo} object
     *
     * @param aClass - The top level class that holds the metadata version annotation
     * @return The {@link MetadataVersionInfo} object
     * @throws IllegalAccessException - If the class is not annotated with the version
     */
    static MetadataVersionInfo createMetadataVersion(Class<?> aClass) throws IllegalAccessException {
        MetadataVersion
                annotation = aClass.getAnnotation(MetadataVersion.class);
        if (annotation == null) {
            throw new IllegalAccessException(
                    "The class " + aClass.getName() + " not annotated with the metadata version annotation");
        }
        return new MetadataVersionInfo(annotation.metadataVersion());
    }

    /**
     * Extracts and changes the enum field annotation. Extracts the enum values and adding those values to the given
     * annotation to represent the acceptable values.
     *
     * @param field      - The enum field
     * @param annotation - The annotation to add the values to
     * @throws ReflectiveOperationException - If an error occurs accessing the field.
     */
    static MetadataInfo createAnnotationForEnumField(Field field, MetadataInfo annotation)
            throws ReflectiveOperationException {
        Class<?> type = field.getType();
        Method method = type.getDeclaredMethod("values");
        Enum[] values = (Enum[]) method.invoke(type);
        String[] enumNames =
                Arrays.stream(values).map(anEnum -> ((MetadataEnum) anEnum).getConfigName()).toArray(String[]::new);
        return createNewAnnotation(annotation, enumNames);
    }

    /**
     * returns a new annotation with the provided values. The default value is taking from the configured annotation
     *
     * @param annotation - the configured annotation
     * @param values     - The values that need to be added
     * @return a new annotation
     */
    private static MetadataInfo createNewAnnotation(MetadataInfo annotation, String[] values) {
        return new MetadataInfo() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return annotation.annotationType();
            }

            @Override
            public String defaultValue() {
                return (annotation.defaultValues() == null || annotation.defaultValues().length == 0)
                        ? annotation.defaultValue().toLowerCase()
                        : null;
            }

            @Override
            public String[] defaultValues() {
                return annotation.defaultValues();
            }

            @Override
            public String[] values() {
                return values;
            }

            @Override
            public boolean skip() {
                return annotation.skip();
            }
        };
    }

    /**
     * Populating the collection (ex: {@link List}) values. The object itself can't be null.
     * If the object null, please use
     * {@link MetadataUtils#extractCollectionParameterizedType(Map, Field, String, Object[])}
     *
     * @param object          - The object to extract the array of values from.
     * @param jsonElementsMap - The map of annotations
     * @param field           - The field to work upon
     * @param name            - The name to be "registered" into the map
     * @throws ReflectiveOperationException - If error occurs working on a field.
     */
    static void populateCollectionValues(@NotNull Object object, Map<String, Annotation> jsonElementsMap, Field field,
            String name) throws ReflectiveOperationException {
        Object[] objects = getArrayOfObjects(object, field);
        if (objects == null) {
            log.trace("No elements found for {}", field.getName());
            return;
        }
        extractCollectionParameterizedType(jsonElementsMap, field, name, objects);
    }

    /**
     * This method will extract the values of the list and populate by configured type.
     * For primitive or wrapped types, use the annotation that presented or create a new one with the values that
     * extracted from the collection.
     * For other types, will populate as for a regular object. For those internal fields of the object, the prefix
     * will include the collection configured name.
     * If the objects array is null (the list wasn't initialized), a new element of the parametized type will be
     * created and extracted.
     *
     * @param jsonElementsMap - The map of annotations
     * @param field           - The field to work upon
     * @param name            - The name to be "registered" into the map
     * @param objects         - The array to extract the values from if init. Can be null.
     * @throws ReflectiveOperationException - If error occurs working on a field.
     */
    private static void extractCollectionParameterizedType(Map<String, Annotation> jsonElementsMap, Field field,
            String name, Object[] objects) throws ReflectiveOperationException {
        ParameterizedType listGenericType = (ParameterizedType) field.getGenericType();
        Class<?> parameterizedClassType = (Class<?>) listGenericType.getActualTypeArguments()[0];
        log.debug("Found list of type: {}", parameterizedClassType.getName());
        if (parameterizedClassType.isPrimitive() || WRAPPER_TYPES.contains(parameterizedClassType)) {
            // add annotation and populate the values
            MetadataInfo metadataInfo = getOrCreateMetadataInfo(field, objects);
            if (!metadataInfo.skip()) {
                putAnnotation(jsonElementsMap, name, metadataInfo);
            }
            return;
        }
        Field[] declaredFields = parameterizedClassType.getDeclaredFields();
        Object fieldObject =
                objects != null && objects.length != 0 ? objects[0] : createNewInstance(parameterizedClassType);
        for (Field declaredField : declaredFields) {
            declaredField.setAccessible(true);
            extractAndPopulate(jsonElementsMap, generateKey(name, declaredField), declaredField, fieldObject);
        }
    }

    /**
     * Create new instance of this class
     *
     * @param aClass - The class to create a new instance from
     * @return the new instance of this class
     * @throws ReflectiveOperationException - If there is a problem accessing the class constractor.
     */
    private static Object createNewInstance(Class<?> aClass)
            throws ReflectiveOperationException {
        Constructor<?> constructor = aClass.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }

    /**
     * Gets or creates a new {@link MetadataVersionInfo} object
     *
     * @param field   - The field to check the annotation upon
     * @param objects - The objects that need to be put as values to the annotation
     * @return - The {@link MetadataVersionInfo} object
     */
    static MetadataInfo getOrCreateMetadataInfo(Field field, Object[] objects) {
        if (field.isAnnotationPresent(MetadataInfo.class)) {
            MetadataInfo metadataInfo = field.getAnnotation(MetadataInfo.class);
            if (metadataInfo.values().length == 0) {
                log.debug("The values for field {} not defined. Creating new annotation with the values",
                        field.getName());
                return createNewAnnotation(metadataInfo,
                        objects != null ? arrayToStringArray(objects) : null);
            }
            log.debug("Found metadata info for field {}", field.getName());
            return metadataInfo;
        }
        log.debug("Creating metadata info object for {}", field.getName());
        return new MetadataInfo() {

            @Override
            public Class<? extends Annotation> annotationType() {
                return MetadataInfo.class;
            }

            @Override
            public String defaultValue() {
                return objects != null && objects.length != 0 ? String.valueOf(objects[0]) : null;
            }

            @Override
            public String[] defaultValues() {
                return null;
            }

            @Override
            public String[] values() {
                return objects != null ? arrayToStringArray(objects) : null;
            }

            @Override
            public boolean skip() {
                return objects == null;
            }
        };
    }

    /**
     * Returns array of objects. The array will be the object itself (if its a {@link List})
     * or
     * the field in the object that is the list.
     *
     * @param object The object that holds the field of the collection or the collection itself.
     * @param field  - The field of the collection
     * @return array of objects
     */
    @Nullable
    static Object[] getArrayOfObjects(Object object, Field field) throws IllegalAccessException {
        Collection collection = null;
        if (!Collection.class.isAssignableFrom(object.getClass())) {
            field.setAccessible(true);
            Object internalField = field.get(object);
            if (internalField != null && Collection.class.isAssignableFrom(internalField.getClass())) {
                collection = (Collection) internalField;
            }
            if (collection == null) {
                return null;
            }
        } else {
            collection = (Collection) object;
        }
        return collection.toArray();
    }

    /**
     * The heavy lifting method. This method checks if extraction is required and if so the type of the field.
     * The extraction is based on the type of the field. For different type, different extraction way.
     * There are different types that are supported:
     * 1. Primitive or wrapper types. Those are added to the map as is if annotation presented.
     * 2. Enums - Extracting the values of the enum to change the values of the annotation on the fly before adding
     * to the map.
     * 3. Collection - For collection type, we need to drill down to identify the object type (complex, primitive,
     * wrapper and etc..). The extraction is then performed by the type of the list parameter type.
     * 4. A field that annotated with the metadata, will be added automatically. This need to be done
     * <strong>after</strong> checking the collection and enum since those two can be annotated with the interface
     * for the default values <strong>but</strong> need the values to be changed programmatically
     * 5.A complex object - An object that is not matching any of those criteria. For example an internal object.
     *
     * @param jsonElementsMap - The map of annotations
     * @param prefix          - The prefix to be added to the map
     * @param declaredField   - The field itself
     * @param fieldObject     - The object itself that action items perform upon.
     * @throws ReflectiveOperationException - Error upon accessing any field.
     */
    static void extractAndPopulate(Map<String, Annotation> jsonElementsMap, String prefix, Field declaredField,
            Object fieldObject) throws ReflectiveOperationException {
        Class<?> declaredFieldType = declaredField.getType();
        log.debug("Populating field {} of type {}", declaredField.getName(), declaredFieldType.getName());
        MetadataInfo metadataInfo = declaredField.getAnnotation(MetadataInfo.class);
        if (metadataInfo != null && metadataInfo.skip()) {
            return;
        }
        if (declaredFieldType.isPrimitive() ||
                WRAPPER_TYPES.contains(declaredFieldType)) {
            addPrimitive(jsonElementsMap, prefix, metadataInfo);
            return;
        }
        if (Enum.class.isAssignableFrom(declaredFieldType)) {
            addEnum(jsonElementsMap, prefix, declaredField, declaredFieldType, metadataInfo);
            return;
        }
        if (Collection.class.isAssignableFrom(declaredFieldType)) {
            addCollection(jsonElementsMap, prefix, declaredField, fieldObject);
            return;
        }

        if (metadataInfo != null) {
            putAnnotation(jsonElementsMap, prefix, metadataInfo);
            return;
        }
        extractAndPopulateComplexObject(jsonElementsMap, prefix, fieldObject, declaredFieldType);
        extractFromSuperClass(jsonElementsMap, prefix, fieldObject, declaredFieldType);
    }

    /**
     * Extracts the information from super class if exists. Super class is the class that extended from.
     * For example you work on A that extends B so B is the super class.
     *
     * @param jsonElementsMap   - The map of annotations
     * @param prefix            - The name to be added to the map
     * @param fieldObject       - The object to perform the work upon
     * @param declaredFieldType - The type of the object.
     * @throws ReflectiveOperationException - An error that is thrown upon any issue accessing the field
     */
    private static void extractFromSuperClass(Map<String, Annotation> jsonElementsMap, String prefix,
            Object fieldObject, Class<?> declaredFieldType) throws ReflectiveOperationException {
        Class<?> superclass = declaredFieldType.getSuperclass();
        while (superclass != null && superclass.getDeclaredFields().length > 0) { //goes till reach the object class
            log.debug("Found super class of type {}", superclass.getSimpleName());
            extractAndPopulateComplexObject(jsonElementsMap, prefix, fieldObject, superclass);
            superclass = superclass.getSuperclass();
        }
    }

    private static void addCollection(Map<String, Annotation> jsonElementsMap, String prefix, Field declaredField,
            Object fieldObject) throws ReflectiveOperationException {
        if (fieldObject != null) {
            log.debug("Found collection {}", declaredField.getName());
            populateCollectionValues(fieldObject, jsonElementsMap, declaredField, prefix);
            return;
        }
        log.debug("Found empty collection {}. Will populate from the parametrized type", declaredField.getName());

        extractCollectionParameterizedType(jsonElementsMap, declaredField, prefix, null);
    }

    private static void addEnum(Map<String, Annotation> jsonElementsMap, String prefix, Field declaredField,
            Class<?> declaredFieldType, MetadataInfo metadataInfo) throws ReflectiveOperationException {
        if (metadataInfo != null) {
            if (metadataInfo.values().length > 0) {
                log.debug("Found existing values of the annotation that configured for the enum");
                putAnnotation(jsonElementsMap, prefix, metadataInfo);
                return;
            }
            if (MetadataEnum.class.isAssignableFrom(declaredFieldType)) {
                log.debug("Found enum {}, changing annotation", declaredField.getName());
                MetadataInfo annotation = createAnnotationForEnumField(declaredField, metadataInfo);
                putAnnotation(jsonElementsMap, prefix, annotation);
                return;
            }
            log.error("Found enum of type {} but this enum is not implementing MetadataEnum.",
                    declaredFieldType.getSimpleName());
        }
    }

    private static void addPrimitive(Map<String, Annotation> jsonElementsMap, String prefix,
            MetadataInfo metadataInfo) {
        if (metadataInfo != null) {
            putAnnotation(jsonElementsMap, prefix, metadataInfo);
        }
    }

    /**
     * Add the annotation to the map
     */
    private static void putAnnotation(Map<String, Annotation> jsonElementsMap, String prefix,
            MetadataInfo metadataInfo) {
        log.debug("Adding {} to the map", prefix);
        jsonElementsMap.put(prefix, metadataInfo);
    }

    /**
     * Extract the information from a complex object that is not primitive, collection, wrapper or enum. For example
     * an internal object that holds internal fields (example for this can be the {@link MetadataVersionInfo}.
     * The method goes by each of the complex object internal fields and performs extraction upon each field depends
     * of the field type. Different appraoch is needed for extracting internal field such as list, enum, wrapper type
     * or primitive then a complex internal field
     *
     * @param jsonElementsMap   - The map of annotations
     * @param prefix            - The name to be added to the map
     * @param object            - The object to perform the work upon
     * @param declaredFieldType - The type of the object.
     * @throws ReflectiveOperationException - An error that is thrown upon any issue accessing the field
     */
    static void extractAndPopulateComplexObject(Map<String, Annotation> jsonElementsMap, String prefix,
            Object object, Class<?> declaredFieldType) throws ReflectiveOperationException {
        log.debug("Found a complex object of type {}", declaredFieldType.getName());
        Field[] internalDeclaredFields = declaredFieldType.getDeclaredFields();
        for (Field internalDeclaredField : internalDeclaredFields) {
            internalDeclaredField.setAccessible(true);
            Class<?> fieldTypeRec = internalDeclaredField.getType();
            log.debug("Populating internal complex field of type: {}", fieldTypeRec.getName());
            if (fieldTypeRec.isPrimitive() || WRAPPER_TYPES.contains(fieldTypeRec) ||
                    Enum.class.isAssignableFrom(fieldTypeRec) || List.class.isAssignableFrom(fieldTypeRec)) {
                extractAndPopulate(jsonElementsMap, generateKey(prefix, internalDeclaredField), internalDeclaredField,
                        object);
                continue;
            }
            extractAndPopulate(jsonElementsMap, generateKey(prefix, internalDeclaredField),
                    internalDeclaredField, object == null ? null : internalDeclaredField.get(object));
        }
    }

    /**
     * Converts the object array to a string array
     *
     * @param objects - The object array to be converted
     * @return a String array
     */
    static String[] arrayToStringArray(Object[] objects) {
        log.trace("Converting array to String array");
        String[] values = Arrays.stream(objects).map(String::valueOf).toArray(String[]::new);
        log.trace("Finished converting array to String array with {} elements", values.length);
        return values;
    }

    @NotNull
    static String generateKey(String prefix, Field declaredField) {
        return prefix + "." + declaredField.getName();
    }

    /**
     * Creates the objects that are not primitive but not requires an extraction
     *
     * @return a set that represents the wrappers objects
     */
    static Set<Class<?>> getWrapperTypes() {
        return Sets.newHashSet(
                Boolean.class, Character.class, Byte.class, Short.class, String.class,
                Integer.class, Long.class, Float.class, Double.class, Void.class);
    }

    /**
     * A class that holds the information regarding the annotated class.
     * The version holds the annotated version value.
     * The info holds the class internal and inherited fields.
     * The configs holds the internal fields that also got the annotation of metadata version.
     */
    @Data
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    static class MetadataVersionInfo {
        private final int version;
        private Map<String, Annotation> info;
        private Map<String, MetadataVersionInfo> configs;

        public MetadataVersionInfo(int version) {
            this.version = version;
            this.configs = new HashMap<>();
        }

        private void addMetadataVersion(String name, MetadataVersionInfo metadataVersion) {
            configs.put(name, metadataVersion);
        }

        private void setInfo(Map<String, Annotation> info) {
            this.info = info;
        }

    }

    public static <T> T treeToValue(JsonNode root, Class<T> clazz) {
        try {
            return objectMapper.treeToValue(root, clazz);
        } catch (JsonProcessingException e) {
            throw new JsonSensitiveDataException(e);
        }
    }

}
