package org.artifactory.common.config.metadata;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.jfrog.common.JsonUtils;
import org.testng.annotations.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;


/**
 * @author Alexei Vainshtein
 */
public class MetadataUtilsTest {

    @Test
    public void testGetWrapperTypes() {
        Set<Class<?>> wrapperClasses =
                Sets.newHashSet(Boolean.class, Character.class, Byte.class, Short.class, String.class, Integer.class,
                        Long.class, Float.class, Double.class, Void.class);
        Set<Class<?>> wrapperTypes = MetadataUtils.getWrapperTypes();
        assertTrue(wrapperTypes.containsAll(wrapperClasses));
    }

    @Test
    public void testInitObjectMapper() {
        ObjectMapper objectMapper = MetadataUtils.initObjectMapper();
        SerializationConfig serializationConfig = objectMapper.getSerializationConfig();
        assertEquals(serializationConfig.getDefaultPropertyInclusion().getContentInclusion(),
                JsonInclude.Include.NON_EMPTY);
    }

    @Test
    public void testObjectToJsonWithoutAnnotation() {
        assertNull(MetadataUtils.objectToJson(new TestObject()));
    }

    @Test
    public void testObjectToJsonWithNullObject() {
        assertNull(MetadataUtils.objectToJson(null));
    }

    @Test
    public void testGetArrayOfObjectsFromObject() throws NoSuchFieldException, IllegalAccessException {
        TestComplex complex = new TestComplex(null);
        Object[] listOfStrings =
                MetadataUtils.getArrayOfObjects(complex, TestComplex.class.getDeclaredField("listOfStrings"));
        assertNotNull(listOfStrings);
        assertEquals(listOfStrings.length, complex.listOfStrings.size());
        Arrays.stream(listOfStrings).forEach(
                o -> assertTrue(complex.listOfStrings.stream().anyMatch(s -> s.equalsIgnoreCase(String.valueOf(o)))));

        assertNull(MetadataUtils.getArrayOfObjects(complex, TestComplex.class.getDeclaredField("listOfComplex")));
    }

    @Test
    public void testObjectToJson() throws NoSuchFieldException {
        TestComplex testComplex = generateTestComplexObject();
        String json = MetadataUtils.objectToJson(testComplex);
        // Check top layer size
        JsonNode jsonNode = JsonUtils.getInstance().readTree(json);
        assertEquals(jsonNode.size(), 3);
        JsonNode version = jsonNode.get("version");
        assertEquals(version.asInt(), TestComplex.class.getAnnotation(MetadataVersion.class).metadataVersion());
        // Check configs
        assertConfig(jsonNode);
        // Check top layer info
        assertInfo(testComplex, jsonNode);
    }

    private void assertInfo(TestComplex testComplex, JsonNode jsonNode) throws NoSuchFieldException {
        JsonNode info = jsonNode.get("info");
        assertEquals(info.size(), 18);
        assertNull(info.get("testAnotherComplexObject"));

        JsonNode enumNode = info.get("testEnum");
        assertNull(enumNode);
        MetadataInfo enumAnnotation = getMetadataInfo(testComplex.getClass().getDeclaredField("testEnumWithCons"));
        assertEnum(info, enumAnnotation, "testEnumWithCons");

        enumAnnotation = getMetadataInfo(testComplex.getClass().getDeclaredField("testEnumWithValuesPreConfigured"));
        assertEnum(info, enumAnnotation, "testEnumWithValuesPreConfigured");

        enumAnnotation = getMetadataInfo(
                testComplex.getClass().getDeclaredField("testComplexObject").getType().getDeclaredField("testEnum"));
        assertEnum(info, enumAnnotation, "testComplexObject.testEnum");


        checkSuperClass(testComplex, info);

        assertEmpty(info, "listOfComplex.name");
        assertEmpty(info, "testComplexObject.listTest.name");

        assertDefaultValueOfFieldsWithNoValues(info, "listOfComplex.size", "size", ListTest.class);
        assertDefaultValueOfFieldsWithNoValues(info, "testComplexObject.value", "value", TestComplexObject.class);
        assertDefaultValueOfFieldsWithNoValues(info, "testComplexObject.isTrue", "isTrue", TestComplexObject.class);
        assertDefaultValueOfFieldsWithNoValues(info, "testComplexObject.intKey", "intKey",
                TestComplexObject.class.getSuperclass());
        assertDefaultValueOfFieldsWithNoValues(info, "testComplexObject.isBoolean", "isBoolean",
                TestComplexObject.class.getSuperclass());
        assertDefaultValueOfFieldsWithNoValues(info, "testComplexObject.listTest.size", "size", ListTest.class);
        assertComplexWithMetadata(testComplex, info, "testEnumWithValuesPreConfigured");
        assertComplexWithMetadata(testComplex, info, "testComplexObjectWithMetadata");

        // assertList without pre configured values
        assertList(testComplex, info, "integerListNoValuesConfigured", testComplex.integerListNoValuesConfigured, true);
        assertList(testComplex, info, "listOfStrings", testComplex.listOfStrings, false);
        assertList(testComplex, info, "integerListWithDefaultValues", testComplex.integerListWithDefaultValues, true);
        assertList(testComplex, info, "integerList", testComplex.integerList, true);

        assertComplexWithAnnotation(testComplex, info);
    }

    private void assertComplexWithAnnotation(TestComplex testComplex, JsonNode info) throws NoSuchFieldException {
        MetadataInfo testComplexObjectWithMetadataAnnotation =
                getMetadataInfo(testComplex.getClass().getDeclaredField("testComplexObjectWithMetadata"));
        JsonNode fieldNode = info.get("testComplexObjectWithMetadata");
        assertSizeAndDefaultValue(fieldNode, 2, testComplexObjectWithMetadataAnnotation.defaultValue());
        JsonNode fieldValues = fieldNode.get("values");
        fieldValues.forEach(jsonNode1 -> assertTrue(Arrays.stream(testComplexObjectWithMetadataAnnotation.values())
                .anyMatch(s -> String.valueOf(s).equalsIgnoreCase(jsonNode1.asText()))));
    }

    private void assertSizeAndDefaultValue(JsonNode fieldNode, int size, String defaultValue) {
        assertEquals(fieldNode.size(), size);
        assertEquals(fieldNode.get("defaultValue").asText(), defaultValue.toLowerCase());
    }

    private void assertList(TestComplex testComplex, JsonNode info, String fieldName, List<?> list,
            boolean withDefaultValue) throws NoSuchFieldException {
        JsonNode fieldNode = info.get(fieldName);
        assertEquals(fieldNode.size(), 2);
        MetadataInfo fieldMetadata = null;
        if (withDefaultValue) {
            fieldMetadata = getMetadataInfo(testComplex.getClass().getDeclaredField(fieldName));
        }

        JsonNode defValNode = fieldNode.get("defaultValue");

        if (defValNode != null) {
            String defaultValue = fieldMetadata == null ? String.valueOf(list.get(0)) : fieldMetadata.defaultValue();
            assertEquals(defValNode.asText(), defaultValue);
            JsonNode fieldValues = fieldNode.get("values");

            if (fieldMetadata != null && fieldMetadata.values().length > 0) {
                String[] values = fieldMetadata.values();
                assertEquals(fieldValues.size(), values.length);
                fieldValues.forEach(jsonNode1 -> assertTrue(Arrays.stream(values)
                        .anyMatch(valueInList -> String.valueOf(valueInList).equalsIgnoreCase(jsonNode1.asText()))));
                return;
            }

            assertEquals(fieldValues.size(), list.size());
            fieldValues.forEach(jsonNode1 -> assertTrue(list.stream()
                    .anyMatch(valueInList -> String.valueOf(valueInList).equalsIgnoreCase(jsonNode1.asText()))));
        }
    }

    private void assertEnum(JsonNode info, MetadataInfo enumAnnotation, String name) {
        JsonNode enumNode = info.get(name);
        assertSizeAndDefaultValue(enumNode, 2, enumAnnotation.defaultValue());
        JsonNode values = enumNode.get("values");
        if (enumAnnotation.values().length == 0) {
            assertTrue(values.size() > 0);
        } else {
            assertEquals(values.size(), enumAnnotation.values().length);
            values.forEach(
                    jsonNode1 -> Arrays.stream(enumAnnotation.values()).anyMatch(s -> s.equals(jsonNode1.asText())));
        }
    }

    private void assertEmpty(JsonNode info, String s) {
        JsonNode listInternalField = info.get(s);
        assertEquals(listInternalField.size(), 0);
    }

    private void checkSuperClass(TestComplex testComplex, JsonNode info) throws NoSuchFieldException {
        Class<?> superclass = testComplex.getClass().getSuperclass();
        compare(info, superclass, "isBoolean");
        compare(info, superclass, "intKey");
        assertNull(info.get("skipped"));
    }

    private void assertDefaultValueOfFieldsWithNoValues(JsonNode info, String nameInJson, String fieldName,
            Class<?> aClass) throws NoSuchFieldException {
        JsonNode listInternalField = info.get(nameInJson);
        assertSizeAndDefaultValue(listInternalField, 1, getValue(aClass, fieldName));
    }

    private String getValue(Class<?> classObject, String fieldName) throws NoSuchFieldException {
        return classObject.getDeclaredField(fieldName).getAnnotation(MetadataInfo.class).defaultValue();
    }

    private void assertComplexWithMetadata(TestComplex testComplex, JsonNode info, String fieldName)
            throws NoSuchFieldException {
        JsonNode fieldNode = info.get(fieldName);
        MetadataInfo fieldMetadata = getMetadataInfo(testComplex.getClass().getDeclaredField(fieldName));
        assertSizeAndDefaultValue(fieldNode, 2, fieldMetadata.defaultValue());
        JsonNode fieldValues = fieldNode.get("values");
        assertEquals(fieldValues.size(), fieldMetadata.values().length);
        fieldValues.forEach(jsonNode1 -> assertTrue(
                Arrays.stream(fieldMetadata.values()).anyMatch(s -> s.equalsIgnoreCase(jsonNode1.asText()))));
    }

    private MetadataInfo getMetadataInfo(Field declaredField) {
        return declaredField.getAnnotation(MetadataInfo.class);
    }

    private void assertConfig(JsonNode jsonNode) {
        JsonNode configs = jsonNode.get("configs");
        assertNotNull(configs);
        assertEquals(configs.size(), 1);
        JsonNode testAnotherComplexObject = configs.get("testAnotherComplexObject");
        assertEquals(testAnotherComplexObject.size(), 2);
        JsonNode version = testAnotherComplexObject.get("version");
        assertEquals(version.asInt(),
                TestAnotherComplexObject.class.getAnnotation(MetadataVersion.class).metadataVersion());
        // Check configs info
        assertConfigInfo(testAnotherComplexObject);
    }

    private void assertConfigInfo(JsonNode testAnotherComplexObject) {
        JsonNode infoConfigs = testAnotherComplexObject.get("info");
        assertEquals(infoConfigs.size(), 2);
        Field[] declaredFields = TestAnotherComplexObject.class.getDeclaredFields();
        for (Field declaredField : declaredFields) {
            MetadataInfo annotation = getMetadataInfo(declaredField);
            JsonNode internalJsonNode = infoConfigs.get(declaredField.getName());
            if (annotation == null || annotation.skip()) {
                assertNull(internalJsonNode);
                continue;
            }
            assertNotNull(internalJsonNode);
        }
    }

    private TestComplex generateTestComplexObject() {
        ListTest listTest = new ListTest();
        listTest.size = 1;
        listTest.name = "A";
        ListTest listTest1 = new ListTest();
        listTest1.size = 2;
        listTest1.name = "B";
        return new TestComplex(null);
    }

    private void compare(JsonNode info, Class<?> superclass, String fieldName) throws NoSuchFieldException {
        JsonNode fieldNode = info.get(fieldName);
        Field declaredField = superclass.getDeclaredField(fieldName);
        MetadataInfo declaredFieldAnnotation = getMetadataInfo(declaredField);
        assertSizeAndDefaultValue(fieldNode, 1, declaredFieldAnnotation.defaultValue());
    }

    @MetadataVersion(metadataVersion = 1)
    @Data
    @EqualsAndHashCode(callSuper=false)
    static class TestComplex extends TestObject {

        private List<ListTestNoMetadataAnnotations> list;
        private List<ListTestAnother> listTestAnothers;
        private final List<String> listOfStrings = Lists.newArrayList("why", "not", "always");
        @MetadataInfo(defaultValue = "1", values = {"4", "5", "6"})
        private TestEnum testEnumWithValuesPreConfigured;
        @MetadataInfo(defaultValue = "2")
        private TestEnumWithCons testEnumWithCons;
        @MetadataInfo(defaultValue = "1", values = {"7", "8", "9"})
        private final List<Integer> integerList = Lists.newArrayList(1, 2, 3);

        @MetadataInfo(defaultValues = {"1", "2"})
        private final List<Integer> integerListWithDefaultValues = List.of(1, 2, 3, 4);

        @MetadataInfo(defaultValue = "1")
        private List<Integer> integerListNoValuesConfigured = Lists.newArrayList(1, 2, 3);
        private List<ListTest> listOfComplex;
        private TestAnotherComplexObject testAnotherComplexObject;
        @MetadataInfo(defaultValue = "One")
        private TestEnum testEnum;
        private TestComplexObject testComplexObject;
        @MetadataInfo(defaultValue = "1", values = {"should", "not", "happen"})
        private TestComplexObject testComplexObjectWithMetadata;

        public TestComplex(List<ListTest> listOfComplex) {
            this.listOfComplex = listOfComplex;
            list = new ArrayList<>();
            ListTestAnother listTestAnother = new ListTestAnother();
            listTestAnother.name = "C";
            listTestAnother.size = 3;
            this.listTestAnothers = Arrays.asList(listTestAnother);
        }
    }

    static class TestObject {

        @MetadataInfo(defaultValue = "true")
        private boolean isBoolean;

        @MetadataInfo(skip = true)
        private String skipped;

        @MetadataInfo(defaultValue = "90")
        private int intKey;

    }

    @MetadataVersion(metadataVersion = 2)
    static class TestAnotherComplexObject {

        @MetadataInfo(defaultValue = "false")
        private boolean isTrue;

        @MetadataInfo(skip = true)
        private String missing;

        @MetadataInfo(defaultValue = "50")
        private int value;

    }

    static class TestComplexObject extends TestObject {

        @MetadataInfo(defaultValue = "false")
        private boolean isTrue;

        @MetadataInfo(skip = true)
        private String missing;

        @MetadataInfo(defaultValue = "50")
        private int value;

        private ListTest listTest;

        @MetadataInfo(defaultValue = "Two")
        private TestEnumWithCons testEnum;
    }

    static class ListTest {

        @MetadataInfo(defaultValue = "0")
        private int size;
        @MetadataInfo
        private String name;

    }

    static class ListTestNoMetadataAnnotations {

        private int size;
        private String name;
    }

    static class ListTestAnother {

        private int size;
        private String name;
    }

    enum TestEnum {
        One, Two, Three
    }

    enum TestEnumWithCons implements MetadataEnum {
        One("1"), Two("2"), Three("3");

        public String message;

        TestEnumWithCons(String message) {
            this.message = message;

        }

        @Override
        public String getConfigName() {
            return message;
        }
    }
}